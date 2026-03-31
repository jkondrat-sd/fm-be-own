# Facility Management Application — Master Planning Document

> **Purpose**: This is the single source of truth for all AI agents working on any part of the FM service stack. Every agent MUST read this document first to understand the full system architecture, inter-service contracts, and cross-cutting concerns before making any decisions.

---

## 1. Product Vision

A facility management platform enabling companies to manage field employees, track attendance via geofencing and facial recognition, report site updates, run compliant payroll (Indian statutory: PF, ESIC, TDS, PT), and provide real-time operations monitoring. Designed for **1,000–3,000 active users at launch**, scaling to 10,000+.

---

## 2. Service Stack Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│                        CLIENTS / CONSUMERS                         │
├──────────────┬──────────────┬──────────────┬───────────────────────-┤
│  Android App │  Web Portal  │ Client Portal│   External Systems    │
│  (Employee)  │ (Admin/Ops/  │ (Attendance  │   (Bank files, PF/    │
│              │  Payroll/    │  Reports)    │    ESIC portals)      │
│              │  Super Admin)│              │                       │
└──────┬───────┴──────┬───────┴──────┬───────┴───────────┬───────────┘
       │              │              │                   │
       ▼              ▼              ▼                   ▼
┌─────────────────────────────────────────────────────────────────────┐
│                        API GATEWAY (NGINX)                         │
│              HTTPS / SSL / Rate Limiting / CORS                    │
└──────────────────────────────┬──────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────────┐
│                     BACKEND API (Node.js / NestJS)                 │
│                                                                     │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ │
│  │   Auth   │ │Attendance│ │ Payroll  │ │   Ops    │ │  Admin   │ │
│  │ Module   │ │ Module   │ │ Module   │ │ Module   │ │ Module   │ │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘ └──────────┘ │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐              │
│  │  Site    │ │ Employee │ │  Notif.  │ │  Media   │              │
│  │ Updates  │ │ Onboard  │ │ Service  │ │ Service  │              │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘              │
└────────┬────────────┬────────────┬────────────┬─────────────────────┘
         │            │            │            │
    ┌────▼────┐  ┌────▼────┐ ┌────▼────┐ ┌────▼─────┐
    │PostgreSQL│  │  Redis  │ │ BullMQ  │ │Object    │
    │+ PostGIS │  │ Cache   │ │  Jobs   │ │Storage   │
    │          │  │         │ │         │ │(R2/S3)   │
    └──────────┘  └─────────┘ └─────────┘ └──────────┘
