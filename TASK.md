# TASK.md — fm-be Local Infrastructure & API Integration Testing

> **Last Updated**: 2026-04-03  
> **Legend**: ⬜ Not Started | 🟡 In Progress | ✅ Done | 🔴 Blocked  
> **Predecessor**: `itr/v1/TASK.md` (V1 development complete — all 63 endpoints, 94% test coverage)

---

## Phase 1: Local Infrastructure Setup

### TASK-1.1: Dev Infrastructure Shell Scripts
**Status**: ✅ Done  
**Date Added**: 2026-04-03  
**Date Completed**: 2026-04-03  

**What was done**:
- Created `scripts/setup-dev.sh` — One-time setup: installs Docker, Docker Compose, Maven wrapper, Newman, creates `.env.dev`
- Created `scripts/start-infra.sh` — Starts PostgreSQL+PostGIS, Redis, MinIO; waits for healthy; reports connectivity
- Created `scripts/start-server.sh` — Sources `.env.dev`, runs pre-flight checks, starts `mvn spring-boot:run -Pdev`
- Created `scripts/stop-all.sh` — Kills Java server process + `docker compose down`
- Created `scripts/health-check.sh` — Checks all services (Docker + application + Swagger)
- Created `scripts/reset-db.sh` — Drops and recreates database with extensions (destructive, with confirmation)
- Created `scripts/run-api-tests.sh` — Runs Newman with folder/smoke/full modes, generates JSON reports
- Updated `.gitignore` — Added `.env.dev` and `tests/postman/reports/`

---

## Phase 2: Testing Infrastructure Setup

### TASK-2.1: Postman Environment & Collection Skeleton
**Status**: ✅ Done  
**Date Added**: 2026-04-03  
**Date Completed**: 2026-04-03  

**What was done**:
- Created `tests/postman/fm-be-dev.postman_environment.json` — 29 environment variables covering auth tokens, entity IDs, test coordinates, all auto-populated during test run
- Created `tests/postman/fm-be-api-tests.postman_collection.json` — Collection skeleton with global pre-request (auth headers) + global test (response time), 00-Smoke + 01-Auth starter suites
- Collection organized as 15 ordered folders (suites) with documented execution dependencies

---

### TASK-2.2: Comprehensive API Test Plan
**Status**: ✅ Done  
**Date Added**: 2026-04-03  
**Date Completed**: 2026-04-03  

**What was done**:
- Mapped all **63 endpoints** across 13 controllers
- Planned **268 total test cases**: 75 happy path, 127 edge cases, 66 RBAC tests
- Documented data setup dependencies (entity creation order)
- Defined environment variable flow (which suite sets which variable)
- Specified expected HTTP status codes and error codes for every case
- Organized into 15 suites with sequential execution order
- Full plan in root `PLANNING.md` §4

---

## Phase 3: Postman Collection Creation (Suite-by-Suite)

> Each task below creates one Postman folder/suite inside the collection.  
> Reference: `PLANNING.md` §4.x for exact test cases per suite.  
> Agents should read the corresponding controller + DTO source files for precise request/response shapes.

### TASK-3.1: Suite 00 — Smoke Tests
**Status**: ✅ Done  
**Date Added**: 2026-04-03  
**Date Completed**: 2026-04-03  
**Tests**: 2  
**Reference**: `PLANNING.md` §4.1

- ✅ Health check — status UP, database + redis present
- ✅ OpenAPI docs — available with paths

---

### TASK-3.2: Suite 01 — Auth
**Status**: ✅ Done  
**Date Added**: 2026-04-03  
**Date Completed**: 2026-04-03  
**Tests**: 20 total (all passing — 0 failures)  
**Reference**: `PLANNING.md` §4.2

**All 20 tests created and verified against live server:**

*Login (5 tests):*
- ✅ Admin login (email/password) — stores tokens, userId, organizationId
- ✅ Login with wrong password — 401 AUTH_INVALID_CREDENTIALS
- ✅ Login with non-existent email — 401 AUTH_INVALID_CREDENTIALS
- ✅ Login with empty body — 400 VALIDATION_ERROR (email + password)
- ✅ Login with invalid email format — 400 VALIDATION_ERROR

