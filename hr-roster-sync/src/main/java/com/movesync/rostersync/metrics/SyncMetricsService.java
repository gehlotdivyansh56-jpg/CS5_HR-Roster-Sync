package com.movesync.rostersync.metrics;

import com.github.benmanes.caffeine.cache.Cache;
import com.movesync.rostersync.domain.enums.OutboxStatus;
import com.movesync.rostersync.repository.OutboxEventRepository;
import com.movesync.rostersync.service.EmployeeVersionCacheService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class SyncMetricsService {

    private final MeterRegistry registry;
    private final OutboxEventRepository outboxEventRepository;
    private final EmployeeVersionCacheService versionCacheService;
    private final Cache<Object, Object> officeCache;

    private final Counter eventsReceivedCounter;
    private final Counter eventsAppliedCounter;
    private final Counter eventsStaleIgnoredCounter;
    private final Counter eventsValidationFailedCounter;
    private final Timer eventProcessingTimer;

    private final Counter reconciliationRunsCounter;
    private final Timer reconciliationDurationTimer;
    private final Counter safetyBrakeTriggeredCounter;
    private final AtomicReference<Double> lastMissingRatio = new AtomicReference<>(0.0);

    private final Counter outboxPublishFailuresCounter;
    private final Timer outboxPublishLagTimer;

    private final Counter dlqPublishedCounter;
    private final Counter authRejectedCounter;

    public SyncMetricsService(MeterRegistry registry,
                              OutboxEventRepository outboxEventRepository,
                              EmployeeVersionCacheService versionCacheService,
                              Cache<Object, Object> officeExistenceCache) {
        this.registry = registry;
        this.outboxEventRepository = outboxEventRepository;
        this.versionCacheService = versionCacheService;
        this.officeCache = officeExistenceCache;

        // 1. Sync Event Counters & Timers
        this.eventsReceivedCounter = Counter.builder("sync.events.received")
                .description("Total employee sync events received")
                .register(registry);

        this.eventsAppliedCounter = Counter.builder("sync.events.applied")
                .description("Events that produced a DB write")
                .register(registry);

        this.eventsStaleIgnoredCounter = Counter.builder("sync.events.stale_ignored")
                .description("Stale or duplicate events ignored")
                .register(registry);

        this.eventsValidationFailedCounter = Counter.builder("sync.events.validation_failed")
                .description("Events rejected due to validation failures")
                .register(registry);

        this.eventProcessingTimer = Timer.builder("sync.events.processing_time")
                .description("Latency distribution for processing sync events")
                .register(registry);

        // 2. Reconciliation Metrics
        this.reconciliationRunsCounter = Counter.builder("reconciliation.runs")
                .description("Total nightly reconciliation runs initiated")
                .register(registry);

        this.reconciliationDurationTimer = Timer.builder("reconciliation.duration")
                .description("Full reconciliation run duration")
                .register(registry);

        this.safetyBrakeTriggeredCounter = Counter.builder("reconciliation.brake_triggered")
                .description("Safety brake activations halting mass deactivations")
                .register(registry);

        Gauge.builder("reconciliation.missing_ratio", lastMissingRatio, AtomicReference::get)
                .description("Last reconciliation run missing-employee ratio")
                .register(registry);

        // 3. Outbox Gauges & Counters
        Gauge.builder("outbox.pending_count", () -> outboxEventRepository.countByStatus(OutboxStatus.PENDING))
                .description("Current number of unpublished events in outbox")
                .register(registry);

        this.outboxPublishLagTimer = Timer.builder("outbox.publish_lag")
                .description("Latency from outbox insertion to Kafka acknowledgment")
                .register(registry);

        this.outboxPublishFailuresCounter = Counter.builder("outbox.publish_failures")
                .description("Failed Kafka publish attempts from outbox relay")
                .register(registry);

        // 4. Kafka DLQ & Security Metrics
        this.dlqPublishedCounter = Counter.builder("kafka.consumer.dlq.published")
                .description("Poison pills forwarded to Dead Letter Queue")
                .register(registry);

        this.authRejectedCounter = Counter.builder("auth.rejected")
                .description("Unauthorized or forbidden API requests")
                .register(registry);

        // 5. Caching Hit Rate Gauges
        Gauge.builder("cache.office.hit_rate", () -> {
            var stats = officeCache.stats();
            return stats.requestCount() > 0 ? stats.hitRate() : 1.0;
        }).description("Office code cache hit ratio").register(registry);

        Gauge.builder("cache.version.hit_rate", versionCacheService::getHitRate)
                .description("Employee version cache hit ratio")
                .register(registry);
    }

    public void recordEventReceived(int count) { eventsReceivedCounter.increment(count); }
    public void recordEventApplied() { eventsAppliedCounter.increment(); }
    public void recordEventStaleIgnored() { eventsStaleIgnoredCounter.increment(); }
    public void recordValidationFailed() { eventsValidationFailedCounter.increment(); }
    public void recordEventProcessingTime(long durationNanos) { eventProcessingTimer.record(durationNanos, TimeUnit.NANOSECONDS); }

    public void recordReconciliationRun() { reconciliationRunsCounter.increment(); }
    public void recordReconciliationDuration(long durationMillis) { reconciliationDurationTimer.record(durationMillis, TimeUnit.MILLISECONDS); }
    public void recordSafetyBrakeTriggered() { safetyBrakeTriggeredCounter.increment(); }
    public void recordMissingRatio(double ratio) { lastMissingRatio.set(ratio); }

    public void recordOutboxPublishLag(long lagMillis) { outboxPublishLagTimer.record(lagMillis, TimeUnit.MILLISECONDS); }
    public void recordOutboxPublishFailure() { outboxPublishFailuresCounter.increment(); }

    public void recordDlqPublished() { dlqPublishedCounter.increment(); }
    public void recordAuthRejected() { authRejectedCounter.increment(); }
}