```

### 2.1 Service Registry

| Service              | Tech Stack                      | Purpose                                              | Port (Dev) |
|----------------------|---------------------------------|------------------------------------------------------|------------|
| **Android App**      | Kotlin, Jetpack Compose, Hilt   | Employee mobile app: attendance, tasks, site updates  | N/A        |
| **Web Portal**       | React (Vite), TypeScript        | Admin, Ops Dashboard, Payroll, Super Admin            | 3000       |
| **Client Portal**    | React (Vite), TypeScript        | Client-facing attendance reports (read-only)          | 3001       |
| **Backend API**      | Node.js, NestJS, TypeScript     | Core business logic, REST API                         | 8080       |
| **Database**         | PostgreSQL 16 + PostGIS 3.4     | Primary data store with geospatial support            | 5432       |
| **Cache**            | Redis 7                         | Session cache, rate limiting, real-time data          | 6379       |
| **Job Queue**        | BullMQ (on Redis)               | Background jobs: payroll, cleanup, heatmaps           | —          |
| **Object Storage**   | Cloudflare R2 / AWS S3          | Images, videos, documents                             | —          |
| **Reverse Proxy**    | NGINX                           | SSL termination, routing, rate limiting               | 80/443     |
| **Push Notifications** | Firebase Cloud Messaging (FCM) | Shift reminders, geofence alerts, admin broadcasts    | —          |

---

## 3. Technology Decisions & Rationale

### 3.1 Backend: NestJS (TypeScript)
- **Why**: Modular architecture with dependency injection, built-in validation (class-validator), guards, interceptors. Perfect for a multi-module platform.
- **Impact on all agents**: Every client must conform to the REST API contracts defined by NestJS controllers. DTOs are the canonical data shape.
- **ORM**: Prisma — type-safe queries, auto-generated types shared across backend modules, easy migrations.

### 3.2 Mobile: Kotlin + Jetpack Compose
- **Why**: Modern Android-first development, excellent performance, coroutine-based async for background location tracking.
- **Impact on all agents**: Mobile consumes the same REST API endpoints as Web Portal. Shared API contract is critical.
- **Offline-first**: Mobile MUST cache attendance punches locally (Room DB) and sync when connectivity resumes. Backend MUST handle idempotent punch submissions.

### 3.3 Web: React + Vite + TypeScript
- **Why**: Fast build times (Vite), strong typing (TypeScript), rich ecosystem for dashboards (charts, maps, data tables).
- **Impact on all agents**: Web Portal and Client Portal share a common UI component library and API client.
- **State Management**: Zustand (lightweight, no boilerplate) + TanStack Query (server state caching, auto-refetch).

### 3.4 Database: PostgreSQL + PostGIS
- **Why**: PostGIS enables geospatial queries (geofence containment checks `ST_Contains`, distance calculations, heatmap aggregation) without external services.
- **Impact on all agents**: All location data is stored as `geography(Point, 4326)`. Mobile sends `{lat, lng, accuracy, timestamp}`. Backend validates and stores.

### 3.5 Cache & Queue: Redis + BullMQ
- **Why**: Single Redis instance serves both caching and job queuing, reducing infrastructure complexity.
- **Impact on all agents**: Real-time employee location is in Redis (TTL-based), not PostgreSQL. Dashboard reads from Redis for live tracking, PostgreSQL for historical data.

---

## 4. Cross-Cutting Concerns — ALL AGENTS MUST FOLLOW

### 4.1 Authentication & Authorization
```
┌─────────────────────────────────────────────────┐
│              Auth Flow                           │
│                                                   │
│  Mobile:  OTP Login → JWT (access + refresh)     │
│  Web:     Email/Pass Login → JWT (access + refresh)│
│  Client:  Email/Pass Login → JWT (access only)   │
│                                                   │
│  Roles: EMPLOYEE, SUPERVISOR, ADMIN, SUPER_ADMIN,│
│          CLIENT                                   │
│                                                   │
│  Guards: @Roles() decorator on NestJS controllers │
│  Mobile: Store tokens in EncryptedSharedPrefs     │
│  Web:    Store access token in memory,            │
│          refresh token in httpOnly cookie          │
└─────────────────────────────────────────────────┘
```

- **JWT payload**: `{ sub: userId, role: Role, siteIds: string[], iat, exp }`
- **Access token TTL**: 15 minutes
- **Refresh token TTL**: 7 days (mobile: 30 days)
- **All agents**: Never store access tokens in localStorage. Mobile uses EncryptedSharedPreferences. Web uses in-memory variable.

### 4.2 API Contract Standards
- **Base URL**: `https://api.fm.example.com/v1`
- **Versioning**: URL-based (`/v1/`, `/v2/`). Never break existing contracts.
- **Request format**: JSON, `Content-Type: application/json`
- **Response envelope**:
```json
{
  "success": true,
  "data": { ... },
  "meta": { "page": 1, "limit": 20, "total": 150 },
  "error": null
}
```
- **Error envelope**:
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "GEOFENCE_VIOLATION",
    "message": "Employee is outside designated site boundary",
    "details": { "distance_meters": 150 }
  }
}
```
- **Pagination**: Cursor-based for large lists (attendance logs, location history). Offset-based for admin tables.
- **Timestamps**: Always ISO 8601 with timezone (`2026-03-30T10:30:00+05:30`). Store as `timestamptz` in PostgreSQL.
- **IDs**: UUIDs (v4) for all entities. Never expose sequential IDs.

### 4.3 Error Codes (Shared Across Stack)
All clients MUST handle these error codes:

| Code                      | HTTP | Context                            |
|---------------------------|------|------------------------------------|
| `AUTH_TOKEN_EXPIRED`      | 401  | Trigger token refresh              |
| `AUTH_UNAUTHORIZED`       | 403  | Show "access denied" UI            |
| `GEOFENCE_VIOLATION`      | 422  | Punch rejected, show map to user   |
| `FACE_MISMATCH`           | 422  | Facial recognition failed          |
| `SHIFT_NOT_STARTED`       | 422  | Too early/late for shift punch     |
| `DUPLICATE_PUNCH`         | 409  | Idempotency — already punched      |
| `PAYROLL_LOCKED`          | 423  | Payroll period is locked           |
| `SITE_INACTIVE`           | 410  | Site has been deactivated          |
| `RATE_LIMITED`             | 429  | Back off and retry                 |
| `VALIDATION_ERROR`        | 400  | Show field-level errors            |
| `SERVER_ERROR`             | 500  | Generic error screen               |

### 4.4 Multi-Language (i18n)
- **Supported**: English (`en`), Hindi (`hi`), Marathi (`mr`)
- **Mobile**: Android string resources (`strings.xml` per locale)
- **Web**: `react-i18next` with JSON translation files
- **Backend**: Error messages returned with `code` (machine-readable). Human-readable messages are client-side responsibility.
- **API**: Client sends `Accept-Language` header. Backend uses it only for email/SMS templates.

### 4.5 Media Handling
- **Upload flow**: Client → Presigned URL from API → Direct upload to R2/S3 → Confirm upload to API
- **Max file sizes**: Images: 5MB, Videos: 50MB, Documents (PDF): 10MB
- **Supported formats**: Images: JPEG, PNG, WebP. Videos: MP4, MOV. Documents: PDF
- **Thumbnails**: Generated on upload by a BullMQ job (sharp for images, ffmpeg for video)
- **CDN**: Serve all media via CDN URL. Never expose raw storage URLs to clients.
- **30-day retention**: Location snapshots and site update images older than 30 days are purged by a scheduled BullMQ job. Payroll documents are retained indefinitely.

### 4.6 Real-Time Data Strategy
```
┌──────────────┐     ┌──────────────┐     ┌──────────────┐
│  Mobile App  │────▶│  Backend API │────▶│    Redis     │
│ (Location    │ POST│ (Validate +  │ SET │ (TTL: 120s)  │
│  every 30s)  │     │  Store)      │     │              │
└──────────────┘     └──────┬───────┘     └──────┬───────┘
                            │                     │
                            │ INSERT (sampled)    │ GET (live)
                            ▼                     ▼
                     ┌──────────────┐     ┌──────────────┐
                     │  PostgreSQL  │     │  Ops Dashboard│
                     │ (Historical) │     │  (Polling 10s)│
                     └──────────────┘     └──────────────┘
