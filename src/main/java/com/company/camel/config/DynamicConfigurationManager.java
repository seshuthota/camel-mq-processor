package com.company.camel.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DYNAMIC CONFIGURATION MANAGER 🔄
 * 
 * Enables runtime configuration updates without system restart:
 * - Partner thread pool sizing
 * - Circuit breaker thresholds
 * - Retry policies
 * - Authentication settings
 * 
 * ZERO DOWNTIME CONFIGURATION UPDATES! 🚀
 */
@Slf4j
@Service
@RestController
@RequestMapping("/api/config")
public class DynamicConfigurationManager {

    @Autowired
    private PartnerConfigurationService configService;

    private final Map<String, ConfigurationChangeListener> listeners = new ConcurrentHashMap<>();

    /**
     * Get current configuration for a partner
     */
    @GetMapping("/partners/{businessUnit}")
    public PartnerConfigurationService.PartnerConfig getPartnerConfiguration(@PathVariable String businessUnit) {
        log.debug("📋 Configuration requested for partner: {}", businessUnit);
        return configService.getPartnerConfig(businessUnit);
    }

    /**
     * Get all partner configurations
     */
    @GetMapping("/partners")
    public Map<String, PartnerConfigurationService.PartnerConfig> getAllPartnerConfigurations() {
        log.debug("📋 All partner configurations requested");
        return configService.getAllPartnerConfigs();
    }

    /**
     * Update partner configuration at runtime
     */
    @PutMapping("/partners/{businessUnit}")
    public ConfigurationUpdateResponse updatePartnerConfiguration(
            @PathVariable String businessUnit,
            @RequestBody PartnerConfigurationService.PartnerConfig newConfig) {
        
        log.info("🔄 Configuration update requested for partner: {}", businessUnit);
        
        try {
            // Validate configuration
            validateConfiguration(newConfig);
            
            // Get old configuration for comparison
            PartnerConfigurationService.PartnerConfig oldConfig = configService.getPartnerConfig(businessUnit);
            
            // Update configuration
            boolean updated = configService.updatePartnerConfig(businessUnit, newConfig);
            
            if (updated) {
                // Notify listeners of configuration change
                notifyConfigurationChange(businessUnit, oldConfig, newConfig);
                
                log.info("✅ Configuration updated successfully for partner: {}", businessUnit);
                return ConfigurationUpdateResponse.success("Configuration updated successfully for " + businessUnit);
            } else {
                log.warn("⚠️ Configuration update failed for partner: {}", businessUnit);
                return ConfigurationUpdateResponse.error("Failed to update configuration for " + businessUnit);
            }
            
        } catch (Exception e) {
            log.error("💥 Configuration update error for partner: {} - {}", businessUnit, e.getMessage(), e);
            return ConfigurationUpdateResponse.error("Configuration update error: " + e.getMessage());
        }
    }

    /**
     * Create new partner configuration
     */
    @PostMapping("/partners/{businessUnit}")
    public ConfigurationUpdateResponse createPartnerConfiguration(
            @PathVariable String businessUnit,
            @RequestBody PartnerConfigurationService.PartnerConfig config) {
        
        log.info("➕ New configuration creation requested for partner: {}", businessUnit);
        
        try {
            validateConfiguration(config);
            config.setBusinessUnit(businessUnit);
            
            configService.addPartnerConfig(businessUnit, config);
            
            log.info("✅ New configuration created successfully for partner: {}", businessUnit);
            return ConfigurationUpdateResponse.success("Configuration created successfully for " + businessUnit);
            
        } catch (Exception e) {
            log.error("💥 Configuration creation error for partner: {} - {}", businessUnit, e.getMessage(), e);
            return ConfigurationUpdateResponse.error("Configuration creation error: " + e.getMessage());
        }
    }

    /**
     * Delete partner configuration
     */
    @DeleteMapping("/partners/{businessUnit}")
    public ConfigurationUpdateResponse deletePartnerConfiguration(@PathVariable String businessUnit) {
        log.info("🗑️ Configuration deletion requested for partner: {}", businessUnit);
        
        try {
            boolean deleted = configService.removePartnerConfig(businessUnit);
            
            if (deleted) {
                log.info("✅ Configuration deleted successfully for partner: {}", businessUnit);
                return ConfigurationUpdateResponse.success("Configuration deleted successfully for " + businessUnit);
            } else {
                log.warn("⚠️ Configuration not found for partner: {}", businessUnit);
                return ConfigurationUpdateResponse.error("Configuration not found for " + businessUnit);
            }
            
        } catch (Exception e) {
            log.error("💥 Configuration deletion error for partner: {} - {}", businessUnit, e.getMessage(), e);
            return ConfigurationUpdateResponse.error("Configuration deletion error: " + e.getMessage());
        }
    }

