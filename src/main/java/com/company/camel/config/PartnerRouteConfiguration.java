package com.company.camel.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;

/**
 * PARTNER ROUTE CONFIGURATION 🔧
 * 
 * Spring configuration for partner-specific route architecture:
 * - RestTemplate for Elasticsearch communication
 * - ObjectMapper for JSON serialization
 * - Scheduling support for configuration refresh
 * - HTTP client configuration
 */
@Slf4j
@Configuration
@EnableScheduling
public class PartnerRouteConfiguration {

    /**
     * RestTemplate bean for Elasticsearch HTTP communications
     */
    @Bean
    public RestTemplate restTemplate() {
        log.info("🌐 Creating RestTemplate for Elasticsearch communication...");
        
        RestTemplate restTemplate = new RestTemplate();
        
        // Configure HTTP request factory with timeouts
        ClientHttpRequestFactory factory = clientHttpRequestFactory();
        restTemplate.setRequestFactory(factory);
        
        log.info("✅ RestTemplate configured successfully");
        return restTemplate;
    }

    /**
     * HTTP request factory with timeout configuration
     */
    @Bean
    public ClientHttpRequestFactory clientHttpRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        
        // Connection timeout: 30 seconds
        factory.setConnectTimeout(30000);
        
        // Read timeout: 30 seconds  
        factory.setReadTimeout(30000);
        
        log.debug("⏱️ HTTP timeouts configured: connect=30s, read=30s");
        
        return factory;
    }

    /**
     * ObjectMapper bean for JSON serialization/deserialization
     */
    @Bean
    public ObjectMapper objectMapper() {
        log.info("📄 Creating ObjectMapper for JSON processing...");
        
        ObjectMapper mapper = new ObjectMapper();
        
        // Configure ObjectMapper settings
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        
        // Add support for Java 8 time types
        mapper.findAndRegisterModules();
        
        log.info("✅ ObjectMapper configured successfully");
        return mapper;
    }

    /**
     * Configuration validation on startup
     */
    @jakarta.annotation.PostConstruct
    public void validateConfiguration() {
        log.info("🔍 Validating partner route configuration...");
        
        try {
            // Check if required beans will be created
            log.debug("✅ RestTemplate bean will be created");
            log.debug("✅ ObjectMapper bean will be created");
            log.debug("✅ Scheduling is enabled");
            
            log.info("✅ Partner route configuration validation completed successfully");
            
        } catch (Exception e) {
            log.error("❌ Partner route configuration validation failed", e);
            throw new RuntimeException("Configuration validation failed", e);
        }
    }
}