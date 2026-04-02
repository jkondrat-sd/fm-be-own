# PLANNING.md — fm-be Backend Service

> **Last Updated**: 2026-04-02  
> **Status**: Active — V1 Development  
> **Source of Truth**: All architectural decisions derive from `context/PLANNING.md` and `context/BACKEND_CONTEXT.md`.

---

## 1. Project Overview

The `fm-be` service is the **central backend API** for the Facility Management Platform. It is the sole data gateway for all clients — **Flutter Mobile App** (field employees), **Web Portal** (Admin / Ops / Payroll / Super Admin), and **Client Portal** (read-only attendance reports). No client connects directly to the database, Redis, or object storage.

### Key Capabilities (V1)
| Capability | Module |
|---|---|
| OTP + Email/Password auth, JWT issue/refresh | `auth` |
| Employee onboarding, document management, face registration | `employees` |
| Geofence-based attendance punch with facial verification | `attendance` |
| Live location ingestion, heatmap aggregation, geofence alerts | `location` |
| Site CRUD, geofence polygon management | `sites` |
| Shift scheduling, employee assignment | `shifts` |
| Field site status reporting with media attachments | `siteupdates` |
| Indian statutory payroll (PF, ESIC, TDS, PT), bank files | `payroll` |
| FCM push, email, SMS notifications | `notifications` |
| Presigned URL media upload, thumbnail generation | `media` |
| User/role management, audit logs, config, data export | `admin` |
| Data retention cleanup, heatmap aggregation | Scheduler jobs |

### Scale Target
- **1,000–3,000 active users at launch** (field employees)
- **100 req/s sustained** (location updates every 30s from all users)
- **< 200ms p95** response time for hot-path endpoints

---

## 2. Tech Stack

```
Java 17 LTS / Spring Boot 3.4.0
├── Maven                          — Build & dependency management
├── Spring Boot Starter Web        — REST controllers, JSON serialization
├── Spring Data JPA + Hibernate    — ORM, JPA repositories
├── Hibernate Spatial              — PostGIS geospatial queries
├── Jakarta Bean Validation        — DTO validation (@NotNull, @Size, etc.)
├── Spring Security + JWT          — Authentication & authorization
├── Spring Scheduler + @Async      — Background jobs (payroll, cleanup, heatmap)
├── Spring Data Redis (Lettuce)    — Caching, live location, rate limiting
├── Flyway                         — Database migration management
├── Lombok                         — Boilerplate reduction (@Data, @Builder, etc.)
├── Springdoc OpenAPI (swagger)    — Auto-generated API documentation
├── Thumbnailator                  — Image resizing/thumbnail generation
├── AWS SDK v2 (S3-compatible)     — Presigned URL generation for R2/S3
├── Firebase Admin SDK             — FCM push notification delivery
├── SLF4J + Logback                — Structured JSON logging with MDC
├── JaCoCo                         — Code coverage reporting (target: 85%+)
├── JUnit 5 + Mockito              — Unit testing
├── Spring Boot Test + MockMvc     — Integration testing
└── Testcontainers                 — E2E testing with real PostgreSQL + Redis
```

### External Dependencies
| Dependency | Purpose |
|---|---|
| PostgreSQL 16 + PostGIS 3.4 | Primary data store with geospatial support |
| Redis 7 | Caching, live location, rate limiting counters |
| Cloudflare R2 / AWS S3 | Object storage for images, videos, documents |
| Firebase Cloud Messaging | Push notifications to mobile devices |
| SMTP / SMS Gateway | OTP delivery, payroll notifications |
| MinIO (dev only) | Local S3-compatible storage for development |

---

## 3. Package Structure

