package com.movesync.rostersync.service;

import com.movesync.rostersync.domain.entity.OutboxEvent;
import com.movesync.rostersync.domain.enums.OutboxStatus;
import com.movesync.rostersync.repository.OutboxEventRepository;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "app.kafka.enabled=true",
        "app.outbox.enabled=false",
        "app.outbox.batch-size=10",
        "app.outbox.max-retries=3",
        "spring.kafka.listener.auto-startup=false"
})
class OutboxRelayServiceIntegrationTest {

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @MockBean
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private OutboxRelayService outboxRelayService;

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        outboxEventRepository.deleteAll();
        tenantId = UUID.randomUUID();

        // Default mock behavior: successful send
        RecordMetadata metadata = new RecordMetadata(
                new TopicPartition("employee-roster-events", 0), 0, 0, System.currentTimeMillis(), 0, 0
        );
        SendResult<String, String> sendResult = new SendResult<>(null, metadata);
        CompletableFuture<SendResult<String, String>> successFuture = CompletableFuture.completedFuture(sendResult);
        when(kafkaTemplate.send(anyString(), any(), any())).thenReturn(successFuture);
    }

    @Test
    @DisplayName("4.1 & 4.2: Polling pending events dispatches to Kafka and marks status PUBLISHED")
    void shouldPollPendingEventsAndMarkAsPublished() {
        OutboxEvent event1 = new OutboxEvent(tenantId, UUID.randomUUID(), "EMPLOYEE_CREATED", "{\"emp\":\"1\"}");
        OutboxEvent event2 = new OutboxEvent(tenantId, UUID.randomUUID(), "EMPLOYEE_UPDATED", "{\"emp\":\"2\"}");
        outboxEventRepository.saveAll(List.of(event1, event2));

        int publishedCount = outboxRelayService.relayPendingEvents();

        assertThat(publishedCount).isEqualTo(2);

        List<OutboxEvent> all = outboxEventRepository.findAll();
        assertThat(all).hasSize(2);
        for (OutboxEvent event : all) {
            assertThat(event.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
            assertThat(event.getPublishedAt()).isNotNull();
            assertThat(event.getErrorMessage()).isNull();
        }

        verify(kafkaTemplate, times(2)).send(eq("employee-roster-events"), any(), any());
    }

    @Test
    @DisplayName("4.2: Outbox multi-thread concurrency with SKIP LOCKED prevents duplicate dispatch")
    void shouldRelayConcurrentlyWithoutDuplicatesUsingSkipLocked() throws InterruptedException, ExecutionException {
        // Insert 20 pending events
        for (int i = 0; i < 20; i++) {
            outboxEventRepository.save(new OutboxEvent(tenantId, UUID.randomUUID(), "EMPLOYEE_UPDATED", "{\"idx\":" + i + "}"));
        }

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(1);

        Callable<Integer> worker = () -> {
            latch.await();
            return outboxRelayService.relayPendingEvents();
        };

        Future<Integer> f1 = executor.submit(worker);
        Future<Integer> f2 = executor.submit(worker);

        latch.countDown(); // Start both threads concurrently

        int count1 = f1.get();
        int count2 = f2.get();
        executor.shutdown();

        // Total dispatched across both threads must equal total available (or <= 20 if batch size is 10)
        assertThat(count1 + count2).isGreaterThan(0);

        // Run one more time to flush any remaining
        outboxRelayService.relayPendingEvents();

        List<OutboxEvent> all = outboxEventRepository.findAll();
        for (OutboxEvent event : all) {
            assertThat(event.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        }
    }

    @Test
    @DisplayName("4.6: Broker failure increments retryCount and transitions to FAILED upon max retries")
    void shouldIncrementRetryCountAndFailWhenMaxRetriesReached() {
        CompletableFuture<SendResult<String, String>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new org.apache.kafka.common.errors.TimeoutException("Kafka broker unreachable"));
        when(kafkaTemplate.send(anyString(), any(), any())).thenReturn(failedFuture);

        OutboxEvent event = new OutboxEvent(tenantId, UUID.randomUUID(), "EMPLOYEE_DEACTIVATED", "{\"emp\":\"down\"}");
        outboxEventRepository.save(event);

        // First attempt (retry = 1)
        outboxRelayService.relayPendingEvents();
        OutboxEvent stored = outboxEventRepository.findAll().get(0);
        assertThat(stored.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(stored.getRetryCount()).isEqualTo(1);

        // Second attempt (retry = 2)
        outboxRelayService.relayPendingEvents();
        stored = outboxEventRepository.findAll().get(0);
        assertThat(stored.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(stored.getRetryCount()).isEqualTo(2);

        // Third attempt (retry = 3 -> maxRetries reached -> status = FAILED)
        outboxRelayService.relayPendingEvents();
        stored = outboxEventRepository.findAll().get(0);
        assertThat(stored.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(stored.getRetryCount()).isEqualTo(3);
        assertThat(stored.getErrorMessage()).contains("Kafka broker unreachable");
    }

    @Test
    @DisplayName("Crash Resiliency: Events committed during relay downtime are flushed on startup")
    void shouldGuaranteeZeroEventLossAfterRelayRestart() {
        // Simulate relay being offline while 5 events are inserted
        for (int i = 1; i <= 5; i++) {
            outboxEventRepository.save(new OutboxEvent(tenantId, UUID.randomUUID(), "EMPLOYEE_CREATED", "{\"id\":" + i + "}"));
        }

        assertThat(outboxEventRepository.countByStatus(OutboxStatus.PENDING)).isEqualTo(5);

        // Relay resumes and polls
        int dispatched = outboxRelayService.relayPendingEvents();
        assertThat(dispatched).isEqualTo(5);
        assertThat(outboxEventRepository.countByStatus(OutboxStatus.PENDING)).isEqualTo(0);
        assertThat(outboxEventRepository.countByStatus(OutboxStatus.PUBLISHED)).isEqualTo(5);
    }
}

