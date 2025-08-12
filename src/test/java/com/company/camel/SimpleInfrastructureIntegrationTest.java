package com.company.camel;

import com.company.camel.config.PartnerConfigurationService;
import com.company.camel.core.CacheClientHandler;
import com.company.camel.monitoring.PartnerCircuitBreakerManager;
import com.company.camel.monitoring.PartnerThreadPoolManager;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SIMPLE INFRASTRUCTURE INTEGRATION TEST 🧪
 * 
 * Tests the complete enhanced system integration:
 * - Thread isolation working correctly
 * - Circuit breakers protecting against failures
 * - Configuration management
 * - Cache handling with thread safety
 * - End-to-end message processing flow
 */
@Slf4j
@SpringBootTest
@ActiveProfiles("test")
public class SimpleInfrastructureIntegrationTest {

    @Autowired
    private PartnerThreadPoolManager threadPoolManager;
    
    @Autowired
    private PartnerCircuitBreakerManager circuitBreakerManager;
    
    @Autowired
    private CacheClientHandler cacheClientHandler;
    
    @Autowired
    private PartnerConfigurationService configService;

    @Test
    public void testCompleteSystemIntegration() throws Exception {
        log.info("🧪 TESTING COMPLETE ENHANCED SYSTEM INTEGRATION!");
        
        String[] partners = {"INTEGRATION_PARTNER_1", "INTEGRATION_PARTNER_2", "INTEGRATION_PARTNER_3"};
        AtomicInteger successCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(partners.length * 10);
        
        // Test complete flow for each partner
        for (String partner : partners) {
            // 1. Setup partner configuration
            setupPartnerConfiguration(partner);
            
            // 2. Setup authentication cache
            setupPartnerAuthentication(partner);
            
            // 3. Process messages with full protection
            for (int i = 0; i < 10; i++) {
                final int messageId = i;
                
                CompletableFuture<Void> future = processMessageWithFullProtection(partner, messageId);
                
                future.whenComplete((result, throwable) -> {
                    if (throwable == null) {
                        successCount.incrementAndGet();
                        log.debug("✅ {} message {} processed successfully", partner, messageId);
                    } else {
                        log.error("💥 {} message {} failed: {}", partner, messageId, throwable.getMessage());
                    }
                    latch.countDown();
                });
            }
        }
        
        // Wait for all processing to complete
        boolean completed = latch.await(30, TimeUnit.SECONDS);
        
        log.info("📊 INTEGRATION TEST RESULTS:");
        log.info("✅ Messages processed successfully: {}", successCount.get());
        log.info("🎯 All completed within timeout: {}", completed);
        
        // Verify system state
        verifySystemHealth(partners);
        
        assertTrue(completed, "All messages should complete within timeout");
        assertTrue(successCount.get() >= partners.length * 8, "At least 80% success rate expected");
        
        log.info("🎉 COMPLETE SYSTEM INTEGRATION TEST PASSED!");
    }

    private void setupPartnerConfiguration(String partner) {
        PartnerConfigurationService.PartnerConfig config = 
            PartnerConfigurationService.PartnerConfig.builder()
                .businessUnit(partner)
                .coreThreads(5)
                .maxThreads(15)
                .queueCapacity(500)
                .circuitBreakerFailureThreshold(50.0f)
                .circuitBreakerMinCalls(5)
                .circuitBreakerWaitDuration(10)
                .retryMaxAttempts(3)
                .retryBackoffMultiplier(1.5)
                .authTokenExpiryMinutes(15)
                .apiTimeoutSeconds(10)
                .priority(PartnerConfigurationService.PartnerPriority.MEDIUM)
                .build();
        
        configService.addPartnerConfig(partner, config);
        log.debug("🎯 Configuration setup for partner: {}", partner);
    }

