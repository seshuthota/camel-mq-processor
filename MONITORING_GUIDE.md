# 📊 Live Data Processing Monitoring Guide

## 🚀 How to See Data Processing at Scale

Your Enhanced Camel MQ Processor provides multiple ways to monitor live data processing at scale. Here's how to see everything in action:

## 1. 🎯 Real-Time Monitoring Dashboard

### Start the Live Dashboard
```bash
# Start the real-time monitoring dashboard
./scripts/monitor-dashboard.sh
```

This shows you:
- **System Health**: Overall status and partner counts
- **Thread Pool Utilization**: Live thread usage per partner
- **Circuit Breaker States**: Real-time failure detection
- **RabbitMQ Queue Stats**: Message flow and processing rates
- **Processing Metrics**: Total messages processed and distribution

### What You'll See:
```
🚀 ENHANCED CAMEL MQ PROCESSOR
   LIVE PROCESSING DASHBOARD
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
⏰ 2025-08-11 18:30:45

🏥 SYSTEM HEALTH OVERVIEW
   System Status: UP
   Total Partners: 8
   Healthy Thread Pools: 8
   Open Circuit Breakers: 0

🧵 THREAD POOL UTILIZATION (TOP 10 ACTIVE)
   AMAZON       Active: 15 Pool: 20 Queue:  150 Completed: 5420
   FLIPKART     Active: 12 Pool: 15 Queue:   89 Completed: 4230
   MYNTRA       Active:  8 Pool: 10 Queue:   45 Completed: 2890
   ...

⚡ CIRCUIT BREAKER STATES
   Summary: CLOSED: 7 | OPEN: 1 | HALF_OPEN: 0
   
   AMAZON       State: CLOSED     Failure Rate:  2.1% Calls: 245 Failed:   5
   FLIPKART     State: CLOSED     Failure Rate:  1.8% Calls: 198 Failed:   3
   BADPARTNER   State: OPEN       Failure Rate: 85.2% Calls:  54 Failed:  46
   ...

📨 RABBITMQ QUEUE STATISTICS
   message.processing.queue     Messages:  234 Ready:  234 Pub Rate:  45.2/s Del Rate:  43.8/s
   partner.amazon.queue         Messages:   89 Ready:   89 Pub Rate:  12.5/s Del Rate:  12.1/s
   ...

📊 MESSAGE PROCESSING METRICS
   Total Messages Processed: 15,420
   Currently Processing: 45
   Messages in Queue: 567
   
   Processing Distribution by Partner:
     AMAZON       [████████████████████] 100% (5420 messages)
     FLIPKART     [███████████████░░░░░] 78% (4230 messages)
     MYNTRA       [██████████░░░░░░░░░░] 53% (2890 messages)
     ...
```

## 2. 🔥 Generate High-Volume Load

### Start Load Generation
```bash
# Interactive load generator
./scripts/generate-load.sh
```

Choose from different load scenarios:
1. **Light Load**: 100 messages/second for 5 minutes
2. **Medium Load**: 500 messages/second for 10 minutes  
3. **Heavy Load**: 1000 messages/second for 15 minutes
4. **Extreme Load**: 2000 messages/second for 20 minutes
5. **Custom Load**: Your own parameters

### What Happens During Load Testing:
- Messages are distributed across all partners
- Each partner processes in their isolated thread pool
- Circuit breakers protect against failures
- Real-time metrics show processing rates
- You can see thread utilization increase

## 3. 📈 API-Based Monitoring

### System Health
```bash
# Overall system health
curl http://localhost:8080/api/monitoring/health | jq

# Response:
{
  "status": "UP",
  "totalPartners": 8,
  "threadPoolsHealthy": 8,
  "circuitBreakersHealthy": 7,
  "circuitBreakersOpen": 1,
  "cacheStats": {
    "totalPartners": 8,
    "totalTokens": 8,
    "totalAuthBodies": 8,
    "totalStatusHeaders": 8
  },
  "timestamp": 1691779845000
}
```

### Thread Pool Statistics
```bash
# All thread pools
curl http://localhost:8080/api/monitoring/threadpools | jq

# Specific partner
curl http://localhost:8080/api/monitoring/threadpools/AMAZON | jq

# Response:
{
  "businessUnit": "AMAZON",
  "activeCount": 15,
  "poolSize": 20,
  "corePoolSize": 20,
  "maximumPoolSize": 100,
  "queueSize": 150,
  "completedTaskCount": 5420,
  "isShutdown": false,
  "isTerminated": false
}
```

### Circuit Breaker States
```bash
# All circuit breakers
curl http://localhost:8080/api/monitoring/circuitbreakers | jq

# Specific partner
curl http://localhost:8080/api/monitoring/circuitbreakers/AMAZON | jq

# Response:
{
  "businessUnit": "AMAZON",
  "state": "CLOSED",
  "failureRate": 2.1,
  "numberOfCalls": 245,
  "numberOfSuccessfulCalls": 240,
  "numberOfFailedCalls": 5,
  "numberOfNotPermittedCalls": 0
}
```

