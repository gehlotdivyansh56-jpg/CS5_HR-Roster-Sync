package com.movesync.rostersync.service;

import com.movesync.rostersync.domain.enums.EventType;
import com.movesync.rostersync.dto.SyncRecordDto;
import com.movesync.rostersync.repository.OfficeRepository;
import com.movesync.rostersync.util.PiiRedactor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class ValidationService {

    // E.164 phone pattern: optional '+', followed by 1-9, followed by 6 to 14 digits
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+?[1-9]\\d{6,14}$");

    // Standard RFC-compliant email pattern
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final OfficeRepository officeRepository;

    public ValidationService(OfficeRepository officeRepository) {
        this.officeRepository = officeRepository;
    }

    /**
     * Validates an incoming sync record for tenant.
     * Returns a list of sanitized validation error messages (empty if valid).
     */
    public List<String> validate(UUID tenantId, SyncRecordDto record) {
        List<String> errors = new ArrayList<>();

        if (record == null) {
            errors.add("Event payload cannot be null");
            return errors;
        }

        if (!StringUtils.hasText(record.getSourceEmployeeId())) {
            errors.add("source_employee_id is mandatory and cannot be blank");
        }

        if (record.getVersion() == null || record.getVersion() < 1) {
            errors.add("version must be a positive integer >= 1");
        }

        if (record.getEventType() == null) {
            errors.add("event_type is mandatory (CREATE, UPDATE, DEACTIVATE)");
            return errors;
        }

        // Deactivation only requires source_employee_id and version
        if (record.getEventType() == EventType.DEACTIVATE) {
            return errors;
        }

        // For CREATE and UPDATE: validate core HR identity fields
        if (!StringUtils.hasText(record.getName())) {
            errors.add("name is mandatory for " + record.getEventType());
        }

        if (!StringUtils.hasText(record.getEmail())) {
            errors.add("email is mandatory for " + record.getEventType());
        } else if (!EMAIL_PATTERN.matcher(record.getEmail().trim()).matches()) {
            errors.add("email format is invalid for value: " + PiiRedactor.maskEmail(record.getEmail()));
        }

        if (StringUtils.hasText(record.getPhone())) {
            String sanitizedPhone = record.getPhone().trim().replaceAll("[\\s-]", "");
            if (!PHONE_PATTERN.matcher(sanitizedPhone).matches()) {
                errors.add("phone format is invalid (must be E.164 compatible): " + PiiRedactor.maskPhone(record.getPhone()));
            }
        }

        if (StringUtils.hasText(record.getOfficeCode())) {
            boolean officeExists = isOfficeValid(tenantId, record.getOfficeCode().trim().toUpperCase());
            if (!officeExists) {
                errors.add("office_code does not exist or is inactive: " + record.getOfficeCode().trim().toUpperCase());
            }
        }

        return errors;
    }

    @Cacheable(value = "officeExistence", key = "#tenantId.toString() + '_' + #officeCode")
    public boolean isOfficeValid(UUID tenantId, String officeCode) {
        return officeRepository.existsByTenantIdAndCodeAndActiveTrue(tenantId, officeCode);
    }
}

