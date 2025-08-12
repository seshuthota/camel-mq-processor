# Partner Configuration API Documentation 🌐

## Overview

The Partner Configuration API provides webhook endpoints for managing partner-specific route configurations. This API enables event-driven configuration updates, replacing the need for continuous Elasticsearch polling.

## Base URL
```
http://localhost:8080/api/v1/partner-config
```

## Authentication
Currently no authentication is required. In production, implement proper API authentication/authorization.

---

## Endpoints

### 1. Configuration Change Webhook

**POST** `/webhook/config-changed`

Webhook endpoint called when partner configuration is updated in Elasticsearch.

#### Request Body
```json
{
  "partnerId": "AMAZON",
  "changeType": "UPDATED",
  "version": "1642678234567",
  "timestamp": 1642678234567,
  "source": "elasticsearch",
  "metadata": {
    "updatedBy": "admin-ui",
    "reason": "timeout configuration change"
  }
}
```

#### Request Fields
| Field | Type | Required | Description |
|-------|------|----------|-------------|
| partnerId | string | ✅ | Business unit identifier (e.g., AMAZON, FLIPKART) |
| changeType | string | ✅ | Type of change: `CREATED`, `UPDATED`, `DELETED` |
| version | string | ❌ | Configuration version identifier |
| timestamp | number | ❌ | Unix timestamp of change |
| source | string | ❌ | Source system that made the change |
| metadata | object | ❌ | Additional metadata about the change |

#### Response
```json
{
  "success": true,
  "message": "Configuration updated and route refreshed for partner: AMAZON",
  "partnerId": "AMAZON",
  "timestamp": 1642678234567
}
```

#### Response Codes
- `200 OK` - Configuration successfully processed
- `400 Bad Request` - Invalid request or partner not found
- `500 Internal Server Error` - Processing error

#### Example Usage
```bash
curl -X POST http://localhost:8080/api/v1/partner-config/webhook/config-changed \
  -H "Content-Type: application/json" \
  -d '{
    "partnerId": "AMAZON",
    "changeType": "UPDATED",
    "timestamp": 1642678234567,
    "source": "admin-ui"
  }'
```

---

### 2. Manual Partner Route Refresh

**POST** `/{partnerId}/refresh`

Manually refresh a specific partner's route configuration.

#### Path Parameters
| Parameter | Type | Description |
|-----------|------|-------------|
| partnerId | string | Business unit identifier |

#### Response
```json
{
  "success": true,
  "message": "Route manually refreshed for partner: AMAZON",
  "partnerId": "AMAZON",
  "timestamp": 1642678234567
}
```

#### Example Usage
```bash
curl -X POST http://localhost:8080/api/v1/partner-config/AMAZON/refresh
```

---

### 3. Refresh All Routes

**POST** `/refresh-all`

Refresh all partner routes with latest configurations.

#### Response
```json
{
  "success": true,
  "message": "All partner routes refreshed successfully",
  "initialRouteCount": 3,
  "finalRouteCount": 3,
  "activeRoutes": ["AMAZON", "FLIPKART", "MYNTRA"]
}
```

#### Example Usage
```bash
curl -X POST http://localhost:8080/api/v1/partner-config/refresh-all
```

---

### 4. Get Route Status

**GET** `/routes/status`

Get status of all partner routes.

#### Response
```json
{
  "success": true,
  "activeRouteCount": 3,
  "activeRoutes": {
    "AMAZON": "Partner:AMAZON:Main",
    "FLIPKART": "Partner:FLIPKART:Main",
    "MYNTRA": "Partner:MYNTRA:Main"
  },
  "timestamp": 1642678234567
}
```

#### Example Usage
```bash
curl -X GET http://localhost:8080/api/v1/partner-config/routes/status
```

---

### 5. Get Partner Configuration

**GET** `/{partnerId}`

Get configuration details for a specific partner.

#### Path Parameters
| Parameter | Type | Description |
|-----------|------|-------------|
| partnerId | string | Business unit identifier |

