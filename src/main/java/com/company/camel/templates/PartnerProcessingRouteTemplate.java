package com.company.camel.templates;

import com.company.camel.config.PartnerConfigurationService;
import com.company.camel.core.CacheClientHandler;
import com.company.camel.monitoring.PartnerCircuitBreakerManager;
import com.company.camel.monitoring.PartnerThreadPoolManager;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * PARTNER-SPECIFIC ROUTE TEMPLATE 🎯
 * 
 * Creates individual processing routes for each partner with:
 * - Dedicated partner queue: partner.{PARTNER_NAME}.queue
 * - Partner-specific configurations from Elasticsearch
 * - Complete isolation between partners
 * - Dynamic route creation/updates
 * 
 * Template Parameters:
 * - partnerId: The business unit identifier (e.g., AMAZON, FLIPKART)
 * - queueName: The partner-specific queue name
 * - concurrentConsumers: Number of concurrent consumers for this partner
 */
@Slf4j
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    name = "app.routes.templates.enabled", 
    havingValue = "true", 
    matchIfMissing = true
)
public class PartnerProcessingRouteTemplate extends RouteBuilder {

    @Autowired
    private PartnerThreadPoolManager threadPoolManager;
    
    @Autowired
    private PartnerCircuitBreakerManager circuitBreakerManager;
    
    @Autowired
    private CacheClientHandler cacheClientHandler;
    
    @Autowired
    private PartnerConfigurationService configService;

    @Override
    public void configure() throws Exception {
        
        // Create route template for partner processing
        routeTemplate("partnerProcessingTemplate")
            .templateParameter("partnerId")
            .templateParameter("queueName") 
            .templateParameter("concurrentConsumers", "5")
            .templateParameter("durable", "true")
            .templateParameter("autoDelete", "false")
            
            // Main partner processing route
            .from("rabbitmq:{{queueName}}?autoDelete={{autoDelete}}&durable={{durable}}&concurrentConsumers={{concurrentConsumers}}")
                .routeId("Partner:{{partnerId}}:Processing")
                .log(LoggingLevel.INFO, "📨 Processing message for partner: {{partnerId}}")
                .setHeader("PartnerId", constant("{{partnerId}}"))
                .process(new PartnerConfigLoader())
                .process(new PartnerHeaderDecryptionProcessor())
                .process(new PartnerAuthenticationProcessor())
                .process(new PartnerMessageTransmissionProcessor())
                .process(new PartnerResultLoggingProcessor())
                .log(LoggingLevel.INFO, "✅ Message processing completed for partner: {{partnerId}}");
    }

    /**
     * Loads partner configuration from Elasticsearch
     */
    private class PartnerConfigLoader implements Processor {
        @Override
        public void process(Exchange exchange) throws Exception {
            String partnerId = exchange.getIn().getHeader("PartnerId", String.class);
            
            if (partnerId == null || partnerId.trim().isEmpty()) {
                throw new IllegalArgumentException("PartnerId header is required");
            }
            
            // Load partner configuration from Elasticsearch
            PartnerConfigurationService.PartnerConfig config = configService.getPartnerConfig(partnerId);
            exchange.getIn().setHeader("PartnerConfig", config);
            
            log.debug("✅ Configuration loaded for partner: {}", partnerId);
        }
    }

    /**
     * Partner-specific header decryption processor
     */
    private class PartnerHeaderDecryptionProcessor implements Processor {
        @Override
        public void process(Exchange exchange) throws Exception {
            String partnerId = exchange.getIn().getHeader("PartnerId", String.class);
            
            CompletableFuture<Void> future = circuitBreakerManager.executeWithCircuitBreaker(partnerId, () -> {
                // Partner-specific header decryption logic
                String encryptedHeaders = exchange.getIn().getHeader("EncryptedHeaders", String.class);
                if (encryptedHeaders != null) {
                    // Use partner-specific decryption key/algorithm
                    exchange.getIn().setHeader("DecryptedHeaders", "decrypted_" + partnerId + "_" + encryptedHeaders);
                    log.debug("🔐 Headers decrypted for partner: {}", partnerId);
                }
            });
            
            future.get();
        }
    }

    /**
     * Partner-specific authentication processor
     */
    private class PartnerAuthenticationProcessor implements Processor {
        @Override
        public void process(Exchange exchange) throws Exception {
            String partnerId = exchange.getIn().getHeader("PartnerId", String.class);
            PartnerConfigurationService.PartnerConfig config = 
                (PartnerConfigurationService.PartnerConfig) exchange.getIn().getHeader("PartnerConfig");
            
            CompletableFuture<Void> future = circuitBreakerManager.executeWithCircuitBreaker(partnerId, () -> {
                // Check partner-specific token
                String token = CacheClientHandler.getTokens(partnerId);
                boolean tokenExpired = cacheClientHandler.isTokenExpired(partnerId, config.getAuthTokenExpiryMinutes());
                
                exchange.getIn().setHeader("TokenExpired", tokenExpired);
                exchange.getIn().setHeader("CurrentToken", token);
                
                if (tokenExpired) {
                    log.info("⏰ Token expired for partner: {}, will refresh", partnerId);
                } else {
                    log.debug("✅ Token valid for partner: {}", partnerId);
                }
            });
            
            future.get();
        }
    }