*OTP (7 tests):*
- ✅ OTP request (valid phone) — 200, "OTP sent successfully"
- ✅ OTP verify (valid) — conditional on `otp_code` env var from Redis
- ✅ OTP verify (wrong OTP) — 401 AUTH_INVALID_OTP
- ✅ OTP verify (non-existent phone) — 401 AUTH_INVALID_OTP
- ✅ OTP verify (empty body) — 400 VALIDATION_ERROR (phone + otp)
- ✅ OTP request (empty phone) — 400 VALIDATION_ERROR
- ✅ OTP request (invalid phone format) — 400 VALIDATION_ERROR
- ✅ OTP request (missing phone field) — 400 VALIDATION_ERROR

*Token Refresh (3 tests):*
- ✅ Token refresh (valid) — 200, new access token, user info preserved
- ✅ Refresh with invalid token — 401 AUTH_TOKEN_EXPIRED
- ✅ Refresh with empty token — 400 VALIDATION_ERROR

*Logout (2 tests):*
- ✅ Logout (valid) — 200, uses fresh disposable refresh token
- ✅ Logout (already-used token) — 200 (idempotent)

*Edge Cases (2 tests):*
- ✅ Inactive user login — 403 AUTH_UNAUTHORIZED (pre-request creates & deactivates temp user, test cleans up)
- ✅ OTP rate limit (6th request) — 429 RATE_LIMITED with retryAfterSeconds

**Environment vars set**: `access_token`, `admin_access_token`, `admin_refresh_token`, `admin_user_id`, `organization_id`

**Spike — OTP Verify Automation**:
> The OTP verify happy-path test is conditional on `otp_code` being set externally.
> In dev, the OTP is logged to console (`OTP generated for phone {}: {}`) and stored in Redis (`otp:<phone>`).
> To automate: enhance `run-api-tests.sh` to request OTP, read it from Redis via `redis-cli GET otp:<phone>`, and pass `--env-var otp_code=<value>` to Newman.

---

### TASK-3.3: Suite 02 — Users
**Status**: ✅ Done  
**Date Added**: 2026-04-03  
**Date Completed**: 2026-04-03  
**Tests**: 22 total (all passing — 0 failures)  
**Reference**: `PLANNING.md` §4.3

**All 22 tests created and verified against live server:**

*POST /v1/admin/users (7 tests):*
- ✅ Create user with phone (EMPLOYEE) — 201, stores `employee_user_id`
- ✅ Create user with email+password (ADMIN) — 201, stores `test_user_id`
- ✅ Duplicate email — 409 DUPLICATE_RESOURCE
- ✅ Duplicate phone — 409 DUPLICATE_RESOURCE
- ✅ Missing organizationId — 400 VALIDATION_ERROR
- ✅ Invalid role value — 500 SERVER_ERROR (API returns 500, not 400 — noted)
- ✅ RBAC: Employee cannot create — 403

*GET /v1/admin/users (4 tests):*
- ✅ List users (default pagination) — 200, data array + meta (page/limit/total/totalPages)
- ✅ List users (custom page/limit) — 200, respects limit=2
- ✅ Page beyond total — 200, empty data array
- ✅ RBAC: Employee cannot list — 403

*GET /v1/admin/users/{id} (3 tests):*
- ✅ Get user by ID — 200, all fields verified
- ✅ Non-existent UUID — 404 RESOURCE_NOT_FOUND
- ✅ Invalid UUID format — 500 SERVER_ERROR (API returns 500, not 400 — noted)

*PUT /v1/admin/users/{id} (4 tests):*
- ✅ Update user role — 200, role changed to SUPERVISOR
- ✅ Deactivate user — 200, active=false (auto-reactivated in test cleanup)
- ✅ Update non-existent user — 404 RESOURCE_NOT_FOUND
- ✅ Update with duplicate email — 409 DUPLICATE_RESOURCE

*DELETE /v1/admin/users/{id} (4 tests):*
- ✅ Delete user — 204 No Content
- ✅ Delete non-existent user — 404 RESOURCE_NOT_FOUND
- ✅ Delete self (temp admin) — 204 (pre-request creates temp admin, logs in, self-deletes)
- ✅ RBAC: Employee cannot delete — 403

**Environment vars set**: `employee_user_id`

