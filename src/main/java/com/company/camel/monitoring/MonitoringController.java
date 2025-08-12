package com.company.camel.monitoring;

import com.company.camel.core.CacheClientHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * MONITORING CONTROLLER - YOUR SYSTEM HEALTH DASHBOARD! 📊
 * 
 * This REST API gives you real-time visibility into:
 * - Thread pool utilization per partner
 * - Circuit breaker states and statistics
 * - Cache statistics and health
 * - Partner-specific performance metrics
 * 
 * Perfect for dashboards, alerts, and troubleshooting!
 */
@Slf4j
@RestController
@RequestMapping("/api/monitoring")
public class MonitoringController {

    @Autowired
    private PartnerThreadPoolManager threadPoolManager;
    
    @Autowired
    private PartnerCircuitBreakerManager circuitBreakerManager;
    
    @Autowired
    private CacheClientHandler cacheClientHandler;

    /**
     * Get overall system health
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getSystemHealth() {
        log.debug("📊 System health check requested");
        
        Map<String, Object> health = new HashMap<>();
        
        try {
            // Thread pool health
            Map<String, PartnerThreadPoolManager.ThreadPoolStats> threadStats = threadPoolManager.getAllPartnerStats();
            health.put("totalPartners", threadStats.size());
            health.put("threadPoolsHealthy", threadStats.values().stream()
                .mapToLong(stats -> stats.isShutdown() ? 0 : 1)
                .sum());
            
            // Circuit breaker health  
            Map<String, PartnerCircuitBreakerManager.CircuitBreakerStats> cbStats = circuitBreakerManager.getAllCircuitBreakerStats();
            long healthyCircuits = cbStats.values().stream()
                .mapToLong(stats -> "CLOSED".equals(stats.getState()) ? 1 : 0)
                .sum();
            health.put("circuitBreakersHealthy", healthyCircuits);
            health.put("circuitBreakersOpen", cbStats.size() - healthyCircuits);
            
            // Cache health
            CacheClientHandler.CacheStats cacheStats = cacheClientHandler.getCacheStats();
            health.put("cacheStats", cacheStats);
            
            health.put("status", "UP");
            health.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(health);
            
        } catch (Exception e) {
            log.error("💥 Error getting system health: {}", e.getMessage(), e);
            health.put("status", "DOWN");
            health.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(health);
        }
    }

    /**
     * Get thread pool statistics for all partners
     */
    @GetMapping("/threadpools")
    public ResponseEntity<Map<String, PartnerThreadPoolManager.ThreadPoolStats>> getAllThreadPoolStats() {
        log.debug("🔍 Thread pool stats requested for all partners");
        
        try {
            Map<String, PartnerThreadPoolManager.ThreadPoolStats> stats = threadPoolManager.getAllPartnerStats();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("💥 Error getting thread pool stats: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get thread pool statistics for a specific partner
     */
    @GetMapping("/threadpools/{businessUnit}")
    public ResponseEntity<PartnerThreadPoolManager.ThreadPoolStats> getPartnerThreadPoolStats(
            @PathVariable String businessUnit) {
        log.debug("🔍 Thread pool stats requested for partner: {}", businessUnit);
        
        try {
            PartnerThreadPoolManager.ThreadPoolStats stats = threadPoolManager.getPartnerStats(businessUnit);
            if (stats == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("💥 Error getting thread pool stats for partner {}: {}", businessUnit, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get circuit breaker statistics for all partners
     */
    @GetMapping("/circuitbreakers")
    public ResponseEntity<Map<String, PartnerCircuitBreakerManager.CircuitBreakerStats>> getAllCircuitBreakerStats() {
        log.debug("⚡ Circuit breaker stats requested for all partners");
        
        try {
            Map<String, PartnerCircuitBreakerManager.CircuitBreakerStats> stats = circuitBreakerManager.getAllCircuitBreakerStats();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("💥 Error getting circuit breaker stats: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get circuit breaker statistics for a specific partner
     */
    @GetMapping("/circuitbreakers/{businessUnit}")
    public ResponseEntity<PartnerCircuitBreakerManager.CircuitBreakerStats> getPartnerCircuitBreakerStats(
            @PathVariable String businessUnit) {
        log.debug("⚡ Circuit breaker stats requested for partner: {}", businessUnit);
        
        try {
            PartnerCircuitBreakerManager.CircuitBreakerStats stats = circuitBreakerManager.getPartnerCircuitBreakerStats(businessUnit);
            if (stats == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("💥 Error getting circuit breaker stats for partner {}: {}", businessUnit, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get partner overview with combined stats
     */
    @GetMapping("/partners")
    public ResponseEntity<Map<String, Object>> getPartnerOverview() {
        log.debug("👥 Partner overview requested");
        
        try {
            Map<String, Object> overview = new HashMap<>();
            
            // Get all stats
            Map<String, PartnerThreadPoolManager.ThreadPoolStats> threadStats = threadPoolManager.getAllPartnerStats();
            Map<String, PartnerCircuitBreakerManager.CircuitBreakerStats> cbStats = circuitBreakerManager.getAllCircuitBreakerStats();
            
            // Combine partner data
            Map<String, Map<String, Object>> partnerData = new HashMap<>();
            
            // Add thread pool data
            threadStats.forEach((partner, stats) -> {
                Map<String, Object> data = partnerData.computeIfAbsent(partner, k -> new HashMap<>());
                data.put("threadPool", stats);
                data.put("healthy", !stats.isShutdown());
            });
            
            // Add circuit breaker data
            cbStats.forEach((partner, stats) -> {
                Map<String, Object> data = partnerData.computeIfAbsent(partner, k -> new HashMap<>());
                data.put("circuitBreaker", stats);
                data.put("circuitHealthy", "CLOSED".equals(stats.getState()));
            });
            
            overview.put("partners", partnerData);
            overview.put("totalPartners", partnerData.size());
            overview.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(overview);
            
        } catch (Exception e) {
            log.error("💥 Error getting partner overview: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get specific partner details
     */
    @GetMapping("/partners/{businessUnit}")
    public ResponseEntity<Map<String, Object>> getPartnerDetails(@PathVariable String businessUnit) {
        log.debug("👤 Partner details requested for: {}", businessUnit);
        
        try {
            Map<String, Object> details = new HashMap<>();
            
            // Thread pool stats
            PartnerThreadPoolManager.ThreadPoolStats threadStats = threadPoolManager.getPartnerStats(businessUnit);
            details.put("threadPool", threadStats);
            
            // Circuit breaker stats
            PartnerCircuitBreakerManager.CircuitBreakerStats cbStats = circuitBreakerManager.getPartnerCircuitBreakerStats(businessUnit);
            details.put("circuitBreaker", cbStats);
            
            // Health indicators
            details.put("threadPoolHealthy", threadStats != null && !threadStats.isShutdown());
            details.put("circuitBreakerHealthy", cbStats != null && "CLOSED".equals(cbStats.getState()));
            details.put("overallHealthy", circuitBreakerManager.isPartnerHealthy(businessUnit));
            
            details.put("businessUnit", businessUnit);
            details.put("timestamp", System.currentTimeMillis());
            
            if (threadStats == null && cbStats == null) {
                return ResponseEntity.notFound().build();
            }
            
            return ResponseEntity.ok(details);
            
        } catch (Exception e) {
            log.error("💥 Error getting partner details for {}: {}", businessUnit, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Force circuit breaker state changes (for emergencies)
     */
    @PostMapping("/circuitbreakers/{businessUnit}/force-open")
    public ResponseEntity<Map<String, String>> forceCircuitBreakerOpen(@PathVariable String businessUnit) {
        log.warn("🔴 Force opening circuit breaker for partner: {}", businessUnit);
        
        try {
            circuitBreakerManager.forceOpen(businessUnit);
            Map<String, String> response = new HashMap<>();
            response.put("status", "SUCCESS");
            response.put("message", "Circuit breaker forced OPEN for " + businessUnit);
            response.put("businessUnit", businessUnit);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("💥 Error forcing circuit breaker open for {}: {}", businessUnit, e.getMessage(), e);
            Map<String, String> response = new HashMap<>();
            response.put("status", "ERROR");
            response.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @PostMapping("/circuitbreakers/{businessUnit}/force-closed")
    public ResponseEntity<Map<String, String>> forceCircuitBreakerClosed(@PathVariable String businessUnit) {
        log.info("🟢 Force closing circuit breaker for partner: {}", businessUnit);
        
        try {
            circuitBreakerManager.forceClosed(businessUnit);
            Map<String, String> response = new HashMap<>();
            response.put("status", "SUCCESS");
            response.put("message", "Circuit breaker forced CLOSED for " + businessUnit);
            response.put("businessUnit", businessUnit);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("💥 Error forcing circuit breaker closed for {}: {}", businessUnit, e.getMessage(), e);
            Map<String, String> response = new HashMap<>();
            response.put("status", "ERROR");
            response.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Get cache statistics
     */
    @GetMapping("/cache")
    public ResponseEntity<CacheClientHandler.CacheStats> getCacheStats() {
        log.debug("💾 Cache stats requested");
        
        try {
            CacheClientHandler.CacheStats stats = cacheClientHandler.getCacheStats();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("💥 Error getting cache stats: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}