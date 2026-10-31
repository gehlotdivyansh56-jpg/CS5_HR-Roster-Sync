package com.movesync.rostersync.controller;

import com.movesync.rostersync.auth.RequiresRole;
import com.movesync.rostersync.auth.TenantContext;
import com.movesync.rostersync.dto.ReconciliationApprovalRequest;
import com.movesync.rostersync.dto.ReconciliationRunResponse;
import com.movesync.rostersync.service.ReconciliationService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reconciliation")
public class ReconciliationController {

    private final ReconciliationService reconciliationService;

    public ReconciliationController(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    /**
     * Uploads and streams nightly reconciliation file.
     * Enforces OPERATOR role.
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequiresRole("OPERATOR")
    public ResponseEntity<ReconciliationRunResponse> uploadReconciliationFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "file_version", required = false) Long fileVersion) throws IOException {

        UUID tenantId = TenantContext.requireTenantId();
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded reconciliation file cannot be empty");
        }

        ReconciliationRunResponse response = reconciliationService.reconcile(tenantId, file.getInputStream(), fileVersion);
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves status, missing ratio, and itemized diff counts for a reconciliation run.
     */
    @GetMapping("/runs/{runId}")
    @RequiresRole("OPERATOR")
    public ResponseEntity<ReconciliationRunResponse> getRun(
            @PathVariable("runId") UUID runId) {

        UUID tenantId = TenantContext.requireTenantId();
        ReconciliationRunResponse response = reconciliationService.getRun(tenantId, runId);
        return ResponseEntity.ok(response);
    }

    /**
     * Approves a run that was halted by the Safety Brake, executing the withheld deactivations.
     */
    @PostMapping("/runs/{runId}/approve")
    @RequiresRole("OPERATOR")
    public ResponseEntity<ReconciliationRunResponse> approveRun(
            @PathVariable("runId") UUID runId,
            @Valid @RequestBody ReconciliationApprovalRequest request) {

        UUID tenantId = TenantContext.requireTenantId();
        ReconciliationRunResponse response = reconciliationService.approveRun(tenantId, runId, request.getApprovedBy());
        return ResponseEntity.ok(response);
    }
}

