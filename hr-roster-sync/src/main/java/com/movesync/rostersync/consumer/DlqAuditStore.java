package com.movesync.rostersync.consumer;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class DlqAuditStore {

    public record DeadLetterRecord(String key, String payload, String exceptionMessage, Instant timestamp) {}

    private final List<DeadLetterRecord> records = new CopyOnWriteArrayList<>();

    public void recordDeadLetter(String key, String payload, String exceptionMessage) {
        records.add(new DeadLetterRecord(key, payload, exceptionMessage, Instant.now()));
    }

    public List<DeadLetterRecord> getRecords() {
        return List.copyOf(records);
    }

    public void clear() {
        records.clear();
    }
}

