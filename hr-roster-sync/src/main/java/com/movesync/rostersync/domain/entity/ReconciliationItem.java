package com.movesync.rostersync.domain.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "reconciliation_item")
public class ReconciliationItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "run_id", nullable = false)
    private ReconciliationRun run;

    @Column(name = "source_employee_id", nullable = false, length = 100)
    private String sourceEmployeeId;

    @Column(nullable = false, length = 30)
    private String action; // ADD, UPDATE, DEACTIVATE, SKIP, ERROR

    @Column(columnDefinition = "text")
    private String reason;

    @Column(name = "processed_at", nullable = false, updatable = false)
    private Instant processedAt = Instant.now();

    public ReconciliationItem() {}

    public ReconciliationItem(ReconciliationRun run, String sourceEmployeeId, String action, String reason) {
        this.run = run;
        this.sourceEmployeeId = sourceEmployeeId;
        this.action = action;
        this.reason = reason;
        this.processedAt = Instant.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public ReconciliationRun getRun() { return run; }
    public void setRun(ReconciliationRun run) { this.run = run; }

    public String getSourceEmployeeId() { return sourceEmployeeId; }
    public void setSourceEmployeeId(String sourceEmployeeId) { this.sourceEmployeeId = sourceEmployeeId; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public Instant getProcessedAt() { return processedAt; }
    public void setProcessedAt(Instant processedAt) { this.processedAt = processedAt; }
}

