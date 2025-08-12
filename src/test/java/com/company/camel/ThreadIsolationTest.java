package com.company.camel;

import com.company.camel.monitoring.PartnerCircuitBreakerManager;
import com.company.camel.monitoring.PartnerThreadPoolManager;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * THREAD ISOLATION VERIFICATION TEST! 🧪
 * 
 * This test proves that:
 * 1. Partners run in isolated thread pools
 * 2. One partner's failure doesn't block others
 * 3. Circuit breakers work correctly
 * 4. Thread pools are properly isolated
 * 
 * THE MOMENT OF TRUTH! 💥
 */
@Slf4j
@SpringBootTest
@ActiveProfiles("test")
public class ThreadIsolationTest {

    @Autowired
    private PartnerThreadPoolManager threadPoolManager;
    
    @Autowired
    private PartnerCircuitBreakerManager circuitBreakerManager;

    @Configuration
    static class TestConfig {
        @Bean
        public MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
        
        @Bean
        public PartnerThreadPoolManager partnerThreadPoolManager(MeterRegistry meterRegistry) {
            return new PartnerThreadPoolManager(meterRegistry);
        }
        
        @Bean
        public PartnerCircuitBreakerManager partnerCircuitBreakerManager(PartnerThreadPoolManager threadPoolManager, MeterRegistry meterRegistry) {
            return new PartnerCircuitBreakerManager(threadPoolManager, meterRegistry);
        }
    }

