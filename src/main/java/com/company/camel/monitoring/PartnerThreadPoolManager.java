package com.company.camel.monitoring;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Map;

/**
 * THE THREAD ISOLATION BEAST! 🔥
 * 
 * This service manages separate thread pools for each partner to prevent
 * the nightmare scenario where one partner's failures block everyone else.
 * 
 * Each partner gets their own isolated thread pool with:
 * - Custom sizing based on partner volume
 * - Monitoring and metrics
 * - Graceful shutdown handling
 * - Circuit breaker integration ready
 */
@Slf4j
@Service
public class PartnerThreadPoolManager {
    
    private final Map<String, ThreadPoolExecutor> partnerThreadPools = new ConcurrentHashMap<>();
    private final Map<String, Counter> partnerTaskCounters = new ConcurrentHashMap<>();
    private final MeterRegistry meterRegistry;
    
    // Default thread pool settings - can be customized per partner
    private static final int DEFAULT_CORE_POOL_SIZE = 5;
    private static final int DEFAULT_MAX_POOL_SIZE = 20;
    private static final long DEFAULT_KEEP_ALIVE_TIME = 60L;
    private static final int DEFAULT_QUEUE_CAPACITY = 1000;
    
    public PartnerThreadPoolManager(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        log.info("🚀 PartnerThreadPoolManager initialized - Ready to isolate those threads!");
    }
    
    /**
     * Get or create a thread pool for a specific partner
     * Each partner gets their own isolated thread pool!
     */
    public ThreadPoolExecutor getPartnerThreadPool(String businessUnit) {
        return partnerThreadPools.computeIfAbsent(businessUnit, this::createPartnerThreadPool);
    }
    
    /**
     * Execute a task in the partner's isolated thread pool
     * This prevents thread blocking across partners! 💪
     */
    public CompletableFuture<Void> executePartnerTask(String businessUnit, Runnable task) {
        ThreadPoolExecutor threadPool = getPartnerThreadPool(businessUnit);
        
        // Increment task counter for monitoring
        partnerTaskCounters.computeIfAbsent(businessUnit, 
            bu -> Counter.builder("partner.tasks.executed")
                    .description("Number of tasks executed for partner")
                    .tag("partner", bu)
                    .register(meterRegistry))
                .increment();
        
        return CompletableFuture.runAsync(() -> {
            try {
                log.debug("🔥 Executing task for partner: {} on thread: {}", 
                    businessUnit, Thread.currentThread().getName());
                task.run();
            } catch (Exception e) {
                log.error("💥 Task failed for partner: {} - {}", businessUnit, e.getMessage(), e);
                throw new RuntimeException("Partner task failed: " + businessUnit, e);
            }
        }, threadPool);
    }
    
