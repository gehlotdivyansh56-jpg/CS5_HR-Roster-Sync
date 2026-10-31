package com.movesync.rostersync.service;

import com.movesync.rostersync.auth.JwtTokenProvider;
import com.movesync.rostersync.domain.entity.Employee;
import com.movesync.rostersync.domain.entity.Office;
import com.movesync.rostersync.domain.entity.Tenant;
import com.movesync.rostersync.domain.enums.EventType;
import com.movesync.rostersync.dto.BatchSyncResponse;
import com.movesync.rostersync.dto.SyncBatchRequest;
import com.movesync.rostersync.dto.SyncRecordDto;
import com.movesync.rostersync.dto.SyncRecordResult;
import com.movesync.rostersync.repository.EmployeeRepository;
import com.movesync.rostersync.repository.OfficeRepository;
import com.movesync.rostersync.repository.TenantRepository;
import com.movesync.rostersync.util.HashUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ConcurrencyAndLoadIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SyncService syncService;

    @Autowired
    private SingleEventSyncProcessor singleEventSyncProcessor;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private OfficeRepository officeRepository;

    @Autowired
    private TenantRateLimiterService rateLimiterService;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private Tenant tenant;
    private Office office;
    private String hrToken;

    @BeforeEach
    void setUp() {
        rateLimiterService.clear();
        rateLimiterService.setCapacity(500); // default high capacity
        employeeRepository.deleteAll();
        officeRepository.deleteAll();
        tenantRepository.deleteAll();

        tenant = tenantRepository.save(new Tenant("concurcorp", "Concurrency Corp", 0.30));
        office = officeRepository.save(new Office(tenant, "BLR-01", "Bengaluru Tech Park", "Bengaluru"));
        hrToken = "Bearer " + jwtTokenProvider.generateToken("hr-service", tenant.getId(), "HR_SYSTEM");
    }

    @Test
    @DisplayName("5.6: 10 parallel threads updating same employee concurrently - zero lost updates, final version = 29")
    void shouldHandle10ConcurrentThreadsOnSameEmployeeWithoutLostUpdates() throws InterruptedException, ExecutionException {
        String empId = "EMP-CONCUR-10";
        String initialHash = HashUtil.computeHrRecordHash("Initial Name", "emp.concur@corp.com", null, null, "BLR-01", "Engineering", "DAY", "ACTIVE");
        Employee initialEmp = new Employee(tenant, empId, 1L, "Initial Name", "emp.concur@corp.com", initialHash);
        initialEmp.setOffice(office);
        employeeRepository.save(initialEmp);

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<SyncRecordResult>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            final long v = 20L + i; // versions 20 to 29
            final int idx = i;
            futures.add(executor.submit(() -> {
                startLatch.await(); // ensure simultaneous execution
                SyncRecordDto record = new SyncRecordDto();
                record.setSourceEmployeeId(empId);
                record.setName("Name " + idx);
                record.setEmail("emp.concur@corp.com");
                record.setOfficeCode("BLR-01");
                record.setDepartment("Dept " + idx);
                record.setShift("DAY");
                record.setVersion(v);
                record.setEventType(EventType.UPDATE);
                return singleEventSyncProcessor.processSingleEvent(tenant.getId(), record);
            }));
        }

        // Release all threads simultaneously
        startLatch.countDown();

        int applied = 0;
        int ignored = 0;
        for (Future<SyncRecordResult> f : futures) {
            SyncRecordResult result = f.get();
            if ("APPLIED".equals(result.getStatus())) {
                applied++;
            } else if ("IGNORED_STALE".equals(result.getStatus()) || "IGNORED_DUPLICATE".equals(result.getStatus())) {
                ignored++;
            }
        }
        executor.shutdown();

        // All 10 requests must either apply or be rejected as stale; 0 unexpected errors
        assertThat(applied + ignored).isEqualTo(threadCount);
        assertThat(applied).isGreaterThan(0);

        // Final state in DB must reflect the highest version (version 29)
        Employee finalEmp = employeeRepository.findByTenantIdAndSourceEmployeeId(tenant.getId(), empId).orElseThrow();
        assertThat(finalEmp.getSourceVersion()).isEqualTo(29L);
    }

    @Test
    @DisplayName("5.7: Tenant rate limiting rejects excessive requests with HTTP 429 and Retry-After")
    void shouldRateLimitExcessiveIngestionRequests() throws Exception {
        // Set capacity to 2 requests
        rateLimiterService.setCapacity(2);

        String jsonPayload = """
                {
                    "events": [
                        {
                            "source_employee_id": "EMP-RL-1",
                            "name": "Rate User",
                            "email": "rl@corp.com",
                            "office_code": "BLR-01",
                            "version": 1,
                            "event_type": "CREATE"
                        }
                    ]
                }
                """;

        // Request 1: OK
        mockMvc.perform(post("/api/v1/sync/events")
                        .header("Authorization", hrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isMultiStatus());

        // Request 2: OK
        mockMvc.perform(post("/api/v1/sync/events")
                        .header("Authorization", hrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isMultiStatus());

        // Request 3: Exceeds capacity -> 429 Too Many Requests
        mockMvc.perform(post("/api/v1/sync/events")
                        .header("Authorization", hrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    @DisplayName("5.7 & Benchmark: Stress test processing 500 events in a batch with sub-20ms per record")
    void shouldProcessLargeBatchOf500EventsWithHighThroughput() {
        int batchSize = 500;
        List<SyncRecordDto> batch = new ArrayList<>(batchSize);
        for (int i = 1; i <= batchSize; i++) {
            SyncRecordDto dto = new SyncRecordDto();
            dto.setSourceEmployeeId("STRESS-" + i);
            dto.setName("Stress User " + i);
            dto.setEmail("stress" + i + "@corp.com");
            dto.setOfficeCode("BLR-01");
            dto.setDepartment("Engineering");
            dto.setShift("DAY");
            dto.setVersion(1L);
            dto.setEventType(EventType.CREATE);
            batch.add(dto);
        }

        long start = System.currentTimeMillis();
        BatchSyncResponse response = syncService.processBatch(tenant.getId(), new SyncBatchRequest(batch));
        long elapsedMs = System.currentTimeMillis() - start;

        assertThat(response.getResults()).hasSize(batchSize);
        long appliedCount = response.getResults().stream().filter(r -> "APPLIED".equals(r.getStatus())).count();
        assertThat(appliedCount).isEqualTo(batchSize);

        // Throughput assertion: 500 records processed in bounded time (< 15000ms) with avg latency < 20ms
        assertThat(elapsedMs).isLessThan(15000);
        double avgLatencyPerRecordMs = (double) elapsedMs / batchSize;
        assertThat(avgLatencyPerRecordMs).isLessThan(20.0);
    }
}
