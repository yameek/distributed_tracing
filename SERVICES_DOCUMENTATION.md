# Distributed Tracing Microservices - Service Documentation

**Date:** November 10, 2025  
**Project:** Distributed Tracing with Spring Boot  
**Current State:** Baseline (No Tracing Implementation)

---

## Table of Contents

1. [Overview](#overview)
2. [Architecture](#architecture)
3. [Service A - API Gateway](#service-a--api-gateway)
4. [Service B - Order Service](#service-b--order-service)
5. [Service C - Inventory Service](#service-c--inventory-service)
6. [Inter-Service Communication](#inter-service-communication)
7. [Request Flow & Timing](#request-flow--timing)
8. [Current Issues](#current-issues)
9. [Technology Stack](#technology-stack)
10. [How to Run](#how-to-run)
11. [API Endpoints Reference](#api-endpoints-reference)

---

## Overview

This is a **three-tier microservices architecture** demonstrating a complete order processing pipeline. Each service handles a specific business domain and communicates with the next service in the chain.

### Current Status: ⚠️ NO TRACING IMPLEMENTATION

The services are fully functional but lack distributed tracing capabilities. This documentation outlines the current baseline state and serves as a reference for implementing Zipkin tracing.

### Key Characteristics

- **Language:** Java 17
- **Framework:** Spring Boot 3.2.0
- **Build Tool:** Maven 3.6+
- **Architecture Pattern:** Microservices with synchronous HTTP communication
- **Database:** None (in-memory simulated operations)
- **Status:** Production-ready baseline without observability

---

## Architecture

### System Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                     REQUEST FLOW                            │
└─────────────────────────────────────────────────────────────┘

Client/Browser
    │
    │ HTTP GET /api/order/{orderId}
    ▼
┌──────────────────────────────────────────────────────────────┐
│ SERVICE A - API Gateway (Port 8080)                         │
│                                                               │
│  - Entry point for all requests                              │
│  - Validates order ID                                        │
│  - Prepares order metadata                                   │
│  - Calls Service B                                           │
│  - Formats final response                                    │
│                                                               │
└──────────────────────────────────────────────────────────────┘
    │
    │ HTTP GET http://localhost:8081/order/{orderId}
    ▼
┌──────────────────────────────────────────────────────────────┐
│ SERVICE B - Order Service (Port 8081)                       │
│                                                               │
│  - Processes order requests                                  │
│  - Checks order eligibility (50ms)                           │
│  - Calculates order amounts (30ms)                           │
│  - Applies business rules (20ms)                             │
│  - Calls Service C                                           │
│  - Returns order confirmation                                │
│                                                               │
└──────────────────────────────────────────────────────────────┘
    │
    │ HTTP GET http://localhost:8082/inventory/{orderId}
    ▼
┌──────────────────────────────────────────────────────────────┐
│ SERVICE C - Inventory Service (Port 8082)                   │
│                                                               │
│  - Manages inventory operations                              │
│  - Queries database (50ms)                                   │
│  - Checks stock levels (40ms)                                │
│  - Reserves inventory (30ms)                                 │
│  - Updates cache (20ms)                                      │
│  - Returns stock availability                                │
│                                                               │
└──────────────────────────────────────────────────────────────┘
    │
    │ Response with inventory status
    ▼
Service B formats response
    │
    │ Response with order status
    ▼
Service A formats response
    │
    │ Final response to client
    ▼
Client/Browser
```

### Service Dependency Graph

```
Client
  │
  └──► Service A (8080)
        │
        └──► Service B (8081)
              │
              └──► Service C (8082)
```

---

## Service A - API Gateway

### Basic Information

| Property | Value |
|----------|-------|
| **Service Name** | `service-a` |
| **Port** | `8080` |
| **Package** | `com.example.servicea` |
| **Role** | Entry point / Gateway |
| **Downstream Services** | Service B |
| **Processing Time** | ~2ms (excluding downstream calls) |

### Architecture Role

Service A acts as the **API Gateway** and **orchestrator** for the entire request flow. It:
- Receives client requests
- Validates input parameters
- Prepares request context/metadata
- Delegates to Service B
- Aggregates and formats responses

### Key Classes

#### ServiceAApplication.java
```java
@SpringBootApplication
public class ServiceAApplication {
    public static void main(String[] args) {
        SpringApplication.run(ServiceAApplication.class, args);
    }
    
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();  // ⚠️ Missing RestTemplateBuilder!
    }
}
```

**Issues:**
- ❌ Does NOT use `RestTemplateBuilder`
- ❌ This breaks trace propagation when implementing Zipkin
- ✅ Should be: `return builder.build();`

#### ServiceAController.java

**Main Endpoint:**
```
GET /api/order/{orderId}
```

**Flow:**
1. Log incoming request
2. Validate order ID (non-null, non-empty)
3. Prepare order metadata:
   - `orderId`: The order identifier
   - `timestamp`: Current timestamp
   - `source`: "service-a"
4. Call Service B: `http://localhost:8081/order/{orderId}`
5. Format response: `"Service A -> " + response_from_B`
6. Return final response

**Key Methods:**
- `getOrder(@PathVariable String orderId)` - Main handler
- `validateRequest(String orderId)` - Validates order ID
- `prepareOrderMetadata(String orderId)` - Creates metadata map
- `formatResponse(String orderResponse)` - Wraps response
- `health()` - Health check endpoint

### Configuration

**application.properties:**
```properties
spring.application.name=service-a
server.port=8080
```

**Missing Tracing Configuration:**
```properties
# NOT CONFIGURED
management.tracing.sampling.probability=1.0
management.zipkin.tracing.endpoint=http://localhost:9411/api/v2/spans
logging.pattern.level=%5p [${spring.application.name:},%X{traceId:-},%X{spanId:-}]
```

### Dependencies

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
```

**Missing Dependencies:**
- ❌ `spring-boot-starter-actuator`
- ❌ `spring-boot-starter-aop`
- ❌ `micrometer-tracing-bridge-brave`
- ❌ `zipkin-reporter-brave`

### Sample Request & Response

**Request:**
```bash
curl http://localhost:8080/api/order/12345
```

**Response:**
```
Service A -> Service B (Order) -> Service C (Inventory): Stock available for order 12345
```

**Health Check:**
```bash
curl http://localhost:8080/health
# Response: Service A is running
```

---

## Service B - Order Service

### Basic Information

| Property | Value |
|----------|-------|
| **Service Name** | `service-b` |
| **Port** | `8081` |
| **Package** | `com.example.serviceb` |
| **Role** | Order processor |
| **Upstream Services** | Service A |
| **Downstream Services** | Service C |
| **Processing Time** | ~100ms (excluding downstream calls) |

### Architecture Role

Service B is the **Order Processing Service** that:
- Processes order requests from Service A
- Performs business logic and validations
- Interacts with Service C for inventory checks
- Maintains order workflow

### Key Classes

#### ServiceBApplication.java
```java
@SpringBootApplication
public class ServiceBApplication {
    public static void main(String[] args) {
        SpringApplication.run(ServiceBApplication.class, args);
    }
    
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();  // ⚠️ Missing RestTemplateBuilder!
    }
}
```

**Issues:**
- ❌ Does NOT use `RestTemplateBuilder`
- ❌ This breaks trace propagation
- ✅ Should be: `return builder.build();`

#### ServiceBController.java

**Main Endpoint:**
```
GET /order/{orderId}
```

**Processing Flow:**

```
Request: /order/{orderId}
    │
    ├─► Log request
    │
    ├─► Check Order Eligibility (50ms)
    │   - Simulates business logic
    │
    ├─► Calculate Order Amount (30ms)
    │   - Formula: 99.99 + orderId.hashCode() % 100
    │
    ├─► Call Service C
    │   - GET http://localhost:8082/inventory/{orderId}
    │
    ├─► Apply Business Rules (20ms)
    │   - Post-processing validation
    │
    └─► Return Response
        - "Service B (Order) -> " + response_from_C
```

**Key Methods:**
- `processOrder(@PathVariable String orderId)` - Main handler
- `checkOrderEligibility(String orderId)` - Business validation (50ms)
- `calculateOrderAmount(String orderId)` - Amount calculation (30ms)
- `applyBusinessRules(String orderId, double amount)` - Post-processing (20ms)
- `health()` - Health check endpoint

### Timing Breakdown

| Operation | Duration | Purpose |
|-----------|----------|---------|
| Order Eligibility | 50ms | Check if order can be processed |
| Amount Calculation | 30ms | Calculate order total |
| Service C Call | Variable | Check inventory |
| Business Rules | 20ms | Apply post-validation logic |
| **Total (excl. C)** | **~100ms** | Internal processing |

### Configuration

**application.properties:**
```properties
spring.application.name=service-b
server.port=8081
```

**Missing Tracing Configuration:**
```properties
management.tracing.sampling.probability=1.0
management.zipkin.tracing.endpoint=http://localhost:9411/api/v2/spans
logging.pattern.level=%5p [${spring.application.name:},%X{traceId:-},%X{spanId:-}]
```

### Dependencies

Same as Service A - missing tracing dependencies.

### Sample Request & Response

**Request:**
```bash
curl http://localhost:8081/order/ORDER-123
```

**Response:**
```
Service B (Order) -> Service C (Inventory): Stock available for order ORDER-123
```

**Health Check:**
```bash
curl http://localhost:8081/health
# Response: Service B is running
```

---

## Service C - Inventory Service

### Basic Information

| Property | Value |
|----------|-------|
| **Service Name** | `service-c` |
| **Port** | `8082` |
| **Package** | `com.example.servicec` |
| **Role** | Inventory manager |
| **Upstream Services** | Service B |
| **Downstream Services** | None (terminal) |
| **Processing Time** | ~140ms |

### Architecture Role

Service C is the **Inventory Management Service** that:
- Manages product inventory
- Checks stock availability
- Reserves inventory for orders
- Updates inventory cache
- Acts as the final endpoint in the chain

### Key Classes

#### ServiceCApplication.java
```java
@SpringBootApplication
public class ServiceCApplication {
    public static void main(String[] args) {
        SpringApplication.run(ServiceCApplication.class, args);
    }
}
```

**Notes:**
- ✅ No RestTemplate bean needed (doesn't call other services)
- ✅ Simple and clean
- Status: Already correct

#### ServiceCController.java

**Main Endpoint:**
```
GET /inventory/{orderId}
```

**Processing Flow:**

```
Request: /inventory/{orderId}
    │
    ├─► Log request
    │
    ├─► Query Database (50ms)
    │   - Simulates database lookup
    │
    ├─► Check Stock Level (40ms)
    │   - Formula: 100 + orderId.hashCode() % 50
    │
    ├─► Reserve Inventory (30ms)
    │   - Allocates stock for order
    │
    ├─► Update Cache (20ms)
    │   - Refreshes inventory cache
    │
    └─► Return Response
        - "Service C (Inventory): Stock available for order " + orderId
```

**Key Methods:**
- `checkInventory(@PathVariable String orderId)` - Main handler
- `queryDatabase(String orderId)` - DB query simulation (50ms)
- `checkStockLevel(String orderId)` - Stock check (40ms)
- `reserveInventory(String orderId, int quantity)` - Reserve stock (30ms)
- `updateInventoryCache(String orderId)` - Cache update (20ms)
- `health()` - Health check endpoint

### Timing Breakdown

| Operation | Duration | Purpose |
|-----------|----------|---------|
| Database Query | 50ms | Fetch inventory records |
| Stock Level Check | 40ms | Verify available stock |
| Reserve Inventory | 30ms | Allocate units for order |
| Update Cache | 20ms | Refresh local cache |
| **Total** | **~140ms** | Complete processing |

### Configuration

**application.properties:**
```properties
spring.application.name=service-c
server.port=8082
```

**Missing Tracing Configuration:**
```properties
management.tracing.sampling.probability=1.0
management.zipkin.tracing.endpoint=http://localhost:9411/api/v2/spans
logging.pattern.level=%5p [${spring.application.name:},%X{traceId:-},%X{spanId:-}]
```

### Dependencies

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
```

**Same Missing Dependencies as Services A & B**

### Sample Request & Response

**Request:**
```bash
curl http://localhost:8082/inventory/ORDER-123
```

**Response:**
```
Service C (Inventory): Stock available for order ORDER-123
```

**Health Check:**
```bash
curl http://localhost:8082/health
# Response: Service C is running
```

---

## Inter-Service Communication

### Network Topology

```
Service A (8080) ──► Service B (8081) ──► Service C (8082)
     │                     │                    │
     └─────► logs          └────► logs          └─► logs
```

### HTTP Calls

| From | To | Endpoint | Method | Purpose |
|------|----|---------|---------|---------
| Service A | Service B | `http://localhost:8081/order/{orderId}` | GET | Process order |
| Service B | Service C | `http://localhost:8082/inventory/{orderId}` | GET | Check inventory |

### RestTemplate Configuration

**Current (Incorrect):**
```java
@Bean
public RestTemplate restTemplate() {
    return new RestTemplate();  // NO TRACING INTERCEPTORS
}
```

**Required (For Tracing):**
```java
@Bean
public RestTemplate restTemplate(RestTemplateBuilder builder) {
    return builder.build();  // AUTO-CONFIGURED WITH TRACING
}
```

### Request Headers

**Current State:** Basic HTTP headers only

**With Tracing (Future):** Will include:
- `X-B3-TraceId` - Trace identifier
- `X-B3-SpanId` - Span identifier
- `X-B3-ParentSpanId` - Parent span reference
- `X-B3-Sampled` - Sampling decision

---

## Request Flow & Timing

### Complete End-to-End Timeline

```
Time(ms) | Event
---------|-----------------------------------------------------
0        | Client sends: GET /api/order/ORDER-123
1        | Service A: Request received
2        | Service A: Validate order ID
3        | Service A: Prepare metadata
4        | Service A: Call Service B
5        | Service B: Request received
5-55     | Service B: Check eligibility (50ms)
55-85    | Service B: Calculate amount (30ms)
85       | Service B: Call Service C
86       | Service C: Request received
86-136   | Service C: Query database (50ms)
136-176  | Service C: Check stock (40ms)
176-206  | Service C: Reserve inventory (30ms)
206-226  | Service C: Update cache (20ms)
226      | Service C: Response sent
227      | Service B: Build response
227-247  | Service B: Apply rules (20ms)
248      | Service B: Response sent
249      | Service A: Build response
250      | Response returned to client
```

**Total Latency: ~250ms**

### Detailed Breakdown

**Service A Processing: 4ms**
- Request parsing: 1ms
- Validation: 1ms
- Metadata preparation: 1ms
- Response formatting: 1ms

**Service B Processing: 100ms**
- Eligibility check: 50ms
- Amount calculation: 30ms
- Business rules: 20ms

**Service C Processing: 140ms**
- Database query: 50ms
- Stock check: 40ms
- Reserve inventory: 30ms
- Cache update: 20ms

**Network Overhead: ~6ms** (estimate)

**Total: ~250ms**

---

## Current Issues

### 🔴 Critical Issues

#### 1. No Distributed Tracing Implementation
**Impact:** Cannot track requests across services
- No tracing dependencies added
- No Zipkin configuration
- No trace context propagation
- No correlation IDs in logs

#### 2. RestTemplate Not Using RestTemplateBuilder
**Impact:** Breaks trace propagation
**Severity:** CRITICAL
**Services Affected:** Service A, Service B

```java
// ❌ CURRENT (WRONG)
@Bean
public RestTemplate restTemplate() {
    return new RestTemplate();
}

// ✅ REQUIRED
@Bean
public RestTemplate restTemplate(RestTemplateBuilder builder) {
    return builder.build();
}
```

#### 3. Missing Tracing Dependencies
**Impact:** Cannot enable observability
**Services Affected:** All services
**Missing:**
- `spring-boot-starter-actuator`
- `spring-boot-starter-aop`
- `micrometer-tracing-bridge-brave`
- `zipkin-reporter-brave`

#### 4. No Sampling Configuration
**Impact:** No trace collection
**Services Affected:** All services
**Missing:**
```properties
management.tracing.sampling.probability=1.0
```

#### 5. No Zipkin Endpoint Configuration
**Impact:** Traces cannot be sent anywhere
**Services Affected:** All services
**Missing:**
```properties
management.zipkin.tracing.endpoint=http://localhost:9411/api/v2/spans
```

#### 6. No Trace ID in Logs
**Impact:** Cannot correlate logs across services
**Services Affected:** All services
**Missing:**
```properties
logging.pattern.level=%5p [${spring.application.name:},%X{traceId:-},%X{spanId:-}]
```

### ⚠️ Warnings

- No health/readiness probes for container orchestration
- No graceful shutdown configuration
- No request timeouts configured
- No circuit breaker for downstream calls
- No retry logic for failed requests

---

## Technology Stack

### Core Technologies

| Component | Version | Purpose |
|-----------|---------|---------|
| Java | 17+ | Programming language |
| Spring Boot | 3.2.0 | Application framework |
| Maven | 3.6+ | Build tool |
| Spring Web | 3.2.0 | HTTP framework |
| SLF4J | (Latest) | Logging |

### Java Version Requirements

```
Minimum: Java 17
Recommended: Java 17 or 21
```

### Maven Plugins

```xml
<plugin>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-maven-plugin</artifactId>
</plugin>
```

### Build Information

- **Build System:** Maven
- **Source Encoding:** UTF-8
- **Target Encoding:** UTF-8

---

## How to Run

### Prerequisites

```bash
# Verify Java version
java -version
# Should show: openjdk version "17" or higher

# Verify Maven
mvn -version
# Should show: Maven 3.6+
```

### Building

```bash
# Build all services
cd /home/yaziz/workspace/self_task/distributed_tracing

# Build Service C first
cd service-c
mvn clean package -DskipTests

# Build Service B
cd ../service-b
mvn clean package -DskipTests

# Build Service A
cd ../service-a
mvn clean package -DskipTests
```

### Running Services

**Option 1: Using Maven (Development)**

Terminal 1 - Service C:
```bash
cd service-c
mvn spring-boot:run
```

Terminal 2 - Service B:
```bash
cd service-b
mvn spring-boot:run
```

Terminal 3 - Service A:
```bash
cd service-a
mvn spring-boot:run
```

**Option 2: Using JAR files (Production)**

```bash
# Service C
java -jar service-c/target/service-c-1.0.0.jar

# Service B
java -jar service-b/target/service-b-1.0.0.jar

# Service A
java -jar service-a/target/service-a-1.0.0.jar
```

### Verification

```bash
# Check all services are running
curl http://localhost:8080/health
curl http://localhost:8081/health
curl http://localhost:8082/health

# All should respond with service name + "is running"
```

### Testing

```bash
# Single request
curl http://localhost:8080/api/order/12345

# Expected output:
# Service A -> Service B (Order) -> Service C (Inventory): Stock available for order 12345

# Multiple requests
for i in {1..5}; do
  echo "Request $i:"
  curl http://localhost:8080/api/order/ORDER-$i
  echo ""
  sleep 1
done
```

### Stopping Services

```bash
# Ctrl+C in each terminal to stop
# Or graceful shutdown:
curl -X POST http://localhost:8080/actuator/shutdown
```

---

## API Endpoints Reference

### Service A - Port 8080

#### GET /api/order/{orderId}
**Description:** Main endpoint to initiate order processing

**Parameters:**
- `orderId` (path parameter, required): Order identifier

**Request:**
```bash
curl http://localhost:8080/api/order/12345
```

**Response:**
```
Service A -> Service B (Order) -> Service C (Inventory): Stock available for order 12345
```

**Status Codes:**
- `200 OK` - Order processed successfully
- `400 Bad Request` - Invalid order ID

#### GET /health
**Description:** Health check endpoint

**Request:**
```bash
curl http://localhost:8080/health
```

**Response:**
```
Service A is running
```

**Status Codes:**
- `200 OK` - Service is running

---

### Service B - Port 8081

#### GET /order/{orderId}
**Description:** Process order and check inventory

**Parameters:**
- `orderId` (path parameter, required): Order identifier

**Request:**
```bash
curl http://localhost:8081/order/ORDER-123
```

**Response:**
```
Service B (Order) -> Service C (Inventory): Stock available for order ORDER-123
```

**Status Codes:**
- `200 OK` - Order processed successfully
- `400 Bad Request` - Invalid request

#### GET /health
**Description:** Health check endpoint

**Request:**
```bash
curl http://localhost:8081/health
```

**Response:**
```
Service B is running
```

---

### Service C - Port 8082

#### GET /inventory/{orderId}
**Description:** Check inventory and reserve stock

**Parameters:**
- `orderId` (path parameter, required): Order identifier

**Request:**
```bash
curl http://localhost:8082/inventory/ORDER-123
```

**Response:**
```
Service C (Inventory): Stock available for order ORDER-123
```

**Status Codes:**
- `200 OK` - Inventory checked successfully
- `404 Not Found` - Item not in inventory

#### GET /health
**Description:** Health check endpoint

**Request:**
```bash
curl http://localhost:8082/health
```

**Response:**
```
Service C is running
```

---

## Summary

### Current Capabilities ✅

- Three independent microservices
- Synchronous HTTP communication
- Order processing workflow
- Health check endpoints
- Comprehensive logging (basic)
- Maven-based build system
- Java 17+ compatible
- Spring Boot 3.2.0 based

### Missing Features ❌

- Distributed tracing (Zipkin)
- Trace context propagation
- Trace ID correlation
- Service dependency visibility
- Performance metrics
- Custom spans
- Error tracking
- Request/response timing analysis

### Next Steps

To implement distributed tracing with Zipkin, refer to the `ZIPKIN_IMPLEMENTATION_GUIDE.md` file, which includes:
1. Adding required dependencies
2. Configuring each service
3. Setting up Zipkin server
4. Verifying trace propagation
5. Viewing traces in UI

---

**Document Version:** 1.0  
**Last Updated:** November 10, 2025  
**Status:** ✅ Current (Baseline state)
