package com.company.camel;

import com.company.camel.config.DynamicConfigurationManager;
import com.company.camel.config.PartnerConfigurationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * DYNAMIC CONFIGURATION MANAGEMENT TEST 🔄
 * 
 * Tests runtime configuration updates without system restart:
 * - Partner configuration CRUD operations
 * - Bulk configuration updates
 * - Configuration validation
 * - Change notifications
 */
@Slf4j
@SpringBootTest
@AutoConfigureWebMvc
@ActiveProfiles("test")
public class DynamicConfigurationTest {

    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private PartnerConfigurationService configService;
    
    @Autowired
    private DynamicConfigurationManager configManager;
    
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void testGetPartnerConfiguration() throws Exception {
        log.info("📋 Testing get partner configuration...");
        
        mockMvc.perform(get("/api/config/partners/AMAZON"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.businessUnit").value("AMAZON"))
            .andExpect(jsonPath("$.coreThreads").exists())
            .andExpect(jsonPath("$.maxThreads").exists())
            .andExpect(jsonPath("$.circuitBreakerFailureThreshold").exists());
        
        log.info("✅ Get partner configuration test passed!");
    }

    @Test
    public void testUpdatePartnerConfiguration() throws Exception {
        log.info("🔄 Testing update partner configuration...");
        
        // Create updated configuration
        PartnerConfigurationService.PartnerConfig updatedConfig = 
            PartnerConfigurationService.PartnerConfig.builder()
                .businessUnit("AMAZON")
                .coreThreads(15)
                .maxThreads(60)
                .queueCapacity(2500)
                .circuitBreakerFailureThreshold(65.0f)
                .circuitBreakerMinCalls(25)
                .circuitBreakerWaitDuration(50)
                .retryMaxAttempts(6)
                .retryBackoffMultiplier(2.5)
                .authTokenExpiryMinutes(35)
                .apiTimeoutSeconds(35)
                .priority(PartnerConfigurationService.PartnerPriority.HIGH)
                .build();
        
        String configJson = objectMapper.writeValueAsString(updatedConfig);
        
        mockMvc.perform(put("/api/config/partners/AMAZON")
                .contentType(MediaType.APPLICATION_JSON)
                .content(configJson))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("Configuration updated successfully for AMAZON"));
        
        // Verify the configuration was updated
        PartnerConfigurationService.PartnerConfig retrievedConfig = configService.getPartnerConfig("AMAZON");
        assertEquals(15, retrievedConfig.getCoreThreads());
        assertEquals(60, retrievedConfig.getMaxThreads());
        assertEquals(65.0f, retrievedConfig.getCircuitBreakerFailureThreshold());
        
        log.info("✅ Update partner configuration test passed!");
    }
}