```
src/main/java/com/fm/be/
├── FmBackendApplication.java              # @SpringBootApplication entry point
│
├── common/                                # Cross-cutting shared code
│   ├── config/                            # Configuration classes
│   │   ├── SecurityConfig.java            # Spring Security filter chain, JWT
│   │   ├── RedisConfig.java               # Redis connection & template config
│   │   ├── AsyncConfig.java               # @Async thread pool config
│   │   ├── CorsConfig.java               # CORS policy for web/mobile origins
│   │   ├── OpenApiConfig.java            # Swagger/OpenAPI setup
│   │   └── JacksonConfig.java            # ObjectMapper customizations (ISO 8601)
│   ├── security/                          # Auth infrastructure
│   │   ├── JwtTokenProvider.java          # Token generation & validation
│   │   ├── JwtAuthenticationFilter.java   # OncePerRequestFilter for JWT
│   │   └── UserPrincipal.java            # Spring Security principal
│   ├── dto/                               # Shared DTOs
│   │   ├── ApiResponse.java              # Standard response envelope
│   │   ├── ApiError.java                 # Error details DTO
│   │   ├── PaginationMeta.java           # Page metadata
│   │   └── PageRequestDto.java           # Common pagination request
│   ├── exception/                         # Exception handling
│   │   ├── GlobalExceptionHandler.java    # @ControllerAdvice
│   │   ├── ResourceNotFoundException.java
│   │   ├── DuplicateResourceException.java
│   │   ├── BusinessRuleException.java     # Domain-specific errors
│   │   └── RateLimitExceededException.java
│   ├── interceptor/                       # Request interceptors
│   │   ├── ResponseEnvelopeAdvice.java    # Auto-wraps responses in ApiResponse
│   │   ├── LoggingInterceptor.java        # MDC correlation ID, request timing
│   │   ├── RateLimitInterceptor.java      # Redis-backed rate limiting
│   │   └── TenantInterceptor.java         # Multi-tenancy org filter injection
│   ├── annotation/                        # Custom annotations
│   │   ├── CurrentUser.java              # Extract authenticated user
│   │   └── ApiPaginated.java            # Mark paginated endpoints
│   ├── constants/                         # Enums and constants
│   │   ├── Role.java                     # EMPLOYEE, SUPERVISOR, ADMIN, SUPER_ADMIN, CLIENT
│   │   ├── ErrorCode.java               # All error codes from PLANNING.md
│   │   ├── PunchType.java               # IN, OUT
│   │   ├── PayrollStatus.java           # DRAFT > PROCESSING > PENDING_APPROVAL > APPROVED/REJECTED > DISBURSED
│   │   └── DocumentType.java           # AADHAAR, PAN, RATION_CARD, etc.
│   └── util/                             # Shared utilities
│       ├── DateTimeUtil.java             # IST timezone helpers
│       └── EncryptionUtil.java           # pgcrypto field encryption
│
├── modules/                              # Feature modules (vertical slices)
│   ├── auth/                             # Authentication & authorization
│   │   ├── AuthController.java
│   │   ├── AuthService.java
│   │   ├── dto/
│   │   │   ├── OtpRequestDto.java
│   │   │   ├── OtpVerifyDto.java
│   │   │   ├── LoginRequestDto.java
│   │   │   ├── LoginResponseDto.java
│   │   │   └── TokenRefreshDto.java
│   │   └── entity/
│   │       └── RefreshToken.java
│   │
│   ├── users/                            # User CRUD, role management
│   │   ├── UserController.java
│   │   ├── UserService.java
│   │   ├── UserRepository.java
│   │   ├── dto/
│   │   └── entity/
│   │       └── User.java
│   │
│   ├── employees/                        # Employee profiles & onboarding
│   │   ├── EmployeeController.java
│   │   ├── EmployeeService.java
│   │   ├── EmployeeRepository.java
│   │   ├── dto/
│   │   └── entity/
│   │       ├── Employee.java
│   │       └── EmployeeDocument.java
│   │
│   ├── sites/                            # Site CRUD & geofence management
│   │   ├── SiteController.java
│   │   ├── SiteService.java
│   │   ├── SiteRepository.java
│   │   ├── dto/
│   │   └── entity/
│   │       └── Site.java
│   │
│   ├── attendance/                       # Punch in/out & attendance reports
│   │   ├── AttendanceController.java
│   │   ├── AttendanceService.java
│   │   ├── AttendanceRepository.java
│   │   ├── dto/
│   │   └── entity/
│   │       └── AttendanceLog.java
│   │
│   ├── location/                         # Live location & heatmap
│   │   ├── LocationController.java
│   │   ├── LocationService.java
│   │   ├── LocationRepository.java
│   │   ├── dto/
│   │   └── entity/
│   │       └── LocationHistory.java
│   │
│   ├── shifts/                           # Shift scheduling & assignment
│   │   ├── ShiftController.java
│   │   ├── ShiftService.java
│   │   ├── ShiftRepository.java
│   │   ├── dto/
│   │   └── entity/
│   │       ├── Shift.java
│   │       └── ShiftAssignment.java
│   │
│   ├── siteupdates/                      # Field site status reports
│   │   ├── SiteUpdateController.java
│   │   ├── SiteUpdateService.java
│   │   ├── SiteUpdateRepository.java
│   │   ├── dto/
│   │   └── entity/
│   │       └── SiteUpdate.java
│   │
│   ├── payroll/                          # Payroll engine (PF, ESIC, TDS, PT)
│   │   ├── PayrollController.java
│   │   ├── PayrollService.java
│   │   ├── PayrollCalculationService.java  # PF/ESIC/TDS/PT computation
│   │   ├── PayrollRepository.java
│   │   ├── dto/
│   │   └── entity/
│   │       ├── SalaryStructure.java
│   │       ├── PayrollRun.java
│   │       └── Payslip.java
│   │
│   ├── notifications/                    # FCM push, SMS, email
│   │   ├── NotificationController.java
│   │   ├── NotificationService.java
│   │   ├── FcmService.java
│   │   ├── dto/
│   │   └── entity/
│   │       └── DeviceToken.java
│   │
│   ├── media/                            # Presigned URL generation & thumbnails
│   │   ├── MediaController.java
│   │   ├── MediaService.java
│   │   └── dto/
│   │
│   └── admin/                            # Super admin operations
│       ├── AdminController.java
│       ├── AdminService.java
│       ├── AuditLogRepository.java
│       ├── dto/
│       └── entity/
│           ├── AuditLog.java
│           └── Organization.java
│
├── scheduler/                            # Background scheduled jobs
│   ├── PayrollJobScheduler.java          # @Async payroll processing
│   ├── HeatmapAggregationJob.java        # Daily 3 AM IST
│   ├── RetentionCleanupJob.java          # Daily 2 AM IST (location + media)
│   ├── ThumbnailProcessorJob.java        # @Async on upload
│   ├── ShiftReminderJob.java             # 30 min before shift
│   └── GeofenceAlertJob.java             # Real-time geofence violation check
│
src/main/resources/
├── application.yml                       # Main config (shared)
├── application-dev.yml                   # Dev profile overrides
├── application-prod.yml                  # Production profile overrides
└── db/migration/                         # Flyway SQL migration files
    ├── V1__init_extensions.sql           # PostGIS, pgcrypto, uuid-ossp
    ├── V2__create_organizations.sql
    ├── V3__create_users.sql
    ├── V4__create_clients.sql
    ├── V5__create_sites.sql
    ├── V6__create_employees.sql
    ├── V7__create_attendance.sql
    ├── V8__create_location.sql
    ├── V9__create_shifts.sql
    ├── V10__create_site_updates.sql
    ├── V11__create_payroll.sql
    ├── V12__create_notifications.sql
    ├── V13__create_audit_logs.sql
    ├── V14__create_refresh_tokens.sql
    └── V15__create_indexes.sql

src/test/java/com/fm/be/
├── unit/                                 # Unit tests per module
│   ├── auth/
│   ├── users/
│   ├── employees/
│   ├── sites/
│   ├── attendance/
│   ├── location/
│   ├── shifts/
│   ├── siteupdates/
│   ├── payroll/
│   ├── notifications/
│   ├── media/
│   └── admin/
├── integration/                          # MockMvc endpoint tests
│   ├── auth/
│   ├── attendance/
│   ├── payroll/
│   └── ...
└── e2e/                                  # Testcontainers full-flow tests
    ├── AuthFlowE2ETest.java
    ├── AttendancePunchE2ETest.java
    └── PayrollRunE2ETest.java
```

