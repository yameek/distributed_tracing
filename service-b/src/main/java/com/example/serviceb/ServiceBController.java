package com.example.serviceb;

import io.micrometer.observation.annotation.Observed;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class ServiceBController {
    
    private static final Logger logger = LoggerFactory.getLogger(ServiceBController.class);
    
    @Autowired
    private RestTemplate restTemplate;
    
    @Autowired
    private RabbitTemplate rabbitTemplate;
    
    @Autowired
    private Tracer tracer;
    
    @GetMapping("/order/{orderId}")
    public String processOrder(@PathVariable String orderId) {
        logger.info("Service B: Processing order {}", orderId);
        
        // Simulate failure scenarios for demonstration
        if (orderId.equals("timeout-order")) {
            logger.error("Service B: Timeout processing order {}", orderId);
            tagError("timeout", "Order processing timeout exceeded");
            throw new ResponseStatusException(
                HttpStatus.REQUEST_TIMEOUT, 
                "Order processing timeout exceeded"
            );
        }
        
        if (orderId.equals("invalid-order")) {
            logger.error("Service B: Invalid order format {}", orderId);
            tagError("validation", "Invalid order ID format");
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, 
                "Invalid order ID format"
            );
        }
        
        if (orderId.equals("not-found-order")) {
            logger.error("Service B: Order not found {}", orderId);
            tagError("not_found", "Order not found in database");
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND, 
                "Order not found in database"
            );
        }
        
        if (orderId.equals("db-error-order")) {
            logger.error("Service B: Database error for order {}", orderId);
            tagError("database", "Database connection failed");
            throw new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR, 
                "Database connection failed"
            );
        }
        
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
    
    private void tagError(String errorType, String errorMessage) {
        if (tracer.currentSpan() != null) {
            tracer.currentSpan().tag("error", "true");
            tracer.currentSpan().tag("error.type", errorType);
            tracer.currentSpan().tag("error.message", errorMessage);
        }
    }
    
    @GetMapping("/health")
    public String health() {
        return "Service B is running";
    }
    
    @Observed(name = "service-b.check-eligibility")
    private void checkOrderEligibility(String orderId) {
        logger.debug("Service B: Checking eligibility for order {}", orderId);
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    @Observed(name = "service-b.calculate-amount")
    private double calculateOrderAmount(String orderId) {
        logger.debug("Service B: Calculating amount for order {}", orderId);
        try {
            Thread.sleep(30);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return 99.99 + orderId.hashCode() % 100;
    }
    
    @Observed(name = "service-b.apply-business-rules")
    private void applyBusinessRules(String orderId, double amount) {
        logger.debug("Service B: Applying business rules for order {} with amount ${}", orderId, amount);
        try {
            Thread.sleep(20);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    @Observed(name = "service-b.send-async-notification")
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
