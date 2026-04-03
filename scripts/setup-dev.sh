#!/usr/bin/env bash
# ============================================================
# FM-BE — One-time Development Environment Setup
# ============================================================
# Installs Docker, Docker Compose, Maven wrapper, Newman, and
# generates the .env.dev file with sensible defaults.
# Run once: ./scripts/setup-dev.sh
# ============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

log()   { echo -e "${GREEN}[setup]${NC} $*"; }
warn()  { echo -e "${YELLOW}[warn]${NC}  $*"; }
fail()  { echo -e "${RED}[error]${NC} $*"; exit 1; }

# ── 1. Docker ────────────────────────────────────────────────
install_docker() {
    if command -v docker &>/dev/null; then
        log "Docker already installed: $(docker --version)"
        return
    fi

    log "Installing Docker..."
    if [ -f /etc/os-release ]; then
        . /etc/os-release
        case "$ID" in
            ubuntu|debian)
                sudo apt-get update -qq
                sudo apt-get install -y -qq ca-certificates curl gnupg lsb-release
                sudo install -m 0755 -d /etc/apt/keyrings
                curl -fsSL "https://download.docker.com/linux/$ID/gpg" | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
                sudo chmod a+r /etc/apt/keyrings/docker.gpg
                echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/$ID $(lsb_release -cs) stable" \
                    | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
                sudo apt-get update -qq
                sudo apt-get install -y -qq docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
                ;;
            fedora|rhel|centos)
                sudo dnf install -y dnf-plugins-core
                sudo dnf config-manager --add-repo https://download.docker.com/linux/fedora/docker-ce.repo
                sudo dnf install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
                ;;
            *)
                fail "Unsupported distro: $ID. Install Docker manually: https://docs.docker.com/engine/install/"
                ;;
        esac
    else
        fail "Cannot detect OS. Install Docker manually: https://docs.docker.com/engine/install/"
    fi

    sudo systemctl enable --now docker
    sudo usermod -aG docker "$USER"
    log "Docker installed. You may need to log out/in for group changes."
}

# ── 2. Docker Compose (v2 plugin) ───────────────────────────
check_docker_compose() {
    if docker compose version &>/dev/null 2>&1; then
        log "Docker Compose plugin: $(docker compose version --short 2>/dev/null || echo 'available')"
    elif command -v docker-compose &>/dev/null; then
        log "Docker Compose standalone: $(docker-compose --version)"
    else
        warn "Docker Compose not found. It should come with Docker CE. Try: sudo apt install docker-compose-plugin"
    fi
}

# ── 3. Java 17 ───────────────────────────────────────────────
check_java() {
    if command -v java &>/dev/null; then
        JAVA_VER=$(java -version 2>&1 | head -1)
        log "Java: $JAVA_VER"
    else
        fail "Java 17 required. Install: sudo apt install openjdk-17-jdk"
    fi
}

# ── 4. Maven Wrapper ─────────────────────────────────────────
setup_maven_wrapper() {
    cd "$PROJECT_ROOT"
    if [ -f mvnw ]; then
        log "Maven wrapper already exists."
        return
    fi

    if command -v mvn &>/dev/null; then
        log "Generating Maven wrapper..."
        mvn wrapper:wrapper -Dmaven=3.9.9 -q 2>/dev/null || {
            warn "mvn wrapper:wrapper failed. Creating wrapper manually..."
            mkdir -p .mvn/wrapper
            cat > .mvn/wrapper/maven-wrapper.properties <<'PROPS'
distributionUrl=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.9/apache-maven-3.9.9-bin.zip
wrapperUrl=https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.3.2/maven-wrapper-3.3.2.jar
PROPS
            curl -sLo .mvn/wrapper/maven-wrapper.jar \
                "https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.3.2/maven-wrapper-3.3.2.jar"
            cat > mvnw <<'MVNW'
#!/bin/sh
exec mvn "$@"
MVNW
            chmod +x mvnw
            log "Maven wrapper created (thin shim → system mvn)."
        }
    else
        fail "Maven not found. Install: sudo apt install maven"
    fi
}