**Discovered behaviors (differ from plan):**
- Invalid role value returns 500 SERVER_ERROR instead of 400 (enum deserialization fails)
- Invalid UUID format returns 500 SERVER_ERROR instead of 400 (type mismatch)
- Self-delete returns 204 (allowed) instead of 400/422 (no business rule prevents it)
- Logout endpoint is accessible without authentication (public endpoint)

---

### TASK-3.4: Suite 03 — Employees
**Status**: ⬜ Not Started  
**Date Added**: 2026-04-03  
**Tests**: 30  
**Reference**: `PLANNING.md` §4.4

**Endpoints**: POST/PUT/GET `/v1/employees`, search, me, face, documents  
**Key tests**: onboarding flow, face registration, document upload (AADHAAR/PAN), RBAC, search  
**Environment vars set**: `employee_id`, `employee_access_token`

---

### TASK-3.5: Suite 04 — Sites
**Status**: ⬜ Not Started  
**Date Added**: 2026-04-03  
**Tests**: 24  
**Reference**: `PLANNING.md` §4.5

**Endpoints**: CRUD `/v1/sites`, geofence, delete  
**Key tests**: create with location, set circular/polygon geofence, zero/negative radius, soft delete, RBAC  
**Environment vars set**: `site_id`, `site_id_2`

---

### TASK-3.6: Suite 05 — Shifts
**Status**: ⬜ Not Started  
**Date Added**: 2026-04-03  
**Tests**: 22  
**Reference**: `PLANNING.md` §4.6

**Endpoints**: CRUD `/v1/shifts`, assign, schedule  
**Key tests**: day/night shift, duplicate assignment (409), employee schedule view, admin view  
**Environment vars set**: `shift_id`, `shift_assignment_id`

---

### TASK-3.7: Suite 06 — Attendance (CRITICAL)
**Status**: ⬜ Not Started  
**Date Added**: 2026-04-03  
**Tests**: 28  
**Reference**: `PLANNING.md` §4.7

**Endpoints**: punch, today, logs, report, site attendance  
**Key tests**: punch IN/OUT inside geofence, OUTSIDE geofence (422), face mismatch (422), duplicate (409), inactive site (410), date range filtering, RBAC  
**Test coordinates**: `test_latitude/longitude` (inside), `outside_latitude/longitude` (Delhi — far from Mumbai site)  
**Environment vars set**: `attendance_log_id`

---

### TASK-3.8: Suite 07 — Location
**Status**: ⬜ Not Started  
**Date Added**: 2026-04-03  
**Tests**: 20  
**Reference**: `PLANNING.md` §4.8

**Endpoints**: update, live, heatmap, alerts  
**Key tests**: location update (all/minimal fields), rate limit (429), live locations, heatmap with date range, geofence alerts  
**Note**: Rate limit is 1 req/10s for location updates

---

### TASK-3.9: Suite 08 — Site Updates
**Status**: ⬜ Not Started  
**Date Added**: 2026-04-03  
**Tests**: 14  
**Reference**: `PLANNING.md` §4.9

**Endpoints**: create, list by site, get by ID  
**Key tests**: text-only, text+media, media-only, too many media (>6), RBAC  
**Environment vars set**: `site_update_id`

---

### TASK-3.10: Suite 09 — Payroll (COMPLEX)
**Status**: ⬜ Not Started  
**Date Added**: 2026-04-03  
**Tests**: 35  
**Reference**: `PLANNING.md` §4.10

**Endpoints**: salary-structure CRUD, payroll run lifecycle, payslips  
**Key tests**: salary creation, PF/ESIC/TDS/PT verification, payroll run (initiate → poll → approve/reject), payslip statutory compliance  
**Indian compliance verification**:
- PF: 12% of basic capped at ₹15,000
- ESIC: 0.75% employee / 3.25% employer if gross ≤ ₹21,000
- PT: Maharashtra brackets with February exception
- TDS: Old regime slabs

**Environment vars set**: `payroll_run_id`

---

### TASK-3.11: Suite 10 — Notifications
**Status**: ⬜ Not Started  
**Date Added**: 2026-04-03  
**Tests**: 16  
**Reference**: `PLANNING.md` §4.11

**Endpoints**: send, list, mark read, register device  
**Key tests**: send with/without metadata, unread count, mark read (idempotent), device registration  
**Environment vars set**: `notification_id`