    /**
     * Bulk update configurations
     */
    @PutMapping("/partners/bulk")
    public BulkConfigurationUpdateResponse bulkUpdateConfigurations(
            @RequestBody Map<String, PartnerConfigurationService.PartnerConfig> configurations) {
        
        log.info("📦 Bulk configuration update requested for {} partners", configurations.size());
        
        BulkConfigurationUpdateResponse response = new BulkConfigurationUpdateResponse();
        
        configurations.forEach((businessUnit, config) -> {
            try {
                validateConfiguration(config);
                config.setBusinessUnit(businessUnit);
                
                PartnerConfigurationService.PartnerConfig oldConfig = configService.getPartnerConfig(businessUnit);
                boolean updated = configService.updatePartnerConfig(businessUnit, config);
                
                if (updated) {
                    notifyConfigurationChange(businessUnit, oldConfig, config);
                    response.addSuccess(businessUnit, "Configuration updated successfully");
                } else {
                    response.addError(businessUnit, "Failed to update configuration");
                }
                
            } catch (Exception e) {
                log.error("💥 Bulk update error for partner: {} - {}", businessUnit, e.getMessage());
                response.addError(businessUnit, "Configuration error: " + e.getMessage());
            }
        });
        
        log.info("✅ Bulk configuration update completed: {} successes, {} errors", 
            response.getSuccesses().size(), response.getErrors().size());
        
        return response;
    }

    /**
     * Validate configuration before applying
     */
    private void validateConfiguration(PartnerConfigurationService.PartnerConfig config) {
        if (config.getCoreThreads() <= 0) {
            throw new IllegalArgumentException("Core threads must be greater than 0");
        }
        if (config.getMaxThreads() < config.getCoreThreads()) {
            throw new IllegalArgumentException("Max threads must be >= core threads");
        }
        if (config.getQueueCapacity() <= 0) {
            throw new IllegalArgumentException("Queue capacity must be greater than 0");
        }
        if (config.getCircuitBreakerFailureThreshold() <= 0 || config.getCircuitBreakerFailureThreshold() > 100) {
            throw new IllegalArgumentException("Circuit breaker failure threshold must be between 0 and 100");
        }
        if (config.getRetryMaxAttempts() < 0) {
            throw new IllegalArgumentException("Retry max attempts must be >= 0");
        }
        if (config.getApiTimeoutSeconds() <= 0) {
            throw new IllegalArgumentException("API timeout must be greater than 0");
        }
    }

    /**
     * Register configuration change listener
     */
    public void registerConfigurationChangeListener(String listenerId, ConfigurationChangeListener listener) {
        listeners.put(listenerId, listener);
        log.info("📡 Configuration change listener registered: {}", listenerId);
    }

    /**
     * Unregister configuration change listener
     */
    public void unregisterConfigurationChangeListener(String listenerId) {
        listeners.remove(listenerId);
        log.info("📡 Configuration change listener unregistered: {}", listenerId);
    }

    /**
     * Notify all listeners of configuration changes
     */
    private void notifyConfigurationChange(String businessUnit, 
                                         PartnerConfigurationService.PartnerConfig oldConfig,
                                         PartnerConfigurationService.PartnerConfig newConfig) {
        
        CompletableFuture.runAsync(() -> {
            listeners.forEach((listenerId, listener) -> {
                try {
                    listener.onConfigurationChanged(businessUnit, oldConfig, newConfig);
                } catch (Exception e) {
                    log.error("💥 Error notifying configuration listener {}: {}", listenerId, e.getMessage());
                }
            });
        });
    }

    /**
     * Configuration change listener interface
     */
    public interface ConfigurationChangeListener {
        void onConfigurationChanged(String businessUnit, 
                                  PartnerConfigurationService.PartnerConfig oldConfig,
                                  PartnerConfigurationService.PartnerConfig newConfig);
    }

    /**
     * Configuration update response
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class ConfigurationUpdateResponse {
        private boolean success;
        private String message;
        private long timestamp;

        public static ConfigurationUpdateResponse success(String message) {
            return new ConfigurationUpdateResponse(true, message, System.currentTimeMillis());
        }

        public static ConfigurationUpdateResponse error(String message) {
            return new ConfigurationUpdateResponse(false, message, System.currentTimeMillis());
        }
    }

    /**
     * Bulk configuration update response
     */
    @lombok.Data
    public static class BulkConfigurationUpdateResponse {
        private Map<String, String> successes = new ConcurrentHashMap<>();
        private Map<String, String> errors = new ConcurrentHashMap<>();
        private long timestamp = System.currentTimeMillis();

        public void addSuccess(String businessUnit, String message) {
            successes.put(businessUnit, message);
        }

        public void addError(String businessUnit, String message) {
            errors.put(businessUnit, message);
        }

        public boolean hasErrors() {
            return !errors.isEmpty();
        }

        public int getTotalProcessed() {
            return successes.size() + errors.size();
        }
    }
}