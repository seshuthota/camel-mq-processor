#!/bin/bash

# Enhanced Camel MQ Processor Deployment Script
# Deploys the complete system with live message processing capabilities

set -e

echo "🚀 Starting Enhanced Camel MQ Processor Deployment..."

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to print colored output
print_status() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

print_header() {
    echo -e "${BLUE}========================================${NC}"
    echo -e "${BLUE} $1${NC}"
    echo -e "${BLUE}========================================${NC}"
}

# Check if Docker and Docker Compose are installed
check_prerequisites() {
    print_header "Checking Prerequisites"
    
    if ! command -v docker &> /dev/null; then
        print_error "Docker is not installed. Please install Docker first."
        exit 1
    fi
    
    if ! command -v docker-compose &> /dev/null; then
        print_error "Docker Compose is not installed. Please install Docker Compose first."
        exit 1
    fi
    
    print_status "Docker and Docker Compose are installed ✅"
}

# Build the application
build_application() {
    print_header "Building Enhanced Camel MQ Processor"
    
    print_status "Cleaning previous builds..."
    ./mvnw clean
    
    print_status "Running tests..."
    ./mvnw test -Dtest=ThreadBlockingDemoTest
    
    print_status "Building application JAR..."
    ./mvnw package -DskipTests
    
    print_status "Application built successfully ✅"
}

# Start infrastructure services
start_infrastructure() {
    print_header "Starting Infrastructure Services"
    
    print_status "Starting RabbitMQ, Elasticsearch, Redis, Prometheus, and Grafana..."
    docker-compose up -d rabbitmq elasticsearch redis prometheus grafana
    
    print_status "Waiting for services to be ready..."
    sleep 30
    
    # Check service health
    check_service_health
}

# Check if services are healthy
check_service_health() {
    print_status "Checking service health..."
    
    # Check RabbitMQ
    if curl -f -u admin:admin123 http://localhost:15672/api/overview &> /dev/null; then
        print_status "RabbitMQ is healthy ✅"
    else
        print_warning "RabbitMQ is not ready yet, waiting..."
        sleep 15
    fi
    
    # Check Elasticsearch
    if curl -f -u elastic:elastic123 http://localhost:9200/_cluster/health &> /dev/null; then
        print_status "Elasticsearch is healthy ✅"
    else
        print_warning "Elasticsearch is not ready yet, waiting..."
        sleep 15
    fi
    
    # Check Redis
    if docker exec redis-cache redis-cli -a redis123 ping &> /dev/null; then
        print_status "Redis is healthy ✅"
    else
        print_warning "Redis is not ready yet, waiting..."
        sleep 10
    fi
}

# Setup sample partner configurations in Elasticsearch
setup_partner_configs() {
    print_header "Setting Up Partner Configurations"
    
    print_status "Creating partner configuration index..."
    
    # Wait for Elasticsearch to be fully ready
    sleep 20
    
    # Create index with mapping
    curl -X PUT "localhost:9200/partner-configurations" \
         -u elastic:elastic123 \
         -H "Content-Type: application/json" \
         -d '{
           "mappings": {
             "properties": {
               "businessUnit": {"type": "keyword"},
               "coreThreads": {"type": "integer"},
               "maxThreads": {"type": "integer"},
               "queueCapacity": {"type": "integer"},
               "circuitBreakerFailureThreshold": {"type": "float"},
               "priority": {"type": "keyword"},
               "apiEndpoint": {"type": "text"},
               "authEndpoint": {"type": "text"}
             }
           }
         }' || print_warning "Index might already exist"
    
    # Add sample partner configurations
    print_status "Adding sample partner configurations..."
    
    # Amazon configuration
    curl -X POST "localhost:9200/partner-configurations/_doc/amazon" \
         -u elastic:elastic123 \
         -H "Content-Type: application/json" \
         -d '{
           "businessUnit": "AMAZON",
           "coreThreads": 20,
           "maxThreads": 100,
           "queueCapacity": 5000,
           "circuitBreakerFailureThreshold": 70.0,
           "circuitBreakerMinCalls": 30,
           "circuitBreakerWaitDuration": 45,
           "retryMaxAttempts": 5,
           "retryBackoffMultiplier": 2.0,
           "authTokenExpiryMinutes": 30,
           "apiTimeoutSeconds": 30,
           "priority": "HIGH",
           "apiEndpoint": "https://api.amazon.com/v1/orders",
           "authEndpoint": "https://api.amazon.com/auth/token"
         }'
    
    # Flipkart configuration
    curl -X POST "localhost:9200/partner-configurations/_doc/flipkart" \
         -u elastic:elastic123 \
         -H "Content-Type: application/json" \
         -d '{
           "businessUnit": "FLIPKART",
           "coreThreads": 15,
           "maxThreads": 80,
           "queueCapacity": 4000,
           "circuitBreakerFailureThreshold": 65.0,
           "circuitBreakerMinCalls": 25,
           "circuitBreakerWaitDuration": 50,
           "retryMaxAttempts": 4,
           "retryBackoffMultiplier": 1.8,
           "authTokenExpiryMinutes": 25,
           "apiTimeoutSeconds": 25,
           "priority": "HIGH",
           "apiEndpoint": "https://api.flipkart.com/v2/orders",
           "authEndpoint": "https://api.flipkart.com/oauth/token"
         }'
    
    # Myntra configuration
    curl -X POST "localhost:9200/partner-configurations/_doc/myntra" \
         -u elastic:elastic123 \
         -H "Content-Type: application/json" \
         -d '{
           "businessUnit": "MYNTRA",
           "coreThreads": 10,
           "maxThreads": 60,
           "queueCapacity": 3000,
           "circuitBreakerFailureThreshold": 60.0,
           "circuitBreakerMinCalls": 20,
           "circuitBreakerWaitDuration": 60,
           "retryMaxAttempts": 3,
           "retryBackoffMultiplier": 1.5,
           "authTokenExpiryMinutes": 20,
           "apiTimeoutSeconds": 20,
           "priority": "MEDIUM",
           "apiEndpoint": "https://api.myntra.com/v1/orders",
           "authEndpoint": "https://api.myntra.com/auth/token"
         }'
    
    print_status "Partner configurations added successfully ✅"
}

