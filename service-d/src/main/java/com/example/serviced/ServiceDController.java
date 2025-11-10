package com.example.serviced;

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
public class ServiceDController {
    
    private static final Logger logger = LoggerFactory.getLogger(ServiceDController.class);
    
    @Autowired
    private RestTemplate restTemplate;
    
    @Autowired
    private NotificationService notificationService;
    
    @PostMapping("/notify")
    public String sendNotification(@RequestBody NotificationRequest request) {
        logger.info("Service D: Received notification request for order {}", request.getOrderId());
        
        validateNotificationRequest(request);
        
        String templateContent = loadNotificationTemplate(request.getType());
        
        String personalizedMessage = personalizeMessage(templateContent, request);
        
        boolean sent = sendToChannel(request.getChannel(), personalizedMessage);
        
        if (sent) {
            auditNotification(request);
            
            if (request.isCallbackRequired()) {
                triggerCallback(request.getOrderId());
            }
        }
        
        logger.info("Service D: Notification sent successfully for order {}", request.getOrderId());
        return "Notification sent: " + personalizedMessage;
    }
    
    @GetMapping("/notifications/{orderId}")
    public String getNotificationStatus(@PathVariable String orderId) {
        logger.info("Service D: Checking notification status for order {}", orderId);
        
        String status = checkNotificationHistory(orderId);
        
        return "Service D (Notification): Status for order " + orderId + " - " + status;
    }
    
    @GetMapping("/health")
    public String health() {
        return "Service D is running";
    }
    
    @Observed(name = "service-d.validate-request")
    private void validateNotificationRequest(NotificationRequest request) {
        logger.debug("Service D: Validating notification request");
        try {
            Thread.sleep(20);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (request.getOrderId() == null || request.getOrderId().isEmpty()) {
            throw new IllegalArgumentException("Order ID is required");
        }
    }
    
    @Observed(name = "service-d.load-template")
    private String loadNotificationTemplate(String type) {
        logger.debug("Service D: Loading notification template for type {}", type);
        try {
            Thread.sleep(30);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return "Dear Customer, your order {{orderId}} is {{status}}";
    }
    
    @Observed(name = "service-d.personalize-message")
    private String personalizeMessage(String template, NotificationRequest request) {
        logger.debug("Service D: Personalizing message for order {}", request.getOrderId());
        try {
            Thread.sleep(25);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return template.replace("{{orderId}}", request.getOrderId())
                       .replace("{{status}}", request.getStatus());
    }
    
    @Observed(name = "service-d.send-to-channel")
    private boolean sendToChannel(String channel, String message) {
        logger.debug("Service D: Sending notification via channel {}", channel);
        try {
            Thread.sleep(40);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return true;
    }
    
    @Observed(name = "service-d.audit-notification")
    private void auditNotification(NotificationRequest request) {
        logger.debug("Service D: Auditing notification for order {}", request.getOrderId());
        try {
            Thread.sleep(15);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    @Observed(name = "service-d.trigger-callback")
    private void triggerCallback(String orderId) {
        logger.debug("Service D: Triggering callback to Service A for order {}", orderId);
        try {
            String response = restTemplate.getForObject(
                "http://localhost:8080/api/callback/" + orderId, 
                String.class
            );
            logger.info("Service D: Callback response: {}", response);
        } catch (Exception e) {
            logger.warn("Service D: Failed to trigger callback: {}", e.getMessage());
        }
    }
    
    @Observed(name = "service-d.check-history")
    private String checkNotificationHistory(String orderId) {
        logger.debug("Service D: Checking notification history for order {}", orderId);
        try {
            Thread.sleep(35);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return "DELIVERED";
    }
}