# ── 5. Node.js + Newman (Postman CLI) ───────────────────────
install_newman() {
    if command -v newman &>/dev/null; then
        log "Newman already installed: $(newman --version)"
        return
    fi

    if command -v npm &>/dev/null; then
        log "Installing Newman (Postman CLI runner)..."
        sudo npm install -g newman newman-reporter-htmlextra 2>/dev/null || {
            npm install -g newman newman-reporter-htmlextra
        }
        log "Newman installed: $(newman --version)"
    elif command -v node &>/dev/null; then
        warn "Node.js found but npm missing. Install npm, then: npm install -g newman newman-reporter-htmlextra"
    else
        warn "Node.js not found. Newman requires Node.js."
        warn "Install Node.js: curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash - && sudo apt install -y nodejs"
        warn "Then: npm install -g newman newman-reporter-htmlextra"
    fi
}

# ── 6. Environment File ─────────────────────────────────────
create_env_file() {
    local ENV_FILE="$PROJECT_ROOT/.env.dev"
    if [ -f "$ENV_FILE" ]; then
        log ".env.dev already exists. Skipping."
        return
    fi

    log "Creating .env.dev..."
    cat > "$ENV_FILE" <<'ENV'
# ============================================================
# FM-BE — Local Development Environment Variables
# ============================================================
# Source this before running the server:
#   source .env.dev
# Or pass to Maven: mvn spring-boot:run (defaults in application-dev.yml)
# ============================================================

# Database (matches docker-compose.yml)
DATABASE_URL=jdbc:postgresql://localhost:5432/fm_db
DATABASE_USERNAME=user
DATABASE_PASSWORD=password

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=

# Storage (MinIO — S3-compatible)
STORAGE_ENDPOINT=http://localhost:9000
STORAGE_ACCESS_KEY=minioadmin
STORAGE_SECRET_KEY=minioadmin
STORAGE_BUCKET=fm-media
STORAGE_REGION=auto

# JWT
JWT_SECRET=dev-secret-key-must-be-at-least-256-bits-long-for-hs256-algorithm-padding
JWT_ACCESS_EXPIRATION=3600000
JWT_REFRESH_EXPIRATION=604800000

# Server
SERVER_PORT=8080
SPRING_PROFILES_ACTIVE=dev

# FCM (disabled in dev)
FCM_ENABLED=false
FCM_CREDENTIALS_PATH=

# PII Encryption (dev key — never use in production)
PII_ENCRYPTION_KEY=dev-pii-key-32chars-longenough!!

# Async
ASYNC_CORE_POOL=2
ASYNC_MAX_POOL=4
ASYNC_QUEUE_CAPACITY=50
ENV

    log ".env.dev created."
}

# ── 7. Gitignore check ──────────────────────────────────────
check_gitignore() {
    local GI="$PROJECT_ROOT/.gitignore"
    local additions=()

    for pattern in ".env.dev" ".env.local" "tests/postman/reports/"; do
        if ! grep -qF "$pattern" "$GI" 2>/dev/null; then
            additions+=("$pattern")
        fi
    done

    if [ ${#additions[@]} -gt 0 ]; then
        log "Adding entries to .gitignore..."
        echo "" >> "$GI"
        echo "# Dev environment & test reports" >> "$GI"
        for p in "${additions[@]}"; do
            echo "$p" >> "$GI"
            log "  + $p"
        done
    fi
}

# ── Run ──────────────────────────────────────────────────────
main() {
    echo ""
    echo "========================================"
    echo "  FM-BE Dev Environment Setup"
    echo "========================================"
    echo ""

    install_docker
    check_docker_compose
    check_java
    setup_maven_wrapper
    install_newman
    create_env_file
    check_gitignore

    echo ""
    log "========================================="
    log "  Setup complete! Next steps:"
    log "========================================="
    log "  1. Start infra:   ./scripts/start-infra.sh"
    log "  2. Start server:  ./scripts/start-server.sh"
    log "  3. Check health:  ./scripts/health-check.sh"
    log "  4. Run API tests: ./scripts/run-api-tests.sh"
    echo ""
}

main "$@"
