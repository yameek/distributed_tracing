package com.example.serviceb;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

@RestController
public class ServiceBController {
    
    private static final Logger logger = LoggerFactory.getLogger(ServiceBController.class);
    
    @Autowired
    private RestTemplate restTemplate;
    
    @GetMapping("/order/{orderId}")
    public String processOrder(@PathVariable String orderId) {
        logger.info("Service B: Processing order {}", orderId);
        
        checkOrderEligibility(orderId);
        
        double orderAmount = calculateOrderAmount(orderId);
        logger.info("Service B: Order amount calculated: ${}", orderAmount);
        
        String inventoryResponse = restTemplate.getForObject(
            "http://localhost:8082/inventory/" + orderId, 
            String.class
        );
        
        applyBusinessRules(orderId, orderAmount);
        
        logger.info("Service B: Order {} processed successfully", orderId);
        return "Service B (Order) -> " + inventoryResponse;
    }
    
    private void checkOrderEligibility(String orderId) {
        logger.debug("Service B: Checking eligibility for order {}", orderId);
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    private double calculateOrderAmount(String orderId) {
        logger.debug("Service B: Calculating amount for order {}", orderId);
        try {
            Thread.sleep(30);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return 99.99 + orderId.hashCode() % 100;
    }
    
    private void applyBusinessRules(String orderId, double amount) {
        logger.debug("Service B: Applying business rules for order {} with amount ${}", orderId, amount);
        try {
            Thread.sleep(20);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    @GetMapping("/health")
    public String health() {
        return "Service B is running";
    }
}
