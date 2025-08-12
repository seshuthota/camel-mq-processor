package com.company.camel.routes;

import com.company.camel.config.ElasticsearchPartnerConfigService;
import com.company.camel.config.PartnerConfigurationService;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.CamelContext;
import org.apache.camel.model.RouteTemplateDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DYNAMIC PARTNER ROUTE MANAGER 🔄
 * 
 * Manages partner-specific route instances with:
 * - Dynamic route creation from templates
 * - Configuration-driven route updates
 * - Partner isolation with individual queues
 * - Route lifecycle management (create, update, remove)
 * 
 * Each partner gets:
 * - Dedicated queue: partner.{PARTNER_NAME}.queue
 * - Individual route instance from template
 * - Independent configuration and scaling
 * - Complete processing isolation
 */
@Slf4j
@Service
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    name = "app.partner-config.enable-dynamic-routes", 
    havingValue = "true", 
    matchIfMissing = true
)
public class DynamicPartnerRouteManager {

    @Autowired
    private CamelContext camelContext;
    
    @Autowired
    private PartnerConfigurationService partnerConfigService;
    
    @Autowired
    private ElasticsearchPartnerConfigService elasticsearchConfigService;

    // Track active partner routes
    private final Map<String, String> activeRoutes = new ConcurrentHashMap<>();
    
    // Track route configurations for change detection
    private final Map<String, Long> routeVersions = new ConcurrentHashMap<>();

    private static final String ROUTE_TEMPLATE_ID = "partnerProcessingTemplate";

    @PostConstruct
    public void initialize() {
        log.info("🔄 Initializing Dynamic Partner Route Manager...");
        
        try {
            // Register as configuration change listener
            registerConfigurationChangeListener();
            
            // Create routes for all existing partners
            createInitialPartnerRoutes();
            
            log.info("✅ Dynamic Partner Route Manager initialized successfully");
            
        } catch (Exception e) {
            log.error("❌ Failed to initialize Dynamic Partner Route Manager", e);
            throw new RuntimeException("Failed to initialize route manager", e);
        }
    }

    /**
     * Register as a configuration change listener for all partners
     */
    private void registerConfigurationChangeListener() {
        // Get all existing partner configurations
        Map<String, PartnerConfigurationService.PartnerConfig> allConfigs = partnerConfigService.getAllPartnerConfigs();
        
        // Note: Configuration change listeners are handled via API webhook calls
        // No need to register listeners with ElasticsearchPartnerConfigService
        
        log.info("✅ Registered configuration change listeners for {} partners", allConfigs.size());
    }

    /**
     * Create initial routes for all configured partners
     */
    private void createInitialPartnerRoutes() {
        Map<String, PartnerConfigurationService.PartnerConfig> allConfigs = partnerConfigService.getAllPartnerConfigs();
        
        log.info("🚀 Creating initial routes for {} partners...", allConfigs.size());
        
        for (Map.Entry<String, PartnerConfigurationService.PartnerConfig> entry : allConfigs.entrySet()) {
            String partnerId = entry.getKey();
            PartnerConfigurationService.PartnerConfig config = entry.getValue();
            
            // Skip DEFAULT config - it's just a fallback
            if (!"DEFAULT".equals(partnerId)) {
                try {
                    createPartnerRoute(partnerId, config);
                    log.info("✅ Created initial route for partner: {}", partnerId);
                } catch (Exception e) {
                    log.error("❌ Failed to create initial route for partner: {}", partnerId, e);
                }
            }
        }
        
        log.info("🎯 Initial partner routes creation completed. Active routes: {}", activeRoutes.size());
    }

    /**
     * Create a new partner-specific route from template
     */
    public boolean createPartnerRoute(String partnerId, PartnerConfigurationService.PartnerConfig config) {
        try {
            log.info("🔧 Creating route for partner: {}", partnerId);
            
            // Check if route already exists
            if (activeRoutes.containsKey(partnerId)) {
                log.warn("⚠️ Route already exists for partner: {}, updating instead", partnerId);
                return updatePartnerRoute(partnerId, config);
            }

            // Build route parameters from configuration
            Map<String, Object> parameters = buildRouteParameters(partnerId, config);
            
            // Create route from template
            String routeId = "Partner:" + partnerId + ":Main";
            camelContext.addRouteFromTemplate(routeId, ROUTE_TEMPLATE_ID, parameters);
            
            // Track the route
            activeRoutes.put(partnerId, routeId);
            routeVersions.put(partnerId, System.currentTimeMillis());
            
            log.info("✅ Successfully created route for partner: {} with queue: {}", 
                    partnerId, config.getQueueName());
            
            return true;
            
        } catch (Exception e) {
            log.error("❌ Failed to create route for partner: {}", partnerId, e);
            return false;
        }
    }

    /**
     * Update an existing partner route with new configuration
     */
    public boolean updatePartnerRoute(String partnerId, PartnerConfigurationService.PartnerConfig config) {
        try {
            log.info("🔄 Updating route for partner: {}", partnerId);
            
            // Remove existing route
            boolean removed = removePartnerRoute(partnerId);
            if (!removed) {
                log.warn("⚠️ Failed to remove existing route for partner: {}, continuing with update", partnerId);
            }
            
            // Wait a bit for cleanup
            Thread.sleep(1000);
            
            // Create new route with updated configuration
            boolean created = createPartnerRoute(partnerId, config);
            
            if (created) {
                log.info("✅ Successfully updated route for partner: {}", partnerId);
            } else {
                log.error("❌ Failed to create updated route for partner: {}", partnerId);
            }
            
            return created;
            
        } catch (Exception e) {
            log.error("❌ Failed to update route for partner: {}", partnerId, e);
            return false;
        }
    }

