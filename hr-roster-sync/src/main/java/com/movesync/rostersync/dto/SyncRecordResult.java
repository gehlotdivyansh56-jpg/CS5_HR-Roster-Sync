package com.movesync.rostersync.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SyncRecordResult {

    @JsonProperty("source_employee_id")
    private String sourceEmployeeId;

    @JsonProperty("version")
    private Long version;

    @JsonProperty("status")
    private String status;

    @JsonProperty("message")
    private String message;

    public SyncRecordResult() {}

    public SyncRecordResult(String sourceEmployeeId, Long version, String status, String message) {
        this.sourceEmployeeId = sourceEmployeeId;
        this.version = version;
        this.status = status;
        this.message = message;
    }

    public static SyncRecordResult applied(String sourceEmployeeId, Long version, String message) {
        return new SyncRecordResult(sourceEmployeeId, version, "APPLIED", message);
    }

    public static SyncRecordResult ignoredDuplicate(String sourceEmployeeId, Long version, String message) {
        return new SyncRecordResult(sourceEmployeeId, version, "IGNORED_DUPLICATE", message);
    }

    public static SyncRecordResult ignoredStale(String sourceEmployeeId, Long version, String message) {
        return new SyncRecordResult(sourceEmployeeId, version, "IGNORED_STALE", message);
    }

    public static SyncRecordResult rejected(String sourceEmployeeId, Long version, String message) {
        return new SyncRecordResult(sourceEmployeeId, version, "REJECTED", message);
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}

