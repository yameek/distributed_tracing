#!/bin/bash

set -e

echo "========================================="
echo "Rebuilding All Microservices"
echo "========================================="

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

SERVICES=("service-a" "service-b" "service-c" "service-d")

# Stop all running services
echo -e "${YELLOW}Stopping all running services...${NC}"
for service in "${SERVICES[@]}"; do
    echo "Stopping $service..."
    pkill -f "spring-boot:run.*$service" || true
done

# Kill Zipkin Docker container
echo -e "${YELLOW}Stopping Zipkin container...${NC}"
docker stop zipkin 2>/dev/null || true
docker rm zipkin 2>/dev/null || true

# Wait for ports to be released
sleep 3

# Start Zipkin
echo -e "${YELLOW}Starting Zipkin...${NC}"
docker run -d --name zipkin -p 9411:9411 openzipkin/zipkin

# Use existing RabbitMQ
echo -e "${YELLOW}Using existing RabbitMQ instance...${NC}"

# Wait for services to be ready
echo -e "${YELLOW}Waiting for Zipkin to start...${NC}"
sleep 5

# Build all services
for service in "${SERVICES[@]}"; do
    echo -e "${YELLOW}Building $service...${NC}"
    cd "$service"
    mvn clean package -DskipTests
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✓ $service built successfully${NC}"
    else
        echo -e "${RED}✗ Failed to build $service${NC}"
        exit 1
    fi
    cd ..
done

# Start all services
echo -e "${YELLOW}Starting all services...${NC}"

for service in "${SERVICES[@]}"; do
    echo "Starting $service..."
    cd "$service"
    mvn spring-boot:run > "../${service}.log" 2>&1 &
    cd ..
    sleep 5
done

echo ""
echo -e "${GREEN}=========================================${NC}"
echo -e "${GREEN}All services started successfully!${NC}"
echo -e "${GREEN}=========================================${NC}"
echo ""
echo "Service A (API Gateway): http://localhost:8080"
echo "Service B (Order Service): http://localhost:8081"
echo "Service C (Inventory Service): http://localhost:8082"
echo "Service D (Notification Service): http://localhost:8083"
echo "Zipkin UI: http://localhost:9411"
echo "RabbitMQ Management: http://localhost:15672 (user: guest, password: guest)"
echo ""
echo "Logs are available in:"
echo "  - service-a.log"
echo "  - service-b.log"
echo "  - service-c.log"
echo "  - service-d.log"
echo ""
echo -e "${YELLOW}Waiting for all services to be ready...${NC}"
sleep 20

echo ""
echo -e "${GREEN}Testing services...${NC}"
curl -s http://localhost:8080/actuator/health > /dev/null && echo -e "${GREEN}✓ Service A is healthy${NC}" || echo -e "${RED}✗ Service A is not responding${NC}"
curl -s http://localhost:8081/actuator/health > /dev/null && echo -e "${GREEN}✓ Service B is healthy${NC}" || echo -e "${RED}✗ Service B is not responding${NC}"
curl -s http://localhost:8082/actuator/health > /dev/null && echo -e "${GREEN}✓ Service C is healthy${NC}" || echo -e "${RED}✗ Service C is not responding${NC}"
curl -s http://localhost:8083/actuator/health > /dev/null && echo -e "${GREEN}✓ Service D is healthy${NC}" || echo -e "${RED}✗ Service D is not responding${NC}"

echo ""
echo -e "${GREEN}Ready to trace! Try: curl http://localhost:8080/api/order/12345${NC}"
