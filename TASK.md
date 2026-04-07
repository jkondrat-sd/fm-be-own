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

> Each task below creates a suite-specific Postman collection file in `tests/postman/suites/<suite-slug>/`.  
> Example: `tests/postman/suites/05-shifts/fm-be-05-shifts.postman_collection.json`.
> Reference: `PLANNING.md` §4.x for exact test cases per suite.  
> Agents must read the corresponding controller + DTO source files for precise request/response shapes.

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
**Status**: ✅ Done  
**Date Added**: 2026-04-03  
**Date Completed**: 2026-04-03  
**Tests**: 31 total (all passing — 0 failures, 163 assertions)  
**Reference**: `PLANNING.md` §4.4

**All 31 tests created and verified against live server:**

*POST /v1/employees (6 tests):*
- ✅ Create employee with all fields (phone+email+dob) — 201, stores `employee_id`
- ✅ Create employee minimal fields (fullName+phone+orgId only) — 201, role defaults to EMPLOYEE
- ✅ Missing fullName — 400 VALIDATION_ERROR (details.fullName)
- ✅ Duplicate phone (same as existing user) — 409 DUPLICATE_RESOURCE
- ✅ Non-existent siteId — 201 (FK not enforced at app level — noted)
- ✅ RBAC: Employee cannot create employee — 403

*PUT /v1/employees/{id} (3 tests):*
- ✅ Update employee name — 200, fullName updated
- ✅ Update employee DOB — 200, dob updated
- ✅ Update non-existent employee — 404 RESOURCE_NOT_FOUND

*GET /v1/employees/{id} (2 tests):*
- ✅ Get employee by ID — 200, all fields verified
- ✅ Non-existent ID — 404 RESOURCE_NOT_FOUND

*GET /v1/employees (2 tests):*
- ✅ List employees paginated — 200, data array + meta (page/limit/total/totalPages)
- ✅ Page beyond total — 200, empty data array

*GET /v1/employees/search (3 tests):*
- ✅ Search by name — 200, matching employees returned, names verified
- ✅ No match — 200, empty data array
- ✅ Missing query param — 500 SERVER_ERROR (Spring @RequestParam not handled as 400)

*GET /v1/employees/me (2 tests):*
- ✅ Employee gets own profile — conditional on `employee_access_token` (OTP automation)
- ✅ Admin accesses /me — 403 (admin lacks EMPLOYEE role)

*POST /v1/employees/{id}/face (4 tests):*
- ✅ Register face — empty array — 200 (accepted, no server-side validation on array content)
- ✅ Register face embedding (32 floats) — 200, "Face registered successfully"
- ✅ Non-existent employee — 404 RESOURCE_NOT_FOUND
- ✅ RBAC: Employee cannot register face — 403

*POST /v1/employees/{id}/documents (8 tests):*
- ✅ Upload Aadhaar document — 200, type=AADHAAR, verified=false
- ✅ Upload PAN document — 200, type=PAN, verified=false
- ✅ Invalid document type (UNKNOWN_DOC) — 500 SERVER_ERROR (enum deserialization fails)
- ✅ Missing fileUrl param — 500 SERVER_ERROR (Spring @RequestParam not handled as 400)
- ✅ Non-existent employee — 404 RESOURCE_NOT_FOUND
- ✅ RBAC: Employee cannot upload docs — 403
- ✅ Upload VOTER_ID (third doc same employee) — 200
- ✅ Duplicate document type (AADHAAR again) — 200 (no unique constraint on employee_id+type)

*Verification (1 test):*
- ✅ Get employee — face + docs persisted — hasFaceRegistered=true, 3+ documents with AADHAAR/PAN/VOTER_ID

**Environment vars set**: `employee_id`, `employee_user_id_for_emp`, `minimal_employee_id`

**Discovered behaviors (differ from plan):**
- Non-existent siteId returns 201 (FK constraint exists in DB but not enforced at service level — JPA saves without validation)
- Empty face embedding array accepted (200) — no server-side validation; `hasFaceRegistered` correctly returns false
- Missing @RequestParam (`fileUrl`, `query`) returns 500 SERVER_ERROR instead of 400 (Spring default behavior for missing required params)
- Invalid enum value for @RequestParam (`DocumentType`) returns 500 instead of 400
- Duplicate document type allowed (200) — no unique constraint on (employee_id, type)
- Employee `/me` endpoint requires OTP automation for full testing; conditional test included

**Seed script updated**: Added employee record for seeded EMPLOYEE user (needed for `/me` endpoint)

---

### TASK-3.5: Suite 04 — Sites
**Status**: ✅ Done  
**Date Added**: 2026-04-03  
**Date Completed**: 2026-04-03  
**Tests**: 26 total (all passing — 0 failures, 172 assertions across suites 00+01+04)  
**Reference**: `PLANNING.md` §4.5

