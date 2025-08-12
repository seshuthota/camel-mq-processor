#!/bin/bash

# High-Volume Message Generator for Enhanced Camel MQ Processor
# Simulates real-world message processing at scale

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

print_status() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

print_header() {
    echo -e "${BLUE}========================================${NC}"
    echo -e "${BLUE} $1${NC}"
    echo -e "${BLUE}========================================${NC}"
}

# Configuration
RABBITMQ_HOST="localhost"
RABBITMQ_PORT="15672"
RABBITMQ_USER="admin"
RABBITMQ_PASS="admin123"
RABBITMQ_VHOST="%2F"

# Partner configurations with different volumes
declare -A PARTNERS=(
    ["AMAZON"]=1000      # High volume partner
    ["FLIPKART"]=800     # High volume partner
    ["MYNTRA"]=600       # Medium volume partner
    ["MEESHO"]=400       # Medium volume partner
    ["SNAPDEAL"]=300     # Low volume partner
    ["SHOPCLUES"]=200    # Low volume partner
    ["PAYTMMALL"]=150    # Low volume partner
    ["TATACLIQ"]=100     # Low volume partner
)

# Message types for realistic simulation
MESSAGE_TYPES=("ORDER_CREATED" "ORDER_UPDATED" "ORDER_SHIPPED" "ORDER_DELIVERED" "ORDER_CANCELLED" "INVENTORY_UPDATE" "PRICE_UPDATE" "PAYMENT_NOTIFICATION")

# Generate realistic message payload
generate_message_payload() {
    local partner=$1
    local msg_id=$2
    local msg_type=${MESSAGE_TYPES[$RANDOM % ${#MESSAGE_TYPES[@]}]}
    
    cat <<EOF
{
  "messageId": "MSG-${partner}-${msg_id}-$(date +%s)",
  "messageType": "${msg_type}",
  "timestamp": "$(date -Iseconds)",
  "partner": "${partner}",
  "data": {
    "orderId": "ORD-${partner}-${msg_id}",
    "customerId": "CUST-$(($RANDOM % 10000))",
    "productSKU": "SKU-$(($RANDOM % 50000))",
    "amount": $((50 + $RANDOM % 500)).$(($RANDOM % 100)),
    "currency": "INR",
    "status": "PROCESSING",
    "priority": "$([ $((RANDOM % 10)) -lt 3 ] && echo "HIGH" || echo "NORMAL")"
  }
}
EOF
}

# Send message to RabbitMQ
send_message() {
    local partner=$1
    local message_payload=$2
    
    curl -s -X POST "http://${RABBITMQ_HOST}:${RABBITMQ_PORT}/api/exchanges/${RABBITMQ_VHOST}/message.processing.exchange/publish" \
         -u "${RABBITMQ_USER}:${RABBITMQ_PASS}" \
         -H "Content-Type: application/json" \
         -d "{
           \"properties\": {
             \"headers\": {
               \"CBUSINESSUNIT\": \"${partner}\",
               \"messageType\": \"LIVE_PROCESSING\",
               \"timestamp\": \"$(date -Iseconds)\",
               \"correlationId\": \"CORR-${partner}-$(date +%s)-${RANDOM}\"
             },
             \"delivery_mode\": 2
           },
           \"routing_key\": \"message.process\",
           \"payload\": $(echo "$message_payload" | jq -c .),
           \"payload_encoding\": \"string\"
         }" > /dev/null
}

