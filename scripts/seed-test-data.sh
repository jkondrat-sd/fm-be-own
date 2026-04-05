#!/usr/bin/env bash
# ============================================================
# FM-BE — Seed Test Data for API Integration Tests
# ============================================================
# Inserts: 1 organization, 1 admin user, 1 employee user
# Idempotent — uses ON CONFLICT DO NOTHING
#
# Usage: ./scripts/seed-test-data.sh
# ============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

log()  { echo -e "${GREEN}[seed]${NC} $*"; }
warn() { echo -e "${YELLOW}[warn]${NC} $*"; }
fail() { echo -e "${RED}[error]${NC} $*"; exit 1; }

DB_HOST="${DATABASE_HOST:-localhost}"
DB_PORT="${DATABASE_PORT:-5432}"
DB_NAME="${DATABASE_NAME:-fm_db}"
DB_USER="${DATABASE_USERNAME:-user}"
DB_PASS="${DATABASE_PASSWORD:-password}"

run_psql() {
    if ! command -v psql &>/dev/null; then
        if command -v docker &>/dev/null && docker ps | grep -q fm-postgres; then
            docker exec -i fm-postgres psql -U "$DB_USER" -d "$DB_NAME" -v ON_ERROR_STOP=1
        else
            fail "psql not found and fm-postgres docker container not running. Install: brew install libpq"
        fi
    else
        PGPASSWORD="$DB_PASS" psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -v ON_ERROR_STOP=1
    fi
}

log "Connecting to $DB_HOST:$DB_PORT/$DB_NAME as $DB_USER ..."

# Fixed UUIDs for deterministic, idempotent seeding
ORG_ID="00000000-0000-4000-a000-000000000001"
ADMIN_USER_ID="00000000-0000-4000-a000-000000000010"
EMPLOYEE_USER_ID="00000000-0000-4000-a000-000000000020"
EMPLOYEE_ID="00000000-0000-4000-a000-000000000021"

run_psql <<SQL

-- Ensure pgcrypto is available for password hashing
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- 1. Organization
INSERT INTO organizations (id, name, created_at, updated_at)
VALUES ('${ORG_ID}', 'FM Test Organization', NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- 2. Admin user (email/password login) — password: Admin@123456
INSERT INTO users (id, email, password_hash, role, is_active, preferred_lang, organization_id, created_at, updated_at)
VALUES ('${ADMIN_USER_ID}', 'admin@fmplatform.com', crypt('Admin@123456', gen_salt('bf', 10)), 'ADMIN', true, 'en', '${ORG_ID}', NOW(), NOW())
ON CONFLICT (id) DO UPDATE SET password_hash = crypt('Admin@123456', gen_salt('bf', 10));

-- 3. Employee user (phone/OTP login)
INSERT INTO users (id, phone, role, is_active, preferred_lang, organization_id, created_at, updated_at)
VALUES ('${EMPLOYEE_USER_ID}', '+919876543210', 'EMPLOYEE', true, 'en', '${ORG_ID}', NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- 4. Employee record for the employee user (needed for /v1/employees/me)
INSERT INTO employees (id, user_id, full_name, organization_id, created_at, updated_at)
VALUES ('${EMPLOYEE_ID}', '${EMPLOYEE_USER_ID}', 'Test Employee', '${ORG_ID}', NOW(), NOW())
ON CONFLICT (user_id) DO NOTHING;

SQL

log "Seed data inserted successfully!"
log ""
log "  Organization : FM Test Organization (${ORG_ID})"
log "  Admin User   : admin@fmplatform.com / Admin@123456 (${ADMIN_USER_ID})"
log "  Employee User: +919876543210 (${EMPLOYEE_USER_ID})"
log "  Employee Rec : Test Employee (${EMPLOYEE_ID})"
log ""