---

## 4. Coding Conventions

### 4.1 Naming
| Context | Convention | Example |
|---|---|---|
| Java packages | lowercase dot-separated | `com.fm.be.modules.attendance` |
| Java classes | PascalCase | `AttendanceService`, `PayrollController` |
| Java methods | camelCase | `validateGeofence()`, `processPayroll()` |
| Constants / Enums | SCREAMING_SNAKE | `GEOFENCE_VIOLATION`, `SUPER_ADMIN` |
| Database tables | snake_case, plural | `attendance_logs`, `payroll_runs` |
| Database columns | snake_case | `punch_time`, `is_active` |
| API endpoints | kebab-case, plural nouns | `/v1/site-updates`, `/v1/attendance/logs` |
| JSON response fields | camelCase | `punchInTime`, `isActive`, `faceMatchScore` |
| Env variables | SCREAMING_SNAKE | `DATABASE_URL`, `JWT_SECRET` |
| Git branches | `type/short-desc` | `feat/attendance-punch`, `fix/payroll-rounding` |

### 4.2 Code Style Rules
- **No file > 500 lines**. If approaching, extract into helper classes or sub-services.
- **Every public method** has a Javadoc comment explaining purpose and parameters.
- **Non-obvious logic** has inline comments explaining _why_, not just _what_.
- **DTOs** use Jakarta Validation annotations (`@NotNull`, `@Size`, `@Email`, etc.).
- **Entities** use Lombok `@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`.
- **Services** are `@Service` + `@RequiredArgsConstructor` (constructor injection via Lombok).
- **Controllers** are thin — delegate all logic to services.
- **Repositories** extend `JpaRepository<Entity, UUID>`.