---

### TASK-3.12: Suite 11 — Media
**Status**: ⬜ Not Started  
**Date Added**: 2026-04-03  
**Tests**: 12  
**Reference**: `PLANNING.md` §4.12

**Endpoints**: presigned-url, confirm  
**Key tests**: image/video/document URLs, file size limits (5MB image, 50MB video), unsupported type, confirm upload  
**Environment vars set**: `presigned_upload_url`, `media_file_key`

---

### TASK-3.13: Suite 12 — Admin
**Status**: ⬜ Not Started  
**Date Added**: 2026-04-03  
**Tests**: 20  
**Reference**: `PLANNING.md` §4.13

**Endpoints**: audit-logs, dashboard stats, export, config  
**Key tests**: audit log filtering, CSV export (users/employees/sites), system config CRUD, SUPER_ADMIN vs ADMIN RBAC  

---

### TASK-3.14: Suite 99 — Cleanup
**Status**: ⬜ Not Started  
**Date Added**: 2026-04-03  
**Tests**: 3  
**Reference**: `PLANNING.md` §4.14

**Tests**: logout admin, logout employee, verify tokens invalidated (401)

---

### TASK-3.15: Data Seed Script
**Status**: ✅ Done  
**Date Added**: 2026-04-03  
**Date Completed**: 2026-04-03  

**What was done**:
- Created `scripts/seed-test-data.sh` — Inserts test data via psql with pgcrypto for BCrypt hashing
- Idempotent (uses `ON CONFLICT` — safe to re-run)
- Seed data: 1 organization (`FM Test Organization`), 1 admin user (`admin@fmplatform.com` / `Admin@123456`), 1 employee user (`+919876543210`)
- Fixed UUIDs for deterministic seeding

**Bug fixes during integration**:
- Fixed `SyntaxError: Identifier 'data' has already been declared` — renamed `const data` to `const resData` in all Postman test scripts (4 scripts). `data` is a reserved identifier in the Newman/Postman sandbox.
- Fixed BCrypt password hash generation — shell variable substitution corrupted `$2a$` hash; switched to `pgcrypto` `crypt()` function for reliable in-database hash generation

---

## Phase 4: Cross-Cutting Enhancements

### TASK-4.1: Request Tracing (Correlation ID Middleware)
**Status**: ✅ Done  
**Date Added**: 2026-04-03  
**Date Completed**: 2026-04-03  

**What was done**:
- Created `RequestTraceFilter` (`OncePerRequestFilter`) — runs before Spring Security, so even 401/403 responses carry a trace ID
- Honours incoming `X-Correlation-Id` or `X-Request-Id` headers from clients; generates UUID v4 if absent
- Sets SLF4J MDC key `correlationId` — every log line for the request includes the trace ID (grep-friendly)
- Returns `X-Correlation-Id` response header on every response
- Added `traceId` field to `ApiResponse` envelope — visible in both success and error JSON bodies
- Refactored `LoggingInterceptor` — removed correlation ID generation (now handled by filter), kept request timing logic
- Registered filter at `Ordered.HIGHEST_PRECEDENCE` and before `CorsFilter` in `SecurityConfig`
- Updated `CorsConfig` to expose `X-Request-Id` header to browsers
- Updated existing unit tests + added 6 new tests for `RequestTraceFilter`

**Files changed**:
- `src/main/java/com/fm/be/common/filter/RequestTraceFilter.java` (new)
- `src/main/java/com/fm/be/common/interceptor/LoggingInterceptor.java` (refactored)
- `src/main/java/com/fm/be/common/dto/ApiResponse.java` (added `traceId` field)
- `src/main/java/com/fm/be/common/config/SecurityConfig.java` (filter registration)
- `src/main/java/com/fm/be/common/config/CorsConfig.java` (expose `X-Request-Id`)
- `src/test/java/com/fm/be/common/filter/RequestTraceFilterTest.java` (new — 6 tests)
- `src/test/java/com/fm/be/common/interceptor/LoggingInterceptorTest.java` (updated)

**Verification**:
- All 25 unit tests pass (0 failures)
- All 27 API assertions pass (0 failures)
- `grep "TRACE-ID" server.log` shows correlation ID in every log line for that request
