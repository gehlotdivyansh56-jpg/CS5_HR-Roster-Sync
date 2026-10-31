package com.movesync.rostersync.service;

import com.movesync.rostersync.domain.entity.Employee;
import com.movesync.rostersync.domain.entity.Office;
import com.movesync.rostersync.domain.entity.OutboxEvent;
import com.movesync.rostersync.domain.entity.Tenant;
import com.movesync.rostersync.domain.enums.EmployeeStatus;
import com.movesync.rostersync.domain.enums.EventType;
import com.movesync.rostersync.domain.enums.OutboxStatus;
import com.movesync.rostersync.dto.*;
import com.movesync.rostersync.repository.EmployeeRepository;
import com.movesync.rostersync.repository.OfficeRepository;
import com.movesync.rostersync.repository.OutboxEventRepository;
import com.movesync.rostersync.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class SyncServiceIntegrationTest {

    @Autowired
    private SyncService syncService;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private OfficeRepository officeRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    private Tenant tenant;
    private Office office;

    @BeforeEach
    void setUp() {
        outboxEventRepository.deleteAll();
        employeeRepository.deleteAll();
        officeRepository.deleteAll();
        tenantRepository.deleteAll();

        tenant = tenantRepository.save(new Tenant("sync-corp", "Sync Corp", 0.30));
        office = officeRepository.save(new Office(tenant, "BLR-01", "Bangalore Tech Park", "Bangalore"));
    }

    @Test
    @DisplayName("Should create employee and generate EMPLOYEE_CREATED outbox event")
    void shouldCreateEmployeeAndOutbox() {
        SyncRecordDto createRecord = new SyncRecordDto(
                "EMP-100", 1L, EventType.CREATE, "Alice Walker", "alice.w@synccorp.com",
                "+919876543210", null, "BLR-01", "Engineering", "DAY"
        );
        SyncBatchRequest request = new SyncBatchRequest(Collections.singletonList(createRecord));

        BatchSyncResponse response = syncService.processBatch(tenant.getId(), request);

        assertThat(response.getTotalEvents()).isEqualTo(1);
        assertThat(response.getAppliedCount()).isEqualTo(1);
        assertThat(response.getResults().get(0).getStatus()).isEqualTo("APPLIED");

        // Verify DB entity
        Optional<Employee> empOpt = employeeRepository.findByTenantIdAndSourceEmployeeId(tenant.getId(), "EMP-100");
        assertThat(empOpt).isPresent();
        assertThat(empOpt.get().getName()).isEqualTo("Alice Walker");
        assertThat(empOpt.get().getSourceVersion()).isEqualTo(1L);
        assertThat(empOpt.get().getStatus()).isEqualTo(EmployeeStatus.ACTIVE);

        // Verify Outbox
        List<OutboxEvent> outboxList = outboxEventRepository.findAll();
        assertThat(outboxList).hasSize(1);
        assertThat(outboxList.get(0).getEventType()).isEqualTo("EMPLOYEE_CREATED");
        assertThat(outboxList.get(0).getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(outboxList.get(0).getPayload()).contains("alice.w@synccorp.com");
    }

    @Test
    @DisplayName("Should update existing employee with higher version and emit EMPLOYEE_UPDATED outbox event")
    void shouldUpdateEmployeeWithHigherVersion() {
        // First create at v1
        syncService.processBatch(tenant.getId(), new SyncBatchRequest(Collections.singletonList(
                new SyncRecordDto("EMP-101", 1L, EventType.CREATE, "Bob Initial", "bob@synccorp.com",
                        null, null, "BLR-01", "Sales", "DAY")
        )));

        // Then update at v2
        SyncRecordDto updateRecord = new SyncRecordDto(
                "EMP-101", 2L, EventType.UPDATE, "Bob Promoted", "bob.promoted@synccorp.com",
                null, null, "BLR-01", "Marketing", "NIGHT"
        );
        BatchSyncResponse response = syncService.processBatch(tenant.getId(), new SyncBatchRequest(Collections.singletonList(updateRecord)));

        assertThat(response.getAppliedCount()).isEqualTo(1);
        Employee emp = employeeRepository.findByTenantIdAndSourceEmployeeId(tenant.getId(), "EMP-101").orElseThrow();
        assertThat(emp.getName()).isEqualTo("Bob Promoted");
        assertThat(emp.getSourceVersion()).isEqualTo(2L);
        assertThat(emp.getDepartment()).isEqualTo("Marketing");

        // Outbox must contain CREATE and then UPDATED
        List<OutboxEvent> outboxList = outboxEventRepository.findAll();
        assertThat(outboxList).hasSize(2);
        assertThat(outboxList.get(1).getEventType()).isEqualTo("EMPLOYEE_UPDATED");
    }

    @Test
    @DisplayName("Out-of-order arrival test: V7 received before V6 keeps V7 state and ignores V6 as stale")
    void shouldHandleOutOfOrderArrival() {
        // Step 1: V7 arrives first
        SyncRecordDto v7 = new SyncRecordDto("EMP-200", 7L, EventType.CREATE, "Charlie V7", "charlie@synccorp.com",
                null, null, "BLR-01", "Design", "DAY");
        BatchSyncResponse r7 = syncService.processBatch(tenant.getId(), new SyncBatchRequest(Collections.singletonList(v7)));
        assertThat(r7.getAppliedCount()).isEqualTo(1);

        // Verify V7 is committed
        Employee empAfterV7 = employeeRepository.findByTenantIdAndSourceEmployeeId(tenant.getId(), "EMP-200").orElseThrow();
        assertThat(empAfterV7.getName()).isEqualTo("Charlie V7");
        assertThat(empAfterV7.getSourceVersion()).isEqualTo(7L);

        // Step 2: Delayed V6 arrives
        SyncRecordDto v6 = new SyncRecordDto("EMP-200", 6L, EventType.UPDATE, "Charlie V6 Stale", "charlie.old@synccorp.com",
                null, null, "BLR-01", "Design", "DAY");
        BatchSyncResponse r6 = syncService.processBatch(tenant.getId(), new SyncBatchRequest(Collections.singletonList(v6)));

        // Verify V6 was rejected as stale
        assertThat(r6.getIgnoredCount()).isEqualTo(1);
        assertThat(r6.getResults().get(0).getStatus()).isEqualTo("IGNORED_STALE");

        // Verify state is still V7
        Employee empFinal = employeeRepository.findByTenantIdAndSourceEmployeeId(tenant.getId(), "EMP-200").orElseThrow();
        assertThat(empFinal.getName()).isEqualTo("Charlie V7");
        assertThat(empFinal.getSourceVersion()).isEqualTo(7L);
    }

    @Test
    @DisplayName("Duplicate event test: Replaying the exact same version is a zero-op")
    void shouldIgnoreDuplicateEvent() {
        SyncRecordDto v3 = new SyncRecordDto("EMP-300", 3L, EventType.CREATE, "David", "david@synccorp.com",
                null, null, "BLR-01", "Legal", "DAY");
        syncService.processBatch(tenant.getId(), new SyncBatchRequest(Collections.singletonList(v3)));

        // Replay exact same version
        BatchSyncResponse replay = syncService.processBatch(tenant.getId(), new SyncBatchRequest(Collections.singletonList(v3)));

        assertThat(replay.getIgnoredCount()).isEqualTo(1);
        assertThat(replay.getResults().get(0).getStatus()).isEqualTo("IGNORED_DUPLICATE");
    }

    @Test
    @DisplayName("Deactivation test: Transitions status to DEACTIVATED and emits EMPLOYEE_DEACTIVATED outbox event")
    void shouldDeactivateEmployeeAndEmitOutbox() {
        // Create at v1
        syncService.processBatch(tenant.getId(), new SyncBatchRequest(Collections.singletonList(
                new SyncRecordDto("EMP-400", 1L, EventType.CREATE, "Eva", "eva@synccorp.com",
                        null, null, "BLR-01", "HR", "DAY")
        )));

        // Deactivate at v2
        SyncRecordDto deact = new SyncRecordDto();
        deact.setSourceEmployeeId("EMP-400");
        deact.setVersion(2L);
        deact.setEventType(EventType.DEACTIVATE);

        BatchSyncResponse deactResponse = syncService.processBatch(tenant.getId(), new SyncBatchRequest(Collections.singletonList(deact)));
        assertThat(deactResponse.getAppliedCount()).isEqualTo(1);

        Employee emp = employeeRepository.findByTenantIdAndSourceEmployeeId(tenant.getId(), "EMP-400").orElseThrow();
        assertThat(emp.getStatus()).isEqualTo(EmployeeStatus.DEACTIVATED);
        assertThat(emp.getDeactivatedAt()).isNotNull();

        List<OutboxEvent> outboxList = outboxEventRepository.findAll();
        assertThat(outboxList).hasSize(2);
        assertThat(outboxList.get(1).getEventType()).isEqualTo("EMPLOYEE_DEACTIVATED");
    }

    @Test
    @DisplayName("Transport update test: updates transit attributes and creates EMPLOYEE_TRANSPORT_UPDATED event")
    void shouldUpdateTransportFields() {
        syncService.processBatch(tenant.getId(), new SyncBatchRequest(Collections.singletonList(
                new SyncRecordDto("EMP-500", 1L, EventType.CREATE, "Frank", "frank@synccorp.com",
                        null, null, "BLR-01", "Ops", "DAY")
        )));

        TransportFieldUpdateRequest transportReq = new TransportFieldUpdateRequest(
                "Koramangala 4th Block", "Office Tower B", "None"
        );

        EmployeeResponseDto res = syncService.updateTransportFields(tenant.getId(), "EMP-500", transportReq);

        assertThat(res.getPickupPoint()).isEqualTo("Koramangala 4th Block");
        assertThat(res.getDropPoint()).isEqualTo("Office Tower B");
        assertThat(res.getTransportFieldVersion()).isEqualTo(1L);

        List<OutboxEvent> outboxList = outboxEventRepository.findAll();
        assertThat(outboxList).hasSize(2);
        assertThat(outboxList.get(1).getEventType()).isEqualTo("EMPLOYEE_TRANSPORT_UPDATED");
    }

    @Test
    @DisplayName("Timestamp-based versioning: Supports epoch millisecond versions and monotonic ordering")
    void shouldSupportTimestampBasedVersionOrdering() {
        long t1 = 1730000000000L; // T1
        long t2 = 1730000060000L; // T2 (1 min later)
        long tStale = 1730000030000L; // T_stale (arrives late)

        // 1. Initial creation at T1
        SyncRecordDto recT1 = new SyncRecordDto("EMP-600", t1, EventType.CREATE, "Grace", "grace@synccorp.com",
                null, null, "BLR-01", "QA", "DAY");
        BatchSyncResponse resT1 = syncService.processBatch(tenant.getId(), new SyncBatchRequest(Collections.singletonList(recT1)));
        assertThat(resT1.getAppliedCount()).isEqualTo(1);

        // 2. Update at T2
        SyncRecordDto recT2 = new SyncRecordDto("EMP-600", t2, EventType.UPDATE, "Grace Promoted", "grace@synccorp.com",
                null, null, "BLR-01", "QA Lead", "DAY");
        BatchSyncResponse resT2 = syncService.processBatch(tenant.getId(), new SyncBatchRequest(Collections.singletonList(recT2)));
        assertThat(resT2.getAppliedCount()).isEqualTo(1);

        // 3. Delayed update with timestamp between T1 and T2 arrives late
        SyncRecordDto recStale = new SyncRecordDto("EMP-600", tStale, EventType.UPDATE, "Grace Stale", "grace@synccorp.com",
                null, null, "BLR-01", "QA Mid", "DAY");
        BatchSyncResponse resStale = syncService.processBatch(tenant.getId(), new SyncBatchRequest(Collections.singletonList(recStale)));
        assertThat(resStale.getIgnoredCount()).isEqualTo(1);
        assertThat(resStale.getResults().get(0).getStatus()).isEqualTo("IGNORED_STALE");

        // Verify stored state remains at T2
        Employee emp = employeeRepository.findByTenantIdAndSourceEmployeeId(tenant.getId(), "EMP-600").orElseThrow();
        assertThat(emp.getName()).isEqualTo("Grace Promoted");
        assertThat(emp.getSourceVersion()).isEqualTo(t2);
    }

    @Test
    @DisplayName("Concurrent update test: Multiple concurrent threads updating same employee are serialized by row lock")
    void shouldSafelyHandleConcurrentUpdatesOnSameEmployee() throws InterruptedException {
        // Create initial employee at version 1
        syncService.processBatch(tenant.getId(), new SyncBatchRequest(Collections.singletonList(
                new SyncRecordDto("EMP-700", 1L, EventType.CREATE, "Harry", "harry@synccorp.com",
                        null, null, "BLR-01", "IT", "DAY")
        )));

        int threadCount = 5;
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(threadCount);
        java.util.concurrent.CountDownLatch startLatch = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch doneLatch = new java.util.concurrent.CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final long v = 10L + i; // versions 10, 11, 12, 13, 14
            executor.submit(() -> {
                try {
                    startLatch.await();
                    SyncRecordDto update = new SyncRecordDto("EMP-700", v, EventType.UPDATE, "Harry V" + v,
                            "harry@synccorp.com", null, null, "BLR-01", "IT", "DAY");
                    syncService.processBatch(tenant.getId(), new SyncBatchRequest(Collections.singletonList(update)));
                } catch (Exception e) {
                    // Logged
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Fire all threads simultaneously
        startLatch.countDown();
        boolean completedInTime = doneLatch.await(10, java.util.concurrent.TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completedInTime).isTrue();

        // The final version in the database must be 14 (the highest version applied)
        Employee emp = employeeRepository.findByTenantIdAndSourceEmployeeId(tenant.getId(), "EMP-700").orElseThrow();
        assertThat(emp.getSourceVersion()).isEqualTo(14L);
        assertThat(emp.getName()).isEqualTo("Harry V14");
    }
}