```
- **Mobile** sends location every 30 seconds when on-shift.
- **Backend** writes to Redis (live tracking, TTL 120s) AND samples to PostgreSQL (every 5 min for heatmaps/history).
- **Dashboard** polls Redis via API every 10 seconds for live view. Consider WebSocket upgrade in V2.
- **Geofence check**: Happens on backend using PostGIS `ST_DWithin()` on every location update. If outside for >5 minutes, trigger alert via FCM + dashboard.

### 4.7 Data Retention & Privacy
- **Location data**: 30-day rolling retention in PostgreSQL. Aggregated heatmap data retained for 1 year.
- **Site update media**: 30 days, then purged from object storage.
- **Payroll data**: Retained indefinitely (legal requirement).
- **Employee PII** (Aadhaar, PAN): Encrypted at rest (PostgreSQL pgcrypto), encrypted in transit (HTTPS). Access logging on all PII queries.
- **Audit trail**: All admin actions logged with `userId`, `action`, `targetEntity`, `timestamp`, `ipAddress`.

---

## 5. Database Schema — Key Entities

> Full schema will be in `context/BACKEND_CONTEXT.md`. Below is the entity relationship overview for all agents.

```
┌──────────┐    ┌──────────┐    ┌──────────┐
│  users   │───▶│ employees│───▶│  shifts  │
│          │    │          │    │          │
│ id (PK)  │    │ userId   │    │ employeeId│
│ role     │    │ siteId   │    │ startTime│
│ phone    │    │ aadhar*  │    │ endTime  │
│ email    │    │ pan*     │    │ status   │
└──────────┘    └────┬─────┘    └──────────┘
                     │
          ┌──────────┼──────────┐
          ▼          ▼          ▼
    ┌──────────┐┌──────────┐┌──────────┐
    │attendance││site_     ││employee_ │
    │_logs     ││updates   ││documents │
    │          ││          ││          │
    │ punchIn  ││ text     ││ type     │
    │ punchOut ││ mediaUrl ││ fileUrl  │
    │ location ││ siteId   ││ verified │
    │ faceMatch││ timestamp││          │
    └──────────┘└──────────┘└──────────┘

