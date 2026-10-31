package com.movesync.rostersync.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.movesync.rostersync.domain.entity.ReconciliationRun;

import java.time.Instant;
import java.util.UUID;

public class ReconciliationRunResponse {

    @JsonProperty("run_id")
    private UUID runId;

    @JsonProperty("tenant_id")
    private UUID tenantId;

    @JsonProperty("status")
    private String status;

    @JsonProperty("file_version")
    private long fileVersion;

    @JsonProperty("total_in_file")
    private int totalInFile;

    @JsonProperty("total_in_store")
    private int totalInStore;

    @JsonProperty("added_count")
    private int addedCount;

    @JsonProperty("updated_count")
    private int updatedCount;

    @JsonProperty("deactivated_count")
    private int deactivatedCount;

    @JsonProperty("skipped_count")
    private int skippedCount;

    @JsonProperty("error_count")
    private int errorCount;

    @JsonProperty("missing_ratio")
    private double missingRatio;

    @JsonProperty("brake_triggered")
    private boolean brakeTriggered;

    @JsonProperty("approved_by")
    private String approvedBy;

    @JsonProperty("started_at")
    private Instant startedAt;

    @JsonProperty("completed_at")
    private Instant completedAt;

    public ReconciliationRunResponse() {}

    public static ReconciliationRunResponse fromEntity(ReconciliationRun run) {
        ReconciliationRunResponse response = new ReconciliationRunResponse();
        response.runId = run.getId();
        response.tenantId = run.getTenantId();
        response.status = run.getStatus().name();
        response.fileVersion = run.getFileVersion();
        response.totalInFile = run.getTotalInFile();
        response.totalInStore = run.getTotalInStore();
        response.addedCount = run.getAddedCount();
        response.updatedCount = run.getUpdatedCount();
        response.deactivatedCount = run.getDeactivatedCount();
        response.skippedCount = run.getSkippedCount();
        response.errorCount = run.getErrorCount();
        response.missingRatio = run.getMissingRatio();
        response.brakeTriggered = run.isBrakeTriggered();
        response.approvedBy = run.getApprovedBy();
        response.startedAt = run.getStartedAt();
        response.completedAt = run.getCompletedAt();
        return response;
    }

    public UUID getRunId() { return runId; }
    public UUID getTenantId() { return tenantId; }
    public String getStatus() { return status; }
    public long getFileVersion() { return fileVersion; }
    public int getTotalInFile() { return totalInFile; }
    public int getTotalInStore() { return totalInStore; }
    public int getAddedCount() { return addedCount; }
    public int getUpdatedCount() { return updatedCount; }
    public int getDeactivatedCount() { return deactivatedCount; }
    public int getSkippedCount() { return skippedCount; }
    public int getErrorCount() { return errorCount; }
    public double getMissingRatio() { return missingRatio; }
    public boolean isBrakeTriggered() { return brakeTriggered; }
    public String getApprovedBy() { return approvedBy; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
}

