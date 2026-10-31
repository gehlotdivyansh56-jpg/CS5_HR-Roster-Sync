package com.movesync.rostersync.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.movesync.rostersync.downstream.BookingServiceClient;
import com.movesync.rostersync.downstream.SeatServiceClient;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class DeactivationHandler {

    private static final Logger log = LoggerFactory.getLogger(DeactivationHandler.class);

    private final BookingServiceClient bookingServiceClient;
    private final SeatServiceClient seatServiceClient;
    private final ObjectMapper objectMapper;
    private final Set<String> processedEventIds = ConcurrentHashMap.newKeySet();

    public DeactivationHandler(BookingServiceClient bookingServiceClient,
                               SeatServiceClient seatServiceClient,
                               ObjectMapper objectMapper) {
        this.bookingServiceClient = bookingServiceClient;
        this.seatServiceClient = seatServiceClient;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "${app.kafka.roster-events-topic:employee-roster-events}",
            groupId = "movesync-roster-consumer-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onRosterEvent(ConsumerRecord<String, String> record) {
        String payload = record.value();
        log.info("Consuming roster event key=[{}] partition=[{}] offset=[{}]",
                record.key(), record.partition(), record.offset());

        try {
            // Poison Pill Detection for Resiliency / DLQ testing
            if (payload != null && payload.contains("POISON_PILL")) {
                log.error("Encountered poison pill in event payload! Rejecting with unrecoverable exception.");
                throw new IllegalArgumentException("Unprocessable poison pill payload: " + record.key());
            }

            JsonNode root = objectMapper.readTree(payload);
            String eventType = root.path("eventType").asText();
            String eventId = root.path("eventId").asText();

            if (!"EMPLOYEE_DEACTIVATED".equalsIgnoreCase(eventType)) {
                log.debug("Skipping non-deactivation event [{}] with id [{}]", eventType, eventId);
                return;
            }

            // Consumer-side Idempotency / Deduplication check
            if (eventId != null && !eventId.isBlank() && processedEventIds.contains(eventId)) {
                log.warn("Duplicate deactivation event [{}] received. Skipping downstream execution.", eventId);
                return;
            }

            String tenantIdStr = root.path("tenantId").asText();
            String aggregateIdStr = root.path("aggregateId").asText();
            String sourceEmpId = root.path("sourceEmployeeId").asText();

            UUID tenantId = UUID.fromString(tenantIdStr);
            UUID employeeId = UUID.fromString(aggregateIdStr);

            log.info("Processing employee deactivation downstream effects for sourceId [{}] (employeeId: [{}], tenantId: [{}])",
                    sourceEmpId, employeeId, tenantId);

            // Trigger downstream cab booking cancellation & vehicle seat release
            bookingServiceClient.cancelActiveBookings(tenantId, employeeId);
            seatServiceClient.releaseAssignedSeats(tenantId, employeeId);

            if (eventId != null && !eventId.isBlank()) {
                processedEventIds.add(eventId);
            }

            log.info("Successfully executed all downstream deactivation handlers for [{}]", sourceEmpId);

        } catch (IllegalArgumentException ex) {
            // Re-throw poison pill or format error to trigger DLQ recovery
            throw ex;
        } catch (Exception ex) {
            log.error("Unexpected error processing roster event record key=[{}]", record.key(), ex);
            throw new RuntimeException("Consumer execution failed for record key " + record.key(), ex);
        }
    }

    public void clearDeduplicationCache() {
        processedEventIds.clear();
    }
}

