package com.movesync.rostersync.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.movesync.rostersync.domain.enums.EventType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class SyncRecordDto {

    @NotBlank(message = "sourceEmployeeId cannot be blank")
    @JsonProperty("source_employee_id")
    private String sourceEmployeeId;

    @NotNull(message = "version is required")
    @Min(value = 1, message = "version must be greater than or equal to 1")
    @JsonProperty("version")
    private Long version;

    @NotNull(message = "eventType is required (CREATE, UPDATE, DEACTIVATE)")
    @JsonProperty("event_type")
    private EventType eventType;

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

    public SyncRecordDto() {}

    public SyncRecordDto(String sourceEmployeeId, Long version, EventType eventType,
                         String name, String email, String phone,
                         String managerSourceId, String officeCode,
                         String department, String shift) {
        this.sourceEmployeeId = sourceEmployeeId;
        this.version = version;
        this.eventType = eventType;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.managerSourceId = managerSourceId;
        this.officeCode = officeCode;
        this.department = department;
        this.shift = shift;
    }

    public String getSourceEmployeeId() {
        return sourceEmployeeId;
    }

    public void setSourceEmployeeId(String sourceEmployeeId) {
        this.sourceEmployeeId = sourceEmployeeId;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public EventType getEventType() {
        return eventType;
    }

    public void setEventType(EventType eventType) {
        this.eventType = eventType;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getManagerSourceId() {
        return managerSourceId;
    }

    public void setManagerSourceId(String managerSourceId) {
        this.managerSourceId = managerSourceId;
    }

    public String getOfficeCode() {
        return officeCode;
    }

    public void setOfficeCode(String officeCode) {
        this.officeCode = officeCode;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getShift() {
        return shift;
    }

    public void setShift(String shift) {
        this.shift = shift;
    }
}

