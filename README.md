# Distributed Tracing with Zipkin

This branch demonstrates distributed tracing using **Micrometer Tracing** with **Zipkin** backend.

## What is Zipkin?

Zipkin is an open-source distributed tracing system that helps gather timing data needed to troubleshoot latency problems in microservice architectures. It manages both the collection and lookup of this data.

## Architecture

- **Service A** (Port 8080): API Gateway/Frontend service
- **Service B** (Port 8081): Order service
- **Service C** (Port 8082): Inventory service
- **Zipkin** (Port 9411): Tracing backend and UI

Request flow: Service A → Service B → Service C

## Implementation Details

### Dependencies Added

Each service includes:
```xml
<!-- Spring Boot Actuator for observability -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>

<!-- Micrometer Tracing with Brave (Zipkin-compatible) -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-brave</artifactId>
</dependency>

<!-- Zipkin Reporter -->
<dependency>
    <groupId>io.zipkin.reporter2</groupId>
    <artifactId>zipkin-reporter-brave</artifactId>
</dependency>
```

### Configuration

Each `application.properties` includes:
```properties
# Enable 100% trace sampling (all requests are traced)
management.tracing.sampling.probability=1.0

# Zipkin endpoint
management.zipkin.tracing.endpoint=http://localhost:9411/api/v2/spans

# Include trace/span IDs in logs
logging.pattern.level=%5p [${spring.application.name:},%X{traceId:-},%X{spanId:-}]
```

### How It Works

1. **Automatic Instrumentation**: Micrometer automatically instruments HTTP requests/responses
2. **Trace Propagation**: Trace context (trace ID, span ID) is propagated via HTTP headers (B3 format)
3. **Span Creation**: Each service creates a span for its work
4. **Reporting**: Spans are asynchronously sent to Zipkin via HTTP

## Running the Application

### Option 1: Docker Compose (Recommended)

Start all services + Zipkin:
```bash
docker-compose up --build
```

### Option 2: Run Locally

**1. Start Zipkin:**
```bash
docker run -d -p 9411:9411 openzipkin/zipkin
```

**2. Run services (in separate terminals):**
```bash
# Service C
cd service-c && mvn spring-boot:run

# Service B
cd service-b && mvn spring-boot:run

# Service A
cd service-a && mvn spring-boot:run
```

## Testing

Send a request:
```bash
curl http://localhost:8080/api/order/12345
```

View traces in Zipkin UI:
```bash
open http://localhost:9411
```

## Understanding Zipkin UI

### 1. Search Traces
- Filter by service name, span name, or time range
- Click "Run Query" to see traces

### 2. Trace Details
Click on a trace to see:
- **Trace ID**: Unique identifier for the entire request
- **Timeline**: Visual representation of spans across services
- **Service Dependencies**: Which service called which
- **Timing**: Latency for each service operation

### 3. Dependencies
View service dependency graph showing:
- Request flow between services
- Request counts
- Average latencies

## Key Features

### Trace Context in Logs
```
INFO [service-a,64f1e7c8f8e9a1b2,64f1e7c8f8e9a1b2] Service A: Received request for order 12345
INFO [service-b,64f1e7c8f8e9a1b2,a1b2c3d4e5f6g7h8] Service B: Processing order 12345
INFO [service-c,64f1e7c8f8e9a1b2,h8g7f6e5d4c3b2a1] Service C: Checking inventory for order 12345
```
- Same trace ID across all services
- Different span IDs for each service operation

### Automatic Propagation
No code changes needed! Micrometer automatically:
- Creates spans for HTTP requests
- Propagates trace context via headers
- Reports spans to Zipkin

## Performance Considerations

### Overhead
- **Memory**: ~5-10MB per service for tracing libraries
- **CPU**: <1% overhead for span creation and reporting
- **Network**: Async reporting minimizes impact
- **Latency**: <1ms per request (span creation)

### Sampling
Current config: 100% sampling
```properties
management.tracing.sampling.probability=1.0
```

For production, reduce to 10-20%:
```properties
management.tracing.sampling.probability=0.1
```

## Comparison with Master Branch

| Metric | Master (No Tracing) | Zipkin Branch |
|--------|---------------------|---------------|
| Dependencies | 1 (spring-boot-starter-web) | 4 (+actuator, +micrometer, +zipkin) |
| JAR Size | ~18 MB | ~25 MB |
| Startup Time | ~3s | ~3.5s |
| Request Latency | Baseline | +0.5-1ms |
| Observability | None | Full trace visibility |

## Troubleshooting

### Traces not appearing in Zipkin?
1. Check Zipkin is running: `curl http://localhost:9411`
2. Verify endpoint in application.properties
3. Check service logs for connection errors

### Missing spans?
- Ensure all services are using RestTemplate (auto-instrumented)
- Check sampling probability is not 0

## Next Steps

Compare with other tracing implementations:
```bash
git checkout opentelemetry  # OpenTelemetry implementation
git checkout jaeger         # Jaeger backend
git checkout grafana-tempo  # Grafana Tempo
```

## Resources

- [Zipkin Documentation](https://zipkin.io/)
- [Micrometer Tracing](https://micrometer.io/docs/tracing)
- [Spring Boot Observability](https://spring.io/blog/2022/10/12/observability-with-spring-boot-3)
