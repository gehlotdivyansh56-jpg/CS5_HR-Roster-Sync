package com.movesync.rostersync.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.movesync.rostersync.domain.entity.Employee;
import com.movesync.rostersync.domain.entity.Office;
import com.movesync.rostersync.domain.entity.OutboxEvent;
import com.movesync.rostersync.domain.entity.Tenant;
import com.movesync.rostersync.domain.enums.EmployeeStatus;
import com.movesync.rostersync.domain.enums.EventType;
import com.movesync.rostersync.dto.SyncRecordDto;
import com.movesync.rostersync.dto.SyncRecordResult;
import com.movesync.rostersync.repository.EmployeeRepository;
import com.movesync.rostersync.repository.OfficeRepository;
import com.movesync.rostersync.repository.OutboxEventRepository;
import com.movesync.rostersync.repository.TenantRepository;
import com.movesync.rostersync.util.HashUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Component
public class SingleEventSyncProcessor {

    private static final Logger log = LoggerFactory.getLogger(SingleEventSyncProcessor.class);

    private final TenantRepository tenantRepository;
    private final OfficeRepository officeRepository;
    private final EmployeeRepository employeeRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ValidationService validationService;
    private final FieldOwnershipService fieldOwnershipService;
    private final ObjectMapper objectMapper;
    private final EmployeeVersionCacheService versionCacheService;
    private final com.movesync.rostersync.metrics.SyncMetricsService syncMetricsService;

    public SingleEventSyncProcessor(TenantRepository tenantRepository,
                                    OfficeRepository officeRepository,
                                    EmployeeRepository employeeRepository,
                                    OutboxEventRepository outboxEventRepository,
                                    ValidationService validationService,
                                    FieldOwnershipService fieldOwnershipService,
                                    ObjectMapper objectMapper,
                                    EmployeeVersionCacheService versionCacheService,
                                    com.movesync.rostersync.metrics.SyncMetricsService syncMetricsService) {
        this.tenantRepository = tenantRepository;
        this.officeRepository = officeRepository;
        this.employeeRepository = employeeRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.validationService = validationService;
        this.fieldOwnershipService = fieldOwnershipService;
        this.objectMapper = objectMapper;
        this.versionCacheService = versionCacheService;
        this.syncMetricsService = syncMetricsService;
    }