┌──────────┐    ┌──────────┐    ┌──────────┐
│  sites   │    │ payroll_ │    │ salary_  │
│          │    │ runs     │    │ components│
│ id (PK)  │    │          │    │          │
│ name     │    │ month    │    │ basic    │
│ geofence │    │ year     │    │ hra      │
│ (polygon)│    │ status   │    │ pf       │
│ clientId │    │ approvedBy│   │ esic     │
└──────────┘    └──────────┘    │ tds      │
                                └──────────┘
(* = encrypted)
```

---

## 6. Deployment Architecture

```
┌───────────────────── Production VPS ──────────────────────┐
│                                                            │
│  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐     │
│  │  NGINX  │  │ NestJS  │  │ NestJS  │  │  Redis  │     │
│  │ (proxy) │─▶│ API (1) │  │ API (2) │  │         │     │
│  │ :80/443 │  │ :8080   │  │ :8081   │  │ :6379   │     │
│  └─────────┘  └─────────┘  └─────────┘  └─────────┘     │
│                                                            │
│  ┌─────────┐  ┌─────────┐  ┌─────────────────────────┐   │
│  │ BullMQ  │  │PostgreSQL│  │ Web Portal (Static,     │   │
│  │ Workers │  │ + PostGIS│  │ served via NGINX/CDN)   │   │
│  │ (2)     │  │ :5432    │  │                         │   │
│  └─────────┘  └─────────┘  └─────────────────────────┘   │
│                                                            │
│  Managed by: Docker Compose (dev) / PM2 (prod)           │
│  OS: Ubuntu 22.04 LTS                                     │
└────────────────────────────────────────────────────────────┘

External:
  - Cloudflare (DNS, CDN, R2 Object Storage)
  - Firebase (FCM Push Notifications)
  - Play Store (Android APK distribution)
