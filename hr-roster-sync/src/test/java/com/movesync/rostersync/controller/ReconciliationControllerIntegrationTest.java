package com.movesync.rostersync.controller;

import com.movesync.rostersync.auth.JwtTokenProvider;
import com.movesync.rostersync.domain.entity.Tenant;
import com.movesync.rostersync.dto.ReconciliationApprovalRequest;
import com.movesync.rostersync.dto.ReconciliationRunResponse;
import com.movesync.rostersync.repository.EmployeeRepository;
import com.movesync.rostersync.repository.OutboxEventRepository;
import com.movesync.rostersync.repository.ReconciliationItemRepository;
import com.movesync.rostersync.repository.ReconciliationRunRepository;
import com.movesync.rostersync.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ReconciliationControllerIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private ReconciliationRunRepository reconciliationRunRepository;

    @Autowired
    private ReconciliationItemRepository reconciliationItemRepository;

    @Autowired
    private com.movesync.rostersync.repository.OfficeRepository officeRepository;

    private Tenant tenant;
    private String operatorToken;
    private String hrToken;

    @BeforeEach
    void setUp() {
        reconciliationItemRepository.deleteAll();
        reconciliationRunRepository.deleteAll();
        outboxEventRepository.deleteAll();
        employeeRepository.deleteAll();
        officeRepository.deleteAll();
        tenantRepository.deleteAll();

        tenant = tenantRepository.save(new Tenant("amazon", "Amazon Inc.", 0.30));
        operatorToken = jwtTokenProvider.generateToken("amazon-operator", tenant.getId(), "OPERATOR");
        hrToken = jwtTokenProvider.generateToken("amazon-hr", tenant.getId(), "HR_SYSTEM");
    }

    @Test
    @DisplayName("Should successfully upload CSV file, stream reconciliation, and return 200 OK")
    void shouldUploadAndReconcile() {
        String url = "http://localhost:" + port + "/api/v1/reconciliation/upload";

        String csvContent = """
                source_employee_id,name,email,phone,manager_source_id,office_code,department,shift
                AMZ-001,Andy Jassy,andy@amazon.com,,,,Executive,DAY
                """;

        ByteArrayResource resource = new ByteArrayResource(csvContent.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return "roster.csv";
            }
        };

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", resource);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setBearerAuth(operatorToken);
        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        ResponseEntity<ReconciliationRunResponse> response = restTemplate.postForEntity(
                url, requestEntity, ReconciliationRunResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        ReconciliationRunResponse run = response.getBody();
        assertThat(run).isNotNull();
        assertThat(run.getStatus()).isEqualTo("COMPLETED");
        assertThat(run.getTotalInFile()).isEqualTo(1);
        assertThat(run.getAddedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should return 403 Forbidden when caller lacks OPERATOR role")
    void shouldDenyUploadToNonOperatorRole() {
        String url = "http://localhost:" + port + "/api/v1/reconciliation/upload";

        String csvContent = "source_employee_id,name,email\nAMZ-001,Andy,andy@amazon.com";
        ByteArrayResource resource = new ByteArrayResource(csvContent.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return "roster.csv";
            }
        };

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", resource);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setBearerAuth(hrToken); // HR_SYSTEM role, lacks OPERATOR
        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(url, requestEntity, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("Should return 401 Unauthorized when unauthenticated")
    void shouldDenyUnauthenticated() {
        String url = "http://localhost:" + port + "/api/v1/reconciliation/upload";

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(url, requestEntity, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
