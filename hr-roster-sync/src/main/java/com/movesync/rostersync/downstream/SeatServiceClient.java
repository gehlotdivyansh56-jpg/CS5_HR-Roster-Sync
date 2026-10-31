package com.movesync.rostersync.downstream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class SeatServiceClient {

    private static final Logger log = LoggerFactory.getLogger(SeatServiceClient.class);
    private final DownstreamAuditStore auditStore;

    public SeatServiceClient(DownstreamAuditStore auditStore) {
        this.auditStore = auditStore;
    }

    public void releaseAssignedSeats(UUID tenantId, UUID employeeId) {
        if (auditStore.isSimulateFailure()) {
            log.error("Simulated downstream Seat Allocation Service outage for employee [{}]", employeeId);
            throw new RuntimeException("Simulated Seat Service 503 Service Unavailable");
        }
        log.info("Successfully released vehicle seat allocation for tenant [{}] employee [{}]", tenantId, employeeId);
        auditStore.recordSeatRelease(tenantId, employeeId);
    }
}

