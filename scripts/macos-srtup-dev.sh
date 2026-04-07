#!/bin/bash

# ============================================================
# FM-BE — Native macOS Setup Script
# Replicates docker-compose.yml infrastructure via Homebrew
# ============================================================

set -e # Exit on error

# Logging colors
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

log() {
    echo -e "${BLUE}[$(date +'%Y-%m-%d %H:%M:%S')]${NC} $1"
}

success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

# 1. Check for Homebrew
if ! command -v brew &> /dev/null; then
    log "Homebrew not found. Please install it from https://brew.sh/"
    exit 1
fi

log "Starting local infrastructure setup..."

# --- POSTGRESQL & POSTGIS ---
log "Installing PostgreSQL 17 and PostGIS..."
brew install postgresql@17 postgis
brew link postgresql@17 --force

log "Starting PostgreSQL service..."
brew services restart postgresql@17

# Wait for Postgres to be ready
log "Waiting for PostgreSQL to initialize..."
until pg_isready -h localhost -p 5432; do
  sleep 2
done

log "Configuring PostgreSQL user and database..."
# Create user and DB if they don't exist
psql postgres -tAc "SELECT 1 FROM pg_roles WHERE rolname='user'" | grep -q 1 || \
    psql postgres -c "CREATE USER \"user\" WITH PASSWORD 'password' SUPERUSER;"
psql postgres -tAc "SELECT 1 FROM pg_database WHERE datname='fm_db'" | grep -q 1 || \
    psql postgres -c "CREATE DATABASE fm_db OWNER \"user\";"

log "Enabling PostGIS extension on fm_db..."
psql -d fm_db -c "CREATE EXTENSION IF NOT EXISTS postgis;"
success "PostgreSQL/PostGIS configured (Port: 5432, DB: fm_db)"


# --- REDIS ---
log "Installing Redis..."
brew install redis

log "Starting Redis service..."
brew services restart redis
success "Redis started (Port: 6379)"


# --- MINIO (S3) ---
log "Installing MinIO server and client..."
brew install minio/stable/minio
brew install minio/stable/mc

log "Starting MinIO..."
# We use brew services, but note that custom env vars for MinIO 
# are often managed via plist or shell exports.
# To match your docker config, we set the root credentials.
export MINIO_ROOT_USER=minioadmin
export MINIO_ROOT_PASSWORD=minioadmin

# Restarting to ensure it picks up environment (may require manual plist edit for persistence)
brew services restart minio

log "Waiting for MinIO to start..."
sleep 5

log "Configuring MinIO buckets (fm-media)..."
mc alias set local http://127.0.0.1:9000 minioadmin minioadmin
mc mb --ignore-existing local/fm-media
mc anonymous set download local/fm-media
success "MinIO configured (API: 9000, Console: 9001)"

# --- SUMMARY ---
echo "------------------------------------------------------------"
success "All services are installed and running!"
log "PostgreSQL: localhost:5432 (User: user, DB: fm_db)"
log "Redis:      localhost:6379"
log "MinIO:      localhost:9000 (Console: localhost:9001)"
echo "------------------------------------------------------------"