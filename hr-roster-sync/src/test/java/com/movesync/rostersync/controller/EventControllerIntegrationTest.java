package com.movesync.rostersync.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.movesync.rostersync.auth.JwtTokenProvider;
import com.movesync.rostersync.domain.entity.Office;
import com.movesync.rostersync.domain.entity.Tenant;
import com.movesync.rostersync.domain.enums.EventType;
import com.movesync.rostersync.dto.BatchSyncResponse;
import com.movesync.rostersync.dto.SyncBatchRequest;
import com.movesync.rostersync.dto.SyncRecordDto;
import com.movesync.rostersync.repository.EmployeeRepository;
import com.movesync.rostersync.repository.OfficeRepository;
import com.movesync.rostersync.repository.OutboxEventRepository;
import com.movesync.rostersync.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class EventControllerIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

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
    private String hrToken;
    private String transportAdminToken;

    @BeforeEach
    void setUp() {
        outboxEventRepository.deleteAll();
        employeeRepository.deleteAll();
        officeRepository.deleteAll();
        tenantRepository.deleteAll();

        tenant = tenantRepository.save(new Tenant("google", "Google Inc.", 0.30));
        office = officeRepository.save(new Office(tenant, "BLR-01", "Google Bangalore Hub", "Bangalore"));

        hrToken = jwtTokenProvider.generateToken("google-hr-service", tenant.getId(), "HR_SYSTEM");
        transportAdminToken = jwtTokenProvider.generateToken("google-transport-admin", tenant.getId(), "TRANSPORT_ADMIN");
    }

    @Test
    @DisplayName("Partial batch failure: 5 records (3 valid, 2 invalid) returns 207 Multi-Status, commits valid ones, redacts PII")
    void shouldHandlePartialBatchFailureWith207MultiStatus() {
        String url = "http://localhost:" + port + "/api/v1/sync/events";

        SyncRecordDto rec1Valid = new SyncRecordDto("EMP-001", 1L, EventType.CREATE, "Alice", "alice@google.com",
                "+919876543210", null, "BLR-01", "Eng", "DAY");
        SyncRecordDto rec2BadPhone = new SyncRecordDto("EMP-002", 1L, EventType.CREATE, "Bob", "bob@google.com",
                "123", null, "BLR-01", "Eng", "DAY");
        SyncRecordDto rec3Valid = new SyncRecordDto("EMP-003", 1L, EventType.CREATE, "Charlie", "charlie@google.com",
                "+919876543212", null, "BLR-01", "Sales", "DAY");
        SyncRecordDto rec4BadOffice = new SyncRecordDto("EMP-004", 1L, EventType.CREATE, "Diana", "diana@google.com",
                "+919876543213", null, "NON-EXISTENT-OFFICE", "Finance", "DAY");
        SyncRecordDto rec5Valid = new SyncRecordDto("EMP-005", 1L, EventType.CREATE, "Evan", "evan@google.com",
                null, null, "BLR-01", "HR", "DAY");

        SyncBatchRequest request = new SyncBatchRequest(Arrays.asList(rec1Valid, rec2BadPhone, rec3Valid, rec4BadOffice, rec5Valid));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(hrToken);
        HttpEntity<SyncBatchRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<BatchSyncResponse> response = restTemplate.postForEntity(url, entity, BatchSyncResponse.class);

        // HTTP status must be 207 Multi-Status
        assertThat(response.getStatusCode().value()).isEqualTo(207);
        BatchSyncResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getTotalEvents()).isEqualTo(5);
        assertThat(body.getAppliedCount()).isEqualTo(3);
        assertThat(body.getRejectedCount()).isEqualTo(2);

        // Verify rejected error message does not leak raw phone "123"
        assertThat(body.getResults().get(1).getStatus()).isEqualTo("REJECTED");
        assertThat(body.getResults().get(1).getMessage()).doesNotContain("123");
        assertThat(body.getResults().get(1).getMessage()).contains("***REDACTED***");

        // Verify that the 3 valid records ARE in the database
        assertThat(employeeRepository.findByTenantIdAndSourceEmployeeId(tenant.getId(), "EMP-001")).isPresent();
        assertThat(employeeRepository.findByTenantIdAndSourceEmployeeId(tenant.getId(), "EMP-003")).isPresent();
        assertThat(employeeRepository.findByTenantIdAndSourceEmployeeId(tenant.getId(), "EMP-005")).isPresent();

        // Verify invalid records are NOT in the database
        assertThat(employeeRepository.findByTenantIdAndSourceEmployeeId(tenant.getId(), "EMP-002")).isEmpty();
        assertThat(employeeRepository.findByTenantIdAndSourceEmployeeId(tenant.getId(), "EMP-004")).isEmpty();
    }

    @Test
    @DisplayName("Should return 403 Forbidden when caller lacks HR_SYSTEM role")
    void shouldDenyAccessToNonHrRole() {
        String url = "http://localhost:" + port + "/api/v1/sync/events";

        SyncRecordDto rec = new SyncRecordDto("EMP-010", 1L, EventType.CREATE, "Zack", "zack@google.com",
                null, null, "BLR-01", "Eng", "DAY");
        SyncBatchRequest request = new SyncBatchRequest(List.of(rec));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(transportAdminToken); // Has TRANSPORT_ADMIN, not HR_SYSTEM
        HttpEntity<SyncBatchRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("Should return 401 Unauthorized when no Authorization header provided")
    void shouldDenyAccessWhenUnauthenticated() {
        String url = "http://localhost:" + port + "/api/v1/sync/events";

        SyncRecordDto rec = new SyncRecordDto("EMP-010", 1L, EventType.CREATE, "Zack", "zack@google.com",
                null, null, "BLR-01", "Eng", "DAY");
        SyncBatchRequest request = new SyncBatchRequest(List.of(rec));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<SyncBatchRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}

