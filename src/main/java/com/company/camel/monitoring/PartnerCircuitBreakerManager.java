package com.company.camel.monitoring;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * THE CIRCUIT BREAKER BEAST! ⚡
 * 
 * This service manages circuit breakers for each partner to prevent:
 * - Cascading failures when a partner is down
 * - Resource waste on failing partners
 * - Thread pool exhaustion
 * - System-wide outages due to single partner issues
 * 
 * Each partner gets their own circuit breaker with customizable:
 * - Failure rate thresholds
 * - Wait duration in open state
 * - Minimum number of calls
 * - Sliding window settings
 */
@Slf4j
@Service
public class PartnerCircuitBreakerManager {

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final PartnerThreadPoolManager threadPoolManager;
    private final MeterRegistry meterRegistry;
    private final Map<String, Counter> partnerFailureCounters = new ConcurrentHashMap<>();
    private final Map<String, Counter> partnerSuccessCounters = new ConcurrentHashMap<>();

    // Default circuit breaker configuration
    private static final float DEFAULT_FAILURE_RATE_THRESHOLD = 50.0f; // 50% failure rate
    private static final int DEFAULT_MINIMUM_NUMBER_OF_CALLS = 10; // Minimum calls before evaluating
    private static final Duration DEFAULT_WAIT_DURATION_IN_OPEN_STATE = Duration.ofSeconds(30); // 30 seconds wait
    private static final int DEFAULT_SLIDING_WINDOW_SIZE = 20; // Sliding window of 20 calls
    
    public PartnerCircuitBreakerManager(PartnerThreadPoolManager threadPoolManager, MeterRegistry meterRegistry) {
        this.threadPoolManager = threadPoolManager;
        this.meterRegistry = meterRegistry;
        
        // Create default circuit breaker config
        CircuitBreakerConfig defaultConfig = CircuitBreakerConfig.custom()
            .failureRateThreshold(DEFAULT_FAILURE_RATE_THRESHOLD)
            .minimumNumberOfCalls(DEFAULT_MINIMUM_NUMBER_OF_CALLS)
            .waitDurationInOpenState(DEFAULT_WAIT_DURATION_IN_OPEN_STATE)
            .slidingWindowSize(DEFAULT_SLIDING_WINDOW_SIZE)
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .build();
        
        this.circuitBreakerRegistry = CircuitBreakerRegistry.of(defaultConfig);
        
        log.info("🔥 PartnerCircuitBreakerManager initialized - Ready to break some circuits!");
    }

    /**
     * Get or create a circuit breaker for a specific partner
     */
    public CircuitBreaker getPartnerCircuitBreaker(String businessUnit) {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(businessUnit);
        
        // Register event listeners if this is a new circuit breaker
        if (!partnerFailureCounters.containsKey(businessUnit)) {
            registerCircuitBreakerEvents(businessUnit, circuitBreaker);
        }
        
        return circuitBreaker;
    }

    /**
     * Execute a partner operation with circuit breaker and thread isolation
     * This is where the magic happens! 🎩✨
     */
    public <T> CompletableFuture<T> executeWithCircuitBreaker(String businessUnit, Callable<T> operation) {
        CircuitBreaker circuitBreaker = getPartnerCircuitBreaker(businessUnit);
        
        // Decorate the operation with circuit breaker
        Supplier<T> decoratedOperation = CircuitBreaker.decorateSupplier(circuitBreaker, () -> {
            try {
                return operation.call();
            } catch (Exception e) {
                log.error("💥 Operation failed for partner: {} - {}", businessUnit, e.getMessage());
                throw new RuntimeException("Partner operation failed: " + businessUnit, e);
            }
        });
        
        // Execute in partner's isolated thread pool
        return threadPoolManager.executePartnerTask(businessUnit, decoratedOperation::get);
    }

    /**
     * Execute a partner operation without return value
     */
    public CompletableFuture<Void> executeWithCircuitBreaker(String businessUnit, Runnable operation) {
        return executeWithCircuitBreaker(businessUnit, () -> {
            operation.run();
            return null;
        }).thenApply(result -> null);
    }

