package com.movesync.rostersync.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

public class BatchSyncResponse {

    @JsonProperty("total_events")
    private int totalEvents;

    @JsonProperty("applied_count")
    private int appliedCount;

    @JsonProperty("ignored_count")
    private int ignoredCount;

    @JsonProperty("rejected_count")
    private int rejectedCount;

    @JsonProperty("results")
    private List<SyncRecordResult> results = new ArrayList<>();

    public BatchSyncResponse() {}

    public BatchSyncResponse(List<SyncRecordResult> results) {
        this.results = results != null ? results : new ArrayList<>();
        this.totalEvents = this.results.size();
        for (SyncRecordResult res : this.results) {
            if ("APPLIED".equals(res.getStatus())) {
                this.appliedCount++;
            } else if ("IGNORED_DUPLICATE".equals(res.getStatus()) || "IGNORED_STALE".equals(res.getStatus())) {
                this.ignoredCount++;
            } else if ("REJECTED".equals(res.getStatus())) {
                this.rejectedCount++;
            }
        }
    }

    public int getTotalEvents() {
        return totalEvents;
    }

    public void setTotalEvents(int totalEvents) {
        this.totalEvents = totalEvents;
    }

    public int getAppliedCount() {
        return appliedCount;
    }

    public void setAppliedCount(int appliedCount) {
        this.appliedCount = appliedCount;
    }

    public int getIgnoredCount() {
        return ignoredCount;
    }

    public void setIgnoredCount(int ignoredCount) {
        this.ignoredCount = ignoredCount;
    }

    public int getRejectedCount() {
        return rejectedCount;
    }

    public void setRejectedCount(int rejectedCount) {
        this.rejectedCount = rejectedCount;
    }

    public List<SyncRecordResult> getResults() {
        return results;
    }

    public void setResults(List<SyncRecordResult> results) {
        this.results = results;
    }
}

