package com.example.serviced;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class NotificationService {
    
    private static final Logger logger = LoggerFactory.getLogger(NotificationService.class);
    
    @Autowired
    private RabbitTemplate rabbitTemplate;
    
    @Autowired
    private RestTemplate restTemplate;
    
    public void sendAsyncNotification(String orderId, String status) {
        logger.info("Service D: Sending async notification to queue for order {}", orderId);
        
        NotificationRequest request = new NotificationRequest(orderId, "ORDER_UPDATE", status, "EMAIL", true);
        rabbitTemplate.convertAndSend("notification-exchange", "notification.order", request);
        
        logger.info("Service D: Notification message sent to queue");
    }
    
    @RabbitListener(queues = "notification-queue")
    public void processNotification(NotificationRequest request) {
        logger.info("Service D: Processing async notification from queue for order {}", request.getOrderId());
        
        prepareNotificationData(request);
        
        enrichNotificationWithUserData(request);
        
        formatNotificationContent(request);
        
        deliverNotification(request);
        
        if (request.isCallbackRequired()) {
            sendCallbackToServiceA(request.getOrderId());
        }
        
        logger.info("Service D: Async notification processed for order {}", request.getOrderId());
    }
    
    private void prepareNotificationData(NotificationRequest request) {
        logger.debug("Service D: Preparing notification data");
        try {
            Thread.sleep(30);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    private void enrichNotificationWithUserData(NotificationRequest request) {
        logger.debug("Service D: Enriching with user data");
        try {
            Thread.sleep(40);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    private void formatNotificationContent(NotificationRequest request) {
        logger.debug("Service D: Formatting notification content");
        try {
            Thread.sleep(25);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    private void deliverNotification(NotificationRequest request) {
        logger.debug("Service D: Delivering notification via {}", request.getChannel());
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    private void sendCallbackToServiceA(String orderId) {
        logger.info("Service D: Sending callback to Service A for order {}", orderId);
        try {
            String response = restTemplate.getForObject(
                "http://localhost:8080/api/callback/" + orderId, 
                String.class
            );
            logger.info("Service D: Callback response from Service A: {}", response);
        } catch (Exception e) {
            logger.warn("Service D: Callback to Service A failed: {}", e.getMessage());
        }
    }
}