### 4.3 API Design Patterns

#### Response Envelope (ALL endpoints)
```json
{
  "success": true,
  "data": { ... },
  "meta": { "page": 1, "limit": 20, "total": 150 },
  "error": null
}
```

#### Error Envelope
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

#### Error Codes (shared with all FE clients)
| Code | HTTP | When |
|---|---|---|
| `AUTH_TOKEN_EXPIRED` | 401 | JWT expired, trigger refresh |
| `AUTH_UNAUTHORIZED` | 403 | Insufficient role/permission |
| `GEOFENCE_VIOLATION` | 422 | Punch outside site boundary |
| `FACE_MISMATCH` | 422 | Facial recognition failed |
| `SHIFT_NOT_STARTED` | 422 | Too early/late for punch |
| `DUPLICATE_PUNCH` | 409 | Idempotent punch already exists |
| `PAYROLL_LOCKED` | 423 | Payroll period locked |
| `SITE_INACTIVE` | 410 | Site deactivated |
| `RATE_LIMITED` | 429 | Too many requests |
| `VALIDATION_ERROR` | 400 | DTO validation failed |
| `SERVER_ERROR` | 500 | Generic server error |

#### Pagination
- **Offset-based** for admin data tables (employees, sites, users, audit logs).
- **Cursor-based** for high-volume data (attendance logs, location history).

#### Timestamps
- Always **ISO 8601 with timezone**: `2026-03-30T10:30:00+05:30`
- Stored as `timestamptz` in PostgreSQL and `Instant` in Java.

#### IDs
- **UUID v4** for all entities. Never expose sequential IDs.

---

## 5. Database Design

### 5.1 Key Entities (Logical Schema)

```
organizations     → id (UUID PK), name, createdAt, updatedAt
users             → id (UUID PK), phone?, email?, passwordHash?, role, isActive, preferredLang, organizationId (FK)
employees         → id (UUID PK), userId (FK unique), fullName, dob?, aadhaarEncrypted?, panEncrypted?, faceEmbedding (float[]), siteId? (FK), organizationId (FK)
sites             → id (UUID PK), name, address?, geofenceRadius?, latitude?, longitude?, geofence (geography Polygon 4326)?, isActive, clientId? (FK), organizationId (FK)
clients           → id (UUID PK), name, contactEmail?, contactPhone?, organizationId (FK)
attendance_logs   → id (UUID PK), employeeId (FK), punchType (IN/OUT), punchTime (timestamptz), lat, lng, accuracy?, faceMatchScore?, isInsideGeofence, deviceInfo (JSONB), siteId (FK)
location_history  → id (UUID PK), employeeId (FK), lat, lng, accuracy?, battery?, signal?, airplaneMode, recordedAt (timestamptz)
site_updates      → id (UUID PK), employeeId (FK), siteId (FK), text?, mediaUrls (text[]), recordedAt (timestamptz)
shifts            → id (UUID PK), siteId (FK), name, startTime (time), endTime (time), isActive
shift_assignments → id (UUID PK), employeeId (FK), shiftId (FK), date (date), UNIQUE(employeeId, date)
employee_documents → id (UUID PK), employeeId (FK), type (DocumentType), fileUrl, isVerified, verifiedBy?, verifiedAt?
salary_structures → id (UUID PK), employeeId (FK unique), basic, hra, conveyance?, special?, grossSalary, pfEmployee, pfEmployer, esicEmployee?, esicEmployer?, ptAmount?
payroll_runs      → id (UUID PK), month, year, status (PayrollStatus), processedBy?, approvedBy?, bankFileUrl?, totalGross?, totalNet?, totalPf?, totalEsic?, totalTds?, organizationId (FK), UNIQUE(month, year, organizationId)
payslips          → id (UUID PK), payrollRunId (FK), employeeId (FK), daysWorked, daysAbsent, grossEarnings, pfDeduction, esicDeduction?, tdsDeduction?, ptDeduction?, otherDeductions?, netPayable
audit_logs        → id (UUID PK), userId (FK), action, targetEntity, targetId, metadata (JSONB), ipAddress?, createdAt
refresh_tokens    → id (UUID PK), userId (FK), token (unique), expiresAt, deviceInfo?
```

