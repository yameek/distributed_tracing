# Error Tracing in Zipkin - Complete Guide

## Overview

This guide demonstrates how failed requests and exceptions are automatically captured and traced in Zipkin, allowing you to debug issues across distributed microservices.

## What Gets Traced During Failures

When an error occurs in your microservices:
1. **Error tags** are automatically added to spans
2. **Failed spans are marked in red** in Zipkin UI
3. **Complete trace context is preserved** across service boundaries
4. **Error details are searchable** in Zipkin

## Error Scenarios Implemented

### 1. Timeout Error
**Trigger:** `http://localhost:8080/api/order/timeout-order`

**Error Tags:**
- `error: true`
- `error.type: timeout`
- `error.message: Order processing timeout exceeded`

**HTTP Status:** 408 Request Timeout

**Use Case:** Simulates when an operation takes too long to complete.

---

### 2. Validation Error
**Trigger:** `http://localhost:8080/api/order/invalid-order`

**Error Tags:**
- `error: true`
- `error.type: validation`
- `error.message: Invalid order ID format`

**HTTP Status:** 400 Bad Request

**Use Case:** Simulates client-side validation failures.

---

### 3. Not Found Error
**Trigger:** `http://localhost:8080/api/order/not-found-order`

**Error Tags:**
- `error: true`
- `error.type: not_found`
- `error.message: Order not found in database`

**HTTP Status:** 404 Not Found

**Use Case:** Simulates resource not found scenarios.

---

### 4. Database Error
**Trigger:** `http://localhost:8080/api/order/db-error-order`

**Error Tags:**
- `error: true`
- `error.type: database`
- `error.message: Database connection failed`

**HTTP Status:** 500 Internal Server Error

**Use Case:** Simulates database connectivity or query failures.

---

## Testing Error Scenarios

### Manual Testing

```bash
# Test timeout
curl -i http://localhost:8080/api/order/timeout-order

# Test validation error
curl -i http://localhost:8080/api/order/invalid-order

# Test not found
curl -i http://localhost:8080/api/order/not-found-order

# Test database error
curl -i http://localhost:8080/api/order/db-error-order

# Test successful case (baseline)
curl http://localhost:8080/api/order/success-123
```

### Automated Testing

Run the provided test script:

```bash
./test-error-scenarios.sh
```

This script will:
1. Test all error scenarios
2. Query Zipkin for error traces
3. Display error details
4. Show success/failure status

---

## Viewing Error Traces in Zipkin

### Method 1: Zipkin Web UI

1. **Open Zipkin:** http://localhost:9411

2. **Filter by error tags:**
   - Click "Add criteria"
   - Select "Tags"
   - Enter: `error=true`
   - Click "Run Query"

3. **Visual indicators:**
   - Failed spans appear in **red** in timeline
   - Error icon (⚠️) next to affected services
   - Error details in span tags

4. **View error details:**
   - Click on a red span
   - Scroll to "Tags" section
   - See `error`, `error.type`, `error.message`

### Method 2: Zipkin API

**Get all error traces:**
```bash
curl -s 'http://localhost:9411/api/v2/traces?annotationQuery=error' | jq .
```

**Get error traces for a specific service:**
```bash
curl -s 'http://localhost:9411/api/v2/traces?serviceName=service-b&annotationQuery=error' | jq .
```

**Get detailed error information:**
```bash
curl -s 'http://localhost:9411/api/v2/traces?limit=10' | \
  jq '[.[] | select([.[] | .tags.error] | any(. == "true")) | {
    traceId: .[0].traceId,
    timestamp: .[0].timestamp,
    errors: [.[] | select(.tags.error == "true") | {
      service: .localEndpoint.serviceName,
      errorType: .tags."error.type",
      errorMessage: .tags."error.message"
    }]
  }]'
```

**Get specific trace by ID:**
```bash
curl -s 'http://localhost:9411/api/v2/trace/{traceId}' | \
  jq '[.[] | select(.tags.error == "true") | {
    service: .localEndpoint.serviceName,
    operation: .name,
    error: .tags.error,
    errorType: .tags."error.type",
    errorMessage: .tags."error.message"
  }]'
```

---

## Implementation Details

### Service B Controller (Error Source)

The `ServiceBController` includes error simulation:

```java
@GetMapping("/order/{orderId}")
public String processOrder(@PathVariable String orderId) {
    // Timeout scenario
    if (orderId.equals("timeout-order")) {
        logger.error("Service B: Timeout processing order {}", orderId);
        tagError("timeout", "Order processing timeout exceeded");
        throw new ResponseStatusException(
            HttpStatus.REQUEST_TIMEOUT, 
            "Order processing timeout exceeded"
        );
    }
    
    // Validation scenario
    if (orderId.equals("invalid-order")) {
        logger.error("Service B: Invalid order format {}", orderId);
        tagError("validation", "Invalid order ID format");
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, 
            "Invalid order ID format"
        );
    }
    
    // ... other error scenarios
    
    // Normal processing
    return processOrderNormally(orderId);
}

private void tagError(String errorType, String errorMessage) {
    if (tracer.currentSpan() != null) {
        tracer.currentSpan().tag("error", "true");
        tracer.currentSpan().tag("error.type", errorType);
        tracer.currentSpan().tag("error.message", errorMessage);
    }
}
```

### Key Components

