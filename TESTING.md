# Testing OpenTelemetry Distributed Tracing

This guide helps you verify that the OpenTelemetry tracing setup is working correctly.

## Prerequisites

Ensure all infrastructure is running:
```bash
docker-compose ps
```

You should see:
- rabbitmq (healthy)
- tempo (running)
- grafana (running)

## Test Scenarios

### 1. Basic Health Check

Verify all services are running:
```bash
curl http://localhost:8080/actuator/health
curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health
curl http://localhost:8083/actuator/health
```

Expected: All return `{"status":"UP"}`

### 2. Simple Synchronous Flow (A → B → C)

Test basic HTTP call chain:
```bash
curl http://localhost:8080/api/order/TEST-001
```

Expected response:
```
Service A -> Service B (Order) -> Service C (Inventory): Stock available for order TEST-001
```

**What to check in Grafana:**
1. Open http://localhost:3000
2. Go to Explore → Tempo
3. Search for service name: `service-a`
4. Click on the trace
5. You should see spans for:
   - Service A: GET /api/order/{orderId}
   - Service B: GET /order/{orderId}
   - Service C: GET /inventory/{orderId}

### 3. Async Notification via RabbitMQ

Test async message processing:
```bash
curl -X POST http://localhost:8083/notify \
  -H "Content-Type: application/json" \
  -d '{
    "orderId": "TEST-ASYNC-001",
    "type": "ORDER_UPDATE",
    "status": "PROCESSING",
    "channel": "EMAIL",
    "callbackRequired": true
  }'
```

**What to check:**
1. RabbitMQ Management UI: http://localhost:15672 (guest/guest)
2. Check "Queues" tab for `notification-queue`
3. You should see message count increase and then decrease (consumed)

**In Grafana:**
- Look for spans showing:
  - RabbitMQ message publish
  - RabbitMQ message consume
  - Callback to Service A

### 4. Complete Flow with Circular Dependency

Test the full chain with callback:
```bash
curl http://localhost:8080/api/order/TEST-FULL-001
```

**What to check in Grafana:**
This creates the most complex trace:
1. Service A → Service B → Service C (sync HTTP)
2. Service B → Service D (notification)
3. Service C → Service D (inventory notification)
4. Service D → RabbitMQ (async message)
5. Service D listener processes message
6. Service D → Service A (callback - creates circular dependency)

The trace should show:
- Multiple paths to Service D
- Async processing with proper parent-child relationships
- Circular flow back to Service A

### 5. Load Test - Multiple Concurrent Requests

Test trace isolation:
```bash
for i in {1..10}; do
  curl http://localhost:8080/api/order/LOAD-TEST-$i &
done
wait
echo "Load test complete"
```

**What to check:**
- Each request should have its own distinct trace ID
- No trace mixing between concurrent requests
- All 10 traces should be visible in Grafana

## Viewing Traces in Grafana

### Access Grafana
1. Open http://localhost:3000
2. No login required (anonymous access enabled)

### Search for Traces

**By Service Name:**
1. Click "Explore" (compass icon)
2. Select "Tempo" from datasource dropdown
3. Click "Search" tab
4. Select Service Name: `service-a`
5. Click "Run Query"

**By Trace ID:**
If you have a specific trace ID from logs:
1. Go to "TraceQL" tab
2. Click "Query type" → "Search"
3. Paste trace ID
4. Click "Run Query"

**By Time Range:**
1. Use the time picker (top right)
2. Select last 5/15/30 minutes
3. Search will show all traces in that range

### Understanding the Trace View

**Timeline:**
- Horizontal bars show span duration
- Parent-child relationships indicated by indentation
- Colors represent different services

**Span Details:**
Click any span to see:
- Service name
- Operation name (HTTP method + path)
- Start time and duration
- Tags (http.method, http.status_code, etc.)
- Trace ID and Span ID
- Parent Span ID

**Service Graph:**
Click "Service Graph" button (if available) to see:
- Visual representation of service dependencies
- Request rates between services
- Error rates