```

- **Dev**: Docker Compose for full stack local development.
- **Staging**: Same VPS layout, separate subdomain (`staging.fm.example.com`).
- **Prod**: PM2 with 2 API instances behind NGINX load balancing. PostgreSQL with daily automated backups. Redis with AOF persistence.

---

## 7. Performance & Scaling Guidelines

| Concern | Strategy | Owner |
|---------|----------|-------|
| **API Response Time** | Target <200ms p95. Use Redis caching for hot paths (employee profile, current shift). | Backend |
| **Location Ingestion** | 3,000 users × 1 req/30s = 100 req/s sustained. Batch-insert to PostgreSQL every 5 min. | Backend |
| **Payroll Computation** | Async via BullMQ. Never compute in API request cycle. Progress via polling. | Backend |
| **Dashboard Live View** | Redis-backed polling (10s). Max 500 concurrent dashboard users. | Backend + Web |
| **Mobile Battery** | Geolocation sampling: 30s on-shift, GPS turned off when off-shift. Use Android WorkManager for reliability. | Mobile |
| **Image Uploads** | Presigned URL → direct to R2/S3. Never proxy through API server. | All clients |
| **Database Indexing** | B-tree on FKs and filter columns. GiST on geofence polygons. Partial indexes on `status` columns. | Backend |

---

## 8. Security Checklist (All Agents)

- [ ] All API calls over HTTPS. No HTTP fallback.
- [ ] JWT tokens validated on every request (NestJS guard).
- [ ] Role-based access on every endpoint.
- [ ] Input validation on every API endpoint (class-validator DTOs).
- [ ] SQL injection prevention (Prisma parameterized queries).
- [ ] Rate limiting on auth endpoints (10 req/min per IP).
- [ ] Rate limiting on location updates (1 req/10s per user).
- [ ] PII fields encrypted at rest (pgcrypto).
- [ ] Audit log for all admin write operations.
- [ ] Mobile: certificate pinning for API domain.
- [ ] Mobile: ProGuard/R8 obfuscation enabled.
- [ ] Web: CSP headers, XSS protection.
- [ ] Media: Presigned URLs expire in 15 minutes.
- [ ] No sensitive data in URL query parameters.
- [ ] All secrets via environment variables, never in code.

---

## 9. Naming Conventions (All Projects)

| Context | Convention | Example |
|---------|-----------|---------|
| **Database tables** | snake_case, plural | `attendance_logs`, `payroll_runs` |
| **Database columns** | snake_case | `punch_in_time`, `is_active` |
| **API endpoints** | kebab-case, plural | `/v1/attendance-logs`, `/v1/payroll-runs` |
| **API query params** | camelCase | `?startDate=2026-03-01&siteId=abc` |
| **JSON response fields** | camelCase | `punchInTime`, `isActive` |
| **TypeScript files** | kebab-case | `attendance.service.ts`, `payroll.controller.ts` |
| **TypeScript classes** | PascalCase | `AttendanceService`, `PayrollController` |
| **Kotlin files** | PascalCase | `AttendanceViewModel.kt` |
| **Kotlin classes** | PascalCase | `AttendanceViewModel` |
| **React components** | PascalCase | `EmployeeList.tsx`, `PayrollDashboard.tsx` |
| **CSS** | BEM or CSS Modules | `.attendance-card__header--active` |
| **Env variables** | SCREAMING_SNAKE | `DATABASE_URL`, `JWT_SECRET` |
| **Git branches** | `type/short-desc` | `feat/geofence-alerts`, `fix/payroll-rounding` |

---

## 10. Repository Structure

```
fm-platform/
├── apps/
│   ├── api/                    # NestJS Backend API
│   ├── web-portal/             # React Web Portal (Admin/Ops/Payroll/SuperAdmin)
│   ├── client-portal/          # React Client Attendance Portal
│   └── mobile/                 # Kotlin Android App
├── packages/
│   ├── shared-types/           # TypeScript types shared between api + web
│   ├── ui-components/          # Shared React component library
│   └── api-client/             # Generated API client (from OpenAPI spec)
├── infrastructure/
│   ├── docker-compose.yml      # Local dev environment
│   ├── nginx/                  # NGINX configs
│   └── scripts/                # Deployment, backup, migration scripts
├── docs/
│   ├── api/                    # OpenAPI/Swagger specs
│   └── architecture/           # Architecture decision records (ADRs)
├── PLANNING.md                 # This file
├── TASK.md                     # Current task tracking
└── README.md                   # Getting started
```

> **Monorepo approach** using **Turborepo** for build orchestration. Shared types ensure API contracts are enforced at compile time between backend and frontend.

---

## 11. Inter-Service Data Flow Summary

### 11.1 Employee Punch-In Flow
```
Mobile → POST /v1/attendance/punch-in { lat, lng, faceImageUrl, timestamp }
  → Backend validates JWT
  → Backend checks shift schedule (is employee supposed to be on-shift?)
  → Backend checks geofence (PostGIS ST_Contains with site polygon)
  → Backend verifies face (compare with stored face embedding)
  → Backend writes attendance_log to PostgreSQL
  → Backend updates Redis (employee status = "on-site")
  → Backend returns { success: true, data: { punchId, punchInTime } }
  → If geofence violation → return error, mobile shows map overlay
  → If face mismatch → return error, mobile prompts retry
```

### 11.2 Payroll Run Flow
```
Web Portal → POST /v1/payroll/run { month, year }
  → Backend creates payroll_run record (status: PROCESSING)
  → Backend enqueues BullMQ job "process-payroll"
  → Returns { jobId } immediately
  → Web polls GET /v1/payroll/run/{jobId}/status
  
BullMQ Worker:
  → Fetches all active employees
  → For each: calculate gross, deductions (PF 12%, ESIC, TDS slab, PT)
  → Generate salary slips
  → Generate bank disbursement file (CSV/Excel)
  → Update payroll_run status to PENDING_APPROVAL
  → Notify admins via FCM/email
  
Web Portal:
  → Admin reviews → POST /v1/payroll/run/{id}/approve
  → Status changes to APPROVED → bank file available for download
```

### 11.3 Real-Time Monitoring Flow
```
Mobile (on-shift) → POST /v1/location/update { lat, lng, battery, signal, airplane }
  → Every 30 seconds
  → Backend writes to Redis (key: "loc:{employeeId}", TTL: 120s)
  → Backend samples to PostgreSQL every 5 minutes
  → Backend checks geofence → if outside for >5 min → FCM alert to supervisor
  → Backend checks airplane mode → if true → flag in Redis