# Start the main application
start_application() {
    print_header "Starting Enhanced Camel MQ Processor Application"
    
    print_status "Building and starting the application container..."
    docker-compose up -d camel-mq-processor
    
    print_status "Waiting for application to start..."
    sleep 45
    
    # Check application health
    check_application_health
}

# Check application health
check_application_health() {
    print_status "Checking application health..."
    
    local max_attempts=12
    local attempt=1
    
    while [ $attempt -le $max_attempts ]; do
        if curl -f http://localhost:8080/actuator/health &> /dev/null; then
            print_status "Application is healthy ✅"
            return 0
        else
            print_warning "Application not ready yet (attempt $attempt/$max_attempts), waiting..."
            sleep 10
            ((attempt++))
        fi
    done
    
    print_error "Application failed to start properly"
    print_status "Checking application logs..."
    docker-compose logs camel-mq-processor
    return 1
}

# Send test messages
send_test_messages() {
    print_header "Sending Test Messages"
    
    print_status "Sending test messages to RabbitMQ..."
    
    # Send messages for different partners
    for partner in "AMAZON" "FLIPKART" "MYNTRA"; do
        for i in {1..10}; do
            curl -X POST "http://localhost:15672/api/exchanges/%2F/message.processing.exchange/publish" \
                 -u admin:admin123 \
                 -H "Content-Type: application/json" \
                 -d "{
                   \"properties\": {
                     \"headers\": {
                       \"CBUSINESSUNIT\": \"$partner\",
                       \"messageId\": \"test-msg-$partner-$i\",
                       \"timestamp\": \"$(date -Iseconds)\"
                     }
                   },
                   \"routing_key\": \"message.process\",
                   \"payload\": \"{\\\"orderId\\\": \\\"ORDER-$partner-$i\\\", \\\"customerId\\\": \\\"CUST-$i\\\", \\\"amount\\\": 100.00}\",
                   \"payload_encoding\": \"string\"
                 }"
        done
        print_status "Sent 10 test messages for $partner"
    done
    
    print_status "Test messages sent successfully ✅"
}

# Display deployment information
show_deployment_info() {
    print_header "Deployment Complete! 🎉"
    
    echo ""
    print_status "🚀 Enhanced Camel MQ Processor is now running!"
    echo ""
    print_status "📊 Access Points:"
    echo "   • Application Health: http://localhost:8080/actuator/health"
    echo "   • Monitoring API: http://localhost:8080/api/monitoring/health"
    echo "   • Partner Management: http://localhost:8080/api/config/partners"
    echo "   • Metrics: http://localhost:8080/actuator/prometheus"
    echo ""
    print_status "🔧 Infrastructure Services:"
    echo "   • RabbitMQ Management: http://localhost:15672 (admin/admin123)"
    echo "   • Elasticsearch: http://localhost:9200 (elastic/elastic123)"
    echo "   • Prometheus: http://localhost:9090"
    echo "   • Grafana: http://localhost:3000 (admin/admin123)"
    echo ""
    print_status "📈 Monitor your system:"
    echo "   • Thread Pool Stats: curl http://localhost:8080/api/monitoring/threadpools"
    echo "   • Circuit Breaker Stats: curl http://localhost:8080/api/monitoring/circuitbreakers"
    echo "   • Partner Overview: curl http://localhost:8080/api/monitoring/partners"
    echo ""
    print_status "🎯 THE THREAD BLOCKING NIGHTMARE IS OVER!"
    print_status "Your system now processes messages with complete partner isolation! 💪"
}

# Cleanup function
cleanup() {
    print_header "Cleaning Up"
    print_status "Stopping all services..."
    docker-compose down
    print_status "Cleanup complete"
}

# Main deployment flow
main() {
    # Handle script interruption
    trap cleanup EXIT
    
    check_prerequisites
    build_application
    start_infrastructure
    setup_partner_configs
    start_application
    send_test_messages
    show_deployment_info
    
    print_status "Deployment script completed successfully! 🎉"
    print_status "Your Enhanced Camel MQ Processor is ready for production! 🚀"
}

# Run main function
main "$@"