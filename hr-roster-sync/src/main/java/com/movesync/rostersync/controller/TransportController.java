package com.movesync.rostersync.controller;

import com.movesync.rostersync.auth.RequiresRole;
import com.movesync.rostersync.auth.TenantContext;
import com.movesync.rostersync.dto.EmployeeResponseDto;
import com.movesync.rostersync.dto.TransportFieldUpdateRequest;
import com.movesync.rostersync.service.SyncService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/employees")
public class TransportController {

    private final SyncService syncService;

    public TransportController(SyncService syncService) {
        this.syncService = syncService;
    }

    /**
     * Updates transport-specific attributes (pickup, drop, assistance) for an employee.
     * Enforces TRANSPORT_ADMIN role. HR attributes cannot be modified via this endpoint.
     */
    @PutMapping("/{sourceEmployeeId}/transport-fields")
    @RequiresRole("TRANSPORT_ADMIN")
    public ResponseEntity<EmployeeResponseDto> updateTransportFields(
            @PathVariable("sourceEmployeeId") String sourceEmployeeId,
            @RequestBody TransportFieldUpdateRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        EmployeeResponseDto response = syncService.updateTransportFields(tenantId, sourceEmployeeId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves an employee's unified profile scoped to the caller's tenant.
     */
    @GetMapping("/{sourceEmployeeId}")
    public ResponseEntity<EmployeeResponseDto> getEmployee(
            @PathVariable("sourceEmployeeId") String sourceEmployeeId) {
        UUID tenantId = TenantContext.requireTenantId();
        EmployeeResponseDto response = syncService.getEmployee(tenantId, sourceEmployeeId);
        return ResponseEntity.ok(response);
    }
}