1. **Tracer Injection:**
   ```java
   @Autowired
   private Tracer tracer;
   ```

2. **Error Tagging:**
   ```java
   tracer.currentSpan().tag("error", "true");
   tracer.currentSpan().tag("error.type", "validation");
   tracer.currentSpan().tag("error.message", "Invalid input");
   ```

3. **HTTP Status Exceptions:**
   ```java
   throw new ResponseStatusException(
       HttpStatus.BAD_REQUEST, 
       "Error message"
   );
   ```

---

## Error Flow Visualization

### Successful Request Flow
```
Client → Service A → Service B → Service C
  ✓         ✓           ✓           ✓
```
All spans are green in Zipkin.

### Failed Request Flow (Database Error)
```
Client → Service A → Service B → ✗ (DB Error)
  ✓         ✓           ✗
```
Service B span is red with error tags.

### Failed Request with Partial Success
```
Client → Service A → Service B → Service C
  ✓         ✓           ✗           ✓
```
Service B fails but trace continues (if error is handled).

---

## Benefits of Error Tracing

### 1. **Quick Error Identification**
- See exactly which service failed
- Identify error type at a glance
- View full request context

### 2. **Root Cause Analysis**
- Trace error propagation across services
- See timing of failure in request lifecycle
- Correlate with other spans in the trace

### 3. **Debugging Support**
- Filter traces by error type
- Search for specific error messages
- Compare failed vs successful traces

### 4. **Monitoring & Alerting**
- Track error rates by service
- Set up alerts on specific error types
- Identify error patterns and trends

---

## Best Practices

### 1. **Use Descriptive Error Types**
```java
// ✓ Good - specific error type
tracer.currentSpan().tag("error.type", "database_connection");
tracer.currentSpan().tag("error.type", "validation_failure");

// ✗ Bad - generic error type
tracer.currentSpan().tag("error.type", "error");
```

### 2. **Include Error Context**
```java
// ✓ Good - includes context
tracer.currentSpan().tag("error.message", "Database connection timeout after 30s");
tracer.currentSpan().tag("error.service", "postgres");
tracer.currentSpan().tag("error.code", "CONNECTION_TIMEOUT");

// ✗ Bad - no context
tracer.currentSpan().tag("error.message", "Error");
```

### 3. **Sanitize Error Messages**
```java
// ✓ Good - no sensitive data
tracer.currentSpan().tag("error.message", "Authentication failed");

// ✗ Bad - exposes sensitive data
tracer.currentSpan().tag("error.message", "Invalid password: " + password);
```

### 4. **Log with Trace IDs**
```java
logger.error("Service failed for order {} [traceId: {}, spanId: {}]", 
    orderId, 
    tracer.currentSpan().context().traceId(),
    tracer.currentSpan().context().spanId(),
    exception);
```

### 5. **Use HTTP Status Codes**
```java
if (tracer.currentSpan() != null) {
    tracer.currentSpan().tag("http.status_code", "503");
    tracer.currentSpan().tag("error", "true");
}
```

---

## Error Statistics Example

After running the test script, you might see:

```
Total Traces: 50
Error Traces: 8 (16%)

Error Breakdown:
- Database Errors: 3 (37.5%)
- Validation Errors: 2 (25%)
- Timeout Errors: 2 (25%)
- Not Found Errors: 1 (12.5%)
```

---

## Advanced: Global Exception Handler

For production applications, implement a global exception handler:

```java
@ControllerAdvice
public class GlobalExceptionHandler {
    
    @Autowired
    private Tracer tracer;
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception ex) {
        // Tag current span with error
        if (tracer.currentSpan() != null) {
            tracer.currentSpan().tag("error", "true");
            tracer.currentSpan().tag("error.type", ex.getClass().getSimpleName());
            tracer.currentSpan().tag("error.message", ex.getMessage());
        }
        
        // Return error response
        ErrorResponse error = new ErrorResponse(
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            ex.getMessage(),
            tracer.currentSpan().context().traceId()
        );
        
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(error);
    }
}
```

---

## Troubleshooting

### Issue: Error tags not appearing in Zipkin

**Solution:**
1. Check tracer is autowired correctly
2. Verify span exists before tagging:
   ```java
   if (tracer.currentSpan() != null) {
       tracer.currentSpan().tag("error", "true");
   }
   ```
3. Ensure Zipkin endpoint is reachable

### Issue: Error traces not showing in red

**Solution:**
- Ensure `error` tag is set to string `"true"` (not boolean)
- Update Zipkin to latest version

---

## Summary

✅ **Error tracing is now fully implemented:**
- Multiple error scenarios (timeout, validation, not found, database)
- Automatic error tagging with meaningful metadata
- Visual indicators in Zipkin UI (red spans)
- Searchable error traces via UI and API
- Test script for automated error testing
- Best practices for production implementation

**Next Steps:**
1. Test all error scenarios: `./test-error-scenarios.sh`
2. View errors in Zipkin UI: http://localhost:9411
3. Implement global exception handler for production
4. Add custom error types for your specific use cases
5. Set up monitoring alerts based on error tags

---

**Related Files:**
- Implementation: `service-b/src/main/java/com/example/serviceb/ServiceBController.java`
- Test Script: `test-error-scenarios.sh`
- Main Guide: `ZIPKIN_IMPLEMENTATION_GUIDE.md`