    /**
     * Execute a task with result in the partner's isolated thread pool
     */
    public <T> CompletableFuture<T> executePartnerTask(String businessUnit, Callable<T> task) {
        ThreadPoolExecutor threadPool = getPartnerThreadPool(businessUnit);
        
        partnerTaskCounters.computeIfAbsent(businessUnit, 
            bu -> Counter.builder("partner.tasks.executed")
                    .description("Number of tasks executed for partner")
                    .tag("partner", bu)
                    .register(meterRegistry))
                .increment();
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("🔥 Executing callable task for partner: {} on thread: {}", 
                    businessUnit, Thread.currentThread().getName());
                return task.call();
            } catch (Exception e) {
                log.error("💥 Callable task failed for partner: {} - {}", businessUnit, e.getMessage(), e);
                throw new RuntimeException("Partner callable task failed: " + businessUnit, e);
            }
        }, threadPool);
    }
    
    /**
     * Create a new thread pool for a partner with monitoring
     */
    private ThreadPoolExecutor createPartnerThreadPool(String businessUnit) {
        log.info("🎯 Creating new thread pool for partner: {}", businessUnit);
        
        // Custom thread factory to name threads properly
        ThreadFactory threadFactory = new ThreadFactory() {
            private final AtomicInteger threadNumber = new AtomicInteger(1);
            
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "Partner-" + businessUnit + "-Thread-" + threadNumber.getAndIncrement());
                t.setDaemon(false);
                t.setPriority(Thread.NORM_PRIORITY);
                return t;
            }
        };
        
        // Create the thread pool with bounded queue to prevent memory issues
        ThreadPoolExecutor threadPool = new ThreadPoolExecutor(
            DEFAULT_CORE_POOL_SIZE,
            DEFAULT_MAX_POOL_SIZE,
            DEFAULT_KEEP_ALIVE_TIME,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(DEFAULT_QUEUE_CAPACITY),
            threadFactory,
            new ThreadPoolExecutor.CallerRunsPolicy() // Fallback policy
        );
        
        // Register metrics for this thread pool
        registerThreadPoolMetrics(businessUnit, threadPool);
        
        log.info("✅ Thread pool created for partner: {} with core={}, max={}, queue={}", 
            businessUnit, DEFAULT_CORE_POOL_SIZE, DEFAULT_MAX_POOL_SIZE, DEFAULT_QUEUE_CAPACITY);
        
        return threadPool;
    }
    
    /**
     * Register metrics for monitoring thread pool health
     */
    private void registerThreadPoolMetrics(String businessUnit, ThreadPoolExecutor threadPool) {
        // Active threads
        Gauge.builder("partner.threadpool.active", threadPool, ThreadPoolExecutor::getActiveCount)
            .description("Number of active threads in partner pool")
            .tag("partner", businessUnit)
            .register(meterRegistry);
        
        // Pool size
        Gauge.builder("partner.threadpool.size", threadPool, ThreadPoolExecutor::getPoolSize)
            .description("Current size of partner thread pool")
            .tag("partner", businessUnit)
            .register(meterRegistry);
        
        // Queue size
        Gauge.builder("partner.threadpool.queue.size", threadPool, tp -> tp.getQueue().size())
            .description("Number of tasks in partner thread pool queue")
            .tag("partner", businessUnit)
            .register(meterRegistry);
        
        // Completed tasks
        Gauge.builder("partner.threadpool.completed", threadPool, ThreadPoolExecutor::getCompletedTaskCount)
            .description("Number of completed tasks in partner thread pool")
            .tag("partner", businessUnit)
            .register(meterRegistry);
    }
    
    /**
     * Get thread pool stats for a partner
     */
    public ThreadPoolStats getPartnerStats(String businessUnit) {
        ThreadPoolExecutor threadPool = partnerThreadPools.get(businessUnit);
        if (threadPool == null) {
            return null;
        }
        
        return ThreadPoolStats.builder()
            .businessUnit(businessUnit)
            .activeCount(threadPool.getActiveCount())
            .poolSize(threadPool.getPoolSize())
            .corePoolSize(threadPool.getCorePoolSize())
            .maximumPoolSize(threadPool.getMaximumPoolSize())
            .queueSize(threadPool.getQueue().size())
            .completedTaskCount(threadPool.getCompletedTaskCount())
            .isShutdown(threadPool.isShutdown())
            .isTerminated(threadPool.isTerminated())
            .build();
    }
    
    /**
     * Get all partner thread pool stats
     */
    public Map<String, ThreadPoolStats> getAllPartnerStats() {
        Map<String, ThreadPoolStats> stats = new ConcurrentHashMap<>();
        partnerThreadPools.forEach((partner, pool) -> {
            stats.put(partner, getPartnerStats(partner));
        });
        return stats;
    }
    
    /**
     * Gracefully shutdown all thread pools
     */
    @PreDestroy
    public void shutdown() {
        log.info("🛑 Shutting down all partner thread pools...");
        
        partnerThreadPools.forEach((partner, threadPool) -> {
            log.info("Shutting down thread pool for partner: {}", partner);
            threadPool.shutdown();
            
            try {
                if (!threadPool.awaitTermination(30, TimeUnit.SECONDS)) {
                    log.warn("Thread pool for partner {} did not terminate gracefully, forcing shutdown", partner);
                    threadPool.shutdownNow();
                }
            } catch (InterruptedException e) {
                log.error("Interrupted while shutting down thread pool for partner: {}", partner);
                threadPool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        });
        
        log.info("✅ All partner thread pools shutdown complete");
    }
    
    /**
     * Data class for thread pool statistics
     */
    @lombok.Builder
    @lombok.Data
    public static class ThreadPoolStats {
        private String businessUnit;
        private int activeCount;
        private int poolSize;
        private int corePoolSize;
        private int maximumPoolSize;
        private int queueSize;
        private long completedTaskCount;
        private boolean isShutdown;
        private boolean isTerminated;
    }
}