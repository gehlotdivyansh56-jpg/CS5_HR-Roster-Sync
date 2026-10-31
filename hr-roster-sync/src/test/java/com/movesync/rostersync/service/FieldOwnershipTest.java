package com.movesync.rostersync.service;

import com.movesync.rostersync.domain.entity.Employee;
import com.movesync.rostersync.domain.entity.Office;
import com.movesync.rostersync.domain.entity.Tenant;
import com.movesync.rostersync.domain.enums.EventType;
import com.movesync.rostersync.dto.SyncRecordDto;
import com.movesync.rostersync.dto.TransportFieldUpdateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FieldOwnershipTest {

    private FieldOwnershipService fieldOwnershipService;
    private Tenant tenant;
    private Office office;

    @BeforeEach
    void setUp() {
        fieldOwnershipService = new FieldOwnershipService();
        tenant = new Tenant("google", "Google Inc.", 0.30);
        office = new Office(tenant, "BLR-01", "Google Bangalore", "Bangalore");
    }

    @Test
    @DisplayName("HR update must update HR fields while preserving Transport Admin fields")
    void shouldPreserveTransportFieldsOnHrUpdate() {
        Employee employee = new Employee(tenant, "EMP-001", 1L, "Alice Initial", "alice@corp.com", "hash1");
        employee.setPickupPoint("Whitefield Gate 1");
        employee.setDropPoint("Electronic City");
        employee.setSpecialAssistance("Wheelchair");
        employee.setTransportFieldVersion(3L);

        SyncRecordDto hrUpdate = new SyncRecordDto(
                "EMP-001", 2L, EventType.UPDATE, "Alice Updated", "alice.updated@corp.com",
                "+919876543210", "MGR-99", "BLR-01", "Security", "NIGHT"
        );

        fieldOwnershipService.applyHrUpdate(employee, hrUpdate, office);

        // HR fields must be updated
        assertThat(employee.getName()).isEqualTo("Alice Updated");
        assertThat(employee.getEmail()).isEqualTo("alice.updated@corp.com");
        assertThat(employee.getDepartment()).isEqualTo("Security");
        assertThat(employee.getShift()).isEqualTo("NIGHT");
        assertThat(employee.getSourceVersion()).isEqualTo(2L);
        assertThat(employee.getRecordHash()).isNotEqualTo("hash1");

        // Transport Admin fields must be preserved exactly
        assertThat(employee.getPickupPoint()).isEqualTo("Whitefield Gate 1");
        assertThat(employee.getDropPoint()).isEqualTo("Electronic City");
        assertThat(employee.getSpecialAssistance()).isEqualTo("Wheelchair");
        assertThat(employee.getTransportFieldVersion()).isEqualTo(3L);
    }

    @Test
    @DisplayName("Transport update must update transit fields without altering HR fields or recordHash")
    void shouldPreserveHrFieldsOnTransportUpdate() {
        String originalHash = "hash-hr-original";
        Employee employee = new Employee(tenant, "EMP-001", 5L, "Bob", "bob@corp.com", originalHash);
        employee.setDepartment("Platform");
        employee.setShift("DAY");
        employee.setTransportFieldVersion(0L);

        TransportFieldUpdateRequest transportReq = new TransportFieldUpdateRequest(
                "Indiranagar 100ft Rd", "Google Bagmane Tech Park", "Visual Assistance"
        );

        fieldOwnershipService.applyTransportUpdate(employee, transportReq);

        // Transport fields must be updated
        assertThat(employee.getPickupPoint()).isEqualTo("Indiranagar 100ft Rd");
        assertThat(employee.getDropPoint()).isEqualTo("Google Bagmane Tech Park");
        assertThat(employee.getSpecialAssistance()).isEqualTo("Visual Assistance");
        assertThat(employee.getTransportFieldVersion()).isEqualTo(1L);

        // HR fields must remain completely untouched
        assertThat(employee.getName()).isEqualTo("Bob");
        assertThat(employee.getEmail()).isEqualTo("bob@corp.com");
        assertThat(employee.getDepartment()).isEqualTo("Platform");
        assertThat(employee.getShift()).isEqualTo("DAY");
        assertThat(employee.getSourceVersion()).isEqualTo(5L);
        assertThat(employee.getRecordHash()).isEqualTo(originalHash);
    }
}

