package com.movesync.rostersync.consumer;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class DlqListener {

    private static final Logger log = LoggerFactory.getLogger(DlqListener.class);
    private final DlqAuditStore dlqAuditStore;
    private final com.movesync.rostersync.metrics.SyncMetricsService syncMetricsService;

    public DlqListener(DlqAuditStore dlqAuditStore,
                       com.movesync.rostersync.metrics.SyncMetricsService syncMetricsService) {
        this.dlqAuditStore = dlqAuditStore;
        this.syncMetricsService = syncMetricsService;
    }

    @KafkaListener(
            topics = "${app.kafka.dlq-topic:employee-events-dlq}",
            groupId = "movesync-roster-dlq-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onDeadLetterEvent(ConsumerRecord<String, String> record) {
        String exceptionMessage = "Unknown";
        for (Header header : record.headers()) {
            if ("kafka_dlt-exception-message".equalsIgnoreCase(header.key()) ||
                KafkaHeaders.DLT_EXCEPTION_MESSAGE.equalsIgnoreCase(header.key())) {
                exceptionMessage = new String(header.value(), StandardCharsets.UTF_8);
            }
        }

        log.warn("RECEIVED DLQ DEAD-LETTER MESSAGE: Topic=[{}], Partition=[{}], Offset=[{}], Key=[{}], Reason=[{}]",
                record.topic(), record.partition(), record.offset(), record.key(), exceptionMessage);

        dlqAuditStore.recordDeadLetter(record.key(), record.value(), exceptionMessage);
        syncMetricsService.recordDlqPublished();
    }
}
