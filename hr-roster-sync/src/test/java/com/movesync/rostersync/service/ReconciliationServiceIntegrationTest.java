package com.movesync.rostersync.service;

import com.movesync.rostersync.domain.entity.*;
import com.movesync.rostersync.domain.enums.EmployeeStatus;
import com.movesync.rostersync.domain.enums.EventType;
import com.movesync.rostersync.domain.enums.ReconciliationStatus;
import com.movesync.rostersync.dto.*;
import com.movesync.rostersync.repository.*;
import com.movesync.rostersync.util.HashUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class ReconciliationServiceIntegrationTest {

    @Autowired
    private ReconciliationService reconciliationService;

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

    @Autowired
    private ReconciliationRunRepository reconciliationRunRepository;

    @Autowired
    private ReconciliationItemRepository reconciliationItemRepository;

    private Tenant tenant;
    private Office office;

    @BeforeEach
    void setUp() {
        reconciliationItemRepository.deleteAll();
        reconciliationRunRepository.deleteAll();
        outboxEventRepository.deleteAll();
        employeeRepository.deleteAll();
        officeRepository.deleteAll();
        tenantRepository.deleteAll();

        tenant = tenantRepository.save(new Tenant("uber", "Uber Technologies", 0.30));
        office = officeRepository.save(new Office(tenant, "BLR-01", "Uber Bangalore Hub", "Bangalore"));
    }

    @Test
    @DisplayName("Identical file diff test: Matching state produces 0 creates, 0 updates, 0 deactivations, 2 skipped")
    void shouldPerformIdenticalFileDiffWithZeroChanges() throws IOException {
        // Pre-populate DB with Alice & Bob
        String hashAlice = HashUtil.computeHrRecordHash("Alice", "alice@uber.com", "+12025550101", null, "BLR-01", "Engineering", "DAY", "ACTIVE");
        Employee empAlice = new Employee(tenant, "EMP-001", 1L, "Alice", "alice@uber.com", hashAlice);
        empAlice.setPhone("+12025550101");
        empAlice.setOffice(office);
        empAlice.setDepartment("Engineering");
        empAlice.setShift("DAY");
        employeeRepository.save(empAlice);

        String hashBob = HashUtil.computeHrRecordHash("Bob", "bob@uber.com", "+12025550102", null, "BLR-01", "Design", "DAY", "ACTIVE");
        Employee empBob = new Employee(tenant, "EMP-002", 1L, "Bob", "bob@uber.com", hashBob);
        empBob.setPhone("+12025550102");
        empBob.setOffice(office);
        empBob.setDepartment("Design");
        empBob.setShift("DAY");
        employeeRepository.save(empBob);

        // Upload CSV identical to DB state
        String csv = """
                source_employee_id,name,email,phone,manager_source_id,office_code,department,shift
                EMP-001,Alice,alice@uber.com,+12025550101,,BLR-01,Engineering,DAY
                EMP-002,Bob,bob@uber.com,+12025550102,,BLR-01,Design,DAY
                """;

        ReconciliationRunResponse response = reconciliationService.reconcile(
                tenant.getId(),
                new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)),
                100L
        );

        assertThat(response.getStatus()).isEqualTo("COMPLETED");
        assertThat(response.getTotalInFile()).isEqualTo(2);
        assertThat(response.getTotalInStore()).isEqualTo(2);
        assertThat(response.getAddedCount()).isEqualTo(0);
        assertThat(response.getUpdatedCount()).isEqualTo(0);
        assertThat(response.getDeactivatedCount()).isEqualTo(0);
        assertThat(response.getSkippedCount()).isEqualTo(2);

        // Zero outbox events generated since nothing changed
        assertThat(outboxEventRepository.count()).isEqualTo(0);
    }

    @Test
    @DisplayName("Delta update test: Modified HR field triggers update with epoch version and outbox event")
    void shouldApplyDeltaUpdatesAndNewJoiners() throws IOException {
        String hashAlice = HashUtil.computeHrRecordHash("Alice", "alice@uber.com", null, null, "BLR-01", "Sales", "DAY", "ACTIVE");
        Employee empAlice = new Employee(tenant, "EMP-001", 1L, "Alice", "alice@uber.com", hashAlice);
        empAlice.setOffice(office);
        empAlice.setDepartment("Sales");
        employeeRepository.save(empAlice);

        // CSV updates Alice's department to "Cybersecurity" and adds new joiner Charlie
        String csv = """
                source_employee_id,name,email,phone,manager_source_id,office_code,department,shift
                EMP-001,Alice,alice@uber.com,,,BLR-01,Cybersecurity,DAY
                EMP-003,Charlie,charlie@uber.com,+12025550103,,BLR-01,Product,DAY
                """;

        long epochVersion = 1730000000000L;
        ReconciliationRunResponse response = reconciliationService.reconcile(
                tenant.getId(),
                new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)),
                epochVersion
        );

        assertThat(response.getStatus()).isEqualTo("COMPLETED");
        assertThat(response.getAddedCount()).isEqualTo(1);
        assertThat(response.getUpdatedCount()).isEqualTo(1);

        // Verify Alice in DB
        Employee updatedAlice = employeeRepository.findByTenantIdAndSourceEmployeeId(tenant.getId(), "EMP-001").orElseThrow();
        assertThat(updatedAlice.getDepartment()).isEqualTo("Cybersecurity");
        assertThat(updatedAlice.getSourceVersion()).isEqualTo(epochVersion);

        // Verify Charlie in DB
        Employee newCharlie = employeeRepository.findByTenantIdAndSourceEmployeeId(tenant.getId(), "EMP-003").orElseThrow();
        assertThat(newCharlie.getName()).isEqualTo("Charlie");
        assertThat(newCharlie.getSourceVersion()).isEqualTo(epochVersion);

        // Verify Outbox events: EMPLOYEE_UPDATED (Alice) and EMPLOYEE_CREATED (Charlie)
        List<OutboxEvent> outboxList = outboxEventRepository.findAll();
        assertThat(outboxList).hasSize(2);
        assertThat(outboxList.stream().anyMatch(e -> "EMPLOYEE_UPDATED".equals(e.getEventType()))).isTrue();
        assertThat(outboxList.stream().anyMatch(e -> "EMPLOYEE_CREATED".equals(e.getEventType()))).isTrue();
    }

    @Test
    @DisplayName("Truncated file test: 60% missing records triggers Safety Brake; zero employees deactivated")
    void shouldTriggerSafetyBrakeOnTruncatedFile() throws IOException {
        // Pre-populate 5 employees
        for (int i = 1; i <= 5; i++) {
            String empId = "EMP-00" + i;
            String hash = HashUtil.computeHrRecordHash("Emp" + i, "emp" + i + "@uber.com", null, null, "BLR-01", "IT", "DAY", "ACTIVE");
            Employee emp = new Employee(tenant, empId, 1L, "Emp" + i, "emp" + i + "@uber.com", hash);
            emp.setOffice(office);
            employeeRepository.save(emp);
        }

        // Nightly file is truncated! Only has 2 of 5 employees (missing 3/5 = 60% > 30% threshold)
        String truncatedCsv = """
                source_employee_id,name,email,phone,manager_source_id,office_code,department,shift
                EMP-001,Emp1,emp1@uber.com,,,BLR-01,IT,DAY
                EMP-002,Emp2,emp2@uber.com,,,BLR-01,IT,DAY
                """;

        ReconciliationRunResponse response = reconciliationService.reconcile(
                tenant.getId(),
                new ByteArrayInputStream(truncatedCsv.getBytes(StandardCharsets.UTF_8)),
                200L
        );

        // Safety brake must trigger
        assertThat(response.getStatus()).isEqualTo("BRAKE_TRIGGERED");
        assertThat(response.isBrakeTriggered()).isTrue();
        assertThat(response.getMissingRatio()).isEqualTo(0.60);
        assertThat(response.getDeactivatedCount()).isEqualTo(0);

        // Verify that NO employees were deactivated; all 5 remain ACTIVE in database!
        for (int i = 1; i <= 5; i++) {
            Employee emp = employeeRepository.findByTenantIdAndSourceEmployeeId(tenant.getId(), "EMP-00" + i).orElseThrow();
            assertThat(emp.getStatus()).isEqualTo(EmployeeStatus.ACTIVE);
            assertThat(emp.getDeactivatedAt()).isNull();
        }

        // Verify outbox contains 0 deactivation events
        assertThat(outboxEventRepository.count()).isEqualTo(0);
    }

    @Test
    @DisplayName("Operator approval test: Calling approveRun resumes deactivation of held records")
    void shouldAllowOperatorToApproveBrakeTriggeredRun() throws IOException {
        // Pre-populate 4 employees
        for (int i = 1; i <= 4; i++) {
            String empId = "EMP-0" + i;
            String hash = HashUtil.computeHrRecordHash("Emp" + i, "emp" + i + "@uber.com", null, null, "BLR-01", "IT", "DAY", "ACTIVE");
            Employee emp = new Employee(tenant, empId, 1L, "Emp" + i, "emp" + i + "@uber.com", hash);
            employeeRepository.save(emp);
        }

        // Export only contains EMP-01 (missing 3/4 = 75% missing > 30% threshold)
        String csv = """
                source_employee_id,name,email,phone,manager_source_id,office_code,department,shift
                EMP-01,Emp1,emp1@uber.com,,,BLR-01,IT,DAY
                """;

        ReconciliationRunResponse runResponse = reconciliationService.reconcile(
                tenant.getId(),
                new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)),
                500L
        );
        assertThat(runResponse.getStatus()).isEqualTo("BRAKE_TRIGGERED");

        // Operator explicitly reviews and approves the run
        ReconciliationRunResponse approvedResponse = reconciliationService.approveRun(
                tenant.getId(),
                runResponse.getRunId(),
                "operator-divyansh"
        );

        assertThat(approvedResponse.getStatus()).isEqualTo("APPROVED");
        assertThat(approvedResponse.getApprovedBy()).isEqualTo("operator-divyansh");
        assertThat(approvedResponse.getDeactivatedCount()).isEqualTo(3);

        // Verify that the 3 missing employees are now DEACTIVATED
        assertThat(employeeRepository.findByTenantIdAndSourceEmployeeId(tenant.getId(), "EMP-02").get().getStatus()).isEqualTo(EmployeeStatus.DEACTIVATED);
        assertThat(employeeRepository.findByTenantIdAndSourceEmployeeId(tenant.getId(), "EMP-03").get().getStatus()).isEqualTo(EmployeeStatus.DEACTIVATED);
        assertThat(employeeRepository.findByTenantIdAndSourceEmployeeId(tenant.getId(), "EMP-04").get().getStatus()).isEqualTo(EmployeeStatus.DEACTIVATED);

        // Verify EMPLOYEE_DEACTIVATED outbox events were generated
        assertThat(outboxEventRepository.count()).isEqualTo(3);
    }

    @Test
    @DisplayName("Stale daytime shield test: Daytime event arriving after reconciliation is rejected as stale")
    void shouldShieldAgainstStaleDaytimeUpdatesAfterReconciliation() throws IOException {
        String hash = HashUtil.computeHrRecordHash("Alice", "alice@uber.com", null, null, "BLR-01", "Security", "DAY", "ACTIVE");
        Employee emp = new Employee(tenant, "EMP-900", 1L, "Alice", "alice@uber.com", hash);
        employeeRepository.save(emp);

        // Nightly reconciliation runs at epoch timestamp 1730000000000L
        long nightlyFileVersion = 1730000000000L;
        String csv = """
                source_employee_id,name,email,phone,manager_source_id,office_code,department,shift
                EMP-900,Alice,alice@uber.com,,,BLR-01,Security Architecture,DAY
                """;
        reconciliationService.reconcile(
                tenant.getId(),
                new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)),
                nightlyFileVersion
        );

        // Verify DB version is updated to nightlyFileVersion
        Employee afterRecon = employeeRepository.findByTenantIdAndSourceEmployeeId(tenant.getId(), "EMP-900").orElseThrow();
        assertThat(afterRecon.getSourceVersion()).isEqualTo(nightlyFileVersion);

        // Daytime update that was delayed in transit arrives with daytime version (e.g. version 5 or older epoch)
        SyncRecordDto delayedDaytime = new SyncRecordDto(
                "EMP-900", 5L, EventType.UPDATE, "Alice Daytime", "alice@uber.com",
                null, null, "BLR-01", "Old Dep", "NIGHT"
        );

        BatchSyncResponse syncResponse = syncService.processBatch(tenant.getId(), new SyncBatchRequest(Collections.singletonList(delayedDaytime)));

        // Must be rejected as IGNORED_STALE
        assertThat(syncResponse.getIgnoredCount()).isEqualTo(1);
        assertThat(syncResponse.getResults().get(0).getStatus()).isEqualTo("IGNORED_STALE");

        // Stored state remains the nightly reconciliation state
        Employee finalEmp = employeeRepository.findByTenantIdAndSourceEmployeeId(tenant.getId(), "EMP-900").orElseThrow();
        assertThat(finalEmp.getDepartment()).isEqualTo("Security Architecture");
        assertThat(finalEmp.getSourceVersion()).isEqualTo(nightlyFileVersion);
    }

    @Test
    @DisplayName("Edge Case: Re-activation of previously deactivated employee during nightly reconciliation")
    void shouldReactivateDeactivatedEmployeeInNightlyFile() throws IOException {
        // Pre-populate an employee who is already DEACTIVATED
        String hash = HashUtil.computeHrRecordHash("Rehired Emp", "rehired@uber.com", null, null, "BLR-01", "Platform", "DAY", "DEACTIVATED");
        Employee emp = new Employee(tenant, "EMP-888", 1L, "Rehired Emp", "rehired@uber.com", hash);
        emp.setStatus(EmployeeStatus.DEACTIVATED);
        emp.setDeactivatedAt(java.time.Instant.now().minusSeconds(86400));
        employeeRepository.save(emp);

        // Nightly file includes them as active
        String csv = """
                source_employee_id,name,email,phone,manager_source_id,office_code,department,shift
                EMP-888,Rehired Emp,rehired@uber.com,,,BLR-01,Platform,DAY
                """;

        long reconVersion = 1730005000000L;
        ReconciliationRunResponse res = reconciliationService.reconcile(
                tenant.getId(),
                new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)),
                reconVersion
        );

        assertThat(res.getStatus()).isEqualTo("COMPLETED");
        assertThat(res.getAddedCount()).isEqualTo(1);

        // Verify status is now ACTIVE, deactivatedAt is null, version is updated
        Employee reactivated = employeeRepository.findByTenantIdAndSourceEmployeeId(tenant.getId(), "EMP-888").orElseThrow();
        assertThat(reactivated.getStatus()).isEqualTo(EmployeeStatus.ACTIVE);
        assertThat(reactivated.getDeactivatedAt()).isNull();
        assertThat(reactivated.getSourceVersion()).isEqualTo(reconVersion);

        // Verify outbox emitted EMPLOYEE_CREATED
        List<OutboxEvent> outboxList = outboxEventRepository.findAll();
        assertThat(outboxList).hasSize(1);
        assertThat(outboxList.get(0).getEventType()).isEqualTo("EMPLOYEE_CREATED");
    }

    @Test
    @DisplayName("Edge Case: HR updates in reconciliation must strictly preserve Transport Admin fields")
    void shouldPreserveTransportFieldsDuringBulkReconciliation() throws IOException {
        String hash = HashUtil.computeHrRecordHash("Alice", "alice@uber.com", null, null, "BLR-01", "Ops", "DAY", "ACTIVE");
        Employee emp = new Employee(tenant, "EMP-777", 1L, "Alice", "alice@uber.com", hash);
        emp.setOffice(office);
        emp.setDepartment("Ops");
        emp.setPickupPoint("Whitefield Gate 1");
        emp.setDropPoint("Electronic City Hub");
        emp.setSpecialAssistance("Wheelchair");
        emp.setTransportFieldVersion(3L);
        employeeRepository.save(emp);

        // Nightly file changes HR department to "Finance"
        String csv = """
                source_employee_id,name,email,phone,manager_source_id,office_code,department,shift
                EMP-777,Alice,alice@uber.com,,,BLR-01,Finance,DAY
                """;

        reconciliationService.reconcile(
                tenant.getId(),
                new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)),
                2000L
        );

        Employee updated = employeeRepository.findByTenantIdAndSourceEmployeeId(tenant.getId(), "EMP-777").orElseThrow();
        // HR field updated
        assertThat(updated.getDepartment()).isEqualTo("Finance");
        assertThat(updated.getSourceVersion()).isEqualTo(2000L);

        // Transport Admin fields must be 100% preserved
        assertThat(updated.getPickupPoint()).isEqualTo("Whitefield Gate 1");
        assertThat(updated.getDropPoint()).isEqualTo("Electronic City Hub");
        assertThat(updated.getSpecialAssistance()).isEqualTo("Wheelchair");
        assertThat(updated.getTransportFieldVersion()).isEqualTo(3L);
    }

    @Test
    @DisplayName("Edge Case: Empty file (only headers) with active store triggers Safety Brake (100% missing)")
    void shouldTriggerSafetyBrakeOnEmptyFileWhenStoreHasEmployees() throws IOException {
        Employee emp = new Employee(tenant, "EMP-001", 1L, "Alice", "alice@uber.com", "hash1");
        employeeRepository.save(emp);

        String emptyCsv = "source_employee_id,name,email,phone,manager_source_id,office_code,department,shift\n";

        ReconciliationRunResponse res = reconciliationService.reconcile(
                tenant.getId(),
                new ByteArrayInputStream(emptyCsv.getBytes(StandardCharsets.UTF_8)),
                3000L
        );

        assertThat(res.getStatus()).isEqualTo("BRAKE_TRIGGERED");
        assertThat(res.isBrakeTriggered()).isTrue();
        assertThat(res.getMissingRatio()).isEqualTo(1.0);
        assertThat(res.getDeactivatedCount()).isEqualTo(0);

        // Alice must remain ACTIVE
        assertThat(employeeRepository.findByTenantIdAndSourceEmployeeId(tenant.getId(), "EMP-001").get().getStatus()).isEqualTo(EmployeeStatus.ACTIVE);
    }

    @Test
    @DisplayName("Edge Case: Cannot approve a run that is not in BRAKE_TRIGGERED status (throws IllegalStateException)")
    void shouldRejectDoubleApprovalOrApprovalOnCompletedRun() throws IOException {
        String csv = "source_employee_id,name,email,phone,manager_source_id,office_code,department,shift\nEMP-001,Alice,alice@uber.com,,,BLR-01,Eng,DAY\n";
        ReconciliationRunResponse res = reconciliationService.reconcile(
                tenant.getId(),
                new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)),
                100L
        );
        assertThat(res.getStatus()).isEqualTo("COMPLETED");

        // Attempting to approve a completed run must fail
        assertThatThrownBy(() -> reconciliationService.approveRun(tenant.getId(), res.getRunId(), "operator-bob"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("is not in BRAKE_TRIGGERED status");
    }

    @Test
    @DisplayName("Edge Case: Tenant isolation on run approval - Tenant B cannot approve Tenant A's run")
    void shouldPreventCrossTenantRunApproval() {
        Tenant tenantB = tenantRepository.save(new Tenant("lyft", "Lyft Inc.", 0.30));

        // Create a run under Tenant A in BRAKE_TRIGGERED status
        ReconciliationRun runA = new ReconciliationRun(tenant.getId(), 500L);
        runA.setStatus(ReconciliationStatus.BRAKE_TRIGGERED);
        reconciliationRunRepository.save(runA);

        // Tenant B operator tries to approve Tenant A's run
        assertThatThrownBy(() -> reconciliationService.approveRun(tenantB.getId(), runA.getId(), "lyft-operator"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Reconciliation run not found");
    }

    @Test
    @DisplayName("Performance Benchmark: Stream & Diff synthetic records in bounded sub-minute time")
    void shouldBenchmarkStreamingDiffPerformance() throws IOException {
        int recordCount = 500;
        StringBuilder csvBuilder = new StringBuilder(recordCount * 80);
        csvBuilder.append("source_employee_id,name,email,phone,manager_source_id,office_code,department,shift\n");
        for (int i = 1; i <= recordCount; i++) {
            csvBuilder.append("BENCH-").append(i)
                    .append(",Employee ").append(i)
                    .append(",emp").append(i).append("@uber.com,,,BLR-01,Engineering,DAY\n");
        }

        long startTime = System.currentTimeMillis();
        ReconciliationRunResponse res = reconciliationService.reconcile(
                tenant.getId(),
                new ByteArrayInputStream(csvBuilder.toString().getBytes(StandardCharsets.UTF_8)),
                99999L
        );
        long elapsed = System.currentTimeMillis() - startTime;

        assertThat(res.getStatus()).isEqualTo("COMPLETED");
        assertThat(res.getTotalInFile()).isEqualTo(recordCount);
        assertThat(res.getAddedCount()).isEqualTo(recordCount);
        assertThat(elapsed).isLessThan(15000); // Bounded execution
    }
}
