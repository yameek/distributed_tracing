# Quick Start Guide - OpenTelemetry Distributed Tracing

Get up and running with OpenTelemetry distributed tracing in 5 minutes!

## Prerequisites Check

```bash
# Verify Docker is installed
docker --version
# Expected: Docker version 20.x or higher

# Verify Docker Compose is installed
docker-compose --version
# Expected: docker-compose version 1.29.x or higher

# Verify Java is installed (for local development)
java -version
# Expected: openjdk version "17.x"

# Verify Maven is installed (for local development)
mvn -version
# Expected: Apache Maven 3.6.x or higher
```

## Option 1: Docker Compose (Fastest - 2 minutes)

Perfect for quick testing and demos.

```bash
# Step 1: Start everything
docker-compose up -d

# Step 2: Wait for services to be ready (30 seconds)
sleep 30

# Step 3: Test the system
curl http://localhost:8080/api/order/QUICK-TEST-001

# Step 4: View traces
# Open browser: http://localhost:3000
# Go to Explore → Tempo → Search for "service-a"
```

**Expected Output:**
```
Service A -> Service B (Order) -> Service C (Inventory): Stock available for order QUICK-TEST-001
```

### View Traces in Grafana

1. Open http://localhost:3000 in your browser
2. Click "Explore" icon (compass) on left sidebar
3. Ensure "Tempo" is selected in datasource dropdown
4. Click "Search" tab
5. Select Service: "service-a"
6. Click "Run Query"
7. Click on any trace to see the complete flow

### Stop Everything

```bash
docker-compose down
```

## Option 2: Local Development (5 minutes)

Best for development and debugging.

### Step 1: Start Infrastructure Only

```bash
# Start Tempo, Grafana, and RabbitMQ
docker-compose up -d tempo grafana rabbitmq

# Wait for infrastructure to be ready
sleep 10
```

### Step 2: Build Services

```bash
# Use the provided script
./rebuild-all.sh

# OR build and run manually:
cd service-d && mvn spring-boot:run &
sleep 5
cd service-c && mvn spring-boot:run &
sleep 5
cd service-b && mvn spring-boot:run &
sleep 5
cd service-a && mvn spring-boot:run &
```

### Step 3: Test

```bash
# Wait for all services to start (20 seconds)
sleep 20

# Test the system
curl http://localhost:8080/api/order/LOCAL-TEST-001
```

### Step 4: View Traces

Same as Option 1 - open http://localhost:3000 and explore traces.

### Stop Services

```bash
# Stop all Spring Boot services
pkill -f spring-boot:run

# Stop infrastructure
docker-compose down
```

## What You Should See

### In Terminal (curl response)
```
Service A -> Service B (Order) -> Service C (Inventory): Stock available for order QUICK-TEST-001
```

### In Grafana (Trace View)

**Timeline View:**
```
[Service A: GET /api/order/{orderId}                    ] 200ms
  [Service B: GET /order/{orderId}                     ] 150ms
    [Service C: GET /inventory/{orderId}               ] 100ms
      [Service D: POST /notify                         ] 50ms
        [RabbitMQ: Publish                             ] 5ms
        [RabbitMQ: Consume                             ] 5ms
          [Service D: processNotification              ] 30ms
            [Service A: GET /api/callback/{orderId}    ] 20ms
```

**You should see:**
- ✅ 8+ spans in a single trace
- ✅ Parent-child relationships (indentation)
- ✅ Service names and operation names
- ✅ Duration for each span
- ✅ HTTP status codes (200)
- ✅ Trace ID linking all spans

## Troubleshooting Quick Fixes

### Problem: "Connection refused" when accessing services

**Solution:**
```bash
# Check if services are running
docker-compose ps
# OR
ps aux | grep spring-boot

# Restart services
docker-compose restart
# OR
./rebuild-all.sh
```

### Problem: No traces appear in Grafana

**Solution:**
```bash
# 1. Wait 10-30 seconds after making request
sleep 10

# 2. Check Tempo is running
docker logs tempo

# 3. Verify OTLP endpoint
curl -I http://localhost:4318/v1/traces
# Should return: HTTP/1.1 405 Method Not Allowed (this is OK!)

# 4. Check service logs
docker logs service-a
# Look for OpenTelemetry initialization messages
```

### Problem: RabbitMQ connection errors

**Solution:**
```bash
# Check RabbitMQ is running and healthy
docker-compose ps rabbitmq
# Status should be "Up (healthy)"

# Check RabbitMQ logs
docker logs rabbitmq

# Restart RabbitMQ
docker-compose restart rabbitmq
```