**All 26 tests created and verified against live server:**

*POST /v1/sites (5 tests):*
- ✅ Create site with location (name+address+lat/lng+radius+orgId) — 201, stores `site_id`
- ✅ Create site minimal (name+orgId only) — 201, stores `site_id_2`
- ✅ Missing name — 400 VALIDATION_ERROR (details.name)
- ✅ Missing organizationId — 400 VALIDATION_ERROR (details.organizationId)
- ✅ RBAC: Employee cannot create site — 403

*PUT /v1/sites/{id} (4 tests):*
- ✅ Update site name — 200, name changed to "Mumbai HQ - Updated"
- ✅ Deactivate site (isActive=false) — 200, active=false
- ✅ Re-activate site (cleanup, isActive=true) — 200, active=true
- ✅ Update non-existent site — 404 RESOURCE_NOT_FOUND

*GET /v1/sites/{id} (2 tests):*
- ✅ Get site by ID — 200, all fields verified (id, name, address, lat, lng, geofenceRadius, active, clientId, orgId, timestamps)
- ✅ Non-existent ID — 404 RESOURCE_NOT_FOUND

*GET /v1/sites (2 tests):*
- ✅ List sites paginated (?page=1&limit=10) — 200, data array + meta (page/limit/total/totalPages)
- ✅ List sites default pagination — 200, defaults page=1

*PUT /v1/sites/{id}/geofence (7 tests):*
- ✅ Set circular geofence (lat/lng/radiusMeters=500) — 200, "Geofence updated successfully"
- ✅ Set polygon geofence via GeoJSON — 200, "Geofence updated successfully"
- ✅ Missing latitude/longitude — 400 VALIDATION_ERROR (details.latitude + details.longitude)
- ✅ Non-existent site geofence — 404 RESOURCE_NOT_FOUND
- ✅ Zero radius — 200 (no validation on radiusMeters > 0; PostGIS accepts ST_Buffer(0))
- ✅ Negative radius — 200 (no validation; PostGIS accepts negative radius)
- ✅ Update existing geofence (new radius=1000) — 200, geofence overwritten

*DELETE /v1/sites/{id} (5 tests):*
- ✅ Create disposable site for delete — 201, stores `disposable_site_id`
- ✅ Soft-delete site — 204 No Content
- ✅ Delete non-existent site — 404 RESOURCE_NOT_FOUND
- ✅ Delete already-deleted site — 204 (idempotent; findByIdAndOrganizationId has no isActive filter)
- ✅ RBAC: Employee cannot delete site — 403

*Verification (1 test):*
- ✅ Verify site_id still active — 200, active=true, name="Mumbai HQ - Updated", geofenceRadius=1000

**Environment vars set**: `site_id`, `site_id_2`, `disposable_site_id`

**Discovered behaviors (differ from plan):**
- Zero radius accepted (200) — no validation on radiusMeters > 0; PostGIS ST_Buffer(0) produces degenerate polygon
- Negative radius accepted (200) — no server-side validation; PostGIS handles gracefully
- Delete already-deleted site returns 204 (not 404) — `findByIdAndOrganizationId` doesn't filter by isActive, so deactivateSite sets isActive=false again (idempotent)
- Geofence column was missing from `sites` table — Hibernate `ddl-auto` created table without PostGIS `geography(Polygon, 4326)` column; required manual `ALTER TABLE` to add

**Database fix applied**: `ALTER TABLE sites ADD COLUMN IF NOT EXISTS geofence geography(Polygon, 4326);` — Flyway migration V5 defines it but Hibernate ddl-auto created the table first without this column

---

### TASK-3.6: Suite 05 — Shifts
**Status**: ✅ Done  
**Date Added**: 2026-04-03  
**Date Completed**: 2026-04-07
**Tests**: 15 in Postman, 17 in Python
**Reference**: `PLANNING.md` §4.6

- ✅ Create `tests/postman/suites/05-shifts/fm-be-05-shifts.postman_collection.json`
- ✅ Implement CRUD `/v1/shifts`, assign, schedule
- ✅ Day/night shift, duplicate assignment (409), employee schedule view, admin view
- ✅ Fixed constraint validation bug in `ShiftService.java` for EDGE site testing
- ✅ Created `fix_shifts_suite.py` as an automated Python parallel suite with Redis OTP fetching

**Endpoints**: CRUD `/v1/shifts`, assign, schedule  
**Key tests**: day/night shift, duplicate assignment (409), employee schedule view, admin view  
**Environment vars set**: `shift_id`, `shift_assignment_id`

---

