#!/usr/bin/env bash
# ============================================================
# FM-BE — Start Infrastructure (PostgreSQL, Redis, MinIO)
# ============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

log()  { echo -e "${GREEN}[infra]${NC} $*"; }
warn() { echo -e "${YELLOW}[warn]${NC}  $*"; }
fail() { echo -e "${RED}[error]${NC} $*"; exit 1; }

cd "$PROJECT_ROOT"

# Detect docker compose command
if docker compose version &>/dev/null 2>&1; then
    DC="docker compose"
elif command -v docker-compose &>/dev/null; then
    DC="docker-compose"
else
    fail "Docker Compose not found. Run ./scripts/setup-dev.sh first."
fi

log "Starting infrastructure services..."
$DC up -d postgres redis minio minio-init

# ── Wait for services ────────────────────────────────────────
wait_for_service() {
    local name="$1"
    local container="$2"
    local max_wait="${3:-60}"
    local elapsed=0

    printf "${GREEN}[infra]${NC} Waiting for %-12s" "$name..."
    while [ $elapsed -lt $max_wait ]; do
        if docker inspect --format='{{.State.Health.Status}}' "$container" 2>/dev/null | grep -q "healthy"; then
            echo -e " ${GREEN}ready${NC} (${elapsed}s)"
            return 0
        fi
        sleep 2
        elapsed=$((elapsed + 2))
    done
    echo -e " ${RED}timeout${NC} (${max_wait}s)"
    return 1
}

wait_for_service "PostgreSQL" "fm-postgres" 60
wait_for_service "Redis"      "fm-redis"    30
wait_for_service "MinIO"      "fm-minio"    30

# ── Verify connectivity ─────────────────────────────────────
echo ""
log "Service status:"

check_postgres() {
    pg_isready -h localhost -p 5432 -U user -d fm_db -q 2>/dev/null \
        || docker exec fm-postgres pg_isready -U user -d fm_db -q 2>/dev/null \
        || nc -z localhost 5432 2>/dev/null
}

if check_postgres; then
    PG_VER=$(docker exec fm-postgres psql -U user -d fm_db -t -c "SELECT version();" 2>/dev/null | head -1 | xargs || echo "unknown")
    log "  PostgreSQL : UP — $PG_VER"
    POSTGIS=$(docker exec fm-postgres psql -U user -d fm_db -t -c "SELECT PostGIS_Version();" 2>/dev/null | xargs || echo "unknown")
    log "  PostGIS    : $POSTGIS"
else
    warn "  PostgreSQL : NOT READY"
fi

if (echo PING | nc -w 2 localhost 6379 2>/dev/null | grep -q "+PONG") \
    || docker exec fm-redis redis-cli ping 2>/dev/null | grep -q "PONG"; then
    log "  Redis      : UP — PONG"
else
    warn "  Redis      : NOT READY"
fi

if curl -sf http://localhost:9000/minio/health/live &>/dev/null; then
    log "  MinIO      : UP — http://localhost:9000"
    log "  MinIO UI   : http://localhost:9001 (minioadmin/minioadmin)"
else
    warn "  MinIO      : NOT READY"
fi

echo ""
log "Infrastructure ready. Ports:"
log "  PostgreSQL : localhost:5432  (user/password, db: fm_db)"
log "  Redis      : localhost:6379"
log "  MinIO API  : localhost:9000"
log "  MinIO UI   : localhost:9001"
echo ""
