# Distributed Tracing with Spring Boot - OpenTelemetry + OTLP + Grafana Tempo

This project demonstrates **distributed tracing** across 4 Spring Boot microservices using **OpenTelemetry** with **OTLP (OpenTelemetry Protocol)** and **Grafana Tempo** as the backend.

## 🎯 Key Features

- ✅ **Spring Boot 4.0.1** - Latest Spring Boot version
- ✅ **OpenTelemetry + OTLP** - Vendor-independent tracing
- ✅ **Grafana Tempo** - Modern, lightweight tracing backend
- ✅ **Synchronous HTTP tracing** - RestTemplate with automatic propagation
- ✅ **Asynchronous RabbitMQ tracing** - Message queue context propagation
- ✅ **CompletableFuture support** - Thread pool context propagation
- ✅ **Circular dependencies** - Complex trace scenarios (D → A callback)
- ✅ **Docker Compose** - Complete infrastructure setup

## 🏗️ Architecture

### Service Flow
```
User → Service A → Service B → Service C → Service D
         ↑                          ↓
         └──────────────────────────┘
              (Callback via RabbitMQ)

Components:
- Service A (8080): API Gateway
- Service B (8081): Order Service
- Service C (8082): Inventory Service
- Service D (8083): Notification Service with RabbitMQ
- Grafana Tempo (4318): Tracing backend (OTLP endpoint)
- Grafana (3000): Trace visualization
- RabbitMQ (5672/15672): Message broker
```

### Tracing Stack

1. **Micrometer Tracing** - Abstraction layer for tracing
2. **OpenTelemetry Bridge** - Micrometer → OpenTelemetry integration
3. **OTLP Exporter** - Ships traces to Tempo via HTTP
4. **Grafana Tempo** - Stores and queries traces
5. **Grafana** - Visualizes traces with service graph

## 🚀 Quick Start

### Prerequisites

- **Docker** and **Docker Compose**
- **Java 17+**
- **Maven 3.6+**

### Option 1: Run with Docker Compose (Recommended)

```bash
# Start all services with infrastructure
docker-compose up -d

# Wait for services to be ready (~30 seconds)
docker-compose logs -f

# Test the complete flow
curl http://localhost:8080/api/order/ORDER-12345

# View traces in Grafana
# Open: http://localhost:3000
# Navigate to Explore → Tempo → Search for traces
```

### Option 2: Run Locally (for Development)

**Step 1: Start Infrastructure**
```bash
# Start only RabbitMQ and Tempo
docker-compose up -d rabbitmq tempo grafana
```

**Step 2: Build Services**
```bash
# Use the provided build script
./rebuild-all.sh

# Or build individually
cd service-a && mvn clean package && cd ..
cd service-b && mvn clean package && cd ..
cd service-c && mvn clean package && cd ..
cd service-d && mvn clean package && cd ..
```

**Step 3: Run Services**
```bash
# Terminal 1 - Service D (must start first for RabbitMQ)
cd service-d && mvn spring-boot:run

# Terminal 2 - Service C
cd service-c && mvn spring-boot:run

# Terminal 3 - Service B
cd service-b && mvn spring-boot:run

# Terminal 4 - Service A
cd service-a && mvn spring-boot:run
```

**Step 4: Test**
```bash
# Make a request
curl http://localhost:8080/api/order/ORDER-12345

# Expected response:
# Service A -> Service B (Order) -> Service C (Inventory): Stock available for order ORDER-12345
```

## 📊 View Traces in Grafana

1. **Open Grafana**: http://localhost:3000
2. **Navigate to Explore** (compass icon on left sidebar)
3. **Select Tempo** datasource (top dropdown)
4. **Search for traces**:
   - Click "Search" tab
   - Select Service: `service-a`
   - Click "Run Query"
5. **Click on any trace** to see the complete flow:
   - Service A → Service B → Service C → Service D
   - RabbitMQ async processing
   - Callback from D → A

### What You'll See