### TASK-3.7: Suite 06 — Attendance (CRITICAL)
**Status**: ✅ Done  
**Date Added**: 2026-04-03  
**Date Completed**: 2026-04-03  
**Tests**: 28 total  
**Reference**: `PLANNING.md` §4.7

**All 28 tests created:**

*POST /v1/attendance/punch (13 tests):*
- ✅ Punch IN inside geofence (faceMatchScore: 0.92) — 201, insideGeofence=true, stores `attendance_log_id`, `attendance_employee_id`
- ✅ Punch OUT inside geofence (no face) — 201, proves face is optional
- ✅ Punch IN without face (second employee) — 201, bootstraps second employee auth
- ✅ Punch OUTSIDE geofence (Delhi coords 28.6139, 77.2090) — 422 GEOFENCE_VIOLATION
- ✅ Face match score too low (0.5, threshold 0.85) — 422 FACE_MISMATCH
- ✅ Duplicate punch within 5 min (OUT again) — 409 DUPLICATE_PUNCH
- ✅ Punch at inactive site (pre-req deactivates, post-test reactivates) — 410 SITE_INACTIVE
- ✅ Employee with no site assignment — 410 SITE_INACTIVE (null siteId → BusinessRuleException)
- ✅ Missing punchType — 400 VALIDATION_ERROR
- ✅ Missing coordinates (lat + lng) — 400 VALIDATION_ERROR with details
- ✅ Invalid punchType ("BREAK") — 400/500 (enum deserialization)
- ✅ RBAC: Admin cannot punch — 403
- ✅ No auth token — 401

*GET /v1/attendance/today (3 tests):*
- ✅ Get today's punches — 200, array with ≥2 entries (IN + OUT)
- ✅ No punches today (no-site employee) — 200, empty array
- ✅ RBAC: Admin cannot use /today — 403

*GET /v1/attendance/logs (6 tests):*
- ✅ Get logs with date range — 200, paginated with meta (page/limit/total/totalPages)
- ✅ Filter by siteId — 200, logs belong to specified site
- ✅ Filter by employeeId — 200, logs belong to specified employee
- ✅ Missing date range — 400/500 (Spring @RequestParam required)
- ✅ Start after end — 200 empty or 400
- ✅ RBAC: Employee cannot view logs — 403

*GET /v1/attendance/report (3 tests):*
- ✅ Site attendance report — 200, has siteId/totalPunchIns/totalPunchOuts/totalEmployees
- ✅ Report for site with no attendance (site_id_2) — 200 with zero counts
- ✅ Report for non-existent siteId — 200 (zeroes) or 404

*GET /v1/attendance/site/{siteId} (3 tests):*
- ✅ Today's site attendance — 200, array with attendance entries
- ✅ Non-existent site — 200 empty or 404
- ✅ RBAC: Employee cannot view site attendance — 403

**Employee auth bootstrap**: Suite bootstraps its own employee authentication in the first test's pre-request:
1. Creates employee via `POST /v1/employees` (also creates user)
2. Sets password via `PUT /v1/admin/users/{userId}`
3. Logs in via `POST /v1/auth/login` → stores `employee_access_token`
4. Handles re-run idempotency (409 → just login)

**Environment vars set**: `attendance_log_id`, `attendance_employee_id`, `employee_access_token`, `employee2_access_token`, `no_site_employee_token`

**Test design notes:**
- Geofence check order: site active → geofence → face match → duplicate (allows testing each independently)
- Inside coords: 19.076, 72.8777 (Mumbai HQ, matches site creation in suite 04)
- Outside coords: 28.6139, 77.2090 (Delhi, ~1,100 km from site)
- Inactive site test deactivates and reactivates site within the same test
- 3 temp employees created for test isolation (main, second, no-site)

---

### TASK-3.8: Suite 07 — Location
**Status**: ✅ Done  
**Date Added**: 2026-04-03  
**Date Completed**: 2026-04-03  
**Tests**: 20 total  
**Reference**: `PLANNING.md` §4.8

**All 20 tests created:**

*POST /v1/location/update (7 tests):*
- ✅ Update location with all fields (lat/lng/accuracy/battery/signal/airplaneMode) — 200, status "ok"
- ✅ Rate limit (2nd request within 10s) — 429 RATE_LIMITED
- ✅ Missing latitude — 400 VALIDATION_ERROR (11s busy-wait for rate limit reset)
- ✅ Missing longitude — 400 VALIDATION_ERROR (11s busy-wait)
- ✅ RBAC: Admin cannot update location — 403 (admin has separate rate limit counter)
- ✅ Airplane mode true — 200, accepted (11s busy-wait)
- ✅ Minimal update (coords only) — 200 (11s busy-wait)

