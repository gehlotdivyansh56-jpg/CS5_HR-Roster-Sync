package com.movesync.rostersync.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.movesync.rostersync.domain.entity.Employee;

import java.time.Instant;
import java.util.UUID;

public class EmployeeResponseDto {

    @JsonProperty("id")
    private UUID id;

    @JsonProperty("source_employee_id")
    private String sourceEmployeeId;

    @JsonProperty("source_version")
    private Long sourceVersion;

    @JsonProperty("status")
    private String status;

    @JsonProperty("name")
    private String name;

    @JsonProperty("email")
    private String email;

    @JsonProperty("phone")
    private String phone;

    @JsonProperty("manager_source_id")
    private String managerSourceId;

    @JsonProperty("office_code")
    private String officeCode;

    @JsonProperty("department")
    private String department;

    @JsonProperty("shift")
    private String shift;

    @JsonProperty("pickup_point")
    private String pickupPoint;

    @JsonProperty("drop_point")
    private String dropPoint;

    @JsonProperty("special_assistance")
    private String specialAssistance;

    @JsonProperty("transport_field_version")
    private Long transportFieldVersion;

    @JsonProperty("record_hash")
    private String recordHash;

    @JsonProperty("created_at")
    private Instant createdAt;

    @JsonProperty("updated_at")
    private Instant updatedAt;

    public EmployeeResponseDto() {}

    public static EmployeeResponseDto fromEntity(Employee emp) {
        EmployeeResponseDto dto = new EmployeeResponseDto();
        dto.id = emp.getId();
        dto.sourceEmployeeId = emp.getSourceEmployeeId();
        dto.sourceVersion = emp.getSourceVersion();
        dto.status = emp.getStatus().name();
        dto.name = emp.getName();
        dto.email = emp.getEmail();
        dto.phone = emp.getPhone();
        dto.managerSourceId = emp.getManagerSourceId();
        dto.officeCode = emp.getOffice() != null ? emp.getOffice().getCode() : null;
        dto.department = emp.getDepartment();
        dto.shift = emp.getShift();
        dto.pickupPoint = emp.getPickupPoint();
        dto.dropPoint = emp.getDropPoint();
        dto.specialAssistance = emp.getSpecialAssistance();
        dto.transportFieldVersion = emp.getTransportFieldVersion();
        dto.recordHash = emp.getRecordHash();
        dto.createdAt = emp.getCreatedAt();
        dto.updatedAt = emp.getUpdatedAt();
        return dto;
    }

    public UUID getId() { return id; }
    public String getSourceEmployeeId() { return sourceEmployeeId; }
    public Long getSourceVersion() { return sourceVersion; }
    public String getStatus() { return status; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getManagerSourceId() { return managerSourceId; }
    public String getOfficeCode() { return officeCode; }
    public String getDepartment() { return department; }
    public String getShift() { return shift; }
    public String getPickupPoint() { return pickupPoint; }
    public String getDropPoint() { return dropPoint; }
    public String getSpecialAssistance() { return specialAssistance; }
    public Long getTransportFieldVersion() { return transportFieldVersion; }
    public String getRecordHash() { return recordHash; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

