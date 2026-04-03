# FM-BE — Facility Management Backend

Central backend API for the Facility Management Platform. Serves the **Flutter Mobile App** (field employees), **Web Portal** (Admin/Ops/Payroll/Super Admin), and **Client Portal** (read-only attendance reports).

## Tech Stack

| Technology | Purpose |
|---|---|
| Java 17 LTS / Spring Boot 3.4.0 | Application framework |
| PostgreSQL 16 + PostGIS 3.4 | Primary data store with geospatial support |
| Redis 7 | Caching, live location, rate limiting |
| Flyway | SQL database migrations |
| Spring Security + JWT | Authentication & authorization |
| AWS SDK v2 (S3-compatible) | Presigned URL media upload (Cloudflare R2 / MinIO) |
| Firebase Admin SDK | FCM push notifications |
| Springdoc OpenAPI | Auto-generated API documentation |

## Prerequisites

- Java 17+
- Maven 3.9+
- Docker & Docker Compose (for local infrastructure)

## Quick Start

### 1. Start Infrastructure

```bash
docker compose up -d
```

This starts PostgreSQL+PostGIS, Redis, and MinIO for local development.

### 2. Run the Application

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

Or build and run the JAR:

```bash
mvn clean package -DskipTests
java -jar target/fm-be-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev
```

### 3. Access

| Resource | URL |
|---|---|
| API Base | http://localhost:8080 |
| Health Check | http://localhost:8080/v1/health |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| API Docs (JSON) | http://localhost:8080/v3/api-docs |
| MinIO Console | http://localhost:9001 (minioadmin/minioadmin) |

## Environment Variables

All secrets and environment-specific values are sourced from environment variables. Defaults are set for local development in `application.yml`.

| Variable | Default | Description |
|---|---|---|
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/fm_db` | PostgreSQL JDBC URL |
| `DATABASE_USERNAME` | `user` | Database username |
| `DATABASE_PASSWORD` | `password` | Database password |
| `REDIS_HOST` | `localhost` | Redis host |
| `REDIS_PORT` | `6379` | Redis port |
| `REDIS_PASSWORD` | _(empty)_ | Redis password |
| `JWT_SECRET` | _(dev default)_ | JWT signing secret (min 256 bits) |
| `JWT_ACCESS_EXPIRATION` | `900000` | Access token TTL (ms, default 15min) |
| `JWT_REFRESH_EXPIRATION` | `604800000` | Refresh token TTL (ms, default 7d) |
| `STORAGE_ENDPOINT` | `http://localhost:9000` | S3/R2 endpoint |
| `STORAGE_ACCESS_KEY` | `minioadmin` | Storage access key |
| `STORAGE_SECRET_KEY` | `minioadmin` | Storage secret key |
| `STORAGE_BUCKET` | `fm-media` | Media storage bucket name |
| `FCM_ENABLED` | `false` | Enable Firebase push notifications |
| `FCM_CREDENTIALS_PATH` | _(empty)_ | Path to Firebase service account JSON |
| `CORS_ORIGINS` | `http://localhost:3000,...` | Allowed CORS origins |
| `SERVER_PORT` | `8080` | Server port |

## API Modules

| Module | Base Path | Description |
|---|---|---|
| Health | `/v1/health` | System health checks (public) |
| Auth | `/v1/auth` | OTP login, email/password login, token refresh |
| Users | `/v1/admin/users` | Admin user management (ADMIN, SUPER_ADMIN) |
| Employees | `/v1/employees` | Employee onboarding, profiles, documents |
| Sites | `/v1/sites` | Site CRUD, geofence polygon management |
| Attendance | `/v1/attendance` | Punch in/out, attendance reports |
| Location | `/v1/location` | Live location tracking, heatmaps, geofence alerts |
| Shifts | `/v1/shifts` | Shift scheduling and assignment |
| Site Updates | `/v1/site-updates` | Field status reports with media |
| Payroll | `/v1/payroll` | Salary structures, payroll runs (PF/ESIC/TDS/PT) |
| Notifications | `/v1/notifications` | FCM push, in-app notifications |
| Media | `/v1/media` | Presigned URL upload, thumbnail generation |
| Admin | `/v1/admin` | Audit logs, dashboard stats, data export, config |

## Production Deployment

### Build Docker Image

```bash
docker build -t fm-be:latest .
```

### Run with Docker

```bash
docker run -d \
  --name fm-be \
  -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e DATABASE_URL=jdbc:postgresql://db-host:5432/fm_db \
  -e DATABASE_USERNAME=prod_user \
  -e DATABASE_PASSWORD=<secret> \
  -e REDIS_HOST=redis-host \
  -e JWT_SECRET=<256-bit-secret> \
  -e STORAGE_ENDPOINT=https://r2.example.com \
  -e STORAGE_ACCESS_KEY=<key> \
  -e STORAGE_SECRET_KEY=<secret> \
  -e FCM_ENABLED=true \
  -e FCM_CREDENTIALS_PATH=/app/firebase-credentials.json \
  fm-be:latest
```

### Production Checklist

- [ ] `SPRING_PROFILES_ACTIVE=prod` is set
- [ ] `JWT_SECRET` is a cryptographically strong 256-bit+ secret
- [ ] Database credentials use a dedicated service account
- [ ] Flyway migrations have been reviewed
- [ ] `FCM_CREDENTIALS_PATH` points to a valid Firebase service account
- [ ] CORS origins are restricted to production domains
- [ ] HTTPS is enforced by the reverse proxy (NGINX)
- [ ] Health check endpoint is configured in load balancer

## Testing

```bash
# Run all tests
mvn test

# Run with coverage report
mvn test jacoco:report
# Report at: target/site/jacoco/index.html
```

## Project Structure

```
src/main/java/com/fm/be/
├── common/          # Cross-cutting: security, config, DTOs, exceptions, interceptors
├── modules/         # Feature modules (auth, users, employees, sites, attendance, ...)
└── scheduler/       # Background jobs (payroll, heatmap, cleanup, shift reminders)

src/main/resources/
├── application.yml      # Shared configuration
├── application-dev.yml  # Development profile
├── application-prod.yml # Production profile
└── db/migration/        # Flyway SQL migrations (V1 through V17)
```
