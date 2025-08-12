package com.company.camel.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * ELASTICSEARCH PARTNER CONFIGURATION SERVICE 🔍
 * 
 * Loads partner configurations from Elasticsearch for live processing:
 * - Real-time partner config loading
 * - Automatic config refresh
 * - Fallback to default configurations
 * - Integration with PartnerConfigurationService
 */
@Slf4j
@Service
public class ElasticsearchPartnerConfigService {

    @Value("${spring.elasticsearch.uris:http://localhost:9200}")
    private String elasticsearchUri;
    
    @Value("${spring.elasticsearch.username:elastic}")
    private String username;
    
    @Value("${spring.elasticsearch.password:elastic123}")
    private String password;
    
    @Value("${app.partners.config-refresh-interval:300s}")
    private String refreshInterval;

    private ElasticsearchClient elasticsearchClient;
    private ScheduledExecutorService scheduler;
    private final PartnerConfigurationService configService;
    private final ObjectMapper objectMapper;

    private static final String PARTNER_CONFIG_INDEX = "partner-configurations";

    public ElasticsearchPartnerConfigService(PartnerConfigurationService configService) {
        this.configService = configService;
        this.objectMapper = new ObjectMapper();
    }

    @PostConstruct
    public void initialize() {
        try {
            log.info("🔍 Initializing Elasticsearch Partner Config Service...");
            
            // Create Elasticsearch client
            createElasticsearchClient();
            
            // Load initial configurations
            loadPartnerConfigurations();
            
            // Schedule periodic refresh
            scheduleConfigRefresh();
            
            log.info("✅ Elasticsearch Partner Config Service initialized successfully");
            
        } catch (Exception e) {
            log.error("💥 Failed to initialize Elasticsearch Partner Config Service: {}", e.getMessage(), e);
            // Fall back to default configurations
            log.warn("⚠️ Falling back to default partner configurations");
        }
    }