#### Response
```json
{
  "success": true,
  "partnerId": "AMAZON",
  "configuration": {
    "businessUnit": "AMAZON",
    "coreThreads": 10,
    "maxThreads": 50,
    "queueCapacity": 2000,
    "circuitBreakerFailureThreshold": 60.0,
    "retryMaxAttempts": 5,
    "apiTimeoutSeconds": 30,
    "priority": "HIGH"
  },
  "hasActiveRoute": true,
  "timestamp": 1642678234567
}
```

#### Example Usage
```bash
curl -X GET http://localhost:8080/api/v1/partner-config/AMAZON
```

---

## Integration Examples

### Elasticsearch Watcher Integration

Configure Elasticsearch Watcher to call the webhook on document changes:

```json
{
  "trigger": {
    "schedule": {
      "interval": "30s"
    }
  },
  "input": {
    "search": {
      "request": {
        "search_type": "query_then_fetch",
        "indices": ["partner-configurations"],
        "body": {
          "query": {
            "range": {
              "lastModified": {
                "gte": "now-30s"
              }
            }
          }
        }
      }
    }
  },
  "condition": {
    "compare": {
      "ctx.payload.hits.total": {
        "gt": 0
      }
    }
  },
  "actions": {
    "webhook": {
      "webhook": {
        "scheme": "http",
        "host": "localhost",
        "port": 8080,
        "method": "post",
        "path": "/api/v1/partner-config/webhook/config-changed",
        "params": {},
        "headers": {
          "Content-Type": "application/json"
        },
        "body": """
        {
          "partnerId": "{{ctx.payload.hits.hits.0._source.businessUnit}}",
          "changeType": "UPDATED",
          "version": "{{ctx.payload.hits.hits.0._source.version}}",
          "timestamp": {{ctx.execution_time}},
          "source": "elasticsearch-watcher"
        }
        """
      }
    }
  }
}
```

### Admin UI Integration

Example JavaScript code for calling the webhook from an admin UI:

```javascript
async function notifyConfigurationChange(partnerId, changeType) {
  try {
    const response = await fetch('/api/v1/partner-config/webhook/config-changed', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        partnerId: partnerId,
        changeType: changeType,
        timestamp: Date.now(),
        source: 'admin-ui',
        metadata: {
          updatedBy: getCurrentUser(),
          reason: getChangeReason()
        }
      })
    });

    const result = await response.json();
    
    if (result.success) {
      console.log(`✅ Route updated for partner: ${partnerId}`);
    } else {
      console.error(`❌ Failed to update route: ${result.message}`);
    }
  } catch (error) {
    console.error('❌ Error calling webhook:', error);
  }
}
```

---

## Architecture Flow

```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────────┐
│  Configuration  │    │   Webhook API    │    │  Route Manager      │
│     Change      │───▶│  /config-changed │───▶│  Update Routes      │
│  (Elasticsearch)│    │                  │    │                     │
└─────────────────┘    └──────────────────┘    └─────────────────────┘
                                │                          │
                                ▼                          ▼
                    ┌──────────────────┐    ┌─────────────────────┐
                    │  Reload Config   │    │    Camel Routes     │
                    │  from ES         │    │  partner.X.queue    │
                    └──────────────────┘    └─────────────────────┘
```

## Benefits

1. **🚀 Real-time Updates**: Immediate route refresh on configuration changes
2. **⚡ Efficient**: No continuous polling, only updates when needed
3. **🎯 Targeted**: Only affected partner routes are updated
4. **📊 Auditable**: All configuration changes are logged with metadata
5. **🔧 Flexible**: Support for multiple trigger sources (ES, admin UI, etc.)

## Monitoring

Monitor the API endpoints using the application's metrics:
- `/actuator/metrics/http.server.requests` - HTTP request metrics
- `/actuator/health` - Application health status
- Application logs for configuration change events

## Error Handling

All endpoints include comprehensive error handling and logging:
- Invalid requests return 400 with descriptive messages
- Configuration not found returns 404 with partner context  
- Internal errors return 500 with error details
- All errors are logged with partner context for debugging