### 5.2 Multi-Tenancy
- **Strategy**: Single database, `organizationId` column on all tenant-scoped tables.
- **Enforcement**: `TenantInterceptor` injects `organizationId` from JWT into all queries.
- **Safety net**: PostgreSQL Row-Level Security (RLS) policies as an additional guard.

### 5.3 Migrations
- **Tool**: Flyway (SQL-based migrations)
- Naming: `V{n}__{description}.sql` (double underscore)
- Hibernate `ddl-auto` set to `validate` in production (never `update` or `create`).

### 5.4 Critical Indexes
```sql
-- Spatial index for geofence containment checks
CREATE INDEX idx_sites_geofence ON sites USING GIST (geofence);

-- Location history spatial + temporal
CREATE INDEX idx_location_history_coords ON location_history 
  USING GIST (ST_SetSRID(ST_MakePoint(longitude, latitude), 4326)::geography);
CREATE INDEX idx_location_history_recorded ON location_history (recorded_at DESC);

-- Attendance fast lookups
CREATE INDEX idx_attendance_employee_time ON attendance_logs (employee_id, punch_time DESC);

-- Partial index for active sites only
CREATE INDEX idx_active_sites ON sites (id) WHERE is_active = true;
```

---

## 6. Cross-Cutting Concerns

### 6.1 Authentication & Authorization
- **Mobile**: OTP login → JWT (access 15min + refresh 30d)
- **Web Portal**: Email/password → JWT (access 15min + refresh 7d via httpOnly cookie)
- **Client Portal**: Email/password → JWT (access 15min only)
- **JWT payload**: `{ sub: userId, role: Role, siteIds: [], iat, exp }`
- **Guards**: `@PreAuthorize` / `@Secured` on controller methods, matched to `Role` enum.
- **Filter**: `JwtAuthenticationFilter` (extends `OncePerRequestFilter`) validates token on every request.

### 6.2 Caching (Redis)
| Key Pattern | Data | TTL | Invalidation |
|---|---|---|---|
| `loc:{employeeId}` | Live location + device info | 120s | Overwritten every 30s by mobile |
| `profile:{employeeId}` | Employee profile snapshot | 10 min | Invalidate on profile update |
| `shift:{employeeId}:{date}` | Today's shift assignment | 1 hour | Invalidate on reassignment |
| `site:{siteId}:geofence` | Geofence polygon/radius | 30 min | Invalidate on geofence edit |
| `report:attendance:{siteId}:{date}` | Daily attendance summary | 5 min | Short TTL, auto-expires |

### 6.3 Rate Limiting (Redis-backed)
| Endpoint | Limit |
|---|---|
| `POST /v1/auth/otp/request` | 5 req/min per phone |
| `POST /v1/location/update` | 1 req/10s per user |
| `POST /v1/attendance/punch` | 1 req/min per user (business logic + dedup) |
| All other endpoints | 100 req/min per user |

### 6.4 Logging
- **Format**: Structured JSON via Logback + SLF4J
- **Correlation ID**: UUID v4 generated per request, stored in MDC, included in every log line.
- **Fields**: `timestamp`, `level`, `correlationId`, `module`, `action`, `userId`, `duration_ms`, `metadata`
- **Access logging on PII queries** (Aadhaar, PAN) via `LoggingInterceptor`.

