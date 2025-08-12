package com.company.camel.api;

import com.company.camel.config.ElasticsearchPartnerConfigService;
import com.company.camel.config.PartnerConfigurationService;
import com.company.camel.routes.DynamicPartnerRouteManager;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;

/**
 * PARTNER CONFIGURATION API 🌐
 * 
 * REST API for partner configuration management and change notifications:
 * - Webhook endpoint for configuration change notifications
 * - Manual partner route refresh triggers
 * - Route status monitoring endpoints
 * - Configuration validation and management
 * 
 * This replaces the scheduled Elasticsearch polling with event-driven updates.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/partner-config")
public class PartnerConfigurationController {

    @Autowired
    private DynamicPartnerRouteManager routeManager;
    
    @Autowired
    private PartnerConfigurationService partnerConfigService;
    
    @Autowired
    private ElasticsearchPartnerConfigService elasticsearchConfigService;

    /**
     * Webhook endpoint for configuration change notifications
     * Called when partner configuration is updated in Elasticsearch
     * 
     * POST /api/v1/partner-config/webhook/config-changed
     */
    @PostMapping("/webhook/config-changed")
    public ResponseEntity<ConfigChangeResponse> handleConfigurationChanged(
            @Valid @RequestBody ConfigChangeNotification notification) {
        
        log.info("📡 Received configuration change notification for partner: {}", notification.getPartnerId());
        
        try {
            String partnerId = notification.getPartnerId();
            String changeType = notification.getChangeType();
            
            switch (changeType.toUpperCase()) {
                case "CREATED":
                case "UPDATED":
                    return handlePartnerConfigUpdate(partnerId, notification);
                    
                case "DELETED":
                    return handlePartnerConfigDeletion(partnerId, notification);
                    
                default:
                    log.warn("⚠️ Unknown change type: {} for partner: {}", changeType, partnerId);
                    return ResponseEntity.badRequest()
                        .body(new ConfigChangeResponse(false, "Unknown change type: " + changeType, partnerId));
            }
            
        } catch (Exception e) {
            log.error("❌ Error processing configuration change notification", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ConfigChangeResponse(false, "Internal error: " + e.getMessage(), 
                    notification.getPartnerId()));
        }
    }

    /**
     * Handle partner configuration update/creation
     */
    private ResponseEntity<ConfigChangeResponse> handlePartnerConfigUpdate(
            String partnerId, ConfigChangeNotification notification) {
        
        log.info("🔄 Processing configuration update for partner: {}", partnerId);
        
        try {
            // First, reload configurations from Elasticsearch to get latest data
            elasticsearchConfigService.reloadConfigurations();
            
            // Now get the updated configuration
            PartnerConfigurationService.PartnerConfig updatedConfig = 
                partnerConfigService.getPartnerConfig(partnerId);
            
            if (updatedConfig == null) {
                log.error("❌ Failed to load updated configuration for partner: {}", partnerId);
                return ResponseEntity.badRequest()
                    .body(new ConfigChangeResponse(false, "Configuration not found in Elasticsearch", partnerId));
            }
            
            // Trigger route update
            routeManager.onConfigurationChanged(partnerId, updatedConfig);
            
            String message = String.format("Configuration updated and route refreshed for partner: %s", partnerId);
            log.info("✅ {}", message);
            
            return ResponseEntity.ok(new ConfigChangeResponse(true, message, partnerId));
            
        } catch (Exception e) {
            String errorMsg = String.format("Failed to update configuration for partner: %s - %s", 
                partnerId, e.getMessage());
            log.error("❌ {}", errorMsg, e);
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ConfigChangeResponse(false, errorMsg, partnerId));
        }
    }

    /**
     * Handle partner configuration deletion
     */
    private ResponseEntity<ConfigChangeResponse> handlePartnerConfigDeletion(
            String partnerId, ConfigChangeNotification notification) {
        
        log.info("🗑️ Processing configuration deletion for partner: {}", partnerId);
        
        try {
            // Trigger route removal
            routeManager.onConfigurationDeleted(partnerId);
            
            String message = String.format("Configuration deleted and route removed for partner: %s", partnerId);
            log.info("✅ {}", message);
            
            return ResponseEntity.ok(new ConfigChangeResponse(true, message, partnerId));
            
        } catch (Exception e) {
            String errorMsg = String.format("Failed to delete configuration for partner: %s - %s", 
                partnerId, e.getMessage());
            log.error("❌ {}", errorMsg, e);
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ConfigChangeResponse(false, errorMsg, partnerId));
        }
    }

    /**
     * Manual partner route refresh endpoint
     * POST /api/v1/partner-config/{partnerId}/refresh
     */
    @PostMapping("/{partnerId}/refresh")
    public ResponseEntity<ConfigChangeResponse> refreshPartnerRoute(@PathVariable String partnerId) {
        
        log.info("🔄 Manual route refresh requested for partner: {}", partnerId);
        
        try {
            // First, reload configurations from Elasticsearch to get latest data
            elasticsearchConfigService.reloadConfigurations();
            
            // Load latest configuration
            PartnerConfigurationService.PartnerConfig config = 
                partnerConfigService.getPartnerConfig(partnerId);
            
            if (config == null) {
                return ResponseEntity.badRequest()
                    .body(new ConfigChangeResponse(false, "Partner configuration not found", partnerId));
            }
            
            // Trigger route update
            routeManager.onConfigurationChanged(partnerId, config);
            
            String message = String.format("Route manually refreshed for partner: %s", partnerId);
            log.info("✅ {}", message);
            
            return ResponseEntity.ok(new ConfigChangeResponse(true, message, partnerId));
            
        } catch (Exception e) {
            String errorMsg = String.format("Failed to refresh route for partner: %s - %s", 
                partnerId, e.getMessage());
            log.error("❌ {}", errorMsg, e);
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ConfigChangeResponse(false, errorMsg, partnerId));
        }
    }

    /**
     * Refresh all partner routes
     * POST /api/v1/partner-config/refresh-all
     */
    @PostMapping("/refresh-all")
    public ResponseEntity<Map<String, Object>> refreshAllRoutes() {
        
        log.info("🔄 Manual refresh requested for all partner routes");
        
        try {
            int initialRouteCount = routeManager.getActiveRouteCount();
            routeManager.refreshAllRoutes();
            int finalRouteCount = routeManager.getActiveRouteCount();
            
            Map<String, Object> response = Map.of(
                "success", true,
                "message", "All partner routes refreshed successfully",
                "initialRouteCount", initialRouteCount,
                "finalRouteCount", finalRouteCount,
                "activeRoutes", routeManager.getActiveRoutes().keySet()
            );
            
            log.info("✅ All routes refreshed: {} -> {} routes", initialRouteCount, finalRouteCount);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("❌ Failed to refresh all routes", e);
            
            Map<String, Object> response = Map.of(
                "success", false,
                "message", "Failed to refresh all routes: " + e.getMessage()
            );
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Get status of all partner routes
     * GET /api/v1/partner-config/routes/status
     */
    @GetMapping("/routes/status")
    public ResponseEntity<Map<String, Object>> getRouteStatus() {
        
        try {
            Map<String, String> activeRoutes = routeManager.getActiveRoutes();
            int routeCount = routeManager.getActiveRouteCount();
            
            Map<String, Object> response = Map.of(
                "success", true,
                "activeRouteCount", routeCount,
                "activeRoutes", activeRoutes,
                "timestamp", System.currentTimeMillis()
            );
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("❌ Failed to get route status", e);
            
            Map<String, Object> response = Map.of(
                "success", false,
                "message", "Failed to get route status: " + e.getMessage()
            );
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Get configuration for a specific partner
     * GET /api/v1/partner-config/{partnerId}
     */
    @GetMapping("/{partnerId}")
    public ResponseEntity<Map<String, Object>> getPartnerConfiguration(@PathVariable String partnerId) {
        
        try {
            PartnerConfigurationService.PartnerConfig config = 
                partnerConfigService.getPartnerConfig(partnerId);
            
            if (config == null) {
                Map<String, Object> response = Map.of(
                    "success", false,
                    "message", "Partner configuration not found",
                    "partnerId", partnerId
                );
                return ResponseEntity.notFound().build();
            }
            
            boolean hasActiveRoute = routeManager.hasActiveRoute(partnerId);
            
            Map<String, Object> response = Map.of(
                "success", true,
                "partnerId", partnerId,
                "configuration", config,
                "hasActiveRoute", hasActiveRoute,
                "timestamp", System.currentTimeMillis()
            );
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("❌ Failed to get configuration for partner: {}", partnerId, e);
            
            Map<String, Object> response = Map.of(
                "success", false,
                "message", "Failed to get configuration: " + e.getMessage(),
                "partnerId", partnerId
            );
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // DTO Classes

    /**
     * Configuration change notification payload
     */
    @Data
    public static class ConfigChangeNotification {
        @NotBlank(message = "Partner ID is required")
        private String partnerId;
        
        @NotBlank(message = "Change type is required")
        private String changeType; // CREATED, UPDATED, DELETED
        
        private String version;
        private Long timestamp;
        private String source; // e.g., "elasticsearch", "admin-ui"
        private Map<String, Object> metadata;
    }

    /**
     * Configuration change response
     */
    @Data
    public static class ConfigChangeResponse {
        private boolean success;
        private String message;
        private String partnerId;
        private Long timestamp;
        
        public ConfigChangeResponse(boolean success, String message, String partnerId) {
            this.success = success;
            this.message = message;
            this.partnerId = partnerId;
            this.timestamp = System.currentTimeMillis();
        }
    }
}