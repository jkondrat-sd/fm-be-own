#!/usr/bin/env bash
# ============================================================
# FM-BE — Unified Services Manager (Native macOS/Homebrew)
# ============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

log()  { echo -e "${BLUE}[services]${NC} $*"; }
success() { echo -e "${GREEN}[success]${NC}  $*"; }
warn() { echo -e "${YELLOW}[warn]${NC}     $*"; }
fail() { echo -e "${RED}[error]${NC}    $*"; exit 1; }

cd "$PROJECT_ROOT"

usage() {
    echo "Usage: $0 <command> [args]"
    echo ""
    echo "Commands:"
    echo "  up          Start all infrastructure and the API server"
    echo "  down        Stop the API server and all local infrastructure"
    echo "  infra-up    Start local infrastructure (Postgres, Redis, MinIO) via Homebrew"
    echo "  infra-down  Stop local infrastructure via Homebrew"
    echo "  server-up   Start the Spring Boot API server (foreground)"
    echo "  server-down Stop any running API server"
    echo "  test        Run all API integration tests"
    echo "  test-fast   Run smoke tests only"
    echo "  reset-db    Reset the database to a clean state with seed data"
    echo "  status      Check health of all services"
    echo ""
}

check_infra() {
    log "Checking infrastructure status..."
    PG_UP=false
    REDIS_UP=false
    MINIO_UP=false

    if pg_isready -h localhost -p 5432 -q; then PG_UP=true; fi
    if (echo PING | nc -w 1 localhost 6379 2>/dev/null | grep -q "+PONG"); then REDIS_UP=true; fi
    if curl -sf http://localhost:9000/minio/health/live &>/dev/null; then MINIO_UP=true; fi

    [ "$PG_UP" = true ] && success "PostgreSQL : UP" || warn "PostgreSQL : DOWN"
    [ "$REDIS_UP" = true ] && success "Redis      : UP" || warn "Redis      : DOWN"
    [ "$MINIO_UP" = true ] && success "MinIO      : UP" || warn "MinIO      : DOWN"
    
    if [ "$PG_UP" = false ] || [ "$REDIS_UP" = false ] || [ "$MINIO_UP" = false ]; then
        return 1
    fi
    return 0
}

case "${1:-help}" in
    up)
        log "Starting everything (native)..."
        "$SCRIPT_DIR/services.sh" infra-up
        "$SCRIPT_DIR/services.sh" server-up
        ;;
    down)
        log "Stopping everything..."
        "$SCRIPT_DIR/services.sh" server-down
        "$SCRIPT_DIR/services.sh" infra-down
        ;;
    infra-up)
        log "Starting Homebrew services (Postgres, Redis)..."
        brew services start postgresql@17 || true
        brew services start redis || true
        
        # MinIO doesn't have a brew service, start manually in background
        if ! curl -sf http://localhost:9000/minio/health/live &>/dev/null; then
            log "Starting MinIO manually in background..."
            export MINIO_ROOT_USER=minioadmin
            export MINIO_ROOT_PASSWORD=minioadmin
            mkdir -p /opt/homebrew/var/minio
            nohup /opt/homebrew/bin/minio server --address=:9000 /opt/homebrew/var/minio > /tmp/minio.log 2>&1 &
            sleep 2
        fi
        
        log "Waiting for services to be ready..."
        check_infra
        ;;
    infra-down)
        log "Stopping Homebrew services..."
        brew services stop postgresql@17 || true
        brew services stop redis || true
        
        # Stop manual MinIO
        MINIO_PID=$(pgrep -f "minio server" 2>/dev/null || true)
        if [ -n "$MINIO_PID" ]; then
            log "Stopping MinIO (PID: $MINIO_PID)..."
            kill "$MINIO_PID" 2>/dev/null || true
        fi
        ;;
    server-up)
        log "Starting API Server..."
        ./scripts/start-server.sh
        ;;
    server-down)
        log "Stopping API Server..."
        SERVER_PID=$(pgrep -f "fm-be.*spring-boot" 2>/dev/null || true)
        if [ -n "$SERVER_PID" ]; then
            kill "$SERVER_PID" 2>/dev/null || true
            success "Server stopped (PID: $SERVER_PID)"
        else
            log "No running server found."
        fi
        ;;
    test)
        log "Running full API test suite..."
        ./scripts/run-api-tests.sh
        ;;
    test-fast)
        log "Running smoke tests..."
        ./scripts/run-api-tests.sh --smoke
        ;;
    reset-db)
        log "Resetting database..."
        ./scripts/reset-db.sh
        ;;
    status)
        check_infra
        SERVER_PID=$(pgrep -f "fm-be.*spring-boot" 2>/dev/null || true)
        if [ -n "$SERVER_PID" ]; then
            success "API Server  : UP (PID: $SERVER_PID)"
        else
            warn "API Server  : DOWN"
        fi
        ;;
    help|--help|-h)
        usage
        ;;
    *)
        fail "Unknown command: $1. Run '$0 help' for usage."
        ;;
esac
