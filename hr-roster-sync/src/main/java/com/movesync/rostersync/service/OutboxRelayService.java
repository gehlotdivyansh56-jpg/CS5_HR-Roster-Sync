package com.movesync.rostersync.service;

import com.movesync.rostersync.domain.entity.OutboxEvent;
import com.movesync.rostersync.domain.enums.OutboxStatus;
import com.movesync.rostersync.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelayService {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayService.class);

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final com.movesync.rostersync.metrics.SyncMetricsService syncMetricsService;

    @Value("${app.kafka.roster-events-topic:employee-roster-events}")
    private String rosterEventsTopic;

    @Value("${app.outbox.batch-size:50}")
    private int batchSize;

    @Value("${app.outbox.max-retries:5}")
    private int maxRetries;

    public OutboxRelayService(OutboxEventRepository outboxEventRepository,
                              KafkaTemplate<String, String> kafkaTemplate,
                              com.movesync.rostersync.metrics.SyncMetricsService syncMetricsService) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.syncMetricsService = syncMetricsService;
    }

    /**
     * Periodic background poller.
     * Uses SELECT ... FOR UPDATE SKIP LOCKED to guarantee multi-node concurrency
     * without lock contention or duplicate Kafka publications.
     */
    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:1000}")
    @ConditionalOnProperty(name = "app.outbox.enabled", havingValue = "true", matchIfMissing = true)
    public void scheduledRelay() {
        relayPendingEvents();
    }

    /**
     * Executes single relay poll batch in an isolated transactional boundary.
     *
     * @return count of successfully published events in this batch
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int relayPendingEvents() {
        List<OutboxEvent> pendingEvents = outboxEventRepository.pollPendingEventsWithSkipLocked(batchSize);
        if (pendingEvents.isEmpty()) {
            return 0;
        }

        log.debug("Outbox relay polled [{}] pending events with SKIP LOCKED", pendingEvents.size());
        int successCount = 0;

        for (OutboxEvent event : pendingEvents) {
            String key = event.getAggregateId() != null ? event.getAggregateId().toString() : event.getTenantId().toString();
            try {
                CompletableFuture<SendResult<String, String>> future = kafkaTemplate.send(
                        rosterEventsTopic,
                        key,
                        event.getPayload()
                );
                
                // Synchronously wait for Kafka broker ACK (with 5 second bounded timeout)
                SendResult<String, String> result = future.get(5, TimeUnit.SECONDS);
                
                event.setStatus(OutboxStatus.PUBLISHED);
                event.setPublishedAt(Instant.now());
                event.setErrorMessage(null);
                outboxEventRepository.save(event);
                successCount++;

                syncMetricsService.recordOutboxPublishLag(java.time.Duration.between(event.getCreatedAt(), Instant.now()).toMillis());

                log.debug("Dispatched outbox event [{}] to topic [{}] partition [{}] offset [{}]",
                        event.getId(),
                        result.getRecordMetadata().topic(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());

            } catch (Exception ex) {
                int retries = event.getRetryCount() + 1;
                event.setRetryCount(retries);
                syncMetricsService.recordOutboxPublishFailure();
                log.error("Failed to publish outbox event [{}] to Kafka on attempt [{}/{}]",
                        event.getId(), retries, maxRetries, ex);

                if (retries >= maxRetries) {
                    event.setStatus(OutboxStatus.FAILED);
                    event.setErrorMessage(ex.getMessage());
                }
                outboxEventRepository.save(event);
            }
        }

        return successCount;
    }
}