### Problem: Port conflicts

**Solution:**
```bash
# Check which process is using the port
lsof -i :8080  # or :8081, :8082, :8083

# Kill the process or use different ports
# Edit docker-compose.yml to change port mappings
```

## Test Scenarios

### Scenario 1: Basic Flow (A → B → C)
```bash
curl http://localhost:8080/api/order/TEST-001
```
**Trace shows:** 3 services with HTTP spans

### Scenario 2: With Notifications (includes RabbitMQ)
```bash
curl http://localhost:8080/api/order/TEST-002
```
**Trace shows:** 4 services + RabbitMQ spans + callback

### Scenario 3: Direct Notification
```bash
curl -X POST http://localhost:8083/notify \
  -H "Content-Type: application/json" \
  -d '{"orderId":"TEST-003","type":"ORDER_UPDATE","status":"PROCESSING","channel":"EMAIL","callbackRequired":true}'
```
**Trace shows:** RabbitMQ async processing + callback

### Scenario 4: Load Test
```bash
for i in {1..5}; do
  curl http://localhost:8080/api/order/LOAD-$i &
done
wait
```
**Trace shows:** 5 separate traces with unique trace IDs

## Access Points

| Service | URL | Purpose |
|---------|-----|---------|
| Service A | http://localhost:8080/api/order/{id} | API Gateway |
| Service B | http://localhost:8081/order/{id} | Order Service |
| Service C | http://localhost:8082/inventory/{id} | Inventory |
| Service D | http://localhost:8083/notify | Notifications |
| Grafana | http://localhost:3000 | Trace UI |
| RabbitMQ | http://localhost:15672 | Queue Admin (guest/guest) |
| Tempo | http://localhost:3200 | Trace API |

## Health Checks

```bash
# Check all services are healthy
curl http://localhost:8080/actuator/health  # Service A
curl http://localhost:8081/actuator/health  # Service B
curl http://localhost:8082/actuator/health  # Service C
curl http://localhost:8083/actuator/health  # Service D

# All should return: {"status":"UP"}
```

## Next Steps

Once you have traces appearing:

1. **Explore different views in Grafana:**
   - Timeline view (default)
   - Service Graph (if available)
   - Span details

2. **Read detailed documentation:**
   - [README-OTEL.md](./README-OTEL.md) - Complete setup guide
   - [TESTING.md](./TESTING.md) - Test scenarios
   - [IMPLEMENTATION_SUMMARY.md](./IMPLEMENTATION_SUMMARY.md) - Technical details

3. **Experiment with configuration:**
   - Change sampling rate (application.properties)
   - Add custom spans
   - Configure different backends

4. **Add more observability:**
   - Integrate Prometheus for metrics
   - Add Loki for logs
   - Create dashboards

## Common Commands

```bash
# View logs
docker-compose logs -f service-a
docker-compose logs -f tempo
tail -f service-a.log  # For local runs

# Restart a service
docker-compose restart service-a

# Rebuild a service
docker-compose up -d --build service-a

# Clean up everything
docker-compose down -v  # -v removes volumes too
rm -f service-*.log     # Remove log files
```

## Success Indicators

✅ All services return HTTP 200  
✅ Trace appears in Grafana within 10 seconds  
✅ Complete trace shows 8+ spans  
✅ RabbitMQ message appears in queue  
✅ Callback to Service A completes  
✅ No errors in service logs  
✅ Service graph shows all dependencies  

**If you see all of these, congratulations! Your distributed tracing is working perfectly!** 🎉

## Getting Help

If you encounter issues:

1. Check the [TESTING.md](./TESTING.md) troubleshooting section
2. Review service logs: `docker-compose logs`
3. Verify infrastructure: `docker-compose ps`
4. Check [IMPLEMENTATION_SUMMARY.md](./IMPLEMENTATION_SUMMARY.md) for technical details

## Configuration Reference

**Sampling (application.properties):**
```properties
management.tracing.sampling.probability=1.0  # 100% (for testing)
# Change to 0.1 for 10% in production
```

**OTLP Endpoint:**
```properties
management.otlp.tracing.endpoint=http://localhost:4318/v1/traces
# Change localhost to tempo hostname in Docker
```

**RabbitMQ (Service D only):**
```properties
spring.rabbitmq.host=localhost
spring.rabbitmq.port=5672
```

---

**Time to first trace: ~2 minutes with Docker Compose!**

Happy tracing! 🚀
