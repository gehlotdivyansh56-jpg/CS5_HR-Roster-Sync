package com.movesync.rostersync.controller;

import com.movesync.rostersync.auth.RequiresRole;
import com.movesync.rostersync.auth.TenantContext;
import com.movesync.rostersync.dto.BatchSyncResponse;
import com.movesync.rostersync.dto.SyncBatchRequest;
import com.movesync.rostersync.service.SyncService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sync")
public class EventController {

    private final SyncService syncService;
    private final com.movesync.rostersync.service.TenantRateLimiterService rateLimiterService;

    public EventController(SyncService syncService,
                           com.movesync.rostersync.service.TenantRateLimiterService rateLimiterService) {
        this.syncService = syncService;
        this.rateLimiterService = rateLimiterService;
    }

    /**
     * Ingests real-time or daytime batch sync events from HR systems.
     * Enforces tenant context isolation and HR_SYSTEM role privilege.
     * Enforces per-tenant rate limits.
     * Returns HTTP 207 Multi-Status with itemized status per record.
     */
    @PostMapping("/events")
    @RequiresRole("HR_SYSTEM")
    public ResponseEntity<?> syncEvents(@Valid @RequestBody SyncBatchRequest request) {
        UUID tenantId = TenantContext.requireTenantId();

        if (!rateLimiterService.tryAcquire(tenantId)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header("Retry-After", "1")
                    .body(java.util.Map.of("error", "Rate limit exceeded for tenant. Please retry after a brief delay."));
        }

        BatchSyncResponse response = syncService.processBatch(tenantId, request);
        return ResponseEntity.status(HttpStatus.MULTI_STATUS).body(response);
    }
}

