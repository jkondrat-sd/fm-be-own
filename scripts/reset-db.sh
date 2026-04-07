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

# read -p "Are you sure? Type 'yes' to confirm: " CONFIRM
# if [ "$CONFIRM" != "yes" ]; then
#     echo "Aborted."
#     exit 0
# fi

DB_NAME="${DB_NAME:-fm_db}"
DB_USER="${DB_USER:-user}"

# Function to run psql command either locally or via docker
run_psql() {
  local database="$1"
  local command="$2"
  
  # Try local psql first
  if command -v psql &>/dev/null && pg_isready -h localhost -p 5432 -q; then
    psql -U "$DB_USER" -d "$database" -c "$command" 2>/dev/null
    return $?
  fi
  
  # Fallback to docker
  if docker ps -q --filter "name=fm-postgres" &>/dev/null; then
    docker exec fm-postgres psql -U "$DB_USER" -d "$database" -c "$command" 2>/dev/null
    return $?
  fi
  
  return 1
}

log "Dropping database '$DB_NAME'..."
run_psql "postgres" "DROP DATABASE IF EXISTS $DB_NAME;" || warn "Could not drop database."

log "Creating database '$DB_NAME'..."
run_psql "postgres" "CREATE DATABASE $DB_NAME OWNER $DB_USER;" || warn "Could not create database."

log "Enabling extensions..."
run_psql "$DB_NAME" "
    CREATE EXTENSION IF NOT EXISTS postgis;
    CREATE EXTENSION IF NOT EXISTS pgcrypto;
    CREATE EXTENSION IF NOT EXISTS \"uuid-ossp\";
" || warn "Could not enable extensions."

log "Database reset complete."
warn "Restart the server to run Flyway migrations (if enabled) or Hibernate DDL auto-update."
echo ""
