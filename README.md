# Distributed Tracing with Spring Boot Microservices

This project demonstrates distributed tracing across 4 Spring Boot microservices using different tracing implementations.

## 🎯 Current Branch: OpenTelemetry + OTLP + Grafana Tempo

This branch demonstrates **vendor-independent distributed tracing** using:
- ✅ **Spring Boot 4.0.1**
- ✅ **OpenTelemetry** with **OTLP (OpenTelemetry Protocol)**
- ✅ **Grafana Tempo** as the tracing backend
- ✅ **Synchronous HTTP tracing** (RestTemplate)
- ✅ **Asynchronous RabbitMQ tracing** with context propagation
- ✅ **CompletableFuture/Thread pool context propagation**

📖 **See [README-OTEL.md](./README-OTEL.md) for complete setup and usage instructions.**

## Architecture

- **Service A** (Port 8080): API Gateway/Frontend service
- **Service B** (Port 8081): Order service
- **Service C** (Port 8082): Inventory service
- **Service D** (Port 8083): Notification service with RabbitMQ

Request flow: Service A → Service B → Service C → Service D → A (callback)

## Quick Start

### Using Docker Compose (Recommended)

```bash
# Start all services with infrastructure
docker-compose up -d

# Wait for services to be ready (~30 seconds)
docker-compose logs -f

# Test the complete flow
curl http://localhost:8080/api/order/ORDER-12345

# View traces in Grafana at http://localhost:3000
```

### Running Locally

```bash
# Start infrastructure only
docker-compose up -d rabbitmq tempo grafana

# Build and run services
cd service-d && mvn spring-boot:run &
cd service-c && mvn spring-boot:run &
cd service-b && mvn spring-boot:run &
cd service-a && mvn spring-boot:run &

# Test
curl http://localhost:8080/api/order/ORDER-12345
```

## Viewing Traces

1. Open Grafana: http://localhost:3000
2. Navigate to Explore → Tempo
3. Search for traces by service name (e.g., `service-a`)
4. Click on any trace to see the complete request flow

## What You'll See

- **Complete distributed trace** across all 4 services
- **RabbitMQ message propagation** with parent-child span relationships
- **Async processing spans** with proper context propagation
- **Circular dependencies** (D → A callback) properly traced
- **Service dependency graph** visualization

## Key Features

✅ **Vendor-independent**: OTLP allows easy switching between backends (Tempo, Jaeger, cloud providers)  
✅ **Async support**: RabbitMQ and CompletableFuture traces properly  
✅ **Production-ready**: Context propagation across threads and message queues  
✅ **Easy to extend**: Add more services or switch tracing backends without code changes

## Branches

This repository contains multiple branches to demonstrate different tracing implementations:

- **master**: Base microservices without tracing (baseline for performance comparison)
- **copilot/add-opentelemetry-otel** (current): OpenTelemetry + OTLP + Grafana Tempo
- **zipkin**: Micrometer Tracing + Zipkin
- **opentelemetry**: OpenTelemetry implementation
- **jaeger**: Jaeger tracing backend
- **grafana-tempo**: Grafana Tempo integration

Each branch includes complete setup instructions and performance analysis.

## Prerequisites

- Java 17+
- Maven 3.6+
- Docker and Docker Compose

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
├── service-d/
│   ├── src/main/java/com/example/serviced/
│   │   ├── ServiceDApplication.java
│   │   ├── ServiceDController.java
│   │   ├── NotificationService.java
│   │   ├── NotificationRequest.java
│   │   └── RabbitMQConfig.java
│   ├── src/main/resources/
│   │   └── application.properties
│   ├── pom.xml
│   └── Dockerfile
├── docker-compose.yml
├── tempo-config.yaml
├── grafana-datasources.yaml
├── README.md
└── README-OTEL.md
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

## Documentation

- **[README-OTEL.md](./README-OTEL.md)** - Complete OpenTelemetry setup guide (current branch)
- **[SERVICES_DOCUMENTATION.md](./SERVICES_DOCUMENTATION.md)** - Detailed service architecture

## Contributing

This is a demonstration project. Feel free to experiment with different configurations and scenarios.

## License

This project is for educational purposes.