    private void setupPartnerAuthentication(String partner) {
        // Setup auth headers
        java.util.Set<String> authHeaders = new java.util.HashSet<>();
        authHeaders.add("Authorization");
        authHeaders.add("Content-Type");
        cacheClientHandler.addStatusHeaders(partner, authHeaders);
        
        // Setup auth body
        java.util.HashMap<String, String> authBody = new java.util.HashMap<>();
        authBody.put("grant_type", "client_credentials");
        authBody.put("client_id", partner.toLowerCase() + "_client");
        cacheClientHandler.setAuthBodyMap(partner, authBody);
        
        // Set initial token
        String token = "Bearer_" + partner + "_" + System.currentTimeMillis();
        CacheClientHandler.setTokens(token, partner);
        
        log.debug("🔐 Authentication setup for partner: {}", partner);
    }

    private CompletableFuture<Void> processMessageWithFullProtection(String partner, int messageId) {
        return circuitBreakerManager.executeWithCircuitBreaker(partner, () -> {
            // Simulate complete message processing pipeline
            
            // 1. Header decryption
            simulateHeaderDecryption(partner, messageId);
            
            // 2. Authentication check
            simulateAuthenticationCheck(partner);
            
            // 3. Message transmission
            simulateMessageTransmission(partner, messageId);
            
            // 4. Result logging
            simulateResultLogging(partner, messageId);
            
            log.debug("📦 Complete processing finished for {} message {}", partner, messageId);
        });
    }

    private void simulateHeaderDecryption(String partner, int messageId) {
        try {
            Thread.sleep(10 + (messageId % 20)); // 10-30ms
            log.debug("🔐 Headers decrypted for {} message {}", partner, messageId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Header decryption interrupted for " + partner, e);
        }
    }

    private void simulateAuthenticationCheck(String partner) {
        try {
            Thread.sleep(5 + (int)(Math.random() * 10)); // 5-15ms
            
            String token = CacheClientHandler.getTokens(partner);
            if (token == null || token.isEmpty()) {
                throw new RuntimeException("Missing token for " + partner);
            }
            
            log.debug("🔑 Authentication verified for {}", partner);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Authentication check interrupted for " + partner, e);
        }
    }

    private void simulateMessageTransmission(String partner, int messageId) {
        try {
            Thread.sleep(50 + (messageId % 100)); // 50-150ms
            
            // Simulate occasional failures for testing
            if (partner.equals("INTEGRATION_PARTNER_3") && messageId % 5 == 0) {
                throw new RuntimeException("Simulated transmission failure");
            }
            
            log.debug("📤 Message transmitted for {} message {}", partner, messageId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Message transmission interrupted for " + partner, e);
        }
    }

    private void simulateResultLogging(String partner, int messageId) {
        try {
            Thread.sleep(5); // 5ms
            log.debug("📊 Result logged for {} message {}", partner, messageId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Result logging interrupted for " + partner, e);
        }
    }

    private void verifySystemHealth(String[] partners) {
        log.info("🏥 VERIFYING SYSTEM HEALTH:");
        
        for (String partner : partners) {
            // Check thread pool health
            PartnerThreadPoolManager.ThreadPoolStats threadStats = threadPoolManager.getPartnerStats(partner);
            assertNotNull(threadStats, "Thread pool stats should exist for " + partner);
            assertFalse(threadStats.isShutdown(), "Thread pool should not be shutdown for " + partner);
            
            // Check circuit breaker health
            PartnerCircuitBreakerManager.CircuitBreakerStats cbStats = 
                circuitBreakerManager.getPartnerCircuitBreakerStats(partner);
            assertNotNull(cbStats, "Circuit breaker stats should exist for " + partner);
            
            // Check configuration
            PartnerConfigurationService.PartnerConfig config = configService.getPartnerConfig(partner);
            assertNotNull(config, "Configuration should exist for " + partner);
            assertEquals(partner, config.getBusinessUnit(), "Configuration should match partner");
            
            log.info("✅ {} - Threads: {}, CB State: {}, Config: OK", 
                partner, threadStats.getPoolSize(), cbStats.getState());
        }
        
        // Check cache health
        CacheClientHandler.CacheStats cacheStats = cacheClientHandler.getCacheStats();
        assertTrue(cacheStats.getTotalPartners() >= partners.length, "Cache should have partner data");
        
        log.info("💾 Cache Health - Partners: {}, Tokens: {}", 
            cacheStats.getTotalPartners(), cacheStats.getTotalTokens());
        
        log.info("🎉 SYSTEM HEALTH VERIFICATION PASSED!");
    }
}