    /**
     * Partner-specific OAuth refresh processor
     */
    private class PartnerOAuthRefreshProcessor implements Processor {
        @Override
        public void process(Exchange exchange) throws Exception {
            String partnerId = exchange.getIn().getHeader("PartnerId", String.class);
            PartnerConfigurationService.PartnerConfig config = 
                (PartnerConfigurationService.PartnerConfig) exchange.getIn().getHeader("PartnerConfig");
            
            CompletableFuture<Void> future = circuitBreakerManager.executeWithCircuitBreaker(partnerId, () -> {
                // Partner-specific OAuth refresh logic
                String authEndpoint = config.getAuthEndpoint();
                String refreshToken = cacheClientHandler.getRefreshToken(partnerId);
                
                // Simulate OAuth refresh call
                log.info("🔄 Refreshing OAuth token for partner: {} using endpoint: {}", partnerId, authEndpoint);
                
                // In real implementation, make HTTP call to partner's auth endpoint
                String newToken = "new_token_" + partnerId + "_" + System.currentTimeMillis();
                cacheClientHandler.storeToken(partnerId, newToken, config.getAuthTokenExpiryMinutes());
                
                exchange.getIn().setHeader("NewToken", newToken);
                exchange.getIn().setHeader("TokenRefreshed", true);
                
                log.info("✅ OAuth token refreshed for partner: {}", partnerId);
            });
            
            future.get();
        }
    }

    /**
     * Partner-specific message transmission processor
     */
    private class PartnerMessageTransmissionProcessor implements Processor {
        @Override
        public void process(Exchange exchange) throws Exception {
            String partnerId = exchange.getIn().getHeader("PartnerId", String.class);
            PartnerConfigurationService.PartnerConfig config = 
                (PartnerConfigurationService.PartnerConfig) exchange.getIn().getHeader("PartnerConfig");
            
            CompletableFuture<Void> future = threadPoolManager.executePartnerTask(partnerId, () -> {
                // Partner-specific message transmission with retry logic
                int maxRetries = config.getRetryMaxAttempts();
                double backoffMultiplier = config.getRetryBackoffMultiplier();
                long delay = config.getRetryInitialDelayMs();
                
                for (int attempt = 1; attempt <= maxRetries; attempt++) {
                    try {
                        log.debug("📤 Transmission attempt {} of {} for partner: {}", attempt, maxRetries, partnerId);
                        
                        // Partner-specific API call
                        simulatePartnerApiCall(partnerId, config);
                        
                        // Success
                        exchange.getIn().setHeader("TransmissionResult", "SUCCESS");
                        exchange.getIn().setHeader("TransmissionAttempts", attempt);
                        log.info("✅ Message transmitted successfully for partner: {} on attempt {}", partnerId, attempt);
                        return;
                        
                    } catch (Exception e) {
                        log.warn("⚠️ Transmission attempt {} failed for partner: {} - {}", attempt, partnerId, e.getMessage());
                        
                        if (attempt == maxRetries) {
                            exchange.getIn().setHeader("TransmissionResult", "FAILED");
                            exchange.getIn().setHeader("TransmissionAttempts", attempt);
                            exchange.getIn().setHeader("TransmissionError", e.getMessage());
                            throw new RuntimeException("Message transmission failed after " + maxRetries + " attempts for " + partnerId, e);
                        }
                        
                        // Wait before retry with exponential backoff
                        try {
                            Thread.sleep((long) (delay * Math.pow(backoffMultiplier, attempt - 1)));
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException("Retry interrupted for " + partnerId, ie);
                        }
                    }
                }
            });
            
            future.get();
        }
        
        private void simulatePartnerApiCall(String partnerId, PartnerConfigurationService.PartnerConfig config) throws Exception {
            // Partner-specific API simulation
            String apiEndpoint = config.getApiEndpoint();
            int timeoutSeconds = config.getApiTimeoutSeconds();
            
            log.debug("📡 Calling partner API: {} with timeout: {}s", apiEndpoint, timeoutSeconds);
            
            // Simulate different partner behaviors
            switch (partnerId) {
                case "FAILING_PARTNER":
                    throw new RuntimeException("Simulated partner API failure");
                case "SLOW_PARTNER":
                    Thread.sleep(timeoutSeconds * 1000L + 1000);
                    break;
                case "INTERMITTENT_PARTNER":
                    if (Math.random() < 0.3) {
                        throw new RuntimeException("Intermittent partner failure");
                    }
                    break;
                default:
                    Thread.sleep(100 + (long) (Math.random() * 200));
                    break;
            }
            
            log.debug("✅ Partner API call successful for: {}", partnerId);
        }
    }

    /**
     * Partner-specific result logging processor
     */
    private class PartnerResultLoggingProcessor implements Processor {
        @Override
        public void process(Exchange exchange) throws Exception {
            String partnerId = exchange.getIn().getHeader("PartnerId", String.class);
            
            CompletableFuture<Void> future = threadPoolManager.executePartnerTask(partnerId, () -> {
                // Prepare partner-specific result data
                String result = exchange.getIn().getHeader("TransmissionResult", String.class);
                Integer attempts = exchange.getIn().getHeader("TransmissionAttempts", Integer.class);
                String error = exchange.getIn().getHeader("TransmissionError", String.class);
                
                PartnerResultData resultData = PartnerResultData.builder()
                    .partnerId(partnerId)
                    .result(result)
                    .attempts(attempts)
                    .error(error)
                    .timestamp(System.currentTimeMillis())
                    .threadName(Thread.currentThread().getName())
                    .routeId("Partner:" + partnerId + ":Processing")
                    .build();
                
                exchange.getIn().setBody(resultData);
                
                log.debug("📊 Result prepared for partner: {} - {}", partnerId, result);
            });
            
            future.get();
        }
    }

    /**
     * Partner-specific result data class
     */
    @lombok.Builder
    @lombok.Data
    private static class PartnerResultData {
        private String partnerId;
        private String result;
        private Integer attempts;
        private String error;
        private long timestamp;
        private String threadName;
        private String routeId;
    }
}