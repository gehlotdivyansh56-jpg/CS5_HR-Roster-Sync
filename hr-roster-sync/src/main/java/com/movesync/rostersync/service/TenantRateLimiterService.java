package com.movesync.rostersync.service;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class TenantRateLimiterService {

    private final Map<UUID, TokenBucket> buckets = new ConcurrentHashMap<>();
    private int capacity = 500; // 500 requests per window
    private long windowMillis = 1000; // 1 second window

    public boolean tryAcquire(UUID tenantId) {
        if (tenantId == null) {
            return true;
        }
        return buckets.computeIfAbsent(tenantId, id -> new TokenBucket(capacity, windowMillis)).tryConsume();
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
        buckets.clear();
    }

    public void clear() {
        buckets.clear();
    }

    private static class TokenBucket {
        private final int maxCapacity;
        private final long windowDurationMs;
        private final AtomicInteger tokens;
        private volatile long windowStart;

        public TokenBucket(int maxCapacity, long windowDurationMs) {
            this.maxCapacity = maxCapacity;
            this.windowDurationMs = windowDurationMs;
            this.tokens = new AtomicInteger(maxCapacity);
            this.windowStart = System.currentTimeMillis();
        }

        public synchronized boolean tryConsume() {
            long now = System.currentTimeMillis();
            if (now - windowStart >= windowDurationMs) {
                tokens.set(maxCapacity);
                windowStart = now;
            }
            if (tokens.get() > 0) {
                tokens.decrementAndGet();
                return true;
            }
            return false;
        }
    }
}

