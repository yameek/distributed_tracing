#!/bin/bash

# Test Error Scenarios for Distributed Tracing
# This script tests various error cases and shows how they appear in Zipkin

echo "========================================="
echo "Testing Error Scenarios in Zipkin"
echo "========================================="
echo ""

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to test an error scenario
test_error() {
    local order_id=$1
    local error_name=$2
    local expected_status=$3
    
    echo -e "${YELLOW}Testing: ${error_name}${NC}"
    echo "Order ID: ${order_id}"
    
    response=$(curl -s -w "\n%{http_code}" http://localhost:8080/api/order/${order_id})
    http_code=$(echo "$response" | tail -n1)
    body=$(echo "$response" | head -n-1)
    
    echo "HTTP Status: ${http_code}"
    echo "Response: ${body}"
    
    if [ "$http_code" == "$expected_status" ]; then
        echo -e "${GREEN}✓ Error case working as expected${NC}"
    else
        echo -e "${RED}✗ Unexpected status code${NC}"
    fi
    
    echo ""
    sleep 1
}

# Test successful case first
echo -e "${GREEN}1. Testing Successful Case (Baseline)${NC}"
echo "========================================="
test_error "success-001" "Successful Order" "200"

# Test error scenarios
echo -e "${RED}2. Testing Timeout Error${NC}"
echo "========================================="
test_error "timeout-order" "Request Timeout" "500"

echo -e "${RED}3. Testing Validation Error${NC}"
echo "========================================="
test_error "invalid-order" "Invalid Order Format" "500"

echo -e "${RED}4. Testing Not Found Error${NC}"
echo "========================================="
test_error "not-found-order" "Order Not Found" "500"

echo -e "${RED}5. Testing Database Error${NC}"
echo "========================================="
test_error "db-error-order" "Database Connection Failed" "500"

# Wait for traces to be reported to Zipkin
echo ""
echo "========================================="
echo "Waiting for traces to be sent to Zipkin..."
echo "========================================="
sleep 5

# Query Zipkin for error traces
echo ""
echo "========================================="
echo "Error Traces in Zipkin:"
echo "========================================="
echo ""

error_traces=$(curl -s 'http://localhost:9411/api/v2/traces?limit=20' | \
    jq '[.[] | {traceId: .[0].traceId, hasError: ([.[] | .tags.error] | any(. == "true")), services: [.[] | .localEndpoint.serviceName] | unique}] | map(select(.hasError == true)) | length')

echo "Total error traces found: ${error_traces}"
echo ""

# Get details of error traces
echo "Error trace details:"
curl -s 'http://localhost:9411/api/v2/traces?limit=20' | \
    jq '[.[] | select([.[] | .tags.error] | any(. == "true")) | {
        traceId: .[0].traceId,
        timestamp: .[0].timestamp,
        errors: [.[] | select(.tags.error == "true") | {
            service: .localEndpoint.serviceName,
            errorType: .tags."error.type",
            errorMessage: .tags."error.message"
        }]
    }] | .[:5]'

echo ""
echo "========================================="
echo "Testing Complete!"
echo "========================================="
echo ""
echo "View all traces at: http://localhost:9411"
echo "Filter error traces: Add tag filter 'error=true' in Zipkin UI"
echo ""
