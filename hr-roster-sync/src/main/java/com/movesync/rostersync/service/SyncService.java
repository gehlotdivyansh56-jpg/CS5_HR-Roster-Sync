package com.movesync.rostersync.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.movesync.rostersync.domain.entity.Employee;
import com.movesync.rostersync.domain.entity.OutboxEvent;
import com.movesync.rostersync.dto.*;
import com.movesync.rostersync.repository.EmployeeRepository;
import com.movesync.rostersync.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class SyncService {

    private static final Logger log = LoggerFactory.getLogger(SyncService.class);

    private final SingleEventSyncProcessor singleEventSyncProcessor;
    private final EmployeeRepository employeeRepository;
    private final FieldOwnershipService fieldOwnershipService;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final com.movesync.rostersync.metrics.SyncMetricsService syncMetricsService;

    public SyncService(SingleEventSyncProcessor singleEventSyncProcessor,
                       EmployeeRepository employeeRepository,
                       FieldOwnershipService fieldOwnershipService,
                       OutboxEventRepository outboxEventRepository,
                       ObjectMapper objectMapper,
                       com.movesync.rostersync.metrics.SyncMetricsService syncMetricsService) {
        this.singleEventSyncProcessor = singleEventSyncProcessor;
        this.employeeRepository = employeeRepository;
        this.fieldOwnershipService = fieldOwnershipService;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
        this.syncMetricsService = syncMetricsService;
    }

    /**
     * Ingests a batch of sync events.
     * Partial batch failure support: each event runs in an isolated transaction.
     */
    public BatchSyncResponse processBatch(UUID tenantId, SyncBatchRequest request) {
        if (request == null || request.getEvents() == null || request.getEvents().isEmpty()) {
            return new BatchSyncResponse(Collections.emptyList());
        }

        long startTime = System.nanoTime();
        syncMetricsService.recordEventReceived(request.getEvents().size());

        List<SyncRecordResult> results = new ArrayList<>(request.getEvents().size());
        for (SyncRecordDto event : request.getEvents()) {
            SyncRecordResult result = singleEventSyncProcessor.processSingleEvent(tenantId, event);
            results.add(result);
        }

        syncMetricsService.recordEventProcessingTime(System.nanoTime() - startTime);
        return new BatchSyncResponse(results);
    }

    /**
     * Updates Transport Admin owned fields (pickupPoint, dropPoint, specialAssistance).
     * Enforces that HR fields, record_hash, and source_version are never altered by transport operations.
     */
    @Transactional
    public EmployeeResponseDto updateTransportFields(UUID tenantId, String sourceEmployeeId, TransportFieldUpdateRequest request) {
        Employee employee = employeeRepository.findForUpdate(tenantId, sourceEmployeeId)
                .orElseThrow(() -> new IllegalArgumentException("Employee not found with id: " + sourceEmployeeId));

        fieldOwnershipService.applyTransportUpdate(employee, request);
        Employee saved = employeeRepository.save(employee);

        // Enqueue outbox event for transport downstream subscribers (routing, vehicle assignment)
        enqueueTransportOutboxEvent(tenantId, saved);

        return EmployeeResponseDto.fromEntity(saved);
    }

    /**
     * Tenant-scoped employee fetch.
     */
    @Transactional(readOnly = true)
    public EmployeeResponseDto getEmployee(UUID tenantId, String sourceEmployeeId) {
        Employee employee = employeeRepository.findByTenantIdAndSourceEmployeeId(tenantId, sourceEmployeeId)
                .orElseThrow(() -> new IllegalArgumentException("Employee not found with id: " + sourceEmployeeId));
        return EmployeeResponseDto.fromEntity(employee);
    }

    private void enqueueTransportOutboxEvent(UUID tenantId, Employee employee) {
        Map<String, Object> payloadMap = new LinkedHashMap<>();
        payloadMap.put("tenant_id", tenantId.toString());
        payloadMap.put("employee_id", employee.getId().toString());
        payloadMap.put("source_employee_id", employee.getSourceEmployeeId());
        payloadMap.put("transport_field_version", employee.getTransportFieldVersion());
        payloadMap.put("pickup_point", employee.getPickupPoint());
        payloadMap.put("drop_point", employee.getDropPoint());
        payloadMap.put("special_assistance", employee.getSpecialAssistance());
        payloadMap.put("event_type", "EMPLOYEE_TRANSPORT_UPDATED");
        payloadMap.put("timestamp", Instant.now().toString());

        try {
            String payloadJson = objectMapper.writeValueAsString(payloadMap);
            OutboxEvent outboxEvent = new OutboxEvent(tenantId, employee.getId(), "EMPLOYEE_TRANSPORT_UPDATED", payloadJson);
            outboxEventRepository.save(outboxEvent);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize transport outbox event payload", e);
            throw new IllegalStateException("Failed to serialize outbox payload", e);
        }
    }
}

