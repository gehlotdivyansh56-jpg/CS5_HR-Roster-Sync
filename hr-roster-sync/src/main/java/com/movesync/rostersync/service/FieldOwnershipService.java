package com.movesync.rostersync.service;

import com.movesync.rostersync.domain.entity.Employee;
import com.movesync.rostersync.domain.entity.Office;
import com.movesync.rostersync.dto.SyncRecordDto;
import com.movesync.rostersync.dto.TransportFieldUpdateRequest;
import com.movesync.rostersync.util.HashUtil;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Service enforcing strict field ownership boundaries.
 * HR System owns identity, organizational, and shift fields.
 * Transport Admin owns physical transit attributes (pickup, drop, assistance).
 */
@Service
public class FieldOwnershipService {

    /**
     * Applies HR-owned updates to an existing employee entity.
     * Preserves all Transport Admin fields (pickupPoint, dropPoint, specialAssistance, transportFieldVersion).
     */
    public void applyHrUpdate(Employee employee, SyncRecordDto record, Office office) {
        employee.setName(record.getName() != null ? record.getName().trim() : employee.getName());
        employee.setEmail(record.getEmail() != null ? record.getEmail().trim().toLowerCase() : employee.getEmail());
        employee.setPhone(record.getPhone() != null ? record.getPhone().trim() : employee.getPhone());
        employee.setManagerSourceId(record.getManagerSourceId() != null ? record.getManagerSourceId().trim() : employee.getManagerSourceId());
        if (office != null) {
            employee.setOffice(office);
        }
        employee.setDepartment(record.getDepartment() != null ? record.getDepartment().trim() : employee.getDepartment());
        employee.setShift(record.getShift() != null ? record.getShift().trim() : employee.getShift());

        // Recompute deterministic SHA-256 hash across HR-owned fields
        String officeCode = employee.getOffice() != null ? employee.getOffice().getCode() : null;
        String newHash = HashUtil.computeHrRecordHash(
                employee.getName(),
                employee.getEmail(),
                employee.getPhone(),
                employee.getManagerSourceId(),
                officeCode,
                employee.getDepartment(),
                employee.getShift(),
                employee.getStatus().name()
        );
        employee.setRecordHash(newHash);
        if (record.getVersion() != null) {
            employee.setSourceVersion(record.getVersion());
        }
        employee.setUpdatedAt(Instant.now());
    }

    /**
     * Applies Transport Admin updates to an existing employee entity.
     * Preserves all HR-owned fields, recordHash, and sourceVersion.
     */
    public void applyTransportUpdate(Employee employee, TransportFieldUpdateRequest request) {
        if (request.getPickupPoint() != null) {
            employee.setPickupPoint(request.getPickupPoint().trim());
        }
        if (request.getDropPoint() != null) {
            employee.setDropPoint(request.getDropPoint().trim());
        }
        if (request.getSpecialAssistance() != null) {
            employee.setSpecialAssistance(request.getSpecialAssistance().trim());
        }

        // Increment transport field version without touching sourceVersion or recordHash
        employee.setTransportFieldVersion(employee.getTransportFieldVersion() + 1);
        employee.setUpdatedAt(Instant.now());
    }
}

