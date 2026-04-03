#!/usr/bin/env bash
# ============================================================
# FM-BE — Stop All Services (server + infrastructure)
# ============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
GREEN='\033[0;32m'
NC='\033[0m'

log() { echo -e "${GREEN}[stop]${NC} $*"; }

cd "$PROJECT_ROOT"

# ── Stop Java server ─────────────────────────────────────────
SERVER_PID=$(pgrep -f "fm-be.*spring-boot" 2>/dev/null || true)
if [ -n "$SERVER_PID" ]; then
    log "Stopping FM-BE server (PID: $SERVER_PID)..."
    kill "$SERVER_PID" 2>/dev/null || true
    sleep 2
    kill -9 "$SERVER_PID" 2>/dev/null || true
    log "Server stopped."
else
    log "No running FM-BE server found."
fi

# ── Stop Docker infrastructure ───────────────────────────────
if docker compose version &>/dev/null 2>&1; then
    DC="docker compose"
elif command -v docker-compose &>/dev/null; then
    DC="docker-compose"
else
    log "Docker Compose not found. Skipping container shutdown."
    exit 0
fi

log "Stopping infrastructure containers..."
$DC down

log "All services stopped."
echo ""