    private void createElasticsearchClient() {
        try {
            // Create credentials provider
            CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(AuthScope.ANY, 
                new UsernamePasswordCredentials(username, password));

            // Create REST client
            RestClient restClient = RestClient.builder(HttpHost.create(elasticsearchUri))
                .setHttpClientConfigCallback(httpClientBuilder -> 
                    httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider))
                .build();

            // Create transport
            ElasticsearchTransport transport = new RestClientTransport(
                restClient, new JacksonJsonpMapper());

            // Create Elasticsearch client
            elasticsearchClient = new ElasticsearchClient(transport);
            
            log.info("🔗 Connected to Elasticsearch at: {}", elasticsearchUri);
            
        } catch (Exception e) {
            log.error("💥 Failed to create Elasticsearch client: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to connect to Elasticsearch", e);
        }
    }

    public void loadPartnerConfigurations() {
        try {
            log.info("📥 Loading partner configurations from Elasticsearch...");
            
            // Search for all partner configurations
            SearchRequest searchRequest = SearchRequest.of(s -> s
                .index(PARTNER_CONFIG_INDEX)
                .size(1000) // Adjust based on number of partners
                .query(q -> q.matchAll(m -> m))
            );

            SearchResponse<Map> response = elasticsearchClient.search(searchRequest, Map.class);
            
            int loadedCount = 0;
            for (Hit<Map> hit : response.hits().hits()) {
                try {
                    Map<String, Object> source = hit.source();
                    if (source != null) {
                        PartnerConfigurationService.PartnerConfig config = mapToPartnerConfig(source);
                        configService.addPartnerConfig(config.getBusinessUnit(), config);
                        loadedCount++;
                        
                        log.debug("📋 Loaded config for partner: {}", config.getBusinessUnit());
                    }
                } catch (Exception e) {
                    log.error("💥 Failed to load config for document {}: {}", hit.id(), e.getMessage());
                }
            }
            
            log.info("✅ Loaded {} partner configurations from Elasticsearch", loadedCount);
            
        } catch (Exception e) {
            log.error("💥 Failed to load partner configurations: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to load partner configurations", e);
        }
    }

    private PartnerConfigurationService.PartnerConfig mapToPartnerConfig(Map<String, Object> source) {
        try {
            return PartnerConfigurationService.PartnerConfig.builder()
                .businessUnit((String) source.get("businessUnit"))
                .coreThreads(getIntValue(source, "coreThreads", 10))
                .maxThreads(getIntValue(source, "maxThreads", 50))
                .queueCapacity(getIntValue(source, "queueCapacity", 2000))
                .keepAliveSeconds(getLongValue(source, "keepAliveSeconds", 300L))
                .circuitBreakerFailureThreshold(getFloatValue(source, "circuitBreakerFailureThreshold", 60.0f))
                .circuitBreakerMinCalls(getIntValue(source, "circuitBreakerMinCalls", 20))
                .circuitBreakerWaitDuration(getIntValue(source, "circuitBreakerWaitDuration", 60))
                .retryMaxAttempts(getIntValue(source, "retryMaxAttempts", 3))
                .retryBackoffMultiplier(getDoubleValue(source, "retryBackoffMultiplier", 2.0))
                .retryInitialDelayMs(getLongValue(source, "retryInitialDelayMs", 1000L))
                .authTokenExpiryMinutes(getIntValue(source, "authTokenExpiryMinutes", 30))
                .authEndpoint((String) source.get("authEndpoint"))
                .authMethod(getStringValue(source, "authMethod", "POST"))
                .apiTimeoutSeconds(getIntValue(source, "apiTimeoutSeconds", 30))
                .apiEndpoint((String) source.get("apiEndpoint"))
                .maxConcurrentCalls(getIntValue(source, "maxConcurrentCalls", 50))
                .priority(getPriorityValue(source, "priority"))
                .healthCheckEnabled(getBooleanValue(source, "healthCheckEnabled", true))
                .healthCheckIntervalSeconds(getIntValue(source, "healthCheckIntervalSeconds", 60))
                .metricsEnabled(getBooleanValue(source, "metricsEnabled", true))
                .alertingEnabled(getBooleanValue(source, "alertingEnabled", true))
                .build();
                
        } catch (Exception e) {
            log.error("💥 Failed to map partner config: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to map partner configuration", e);
        }
    }

    private void scheduleConfigRefresh() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "elasticsearch-config-refresh");
            t.setDaemon(true);
            return t;
        });

        // Parse refresh interval (e.g., "300s" -> 300 seconds)
        long intervalSeconds = parseIntervalToSeconds(refreshInterval);
        
        scheduler.scheduleAtFixedRate(() -> {
            try {
                log.debug("🔄 Refreshing partner configurations from Elasticsearch...");
                loadPartnerConfigurations();
            } catch (Exception e) {
                log.error("💥 Failed to refresh partner configurations: {}", e.getMessage());
            }
        }, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
        
        log.info("⏰ Scheduled config refresh every {} seconds", intervalSeconds);
    }

    private long parseIntervalToSeconds(String interval) {
        try {
            if (interval.endsWith("s")) {
                return Long.parseLong(interval.substring(0, interval.length() - 1));
            } else if (interval.endsWith("m")) {
                return Long.parseLong(interval.substring(0, interval.length() - 1)) * 60;
            } else if (interval.endsWith("h")) {
                return Long.parseLong(interval.substring(0, interval.length() - 1)) * 3600;
            } else {
                return Long.parseLong(interval);
            }
        } catch (Exception e) {
            log.warn("⚠️ Failed to parse refresh interval '{}', using default 300s", interval);
            return 300;
        }
    }

    // Helper methods for safe type conversion
    private int getIntValue(Map<String, Object> source, String key, int defaultValue) {
        Object value = source.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }

    private long getLongValue(Map<String, Object> source, String key, long defaultValue) {
        Object value = source.get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return defaultValue;
    }

    private float getFloatValue(Map<String, Object> source, String key, float defaultValue) {
        Object value = source.get(key);
        if (value instanceof Number) {
            return ((Number) value).floatValue();
        }
        return defaultValue;
    }

    private double getDoubleValue(Map<String, Object> source, String key, double defaultValue) {
        Object value = source.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return defaultValue;
    }

    private String getStringValue(Map<String, Object> source, String key, String defaultValue) {
        Object value = source.get(key);
        return value instanceof String ? (String) value : defaultValue;
    }

    private boolean getBooleanValue(Map<String, Object> source, String key, boolean defaultValue) {
        Object value = source.get(key);
        return value instanceof Boolean ? (Boolean) value : defaultValue;
    }

    private PartnerConfigurationService.PartnerPriority getPriorityValue(Map<String, Object> source, String key) {
        Object value = source.get(key);
        if (value instanceof String) {
            try {
                return PartnerConfigurationService.PartnerPriority.valueOf((String) value);
            } catch (Exception e) {
                log.warn("⚠️ Invalid priority value: {}, using MEDIUM", value);
            }
        }
        return PartnerConfigurationService.PartnerPriority.MEDIUM;
    }

    @PreDestroy
    public void shutdown() {
        log.info("🛑 Shutting down Elasticsearch Partner Config Service...");
        
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        if (elasticsearchClient != null) {
            try {
                elasticsearchClient._transport().close();
            } catch (Exception e) {
                log.error("💥 Error closing Elasticsearch client: {}", e.getMessage());
            }
        }
        
        log.info("✅ Elasticsearch Partner Config Service shutdown complete");
    }
}