Ops Dashboard → GET /v1/location/live?siteId=xyz
  → Every 10 seconds (polling)
  → Backend reads all "loc:*" keys from Redis for site
  → Returns array of { employeeId, lat, lng, battery, signal, lastSeen }
  
Ops Dashboard → GET /v1/location/heatmap?siteId=xyz&date=2026-03-30
  → Backend queries aggregated PostgreSQL data
  → Returns GeoJSON for heatmap overlay
```

---

## 12. V2 Features (Out of Scope for V1, but Design For)

These features are NOT in V1, but agents MUST design schemas, APIs, and components to be **extensible** for these:

| Feature | Design Consideration |
|---------|---------------------|
| **iOS App** | API is platform-agnostic. No Android-specific assumptions in backend. |
| **Visitor Management** | `sites` table should support `site_type` enum. Visitors will link to sites. |
| **Complaint/Ticket System** | Design employee/site models to be referenceable by a future `tickets` table. |
| **WebSocket live tracking** | Redis pub/sub is already in place. Dashboard polling can be replaced. |
| **Multi-tenant** | Add `organizationId` to all tables from V1. Even if only one org in V1. |
| **Biometric integration** | Face embedding storage should be in a separate `biometrics` table, not embedded in `employees`. |

---

## 13. Environment Variables (All Services)

```env
# Database
DATABASE_URL=postgresql://user:pass@localhost:5432/fm_db
REDIS_URL=redis://localhost:6379

# Auth
JWT_SECRET=<random-256-bit>
JWT_ACCESS_TTL=15m
JWT_REFRESH_TTL=7d
OTP_EXPIRY=300

# Storage
S3_ENDPOINT=https://xxx.r2.cloudflarestorage.com
S3_ACCESS_KEY=<key>
S3_SECRET_KEY=<secret>
S3_BUCKET=fm-media
CDN_BASE_URL=https://media.fm.example.com

# Push Notifications
FCM_SERVER_KEY=<firebase-key>
FCM_PROJECT_ID=<firebase-project>

# Payroll
PF_RATE=0.12
ESIC_THRESHOLD=21000
TDS_SLABS=<json-encoded-slabs>

# App
NODE_ENV=production
API_PORT=8080
CORS_ORIGINS=https://portal.fm.example.com,https://client.fm.example.com
LOG_LEVEL=info
```

---

## 14. Quality & Testing Strategy

| Layer | Tool | Minimum Coverage |
|-------|------|-----------------|
| **Backend Unit** | Jest | 80% on services/modules |
| **Backend Integration** | Jest + Supertest | All endpoints |
| **Backend E2E** | Jest + TestContainers | Critical flows (auth, punch, payroll) |
| **Web Unit** | Vitest | 70% on components |
| **Web E2E** | Playwright | All user journeys |
| **Mobile Unit** | JUnit 5 + MockK | 70% on ViewModels |
| **Mobile UI** | Espresso / Compose Testing | Critical screens |
| **API Contract** | OpenAPI validation | All DTOs match spec |

---

## 15. Communication Protocol Between Agents

When AI agents are building individual services, they MUST:

1. **Read this PLANNING.md first** — understand the full stack.
2. **Read their service-specific context file** — for focused guidance.
3. **Never change shared contracts** (API response format, error codes, auth flow) without updating this file.
4. **Use shared types** from `packages/shared-types/` for any cross-service data.
5. **Document new endpoints** in the OpenAPI spec before implementing.
6. **Consider upstream/downstream** — "If I change this, does it break the mobile app? The dashboard? Payroll?"
7. **Design for V2** — even if not building it now.

---

## 16. Deployment Must-Dos

The following cross-cutting risks were identified during a tech stack scalability and reliability audit:
- **Infrastructure Redundancy**: Avoid single VPS/Database failure points. Plan for high availability (e.g., PostgreSQL replication, standby VPS) before full launch.
- **CI/CD Pipeline**: Implement automated deployments to eliminate manual PM2 push errors.
- **Security Posture**: Implement proper Content Security Policy (CSP) headers on all web portals.
- **Mobile Reliability**: Develop mitigations for OEM-specific background service kills (Xiaomi, Oppo, Vivo) which otherwise break location tracking.
- **Performance Optimizations**: Shift Redis location `SCAN` to O(1) lookups, add map marker clustering for 3,000+ entities, and consider table partitioning for `location_history`.
