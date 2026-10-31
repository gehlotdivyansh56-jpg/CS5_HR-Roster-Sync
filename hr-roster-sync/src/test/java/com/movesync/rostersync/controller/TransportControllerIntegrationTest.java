package com.movesync.rostersync.controller;

import com.movesync.rostersync.auth.JwtTokenProvider;
import com.movesync.rostersync.domain.entity.Employee;
import com.movesync.rostersync.domain.entity.Office;
import com.movesync.rostersync.domain.entity.Tenant;
import com.movesync.rostersync.dto.EmployeeResponseDto;
import com.movesync.rostersync.dto.TransportFieldUpdateRequest;
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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class TransportControllerIntegrationTest {

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

    private Tenant tenantGoogle;
    private Tenant tenantMicrosoft;
    private String transportAdminTokenGoogle;
    private String hrTokenGoogle;
    private String transportAdminTokenMicrosoft;

    @BeforeEach
    void setUp() {
        outboxEventRepository.deleteAll();
        employeeRepository.deleteAll();
        officeRepository.deleteAll();
        tenantRepository.deleteAll();

        tenantGoogle = tenantRepository.save(new Tenant("google", "Google Inc.", 0.30));
        Office officeGoogle = officeRepository.save(new Office(tenantGoogle, "BLR-01", "Google Bangalore", "Bangalore"));

        tenantMicrosoft = tenantRepository.save(new Tenant("microsoft", "Microsoft Corp.", 0.30));

        // Create an employee in Google
        Employee emp = new Employee(tenantGoogle, "EMP-999", 1L, "George", "george@google.com", "hash999");
        emp.setOffice(officeGoogle);
        emp.setDepartment("Security");
        employeeRepository.save(emp);

        transportAdminTokenGoogle = jwtTokenProvider.generateToken("google-transport-admin", tenantGoogle.getId(), "TRANSPORT_ADMIN");
        hrTokenGoogle = jwtTokenProvider.generateToken("google-hr", tenantGoogle.getId(), "HR_SYSTEM");
        transportAdminTokenMicrosoft = jwtTokenProvider.generateToken("msft-transport-admin", tenantMicrosoft.getId(), "TRANSPORT_ADMIN");
    }

    @Test
    @DisplayName("Should successfully update transport fields when invoked by TRANSPORT_ADMIN")
    void shouldUpdateTransportFields() {
        String url = "http://localhost:" + port + "/api/v1/employees/EMP-999/transport-fields";

        TransportFieldUpdateRequest request = new TransportFieldUpdateRequest(
                "Hebbal Flyover Bus Stop", "Google RMZ Infinity", "None"
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(transportAdminTokenGoogle);
        HttpEntity<TransportFieldUpdateRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<EmployeeResponseDto> response = restTemplate.exchange(
                url, HttpMethod.PUT, entity, EmployeeResponseDto.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        EmployeeResponseDto body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getPickupPoint()).isEqualTo("Hebbal Flyover Bus Stop");
        assertThat(body.getDropPoint()).isEqualTo("Google RMZ Infinity");
        assertThat(body.getSpecialAssistance()).isEqualTo("None");
        assertThat(body.getTransportFieldVersion()).isEqualTo(1L);

        // Verify HR fields remain unchanged
        assertThat(body.getDepartment()).isEqualTo("Security");
        assertThat(body.getSourceVersion()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Should return 403 Forbidden when HR_SYSTEM role attempts to call transport endpoint")
    void shouldDenyTransportUpdateToHrRole() {
        String url = "http://localhost:" + port + "/api/v1/employees/EMP-999/transport-fields";

        TransportFieldUpdateRequest request = new TransportFieldUpdateRequest("Stop A", "Stop B", null);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(hrTokenGoogle); // HR_SYSTEM role, should be rejected
        HttpEntity<TransportFieldUpdateRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.PUT, entity, String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("Tenant isolation: Microsoft transport admin cannot update Google employee")
    void shouldPreventCrossTenantTransportUpdate() {
        String url = "http://localhost:" + port + "/api/v1/employees/EMP-999/transport-fields";

        TransportFieldUpdateRequest request = new TransportFieldUpdateRequest("Stop A", "Stop B", null);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(transportAdminTokenMicrosoft); // Microsoft tenant token
        HttpEntity<TransportFieldUpdateRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.PUT, entity, String.class
        );

        // Employee EMP-999 does not exist under Microsoft tenant, should return 400 Bad Request
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("Employee not found with id: EMP-999");
    }
}

