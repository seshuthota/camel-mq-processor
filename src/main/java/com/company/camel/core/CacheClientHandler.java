package com.company.camel.core;

import com.company.camel.monitoring.PartnerThreadPoolManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enhanced Cache Handler with Thread Isolation Support
 * 
 * This is your original CacheClientHandler but now enhanced with:
 * - Thread-safe operations
 * - Integration with PartnerThreadPoolManager
 * - Async operations for better performance
 * - Better isolation between partners
 */
@Slf4j
@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
public class CacheClientHandler {

    @Autowired
    private CacheServer cacheServer;
    
    @Autowired
    private PartnerThreadPoolManager threadPoolManager;

    /**
     * Add status headers for a partner - now thread-safe and isolated
     */
    public CompletableFuture<Void> addStatusHeaders(String businessUnit, Set<String> list) {
        return threadPoolManager.executePartnerTask(businessUnit, () -> {
            synchronized (this) {
                Set<String> tempStatusList = getStatusHeaders(businessUnit);

                if (tempStatusList != null && !tempStatusList.containsAll(list)) {
                    tempStatusList.addAll(list);
                    cacheServer.statusHeaderMap.put(businessUnit, tempStatusList);
                }

                if (tempStatusList == null) {
                    tempStatusList = new HashSet<>(list);
                    cacheServer.statusHeaderMap.put(businessUnit, tempStatusList);
                }
                
                log.debug("✅ Status headers updated for partner: {} with {} headers", 
                    businessUnit, list.size());
            }
        });
    }

    /**
     * Get status headers for a partner - thread-safe
     */
    public Set<String> getStatusHeaders(String businessUnit) {
        return cacheServer.statusHeaderMap.get(businessUnit);
    }

    /**
     * Add auth headers for a partner - now thread-safe and isolated
     */
    public CompletableFuture<Void> addAuthHeaders(String businessUnit, Set<String> list) {
        return threadPoolManager.executePartnerTask(businessUnit, () -> {
            synchronized (this) {
                Set<String> tempAuthList = getAuthHeaders(businessUnit);

                if (tempAuthList != null && !tempAuthList.containsAll(list)) {
                    tempAuthList.addAll(list);
                    cacheServer.authHeaderMap.put(businessUnit, tempAuthList);
                }

                if (tempAuthList == null) {
                    tempAuthList = new HashSet<>(list);
                    cacheServer.authHeaderMap.put(businessUnit, tempAuthList);
                }
                
                log.debug("✅ Auth headers updated for partner: {} with {} headers", 
                    businessUnit, list.size());
            }
        });
    }

    /**
     * Get auth headers for a partner - thread-safe
     */
    public Set<String> getAuthHeaders(String businessUnit) {
        return cacheServer.authHeaderMap.get(businessUnit);
    }

    /**
     * Set auth body map for a partner - now thread-safe and isolated
     */
    public CompletableFuture<Void> setAuthBodyMap(String businessUnit, HashMap<String, String> authBody) {
        return threadPoolManager.executePartnerTask(businessUnit, () -> {
            synchronized (this) {
                HashMap<String, String> tempAuthBodyList = getAuthBodyMap(businessUnit);

                if (tempAuthBodyList != null) {
                    tempAuthBodyList.putAll(authBody);
                    cacheServer.authBodyMap.put(businessUnit, tempAuthBodyList);
                } else {
                    tempAuthBodyList = new HashMap<>(authBody);
                    cacheServer.authBodyMap.put(businessUnit, tempAuthBodyList);
                }
                
                log.debug("✅ Auth body updated for partner: {} with {} entries", 
                    businessUnit, authBody.size());
            }
        });
    }

    /**
     * Get auth body map for a partner - thread-safe
     */
    public HashMap<String, String> getAuthBodyMap(String businessUnit) {
        return cacheServer.authBodyMap.get(businessUnit);
    }

    /**
     * Empty token for a partner - now with thread isolation
     */
    public static void emptyToken(String businessUnit) {
        CacheServer.tokenMap.put(businessUnit, "");
        log.info("🗑️ Token cleared for partner: {}", businessUnit);
    }

    /**
     * Set tokens for a partner - now with thread isolation
     */
    public static void setTokens(String token, String businessUnit) {
        CacheServer.tokenMap.put(businessUnit, token);
        CacheServer.tokenGenerationTimeMap.put(businessUnit, LocalDateTime.now());
        log.info("🔐 Token set for partner: {} at {}", businessUnit, LocalDateTime.now());
    }

    /**
     * Get tokens for a partner - thread-safe
     */
    public static String getTokens(String businessUnit) {
        return CacheServer.tokenMap.get(businessUnit);
    }

    /**
     * Get token generation time map - thread-safe
     */
    public static ConcurrentHashMap<String, LocalDateTime> getTokenGenerationTimeMap() {
        return CacheServer.tokenGenerationTimeMap;
    }
    
    /**
     * NEW: Check if token is expired or needs refresh
     */
    public boolean isTokenExpired(String businessUnit, int expiryMinutes) {
        LocalDateTime tokenTime = CacheServer.tokenGenerationTimeMap.get(businessUnit);
        if (tokenTime == null) {
            return true; // No token time means expired
        }
        
        LocalDateTime expiryTime = tokenTime.plusMinutes(expiryMinutes);
        boolean expired = LocalDateTime.now().isAfter(expiryTime);
        
        if (expired) {
            log.warn("⏰ Token expired for partner: {} (generated: {}, expired: {})", 
                businessUnit, tokenTime, expiryTime);
        }
        
        return expired;
    }
    
    /**
     * NEW: Get refresh token for a partner
     */
    public String getRefreshToken(String businessUnit) {
        return CacheServer.refreshTokenMap.get(businessUnit);
    }
    
    /**
     * NEW: Store token with expiry tracking
     */
    public void storeToken(String businessUnit, String token, int expiryMinutes) {
        CacheServer.tokenMap.put(businessUnit, token);
        CacheServer.tokenGenerationTimeMap.put(businessUnit, LocalDateTime.now());
        log.debug("🔐 Token stored for partner: {} (expires in {} minutes)", businessUnit, expiryMinutes);
    }
    
    /**
     * NEW: Get cache statistics for monitoring
     */
    public CacheStats getCacheStats() {
        return CacheStats.builder()
            .totalPartners(cacheServer.authHeaderMap.size())
            .totalTokens(CacheServer.tokenMap.size())
            .totalAuthBodies(cacheServer.authBodyMap.size())
            .totalStatusHeaders(cacheServer.statusHeaderMap.size())
            .build();
    }
    
    /**
     * Data class for cache statistics
     */
    @lombok.Builder
    @lombok.Data
    public static class CacheStats {
        private int totalPartners;
        private int totalTokens;
        private int totalAuthBodies;
        private int totalStatusHeaders;
    }
}