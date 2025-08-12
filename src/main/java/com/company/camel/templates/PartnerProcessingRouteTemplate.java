package com.company.camel.templates;

import com.company.camel.config.ElasticsearchPartnerConfigService;
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
    private ElasticsearchPartnerConfigService configService;

    @Override
    public void configure() throws Exception {
        
        // Create route template for partner processing
        routeTemplate("partnerProcessingTemplate")
            .templateParameter("partnerId")
            .templateParameter("queueName") 
            .templateParameter("concurrentConsumers", "5")
            .templateParameter("durable", "true")
            .templateParameter("autoDelete", "false")
            
            // Partner-specific exception handling
            .onException(Exception.class)
                .handled(true)
                .setHeader("ErrorMessage", simple("${exception.message}"))
                .setHeader("ErrorTimestamp", simple("${date:now:yyyy-MM-dd HH:mm:ss}"))
                .setHeader("PartnerId", simple("{{partnerId}}"))
                .process(exchange -> {
                    String partnerId = exchange.getIn().getHeader("PartnerId", String.class);
                    String errorMsg = exchange.getIn().getHeader("ErrorMessage", String.class);
                    
                    log.error("💥 Partner {} processing error: {}", partnerId, errorMsg);
                    
                    exchange.getIn().setHeader("ProcessingStage", "PartnerSpecificProcessing");
                    exchange.getIn().setHeader("RouteId", "Partner:" + partnerId);
                })
                .log(LoggingLevel.ERROR, "Partner ${headers.PartnerId} Error: ${headers.ErrorMessage}")
                .to("direct:elasticExceptionStore.{{partnerId}}")
                .end()
            
            // Main partner processing route
            .from("rabbitmq:{{queueName}}?autoDelete={{autoDelete}}&durable={{durable}}&concurrentConsumers={{concurrentConsumers}}")
                .routeId("Partner:{{partnerId}}:Processing")
                .log(LoggingLevel.INFO, "📨 Processing message for partner: {{partnerId}}")
                .setHeader("PartnerId", constant("{{partnerId}}"))
                .process(new PartnerConfigLoader())
                .to("direct:partnerHeaderDecryption.{{partnerId}}")
                .to("direct:partnerAuthentication.{{partnerId}}")
                .to("direct:partnerMessageTransmission.{{partnerId}}")
                .to("direct:partnerResultLogging.{{partnerId}}")
                .log(LoggingLevel.INFO, "✅ Message processing completed for partner: {{partnerId}}")
            
            // Partner-specific header decryption
            .from("direct:partnerHeaderDecryption.{{partnerId}}")
                .routeId("Partner:{{partnerId}}:HeaderDecryption")
                .log(LoggingLevel.DEBUG, "🔐 Header decryption for partner: {{partnerId}}")
                .process(new PartnerHeaderDecryptionProcessor())
                .log(LoggingLevel.DEBUG, "✅ Header decryption completed for partner: {{partnerId}}")
            
            // Partner-specific authentication
            .from("direct:partnerAuthentication.{{partnerId}}")
                .routeId("Partner:{{partnerId}}:Authentication")
                .log(LoggingLevel.DEBUG, "🔑 Authentication check for partner: {{partnerId}}")
                .process(new PartnerAuthenticationProcessor())
                .choice()
                    .when(header("TokenExpired").isEqualTo(true))
                        .log(LoggingLevel.INFO, "🔄 Token expired for {{partnerId}}, refreshing...")
                        .to("direct:partnerOAuthRefresh.{{partnerId}}")
                    .otherwise()
                        .log(LoggingLevel.DEBUG, "✅ Token valid for {{partnerId}}")
                .end()
            
            // Partner-specific OAuth refresh
            .from("direct:partnerOAuthRefresh.{{partnerId}}")
                .routeId("Partner:{{partnerId}}:OAuthRefresh")
                .log(LoggingLevel.INFO, "🔄 OAuth token refresh for partner: {{partnerId}}")
                .process(new PartnerOAuthRefreshProcessor())
                .log(LoggingLevel.INFO, "✅ OAuth token refreshed for partner: {{partnerId}}")
            
            // Partner-specific message transmission
            .from("direct:partnerMessageTransmission.{{partnerId}}")
                .routeId("Partner:{{partnerId}}:MessageTransmission")
                .log(LoggingLevel.INFO, "📤 Message transmission for partner: {{partnerId}}")
                .process(new PartnerMessageTransmissionProcessor())
                .log(LoggingLevel.INFO, "✅ Message transmission completed for partner: {{partnerId}}")
            
            // Partner-specific result logging
            .from("direct:partnerResultLogging.{{partnerId}}")
                .routeId("Partner:{{partnerId}}:ResultLogging")
                .log(LoggingLevel.DEBUG, "📊 Result logging for partner: {{partnerId}}")
                .process(new PartnerResultLoggingProcessor())
                .to("direct:elasticResultStore.{{partnerId}}")
                .log(LoggingLevel.DEBUG, "✅ Result logging completed for partner: {{partnerId}}")
            
            // Partner-specific Elasticsearch result storage
            .from("direct:elasticResultStore.{{partnerId}}")
                .routeId("Partner:{{partnerId}}:ElasticResultStore")
                .log(LoggingLevel.DEBUG, "📚 Storing result for partner: {{partnerId}}")
                .setHeader(Exchange.HTTP_METHOD, constant("POST"))
                .setHeader(Exchange.CONTENT_TYPE, constant("application/json"))
                .setHeader(Exchange.HTTP_URI, simple("http://localhost:9200/partner-{{partnerId}}-results/_doc"))
                .marshal().json()
                .to("http://dummy") // Will be replaced with actual Elasticsearch endpoint
                .log(LoggingLevel.DEBUG, "✅ Result stored for partner: {{partnerId}}")
            
            // Partner-specific Elasticsearch exception storage
            .from("direct:elasticExceptionStore.{{partnerId}}")
                .routeId("Partner:{{partnerId}}:ElasticExceptionStore")
                .log(LoggingLevel.DEBUG, "📚 Storing exception for partner: {{partnerId}}")
                .setHeader(Exchange.HTTP_METHOD, constant("POST"))
                .setHeader(Exchange.CONTENT_TYPE, constant("application/json"))
                .setHeader(Exchange.HTTP_URI, simple("http://localhost:9200/partner-{{partnerId}}-exceptions/_doc"))
                .marshal().json()
                .to("http://dummy") // Will be replaced with actual Elasticsearch endpoint
                .log(LoggingLevel.DEBUG, "✅ Exception stored for partner: {{partnerId}}");
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
            ElasticsearchPartnerConfigService.PartnerConfig config = configService.getPartnerConfig(partnerId);
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
            ElasticsearchPartnerConfigService.PartnerConfig config = 
                (ElasticsearchPartnerConfigService.PartnerConfig) exchange.getIn().getHeader("PartnerConfig");
            
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
            ElasticsearchPartnerConfigService.PartnerConfig config = 
                (ElasticsearchPartnerConfigService.PartnerConfig) exchange.getIn().getHeader("PartnerConfig");
            
            CompletableFuture<Void> future = circuitBreakerManager.executeWithCircuitBreaker(partnerId, () -> {
                // Partner-specific OAuth refresh logic
                String authEndpoint = config.getAuthEndpoint();
                String refreshToken = CacheClientHandler.getRefreshToken(partnerId);
                
                // Simulate OAuth refresh call
                log.info("🔄 Refreshing OAuth token for partner: {} using endpoint: {}", partnerId, authEndpoint);
                
                // In real implementation, make HTTP call to partner's auth endpoint
                String newToken = "new_token_" + partnerId + "_" + System.currentTimeMillis();
                CacheClientHandler.storeToken(partnerId, newToken, config.getAuthTokenExpiryMinutes());
                
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
            ElasticsearchPartnerConfigService.PartnerConfig config = 
                (ElasticsearchPartnerConfigService.PartnerConfig) exchange.getIn().getHeader("PartnerConfig");
            
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
        
        private void simulatePartnerApiCall(String partnerId, ElasticsearchPartnerConfigService.PartnerConfig config) throws Exception {
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