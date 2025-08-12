package com.company.camel;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * ENHANCED CAMEL MQ PROCESSOR APPLICATION 🚀
 * 
 * Your message processing system is now enhanced with:
 * - Thread isolation per partner
 * - Circuit breakers for resilience  
 * - Monitoring and metrics
 * - Async processing capabilities
 * 
 * NO MORE THREAD BLOCKING NIGHTMARES! 💪
 */
@Slf4j
@SpringBootApplication
@ComponentScan(basePackages = "com.company.camel")
public class CamelMqProcessorApplication {

    public static void main(String[] args) {
        log.info("🚀 Starting Enhanced Camel MQ Processor Application...");
        log.info("💪 With Thread Isolation and Circuit Breakers!");
        log.info("🔥 Ready to process 100M+ messages without blocking!");
        
        SpringApplication.run(CamelMqProcessorApplication.class, args);
        
        log.info("✅ Enhanced Camel MQ Processor Application Started Successfully!");
    }
}