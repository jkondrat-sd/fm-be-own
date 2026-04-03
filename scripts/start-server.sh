#!/usr/bin/env bash
# ============================================================
# FM-BE — Start Spring Boot Server (dev profile)
# ============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

log()  { echo -e "${GREEN}[server]${NC} $*"; }
warn() { echo -e "${YELLOW}[warn]${NC}   $*"; }
fail() { echo -e "${RED}[error]${NC}  $*"; exit 1; }

cd "$PROJECT_ROOT"

# Source env if available
if [ -f .env.dev ]; then
    set -a
    source .env.dev
    set +a
    log "Loaded .env.dev"
fi

# ── Pre-flight checks ───────────────────────────────────────
log "Pre-flight checks..."

check_postgres() {
    pg_isready -h localhost -p 5432 -U user -d fm_db -q 2>/dev/null \
        || docker exec fm-postgres pg_isready -U user -d fm_db -q 2>/dev/null \
        || nc -z localhost 5432 2>/dev/null
}

check_redis() {
    (echo PING | nc -w 2 localhost 6379 2>/dev/null | grep -q "+PONG") \
        || docker exec fm-redis redis-cli ping 2>/dev/null | grep -q "PONG"
}

if ! check_postgres; then
    warn "PostgreSQL not reachable. Start infra first: ./scripts/start-infra.sh"
    read -p "Continue anyway? [y/N] " -n 1 -r
    echo
    [[ $REPLY =~ ^[Yy]$ ]] || exit 1
else
    log "PostgreSQL    : OK"
fi

if ! check_redis; then
    warn "Redis not reachable."
else
    log "Redis         : OK"
fi

# ── Build & Run ──────────────────────────────────────────────
PROFILE="${1:-dev}"
log "Profile: $PROFILE"
log "Starting FM-BE server..."
echo ""

if command -v mvn &>/dev/null; then
    MVN="mvn"
elif [ -f mvnw ]; then
    MVN="./mvnw"
else
    fail "Maven not found. Run ./scripts/setup-dev.sh first."
fi

exec $MVN spring-boot:run \
    -Dspring-boot.run.profiles="$PROFILE" \
    -Dspring-boot.run.jvmArguments="-Xmx512m -XX:+UseG1GC"