    /**
     * Executes single event sync inside an isolated transaction.
     * Prevents partial failures from aborting the entire batch.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SyncRecordResult processSingleEvent(UUID tenantId, SyncRecordDto record) {
        String empId = record != null ? record.getSourceEmployeeId() : "UNKNOWN";
        Long version = record != null ? record.getVersion() : 0L;

        try {
            // 1. Validation check
            List<String> validationErrors = validationService.validate(tenantId, record);
            if (!validationErrors.isEmpty()) {
                String errorSummary = String.join("; ", validationErrors);
                log.warn("Validation failed for employee [{}] version [{}]: {}", empId, version, errorSummary);
                syncMetricsService.recordValidationFailed();
                return SyncRecordResult.rejected(empId, version, errorSummary);
            }

            // Fast-path Caffeine in-memory version check
            Long cachedVer = versionCacheService.getVersion(tenantId, record.getSourceEmployeeId().trim());
            if (cachedVer != null && record.getVersion() < cachedVer) {
                log.info("Fast-path cache hit: Ignored stale event for employee [{}] incoming version [{}] < cached version [{}]",
                        empId, record.getVersion(), cachedVer);
                syncMetricsService.recordEventStaleIgnored();
                return SyncRecordResult.ignoredStale(empId, record.getVersion(),
                        "Ignored stale event: incoming version " + record.getVersion() + " is older than cached version " + cachedVer);
            } else if (cachedVer != null && record.getVersion().equals(cachedVer)) {
                log.info("Fast-path cache hit: Ignored duplicate event for employee [{}] version [{}]", empId, record.getVersion());
                syncMetricsService.recordEventStaleIgnored();
                return SyncRecordResult.ignoredDuplicate(empId, record.getVersion(),
                        "Ignored duplicate event: version " + record.getVersion() + " has already been processed");
            }

            // 2. Fetch tenant
            Tenant tenant = tenantRepository.findById(tenantId)
                    .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + tenantId));

            // 3. Resolve office if provided
            Office office = null;
            if (record.getOfficeCode() != null && !record.getOfficeCode().trim().isEmpty()) {
                office = officeRepository.findByTenantIdAndCode(tenantId, record.getOfficeCode().trim().toUpperCase())
                        .orElse(null);
            }

            // 4. Pessimistic lock row lookup to eliminate concurrent write races on the same employee
            Optional<Employee> existingOpt = employeeRepository.findForUpdate(tenantId, record.getSourceEmployeeId().trim());

            if (existingOpt.isPresent()) {
                Employee employee = existingOpt.get();

                // Idempotency & Monotonic Version Ordering check
                if (record.getVersion() < employee.getSourceVersion()) {
                    log.info("Ignored stale event for employee [{}] incoming version [{}] < stored version [{}]",
                            empId, record.getVersion(), employee.getSourceVersion());
                    syncMetricsService.recordEventStaleIgnored();
                    return SyncRecordResult.ignoredStale(empId, record.getVersion(),
                            "Ignored stale event: incoming version " + record.getVersion() +
                            " is older than current version " + employee.getSourceVersion());
                }

                if (record.getVersion().equals(employee.getSourceVersion())) {
                    log.info("Ignored duplicate event for employee [{}] version [{}]", empId, record.getVersion());
                    syncMetricsService.recordEventStaleIgnored();
                    return SyncRecordResult.ignoredDuplicate(empId, record.getVersion(),
                            "Ignored duplicate event: version " + record.getVersion() + " has already been processed");
                }

                // Incoming version is strictly greater: apply mutation
                if (record.getEventType() == EventType.DEACTIVATE) {
                    employee.setStatus(EmployeeStatus.DEACTIVATED);
                    employee.setDeactivatedAt(Instant.now());
                    employee.setSourceVersion(record.getVersion());
                    employee.setUpdatedAt(Instant.now());

                    String officeCode = employee.getOffice() != null ? employee.getOffice().getCode() : null;
                    String newHash = HashUtil.computeHrRecordHash(
                            employee.getName(),
                            employee.getEmail(),
                            employee.getPhone(),
                            employee.getManagerSourceId(),
                            officeCode,
                            employee.getDepartment(),
                            employee.getShift(),
                            EmployeeStatus.DEACTIVATED.name()
                    );
                    employee.setRecordHash(newHash);
                    employeeRepository.save(employee);

                    enqueueOutboxEvent(tenantId, employee.getId(), "EMPLOYEE_DEACTIVATED", employee);
                    versionCacheService.putVersion(tenantId, employee.getSourceEmployeeId(), employee.getSourceVersion());
                    syncMetricsService.recordEventApplied();

                    log.info("Deactivated employee [{}] to version [{}]", empId, record.getVersion());
                    return SyncRecordResult.applied(empId, record.getVersion(), "Employee deactivated successfully");

                } else {
                    // CREATE or UPDATE on existing employee (re-activation or field changes)
                    employee.setStatus(EmployeeStatus.ACTIVE);
                    employee.setDeactivatedAt(null);
                    fieldOwnershipService.applyHrUpdate(employee, record, office);
                    employeeRepository.save(employee);

                    enqueueOutboxEvent(tenantId, employee.getId(), "EMPLOYEE_UPDATED", employee);
                    versionCacheService.putVersion(tenantId, employee.getSourceEmployeeId(), employee.getSourceVersion());
                    syncMetricsService.recordEventApplied();

                    log.info("Updated employee [{}] to version [{}]", empId, record.getVersion());
                    return SyncRecordResult.applied(empId, record.getVersion(), "Employee updated successfully");
                }

            } else {
                // Employee does not exist yet
                if (record.getEventType() == EventType.DEACTIVATE) {
                    log.info("Deactivate received for non-existent employee [{}]; ignored", empId);
                    syncMetricsService.recordEventStaleIgnored();
                    return SyncRecordResult.ignoredStale(empId, record.getVersion(),
                            "Cannot deactivate non-existent employee [" + empId + "]");
                }

                String officeCode = office != null ? office.getCode() : null;
                String hash = HashUtil.computeHrRecordHash(
                        record.getName(),
                        record.getEmail(),
                        record.getPhone(),
                        record.getManagerSourceId(),
                        officeCode,
                        record.getDepartment(),
                        record.getShift(),
                        EmployeeStatus.ACTIVE.name()
                );

                Employee employee = new Employee(
                        tenant,
                        record.getSourceEmployeeId().trim(),
                        record.getVersion(),
                        record.getName().trim(),
                        record.getEmail().trim().toLowerCase(),
                        hash
                );
                employee.setPhone(record.getPhone() != null ? record.getPhone().trim() : null);
                employee.setManagerSourceId(record.getManagerSourceId() != null ? record.getManagerSourceId().trim() : null);
                employee.setOffice(office);
                employee.setDepartment(record.getDepartment() != null ? record.getDepartment().trim() : null);
                employee.setShift(record.getShift() != null ? record.getShift().trim() : null);
                employee.setStatus(EmployeeStatus.ACTIVE);

                Employee saved = employeeRepository.save(employee);
                enqueueOutboxEvent(tenantId, saved.getId(), "EMPLOYEE_CREATED", saved);
                versionCacheService.putVersion(tenantId, saved.getSourceEmployeeId(), saved.getSourceVersion());
                syncMetricsService.recordEventApplied();

                log.info("Created new employee [{}] with version [{}]", empId, record.getVersion());
                return SyncRecordResult.applied(empId, record.getVersion(), "Employee created successfully");
            }

        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            log.warn("Data integrity conflict for employee [{}] version [{}]: {}", empId, version, e.getMessage());
            return SyncRecordResult.rejected(empId, version, "Data integrity conflict: unique constraint violation or concurrent write conflict");
        } catch (Exception e) {
            log.error("Unexpected error processing sync record for employee [{}] version [{}]: {}",
                    empId, version, e.getMessage(), e);
            return SyncRecordResult.rejected(empId, version, "Internal processing error: " + e.getMessage());
        }
    }

    private void enqueueOutboxEvent(UUID tenantId, UUID employeeId, String eventType, Employee employee) {
        Map<String, Object> payloadMap = new LinkedHashMap<>();
        payloadMap.put("tenant_id", tenantId.toString());
        payloadMap.put("employee_id", employeeId.toString());
        payloadMap.put("source_employee_id", employee.getSourceEmployeeId());
        payloadMap.put("source_version", employee.getSourceVersion());
        payloadMap.put("event_type", eventType);
        payloadMap.put("status", employee.getStatus().name());
        payloadMap.put("name", employee.getName());
        payloadMap.put("email", employee.getEmail());
        payloadMap.put("timestamp", Instant.now().toString());

        try {
            String payloadJson = objectMapper.writeValueAsString(payloadMap);
            OutboxEvent outboxEvent = new OutboxEvent(tenantId, employeeId, eventType, payloadJson);
            outboxEventRepository.save(outboxEvent);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize outbox event payload for employee [{}]", employee.getSourceEmployeeId(), e);
            throw new IllegalStateException("Failed to serialize outbox payload", e);
        }
    }
}

