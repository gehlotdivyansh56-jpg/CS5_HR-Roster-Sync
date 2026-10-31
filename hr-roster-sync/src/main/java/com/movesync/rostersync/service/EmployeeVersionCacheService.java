package com.movesync.rostersync.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class EmployeeVersionCacheService {

    private static final Logger log = LoggerFactory.getLogger(EmployeeVersionCacheService.class);
    private final Cache<Object, Object> employeeVersionCache;

    public EmployeeVersionCacheService(@Qualifier("employeeVersionCache") Cache<Object, Object> employeeVersionCache) {
        this.employeeVersionCache = employeeVersionCache;
    }

    private String buildKey(UUID tenantId, String sourceEmployeeId) {
        return tenantId + ":" + sourceEmployeeId;
    }

    public Long getVersion(UUID tenantId, String sourceEmployeeId) {
        if (tenantId == null || sourceEmployeeId == null) {
            return null;
        }
        Object val = employeeVersionCache.getIfPresent(buildKey(tenantId, sourceEmployeeId));
        if (val instanceof Long l) {
            return l;
        } else if (val instanceof Number n) {
            return n.longValue();
        }
        return null;
    }

    /**
     * Write-Through update: guarantees 0ms staleness for the current node.
     */
    public void putVersion(UUID tenantId, String sourceEmployeeId, Long version) {
        if (tenantId != null && sourceEmployeeId != null && version != null) {
            employeeVersionCache.put(buildKey(tenantId, sourceEmployeeId), version);
            log.debug("Write-through version cache updated for employee [{}] to version [{}]", sourceEmployeeId, version);
        }
    }

    public void evict(UUID tenantId, String sourceEmployeeId) {
        if (tenantId != null && sourceEmployeeId != null) {
            employeeVersionCache.invalidate(buildKey(tenantId, sourceEmployeeId));
        }
    }

    public void clear() {
        employeeVersionCache.invalidateAll();
    }

    public double getHitRate() {
        CacheStats stats = employeeVersionCache.stats();
        return stats.requestCount() > 0 ? stats.hitRate() : 1.0;
    }

    public long getEstimatedSize() {
        return employeeVersionCache.estimatedSize();
    }
}
