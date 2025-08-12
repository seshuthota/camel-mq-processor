package com.company.camel.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PARTNER CONFIGURATION SERVICE 🎯
 * 
 * Manages partner-specific configurations including:
 * - Thread pool settings per partner
 * - Circuit breaker thresholds
 * - Retry policies
 * - Authentication settings
 * - API endpoints and timeouts
 */
@Slf4j
@Service
public class PartnerConfigurationService {

    @Value("${app.partners.load-from-elastic:true}")
    private boolean loadFromElastic;

    @Value("${app.partners.default-consumer-count:10}")
    private int defaultConsumerCount;

    private final Map<String, PartnerConfig> partnerConfigs = new ConcurrentHashMap<>();

    @PostConstruct
    public void initialize() {
        log.info("🎯 Initializing Partner Configuration Service...");
        loadDefaultConfigurations();
        log.info("✅ Partner Configuration Service initialized with {} partners", partnerConfigs.size());
    }

    /**
     * Load default configurations for common partners
     */
    private void loadDefaultConfigurations() {
        // Add some default partner configurations
        addPartnerConfig("AMAZON", PartnerConfig.builder()
            .businessUnit("AMAZON")
            .coreThreads(10)
            .maxThreads(50)
            .queueCapacity(2000)
            .circuitBreakerFailureThreshold(60.0f)
            .circuitBreakerMinCalls(20)
            .circuitBreakerWaitDuration(45)
            .retryMaxAttempts(5)
            .retryBackoffMultiplier(2.0)
            .authTokenExpiryMinutes(30)
            .apiTimeoutSeconds(30)
            .priority(PartnerPriority.HIGH)
            .build());

        addPartnerConfig("FLIPKART", PartnerConfig.builder()
            .businessUnit("FLIPKART")
            .coreThreads(8)
            .maxThreads(40)
            .queueCapacity(1500)
            .circuitBreakerFailureThreshold(50.0f)
            .circuitBreakerMinCalls(15)
            .circuitBreakerWaitDuration(30)
            .retryMaxAttempts(4)
            .retryBackoffMultiplier(1.5)
            .authTokenExpiryMinutes(25)
            .apiTimeoutSeconds(25)
            .priority(PartnerPriority.HIGH)
            .build());

        addPartnerConfig("MYNTRA", PartnerConfig.builder()
            .businessUnit("MYNTRA")
            .coreThreads(6)
            .maxThreads(30)
            .queueCapacity(1000)
            .circuitBreakerFailureThreshold(45.0f)
            .circuitBreakerMinCalls(12)
            .circuitBreakerWaitDuration(25)
            .retryMaxAttempts(3)
            .retryBackoffMultiplier(1.2)
            .authTokenExpiryMinutes(20)
            .apiTimeoutSeconds(20)
            .priority(PartnerPriority.MEDIUM)
            .build());

        // Add default configuration for unknown partners
        addPartnerConfig("DEFAULT", PartnerConfig.builder()
            .businessUnit("DEFAULT")
            .coreThreads(5)
            .maxThreads(20)
            .queueCapacity(1000)
            .circuitBreakerFailureThreshold(50.0f)
            .circuitBreakerMinCalls(10)
            .circuitBreakerWaitDuration(30)
            .retryMaxAttempts(3)
            .retryBackoffMultiplier(1.5)
            .authTokenExpiryMinutes(15)
            .apiTimeoutSeconds(15)
            .priority(PartnerPriority.LOW)
            .build());
    }

    /**
     * Get configuration for a specific partner
     */
    public PartnerConfig getPartnerConfig(String businessUnit) {
        PartnerConfig config = partnerConfigs.get(businessUnit);
        if (config == null) {
            log.warn("⚠️ No specific config found for partner: {}, using DEFAULT", businessUnit);
            config = partnerConfigs.get("DEFAULT");
        }
        return config;
    }

    /**
     * Add or update partner configuration
     */
    public void addPartnerConfig(String businessUnit, PartnerConfig config) {
        partnerConfigs.put(businessUnit, config);
        log.info("✅ Configuration added/updated for partner: {}", businessUnit);
    }

    /**
     * Get all partner configurations
     */
    public Map<String, PartnerConfig> getAllPartnerConfigs() {
        return new ConcurrentHashMap<>(partnerConfigs);
    }

    /**
     * Update partner configuration at runtime
     */
    public boolean updatePartnerConfig(String businessUnit, PartnerConfig newConfig) {
        if (partnerConfigs.containsKey(businessUnit)) {
            partnerConfigs.put(businessUnit, newConfig);
            log.info("🔄 Configuration updated for partner: {}", businessUnit);
            return true;
        }
        log.warn("⚠️ Cannot update config for unknown partner: {}", businessUnit);
        return false;
    }

    /**
     * Remove partner configuration
     */
    public boolean removePartnerConfig(String businessUnit) {
        if (partnerConfigs.remove(businessUnit) != null) {
            log.info("🗑️ Configuration removed for partner: {}", businessUnit);
            return true;
        }
        return false;
    }

    /**
     * Partner configuration data class
     */
    @Data
    @lombok.Builder
    public static class PartnerConfig {
        private String businessUnit;
        
        // Thread pool settings
        private int coreThreads;
        private int maxThreads;
        private int queueCapacity;
        @lombok.Builder.Default
        private long keepAliveSeconds = 60;
        
        // Circuit breaker settings
        private float circuitBreakerFailureThreshold;
        private int circuitBreakerMinCalls;
        private int circuitBreakerWaitDuration;
        
        // Retry settings
        private int retryMaxAttempts;
        private double retryBackoffMultiplier;
        @lombok.Builder.Default
        private long retryInitialDelayMs = 1000;
        
        // Authentication settings
        private int authTokenExpiryMinutes;
        private String authEndpoint;
        @lombok.Builder.Default
        private String authMethod = "POST";
        
        // API settings
        private int apiTimeoutSeconds;
        private String apiEndpoint;
        @lombok.Builder.Default
        private int maxConcurrentCalls = 25;
        
        // Partner priority
        private PartnerPriority priority;
        
        // Health check settings
        @lombok.Builder.Default
        private boolean healthCheckEnabled = true;
        @lombok.Builder.Default
        private int healthCheckIntervalSeconds = 60;
        
        // Monitoring settings
        @lombok.Builder.Default
        private boolean metricsEnabled = true;
        @lombok.Builder.Default
        private boolean alertingEnabled = true;
        
        // Convenience method to get queue name
        public String getQueueName() {
            return "partner." + businessUnit + ".queue";
        }
    }

    /**
     * Partner priority levels
     */
    public enum PartnerPriority {
        HIGH(1),
        MEDIUM(2), 
        LOW(3);
        
        private final int level;
        
        PartnerPriority(int level) {
            this.level = level;
        }
        
        public int getLevel() {
            return level;
        }
    }
}