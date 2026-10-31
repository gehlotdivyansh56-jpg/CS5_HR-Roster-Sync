package com.movesync.rostersync.domain.entity;

import com.movesync.rostersync.domain.enums.ReconciliationStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reconciliation_run")
public class ReconciliationRun {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ReconciliationStatus status = ReconciliationStatus.RUNNING;

    @Column(name = "total_in_file", nullable = false)
    private int totalInFile = 0;

    @Column(name = "total_in_store", nullable = false)
    private int totalInStore = 0;

    @Column(name = "added_count", nullable = false)
    private int addedCount = 0;

    @Column(name = "updated_count", nullable = false)
    private int updatedCount = 0;

    @Column(name = "deactivated_count", nullable = false)
    private int deactivatedCount = 0;

    @Column(name = "skipped_count", nullable = false)
    private int skippedCount = 0;

    @Column(name = "error_count", nullable = false)
    private int errorCount = 0;

    @Column(name = "missing_ratio", nullable = false)
    private double missingRatio = 0.0;

    @Column(name = "brake_triggered", nullable = false)
    private boolean brakeTriggered = false;

    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    @Column(name = "file_version", nullable = false)
    private long fileVersion;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt = Instant.now();

    @Column(name = "completed_at")
    private Instant completedAt;

    public ReconciliationRun() {}

    public ReconciliationRun(UUID tenantId, long fileVersion) {
        this.tenantId = tenantId;
        this.fileVersion = fileVersion;
        this.status = ReconciliationStatus.RUNNING;
        this.startedAt = Instant.now();
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public ReconciliationStatus getStatus() { return status; }
    public void setStatus(ReconciliationStatus status) { this.status = status; }

    public int getTotalInFile() { return totalInFile; }
    public void setTotalInFile(int totalInFile) { this.totalInFile = totalInFile; }

    public int getTotalInStore() { return totalInStore; }
    public void setTotalInStore(int totalInStore) { this.totalInStore = totalInStore; }

    public int getAddedCount() { return addedCount; }
    public void setAddedCount(int addedCount) { this.addedCount = addedCount; }

    public int getUpdatedCount() { return updatedCount; }
    public void setUpdatedCount(int updatedCount) { this.updatedCount = updatedCount; }

    public int getDeactivatedCount() { return deactivatedCount; }
    public void setDeactivatedCount(int deactivatedCount) { this.deactivatedCount = deactivatedCount; }

    public int getSkippedCount() { return skippedCount; }
    public void setSkippedCount(int skippedCount) { this.skippedCount = skippedCount; }

    public int getErrorCount() { return errorCount; }
    public void setErrorCount(int errorCount) { this.errorCount = errorCount; }

    public double getMissingRatio() { return missingRatio; }
    public void setMissingRatio(double missingRatio) { this.missingRatio = missingRatio; }

    public boolean isBrakeTriggered() { return brakeTriggered; }
    public void setBrakeTriggered(boolean brakeTriggered) { this.brakeTriggered = brakeTriggered; }

    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }

    public long getFileVersion() { return fileVersion; }
    public void setFileVersion(long fileVersion) { this.fileVersion = fileVersion; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}

