# Zipkin Distributed Tracing - Fix Summary

## Problem
Zipkin was not receiving any traces from the microservices despite having basic tracing dependencies configured.

## Root Causes Identified

### 1. Missing Zipkin Sender Dependency
The critical `zipkin-sender-urlconnection` dependency was missing from all service POMs. This dependency is required to actually send span data to the Zipkin server.

### 2. RabbitMQ Observation Not Enabled
RabbitMQ templates and listeners were not configured to enable observation/tracing for async messaging.

### 3. Incorrect Configuration
- RabbitMQ host was set to `rabbitmq` (Docker name) but services were running locally
- Zipkin endpoint was set to `http://zipkin:9411` but Zipkin was running on localhost

## Changes Made

### 1. Updated POM Dependencies (All Services)
Added to all service POMs (service-a, service-b, service-c, service-d):
```xml
<dependency>
    <groupId>io.zipkin.reporter2</groupId>
    <artifactId>zipkin-sender-urlconnection</artifactId>
</dependency>
```

### 2. Updated Application Properties (All Services)
Changed configuration to work with local deployment:
```properties
# Changed from rabbitmq to localhost
spring.rabbitmq.host=localhost

# Changed from zipkin to localhost  
management.zipkin.tracing.endpoint=http://localhost:9411/api/v2/spans

# Added actuator endpoints exposure
management.endpoints.web.exposure.include=health,info,metrics,prometheus,httpexchanges
management.endpoint.health.show-details=always

# Added debug logging for troubleshooting
logging.level.zipkin2=DEBUG
logging.level.brave=DEBUG
```

### 3. Enabled RabbitMQ Observation

#### Service A Application
Added RabbitTemplate bean with observation enabled:
```java
@Bean
public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, 
                                      ObservationRegistry observationRegistry) {
    RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
    rabbitTemplate.setMessageConverter(jsonMessageConverter());
    rabbitTemplate.setObservationEnabled(true);
    return rabbitTemplate;
}
```

#### Services B, C, D RabbitMQ Config
Updated RabbitMQConfig to enable observation:
```java
@Bean
public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                      ObservationRegistry observationRegistry) {
    RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
    rabbitTemplate.setMessageConverter(messageConverter());
    rabbitTemplate.setObservationEnabled(true);
    return rabbitTemplate;
}
```

#### Service D (Listener)
Added listener container factory with observation:
```java
@Bean
public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
        ConnectionFactory connectionFactory,
        ObservationRegistry observationRegistry) {
    SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
    factory.setConnectionFactory(connectionFactory);
    factory.setMessageConverter(messageConverter());
    factory.setObservationEnabled(true);
    return factory;
}
```

## Verified Trace Patterns

### ✅ Synchronous HTTP Calls
1. **Service A → Service B** - Traced successfully
2. **Service A → Service C** - Traced successfully
3. **Service B → Service C** - Traced successfully
4. **Service B → Service A** (callback) - Traced successfully
5. **Service C → Service A** (verification) - Traced successfully

### ✅ Asynchronous RabbitMQ Calls
1. **Service A → Service D** (via RabbitMQ) - Traced successfully
2. **Service B → Service D** (via RabbitMQ) - Traced successfully
3. **Service C → Service D** (via RabbitMQ) - Traced successfully
4. **Service D → Service A** (callback after async processing) - Traced successfully

## Current Trace Statistics
From the latest check of Zipkin:
- Total traces captured: 16+
- Services being traced: service-a, service-b, service-c, service-d
- Trace patterns include both sync HTTP and async RabbitMQ messaging
- All service interactions properly propagate trace context

## How to Test

1. **Start all services** (already running):
   - Service A: http://localhost:8080
   - Service B: http://localhost:8081
   - Service C: http://localhost:8082
   - Service D: http://localhost:8083
   - Zipkin: http://localhost:9411
   - RabbitMQ: http://localhost:15672

2. **Make a test call**:
   ```bash
   curl http://localhost:8080/api/order/test123
   ```

3. **View traces in Zipkin**:
   - Web UI: http://localhost:9411
   - API: http://localhost:9411/api/v2/traces?limit=10

4. **Check for specific trace**:
   ```bash
   curl -s 'http://localhost:9411/api/v2/traces?limit=10' | jq '.[0]'
   ```

## Dependencies Required

All services need these dependencies in their `pom.xml`:
```xml
<!-- Spring Boot Actuator -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>

<!-- AOP for @Observed annotations -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aop</artifactId>
</dependency>

<!-- Micrometer Tracing Bridge -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-brave</artifactId>
</dependency>

<!-- Zipkin Reporter -->
<dependency>
    <groupId>io.zipkin.reporter2</groupId>
    <artifactId>zipkin-reporter-brave</artifactId>
</dependency>

<!-- Zipkin Sender (CRITICAL - was missing!) -->
<dependency>
    <groupId>io.zipkin.reporter2</groupId>
    <artifactId>zipkin-sender-urlconnection</artifactId>
</dependency>
```

## Result
✅ **All tracing requirements are now working correctly!**

The distributed tracing system now properly captures:
- Synchronous REST API calls between services
- Asynchronous RabbitMQ message publishing and consumption
- Complete trace context propagation across all services
- Proper span relationships showing parent-child hierarchies
