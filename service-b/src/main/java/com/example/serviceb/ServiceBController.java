package com.example.serviceb;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
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
    
    @Autowired
    private RabbitTemplate rabbitTemplate;
    
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
        
        String callbackResponse = restTemplate.getForObject(
            "http://localhost:8080/process/" + orderId,
            String.class
        );
        
        sendAsyncNotification(orderId, "ORDER_PROCESSED");
        
        logger.info("Service B: Order {} processed successfully", orderId);
        return "Service B -> C: " + inventoryResponse;
    }
    
    @GetMapping("/health")
    public String health() {
        return "Service B is running";
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
    
    private void sendAsyncNotification(String orderId, String eventType) {
        logger.info("Service B: Sending async notification to Service D for order {}", orderId);
        try {
            NotificationRequest notification = new NotificationRequest();
            notification.setOrderId(orderId);
            notification.setType(eventType);
            notification.setStatus("PROCESSED");
            notification.setChannel("SMS");
            notification.setCallbackRequired(false);
            
            rabbitTemplate.convertAndSend("notification-queue", notification);
            logger.info("Service B: Notification sent to queue");
        } catch (Exception e) {
            logger.warn("Service B: Failed to send async notification: {}", e.getMessage());
        }
    }
}
