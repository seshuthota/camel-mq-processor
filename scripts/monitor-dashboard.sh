#!/bin/bash

# Real-Time Monitoring Dashboard for Enhanced Camel MQ Processor
# Shows live data processing at scale

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
MAGENTA='\033[0;35m'
NC='\033[0m' # No Color

# API endpoints
HEALTH_API="http://localhost:8080/api/monitoring/health"
THREADPOOL_API="http://localhost:8080/api/monitoring/threadpools"
CIRCUITBREAKER_API="http://localhost:8080/api/monitoring/circuitbreakers"
PARTNERS_API="http://localhost:8080/api/monitoring/partners"
RABBITMQ_API="http://localhost:15672/api"

# Function to draw progress bar
draw_progress_bar() {
    local current=$1
    local max=$2
    local width=20
    local percentage=$((current * 100 / max))
    local filled=$((current * width / max))
    
    printf "["
    for ((i=0; i<filled; i++)); do printf "█"; done
    for ((i=filled; i<width; i++)); do printf "░"; done
    printf "] %3d%%" $percentage
}

# Function to get color based on status
get_status_color() {
    local status=$1
    case $status in
        "UP"|"CLOSED"|"HEALTHY") echo -e "${GREEN}" ;;
        "DOWN"|"OPEN"|"UNHEALTHY") echo -e "${RED}" ;;
        "HALF_OPEN"|"DEGRADED") echo -e "${YELLOW}" ;;
        *) echo -e "${NC}" ;;
    esac
}

