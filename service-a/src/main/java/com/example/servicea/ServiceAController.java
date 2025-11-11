package com.example.servicea;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@RestController
public class ServiceAController {
    
    private static final Logger logger = LoggerFactory.getLogger(ServiceAController.class);
    
    @Autowired
    private RestTemplate restTemplate;
    
    @Autowired
    private RabbitTemplate rabbitTemplate;
    
    @GetMapping("/api/order/{orderId}")
    public String getOrder(@PathVariable String orderId) {
        logger.info("Service A: Received request for order {}", orderId);
        
        validateRequest(orderId);
        
        Map<String, Object> orderMetadata = prepareOrderMetadata(orderId);
        logger.info("Service A: Order metadata prepared: {}", orderMetadata);
        
        String orderResponse = restTemplate.getForObject(
            "http://service-b:8081/order/" + orderId, 
            String.class
        );
        
        String inventoryResponse = restTemplate.getForObject(
            "http://service-c:8082/inventory/" + orderId,
            String.class
        );
        
        sendAsyncNotification(orderId, "ORDER_CREATED");
        
        String finalResponse = formatResponse(orderResponse, inventoryResponse);
        
        logger.info("Service A: Completed request for order {}", orderId);
        return finalResponse;
    }
    
    private void validateRequest(String orderId) {
        logger.debug("Service A: Validating order ID {}", orderId);
        if (orderId == null || orderId.isEmpty()) {
            throw new IllegalArgumentException("Order ID cannot be empty");
        }
    }
    
    private Map<String, Object> prepareOrderMetadata(String orderId) {
        logger.debug("Service A: Preparing metadata for order {}", orderId);
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("orderId", orderId);
        metadata.put("timestamp", System.currentTimeMillis());
        metadata.put("source", "service-a");
        return metadata;
    }
    
    private String formatResponse(String orderResponse, String inventoryResponse) {
        logger.debug("Service A: Formatting response");
        return String.format("Service A -> [B: %s] [C: %s]", orderResponse, inventoryResponse);
    }
    
    private void sendAsyncNotification(String orderId, String eventType) {
        logger.info("Service A: Sending async notification to Service D for order {}", orderId);
        try {
            NotificationRequest notification = new NotificationRequest();
            notification.setOrderId(orderId);
            notification.setType(eventType);
            notification.setChannel("EMAIL");
            notification.setCallbackRequired(true);
            
            rabbitTemplate.convertAndSend("notification-queue", notification);
            logger.info("Service A: Notification sent to queue");
        } catch (Exception e) {
            logger.warn("Service A: Failed to send async notification: {}", e.getMessage());
        }
    }
    
    @GetMapping("/health")
    public String health() {
        return "Service A is running";
    }
    
    @GetMapping("/process/{orderId}")
    public String processFromServiceB(@PathVariable String orderId) {
        logger.info("Service A: Received callback from Service B for order {}", orderId);
        
        processCallback(orderId);
        
        updateOrderStatus(orderId);
        
        logger.info("Service A: Callback processed for order {}", orderId);
        return "Service A: Processed callback for order " + orderId;
    }
    
    @GetMapping("/verify/{orderId}")
    public String verifyFromServiceC(@PathVariable String orderId) {
        logger.info("Service A: Received verification from Service C for order {}", orderId);
        
        verifyOrder(orderId);
        
        logger.info("Service A: Verification completed for order {}", orderId);
        return "Service A: Verified order " + orderId;
    }
    
    @GetMapping("/api/callback/{orderId}")
    public String handleCallback(@PathVariable String orderId) {
        logger.info("Service A: Received callback from Service D for order {}", orderId);
        
        processCallback(orderId);
        
        updateOrderStatus(orderId);
        
        logger.info("Service A: Callback processed for order {}", orderId);
        return "Service A: Callback received for order " + orderId;
    }
    
    private void processCallback(String orderId) {
        logger.debug("Service A: Processing callback for order {}", orderId);
        try {
            Thread.sleep(30);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    private void updateOrderStatus(String orderId) {
        logger.debug("Service A: Updating order status for {}", orderId);
        try {
            Thread.sleep(20);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    private void verifyOrder(String orderId) {
        logger.debug("Service A: Verifying order {}", orderId);
        try {
            Thread.sleep(25);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