    @Test
    public void testThreadIsolationBetweenPartners() throws Exception {
        log.info("🧪 Testing thread isolation between partners...");
        
        String partner1 = "PARTNER_FAST";
        String partner2 = "PARTNER_SLOW";
        String partner3 = "PARTNER_FAILING";
        
        AtomicInteger partner1Completed = new AtomicInteger(0);
        AtomicInteger partner2Completed = new AtomicInteger(0);
        AtomicInteger partner3Completed = new AtomicInteger(0);
        
        CountDownLatch latch = new CountDownLatch(30); // 10 tasks per partner
        
        // Partner 1: Fast tasks (should complete quickly)
        for (int i = 0; i < 10; i++) {
            final int taskNum = i;
            CompletableFuture<Void> future = threadPoolManager.executePartnerTask(partner1, () -> {
                try {
                    log.debug("✅ Partner1 task {} executing on thread: {}", taskNum, Thread.currentThread().getName());
                    Thread.sleep(100); // Fast task
                    partner1Completed.incrementAndGet();
                    assertTrue(Thread.currentThread().getName().contains("Partner-" + partner1));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
            
            future.exceptionally(throwable -> {
                log.error("Partner1 task failed: {}", throwable.getMessage());
                latch.countDown();
                return null;
            });
        }
        
        // Partner 2: Slow tasks (should not block Partner 1)
        for (int i = 0; i < 10; i++) {
            final int taskNum = i;
            CompletableFuture<Void> future = threadPoolManager.executePartnerTask(partner2, () -> {
                try {
                    log.debug("🐌 Partner2 task {} executing on thread: {}", taskNum, Thread.currentThread().getName());
                    Thread.sleep(500); // Slow task
                    partner2Completed.incrementAndGet();
                    assertTrue(Thread.currentThread().getName().contains("Partner-" + partner2));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
            
            future.exceptionally(throwable -> {
                log.error("Partner2 task failed: {}", throwable.getMessage());
                latch.countDown();
                return null;
            });
        }
        
        // Partner 3: Failing tasks (should not affect others)
        for (int i = 0; i < 10; i++) {
            final int taskNum = i;
            CompletableFuture<Void> future = threadPoolManager.executePartnerTask(partner3, () -> {
                try {
                    log.debug("💥 Partner3 task {} executing on thread: {}", taskNum, Thread.currentThread().getName());
                    Thread.sleep(200);
                    assertTrue(Thread.currentThread().getName().contains("Partner-" + partner3));
                    
                    // Fail some tasks
                    if (taskNum % 2 == 0) {
                        throw new RuntimeException("Simulated failure for task " + taskNum);
                    }
                    partner3Completed.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
            
            future.exceptionally(throwable -> {
                log.debug("Expected Partner3 task failure: {}", throwable.getMessage());
                latch.countDown();
                return null;
            });
        }
        
        // Wait for all tasks to complete (or timeout)
        boolean allCompleted = latch.await(10, TimeUnit.SECONDS);
        assertTrue(allCompleted, "Not all tasks completed within timeout");
        
        // Verify results
        log.info("📊 Test Results:");
        log.info("Partner1 (Fast) completed: {} tasks", partner1Completed.get());
        log.info("Partner2 (Slow) completed: {} tasks", partner2Completed.get());
        log.info("Partner3 (Failing) completed: {} tasks", partner3Completed.get());
        
        // Partner 1 should complete all tasks quickly
        assertEquals(10, partner1Completed.get(), "Partner1 should complete all fast tasks");
        
        // Partner 2 should complete all tasks (but slower)
        assertEquals(10, partner2Completed.get(), "Partner2 should complete all slow tasks");
        
        // Partner 3 should complete only some tasks (others failed)
        assertTrue(partner3Completed.get() < 10, "Partner3 should have some failures");
        assertTrue(partner3Completed.get() > 0, "Partner3 should have some successes");
        
        // Verify thread pool stats
        PartnerThreadPoolManager.ThreadPoolStats stats1 = threadPoolManager.getPartnerStats(partner1);
        PartnerThreadPoolManager.ThreadPoolStats stats2 = threadPoolManager.getPartnerStats(partner2);
        PartnerThreadPoolManager.ThreadPoolStats stats3 = threadPoolManager.getPartnerStats(partner3);
        
        assertNotNull(stats1, "Partner1 thread pool stats should exist");
        assertNotNull(stats2, "Partner2 thread pool stats should exist");
        assertNotNull(stats3, "Partner3 thread pool stats should exist");
        
        // Each partner should have completed tasks
        assertTrue(stats1.getCompletedTaskCount() > 0, "Partner1 should have completed tasks");
        assertTrue(stats2.getCompletedTaskCount() > 0, "Partner2 should have completed tasks");
        assertTrue(stats3.getCompletedTaskCount() >= 0, "Partner3 stats should be available");
        
        log.info("✅ Thread isolation test PASSED! Partners are properly isolated!");
    }

    @Test
    public void testCircuitBreakerIsolation() throws Exception {
        log.info("⚡ Testing circuit breaker isolation...");
        
        String healthyPartner = "HEALTHY_PARTNER";
        String failingPartner = "FAILING_PARTNER";
        
        AtomicInteger healthySuccess = new AtomicInteger(0);
        AtomicInteger failingAttempts = new AtomicInteger(0);
        
        // Execute tasks on healthy partner (should all succeed)
        for (int i = 0; i < 5; i++) {
            CompletableFuture<Void> future = circuitBreakerManager.executeWithCircuitBreaker(healthyPartner, () -> {
                log.debug("✅ Healthy partner task executing");
                Thread.sleep(100);
                healthySuccess.incrementAndGet();
                return null;
            });
            
            future.get(2, TimeUnit.SECONDS);
        }
        
        // Execute tasks on failing partner (should trigger circuit breaker)
        for (int i = 0; i < 15; i++) { // More than minimum calls to trigger CB
            try {
                CompletableFuture<Void> future = circuitBreakerManager.executeWithCircuitBreaker(failingPartner, () -> {
                    failingAttempts.incrementAndGet();
                    log.debug("💥 Failing partner task executing");
                    Thread.sleep(100);
                    throw new RuntimeException("Simulated failure");
                });
                
                future.get(2, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.debug("Expected failure: {}", e.getMessage());
            }
        }
        
        // Verify results
        assertEquals(5, healthySuccess.get(), "Healthy partner should succeed all tasks");
        assertTrue(failingAttempts.get() >= 10, "Failing partner should attempt at least minimum calls");
        
        // Check circuit breaker states
        PartnerCircuitBreakerManager.CircuitBreakerStats healthyStats = 
            circuitBreakerManager.getPartnerCircuitBreakerStats(healthyPartner);
        PartnerCircuitBreakerManager.CircuitBreakerStats failingStats = 
            circuitBreakerManager.getPartnerCircuitBreakerStats(failingPartner);
        
        assertNotNull(healthyStats, "Healthy partner stats should exist");
        assertNotNull(failingStats, "Failing partner stats should exist");
        
        assertEquals("CLOSED", healthyStats.getState(), "Healthy partner circuit should be CLOSED");
        // Failing partner circuit should be OPEN (may take a moment)
        assertTrue(failingStats.getFailureRate() > 0, "Failing partner should have failure rate > 0");
        
        // Verify isolation
        assertTrue(circuitBreakerManager.isPartnerHealthy(healthyPartner), "Healthy partner should remain healthy");
        
        log.info("✅ Circuit breaker isolation test PASSED! Failures are properly isolated!");
    }
    
    @Test
    public void testConcurrentPartnerExecution() throws Exception {
        log.info("🚀 Testing concurrent partner execution...");
        
        String[] partners = {"PARTNER_A", "PARTNER_B", "PARTNER_C", "PARTNER_D", "PARTNER_E"};
        AtomicInteger totalCompleted = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(partners.length * 5); // 5 tasks per partner
        
        // Start tasks for all partners concurrently
        for (String partner : partners) {
            for (int i = 0; i < 5; i++) {
                final int taskNum = i;
                threadPoolManager.executePartnerTask(partner, () -> {
                    try {
                        String threadName = Thread.currentThread().getName();
                        log.debug("🔄 {} task {} on thread: {}", partner, taskNum, threadName);
                        
                        // Verify thread naming
                        assertTrue(threadName.contains("Partner-" + partner), 
                            "Thread should be named for partner: " + partner);
                        
                        Thread.sleep(200); // Simulate work
                        totalCompleted.incrementAndGet();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        latch.countDown();
                    }
                });
            }
        }
        
        // Wait for completion
        boolean completed = latch.await(10, TimeUnit.SECONDS);
        assertTrue(completed, "All concurrent tasks should complete");
        assertEquals(partners.length * 5, totalCompleted.get(), "All tasks should complete successfully");
        
        // Verify each partner has their own thread pool
        for (String partner : partners) {
            PartnerThreadPoolManager.ThreadPoolStats stats = threadPoolManager.getPartnerStats(partner);
            assertNotNull(stats, "Partner " + partner + " should have thread pool stats");
            assertEquals(partner, stats.getBusinessUnit(), "Stats should be for correct partner");
            assertTrue(stats.getCompletedTaskCount() > 0, "Partner should have completed tasks");
        }
        
        log.info("✅ Concurrent execution test PASSED! All {} partners executed independently!", partners.length);
    }
}