# Monitor system metrics in real-time
start_monitoring() {
    print_header "Starting Real-Time Monitoring"
    
    # Create monitoring script
    cat > /tmp/monitor_system.sh << 'EOF'
#!/bin/bash
while true; do
    clear
    echo "🚀 ENHANCED CAMEL MQ PROCESSOR - LIVE MONITORING"
    echo "=================================================="
    echo "⏰ $(date)"
    echo ""
    
    # System Health
    echo "🏥 SYSTEM HEALTH:"
    health=$(curl -s http://localhost:8080/api/monitoring/health 2>/dev/null)
    if [ $? -eq 0 ]; then
        echo "   Status: $(echo $health | jq -r '.status // "UNKNOWN"')"
        echo "   Partners: $(echo $health | jq -r '.totalPartners // "N/A"')"
        echo "   Healthy Thread Pools: $(echo $health | jq -r '.threadPoolsHealthy // "N/A"')"
        echo "   Circuit Breakers Open: $(echo $health | jq -r '.circuitBreakersOpen // "N/A"')"
    else
        echo "   Status: UNAVAILABLE"
    fi
    echo ""
    
    # Thread Pool Stats
    echo "🧵 THREAD POOL UTILIZATION:"
    pools=$(curl -s http://localhost:8080/api/monitoring/threadpools 2>/dev/null)
    if [ $? -eq 0 ]; then
        echo $pools | jq -r 'to_entries[] | "   \(.key): Active=\(.value.activeCount), Pool=\(.value.poolSize), Queue=\(.value.queueSize), Completed=\(.value.completedTaskCount)"' | head -8
    else
        echo "   Thread pool data unavailable"
    fi
    echo ""
    
    # Circuit Breaker States
    echo "⚡ CIRCUIT BREAKER STATES:"
    breakers=$(curl -s http://localhost:8080/api/monitoring/circuitbreakers 2>/dev/null)
    if [ $? -eq 0 ]; then
        echo $breakers | jq -r 'to_entries[] | "   \(.key): \(.value.state) (Failure Rate: \(.value.failureRate)%, Calls: \(.value.numberOfCalls))"' | head -8
    else
        echo "   Circuit breaker data unavailable"
    fi
    echo ""
    
    # RabbitMQ Queue Stats
    echo "📨 RABBITMQ QUEUE STATS:"
    queue_stats=$(curl -s -u admin:admin123 http://localhost:15672/api/queues 2>/dev/null)
    if [ $? -eq 0 ]; then
        echo $queue_stats | jq -r '.[] | select(.name | contains("queue")) | "   \(.name): Messages=\(.messages), Ready=\(.messages_ready), Rate=\(.message_stats.publish_details.rate // 0)/sec"' | head -5
    else
        echo "   Queue data unavailable"
    fi
    echo ""
    
    echo "Press Ctrl+C to stop monitoring..."
    sleep 5
done
EOF
    
    chmod +x /tmp/monitor_system.sh
    
    # Start monitoring in background
    gnome-terminal -- bash -c "/tmp/monitor_system.sh" 2>/dev/null || \
    xterm -e "/tmp/monitor_system.sh" 2>/dev/null || \
    echo "Please run: /tmp/monitor_system.sh in a separate terminal to see live monitoring"
}

# Generate high-volume load
generate_high_volume_load() {
    local duration_minutes=$1
    local messages_per_second=$2
    
    print_header "Generating High-Volume Load"
    print_status "Duration: ${duration_minutes} minutes"
    print_status "Target Rate: ${messages_per_second} messages/second"
    print_status "Total Messages: $((duration_minutes * 60 * messages_per_second))"
    
    local end_time=$(($(date +%s) + duration_minutes * 60))
    local message_count=0
    local batch_size=10
    
    while [ $(date +%s) -lt $end_time ]; do
        local batch_start=$(date +%s%3N)
        
        # Send batch of messages
        for i in $(seq 1 $batch_size); do
            # Select random partner based on volume weights
            local partner_keys=(${!PARTNERS[@]})
            local partner=${partner_keys[$RANDOM % ${#partner_keys[@]}]}
            
            # Generate and send message
            local payload=$(generate_message_payload "$partner" "$message_count")
            send_message "$partner" "$payload" &
            
            ((message_count++))
        done
        
        # Wait for batch to complete
        wait
        
        # Calculate timing for rate limiting
        local batch_end=$(date +%s%3N)
        local batch_duration=$((batch_end - batch_start))
        local target_duration=$((batch_size * 1000 / messages_per_second))
        
        if [ $batch_duration -lt $target_duration ]; then
            sleep $(echo "scale=3; ($target_duration - $batch_duration) / 1000" | bc -l) 2>/dev/null || sleep 0.1
        fi
        
        # Progress update
        if [ $((message_count % 100)) -eq 0 ]; then
            local elapsed=$(($(date +%s) - (end_time - duration_minutes * 60)))
            local rate=$((message_count / (elapsed + 1)))
            print_status "Sent $message_count messages (${rate}/sec avg)"
        fi
    done
    
    print_status "Load generation complete! Sent $message_count messages"
}

# Simulate partner failures for testing
simulate_partner_failures() {
    print_header "Simulating Partner Failures"
    
    # Force some circuit breakers open to simulate failures
    local failing_partners=("SHOPCLUES" "PAYTMMALL")
    
    for partner in "${failing_partners[@]}"; do
        print_status "Simulating failure for $partner..."
        curl -s -X POST "http://localhost:8080/api/monitoring/circuitbreakers/${partner}/force-open" > /dev/null
        print_status "$partner circuit breaker forced OPEN"
    done
    
    print_status "Partner failures simulated. Check monitoring to see isolation in action!"
    
    # Wait and then recover
    sleep 30
    
    print_status "Recovering failed partners..."
    for partner in "${failing_partners[@]}"; do
        curl -s -X POST "http://localhost:8080/api/monitoring/circuitbreakers/${partner}/force-closed" > /dev/null
        print_status "$partner circuit breaker recovered"
    done
}

# Show real-time metrics
show_live_metrics() {
    print_header "Live System Metrics"
    
    while true; do
        echo "📊 REAL-TIME METRICS ($(date))"
        echo "================================"
        
        # Get system health
        health=$(curl -s http://localhost:8080/api/monitoring/health)
        echo "System Status: $(echo $health | jq -r '.status')"
        echo "Active Partners: $(echo $health | jq -r '.totalPartners')"
        
        # Get top 5 busiest partners
        echo ""
        echo "🔥 TOP 5 BUSIEST PARTNERS:"
        pools=$(curl -s http://localhost:8080/api/monitoring/threadpools)
        echo $pools | jq -r 'to_entries | sort_by(.value.completedTaskCount) | reverse | .[:5][] | "   \(.key): \(.value.completedTaskCount) completed, \(.value.activeCount) active"'
        
        # Get circuit breaker summary
        echo ""
        echo "⚡ CIRCUIT BREAKER SUMMARY:"
        breakers=$(curl -s http://localhost:8080/api/monitoring/circuitbreakers)
        echo "   CLOSED: $(echo $breakers | jq '[.[] | select(.state == "CLOSED")] | length')"
        echo "   OPEN: $(echo $breakers | jq '[.[] | select(.state == "OPEN")] | length')"
        echo "   HALF_OPEN: $(echo $breakers | jq '[.[] | select(.state == "HALF_OPEN")] | length')"
        
        echo ""
        echo "Press Ctrl+C to stop..."
        sleep 5
        clear
    done
}

# Main menu
show_menu() {
    print_header "Enhanced Camel MQ Processor - Load Testing & Monitoring"
    echo ""
    echo "Choose an option:"
    echo "1. Start Real-Time Monitoring Dashboard"
    echo "2. Generate Light Load (100 msg/sec for 5 minutes)"
    echo "3. Generate Medium Load (500 msg/sec for 10 minutes)"
    echo "4. Generate Heavy Load (1000 msg/sec for 15 minutes)"
    echo "5. Generate Extreme Load (2000 msg/sec for 20 minutes)"
    echo "6. Simulate Partner Failures"
    echo "7. Show Live Metrics"
    echo "8. Custom Load Generation"
    echo "9. Exit"
    echo ""
    read -p "Enter your choice (1-9): " choice
    
    case $choice in
        1)
            start_monitoring
            ;;
        2)
            generate_high_volume_load 5 100
            ;;
        3)
            generate_high_volume_load 10 500
            ;;
        4)
            generate_high_volume_load 15 1000
            ;;
        5)
            generate_high_volume_load 20 2000
            ;;
        6)
            simulate_partner_failures
            ;;
        7)
            show_live_metrics
            ;;
        8)
            read -p "Enter duration in minutes: " duration
            read -p "Enter messages per second: " rate
            generate_high_volume_load $duration $rate
            ;;
        9)
            print_status "Goodbye!"
            exit 0
            ;;
        *)
            echo "Invalid choice. Please try again."
            show_menu
            ;;
    esac
}

# Check if system is running
check_system() {
    print_status "Checking if Enhanced Camel MQ Processor is running..."
    
    if ! curl -s http://localhost:8080/actuator/health > /dev/null; then
        print_status "❌ System is not running. Please start it first with: ./deploy.sh"
        exit 1
    fi
    
    print_status "✅ System is running and ready for load testing!"
}

# Main execution
main() {
    check_system
    
    while true; do
        show_menu
        echo ""
        read -p "Press Enter to continue or Ctrl+C to exit..."
        clear
    done
}

# Run main function
main "$@"