    /**
     * Register event listeners for circuit breaker monitoring
     */
    private void registerCircuitBreakerEvents(String businessUnit, CircuitBreaker circuitBreaker) {
        // Success counter
        Counter successCounter = Counter.builder("partner.circuitbreaker.success")
            .description("Number of successful partner operations")
            .tag("partner", businessUnit)
            .register(meterRegistry);
        partnerSuccessCounters.put(businessUnit, successCounter);

        // Failure counter
        Counter failureCounter = Counter.builder("partner.circuitbreaker.failure")
            .description("Number of failed partner operations")
            .tag("partner", businessUnit)
            .register(meterRegistry);
        partnerFailureCounters.put(businessUnit, failureCounter);

        // Event listeners
        circuitBreaker.getEventPublisher()
            .onSuccess(event -> {
                log.debug("✅ Circuit breaker success for partner: {} (duration: {}ms)", 
                    businessUnit, event.getElapsedDuration().toMillis());
                successCounter.increment();
            })
            .onError(event -> {
                log.warn("❌ Circuit breaker error for partner: {} - {} (duration: {}ms)", 
                    businessUnit, event.getThrowable().getMessage(), event.getElapsedDuration().toMillis());
                failureCounter.increment();
            })
            .onStateTransition(event -> {
                log.warn("🔄 Circuit breaker state transition for partner: {} from {} to {}", 
                    businessUnit, event.getStateTransition().getFromState(), event.getStateTransition().getToState());
                
                // Record state transition
                Counter.builder("partner.circuitbreaker.state.transition")
                    .description("Circuit breaker state transitions")
                    .tag("partner", businessUnit)
                    .tag("from_state", event.getStateTransition().getFromState().name())
                    .tag("to_state", event.getStateTransition().getToState().name())
                    .register(meterRegistry)
                    .increment();
            })
            .onCallNotPermitted(event -> {
                log.warn("🚫 Circuit breaker call not permitted for partner: {} - Circuit is OPEN", businessUnit);
                
                Counter.builder("partner.circuitbreaker.rejected")
                    .description("Number of rejected calls due to open circuit")
                    .tag("partner", businessUnit)
                    .register(meterRegistry)
                    .increment();
            });
    }

    /**
     * Get circuit breaker statistics for a partner
     */
    public CircuitBreakerStats getPartnerCircuitBreakerStats(String businessUnit) {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(businessUnit);
        if (circuitBreaker == null) {
            return null;
        }

        CircuitBreaker.Metrics metrics = circuitBreaker.getMetrics();
        
        return CircuitBreakerStats.builder()
            .businessUnit(businessUnit)
            .state(circuitBreaker.getState().name())
            .failureRate(metrics.getFailureRate())
            .numberOfCalls((int) metrics.getNumberOfBufferedCalls())
            .numberOfSuccessfulCalls((int) metrics.getNumberOfSuccessfulCalls())
            .numberOfFailedCalls((int) metrics.getNumberOfFailedCalls())
            .numberOfNotPermittedCalls(metrics.getNumberOfNotPermittedCalls())
            .build();
    }

    /**
     * Get all circuit breaker statistics
     */
    public Map<String, CircuitBreakerStats> getAllCircuitBreakerStats() {
        Map<String, CircuitBreakerStats> stats = new ConcurrentHashMap<>();
        circuitBreakerRegistry.getAllCircuitBreakers().forEach(circuitBreaker -> {
            String businessUnit = circuitBreaker.getName();
            stats.put(businessUnit, getPartnerCircuitBreakerStats(businessUnit));
        });
        return stats;
    }

    /**
     * Force circuit breaker to open state (for emergency shutdown)
     */
    public void forceOpen(String businessUnit) {
        CircuitBreaker circuitBreaker = getPartnerCircuitBreaker(businessUnit);
        circuitBreaker.transitionToOpenState();
        log.warn("🔴 Forced circuit breaker OPEN for partner: {}", businessUnit);
    }

    /**
     * Force circuit breaker to closed state (for recovery)
     */
    public void forceClosed(String businessUnit) {
        CircuitBreaker circuitBreaker = getPartnerCircuitBreaker(businessUnit);
        circuitBreaker.transitionToClosedState();
        log.info("🟢 Forced circuit breaker CLOSED for partner: {}", businessUnit);
    }

    /**
     * Force circuit breaker to half-open state (for testing)
     */
    public void forceHalfOpen(String businessUnit) {
        CircuitBreaker circuitBreaker = getPartnerCircuitBreaker(businessUnit);
        circuitBreaker.transitionToHalfOpenState();
        log.info("🟡 Forced circuit breaker HALF-OPEN for partner: {}", businessUnit);
    }

    /**
     * Check if partner is healthy (circuit breaker closed)
     */
    public boolean isPartnerHealthy(String businessUnit) {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(businessUnit);
        return circuitBreaker == null || circuitBreaker.getState() == CircuitBreaker.State.CLOSED;
    }

    /**
     * Cleanup resources on shutdown
     */
    @PreDestroy
    public void shutdown() {
        log.info("🛑 Shutting down circuit breaker manager...");
        // Circuit breakers will be cleaned up automatically
        log.info("✅ Circuit breaker manager shutdown complete");
    }

    /**
     * Data class for circuit breaker statistics
     */
    @lombok.Builder
    @lombok.Data
    public static class CircuitBreakerStats {
        private String businessUnit;
        private String state;
        private float failureRate;
        private int numberOfCalls;
        private int numberOfSuccessfulCalls;
        private int numberOfFailedCalls;
        private long numberOfNotPermittedCalls;
    }
}