package com.movesync.rostersync.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.movesync.rostersync.domain.entity.*;
import com.movesync.rostersync.domain.enums.EmployeeStatus;
import com.movesync.rostersync.domain.enums.ReconciliationStatus;
import com.movesync.rostersync.dto.ReconciliationRunResponse;
import com.movesync.rostersync.dto.SyncRecordDto;
import com.movesync.rostersync.repository.*;
import com.movesync.rostersync.service.parser.StreamingCsvParser;
import com.movesync.rostersync.util.HashUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.*;

@Service
public class ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);

    private final TenantRepository tenantRepository;
    private final OfficeRepository officeRepository;
    private final EmployeeRepository employeeRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ReconciliationRunRepository reconciliationRunRepository;
    private final ReconciliationItemRepository reconciliationItemRepository;
    private final StreamingCsvParser streamingCsvParser;
    private final SafetyBrakeService safetyBrakeService;
    private final FieldOwnershipService fieldOwnershipService;
    private final ObjectMapper objectMapper;
    private final com.movesync.rostersync.metrics.SyncMetricsService syncMetricsService;

    public ReconciliationService(TenantRepository tenantRepository,
                                 OfficeRepository officeRepository,
                                 EmployeeRepository employeeRepository,
                                 OutboxEventRepository outboxEventRepository,
                                 ReconciliationRunRepository reconciliationRunRepository,
                                 ReconciliationItemRepository reconciliationItemRepository,
                                 StreamingCsvParser streamingCsvParser,
                                 SafetyBrakeService safetyBrakeService,
                                 FieldOwnershipService fieldOwnershipService,
                                 ObjectMapper objectMapper,
                                 com.movesync.rostersync.metrics.SyncMetricsService syncMetricsService) {
        this.tenantRepository = tenantRepository;
        this.officeRepository = officeRepository;
        this.employeeRepository = employeeRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.reconciliationRunRepository = reconciliationRunRepository;
        this.reconciliationItemRepository = reconciliationItemRepository;
        this.streamingCsvParser = streamingCsvParser;
        this.safetyBrakeService = safetyBrakeService;
        this.fieldOwnershipService = fieldOwnershipService;
        this.objectMapper = objectMapper;
        this.syncMetricsService = syncMetricsService;
    }

    /**
     * Executes bulk nightly reconciliation diff.
     * 1. Fetches ID->Hash projections of active employees (~24MB for 200k rows).
     * 2. Streams the export file line-by-line (O(1) heap memory).
     * 3. Calculates diff (ADD, UPDATE, SKIP).
     * 4. Applies Safety Brake to prevent mass deactivation if missing ratio > threshold.
     */
    @Transactional
    public ReconciliationRunResponse reconcile(UUID tenantId, InputStream fileStream, Long providedVersion) throws IOException {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + tenantId));

        long fileVersion = (providedVersion != null && providedVersion > 0)
                ? providedVersion
                : System.currentTimeMillis();

        syncMetricsService.recordReconciliationRun();
        ReconciliationRun run = new ReconciliationRun(tenantId, fileVersion);
        run = reconciliationRunRepository.save(run);

        // Step 1: Pre-load active employee hashes via interface projection (ADR-004)
        List<EmployeeHashView> activeViews = employeeRepository.findAllActiveHashes(tenantId, EmployeeStatus.ACTIVE);
        Map<String, String> activeStoreHashes = new HashMap<>(activeViews.size());
        for (EmployeeHashView view : activeViews) {
            activeStoreHashes.put(view.getSourceEmployeeId(), view.getRecordHash());
        }
        int totalInStore = activeStoreHashes.size();
        run.setTotalInStore(totalInStore);

        // Counters: totalInFile, added, updated, skipped, errors
        int[] counters = {0, 0, 0, 0, 0};

        // Step 2: Line-by-line streaming diff
        final ReconciliationRun currentRun = run;
        streamingCsvParser.parseStream(fileStream, (SyncRecordDto record) -> {
            record.setVersion(fileVersion);
            counters[0]++; // totalInFile
            String empId = record.getSourceEmployeeId();
            String officeCode = record.getOfficeCode() != null ? record.getOfficeCode().trim().toUpperCase() : null;

            String recordHash = HashUtil.computeHrRecordHash(
                    record.getName(),
                    record.getEmail(),
                    record.getPhone(),
                    record.getManagerSourceId(),
                    officeCode,
                    record.getDepartment(),
                    record.getShift(),
                    EmployeeStatus.ACTIVE.name()
            );

            if (activeStoreHashes.containsKey(empId)) {
                String existingHash = activeStoreHashes.remove(empId);
                if (existingHash.equals(recordHash)) {
                    // Record unchanged
                    counters[3]++; // skipped
                } else {
                    // Hash mismatch -> update employee
                    Optional<Employee> empOpt = employeeRepository.findForUpdate(tenantId, empId);
                    if (empOpt.isPresent()) {
                        Employee employee = empOpt.get();
                        Office office = officeCode != null
                                ? officeRepository.findByTenantIdAndCode(tenantId, officeCode).orElse(null)
                                : null;
                        fieldOwnershipService.applyHrUpdate(employee, record, office);
                        employee.setSourceVersion(fileVersion);
                        employeeRepository.save(employee);

                        enqueueOutboxEvent(tenantId, employee.getId(), "EMPLOYEE_UPDATED", employee);
                        reconciliationItemRepository.save(new ReconciliationItem(
                                currentRun, empId, "UPDATE", "Nightly export HR field update"
                        ));
                        counters[2]++; // updated
                    }
                }
            } else {
                // New employee or re-activation
                Optional<Employee> existingEmpOpt = employeeRepository.findByTenantIdAndSourceEmployeeId(tenantId, empId);
                Office office = officeCode != null
                        ? officeRepository.findByTenantIdAndCode(tenantId, officeCode).orElse(null)
                        : null;

                if (existingEmpOpt.isPresent()) {
                    // Re-activate previously deactivated employee
                    Employee employee = existingEmpOpt.get();
                    employee.setStatus(EmployeeStatus.ACTIVE);
                    employee.setDeactivatedAt(null);
                    employee.setSourceVersion(fileVersion);
                    fieldOwnershipService.applyHrUpdate(employee, record, office);
                    employeeRepository.save(employee);

                    enqueueOutboxEvent(tenantId, employee.getId(), "EMPLOYEE_CREATED", employee);
                    reconciliationItemRepository.save(new ReconciliationItem(
                            currentRun, empId, "ADD", "Re-activated from nightly export"
                    ));
                } else {
                    // Brand new employee
                    Employee employee = new Employee(
                            tenant,
                            empId,
                            fileVersion,
                            record.getName() != null ? record.getName().trim() : "Unknown",
                            record.getEmail() != null ? record.getEmail().trim().toLowerCase() : "unknown@corp.com",
                            recordHash
                    );
                    employee.setPhone(record.getPhone() != null ? record.getPhone().trim() : null);
                    employee.setManagerSourceId(record.getManagerSourceId() != null ? record.getManagerSourceId().trim() : null);
                    employee.setOffice(office);
                    employee.setDepartment(record.getDepartment() != null ? record.getDepartment().trim() : null);
                    employee.setShift(record.getShift() != null ? record.getShift().trim() : null);
                    employee.setStatus(EmployeeStatus.ACTIVE);

                    Employee saved = employeeRepository.save(employee);
                    enqueueOutboxEvent(tenantId, saved.getId(), "EMPLOYEE_CREATED", saved);
                    reconciliationItemRepository.save(new ReconciliationItem(
                            currentRun, empId, "ADD", "New joiner from nightly export"
                    ));
                }
                counters[1]++; // added
            }
        });

        // Step 3: Safety Brake evaluation on remaining missing keys
        int missingCount = activeStoreHashes.size();
        SafetyBrakeService.SafetyBrakeResult brakeResult = safetyBrakeService.evaluate(
                totalInStore, missingCount, tenant.getSafetyBrakeThreshold()
        );

        run.setTotalInFile(counters[0]);
        run.setAddedCount(counters[1]);
        run.setUpdatedCount(counters[2]);
        run.setSkippedCount(counters[3]);
        run.setErrorCount(counters[4]);
        run.setMissingRatio(brakeResult.missingRatio());
        run.setBrakeTriggered(brakeResult.brakeTriggered());

        if (brakeResult.brakeTriggered()) {
            run.setStatus(ReconciliationStatus.BRAKE_TRIGGERED);
            for (String missingId : activeStoreHashes.keySet()) {
                reconciliationItemRepository.save(new ReconciliationItem(
                        run, missingId, "BRAKE_HELD",
                        "Deactivation withheld: Safety brake triggered (" + String.format("%.2f%%", brakeResult.missingRatio() * 100) + " missing)"
                ));
            }
            reconciliationRunRepository.save(run);
            syncMetricsService.recordSafetyBrakeTriggered();
            syncMetricsService.recordMissingRatio(brakeResult.missingRatio());
            syncMetricsService.recordReconciliationDuration(java.time.Duration.between(run.getStartedAt(), Instant.now()).toMillis());

            log.warn("Reconciliation Run [{}] finished with BRAKE_TRIGGERED: missing {} of {} active records",
                    run.getId(), missingCount, totalInStore);
            return ReconciliationRunResponse.fromEntity(run);
        }

        // Step 4: Within safe threshold: apply deactivations
        int deactivatedCount = 0;
        for (String missingId : activeStoreHashes.keySet()) {
            Optional<Employee> empOpt = employeeRepository.findForUpdate(tenantId, missingId);
            if (empOpt.isPresent()) {
                Employee employee = empOpt.get();
                employee.setStatus(EmployeeStatus.DEACTIVATED);
                employee.setDeactivatedAt(Instant.now());
                employee.setSourceVersion(fileVersion);
                employeeRepository.save(employee);

                enqueueOutboxEvent(tenantId, employee.getId(), "EMPLOYEE_DEACTIVATED", employee);
                reconciliationItemRepository.save(new ReconciliationItem(
                        run, missingId, "DEACTIVATE", "Omitted from nightly export"
                ));
                deactivatedCount++;
            }
        }

        run.setDeactivatedCount(deactivatedCount);
        run.setStatus(ReconciliationStatus.COMPLETED);
        run.setCompletedAt(Instant.now());
        reconciliationRunRepository.save(run);

        syncMetricsService.recordMissingRatio(brakeResult.missingRatio());
        syncMetricsService.recordReconciliationDuration(java.time.Duration.between(run.getStartedAt(), Instant.now()).toMillis());

        log.info("Reconciliation Run [{}] COMPLETED: file={}, store={}, added={}, updated={}, skipped={}, deactivated={}",
                run.getId(), counters[0], totalInStore, counters[1], counters[2], counters[3], deactivatedCount);

        return ReconciliationRunResponse.fromEntity(run);
    }

    /**
     * Operator manual override to approve a run halted by the Safety Brake.
     */
    @Transactional
    public ReconciliationRunResponse approveRun(UUID tenantId, UUID runId, String operatorUsername) {
        ReconciliationRun run = reconciliationRunRepository.findByIdAndTenantId(runId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Reconciliation run not found: " + runId));

        if (run.getStatus() != ReconciliationStatus.BRAKE_TRIGGERED) {
            throw new IllegalStateException("Run " + runId + " is not in BRAKE_TRIGGERED status (current: " + run.getStatus() + ")");
        }

        // Fetch held items and apply deactivations
        List<ReconciliationItem> items = reconciliationItemRepository.findByRunId(runId, Pageable.unpaged()).getContent();
        int deactivatedCount = 0;

        for (ReconciliationItem item : items) {
            if ("BRAKE_HELD".equals(item.getAction())) {
                Optional<Employee> empOpt = employeeRepository.findForUpdate(tenantId, item.getSourceEmployeeId());
                if (empOpt.isPresent()) {
                    Employee employee = empOpt.get();
                    employee.setStatus(EmployeeStatus.DEACTIVATED);
                    employee.setDeactivatedAt(Instant.now());
                    employee.setSourceVersion(run.getFileVersion());
                    employeeRepository.save(employee);

                    enqueueOutboxEvent(tenantId, employee.getId(), "EMPLOYEE_DEACTIVATED", employee);
                    item.setAction("DEACTIVATE_APPROVED");
                    item.setReason("Approved by operator: " + operatorUsername);
                    reconciliationItemRepository.save(item);
                    deactivatedCount++;
                }
            }
        }

        run.setDeactivatedCount(deactivatedCount);
        run.setStatus(ReconciliationStatus.APPROVED);
        run.setApprovedBy(operatorUsername);
        run.setCompletedAt(Instant.now());
        reconciliationRunRepository.save(run);

        log.info("Reconciliation Run [{}] APPROVED by operator [{}]: {} deactivations executed",
                runId, operatorUsername, deactivatedCount);

        return ReconciliationRunResponse.fromEntity(run);
    }

    /**
     * Retrieves reconciliation run details.
     */
    @Transactional(readOnly = true)
    public ReconciliationRunResponse getRun(UUID tenantId, UUID runId) {
        ReconciliationRun run = reconciliationRunRepository.findByIdAndTenantId(runId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Reconciliation run not found: " + runId));
        return ReconciliationRunResponse.fromEntity(run);
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
            log.error("Failed to serialize outbox event payload", e);
            throw new IllegalStateException("Failed to serialize outbox payload", e);
        }
    }
}
