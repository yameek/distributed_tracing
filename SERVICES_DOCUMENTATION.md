# Distributed Tracing Microservices - Service Documentation

**Date:** November 10, 2025  
**Project:** Distributed Tracing with Spring Boot  
**Current State:** 4 Services with Async Messaging & Circular Dependencies

---

## Table of Contents

1. [Overview](#overview)
2. [Architecture](#architecture)
3. [Service A - API Gateway](#service-a--api-gateway)
4. [Service B - Order Service](#service-b--order-service)
5. [Service C - Inventory Service](#service-c--inventory-service)
6. [Service D - Notification Service](#service-d--notification-service)
7. [RabbitMQ Integration](#rabbitmq-integration)
8. [Circular Dependencies](#circular-dependencies)
9. [Complete Request Flow](#complete-request-flow)
10. [Technology Stack](#technology-stack)
11. [How to Run](#how-to-run)
12. [API Endpoints Reference](#api-endpoints-reference)

---

## Overview

This is a **four-tier microservices architecture** with async messaging demonstrating a complete order processing and notification pipeline. Each service handles a specific business domain with both synchronous and asynchronous communication patterns.

### Current Status: ✅ COMPLETE WITH ASYNC MESSAGING

The services include:
- **Synchronous HTTP calls** between services
- **Asynchronous messaging** via RabbitMQ
- **Circular dependencies** for complex tracing scenarios
- **Multiple internal operations** per service for detailed span visibility

### Key Characteristics

- **Language:** Java 17
- **Framework:** Spring Boot 3.2.0
- **Build Tool:** Maven 3.6+
- **Architecture Pattern:** Microservices with sync HTTP + async messaging
- **Message Broker:** RabbitMQ (for Service D)
- **Services:** 4 microservices (A, B, C, D)
- **Total Operations:** 25+ operations per request

---

## Architecture

### System Diagram

```
Service Flow:
1. A → B → C → D (main flow)
2. B → D (order notification)
3. C → D (inventory notification)
4. D → RabbitMQ → D (async processing)
5. D → A (callback - creates circular dependency)

   ┌──────────┐      ┌──────────┐      ┌──────────┐      ┌──────────┐
   │Service A │─────▶│Service B │─────▶│Service C │─────▶│Service D │
   │  (8080)  │      │  (8081)  │      │  (8082)  │      │  (8083)  │
   └──────────┘      └──────────┘      └──────────┘      └──────────┘
        ▲                  │                  │                  │
        │                  │                  │                  │
        │                  └──────────────────┼─────────────────▶│
        │                                     │                  │
        │                                     └─────────────────▶│
        │                                                        │
        │                    ┌──────────────┐                   │
        │                    │  RabbitMQ    │◀──────────────────┘
        │                    │  Queue       │
        │                    └──────────────┘
        │                           │
        │                           ▼
        │                    [Async Processing]
        │                           │
        └───────────────────────────┴──────────────────────────────
                            (Callback)
```

---

## Service D - Notification Service

### Overview
**Port:** 8083  
**Purpose:** Handles notifications via multiple channels with async processing  
**Technology:** Spring Boot + Spring AMQP (RabbitMQ)

### Responsibilities
- Receive notification requests from Service B and C
- Process notifications asynchronously via RabbitMQ
- Send notifications through various channels (Email, SMS, Push)
- Trigger callbacks to Service A (creates circular dependency)
- Maintain notification history and audit logs

### Internal Operations (7+ spans when traced)
1. **validateNotificationRequest()** - Validates incoming requests (20ms)
2. **loadNotificationTemplate()** - Loads message template (30ms)
3. **personalizeMessage()** - Personalizes message content (25ms)
4. **sendToChannel()** - Sends via specified channel (40ms)
5. **auditNotification()** - Logs notification audit (15ms)
6. **triggerCallback()** - Calls back to Service A (variable)
7. **checkNotificationHistory()** - Query notification status (35ms)

### Async Processing Operations
1. **prepareNotificationData()** - Prepares data for processing (30ms)
2. **enrichNotificationWithUserData()** - Enriches with user info (40ms)
3. **formatNotificationContent()** - Formats final content (25ms)
4. **deliverNotification()** - Delivers to channel (50ms)
5. **sendCallbackToServiceA()** - Async callback to Service A

---

## RabbitMQ Integration

### Message Flow
1. Service D Controller receives HTTP request
2. Sends message to RabbitMQ queue
3. RabbitMQ holds message
4. Service D Listener processes message asynchronously
5. Callback to Service A (if required)

### Configuration
- **Queue:** notification-queue
- **Exchange:** notification-exchange (Topic)
- **Routing Key:** notification.order
- **Message Format:** JSON

### Running RabbitMQ
```bash
docker run -d --name rabbitmq -p 5672:5672 -p 15672:15672 rabbitmq:3-management
```

Management UI: http://localhost:15672 (guest/guest)

---

## Circular Dependencies

### Circular Flow
```
Service A → B → C → D → A (callback)
    ↑                     │
    └─────────────────────┘
```

### Multiple Paths to Service D
- **Path 1:** A → B → C → D
- **Path 2:** A → B → D (direct from B)
- **Path 3:** A → B → C → D (from C)

This creates a rich dependency graph for tracing visualization.

---

## Complete Request Flow

```
1. User → Service A: GET /api/order/ORDER-123
   ├─ Validate request
   ├─ Prepare metadata
   ├─ Format response
   
2. Service A → Service B: GET /order/ORDER-123
   ├─ Check eligibility (50ms)
   ├─ Calculate amount (30ms)
   ├─ Apply business rules (20ms)
   ├─ Notify Service D
   
3. Service B → Service C: GET /inventory/ORDER-123
   ├─ Query database (50ms)
   ├─ Check stock level (40ms)
   ├─ Reserve inventory (30ms)
   ├─ Update cache (20ms)
   ├─ Notify Service D
   
4. Service B/C → Service D: POST /notify
   ├─ Validate request (20ms)
   ├─ Load template (30ms)
   ├─ Personalize message (25ms)
   ├─ Send to channel (40ms)
   ├─ Audit (15ms)
   
5. Service D → RabbitMQ: Queue message
   
6. Service D (Async) processes:
   ├─ Prepare data (30ms)
   ├─ Enrich with user data (40ms)
   ├─ Format content (25ms)
   ├─ Deliver (50ms)
   
7. Service D → Service A: GET /api/callback/ORDER-123
   ├─ Process callback (30ms)
   ├─ Update status (20ms)
```

**Total: 25+ operations across all services!**

---

## API Endpoints Reference

### Service A (Port 8080)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | /api/order/{orderId} | Process order request |
| GET | /api/callback/{orderId} | Handle callback from Service D |
| GET | /health | Health check |

### Service B (Port 8081)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | /order/{orderId} | Process order, call C and D |
| GET | /health | Health check |

### Service C (Port 8082)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | /inventory/{orderId} | Check inventory, call D |
| GET | /health | Health check |

### Service D (Port 8083)

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | /notify | Send notification |
| GET | /notifications/{orderId} | Get notification status |
| GET | /health | Health check |

---

## Technology Stack

### Core
- Spring Boot 3.2.0
- Java 17
- Maven 3.6+

### Dependencies
- spring-boot-starter-web (All services)
- spring-boot-starter-amqp (Service D)
- spring-boot-starter-test (All services)

### Infrastructure
- RabbitMQ 3.x (Message broker)

---

## How to Run

### Step 1: Start RabbitMQ
```bash
docker run -d --name rabbitmq -p 5672:5672 -p 15672:15672 rabbitmq:3-management
```

### Step 2: Build Services
```bash
cd service-d && mvn clean package -DskipTests
cd ../service-c && mvn clean package -DskipTests
cd ../service-b && mvn clean package -DskipTests
cd ../service-a && mvn clean package -DskipTests
```

### Step 3: Run Services
```bash
# Terminal 1
cd service-d && mvn spring-boot:run

# Terminal 2
cd service-c && mvn spring-boot:run

# Terminal 3
cd service-b && mvn spring-boot:run

# Terminal 4
cd service-a && mvn spring-boot:run
```

### Step 4: Test
```bash
curl http://localhost:8080/api/order/ORDER-123
```

---

## Summary

✅ 4 Services (A, B, C, D)  
✅ Synchronous HTTP communication  
✅ Asynchronous RabbitMQ messaging  
✅ Circular dependencies (D → A)  
✅ Multiple paths to services  
✅ 25+ internal operations  
✅ Ready for distributed tracing  

**Next:** Check `zipkin` branch for tracing implementation!