### 6.5 Auditing
- All admin write operations logged in `audit_logs` table.
- Fields: `userId`, `action`, `targetEntity`, `targetId`, `metadata` (JSONB), `ipAddress`, `createdAt`.

### 6.6 Data Retention
- **Location history**: 30-day rolling retention (daily 2 AM IST cleanup job).
- **Site update media**: 30 days, then purged from R2/S3.
- **Payroll data**: Retained indefinitely (legal requirement).
- **Aggregated heatmap data**: Retained 1 year.

---

## 7. Background Jobs

| Job | Trigger | Schedule | Description |
|---|---|---|---|
| `processPayroll` | @Async (on-demand) | Via API call | Full payroll computation for a month |
| `generateBankFile` | @Async (on-demand) | After payroll approval | CSV/Excel for bank disbursement |
| `cleanupLocationData` | @Scheduled | Daily 2:00 AM IST | Delete `location_history` > 30 days |
| `cleanupMedia` | @Scheduled | Daily 2:30 AM IST | Delete expired media from R2/S3 |
| `aggregateHeatmap` | @Scheduled | Daily 3:00 AM IST | Aggregate location data into heatmap view |
| `generateThumbnail` | @Async (on upload) | On file upload confirm | Resize images, extract video frames |
| `sendShiftReminder` | @Scheduled | Every minute (check 30min window) | FCM notification for upcoming shifts |
| `checkGeofence` | @Async (triggered) | On location update | Alert when employee exits geofence >5 min |

---

## 8. Indian Statutory Payroll Compliance

### 8.1 Provident Fund (PF)
- Employee: 12% of Basic + DA (capped at ₹15,000 base)
- Employer: 12% of Basic + DA (3.67% EPF + 8.33% EPS)
- Generate PF ECR file monthly for EPFO portal

### 8.2 ESIC (Employee State Insurance)
- Applicable if Gross ≤ ₹21,000/month
- Employee: 0.75% of Gross
- Employer: 3.25% of Gross
- Re-evaluate eligibility every April and October

### 8.3 TDS (Tax Deducted at Source)
- **Old regime slabs** (default, configurable):
  - ₹0–₹2,50,000: Nil
  - ₹2,50,001–₹5,00,000: 5%
  - ₹5,00,001–₹10,00,000: 20%
  - Above ₹10,00,000: 30%
- Monthly TDS = Estimated annual tax / 12
- **Store TDS slabs as configuration, NOT hardcoded** (change annually).

### 8.4 Professional Tax (PT) — Maharashtra V1
- Gross ≤ ₹7,500: Nil
- ₹7,501–₹10,000: ₹175/month
- Above ₹10,000: ₹200/month (₹300 in February)
- **Store PT rules as configurable per-state data**.

> **Critical**: All tax rates, thresholds, and slabs MUST be stored in database configuration tables, never hardcoded. They change annually/biannually.

---

## 9. Performance Targets

| Endpoint | Target (p95) | Strategy |
|---|---|---|
| `POST /v1/attendance/punch` | < 500ms | GiST spatial index on geofence |
| `POST /v1/location/update` | < 100ms | Redis write only; DB async batch |
| `GET /v1/location/live` | < 200ms | Pure Redis read |
| `GET /v1/attendance/report` | < 1s | Indexed queries, materialized views |
| `POST /v1/payroll/run` | < 500ms | Returns immediately, @Async processing |
| `GET /v1/payroll/run/:id/status` | < 50ms | Simple DB read |

### Location Ingestion Strategy
1. **Every request**: Write to Redis only (fast path, <100ms)
2. **Every 5 minutes**: `@Scheduled` job flushes Redis → PostgreSQL batch insert (~3,000 rows)
3. **Geofence check**: Runs on every update using PostGIS `ST_DWithin()` query

---

## 10. Testing Strategy

### Coverage Target: **80%+ on services and modules**

| Layer | Tool | Coverage Target |
|---|---|---|
| **Unit Tests** | JUnit 5 + Mockito | 80%+ on `Service` classes |
| **Integration Tests** | Spring Boot Test + MockMvc | All endpoints tested |
| **E2E Tests** | Testcontainers (PostgreSQL + Redis) | Critical flows: auth, punch, payroll |
| **Coverage Reporting** | JaCoCo | Enforced via Maven plugin, fail build if < 80% |