- **Complete trace timeline** - All service calls with durations
- **Service graph** - Visual representation of dependencies
- **Span details** - HTTP methods, status codes, errors
- **RabbitMQ spans** - Message send and receive operations
- **Async processing** - CompletableFuture spans with proper parent-child relationships

## 🔍 Testing Different Scenarios

### 1. Basic Sync Flow (A → B → C)
```bash
curl http://localhost:8080/api/order/ORDER-001
```
**Trace shows**: HTTP calls across 3 services

### 2. Async RabbitMQ Processing
```bash
curl -X POST http://localhost:8083/notify \
  -H "Content-Type: application/json" \
  -d '{
    "orderId": "ORDER-002",
    "type": "ORDER_UPDATE",
    "status": "PROCESSING",
    "channel": "EMAIL",
    "callbackRequired": true
  }'
```
**Trace shows**: Message queue publish → consume → callback

### 3. Complete Flow with Circular Dependency
```bash
curl http://localhost:8080/api/order/ORDER-003
```
**Trace shows**: 
- A → B → C (sync)
- B → D (notification)
- C → D (inventory notification)
- D → RabbitMQ → D (async processing)
- D → A (callback - circular dependency)

### 4. Multiple Concurrent Requests
```bash
for i in {1..10}; do
  curl http://localhost:8080/api/order/ORDER-$i &
done
wait
```
**Trace shows**: Multiple traces with proper isolation

## 🔧 Configuration Details

### OpenTelemetry Configuration

Each service is configured in `application.properties`:

```properties
# Service name for trace identification
spring.application.name=service-a

# Sample all requests (100%)
management.tracing.sampling.probability=1.0

# OTLP endpoint (Tempo)
management.otlp.tracing.endpoint=http://localhost:4318/v1/traces

# Enable metrics for better observability
management.metrics.distribution.percentiles-histogram.http.server.requests=true
```

### Context Propagation

**RestTemplate** - Automatic instrumentation via Micrometer:
- W3C Trace Context headers automatically added
- Parent span context propagated to downstream services

**RabbitMQ** - Automatic message header propagation:
- Trace context injected into AMQP message headers
- Consumer extracts context and continues the trace

**CompletableFuture/Async** - Thread pool context propagation:
```java
@Bean(name = "taskExecutor")
public Executor taskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    // Context snapshot wraps runnables to propagate trace context
    executor.setTaskDecorator(runnable -> ContextSnapshot.captureAll().wrap(runnable));
    executor.initialize();
    return executor;
}
```

## 🎛️ Management Endpoints

Each service exposes actuator endpoints:

- `http://localhost:8080/actuator/health` - Health check
- `http://localhost:8080/actuator/metrics` - Metrics
- `http://localhost:8080/actuator/prometheus` - Prometheus metrics

## 📦 Service Details

### Service A - API Gateway (Port 8080)
**Endpoints**:
- `GET /api/order/{orderId}` - Main entry point, calls Service B
- `GET /api/callback/{orderId}` - Receives callbacks from Service D
- `GET /health` - Health check

### Service B - Order Service (Port 8081)
**Endpoints**:
- `GET /order/{orderId}` - Process order, calls Service C and notifies Service D
- `GET /health` - Health check

**Operations**: Eligibility check → Amount calculation → Business rules → Inventory check → Notification

### Service C - Inventory Service (Port 8082)
**Endpoints**:
- `GET /inventory/{orderId}` - Check inventory, notifies Service D
- `GET /health` - Health check

**Operations**: DB query → Stock check → Reserve inventory → Cache update → Notification

### Service D - Notification Service (Port 8083)
**Endpoints**:
- `POST /notify` - Send notification (sync endpoint)
- `GET /notifications/{orderId}` - Check notification status
- `GET /health` - Health check

**Operations**:
- **Sync**: Validate → Load template → Personalize → Send → Audit
- **Async** (via RabbitMQ): Prepare → Enrich → Format → Deliver → Callback

## 🐰 RabbitMQ Management

- **URL**: http://localhost:15672
- **Username**: guest
- **Password**: guest

**Queues to monitor**:
- `notification-queue` - Async notification processing

## 🛠️ Troubleshooting