# Main monitoring loop
monitor_system() {
    while true; do
        clear
        
        # Header
        echo -e "${BLUE}╔══════════════════════════════════════════════════════════════════════════════╗${NC}"
        echo -e "${BLUE}║                    🚀 ENHANCED CAMEL MQ PROCESSOR                            ║${NC}"
        echo -e "${BLUE}║                        LIVE PROCESSING DASHBOARD                             ║${NC}"
        echo -e "${BLUE}╚══════════════════════════════════════════════════════════════════════════════╝${NC}"
        echo -e "${CYAN}⏰ $(date '+%Y-%m-%d %H:%M:%S')${NC}"
        echo ""
        
        # System Health Overview
        echo -e "${MAGENTA}🏥 SYSTEM HEALTH OVERVIEW${NC}"
        echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        
        health=$(curl -s $HEALTH_API 2>/dev/null)
        if [ $? -eq 0 ] && [ -n "$health" ]; then
            status=$(echo $health | jq -r '.status // "UNKNOWN"')
            total_partners=$(echo $health | jq -r '.totalPartners // 0')
            healthy_pools=$(echo $health | jq -r '.threadPoolsHealthy // 0')
            open_circuits=$(echo $health | jq -r '.circuitBreakersOpen // 0')
            
            status_color=$(get_status_color $status)
            echo -e "   System Status: ${status_color}${status}${NC}"
            echo -e "   Total Partners: ${GREEN}${total_partners}${NC}"
            echo -e "   Healthy Thread Pools: ${GREEN}${healthy_pools}${NC}"
            echo -e "   Open Circuit Breakers: ${RED}${open_circuits}${NC}"
        else
            echo -e "   ${RED}❌ System Health API Unavailable${NC}"
        fi
        echo ""
        
        # Thread Pool Utilization
        echo -e "${MAGENTA}🧵 THREAD POOL UTILIZATION (TOP 10 ACTIVE)${NC}"
        echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        
        pools=$(curl -s $THREADPOOL_API 2>/dev/null)
        if [ $? -eq 0 ] && [ -n "$pools" ]; then
            echo $pools | jq -r 'to_entries | sort_by(.value.activeCount) | reverse | .[:10][] | 
                "\(.key)|\(.value.activeCount)|\(.value.poolSize)|\(.value.queueSize)|\(.value.completedTaskCount)"' | \
            while IFS='|' read -r partner active pool queue completed; do
                if [ -n "$partner" ]; then
                    utilization=$((active * 100 / (pool > 0 ? pool : 1)))
                    util_color=$([ $utilization -gt 80 ] && echo -e "${RED}" || ([ $utilization -gt 60 ] && echo -e "${YELLOW}" || echo -e "${GREEN}"))
                    
                    printf "   %-12s Active: %s%2d${NC} Pool: %2d Queue: %4d Completed: %s\n" \
                        "$partner" "$util_color" "$active" "$pool" "$queue" "$completed"
                fi
            done
        else
            echo -e "   ${RED}❌ Thread Pool API Unavailable${NC}"
        fi
        echo ""
        
        # Circuit Breaker States
        echo -e "${MAGENTA}⚡ CIRCUIT BREAKER STATES${NC}"
        echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        
        breakers=$(curl -s $CIRCUITBREAKER_API 2>/dev/null)
        if [ $? -eq 0 ] && [ -n "$breakers" ]; then
            # Summary
            closed_count=$(echo $breakers | jq '[.[] | select(.state == "CLOSED")] | length')
            open_count=$(echo $breakers | jq '[.[] | select(.state == "OPEN")] | length')
            half_open_count=$(echo $breakers | jq '[.[] | select(.state == "HALF_OPEN")] | length')
            
            echo -e "   Summary: ${GREEN}CLOSED: $closed_count${NC} | ${RED}OPEN: $open_count${NC} | ${YELLOW}HALF_OPEN: $half_open_count${NC}"
            echo ""
            
            # Top failing partners
            echo "   Top Partners by Failure Rate:"
            echo $breakers | jq -r 'to_entries | sort_by(.value.failureRate) | reverse | .[:8][] | 
                "\(.key)|\(.value.state)|\(.value.failureRate)|\(.value.numberOfCalls)|\(.value.numberOfFailedCalls)"' | \
            while IFS='|' read -r partner state failure_rate calls failed; do
                if [ -n "$partner" ]; then
                    state_color=$(get_status_color $state)
                    failure_color=$([ $(echo "$failure_rate > 50" | bc -l 2>/dev/null || echo 0) -eq 1 ] && echo -e "${RED}" || echo -e "${GREEN}")
                    
                    printf "     %-12s State: %s%-10s${NC} Failure Rate: %s%5.1f%%${NC} Calls: %3d Failed: %3d\n" \
                        "$partner" "$state_color" "$state" "$failure_color" "$failure_rate" "$calls" "$failed"
                fi
            done
        else
            echo -e "   ${RED}❌ Circuit Breaker API Unavailable${NC}"
        fi
        echo ""
        
        # RabbitMQ Queue Statistics
        echo -e "${MAGENTA}📨 RABBITMQ QUEUE STATISTICS${NC}"
        echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        
        queues=$(curl -s -u admin:admin123 $RABBITMQ_API/queues 2>/dev/null)
        if [ $? -eq 0 ] && [ -n "$queues" ]; then
            echo $queues | jq -r '.[] | select(.name | contains("queue")) | 
                "\(.name)|\(.messages)|\(.messages_ready)|\(.message_stats.publish_details.rate // 0)|\(.message_stats.deliver_details.rate // 0)"' | \
            head -8 | while IFS='|' read -r queue_name messages ready publish_rate deliver_rate; do
                if [ -n "$queue_name" ]; then
                    queue_color=$([ "$messages" -gt 1000 ] && echo -e "${RED}" || ([ "$messages" -gt 100 ] && echo -e "${YELLOW}" || echo -e "${GREEN}"))
                    
                    printf "   %-25s Messages: %s%4d${NC} Ready: %4d Pub Rate: %6.1f/s Del Rate: %6.1f/s\n" \
                        "$queue_name" "$queue_color" "$messages" "$ready" "$publish_rate" "$deliver_rate"
                fi
            done
        else
            echo -e "   ${RED}❌ RabbitMQ API Unavailable${NC}"
        fi
        echo ""
        
        # Message Processing Rate
        echo -e "${MAGENTA}📊 MESSAGE PROCESSING METRICS${NC}"
        echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        
        # Calculate total processing rate
        if [ -n "$pools" ]; then
            total_completed=$(echo $pools | jq '[.[] | .completedTaskCount] | add')
            total_active=$(echo $pools | jq '[.[] | .activeCount] | add')
            total_queue=$(echo $pools | jq '[.[] | .queueSize] | add')
            
            echo -e "   Total Messages Processed: ${GREEN}${total_completed}${NC}"
            echo -e "   Currently Processing: ${YELLOW}${total_active}${NC}"
            echo -e "   Messages in Queue: ${CYAN}${total_queue}${NC}"
            
            # Show processing distribution
            echo ""
            echo "   Processing Distribution by Partner:"
            echo $pools | jq -r 'to_entries | sort_by(.value.completedTaskCount) | reverse | .[:6][] | 
                "\(.key)|\(.value.completedTaskCount)"' | \
            while IFS='|' read -r partner completed; do
                if [ -n "$partner" ] && [ "$completed" -gt 0 ]; then
                    percentage=$((completed * 100 / (total_completed > 0 ? total_completed : 1)))
                    printf "     %-12s " "$partner"
                    draw_progress_bar $completed $total_completed
                    printf " (%d messages)\n" $completed
                fi
            done
        fi
        echo ""
        
        # Footer
        echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
        echo -e "${CYAN}🎯 THE THREAD BLOCKING NIGHTMARE IS OVER! Partners are processing independently! 💪${NC}"
        echo -e "${BLUE}Press Ctrl+C to exit monitoring...${NC}"
        
        sleep 3
    done
}

# Check if system is running
check_system() {
    echo "🔍 Checking if Enhanced Camel MQ Processor is running..."
    
    if ! curl -s http://localhost:8080/actuator/health > /dev/null; then
        echo "❌ System is not running. Please start it first with: ./deploy.sh"
        exit 1
    fi
    
    echo "✅ System is running! Starting live monitoring dashboard..."
    sleep 2
}

# Main execution
main() {
    # Check dependencies
    if ! command -v jq &> /dev/null; then
        echo "❌ jq is required for JSON parsing. Please install it:"
        echo "   Ubuntu/Debian: sudo apt-get install jq"
        echo "   CentOS/RHEL: sudo yum install jq"
        echo "   macOS: brew install jq"
        exit 1
    fi
    
    if ! command -v bc &> /dev/null; then
        echo "❌ bc is required for calculations. Please install it:"
        echo "   Ubuntu/Debian: sudo apt-get install bc"
        echo "   CentOS/RHEL: sudo yum install bc"
        exit 1
    fi
    
    check_system
    monitor_system
}

# Handle Ctrl+C gracefully
trap 'echo -e "\n\n${GREEN}Monitoring stopped. Goodbye! 👋${NC}"; exit 0' INT

# Run main function
main "$@"