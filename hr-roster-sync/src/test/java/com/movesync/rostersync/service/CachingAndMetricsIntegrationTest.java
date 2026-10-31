package com.movesync.rostersync.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.movesync.rostersync.auth.JwtTokenProvider;
import com.movesync.rostersync.domain.entity.Office;
import com.movesync.rostersync.domain.entity.Tenant;
import com.movesync.rostersync.dto.SyncBatchRequest;
import com.movesync.rostersync.dto.SyncRecordDto;
import com.movesync.rostersync.repository.OfficeRepository;
import com.movesync.rostersync.repository.TenantRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CachingAndMetricsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ValidationService validationService;

    @Autowired
    private EmployeeVersionCacheService versionCacheService;

    @Autowired
    @Qualifier("officeExistenceCache")
    private Cache<Object, Object> officeExistenceCache;

    @Autowired
    private com.movesync.rostersync.repository.EmployeeRepository employeeRepository;

    @Autowired
    private com.movesync.rostersync.repository.OutboxEventRepository outboxEventRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private OfficeRepository officeRepository;

    @Autowired
    private SyncService syncService;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private Tenant tenant;
    private Office office;
    private String adminToken;

    @BeforeEach
    void setUp() {
        officeExistenceCache.invalidateAll();
        versionCacheService.clear();

        outboxEventRepository.deleteAll();
        employeeRepository.deleteAll();
        officeRepository.deleteAll();
        tenantRepository.deleteAll();

        tenant = tenantRepository.save(new Tenant("cachecorp", "Cache Corp", 0.30));
        office = officeRepository.save(new Office(tenant, "BLR-01", "Bengaluru Tech Park", "Bengaluru"));

        adminToken = "Bearer " + jwtTokenProvider.generateToken("admin-user", tenant.getId(), "ADMIN");
    }

    @Test
    @DisplayName("5.4: Office code Caffeine cache reduces DB hits (hit rate > 80% on repeated queries)")
    void shouldCacheOfficeCodeLookupsAndAchieveHighHitRate() {
        var initialStats = officeExistenceCache.stats();

        // Query the same office code 10 times
        for (int i = 0; i < 10; i++) {
            boolean valid = validationService.isOfficeValid(tenant.getId(), "BLR-01");
            assertThat(valid).isTrue();
        }

        var finalStats = officeExistenceCache.stats();
        long requests = finalStats.requestCount() - initialStats.requestCount();
        long hits = finalStats.hitCount() - initialStats.hitCount();
        long misses = finalStats.missCount() - initialStats.missCount();

        assertThat(requests).isEqualTo(10L);
        assertThat(hits).isEqualTo(9L); // 1 miss (first query) + 9 hits
        assertThat(misses).isEqualTo(1L);
        assertThat((double) hits / requests).isGreaterThan(0.80);
    }

    @Test
    @DisplayName("5.5 & 5.6: Employee version write-through caching updates cache on write with 0ms staleness")
    void shouldUpdateVersionCacheOnWriteThrough() {
        String empId = "EMP-VCACHE-01";
        SyncRecordDto createRecord = new SyncRecordDto();
        createRecord.setSourceEmployeeId(empId);
        createRecord.setName("Cache User");
        createRecord.setEmail("cache.user@corp.com");
        createRecord.setVersion(1L);
        createRecord.setEventType(com.movesync.rostersync.domain.enums.EventType.CREATE);
        createRecord.setOfficeCode("BLR-01");

        syncService.processBatch(tenant.getId(), new SyncBatchRequest(List.of(createRecord)));

        // Verify version cache immediately returns version 1L without DB hit
        Long cachedVersion = versionCacheService.getVersion(tenant.getId(), empId);
        assertThat(cachedVersion).isEqualTo(1L);

        // Update employee to version 2
        SyncRecordDto updateRecord = new SyncRecordDto();
        updateRecord.setSourceEmployeeId(empId);
        updateRecord.setName("Cache User Updated");
        updateRecord.setEmail("cache.user@corp.com");
        updateRecord.setVersion(2L);
        updateRecord.setEventType(com.movesync.rostersync.domain.enums.EventType.UPDATE);
        updateRecord.setOfficeCode("BLR-01");

        syncService.processBatch(tenant.getId(), new SyncBatchRequest(List.of(updateRecord)));

        // Verify write-through cache immediately reflects version 2L
        Long updatedCache = versionCacheService.getVersion(tenant.getId(), empId);
        assertThat(updatedCache).isEqualTo(2L);
    }

    @Test
    @DisplayName("5.4 & 5.5: Admin /api/v1/admin/cache/invalidate flushes all Caffeine caches")
    void shouldInvalidateAllCachesViaAdminEndpoint() throws Exception {
        // Populate both caches
        validationService.isOfficeValid(tenant.getId(), "BLR-01");
        versionCacheService.putVersion(tenant.getId(), "EMP-TEST", 5L);

        assertThat(officeExistenceCache.estimatedSize()).isGreaterThan(0);
        assertThat(versionCacheService.getEstimatedSize()).isGreaterThan(0);

        mockMvc.perform(post("/api/v1/admin/cache/invalidate")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.officeCacheEstimatedSize").value(0))
                .andExpect(jsonPath("$.versionCacheEstimatedSize").value(0));

        assertThat(officeExistenceCache.estimatedSize()).isEqualTo(0);
        assertThat(versionCacheService.getEstimatedSize()).isEqualTo(0);
    }

    @Test
    @DisplayName("5.1 & 5.2: Prometheus /actuator/prometheus exposes custom metrics from Section 13.1")
    void shouldExposeCustomMetricsOnPrometheusEndpoint() throws Exception {
        // Ingest an event to increment metrics
        SyncRecordDto record = new SyncRecordDto();
        record.setSourceEmployeeId("EMP-METRIC-01");
        record.setName("Metric User");
        record.setEmail("metric@corp.com");
        record.setVersion(1L);
        record.setEventType(com.movesync.rostersync.domain.enums.EventType.CREATE);
        record.setOfficeCode("BLR-01");
        syncService.processBatch(tenant.getId(), new SyncBatchRequest(List.of(record)));

        // Scrape Prometheus endpoint
        String response = mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Verify core metrics from specification
        assertThat(response).contains("sync_events_received_total");
        assertThat(response).contains("sync_events_applied_total");
        assertThat(response).contains("sync_events_processing_time_seconds");
        assertThat(response).contains("outbox_pending_count");
        assertThat(response).contains("cache_office_hit_rate");
        assertThat(response).contains("cache_version_hit_rate");
    }
}