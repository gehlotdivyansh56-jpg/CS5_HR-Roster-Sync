package com.movesync.rostersync.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    public static final String OFFICE_CACHE_NAME = "officeExistence";
    public static final String VERSION_CACHE_NAME = "employeeVersion";

    @Bean
    public Cache<Object, Object> officeExistenceCache() {
        return Caffeine.newBuilder()
                .maximumSize(2000)
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .recordStats()
                .build();
    }

    @Bean
    public Cache<Object, Object> employeeVersionCache() {
        return Caffeine.newBuilder()
                .maximumSize(200000)
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .recordStats()
                .build();
    }

    @Bean
    public CacheManager cacheManager(Cache<Object, Object> officeExistenceCache,
                                     Cache<Object, Object> employeeVersionCache) {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.registerCustomCache(OFFICE_CACHE_NAME, officeExistenceCache);
        cacheManager.registerCustomCache(VERSION_CACHE_NAME, employeeVersionCache);
        return cacheManager;
    }
}
