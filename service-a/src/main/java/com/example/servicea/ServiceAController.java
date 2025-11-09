package com.example.servicea;

import io.micrometer.observation.annotation.Observed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    
    @GetMapping("/api/order/{orderId}")
    public String getOrder(@PathVariable String orderId) {
        logger.info("Service A: Received request for order {}", orderId);
        
        validateRequest(orderId);
        
        Map<String, Object> orderMetadata = prepareOrderMetadata(orderId);
        logger.info("Service A: Order metadata prepared: {}", orderMetadata);
        
        String orderResponse = restTemplate.getForObject(
            "http://localhost:8081/order/" + orderId, 
            String.class
        );
        
        String finalResponse = formatResponse(orderResponse);
        
        logger.info("Service A: Completed request for order {}", orderId);
        return finalResponse;
    }
    
    @Observed(name = "service-a.validate-request")
    private void validateRequest(String orderId) {
        logger.debug("Service A: Validating order ID {}", orderId);
        if (orderId == null || orderId.isEmpty()) {
            throw new IllegalArgumentException("Order ID cannot be empty");
        }
    }
    
    @Observed(name = "service-a.prepare-metadata")
    private Map<String, Object> prepareOrderMetadata(String orderId) {
        logger.debug("Service A: Preparing metadata for order {}", orderId);
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("orderId", orderId);
        metadata.put("timestamp", System.currentTimeMillis());
        metadata.put("source", "service-a");
        return metadata;
    }
    
    @Observed(name = "service-a.format-response")
    private String formatResponse(String orderResponse) {
        logger.debug("Service A: Formatting response");
        return "Service A -> " + orderResponse;
    }
    
    @GetMapping("/health")
    public String health() {
        return "Service A is running";
    }
}
