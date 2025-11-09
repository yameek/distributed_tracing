# Distributed Tracing with Spring Boot Microservices

This project demonstrates distributed tracing across 3 Spring Boot microservices using different tracing implementations.

## Architecture

- **Service A** (Port 8080): API Gateway/Frontend service
- **Service B** (Port 8081): Order service
- **Service C** (Port 8082): Inventory service

Request flow: Service A → Service B → Service C

## Branches

This repository contains multiple branches to demonstrate different tracing implementations:

- **master**: Base microservices without tracing (baseline for performance comparison)
- **zipkin**: Micrometer Tracing + Zipkin
- **opentelemetry**: OpenTelemetry implementation
- **jaeger**: Jaeger tracing backend
- **grafana-tempo**: Grafana Tempo integration

Each branch includes complete setup instructions and performance analysis.

## Prerequisites

- Java 17+
- Maven 3.6+

## Running the Services (Master Branch - No Tracing)

### Build and Run

**Service C** (run first):
```bash
cd service-c
mvn clean package
mvn spring-boot:run
```

**Service B**:
```bash
cd service-b
mvn clean package
mvn spring-boot:run
```

**Service A**:
```bash
cd service-a
mvn clean package
mvn spring-boot:run
```

### Test the System
```bash
curl http://localhost:8080/api/order/12345
```

Expected response:
```
Service A -> Service B (Order) -> Service C (Inventory): Stock available for order 12345
```

## Endpoints

### Service A
- `GET /api/order/{orderId}` - Main endpoint (calls Service B)
- `GET /health` - Health check

### Service B
- `GET /order/{orderId}` - Process order (calls Service C)
- `GET /health` - Health check

### Service C
- `GET /inventory/{orderId}` - Check inventory
- `GET /health` - Health check

## Project Structure

```
distributed_tracing/
├── service-a/
│   ├── src/main/java/com/example/servicea/
│   │   ├── ServiceAApplication.java
│   │   └── ServiceAController.java
│   ├── src/main/resources/
│   │   └── application.properties
│   ├── pom.xml
│   └── Dockerfile
├── service-b/
│   ├── src/main/java/com/example/serviceb/
│   │   ├── ServiceBApplication.java
│   │   └── ServiceBController.java
│   ├── src/main/resources/
│   │   └── application.properties
│   ├── pom.xml
│   └── Dockerfile
├── service-c/
│   ├── src/main/java/com/example/servicec/
│   │   ├── ServiceCApplication.java
│   │   └── ServiceCController.java
│   ├── src/main/resources/
│   │   └── application.properties
│   ├── pom.xml
│   └── Dockerfile
└── README.md
```

## Next Steps

To explore distributed tracing implementations, checkout the respective branches:

```bash
# Zipkin implementation
git checkout zipkin

# OpenTelemetry implementation
git checkout opentelemetry

# Jaeger implementation
git checkout jaeger

# Grafana Tempo implementation
git checkout grafana-tempo
```

Each branch includes:
- Complete tracing setup
- Docker Compose configuration
- Performance metrics
- Implementation details