### Partner Overview
```bash
# Complete partner overview
curl http://localhost:8080/api/monitoring/partners | jq

# Specific partner details
curl http://localhost:8080/api/monitoring/partners/AMAZON | jq

# Response:
{
  "threadPool": {
    "businessUnit": "AMAZON",
    "activeCount": 15,
    "poolSize": 20,
    "completedTaskCount": 5420
  },
  "circuitBreaker": {
    "businessUnit": "AMAZON",
    "state": "CLOSED",
    "failureRate": 2.1,
    "numberOfCalls": 245
  },
  "threadPoolHealthy": true,
  "circuitBreakerHealthy": true,
  "overallHealthy": true,
  "timestamp": 1691779845000
}
```

## 4. 🎛️ Grafana Dashboards

### Access Grafana
- **URL**: http://localhost:3000
- **Username**: admin
- **Password**: admin123

### Pre-configured Dashboards:
- **System Overview**: Overall health and performance
- **Partner Performance**: Individual partner metrics
- **Thread Pool Utilization**: Thread usage across partners
- **Circuit Breaker States**: Failure detection and recovery
- **Message Processing Rates**: Throughput and latency metrics

## 5. 📊 Prometheus Metrics

### Access Prometheus
- **URL**: http://localhost:9090

### Key Metrics to Monitor:
```
# Thread pool metrics
partner_threadpool_active{partner="AMAZON"}
partner_threadpool_size{partner="AMAZON"}
partner_threadpool_queue_size{partner="AMAZON"}

# Circuit breaker metrics
resilience4j_circuitbreaker_calls{name="AMAZON",kind="successful"}
resilience4j_circuitbreaker_calls{name="AMAZON",kind="failed"}
resilience4j_circuitbreaker_state{name="AMAZON"}

# Application metrics
http_server_requests_seconds_count
jvm_memory_used_bytes
jvm_threads_live_threads
```

## 6. 🔍 RabbitMQ Management

### Access RabbitMQ Management
- **URL**: http://localhost:15672
- **Username**: admin
- **Password**: admin123

### What to Monitor:
- **Queues**: Message counts and processing rates
- **Exchanges**: Message routing and delivery
- **Connections**: Active connections per partner
- **Channels**: Channel utilization

## 7. 🧪 Simulate Real-World Scenarios

### Partner Failure Simulation
```bash
# Force a partner to fail (circuit breaker opens)
curl -X POST http://localhost:8080/api/monitoring/circuitbreakers/BADPARTNER/force-open

# Watch other partners continue processing normally
./scripts/monitor-dashboard.sh

# Recover the partner
curl -X POST http://localhost:8080/api/monitoring/circuitbreakers/BADPARTNER/force-closed
```

### High-Volume Burst Testing
```bash
# Generate extreme load and watch system handle it
./scripts/generate-load.sh
# Choose option 5: Extreme Load (2000 msg/sec)

# Monitor in real-time
./scripts/monitor-dashboard.sh
```

## 8. 📱 Watch Live Processing

### Terminal-Based Monitoring
```bash
# Watch thread pools in real-time
watch -n 2 'curl -s http://localhost:8080/api/monitoring/threadpools | jq "to_entries[] | {partner: .key, active: .value.activeCount, completed: .value.completedTaskCount}"'

# Watch circuit breaker states
watch -n 5 'curl -s http://localhost:8080/api/monitoring/circuitbreakers | jq "to_entries[] | {partner: .key, state: .value.state, failures: .value.failureRate}"'

# Watch system health
watch -n 3 'curl -s http://localhost:8080/api/monitoring/health | jq'
```

### Log Monitoring
```bash
# Watch application logs
docker-compose logs -f camel-mq-processor

# Filter for specific partner
docker-compose logs -f camel-mq-processor | grep "AMAZON"

# Watch for errors
docker-compose logs -f camel-mq-processor | grep "ERROR"
```

## 9. 🎯 Key Metrics to Watch

### Performance Indicators
- **Thread Pool Utilization**: Should be 70-80% for optimal performance
- **Circuit Breaker States**: Most should be CLOSED (healthy)
- **Message Processing Rate**: Should match your expected throughput
- **Queue Depths**: Should remain low (messages processed quickly)
- **Failure Rates**: Should be <5% for healthy partners

### Health Indicators
- **System Status**: Should be "UP"
- **Partner Isolation**: Failed partners don't affect others
- **Memory Usage**: Should remain stable under load
- **Response Times**: Should be <500ms average

## 10. 🚨 Alerting and Notifications

### Set Up Alerts
```bash
# Monitor for high failure rates
curl -s http://localhost:8080/api/monitoring/circuitbreakers | jq '.[] | select(.failureRate > 50)'

# Monitor for thread pool exhaustion
curl -s http://localhost:8080/api/monitoring/threadpools | jq '.[] | select(.activeCount >= .maximumPoolSize * 0.9)'

# Monitor for high queue depths
curl -s http://localhost:8080/api/monitoring/threadpools | jq '.[] | select(.queueSize > 1000)'
```

## 🎉 What You'll See in Action

When you run the monitoring tools, you'll see:

1. **Complete Partner Isolation**: Each partner processing independently
2. **Circuit Breaker Protection**: Failed partners automatically isolated
3. **Real-Time Metrics**: Live updates of processing rates and health
4. **Scalable Performance**: System handling thousands of messages per second
5. **Zero Cross-Partner Blocking**: Healthy partners unaffected by failures

**The thread blocking nightmare is officially OVER!** 🎯💪

Your system now processes messages at scale with complete partner isolation and real-time visibility into every aspect of the operation!