#!/usr/bin/env bash
# ============================================================
# FM-BE — Reset Database (drop + recreate)
# ============================================================
# WARNING: Destroys all data! For dev use only.
# ============================================================
set -euo pipefail

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log()  { echo -e "${GREEN}[db]${NC} $*"; }
warn() { echo -e "${YELLOW}[warn]${NC} $*"; }

echo ""
echo -e "${RED}╔══════════════════════════════════════════╗${NC}"
echo -e "${RED}║  WARNING: This will DELETE all data!     ║${NC}"
echo -e "${RED}╚══════════════════════════════════════════╝${NC}"
echo ""

read -p "Are you sure? Type 'yes' to confirm: " CONFIRM
if [ "$CONFIRM" != "yes" ]; then
    echo "Aborted."
    exit 0
fi

DB_NAME="${DB_NAME:-fm_db}"
DB_USER="${DB_USER:-user}"

log "Dropping database '$DB_NAME'..."
docker exec fm-postgres psql -U "$DB_USER" -d postgres -c "DROP DATABASE IF EXISTS $DB_NAME;" 2>/dev/null

log "Creating database '$DB_NAME'..."
docker exec fm-postgres psql -U "$DB_USER" -d postgres -c "CREATE DATABASE $DB_NAME OWNER $DB_USER;" 2>/dev/null

log "Enabling extensions..."
docker exec fm-postgres psql -U "$DB_USER" -d "$DB_NAME" -c "
    CREATE EXTENSION IF NOT EXISTS postgis;
    CREATE EXTENSION IF NOT EXISTS pgcrypto;
    CREATE EXTENSION IF NOT EXISTS \"uuid-ossp\";
" 2>/dev/null

log "Database reset complete."
warn "Restart the server to run Flyway migrations (if enabled) or Hibernate DDL auto-update."
echo ""
