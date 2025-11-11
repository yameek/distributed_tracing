package com.example.servicec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
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
    
    @Autowired
    private RabbitTemplate rabbitTemplate;
    
    @GetMapping("/inventory/{orderId}")
    public String checkInventory(@PathVariable String orderId) {
        logger.info("Service C: Checking inventory for order {}", orderId);
        
        queryDatabase(orderId);
        
        int stockLevel = checkStockLevel(orderId);
        logger.info("Service C: Stock level for order {}: {}", orderId, stockLevel);
        
        reserveInventory(orderId, stockLevel);
        
        updateInventoryCache(orderId);
        
        String callbackResponse = restTemplate.getForObject(
            "http://localhost:8080/verify/" + orderId,
            String.class
        );
        
        sendAsyncNotification(orderId, "INVENTORY_RESERVED");
        
        logger.info("Service C: Inventory check completed for order {}", orderId);
        return "Service C: Stock available for order " + orderId;
    }
    
    @GetMapping("/health")
    public String health() {
        return "Service C is running";
    }
    
    private void queryDatabase(String orderId) {
        logger.debug("Service C: Querying database for order {}", orderId);
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    private int checkStockLevel(String orderId) {
        logger.debug("Service C: Checking stock level for order {}", orderId);
        try {
            Thread.sleep(40);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return 100 + orderId.hashCode() % 50;
    }
    
    private void reserveInventory(String orderId, int quantity) {
        logger.debug("Service C: Reserving {} units for order {}", quantity, orderId);
        try {
            Thread.sleep(30);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    private void updateInventoryCache(String orderId) {
        logger.debug("Service C: Updating cache for order {}", orderId);
        try {
            Thread.sleep(20);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    private void sendAsyncNotification(String orderId, String eventType) {
        logger.info("Service C: Sending async notification to Service D for order {}", orderId);
        try {
            NotificationRequest notification = new NotificationRequest();
            notification.setOrderId(orderId);
            notification.setType(eventType);
            notification.setStatus("RESERVED");
            notification.setChannel("PUSH");
            notification.setCallbackRequired(false);
            
            rabbitTemplate.convertAndSend("notification-queue", notification);
            logger.info("Service C: Notification sent to queue");
        } catch (Exception e) {
            logger.warn("Service C: Failed to send async notification: {}", e.getMessage());
        }
    }
}

