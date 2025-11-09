package com.example.servicea;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

@RestController
public class ServiceAController {
    
    private static final Logger logger = LoggerFactory.getLogger(ServiceAController.class);
    
    @Autowired
    private RestTemplate restTemplate;
    
    @GetMapping("/api/order/{orderId}")
    public String getOrder(@PathVariable String orderId) {
        logger.info("Service A: Received request for order {}", orderId);
        
        String orderResponse = restTemplate.getForObject(
            "http://localhost:8081/order/" + orderId, 
            String.class
        );
        
        logger.info("Service A: Completed request for order {}", orderId);
        return "Service A -> " + orderResponse;
    }
    
    @GetMapping("/health")
    public String health() {
        return "Service A is running";
    }
}
