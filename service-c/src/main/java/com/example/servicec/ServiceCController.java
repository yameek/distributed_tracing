package com.example.servicec;

import io.micrometer.observation.annotation.Observed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

@RestController
public class ServiceCController {
    
    private static final Logger logger = LoggerFactory.getLogger(ServiceCController.class);
    
    @Autowired
    private RestTemplate restTemplate;
    
    @GetMapping("/inventory/{orderId}")
    public String checkInventory(@PathVariable String orderId) {
        logger.info("Service C: Checking inventory for order {}", orderId);
        
        queryDatabase(orderId);
        
        int stockLevel = checkStockLevel(orderId);
        logger.info("Service C: Stock level for order {}: {}", orderId, stockLevel);
        
        reserveInventory(orderId, stockLevel);
        
        updateInventoryCache(orderId);
        
        notifyServiceD(orderId, "RESERVED");
        
        logger.info("Service C: Inventory check completed for order {}", orderId);
        return "Service C (Inventory): Stock available for order " + orderId;
    }
    
    @GetMapping("/health")
    public String health() {
        return "Service C is running";
    }
    
    @Observed(name = "service-c.query-database")
    private void queryDatabase(String orderId) {
        logger.debug("Service C: Querying database for order {}", orderId);
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    @Observed(name = "service-c.check-stock-level")
    private int checkStockLevel(String orderId) {
        logger.debug("Service C: Checking stock level for order {}", orderId);
        try {
            Thread.sleep(40);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return 100 + orderId.hashCode() % 50;
    }
    
    @Observed(name = "service-c.reserve-inventory")
    private void reserveInventory(String orderId, int quantity) {
        logger.debug("Service C: Reserving {} units for order {}", quantity, orderId);
        try {
            Thread.sleep(30);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    @Observed(name = "service-c.update-cache")
    private void updateInventoryCache(String orderId) {
        logger.debug("Service C: Updating cache for order {}", orderId);
        try {
            Thread.sleep(20);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    private void notifyServiceD(String orderId, String status) {
        logger.info("Service C: Notifying Service D about order {}", orderId);
        try {
            String payload = String.format("{\"orderId\":\"%s\",\"type\":\"ORDER_UPDATE\",\"status\":\"%s\",\"channel\":\"EMAIL\",\"callbackRequired\":true}", 
                orderId, status);
            restTemplate.postForObject(
                "http://localhost:8083/notify", 
                payload,
                String.class
            );
        } catch (Exception e) {
            logger.warn("Service C: Failed to notify Service D: {}", e.getMessage());
        }
    }
}