### Services can't connect to Tempo
```bash
# Check if Tempo is running
docker ps | grep tempo

# Check Tempo logs
docker logs tempo

# Verify endpoint is accessible
curl http://localhost:4318/v1/traces
```

### No traces in Grafana
1. **Wait 10-30 seconds** - Traces need time to be ingested
2. **Check sampling** - Should be 1.0 (100%)
3. **Verify service logs** - Look for OpenTelemetry initialization
4. **Check Tempo connectivity** - Services should log OTLP export attempts

### RabbitMQ connection issues
```bash
# Ensure RabbitMQ is healthy
docker-compose ps rabbitmq

# Check RabbitMQ logs
docker logs rabbitmq

# Verify connection from service-d
docker logs service-d | grep -i rabbit
```

### Build failures
```bash
# Clean all services
cd service-a && mvn clean && cd ..
cd service-b && mvn clean && cd ..
cd service-c && mvn clean && cd ..
cd service-d && mvn clean && cd ..

# Rebuild
./rebuild-all.sh
```

## 🔄 Switching Tracing Backends

This setup uses **OTLP**, which is **vendor-independent**. You can easily switch backends:

### To Jaeger
Change `management.otlp.tracing.endpoint` to:
```properties
management.otlp.tracing.endpoint=http://localhost:4318/v1/traces
```
And run Jaeger with OTLP support:
```bash
docker run -d --name jaeger \
  -p 4318:4318 \
  -p 16686:16686 \
  jaegertracing/all-in-one:latest
```

### To Cloud Providers
- **Datadog**: Change endpoint to Datadog OTLP ingestion URL
- **New Relic**: Use New Relic OTLP endpoint
- **AWS X-Ray**: Use AWS Distro for OpenTelemetry

## 📈 Performance Characteristics

- **Overhead**: ~1-2ms per span (negligible)
- **Sampling**: 100% (adjustable via `management.tracing.sampling.probability`)
- **Async export**: Traces exported in background, no blocking
- **Context propagation**: Zero-copy context snapshot for thread pools

## 🧪 Testing

```bash
# Run unit tests
cd service-a && mvn test && cd ..
cd service-b && mvn test && cd ..
cd service-c && mvn test && cd ..
cd service-d && mvn test && cd ..

# Integration test - verify complete trace
curl http://localhost:8080/api/order/TEST-001
# Then check Grafana for the trace
```

## 🛡️ Security Notes

- Grafana anonymous access is enabled for demo purposes
- In production, enable authentication and use proper secrets
- Use TLS for OTLP exports in production
- Secure RabbitMQ with proper credentials

## 📚 Additional Resources

- [OpenTelemetry Documentation](https://opentelemetry.io/docs/)
- [Micrometer Tracing](https://micrometer.io/docs/tracing)
- [Grafana Tempo Documentation](https://grafana.com/docs/tempo/latest/)
- [Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)

## 🎓 Learning Objectives

This project demonstrates:
1. ✅ Vendor-independent tracing with OTLP
2. ✅ Synchronous HTTP call tracing
3. ✅ Asynchronous message queue tracing
4. ✅ Thread pool context propagation
5. ✅ Circular dependency handling
6. ✅ Service graph visualization
7. ✅ Easy backend swapping (Tempo → Jaeger → Cloud)

## 📝 Next Steps

1. ✅ **Add metrics** - Integrate Prometheus for metrics correlation
2. ✅ **Add logs** - Configure Loki for logs-traces correlation
3. ✅ **Add alerts** - Set up Grafana alerts on trace errors
4. ✅ **Performance testing** - Load test with k6 or Gatling
5. ✅ **Production setup** - Add authentication, TLS, and monitoring

## 🤝 Contributing

This is a demonstration project. Feel free to:
- Experiment with different configurations
- Add more services
- Test different tracing scenarios
- Compare with other tracing solutions

## 📄 License

This project is for educational purposes.

---

**Happy Tracing! 🎉**

For questions or issues, check the service logs or Grafana Tempo logs:
```bash
docker-compose logs -f service-a
docker-compose logs -f tempo
```
