#!/bin/bash

# SMB Test Environment Control Script
# Provides easy commands to manage the SMB test server

set -e

COMPOSE_FILE="docker-compose.smb-test.yml"
CONTAINER_NAME="semoss-smb-test"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

print_header() {
    echo -e "${BLUE}========================================${NC}"
    echo -e "${BLUE}  SMB Storage Engine Test Environment${NC}"
    echo -e "${BLUE}========================================${NC}\n"
}

print_success() {
    echo -e "${GREEN}✓${NC} $1"
}

print_error() {
    echo -e "${RED}✗${NC} $1"
}

print_info() {
    echo -e "${YELLOW}ℹ${NC} $1"
}

check_docker() {
    if ! command -v docker &> /dev/null; then
        print_error "Docker is not installed or not in PATH"
        exit 1
    fi

    if ! docker ps &> /dev/null; then
        print_error "Docker daemon is not running"
        exit 1
    fi
}

start_server() {
    print_header
    echo "Starting SMB test server..."

    check_docker

    # Create mount directory if it doesn't exist
    mkdir -p test-data/smb-mount

    # Start the container
    docker-compose -f "$COMPOSE_FILE" up -d

    # Wait for container to be ready
    echo -n "Waiting for server to be ready"
    for i in {1..10}; do
        if docker exec "$CONTAINER_NAME" pgrep smbd &> /dev/null 2>&1; then
            echo ""
            print_success "SMB server is running!"
            break
        fi
        echo -n "."
        sleep 1
    done
    echo ""

    echo ""
    print_info "Connection details:"
    echo "  Host:       localhost"
    echo "  Port:       445"
    echo "  Share:      testshare"
    echo "  Username:   testuser"
    echo "  Password:   testpass"
    echo "  Domain:     WORKGROUP"
    echo ""
    print_info "Mount directory: $(pwd)/test-data/smb-mount"
    echo ""
}

stop_server() {
    print_header
    echo "Stopping SMB test server..."

    docker-compose -f "$COMPOSE_FILE" down

    print_success "SMB server stopped"
}

restart_server() {
    stop_server
    echo ""
    start_server
}

status_server() {
    print_header

    if docker ps --format "{{.Names}}" | grep -q "^${CONTAINER_NAME}$"; then
        print_success "SMB server is running"
        echo ""
        docker-compose -f "$COMPOSE_FILE" ps
        echo ""
        print_info "Logs: ./test-smb.sh logs"
    else
        print_info "SMB server is not running"
        echo ""
        print_info "Start it with: ./test-smb.sh start"
    fi
}

logs_server() {
    print_header
    echo "Showing logs (Ctrl+C to exit)..."
    echo ""
    docker-compose -f "$COMPOSE_FILE" logs -f
}

run_manual_test() {
    print_header

    # Check if server is running
    if ! docker ps --format "{{.Names}}" | grep -q "^${CONTAINER_NAME}$"; then
        print_error "SMB server is not running"
        echo ""
        print_info "Start it first: ./test-smb.sh start"
        exit 1
    fi

    print_info "Running manual test..."
    echo ""

    # Find the test class and run it
    mvn exec:java -Dexec.mainClass="prerna.engine.impl.storage.SmbStorageEngineManualTest" -Dexec.classpathScope=test
}

run_unit_tests() {
    print_header

    # Check if server is running for manual tests
    if ! docker ps --format "{{.Names}}" | grep -q "^${CONTAINER_NAME}$"; then
        print_info "Starting SMB server for tests..."
        start_server
        STOP_AFTER=true
    fi

    print_info "Running JUnit tests..."
    echo ""

    mvn test -Dtest=SmbStorageEngineTest

    if [ "$STOP_AFTER" = true ]; then
        echo ""
        print_info "Stopping test server..."
        stop_server
    fi
}

show_files() {
    print_header
    echo "Files in SMB share:"
    echo ""

    if [ -d "test-data/smb-mount" ]; then
        ls -lah test-data/smb-mount/
    else
        print_error "Mount directory does not exist"
    fi
}

clean_files() {
    print_header
    echo "Cleaning SMB share files..."

    if [ -d "test-data/smb-mount" ]; then
        rm -rf test-data/smb-mount/*
        print_success "Files cleaned"
    else
        print_info "Mount directory does not exist"
    fi
}

shell_access() {
    print_header
    echo "Opening shell in SMB container..."
    echo ""
    docker exec -it "$CONTAINER_NAME" /bin/bash
}

show_help() {
    print_header
    echo "Usage: ./test-smb.sh [command]"
    echo ""
    echo "Commands:"
    echo "  start       - Start the SMB test server"
    echo "  stop        - Stop the SMB test server"
    echo "  restart     - Restart the SMB test server"
    echo "  status      - Show server status"
    echo "  logs        - Show server logs (follow)"
    echo "  test        - Run manual test"
    echo "  junit       - Run JUnit integration tests"
    echo "  files       - List files in SMB share"
    echo "  clean       - Remove all files from SMB share"
    echo "  shell       - Open shell in container"
    echo "  help        - Show this help message"
    echo ""
    echo "Examples:"
    echo "  ./test-smb.sh start          # Start server"
    echo "  ./test-smb.sh test           # Run manual test"
    echo "  ./test-smb.sh logs           # Watch logs"
    echo "  ./test-smb.sh stop           # Stop server"
    echo ""
}

# Main command dispatcher
case "${1:-help}" in
    start)
        start_server
        ;;
    stop)
        stop_server
        ;;
    restart)
        restart_server
        ;;
    status)
        status_server
        ;;
    logs)
        logs_server
        ;;
    test)
        run_manual_test
        ;;
    junit)
        run_unit_tests
        ;;
    files)
        show_files
        ;;
    clean)
        clean_files
        ;;
    shell)
        shell_access
        ;;
    help|--help|-h)
        show_help
        ;;
    *)
        print_error "Unknown command: $1"
        echo ""
        show_help
        exit 1
        ;;
esac
