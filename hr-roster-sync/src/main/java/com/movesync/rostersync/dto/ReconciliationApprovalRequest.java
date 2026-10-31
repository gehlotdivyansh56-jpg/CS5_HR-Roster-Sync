package com.movesync.rostersync.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public class ReconciliationApprovalRequest {

    @NotBlank(message = "approved_by cannot be blank")
    @JsonProperty("approved_by")
    private String approvedBy;

    public ReconciliationApprovalRequest() {}

    public ReconciliationApprovalRequest(String approvedBy) {
        this.approvedBy = approvedBy;
    }

    public String getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(String approvedBy) {
        this.approvedBy = approvedBy;
    }
}

