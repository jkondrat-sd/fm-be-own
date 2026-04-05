#!/usr/bin/env bash
# ============================================================
# FM-BE — Run API Tests via Newman (Multi-Suite Support)
# ============================================================
# Usage:
#   ./scripts/run-api-tests.sh                 # Run all suites in order
#   ./scripts/run-api-tests.sh --folder 01-Auth # Run one folder
#   ./scripts/run-api-tests.sh --smoke         # Run smoke tests only
# ============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
POSTMAN_DIR="$PROJECT_ROOT/tests/postman"
SUITES_DIR="$POSTMAN_DIR/suites"
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

ENVIRONMENT="$POSTMAN_DIR/fm-be-dev.postman_environment.json"
[ -f "$ENVIRONMENT" ]  || fail "Environment not found: $ENVIRONMENT"

mkdir -p "$REPORTS_DIR"

# ── Parse arguments ──────────────────────────────────────────
FOLDER_ARG=""
SMOKE_ONLY=false
NEWMAN_EXTRA_ARGS=()
TIMESTAMP=$(date +%Y%m%d-%H%M%S)

while [[ $# -gt 0 ]]; do
    case "$1" in
        --folder)
            FOLDER_ARG="$2"
            shift 2
            ;;
        --smoke)
            SMOKE_ONLY=true
            shift
            ;;
        *)
            NEWMAN_EXTRA_ARGS+=("$1")
            shift
            ;;
    esac
done

# ── Determine Collections to run ────────────────────────────
SELECTED_COLLECTIONS=()

if [ "$SMOKE_ONLY" = true ]; then
    COLLECTION=$(find "$SUITES_DIR/00-smoke" -name "*.json" | head -n 1)
    [ -f "$COLLECTION" ] || fail "Smoke collection not found in $SUITES_DIR/00-smoke"
    SELECTED_COLLECTIONS+=("$COLLECTION")
    REPORT_NAME="fm-be-smoke-${TIMESTAMP}"
elif [ -n "$FOLDER_ARG" ]; then
    # Slugify folder name to match directory naming (e.g. "01-Auth" -> "01-auth")
    FOLDER_SLUG=$(echo "$FOLDER_ARG" | tr '[:upper:]' '[:lower:]' | sed 's/[^a-z0-9]/-/g')
    COLLECTION=$(find "$SUITES_DIR/$FOLDER_SLUG" -name "*.json" | head -n 1)
    [ -f "$COLLECTION" ] || fail "Collection for folder '$FOLDER_ARG' (slug: $FOLDER_SLUG) not found in $SUITES_DIR"
    SELECTED_COLLECTIONS+=("$COLLECTION")
    REPORT_NAME="fm-be-${FOLDER_SLUG}-${TIMESTAMP}"
else
    # Full run - all collections in order
    while IFS= read -r line; do
        SELECTED_COLLECTIONS+=("$line")
    done < <(find "$SUITES_DIR" -name "*.json" | sort)
    [ ${#SELECTED_COLLECTIONS[@]} -gt 0 ] || fail "No collections found in $SUITES_DIR"
    REPORT_NAME="fm-be-full-${TIMESTAMP}"
fi

# ── Health check ─────────────────────────────────────────────
BASE_URL=$(jq -r '.values[] | select(.key=="base_url") | .value' "$ENVIRONMENT" 2>/dev/null || echo "http://localhost:8080")
log "Checking server at $BASE_URL..."
if ! curl -sf --max-time 5 "$BASE_URL/v1/health" &>/dev/null; then
    warn "Server not reachable at $BASE_URL"
    read -p "Continue anyway? [y/N] " -n 1 -r
    echo
    [[ $REPLY =~ ^[Yy]$ ]] || exit 1
fi

# ── Run Collections ──────────────────────────────────────────
# Use a temp environment to persist changes between suite runs
TEMP_ENV="/tmp/fm-be-env-$$.json"
cp "$ENVIRONMENT" "$TEMP_ENV"
# trap 'rm -f "$TEMP_ENV"' EXIT
log "Using temp environment: $TEMP_ENV"

log "Running API tests (${#SELECTED_COLLECTIONS[@]} collections)..."
echo ""

TOTAL_EXIT_CODE=0

for COLLECTION in "${SELECTED_COLLECTIONS[@]}"; do
    COLL_NAME=$(basename "$COLLECTION" .postman_collection.json)
    log "Suite: $COLL_NAME"

    # Run newman, use and update the TEMP_ENV
    newman run "$COLLECTION" \
        --environment "$TEMP_ENV" \
        --export-environment "$TEMP_ENV" \
        --reporters cli,json \
        --reporter-json-export "$REPORTS_DIR/${REPORT_NAME}-${COLL_NAME}.json" \
        --delay-request 100 \
        --timeout-request 10000 \
        --bail \
        ${NEWMAN_EXTRA_ARGS[@]+"${NEWMAN_EXTRA_ARGS[@]}"} || {
            TOTAL_EXIT_CODE=$?
            fail "Suite $COLL_NAME failed with exit code $TOTAL_EXIT_CODE"
        }
    echo ""
done

log "All selected suites passed!"
echo ""
exit 0
