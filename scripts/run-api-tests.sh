#!/usr/bin/env bash
# ============================================================
# FM-BE — Run API Tests via Newman (Postman CLI)
# ============================================================
# Usage:
#   ./scripts/run-api-tests.sh                 # Run all suites
#   ./scripts/run-api-tests.sh --folder Auth   # Run one folder
#   ./scripts/run-api-tests.sh --smoke         # Run smoke tests only
# ============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
POSTMAN_DIR="$PROJECT_ROOT/tests/postman"
REPORTS_DIR="$POSTMAN_DIR/reports"
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

log()  { echo -e "${GREEN}[test]${NC} $*"; }
warn() { echo -e "${YELLOW}[warn]${NC} $*"; }
fail() { echo -e "${RED}[error]${NC} $*"; exit 1; }

# ── Pre-flight ───────────────────────────────────────────────
if ! command -v newman &>/dev/null; then
    fail "Newman not found. Install: npm install -g newman newman-reporter-htmlextra"
fi

COLLECTION="$POSTMAN_DIR/fm-be-api-tests.postman_collection.json"
ENVIRONMENT="$POSTMAN_DIR/fm-be-dev.postman_environment.json"

[ -f "$COLLECTION" ]   || fail "Collection not found: $COLLECTION"
[ -f "$ENVIRONMENT" ]  || fail "Environment not found: $ENVIRONMENT"

mkdir -p "$REPORTS_DIR"

# ── Parse arguments ──────────────────────────────────────────
NEWMAN_ARGS=()
FOLDER=""
SMOKE_ONLY=false
TIMESTAMP=$(date +%Y%m%d-%H%M%S)

while [[ $# -gt 0 ]]; do
    case "$1" in
        --folder)
            FOLDER="$2"
            shift 2
            ;;
        --smoke)
            SMOKE_ONLY=true
            shift
            ;;
        *)
            NEWMAN_ARGS+=("$1")
            shift
            ;;
    esac
done

if [ -n "$FOLDER" ]; then
    NEWMAN_ARGS+=(--folder "$FOLDER")
    REPORT_NAME="fm-be-${FOLDER,,}-${TIMESTAMP}"
elif [ "$SMOKE_ONLY" = true ]; then
    NEWMAN_ARGS+=(--folder "00-Smoke")
    REPORT_NAME="fm-be-smoke-${TIMESTAMP}"
else
    REPORT_NAME="fm-be-full-${TIMESTAMP}"
fi

# ── Health check before running tests ────────────────────────
BASE_URL=$(jq -r '.values[] | select(.key=="base_url") | .value' "$ENVIRONMENT" 2>/dev/null || echo "http://localhost:8080")

log "Checking server at $BASE_URL..."
if ! curl -sf --max-time 5 "$BASE_URL/v1/health" &>/dev/null; then
    warn "Server not reachable at $BASE_URL"
    read -p "Continue anyway? [y/N] " -n 1 -r
    echo
    [[ $REPLY =~ ^[Yy]$ ]] || exit 1
fi

# ── Run Newman ───────────────────────────────────────────────
log "Running API tests..."
echo ""

newman run "$COLLECTION" \
    --environment "$ENVIRONMENT" \
    --reporters cli,json \
    --reporter-json-export "$REPORTS_DIR/${REPORT_NAME}.json" \
    --delay-request 100 \
    --timeout-request 10000 \
    --bail \
    "${NEWMAN_ARGS[@]}" 2>&1

EXIT_CODE=$?

echo ""
if [ $EXIT_CODE -eq 0 ]; then
    log "All tests passed!"
else
    fail "Some tests failed. Exit code: $EXIT_CODE"
fi

log "Report: $REPORTS_DIR/${REPORT_NAME}.json"
echo ""

exit $EXIT_CODE
