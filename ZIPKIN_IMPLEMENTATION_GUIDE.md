# Complete Guide: Implementing Zipkin Distributed Tracing in Spring Boot Microservices

## Table of Contents
1. [Overview](#overview)
2. [Prerequisites](#prerequisites)
3. [Architecture](#architecture)
4. [Step-by-Step Implementation](#step-by-step-implementation)
5. [Configuration Details](#configuration-details)
6. [Testing and Verification](#testing-and-verification)
7. [Common Issues and Solutions](#common-issues-and-solutions)
8. [Advanced Topics](#advanced-topics)

---

## Overview

This guide walks you through implementing Zipkin distributed tracing in Spring Boot microservices from scratch. You'll learn how to:
- Add tracing dependencies
- Configure services for distributed tracing
- Set up Zipkin backend
- Verify trace propagation across services
- View and analyze traces

**What is Distributed Tracing?**
Distributed tracing tracks requests as they flow through multiple microservices, helping you:
- Debug latency issues
- Understand service dependencies
- Monitor system performance
- Identify bottlenecks

---

## Prerequisites

- **Java 17+**
- **Maven 3.6+**
- **Spring Boot 3.2.0+**
- **Docker** (for running Zipkin)
- Basic understanding of Spring Boot and REST APIs

---

## Architecture

```
Synchronous HTTP Calls:
  A → B, A → C, B → C, B → A, C → A

Asynchronous RabbitMQ:
  A → D, B → D, C → D

Visual Diagram:
┌──────────────────────────────────────┐
│         Service A (8080)             │
│          API Gateway                 │
└──┬──────┬────▲────────▲──────────────┘
   │      │    │        │
 sync   sync  sync    sync
   │      │    │        │
   ▼      ▼    │        │
┌──────┐ ┌────────┐    │
│  B   │─│   C    │────┘
│ 8081 │ │  8082  │
└──┬───┘ └───┬────┘
   │         │
  async    async
   │         │
   └────┬────┘
        ▼
   ┌─────────┐
   │RabbitMQ │
   └────┬────┘
        │
      async
        ▼
   ┌─────────┐
   │    D    │
   │  8083   │
   └─────────┘
```

All services report traces to Zipkin on port 9411.

**Trace Flow:**
1. Request comes to Service A
2. Service A calls Service B and C (sync - trace context propagated via HTTP headers)
3. Service B calls Service C and A (sync - trace context propagated)
4. Service C calls Service A (sync callback - trace context propagated)
5. Services A, B, C send async messages to RabbitMQ (trace context in message headers)
6. Service D processes messages from queue (trace context preserved)
7. All services send span data to Zipkin

---
5. Zipkin aggregates and displays the complete trace

---

## Step-by-Step Implementation

### Step 1: Add Dependencies to Each Service

Add these dependencies to `pom.xml` for **each microservice**:

```xml
<dependencies>
    <!-- Core Spring Boot Web -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    
    <!-- Spring Boot Actuator (enables observability) -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>
    
    <!-- AOP for @Observed annotation support -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-aop</artifactId>
    </dependency>
    
    <!-- Micrometer Tracing with Brave (Zipkin-compatible) -->
    <dependency>
        <groupId>io.micrometer</groupId>
        <artifactId>micrometer-tracing-bridge-brave</artifactId>
    </dependency>
    
    <!-- Zipkin Reporter (sends traces to Zipkin) -->
    <dependency>
        <groupId>io.zipkin.reporter2</groupId>
        <artifactId>zipkin-reporter-brave</artifactId>
    </dependency>
    
    <!-- RabbitMQ for async messaging (Services A, B, C, D) -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-amqp</artifactId>
    </dependency>
</dependencies>
```

**Dependency Breakdown:**
- **spring-boot-starter-actuator**: Enables observability features
- **spring-boot-starter-aop**: Required for annotation-based tracing
- **micrometer-tracing-bridge-brave**: Tracing implementation (Brave is Zipkin-compatible)
- **zipkin-reporter-brave**: Sends trace data to Zipkin server
- **spring-boot-starter-amqp**: RabbitMQ support with automatic trace propagation

---

### Step 2: Configure Application Properties

Create or update `src/main/resources/application.properties` for **each service**:

#### Service A (application.properties)
```properties
spring.application.name=service-a
server.port=8080

# Enable 100% trace sampling (trace all requests)
management.tracing.sampling.probability=1.0

# Zipkin endpoint
management.zipkin.tracing.endpoint=http://localhost:9411/api/v2/spans

# Include trace/span IDs in logs
logging.pattern.level=%5p [${spring.application.name:},%X{traceId:-},%X{spanId:-}]
```

#### Service B (application.properties)
```properties
spring.application.name=service-b
server.port=8081

management.tracing.sampling.probability=1.0
management.zipkin.tracing.endpoint=http://localhost:9411/api/v2/spans
logging.pattern.level=%5p [${spring.application.name:},%X{traceId:-},%X{spanId:-}]
```

#### Service D (application.properties)
```properties
spring.application.name=service-d
server.port=8083

management.tracing.sampling.probability=1.0
management.zipkin.tracing.endpoint=http://localhost:9411/api/v2/spans
logging.pattern.level=%5p [${spring.application.name:},%X{traceId:-},%X{spanId:-}]

# RabbitMQ Configuration
spring.rabbitmq.host=localhost
spring.rabbitmq.port=5672
spring.rabbitmq.username=guest
spring.rabbitmq.password=guest
```

**Configuration Explained:**
- **spring.application.name**: Service identifier in traces
- **management.tracing.sampling.probability**: 1.0 = 100% (trace all requests)
  - For production, use 0.1 (10%) to reduce overhead
- **management.zipkin.tracing.endpoint**: Where to send trace data
- **logging.pattern**: Adds trace/span IDs to log output
- **spring.rabbitmq.***: RabbitMQ connection settings (trace context auto-propagated)

---

### Step 3: Configure RestTemplate with RestTemplateBuilder

**⚠️ CRITICAL:** This is the most important step for trace propagation!

#### Service A Application Class

```java
package com.example.servicea;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication
public class ServiceAApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(ServiceAApplication.class, args);
    }
    
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        // ✅ USE RestTemplateBuilder - it auto-configures tracing!
        return builder.build();
    }
}
```

**❌ WRONG WAY (No trace propagation):**
```java
@Bean
public RestTemplate restTemplate() {
    return new RestTemplate();  // Missing tracing interceptors!
}
```

**✅ CORRECT WAY (Trace propagation enabled):**
```java
@Bean
public RestTemplate restTemplate(RestTemplateBuilder builder) {
    return builder.build();  // Automatically adds tracing interceptors
}
```

**Apply this same pattern to Service B and any other service that makes HTTP calls.**

---

### Step 4: Create Controllers with Service Calls

#### Service A Controller

```java
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
        
        // Call Service B - trace context automatically propagated
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
```

#### Service B Controller

```java
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
        
        // Call Service C - trace context automatically propagated
        String inventoryResponse = restTemplate.getForObject(
            "http://localhost:8082/inventory/" + orderId, 
            String.class
        );
        
        logger.info("Service B: Order {} processed successfully", orderId);
        return "Service B (Order) -> " + inventoryResponse;
    }
    
    @GetMapping("/health")
    public String health() {
        return "Service B is running";
    }
}
```

#### Service C Controller

```java
package com.example.servicec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ServiceCController {
    
    private static final Logger logger = LoggerFactory.getLogger(ServiceCController.class);
    
    @GetMapping("/inventory/{orderId}")
    public String checkInventory(@PathVariable String orderId) {
        logger.info("Service C: Checking inventory for order {}", orderId);
        
        // Simulate some work
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        logger.info("Service C: Inventory check completed for order {}", orderId);
        return "Service C (Inventory): Stock available for order " + orderId;
    }
    
    @GetMapping("/health")
    public String health() {
        return "Service C is running";
    }
}
```

---

### Step 5: Start Zipkin Server

#### Option 1: Docker (Recommended)

```bash
docker run -d -p 9411:9411 --name zipkin openzipkin/zipkin
```

#### Option 2: Docker Compose

Create `docker-compose.yml`:

```yaml
version: '3.8'

services:
  zipkin:
    image: openzipkin/zipkin:latest
    container_name: zipkin
    ports:
      - "9411:9411"
```

Run:
```bash
docker-compose up -d
```

#### Option 3: Download JAR

```bash
curl -sSL https://zipkin.io/quickstart.sh | bash -s
java -jar zipkin.jar
```

**Verify Zipkin is running:**
```bash
curl http://localhost:9411/health
```

Expected output:
```json
{"status":"UP"}
```

---

### Step 6: Build and Run Services

#### Build All Services

```bash
# Service C
cd service-c
mvn clean package -DskipTests

# Service B
cd ../service-b
mvn clean package -DskipTests

# Service A
cd ../service-a
mvn clean package -DskipTests
```

#### Run Services (in order)

**Terminal 1 - Service C:**
```bash
cd service-c
mvn spring-boot:run
```

**Terminal 2 - Service B:**
```bash
cd service-b
mvn spring-boot:run
```

**Terminal 3 - Service A:**
```bash
cd service-a
mvn spring-boot:run
```

**Verify all services are running:**
```bash
curl http://localhost:8080/health  # Service A
curl http://localhost:8081/health  # Service B
curl http://localhost:8082/health  # Service C
```

---

## Testing and Verification

### Step 1: Generate Test Requests

```bash
# Send a test request through the entire chain
curl http://localhost:8080/api/order/12345

# Expected output:
# Service A -> Service B (Order) -> Service C (Inventory): Stock available for order 12345
```

Generate multiple requests:
```bash
for i in {1..10}; do
  curl http://localhost:8080/api/order/ORDER-$i
  sleep 1
done
```

### Step 2: View Traces in Zipkin UI

Open your browser:
```
http://localhost:9411
```

**Steps to view traces:**
1. Click the blue **"RUN QUERY"** button
2. You'll see a list of traces
3. Click on any trace to see details

**What you'll see:**
- **Timeline view** showing request flow across services
- **Span details** with duration for each service
- **Service dependencies** showing A → B → C

### Step 3: View Service Dependencies Graph

```
http://localhost:9411/zipkin/dependency
```

This shows a visual graph of service-to-service communication.

### Step 4: Check Trace Propagation via API

```bash
# Get latest traces
curl "http://localhost:9411/api/v2/traces?limit=1" | python3 -m json.tool

# Search by service name
curl "http://localhost:9411/api/v2/traces?serviceName=service-a&limit=10"
```

### Step 5: Verify Logs with Trace IDs

Check your service logs - you should see trace IDs:

```
INFO [service-a,64f1e7c8f8e9a1b2,64f1e7c8f8e9a1b2] Service A: Received request...
INFO [service-b,64f1e7c8f8e9a1b2,a1b2c3d4e5f6g7h8] Service B: Processing order...
INFO [service-c,64f1e7c8f8e9a1b2,h8g7f6e5d4c3b2a1] Service C: Checking inventory...
```

Note: Same **trace ID** across all services!

---

## Configuration Details

### Sampling Configuration

Control what percentage of requests are traced:

```properties
# Trace 100% of requests (development)
management.tracing.sampling.probability=1.0

# Trace 10% of requests (production)
management.tracing.sampling.probability=0.1

# Trace 1% of requests (high-traffic production)
management.tracing.sampling.probability=0.01
```

### Custom Zipkin Endpoint

If Zipkin is running on a different host:

```properties
# Remote Zipkin server
management.zipkin.tracing.endpoint=http://zipkin-server:9411/api/v2/spans

# Kubernetes service
management.zipkin.tracing.endpoint=http://zipkin.monitoring.svc.cluster.local:9411/api/v2/spans
```

### Docker Network Configuration

If using Docker Compose, update endpoints:

```yaml
services:
  service-a:
    environment:
      - MANAGEMENT_ZIPKIN_TRACING_ENDPOINT=http://zipkin:9411/api/v2/spans
```

---

## Common Issues and Solutions

### Issue 1: Traces Not Appearing in Zipkin

**Symptoms:** 
- Services running fine
- No traces in Zipkin UI

**Solutions:**

1. **Check Zipkin connectivity:**
```bash
# From service host
curl http://localhost:9411/health
```

2. **Verify sampling is enabled:**
```properties
management.tracing.sampling.probability=1.0
```

3. **Check service logs for errors:**
```bash
# Look for Zipkin connection errors
grep -i zipkin /tmp/service-a.log
```

4. **Verify RestTemplateBuilder is used:**
```java
// ✅ Correct
@Bean
public RestTemplate restTemplate(RestTemplateBuilder builder) {
    return builder.build();
}

// ❌ Wrong
@Bean
public RestTemplate restTemplate() {
    return new RestTemplate();
}
```

---

### Issue 2: Trace Not Propagating Across Services

**Symptoms:**
- Only see one span per service
- Services appear as separate traces

**Solution:**

**Must use RestTemplateBuilder!** This is the #1 cause of missing trace propagation.

```java
import org.springframework.boot.web.client.RestTemplateBuilder;

@Bean
public RestTemplate restTemplate(RestTemplateBuilder builder) {
    return builder.build();
}
```

---

### Issue 3: Missing Service Dependencies Graph

**Symptoms:**
- Traces show up but dependency graph is empty

**Root Cause:**
- Traces are not properly linked across services

**Solution:**
1. Ensure all services use RestTemplateBuilder
2. Generate several requests to populate the graph
3. Wait a few seconds for Zipkin to aggregate data

---

### Issue 4: High Memory/CPU Usage

**Symptoms:**
- Services consuming too much resources

**Solutions:**

1. **Reduce sampling rate:**
```properties
management.tracing.sampling.probability=0.1  # 10% instead of 100%
```

2. **Configure async reporting:**
```properties
# Send spans asynchronously
management.zipkin.tracing.timeout=1s
```

---

## Advanced Topics

### Adding Custom Spans

Use `@Observed` annotation to create custom spans:

```java
import io.micrometer.observation.annotation.Observed;

@Observed(name = "database.query")
private List<Order> queryDatabase(String orderId) {
    // Your database logic
    return orderRepository.findByOrderId(orderId);
}
```

**Enable AOP support:**

1. Add dependency:
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aop</artifactId>
</dependency>
```

2. Create configuration:
```java
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.aop.ObservedAspect;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ObservationConfig {
    
    @Bean
    public ObservedAspect observedAspect(ObservationRegistry observationRegistry) {
        return new ObservedAspect(observationRegistry);
    }
}
```

---

### Using WebClient Instead of RestTemplate

For reactive applications using WebClient:

```java
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.client.WebClient;

@Bean
public WebClient webClient(WebClient.Builder builder) {
    return builder.build();  // Auto-configured with tracing
}
```

Usage:
```java
@Autowired
private WebClient webClient;

public Mono<String> callService() {
    return webClient.get()
        .uri("http://localhost:8081/order/123")
        .retrieve()
        .bodyToMono(String.class);
}
```

---

### Persistent Storage for Traces

By default, Zipkin stores traces in memory. For production, use persistent storage:

#### Using MySQL

```bash
docker run -d -p 9411:9411 \
  -e STORAGE_TYPE=mysql \
  -e MYSQL_HOST=mysql-host \
  -e MYSQL_USER=zipkin \
  -e MYSQL_PASS=zipkin \
  openzipkin/zipkin
```

#### Using Elasticsearch

```bash
docker run -d -p 9411:9411 \
  -e STORAGE_TYPE=elasticsearch \
  -e ES_HOSTS=http://elasticsearch:9200 \
  openzipkin/zipkin
```

---

### Integration with Prometheus

Export trace metrics to Prometheus:

```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

Configuration:
```properties
management.endpoints.web.exposure.include=health,prometheus
management.metrics.export.prometheus.enabled=true
```

---

### Custom Tags and Baggage

Add custom information to spans:

```java
import io.micrometer.tracing.Tracer;
import org.springframework.beans.factory.annotation.Autowired;

@RestController
public class OrderController {
    
    @Autowired
    private Tracer tracer;
    
    @GetMapping("/order/{id}")
    public String getOrder(@PathVariable String id) {
        // Add custom tag to current span
        tracer.currentSpan().tag("order.id", id);
        tracer.currentSpan().tag("user.type", "premium");
        
        // Your business logic
        return processOrder(id);
    }
}
```

---

## Performance Considerations

### Production Settings

```properties
# Reduce sampling to 10%
management.tracing.sampling.probability=0.1

# Set timeout for Zipkin reporting
management.zipkin.tracing.timeout=1s

# Async reporting (non-blocking)
management.zipkin.tracing.api-version=2

# Batch size for spans
management.zipkin.tracing.sender.type=web
```

### Resource Impact

| Configuration | Memory Overhead | CPU Overhead | Network Traffic |
|---------------|----------------|--------------|-----------------|
| No Tracing | 0 MB | 0% | 0 KB/req |
| 100% Sampling | 5-10 MB | <1% | 2-5 KB/req |
| 10% Sampling | 1-2 MB | <0.1% | 0.2-0.5 KB/req |

---

## Summary Checklist

- [ ] Add Micrometer and Zipkin dependencies to all services
- [ ] Configure `application.properties` with Zipkin endpoint
- [ ] Use `RestTemplateBuilder` for RestTemplate bean (CRITICAL!)
- [ ] Start Zipkin server on port 9411
- [ ] Build and run all microservices
- [ ] Generate test requests
- [ ] Verify traces in Zipkin UI at http://localhost:9411
- [ ] Check service dependency graph
- [ ] Verify trace IDs in logs
- [ ] Adjust sampling for production (0.1 or 0.01)

---

## Complete Working Example

All code from this guide is available in a complete working example:

**Repository Structure:**
```
distributed_tracing/
├── service-a/
│   ├── src/main/java/com/example/servicea/
│   │   ├── ServiceAApplication.java
│   │   └── ServiceAController.java
│   ├── src/main/resources/
│   │   └── application.properties
│   └── pom.xml
├── service-b/
│   ├── src/main/java/com/example/serviceb/
│   │   ├── ServiceBApplication.java
│   │   └── ServiceBController.java
│   ├── src/main/resources/
│   │   └── application.properties
│   └── pom.xml
├── service-c/
│   ├── src/main/java/com/example/servicec/
│   │   ├── ServiceCApplication.java
│   │   └── ServiceCController.java
│   ├── src/main/resources/
│   │   └── application.properties
│   └── pom.xml
└── docker-compose.yml
```

---

## Next Steps

1. **Explore Zipkin UI features:**
   - Search traces by service name
   - Filter by duration
   - Analyze trace dependencies

2. **Learn other tracing systems:**
   - Jaeger (CNCF project)
   - OpenTelemetry (vendor-neutral standard)
   - Grafana Tempo (with Grafana integration)

3. **Integrate with monitoring:**
   - Combine with Prometheus metrics
   - Set up alerts on high latency
   - Create dashboards in Grafana

4. **Production deployment:**
   - Use persistent storage (Elasticsearch/MySQL)
   - Set up proper sampling rates
   - Configure retention policies
   - Secure Zipkin endpoint

---

## Additional Resources

- **Zipkin Documentation:** https://zipkin.io/
- **Micrometer Tracing:** https://micrometer.io/docs/tracing
- **Spring Boot Observability:** https://spring.io/blog/2022/10/12/observability-with-spring-boot-3
- **Brave (Zipkin instrumentation):** https://github.com/openzipkin/brave

---

**Author:** Generated with assistance  
**Last Updated:** 2025-11-09  
**Version:** 1.0
