package com.movesync.rostersync.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.movesync.rostersync.downstream.DownstreamAuditStore;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(
        partitions = 1,
        topics = {"employee-roster-events", "employee-events-dlq"},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
@TestPropertySource(properties = {
        "app.kafka.enabled=true",
        "app.outbox.enabled=false",
        "spring.kafka.listener.auto-startup=true",
        "spring.kafka.consumer.auto-offset-reset=earliest"
})
class DeactivationHandlerIntegrationTest {

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Autowired
    private DownstreamAuditStore downstreamAuditStore;

    @Autowired
    private DlqAuditStore dlqAuditStore;

    @Autowired
    private DeactivationHandler deactivationHandler;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${app.kafka.roster-events-topic:employee-roster-events}")
    private String rosterEventsTopic;

    private KafkaTemplate<String, String> rawKafkaTemplate;

    @BeforeEach
    void setUp() {
        downstreamAuditStore.clear();
        dlqAuditStore.clear();
        deactivationHandler.clearDeduplicationCache();

        Map<String, Object> producerProps = new HashMap<>(KafkaTestUtils.producerProps(embeddedKafkaBroker));
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        rawKafkaTemplate = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(producerProps));
    }

    @Test
    @DisplayName("4.4 & 4.5: Consuming EMPLOYEE_DEACTIVATED triggers downstream ride cancellation & seat release")
    void shouldTriggerDownstreamCancellationsOnDeactivationEvent() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        String eventId = UUID.randomUUID().toString();

        Map<String, Object> payload = Map.of(
                "eventId", eventId,
                "eventType", "EMPLOYEE_DEACTIVATED",
                "tenantId", tenantId.toString(),
                "aggregateId", employeeId.toString(),
                "sourceEmployeeId", "EMP-900",
                "version", 100L
        );

        rawKafkaTemplate.send(rosterEventsTopic, employeeId.toString(), objectMapper.writeValueAsString(payload)).get(5, TimeUnit.SECONDS);

        // Await downstream cancellation and seat release records
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(downstreamAuditStore.getCancellations()).hasSize(1);
            assertThat(downstreamAuditStore.getSeatReleases()).hasSize(1);
        });

        DownstreamAuditStore.BookingCancellationRecord cancellation = downstreamAuditStore.getCancellations().get(0);
        assertThat(cancellation.tenantId()).isEqualTo(tenantId);
        assertThat(cancellation.employeeId()).isEqualTo(employeeId);

        DownstreamAuditStore.SeatReleaseRecord seatRelease = downstreamAuditStore.getSeatReleases().get(0);
        assertThat(seatRelease.tenantId()).isEqualTo(tenantId);
        assertThat(seatRelease.employeeId()).isEqualTo(employeeId);
    }

    @Test
    @DisplayName("Consumer Idempotency: Replayed deactivation event does not trigger duplicate downstream cancellation")
    void shouldDeduplicateReplayedEvents() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        String eventId = "UNIQUE-EVENT-" + UUID.randomUUID();

        Map<String, Object> payload = Map.of(
                "eventId", eventId,
                "eventType", "EMPLOYEE_DEACTIVATED",
                "tenantId", tenantId.toString(),
                "aggregateId", employeeId.toString(),
                "sourceEmployeeId", "EMP-901"
        );

        String json = objectMapper.writeValueAsString(payload);

        // Send first time
        rawKafkaTemplate.send(rosterEventsTopic, employeeId.toString(), json).get(5, TimeUnit.SECONDS);
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(downstreamAuditStore.getCancellations()).hasSize(1);
        });

        // Replay exact same event
        rawKafkaTemplate.send(rosterEventsTopic, employeeId.toString(), json).get(5, TimeUnit.SECONDS);

        // Give consumer opportunity to process; count must strictly remain 1
        Thread.sleep(1000);
        assertThat(downstreamAuditStore.getCancellations()).hasSize(1);
        assertThat(downstreamAuditStore.getSeatReleases()).hasSize(1);
    }

    @Test
    @DisplayName("4.6: Poison pill payload retries and is safely routed to Dead Letter Queue (DLQ)")
    void shouldRoutePoisonPillToDeadLetterQueue() throws Exception {
        String poisonKey = "POISON-" + UUID.randomUUID();
        String poisonPayload = "{\"eventType\":\"EMPLOYEE_DEACTIVATED\", \"content\":\"POISON_PILL_TEST\"}";

        rawKafkaTemplate.send(rosterEventsTopic, poisonKey, poisonPayload).get(5, TimeUnit.SECONDS);

        // Verify that Dead Letter Queue received the unprocessable event
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            assertThat(dlqAuditStore.getRecords()).hasSize(1);
        });

        DlqAuditStore.DeadLetterRecord dlqRecord = dlqAuditStore.getRecords().get(0);
        assertThat(dlqRecord.key()).isEqualTo(poisonKey);
        assertThat(dlqRecord.payload()).contains("POISON_PILL_TEST");
        assertThat(dlqRecord.exceptionMessage()).contains("Unprocessable poison pill payload");

        // Verify partition was not blocked and normal messages continue to process
        UUID normalEmpId = UUID.randomUUID();
        Map<String, Object> normalPayload = Map.of(
                "eventId", UUID.randomUUID().toString(),
                "eventType", "EMPLOYEE_DEACTIVATED",
                "tenantId", UUID.randomUUID().toString(),
                "aggregateId", normalEmpId.toString(),
                "sourceEmployeeId", "EMP-HEALTHY"
        );
        rawKafkaTemplate.send(rosterEventsTopic, normalEmpId.toString(), objectMapper.writeValueAsString(normalPayload)).get(5, TimeUnit.SECONDS);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(downstreamAuditStore.getCancellations()).hasSize(1);
        });
    }
}

