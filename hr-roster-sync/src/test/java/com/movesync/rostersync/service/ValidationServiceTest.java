package com.movesync.rostersync.service;

import com.movesync.rostersync.domain.enums.EventType;
import com.movesync.rostersync.dto.SyncRecordDto;
import com.movesync.rostersync.repository.OfficeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class ValidationServiceTest {

    private OfficeRepository officeRepository;
    private ValidationService validationService;
    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        officeRepository = Mockito.mock(OfficeRepository.class);
        validationService = new ValidationService(officeRepository);
        when(officeRepository.existsByTenantIdAndCodeAndActiveTrue(eq(tenantId), eq("BLR-01"))).thenReturn(true);
        when(officeRepository.existsByTenantIdAndCodeAndActiveTrue(eq(tenantId), eq("INVALID-OFFICE"))).thenReturn(false);
    }

    @Test
    @DisplayName("Should pass valid CREATE record")
    void shouldPassValidRecord() {
        SyncRecordDto record = new SyncRecordDto(
                "EMP-001", 1L, EventType.CREATE, "John Doe", "john.doe@corp.com",
                "+919876543210", "MGR-001", "BLR-01", "Finance", "DAY"
        );

        List<String> errors = validationService.validate(tenantId, record);
        assertThat(errors).isEmpty();
    }

    @Test
    @DisplayName("Should reject invalid phone format without leaking raw phone in error")
    void shouldRejectInvalidPhoneAndMaskPii() {
        SyncRecordDto record = new SyncRecordDto(
                "EMP-002", 1L, EventType.CREATE, "John Doe", "john.doe@corp.com",
                "123", null, "BLR-01", null, null
        );

        List<String> errors = validationService.validate(tenantId, record);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0)).contains("phone format is invalid");
        assertThat(errors.get(0)).doesNotContain("123");
        assertThat(errors.get(0)).contains("***REDACTED***");
    }

    @Test
    @DisplayName("Should reject invalid email format without leaking raw email in error")
    void shouldRejectInvalidEmailAndMaskPii() {
        SyncRecordDto record = new SyncRecordDto(
                "EMP-003", 1L, EventType.CREATE, "John Doe", "not-an-email",
                "+919876543210", null, "BLR-01", null, null
        );

        List<String> errors = validationService.validate(tenantId, record);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0)).contains("email format is invalid");
        assertThat(errors.get(0)).doesNotContain("not-an-email");
    }

    @Test
    @DisplayName("Should reject non-existent office code")
    void shouldRejectNonExistentOffice() {
        SyncRecordDto record = new SyncRecordDto(
                "EMP-004", 1L, EventType.CREATE, "John Doe", "john.doe@corp.com",
                "+919876543210", null, "INVALID-OFFICE", null, null
        );

        List<String> errors = validationService.validate(tenantId, record);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0)).contains("office_code does not exist or is inactive");
    }

    @Test
    @DisplayName("Should allow DEACTIVATE event with only employeeId and version")
    void shouldAllowDeactivateWithMinimalFields() {
        SyncRecordDto record = new SyncRecordDto();
        record.setSourceEmployeeId("EMP-005");
        record.setVersion(5L);
        record.setEventType(EventType.DEACTIVATE);

        List<String> errors = validationService.validate(tenantId, record);
        assertThat(errors).isEmpty();
    }
}