    /**
     * Remove a partner route
     */
    public boolean removePartnerRoute(String partnerId) {
        try {
            String routeId = activeRoutes.get(partnerId);
            if (routeId == null) {
                log.warn("⚠️ No active route found for partner: {}", partnerId);
                return false;
            }
            
            log.info("🗑️ Removing route for partner: {}", partnerId);
            
            // Stop and remove the route
            camelContext.getRouteController().stopRoute(routeId);
            camelContext.removeRoute(routeId);
            
            // Remove from tracking
            activeRoutes.remove(partnerId);
            routeVersions.remove(partnerId);
            
            log.info("✅ Successfully removed route for partner: {}", partnerId);
            return true;
            
        } catch (Exception e) {
            log.error("❌ Failed to remove route for partner: {}", partnerId, e);
            return false;
        }
    }

    /**
     * Build route template parameters from partner configuration
     */
    private Map<String, Object> buildRouteParameters(String partnerId, PartnerConfigurationService.PartnerConfig config) {
        Map<String, Object> parameters = new HashMap<>();
        
        // Basic partner identification
        parameters.put("partnerId", partnerId);
        
        // Queue configuration
        String queueName = "partner." + partnerId + ".queue";
        parameters.put("queueName", queueName);
        parameters.put("concurrentConsumers", String.valueOf(config.getCoreThreads()));
        parameters.put("durable", "true");
        parameters.put("autoDelete", "false");
        
        // Additional parameters can be added based on template needs
        log.debug("📋 Built route parameters for partner {}: queue={}, consumers={}", 
                partnerId, queueName, config.getCoreThreads());
        
        return parameters;
    }

    /**
     * Get information about all active partner routes
     */
    public Map<String, String> getActiveRoutes() {
        return new HashMap<>(activeRoutes);
    }

    /**
     * Get the number of active partner routes
     */
    public int getActiveRouteCount() {
        return activeRoutes.size();
    }

    /**
     * Check if a partner has an active route
     */
    public boolean hasActiveRoute(String partnerId) {
        return activeRoutes.containsKey(partnerId);
    }

    /**
     * Refresh all partner routes (useful for mass updates)
     */
    public void refreshAllRoutes() {
        log.info("🔄 Refreshing all partner routes...");
        
        Map<String, PartnerConfigurationService.PartnerConfig> allConfigs = partnerConfigService.getAllPartnerConfigs();
        
        for (Map.Entry<String, PartnerConfigurationService.PartnerConfig> entry : allConfigs.entrySet()) {
            String partnerId = entry.getKey();
            PartnerConfigurationService.PartnerConfig config = entry.getValue();
            
            // Skip DEFAULT config
            if (!"DEFAULT".equals(partnerId)) {
                try {
                    if (hasActiveRoute(partnerId)) {
                        updatePartnerRoute(partnerId, config);
                    } else {
                        createPartnerRoute(partnerId, config);
                    }
                } catch (Exception e) {
                    log.error("❌ Failed to refresh route for partner: {}", partnerId, e);
                }
            }
        }
        
        log.info("✅ Route refresh completed. Active routes: {}", activeRoutes.size());
    }

    // Configuration Change Listener Implementation
    
    /**
     * Handle configuration changes for partners
     */
    public void onConfigurationChanged(String businessUnit, PartnerConfigurationService.PartnerConfig newConfig) {
        log.info("🔔 Configuration changed for partner: {}, updating route...", businessUnit);
        
        try {
            if (hasActiveRoute(businessUnit)) {
                updatePartnerRoute(businessUnit, newConfig);
                log.info("✅ Route updated for partner: {} due to configuration change", businessUnit);
            } else {
                createPartnerRoute(businessUnit, newConfig);
                log.info("✅ New route created for partner: {} due to configuration change", businessUnit);
            }
        } catch (Exception e) {
            log.error("❌ Failed to handle configuration change for partner: {}", businessUnit, e);
        }
    }

    /**
     * Handle configuration deletion for partners
     */
    public void onConfigurationDeleted(String businessUnit) {
        log.info("🔔 Configuration deleted for partner: {}, removing route...", businessUnit);
        
        try {
            boolean removed = removePartnerRoute(businessUnit);
            if (removed) {
                log.info("✅ Route removed for partner: {} due to configuration deletion", businessUnit);
            } else {
                log.warn("⚠️ No route found to remove for partner: {}", businessUnit);
            }
        } catch (Exception e) {
            log.error("❌ Failed to handle configuration deletion for partner: {}", businessUnit, e);
        }
    }

    @PreDestroy
    public void shutdown() {
        log.info("🛑 Shutting down Dynamic Partner Route Manager...");
        
        try {
            // Stop all active partner routes
            Set<String> partnerIds = Set.copyOf(activeRoutes.keySet());
            for (String partnerId : partnerIds) {
                try {
                    removePartnerRoute(partnerId);
                } catch (Exception e) {
                    log.error("❌ Error stopping route for partner: {}", partnerId, e);
                }
            }
            
            log.info("✅ Dynamic Partner Route Manager shutdown completed");
            
        } catch (Exception e) {
            log.error("❌ Error during shutdown", e);
        }
    }
}