### Testing Conventions
- Test directory mirrors main source: `src/test/java/com/fm/be/unit/{module}/`
- Each service class has a corresponding `{Service}Test.java`.
- Each controller has a corresponding `{Controller}IntegrationTest.java`.
- Tests cover: happy path, edge cases, validation errors, authorization failures, concurrency scenarios.
- Payroll tests include explicit assertions against Indian statutory compliance rules.

---

## 11. Security Checklist

- [ ] All API calls over HTTPS (enforced by NGINX)
- [ ] JWT validated on every request via `JwtAuthenticationFilter`
- [ ] Role-based `@PreAuthorize` on every endpoint
- [ ] Jakarta Bean Validation on every DTO
- [ ] JPA parameterized queries (no SQL injection)
- [ ] Rate limiting on auth + location endpoints
- [ ] PII (Aadhaar, PAN) encrypted at rest via pgcrypto
- [ ] Audit log for all admin write operations
- [ ] Presigned URLs expire in 15 minutes
- [ ] No secrets in code — all via environment variables
- [ ] Correlation IDs in all logs for traceability
- [ ] CORS restricted to known origins

---

## 12. Development Phases

### Phase 1: Foundation (Week 1–2)
> Setup, backend foundation, DB schema, auth, common infrastructure
- Project bootstrap, dependency setup, Flyway migrations
- Common: ApiResponse, GlobalExceptionHandler, logging, CORS, security config
- Auth module (OTP + email/password login, JWT, refresh tokens)
- Health endpoint enhancement
- Users module (CRUD, role management)

### Phase 2: Core Modules (Week 3–5)
> Android-critical endpoints first — attendance, location, sites, shifts
- Employees module (profiles, onboarding, documents, face registration)
- Sites module (CRUD, geofence polygon management with PostGIS)
- Attendance module (punch in/out with geofence + face verification, reports)
- Location module (live location ingestion, Redis strategy, heatmap queries)
- Shifts module (scheduling, assignment, mobile task view data)
- Site Updates module (text + media reports)

### Phase 3: Advanced Modules (Week 5–7)
> Payroll engine, notifications, media
- Payroll module (salary structures, payroll run with @Async, PF/ESIC/TDS/PT)
- Bank file generation (CSV/Excel)
- Notifications module (FCM push, device token management)
- Media module (presigned URL flow, thumbnail generation)

### Phase 4: Ops & Admin (Week 7–8)
> Dashboard data, admin panel, background jobs
- Admin module (user management, audit logs, system config, data export)
- Ops dashboard endpoints (live tracking, alerts, device audit)
- All scheduled jobs (cleanup, heatmap aggregation, shift reminders)
- Rate limiting interceptor

### Phase 5: Polish & Hardening (Week 9–10)
> Testing, performance, security
- 85%+ test coverage across all modules
- Performance testing and optimization
- Security audit and hardening
- API documentation (Springdoc OpenAPI)
- Production configuration and deployment preparation

---

## 13. V2 Extensibility

Design all schemas and APIs to accommodate future requirements:

| Future Feature | Design Decision Now |
|---|---|
| iOS App | API is platform-agnostic; no Android-specific assumptions |
| Visitor Management | `sites` supports `siteType` enum extensibility |
| Complaint/Ticket System | Employee + Site models are referenceable by future tables |
| WebSocket live tracking | Redis pub/sub infrastructure in place |
| Multi-tenant | `organizationId` on all tables from V1 |
| Separate biometrics | Face embedding storage designed for future extraction |

---

## 14. References

- [Master Planning Document](context/PLANNING.md)
- [Backend Context](context/BACKEND_CONTEXT.md)
- [Mobile Context](context/MOBILE_CONTEXT.md) — understand FE contract expectations
- [Web Portal Context](context/WEB_PORTAL_CONTEXT.md) — understand dashboard/payroll FE needs
- [Client Portal Context](context/CLIENT_PORTAL_CONTEXT.md) — read-only reporting needs
- [Infrastructure Context](context/INFRASTRUCTURE_CONTEXT.md) — deployment architecture
- [FM Overview](context/FM-OVERVIEW.md) — V1 scope table
- [FM Detailed](context/FM-DETAILED.md) — detailed scope of work
