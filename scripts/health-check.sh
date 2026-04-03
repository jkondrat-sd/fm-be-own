#!/usr/bin/env bash
# ============================================================
# FM-BE — Health Check (server + infrastructure)
# ============================================================
set -euo pipefail

BASE_URL="${FM_BASE_URL:-http://localhost:8080}"
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

ok()   { echo -e "  ${GREEN}✓${NC} $*"; }
fail() { echo -e "  ${RED}✗${NC} $*"; }
warn() { echo -e "  ${YELLOW}⚠${NC} $*"; }

echo ""
echo "═══════════════════════════════════════"
echo "  FM-BE Health Check"
echo "═══════════════════════════════════════"
echo ""

ERRORS=0

# ── Infrastructure ───────────────────────────────────────────
echo "Infrastructure:"

check_postgres() {
    pg_isready -h localhost -p 5432 -U user -d fm_db -q 2>/dev/null \
        || docker exec fm-postgres pg_isready -U user -d fm_db -q 2>/dev/null \
        || nc -z localhost 5432 2>/dev/null
}

check_redis() {
    (echo PING | nc -w 2 localhost 6379 2>/dev/null | grep -q "+PONG") \
        || docker exec fm-redis redis-cli ping 2>/dev/null | grep -q "PONG"
}

if check_postgres; then
    ok "PostgreSQL    : localhost:5432"
else
    fail "PostgreSQL    : NOT REACHABLE"; ERRORS=$((ERRORS+1))
fi

if check_redis; then
    ok "Redis         : localhost:6379"
else
    fail "Redis         : NOT REACHABLE"; ERRORS=$((ERRORS+1))
fi

if curl -sf http://localhost:9000/minio/health/live &>/dev/null; then
    ok "MinIO         : localhost:9000"
else
    fail "MinIO         : NOT REACHABLE"; ERRORS=$((ERRORS+1))
fi

# ── Application ──────────────────────────────────────────────
echo ""
echo "Application:"

HEALTH_RESPONSE=$(curl -sf --max-time 5 "${BASE_URL}/v1/health" 2>/dev/null || echo "UNREACHABLE")

if [ "$HEALTH_RESPONSE" = "UNREACHABLE" ]; then
    fail "FM-BE Server  : NOT REACHABLE at ${BASE_URL}"
    ERRORS=$((ERRORS+1))
else
    STATUS=$(echo "$HEALTH_RESPONSE" | jq -r '.data.status // .status // "UNKNOWN"' 2>/dev/null)
    if [ "$STATUS" = "UP" ]; then
        ok "FM-BE Server  : ${BASE_URL} (status: $STATUS)"
    else
        warn "FM-BE Server  : ${BASE_URL} (status: $STATUS)"
    fi

    DB_STATUS=$(echo "$HEALTH_RESPONSE" | jq -r '.data.services.database.status // "?"' 2>/dev/null)
    REDIS_STATUS=$(echo "$HEALTH_RESPONSE" | jq -r '.data.services.redis.status // "?"' 2>/dev/null)
    echo "  Database    : $DB_STATUS"
    echo "  Redis       : $REDIS_STATUS"
fi

# ── Swagger ──────────────────────────────────────────────────
echo ""
echo "API Docs:"

if curl -sf --max-time 5 "${BASE_URL}/v3/api-docs" &>/dev/null; then
    ok "OpenAPI JSON   : ${BASE_URL}/v3/api-docs"
    ok "Swagger UI     : ${BASE_URL}/swagger-ui.html"
else
    warn "Swagger       : Not available (server may be down)"
fi

# ── Summary ──────────────────────────────────────────────────
echo ""
if [ $ERRORS -eq 0 ]; then
    echo -e "${GREEN}All services healthy.${NC}"
else
    echo -e "${RED}${ERRORS} service(s) have issues.${NC}"
fi
echo ""

exit $ERRORS
