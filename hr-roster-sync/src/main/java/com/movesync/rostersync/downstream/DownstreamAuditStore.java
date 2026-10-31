package com.movesync.rostersync.downstream;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class DownstreamAuditStore {

    public record BookingCancellationRecord(UUID tenantId, UUID employeeId, Instant timestamp) {}
    public record SeatReleaseRecord(UUID tenantId, UUID employeeId, Instant timestamp) {}

    private final List<BookingCancellationRecord> cancellations = new CopyOnWriteArrayList<>();
    private final List<SeatReleaseRecord> seatReleases = new CopyOnWriteArrayList<>();
    private final AtomicBoolean simulateFailure = new AtomicBoolean(false);

    public void recordCancellation(UUID tenantId, UUID employeeId) {
        cancellations.add(new BookingCancellationRecord(tenantId, employeeId, Instant.now()));
    }

    public void recordSeatRelease(UUID tenantId, UUID employeeId) {
        seatReleases.add(new SeatReleaseRecord(tenantId, employeeId, Instant.now()));
    }

    public List<BookingCancellationRecord> getCancellations() {
        return List.copyOf(cancellations);
    }

    public List<SeatReleaseRecord> getSeatReleases() {
        return List.copyOf(seatReleases);
    }

    public boolean isSimulateFailure() {
        return simulateFailure.get();
    }

    public void setSimulateFailure(boolean simulate) {
        this.simulateFailure.set(simulate);
    }

    public void clear() {
        cancellations.clear();
        seatReleases.clear();
        simulateFailure.set(false);
    }
}

