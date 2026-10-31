package com.movesync.rostersync.controller;

import com.github.benmanes.caffeine.cache.Cache;
import com.movesync.rostersync.auth.RequiresRole;
import com.movesync.rostersync.config.CacheConfig;
import com.movesync.rostersync.service.EmployeeVersionCacheService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.CacheManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/cache")
public class CacheAdminController {

    private final Cache<Object, Object> officeExistenceCache;
    private final EmployeeVersionCacheService versionCacheService;
    private final CacheManager cacheManager;

    public CacheAdminController(@Qualifier("officeExistenceCache") Cache<Object, Object> officeExistenceCache,
                                EmployeeVersionCacheService versionCacheService,
                                CacheManager cacheManager) {
        this.officeExistenceCache = officeExistenceCache;
        this.versionCacheService = versionCacheService;
        this.cacheManager = cacheManager;
    }

    @PostMapping("/invalidate")
    @RequiresRole("ADMIN")
    public ResponseEntity<Map<String, Object>> invalidateAllCaches() {
        officeExistenceCache.invalidateAll();
        versionCacheService.clear();

        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "All in-process Caffeine caches invalidated successfully",
                "officeCacheEstimatedSize", officeExistenceCache.estimatedSize(),
                "versionCacheEstimatedSize", versionCacheService.getEstimatedSize()
        ));
    }

    @GetMapping("/stats")
    @RequiresRole("ADMIN")
    public ResponseEntity<Map<String, Object>> getCacheStats() {
        var officeStats = officeExistenceCache.stats();

        return ResponseEntity.ok(Map.of(
                "officeCache", Map.of(
                        "hitRate", officeStats.hitRate(),
                        "hitCount", officeStats.hitCount(),
                        "missCount", officeStats.missCount(),
                        "estimatedSize", officeExistenceCache.estimatedSize()
                ),
                "versionCache", Map.of(
                        "hitRate", versionCacheService.getHitRate(),
                        "estimatedSize", versionCacheService.getEstimatedSize()
                )
        ));
    }
}