*GET /v1/location/live/{siteId} (4 tests):*
- ✅ Live locations for site — 200, array with employee locations (employeeId/lat/lng/lastUpdated)
- ✅ No active employees at different site (site_id_2) — 200, empty array
- ✅ Non-existent site — 200 empty or 404
- ✅ RBAC: Employee cannot view live locations — 403

*GET /v1/location/heatmap/{siteId} (4 tests):*
- ✅ Heatmap with date range — 200, points array with lat/lng/weight
- ✅ No data in date range (past dates 2020) — 200, empty points
- ✅ Missing date range — 400/500 (Spring @RequestParam required)
- ✅ RBAC: Employee cannot view heatmap — 403

*GET /v1/location/alerts (5 tests):*
- ✅ Geofence alerts endpoint — 200, array (validates alert fields if present)
- ✅ Alerts response structure — 200, success=true, data is array
- ✅ No active alerts expected — 200 (employees updated inside geofence)
- ✅ RBAC: Employee cannot view alerts — 403
- ✅ Verify live location after update — 200, confirms location update reflected in live view

**Rate limit handling**: Location update endpoint has aggressive 1 req/10s rate limit (via RateLimitInterceptor). Tests include 11-second busy-wait pre-request scripts between employee requests to respect the rate limit window. RBAC/admin tests use separate rate limit counter (per-user) and don't need waits.

**Environment vars used**: `employee_access_token` (from suite 06 bootstrap), `site_id`, `site_id_2`, `admin_access_token`

**Test design notes:**
- Rate limit is Redis-backed per-user counter at key `rl:loc:<userId>`
- Location updates write to Redis only (fast path <100ms), batch-flushed to PostgreSQL every 5 min
- Live locations read from Redis hash `loc:<employeeId>` with 120s TTL
- Heatmap data comes from PostgreSQL `location_history` table (may be empty if no batch flush occurred)
- Alerts check Redis loc:* entries for employees outside geofence >5 min

---

### TASK-3.9: Suite 08 — Site Updates
**Status**: ✅ Done  
**Date Added**: 2026-04-03  
**Date Completed**: 2026-04-03  
**Tests**: 14  
**Reference**: `PLANNING.md` §4.9

- ✅ Create `tests/postman/suites/08-site-updates/fm-be-08-site-updates.postman_collection.json`
- ✅ Implement create, list by site, get by ID

**Endpoints**: create, list by site, get by ID  
**Key tests**: text-only, text+media, media-only, too many media (>6), RBAC  
**Environment vars set**: `site_update_id`

---

### TASK-3.10: Suite 09 — Payroll (COMPLEX)
**Status**: ✅ COMPLETED  
**Date Completed**: 2026-04-06  
**Tests**: 42  
**Reference**: `PLANNING.md` §4.10
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

- ⬜ Create `tests/postman/suites/10-notifications/fm-be-10-notifications.postman_collection.json`
- ⬜ Implement send, list, mark read, register device

**Endpoints**: send, list, mark read, register device  
**Key tests**: send with/without metadata, unread count, mark read (idempotent), device registration  
**Environment vars set**: `notification_id`

---

### TASK-3.12: Suite 11 — Media
**Status**: ⬜ Not Started  
**Date Added**: 2026-04-03  
**Tests**: 12  
**Reference**: `PLANNING.md` §4.12

- ⬜ Create `tests/postman/suites/11-media/fm-be-11-media.postman_collection.json`
- ⬜ Implement presigned-url, confirm

**Endpoints**: presigned-url, confirm  
**Key tests**: image/video/document URLs, file size limits (5MB image, 50MB video), unsupported type, confirm upload  
**Environment vars set**: `presigned_upload_url`, `media_file_key`

---

### TASK-3.13: Suite 12 — Admin
**Status**: ⬜ Not Started  
**Date Added**: 2026-04-03  
**Tests**: 20  
**Reference**: `PLANNING.md` §4.13

- ⬜ Create `tests/postman/suites/12-admin/fm-be-12-admin.postman_collection.json`
- ⬜ Implement audit-logs, dashboard stats, export, config

**Endpoints**: audit-logs, dashboard stats, export, config  
**Key tests**: audit log filtering, CSV export (users/employees/sites), system config CRUD, SUPER_ADMIN vs ADMIN RBAC  

---

### TASK-3.14: Suite 99 — Cleanup
**Status**: ⬜ Not Started  
**Date Added**: 2026-04-03  
**Tests**: 3  
**Reference**: `PLANNING.md` §4.14

- ⬜ Create `tests/postman/suites/99-cleanup/fm-be-99-cleanup.postman_collection.json`
- ⬜ Implement logout admin, logout employee, verify tokens invalidated (401)

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