## Troubleshooting

### No Traces Appearing

1. **Check service logs:**
```bash
docker logs service-a 2>&1 | grep -i otel
# or for local runs:
tail -f service-a.log | grep -i otel
```

2. **Verify OTLP endpoint is accessible:**
```bash
curl -I http://localhost:4318/v1/traces
```
Should return `HTTP/1.1 405 Method Not Allowed` (POST required)

3. **Check Tempo logs:**
```bash
docker logs tempo
```

4. **Verify sampling rate:**
Check application.properties:
```
management.tracing.sampling.probability=1.0
```
Should be 1.0 for testing (100% sampling)

### Traces Incomplete

1. **Check for errors in service logs**
2. **Verify RabbitMQ is running:**
```bash
curl http://localhost:15672/api/health/checks/alarms
```

3. **Restart services:**
```bash
docker-compose restart service-a service-b service-c service-d
```

### RabbitMQ Connection Issues

1. **Check RabbitMQ health:**
```bash
docker logs rabbitmq | tail -20
```

2. **Verify connection from Service D:**
```bash
docker logs service-d | grep -i rabbit
```

3. **Check queue exists:**
- Open http://localhost:15672
- Login: guest/guest
- Go to "Queues" tab
- Look for `notification-queue`

## Expected Trace Characteristics

### Service A Trace
- Inbound HTTP span: `GET /api/order/{orderId}`
- Outbound HTTP span: Client call to Service B
- Internal spans: validateRequest, prepareOrderMetadata, formatResponse

### Service B Trace
- Inbound HTTP span: `GET /order/{orderId}`
- Outbound HTTP span: Client call to Service C
- Outbound HTTP span: Client call to Service D (notification)
- Internal spans: checkOrderEligibility, calculateOrderAmount, applyBusinessRules

### Service C Trace
- Inbound HTTP span: `GET /inventory/{orderId}`
- Outbound HTTP span: Client call to Service D
- Internal spans: queryDatabase, checkStockLevel, reserveInventory, updateCache

### Service D Trace
- Inbound HTTP span: `POST /notify`
- RabbitMQ publish span
- RabbitMQ consume span (async)
- Outbound HTTP span: Callback to Service A
- Internal spans: validation, template loading, personalization, delivery

## Performance Expectations

- **Trace overhead**: ~1-2ms per span
- **Network latency**: ~5-10ms for local Docker networking
- **Total request time**: ~200-300ms for complete flow
- **Trace availability**: 5-10 seconds after request completes

## Metrics to Monitor

Access actuator endpoints:
```bash
# Prometheus metrics
curl http://localhost:8080/actuator/prometheus

# Metrics summary
curl http://localhost:8080/actuator/metrics

# Specific metric (e.g., HTTP requests)
curl http://localhost:8080/actuator/metrics/http.server.requests
```

## Next Steps

1. ✅ Verify basic synchronous flow works
2. ✅ Verify async RabbitMQ tracing works
3. ✅ Verify circular dependency traces correctly
4. 🔄 Experiment with different trace scenarios
5. 🔄 Add custom spans for specific operations
6. 🔄 Set up alerts based on trace data
7. 🔄 Integrate with logging for correlation

## Additional Resources

- [Grafana Tempo Query Language](https://grafana.com/docs/tempo/latest/traceql/)
- [OpenTelemetry Span Attributes](https://opentelemetry.io/docs/reference/specification/trace/semantic_conventions/)
- [Micrometer Tracing Docs](https://micrometer.io/docs/tracing)

## Success Criteria

✅ All services return healthy status  
✅ Basic HTTP flow creates complete trace  
✅ RabbitMQ messages propagate trace context  
✅ Async processing maintains parent-child relationships  
✅ Circular dependencies don't break traces  
✅ Concurrent requests have distinct trace IDs  
✅ Traces visible in Grafana within 10 seconds  
✅ Service graph shows correct dependencies  

**If all criteria pass, your OpenTelemetry setup is working correctly!** 🎉
