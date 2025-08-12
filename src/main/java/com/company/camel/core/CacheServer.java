package com.company.camel.core;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enhanced CacheServer with Thread-Safe Collections
 * 
 * Your original cache server but now using ConcurrentHashMap everywhere
 * for thread safety across multiple partner threads.
 */
@Component
@Data
@AllArgsConstructor
@NoArgsConstructor
@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
public class CacheServer {

    // Thread-safe collections for multi-partner concurrent access
    public ConcurrentHashMap<String, Set<String>> authHeaderMap = new ConcurrentHashMap<>();
    
    public ConcurrentHashMap<String, Set<String>> statusHeaderMap = new ConcurrentHashMap<>();
    
    public ConcurrentHashMap<String, HashMap<String, String>> authBodyMap = new ConcurrentHashMap<>();

    // Static for global token management across all instances
    public static ConcurrentHashMap<String, String> tokenMap = new ConcurrentHashMap<>();
    
    public static ConcurrentHashMap<String, String> refreshTokenMap = new ConcurrentHashMap<>();

    public static ConcurrentHashMap<String, LocalDateTime> tokenGenerationTimeMap = new ConcurrentHashMap<>();
}