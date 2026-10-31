package com.movesync.rostersync.downstream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class BookingServiceClient {

    private static final Logger log = LoggerFactory.getLogger(BookingServiceClient.class);
    private final DownstreamAuditStore auditStore;

    public BookingServiceClient(DownstreamAuditStore auditStore) {
        this.auditStore = auditStore;
    }

    public void cancelActiveBookings(UUID tenantId, UUID employeeId) {
        if (auditStore.isSimulateFailure()) {
            log.error("Simulated downstream Booking Service outage for employee [{}]", employeeId);
            throw new RuntimeException("Simulated Booking Service 503 Service Unavailable");
        }
        log.info("Successfully cancelled active and upcoming cab bookings for tenant [{}] employee [{}]", tenantId, employeeId);
        auditStore.recordCancellation(tenantId, employeeId);
    }
}

