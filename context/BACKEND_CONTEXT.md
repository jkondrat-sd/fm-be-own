# Backend API — Service Context for AI Agent

> **Read `PLANNING.md` first.** This file provides focused context for the NestJS Backend API agent.

---

## 1. Role in the Stack

The Backend API is the **central nervous system** of the FM platform. Every client (Mobile, Web Portal, Client Portal) communicates exclusively through this API. No client should ever connect directly to the database, Redis, or object storage.

### Upstream (Who calls you)
- **Android Mobile App** — Attendance punches, location updates, site updates, employee profile
- **Web Portal** — Payroll management, ops dashboard data, admin CRUD, employee onboarding
- **Client Portal** — Read-only attendance reports for client-assigned personnel
- **NGINX** — Reverse proxy, SSL termination, routes to API

### Downstream (What you call)
- **PostgreSQL + PostGIS** — Primary data store, geospatial queries
- **Redis** — Caching (sessions, employee status, live location), BullMQ backing store
- **BullMQ Workers** — Payroll processing, heatmap aggregation, media thumbnail generation, data retention cleanup
- **Cloudflare R2 / AWS S3** — Presigned URL generation for media uploads
- **Firebase Cloud Messaging** — Push notifications to mobile devices
- **SMTP / SMS Gateway** — OTP delivery, payroll notifications

---

## 2. Tech Stack

```
NestJS 10+ (Node.js 20 LTS)
├── TypeScript 5.x (strict mode)
├── Prisma ORM (PostgreSQL + PostGIS)
├── class-validator + class-transformer (DTO validation)
├── passport-jwt (auth strategy)
├── BullMQ (background jobs)
├── @nestjs/config (environment config)
├── @nestjs/swagger (auto OpenAPI docs)
├── sharp (image processing)
├── winston (structured logging)
└── Jest + Supertest (testing)
```

---

## 3. Module Architecture

```
src/
├── main.ts                         # Bootstrap, CORS, Swagger, global pipes
├── app.module.ts                   # Root module, imports all feature modules
├── common/                         # Shared utilities
│   ├── decorators/                 # @Roles(), @CurrentUser(), @ApiPaginated()
│   ├── guards/                     # JwtAuthGuard, RolesGuard
│   ├── interceptors/               # TransformResponseInterceptor, LoggingInterceptor
│   ├── filters/                    # GlobalExceptionFilter
│   ├── pipes/                      # ValidationPipe config
│   ├── dto/                        # PaginationDto, ApiResponseDto
│   └── constants/                  # Error codes, roles enum
├── modules/
│   ├── auth/                       # OTP login, JWT issue/refresh, password auth
│   │   ├── auth.module.ts
│   │   ├── auth.controller.ts
│   │   ├── auth.service.ts
│   │   ├── strategies/
│   │   │   └── jwt.strategy.ts
│   │   └── dto/
│   │       ├── otp-request.dto.ts
│   │       ├── otp-verify.dto.ts
│   │       └── login.dto.ts
│   ├── users/                      # User CRUD, role management
│   ├── employees/                  # Employee profiles, onboarding, documents
│   ├── sites/                      # Site CRUD, geofence polygon management
│   ├── attendance/                 # Punch in/out, attendance logs, reports
│   ├── location/                   # Live location ingestion, heatmap queries
│   ├── site-updates/               # Field employee site status reports
│   ├── shifts/                     # Shift schedule management
│   ├── payroll/                    # Payroll engine (PF, ESIC, TDS, PT)
│   ├── notifications/              # FCM push, email, SMS
│   ├── media/                      # Presigned URL generation, thumbnail jobs
│   └── admin/                      # Super admin operations, audit log
├── jobs/                           # BullMQ job processors
│   ├── payroll.processor.ts
│   ├── heatmap.processor.ts
│   ├── retention-cleanup.processor.ts
│   └── thumbnail.processor.ts
├── prisma/
│   ├── schema.prisma               # Database schema
│   ├── migrations/                 # Migration files
│   └── seed.ts                     # Dev seed data
└── test/
    ├── unit/                       # Unit tests (per module)
    ├── integration/                # Endpoint tests (Supertest)
    └── e2e/                        # Full flow tests
```

---

## 4. Database Schema (Prisma)

```prisma
generator client {
  provider        = "prisma-client-js"
  previewFeatures = ["postgresqlExtensions"]
}

datasource db {
  provider   = "postgresql"
  url        = env("DATABASE_URL")
  extensions = [postgis, pgcrypto]
}

// ── Connection Pool (Scalability: 3000+ users = ~100 req/s) ──
// Prisma defaults to 5 connections per instance — far too low.
// With 2 PM2 API instances, configure via DATABASE_URL query param:
//   ?connection_limit=20&pool_timeout=10
// Total: 2 instances × 20 = 40 connections → ensure PostgreSQL
// max_connections >= 50 (40 pool + headroom for migrations/admin).

enum Role {
  EMPLOYEE
  SUPERVISOR
  ADMIN
  SUPER_ADMIN
  CLIENT
}

enum PunchType {
  IN
  OUT
}

enum PayrollStatus {
  DRAFT
  PROCESSING
  PENDING_APPROVAL
  APPROVED
  REJECTED
  DISBURSED
}

enum DocumentType {
  AADHAAR
  PAN
  RATION_CARD
  POLICE_VERIFICATION
  PF_DOCUMENT
  ESIC_DOCUMENT
  PHOTO             // Engineering addition: not in FM-DETAILED scope but required for
                    // facial recognition (§9.2) and Employee ID card (MOBILE_CONTEXT §4.6)
}

model Organization {
  id        String   @id @default(uuid())
  name      String
  createdAt DateTime @default(now()) @map("created_at")
  updatedAt DateTime @updatedAt @map("updated_at")

  users    User[]
  sites    Site[]
  clients  Client[]

  @@map("organizations")
}

model User {
  id             String   @id @default(uuid())
  phone          String?  @unique
  email          String?  @unique
  passwordHash   String?  @map("password_hash")
  role           Role
  isActive       Boolean  @default(true) @map("is_active")
  preferredLang  String   @default("en") @map("preferred_lang")
  organizationId String   @map("organization_id")
  createdAt      DateTime @default(now()) @map("created_at")
  updatedAt      DateTime @updatedAt @map("updated_at")

  organization Organization @relation(fields: [organizationId], references: [id])
  employee     Employee?
  auditLogs    AuditLog[]
  refreshTokens RefreshToken[]

  @@index([organizationId])
  @@map("users")
}

model Employee {
  id              String   @id @default(uuid())
  userId          String   @unique @map("user_id")
  fullName        String   @map("full_name")
  dateOfBirth     DateTime? @map("date_of_birth")
  aadhaarEncrypted String? @map("aadhaar_encrypted") // pgcrypto
  panEncrypted    String?  @map("pan_encrypted")     // pgcrypto
  faceEmbedding   Float[]  @map("face_embedding")    // for facial recognition
  siteId          String?  @map("site_id")
  organizationId  String   @map("organization_id")
  createdAt       DateTime @default(now()) @map("created_at")
  updatedAt       DateTime @updatedAt @map("updated_at")

  user            User     @relation(fields: [userId], references: [id])
  site            Site?    @relation(fields: [siteId], references: [id])
  attendanceLogs  AttendanceLog[]
  locationHistory LocationHistory[]
  documents       EmployeeDocument[]
  shifts          ShiftAssignment[]
  siteUpdates     SiteUpdate[]
  salaryStructure SalaryStructure?

  @@index([siteId])
  @@index([organizationId])
  @@map("employees")
}

model Site {
  id             String   @id @default(uuid())
  name           String
  address        String?
  // PostGIS: geofence polygon stored as geography
  // Prisma doesn't natively support PostGIS, so use raw SQL for geospatial ops
  // geofence       Unsupported("geography(Polygon, 4326)")
  geofenceRadius Float?   @map("geofence_radius") // meters, for circular fallback
  latitude       Float?
  longitude      Float?
  isActive       Boolean  @default(true) @map("is_active")
  clientId       String?  @map("client_id")
  organizationId String   @map("organization_id")
  createdAt      DateTime @default(now()) @map("created_at")
  updatedAt      DateTime @updatedAt @map("updated_at")

  organization Organization @relation(fields: [organizationId], references: [id])
  client       Client?      @relation(fields: [clientId], references: [id])
  employees    Employee[]
  siteUpdates  SiteUpdate[]
  shifts       Shift[]

  @@index([organizationId])
  @@index([clientId])
  @@map("sites")
}

model Client {
  id             String   @id @default(uuid())
  name           String
  contactEmail   String?  @map("contact_email")
  contactPhone   String?  @map("contact_phone")
  organizationId String   @map("organization_id")
  createdAt      DateTime @default(now()) @map("created_at")
  updatedAt      DateTime @updatedAt @map("updated_at")

  organization Organization @relation(fields: [organizationId], references: [id])
  sites        Site[]

  @@index([organizationId])
  @@map("clients")
}

model AttendanceLog {
  id            String    @id @default(uuid())
  employeeId    String    @map("employee_id")
  punchType     PunchType @map("punch_type")
  punchTime     DateTime  @map("punch_time")
  latitude      Float
  longitude     Float
  accuracy      Float?    // GPS accuracy in meters
  faceMatchScore Float?   @map("face_match_score")
  isInsideGeofence Boolean @map("is_inside_geofence")
  deviceInfo    Json?     @map("device_info") // { battery, signal, model }
  siteId        String    @map("site_id")
  createdAt     DateTime  @default(now()) @map("created_at")

  employee Employee @relation(fields: [employeeId], references: [id])

  @@index([employeeId, punchTime])
  @@index([siteId, punchTime])
  @@map("attendance_logs")
}

model LocationHistory {
  id           String   @id @default(uuid())
  employeeId   String   @map("employee_id")
  latitude     Float
  longitude    Float
  accuracy     Float?
  battery      Int?     // percentage
  signal       String?  // "strong", "weak", "none"
  airplaneMode Boolean  @default(false) @map("airplane_mode")
  recordedAt   DateTime @map("recorded_at")
  createdAt    DateTime @default(now()) @map("created_at")

  employee Employee @relation(fields: [employeeId], references: [id])

  @@index([employeeId, recordedAt])
  @@index([recordedAt]) // for retention cleanup
  @@map("location_history")
}

model SiteUpdate {
  id          String   @id @default(uuid())
  employeeId  String   @map("employee_id")
  siteId      String   @map("site_id")
  text        String?
  mediaUrls   String[] @map("media_urls") // array of CDN URLs
  recordedAt  DateTime @map("recorded_at")
  createdAt   DateTime @default(now()) @map("created_at")

  employee Employee @relation(fields: [employeeId], references: [id])
  site     Site     @relation(fields: [siteId], references: [id])

  @@index([siteId, recordedAt])
  @@map("site_updates")
}

model Shift {
  id        String   @id @default(uuid())
  siteId    String   @map("site_id")
  name      String   // "Morning", "Evening", "Night"
  startTime String   @map("start_time") // "09:00" (HH:mm)
  endTime   String   @map("end_time")
  isActive  Boolean  @default(true) @map("is_active")

  site        Site             @relation(fields: [siteId], references: [id])
  assignments ShiftAssignment[]

  @@map("shifts")
}

model ShiftAssignment {
  id         String   @id @default(uuid())
  employeeId String   @map("employee_id")
  shiftId    String   @map("shift_id")
  date       DateTime @db.Date
  createdAt  DateTime @default(now()) @map("created_at")

  employee Employee @relation(fields: [employeeId], references: [id])
  shift    Shift    @relation(fields: [shiftId], references: [id])

  @@unique([employeeId, date])
  @@index([shiftId, date])
  @@map("shift_assignments")
}

model EmployeeDocument {
  id          String       @id @default(uuid())
  employeeId  String       @map("employee_id")
  type        DocumentType
  fileUrl     String       @map("file_url")
  isVerified  Boolean      @default(false) @map("is_verified")
  verifiedBy  String?      @map("verified_by")
  verifiedAt  DateTime?    @map("verified_at")
  createdAt   DateTime     @default(now()) @map("created_at")

  employee Employee @relation(fields: [employeeId], references: [id])

  @@index([employeeId])
  @@map("employee_documents")
}

model SalaryStructure {
  id          String @id @default(uuid())
  employeeId  String @unique @map("employee_id")
  basic       Float
  hra         Float       // House Rent Allowance
  conveyance  Float?
  special     Float?      // Special allowance
  grossSalary Float       @map("gross_salary")
  pfEmployee  Float       @map("pf_employee")   // Employee PF contribution
  pfEmployer  Float       @map("pf_employer")   // Employer PF contribution
  esicEmployee Float?     @map("esic_employee")
  esicEmployer Float?     @map("esic_employer")
  ptAmount    Float?      @map("pt_amount")     // Professional Tax
  createdAt   DateTime    @default(now()) @map("created_at")
  updatedAt   DateTime    @updatedAt @map("updated_at")

  employee Employee @relation(fields: [employeeId], references: [id])

  @@map("salary_structures")
}

model PayrollRun {
  id          String        @id @default(uuid())
  month       Int
  year        Int
  status      PayrollStatus @default(DRAFT)
  processedBy String?       @map("processed_by")
  approvedBy  String?       @map("approved_by")
  bankFileUrl String?       @map("bank_file_url")
  totalGross  Float?        @map("total_gross")
  totalNet    Float?        @map("total_net")
  totalPf     Float?        @map("total_pf")
  totalEsic   Float?        @map("total_esic")
  totalTds    Float?        @map("total_tds")
  organizationId String     @map("organization_id")
  createdAt   DateTime      @default(now()) @map("created_at")
  updatedAt   DateTime      @updatedAt @map("updated_at")

  payslips Payslip[]

  @@unique([month, year, organizationId])
  @@map("payroll_runs")
}

model Payslip {
  id            String   @id @default(uuid())
  payrollRunId  String   @map("payroll_run_id")
  employeeId    String   @map("employee_id")
  daysWorked    Int      @map("days_worked")
  daysAbsent    Int      @map("days_absent")
  grossEarnings Float    @map("gross_earnings")
  pfDeduction   Float    @map("pf_deduction")
  esicDeduction Float?   @map("esic_deduction")
  tdsDeduction  Float?   @map("tds_deduction")
  ptDeduction   Float?   @map("pt_deduction")
  otherDeductions Float? @map("other_deductions")
  netPayable    Float    @map("net_payable")
  createdAt     DateTime @default(now()) @map("created_at")

  payrollRun PayrollRun @relation(fields: [payrollRunId], references: [id])

  @@index([payrollRunId])
  @@index([employeeId])
  @@map("payslips")
}

model AuditLog {
  id            String   @id @default(uuid())
  userId        String   @map("user_id")
  action        String   // "CREATE_SITE", "APPROVE_PAYROLL", "DELETE_USER"
  targetEntity  String   @map("target_entity") // "sites", "payroll_runs"
  targetId      String   @map("target_id")
  metadata      Json?    // additional context
  ipAddress     String?  @map("ip_address")
  createdAt     DateTime @default(now()) @map("created_at")

  user User @relation(fields: [userId], references: [id])

  @@index([userId])
  @@index([createdAt])
  @@map("audit_logs")
}

model RefreshToken {
  id        String   @id @default(uuid())
  userId    String   @map("user_id")
  token     String   @unique
  expiresAt DateTime @map("expires_at")
  deviceInfo String? @map("device_info")
  createdAt DateTime @default(now()) @map("created_at")

  user User @relation(fields: [userId], references: [id])

  @@index([userId])
  @@map("refresh_tokens")
}
```

---

## 5. API Endpoints (Full Reference)

### 5.1 Auth Module
```
POST   /v1/auth/otp/request       # Send OTP to phone (mobile)
POST   /v1/auth/otp/verify        # Verify OTP, return JWT
POST   /v1/auth/login             # Email/password login (web)
POST   /v1/auth/refresh           # Refresh access token
POST   /v1/auth/logout            # Invalidate refresh token
```

### 5.2 Attendance Module
```
POST   /v1/attendance/punch       # Punch in/out with location + face
GET    /v1/attendance/today        # Employee's today's attendance
GET    /v1/attendance/logs         # Paginated attendance logs (admin/client)
GET    /v1/attendance/report       # Attendance report (daily/weekly/monthly)
GET    /v1/attendance/site/:siteId # Site-specific attendance summary
```

### 5.3 Location Module
```
POST   /v1/location/update        # Mobile sends live location
GET    /v1/location/live           # Live employee locations (ops dashboard)
GET    /v1/location/heatmap        # Heatmap data for a site/date range
GET    /v1/location/alerts         # Geofence violation alerts
```

### 5.4 Sites Module
```
GET    /v1/sites                   # List all sites (paginated)
POST   /v1/sites                   # Create site (admin)
GET    /v1/sites/:id               # Site details with geofence
PUT    /v1/sites/:id               # Update site
DELETE /v1/sites/:id               # Soft delete site
POST   /v1/sites/:id/geofence     # Set geofence polygon (GeoJSON)
GET    /v1/sites/:id/employees     # Employees assigned to site
```

### 5.5 Employees Module
```
GET    /v1/employees               # List employees (paginated, filterable)
POST   /v1/employees               # Create/onboard employee
GET    /v1/employees/:id           # Employee profile
PUT    /v1/employees/:id           # Update employee
GET    /v1/employees/:id/documents # List employee documents
POST   /v1/employees/:id/documents # Upload document (gets presigned URL)
PUT    /v1/employees/:id/documents/:docId/verify  # Verify document
POST   /v1/employees/:id/face      # Upload face image for recognition
```

### 5.6 Shifts Module
> **Note**: FM-OVERVIEW.md references "task view" for mobile. In V1, shift assignments serve as the employee's task list — there is no separate task entity. The schedule endpoint below provides the data for the mobile "task view".

```
GET    /v1/shifts                  # List shifts for site
POST   /v1/shifts                  # Create shift
PUT    /v1/shifts/:id              # Update shift
POST   /v1/shifts/assign           # Assign employee to shift on date
GET    /v1/shifts/schedule         # Get schedule for employee/date range
```

### 5.7 Site Updates Module
```
POST   /v1/site-updates            # Create site update (employee)
GET    /v1/site-updates            # List updates (paginated by site/date)
GET    /v1/site-updates/:id        # Single update details
```

### 5.8 Payroll Module
```
GET    /v1/payroll/salary-structures          # List all salary structures
POST   /v1/payroll/salary-structures          # Create salary structure
PUT    /v1/payroll/salary-structures/:id      # Update salary structure
POST   /v1/payroll/run                         # Initiate payroll run
GET    /v1/payroll/run/:id                     # Payroll run details
GET    /v1/payroll/run/:id/status              # Payroll run status (for polling)
POST   /v1/payroll/run/:id/approve             # Approve payroll
POST   /v1/payroll/run/:id/reject              # Reject payroll
GET    /v1/payroll/run/:id/bank-file           # Download bank disbursement file
GET    /v1/payroll/run/:id/payslips            # List payslips for a run
GET    /v1/payroll/reports                      # Payroll reports (PF, ESIC, TDS)
```

### 5.9 Notifications Module
```
POST   /v1/notifications/send       # Send targeted notification (admin)
GET    /v1/notifications            # List user's notifications
PUT    /v1/notifications/:id/read   # Mark notification as read
POST   /v1/notifications/register-device  # Register FCM token
```

### 5.10 Admin Module
```
GET    /v1/admin/users              # List all users (super admin)
POST   /v1/admin/users              # Create user with role
PUT    /v1/admin/users/:id          # Update user role/status
GET    /v1/admin/audit-logs         # Audit trail (paginated)
GET    /v1/admin/dashboard/stats    # System-wide statistics
POST   /v1/admin/export/:entity     # Export data (CSV/Excel)
GET    /v1/admin/config             # System configuration
PUT    /v1/admin/config             # Update system configuration
```

### 5.11 Media Module
```
POST   /v1/media/presigned-url      # Get presigned upload URL
POST   /v1/media/confirm            # Confirm upload completion
```

---

## 6. Key Implementation Patterns

### 6.1 Response Interceptor
All responses MUST use the standard envelope from `PLANNING.md`:
```typescript
// common/interceptors/transform-response.interceptor.ts
@Injectable()
export class TransformResponseInterceptor implements NestInterceptor {
  intercept(context: ExecutionContext, next: CallHandler): Observable<any> {
    return next.handle().pipe(
      map(data => ({
        success: true,
        data: data?.data ?? data,
        meta: data?.meta ?? null,
        error: null,
      })),
    );
  }
}
```

### 6.2 Global Exception Filter
```typescript
// common/filters/global-exception.filter.ts
@Catch()
export class GlobalExceptionFilter implements ExceptionFilter {
  catch(exception: unknown, host: ArgumentsHost) {
    // Map to standard error envelope
    // Log with correlation ID
    // Return { success: false, data: null, error: { code, message, details } }
  }
}
```

### 6.3 Geofence Validation (Raw SQL via Prisma)
```typescript
// modules/attendance/attendance.service.ts
async validateGeofence(lat: number, lng: number, siteId: string): Promise<boolean> {
  const result = await this.prisma.$queryRaw`
    SELECT ST_DWithin(
      geofence::geography,
      ST_SetSRID(ST_MakePoint(${lng}, ${lat}), 4326)::geography,
      0 -- 0 meter tolerance, fence is the boundary
    ) as is_inside
    FROM sites
    WHERE id = ${siteId}
  `;
  return result[0]?.is_inside ?? false;
}
```

### 6.4 Idempotent Punch Submission
Mobile may retry punches (network issues). Backend MUST handle duplicates:
```typescript
// Check if punch exists within 5-minute window
const existing = await this.prisma.attendanceLog.findFirst({
  where: {
    employeeId,
    punchType,
    punchTime: {
      gte: new Date(timestamp - 5 * 60 * 1000),
      lte: new Date(timestamp + 5 * 60 * 1000),
    },
  },
});
if (existing) return { code: 'DUPLICATE_PUNCH', punchId: existing.id };
```

### 6.5 BullMQ Job Definitions
```typescript
// Payroll processing job
interface PayrollJobData {
  payrollRunId: string;
  month: number;
  year: number;
  organizationId: string;
}

// Retention cleanup job (runs daily at 2 AM)
interface RetentionCleanupJobData {
  retentionDays: number; // 30
  entities: ('location_history' | 'site_updates')[];
}

// Heatmap aggregation job (runs daily at 3 AM)
interface HeatmapAggregationJobData {
  siteId: string;
  date: string; // ISO date
}
```

### 6.6 Location Batch-Insert Strategy (Scalability Critical)

At 3,000 users, location updates arrive at **100 req/s**. Writing each to PostgreSQL individually is unsustainable. Strategy:

```typescript
// modules/location/location.service.ts

// 1. EVERY request: write to Redis only (fast path, <100ms)
async handleLocationUpdate(dto: LocationUpdateDto) {
  await this.redis.setex(
    `loc:${dto.employeeId}`,
    120, // TTL 120 seconds
    JSON.stringify({ lat: dto.lat, lng: dto.lng, battery: dto.battery, signal: dto.signal, airplaneMode: dto.airplaneMode, timestamp: dto.timestamp })
  );
  // Geofence check happens here too (PostGIS query, but only on the live update)
}

// 2. SAMPLED: BullMQ repeatable job flushes Redis → PostgreSQL every 5 min
//    Job: 'flush-locations' on 'location' queue, repeat: { every: 300_000 }
//    - SCAN Redis for all 'loc:*' keys
//    - Batch INSERT into location_history (Prisma createMany)
//    - ~3,000 rows per flush = trivial for PostgreSQL
//
// This reduces PostgreSQL writes from 100/s → ~10/s (batched).
```

---

## 7. Indian Statutory Compliance — Payroll Rules

### 7.1 Provident Fund (PF)
- **Employee contribution**: 12% of Basic + DA (capped at ₹15,000 base)
- **Employer contribution**: 12% of Basic + DA (3.67% to EPF, 8.33% to EPS)
- **PF ECR file**: Generate monthly for EPFO portal upload

### 7.2 ESIC (Employee State Insurance)
- **Applicable if**: Gross salary ≤ ₹21,000/month
- **Employee contribution**: 0.75% of Gross
- **Employer contribution**: 3.25% of Gross
- **Re-evaluate** every April and October

### 7.3 TDS (Tax Deducted at Source)
- **Old regime slabs** (default, configurable):
  - ₹0 – ₹2,50,000: Nil
  - ₹2,50,001 – ₹5,00,000: 5%
  - ₹5,00,001 – ₹10,00,000: 20%
  - Above ₹10,00,000: 30%
- **Monthly TDS** = Estimated annual tax / 12
- **Important**: Store TDS slabs as configuration, not hardcoded. They change annually.

### 7.4 Professional Tax (PT)
- **State-specific** (Maharashtra for V1):
  - Gross ≤ ₹7,500: Nil
  - ₹7,501 – ₹10,000: ₹175/month
  - Above ₹10,000: ₹200/month (₹300 in February)
- **Store PT rules** as configurable per-state data.

---

## 8. Background Jobs (BullMQ)

| Job Name | Queue | Schedule | Description |
|----------|-------|----------|-------------|
| `process-payroll` | `payroll` | On-demand | Full payroll computation for a month |
| `generate-bank-file` | `payroll` | After payroll approval | CSV/Excel for bank disbursement |
| `cleanup-location-data` | `maintenance` | Daily 2:00 AM IST | Delete location_history > 30 days |
| `cleanup-media` | `maintenance` | Daily 2:30 AM IST | Delete expired media from R2/S3 |
| `aggregate-heatmap` | `analytics` | Daily 3:00 AM IST | Aggregate location data for heatmaps |
| `generate-thumbnail` | `media` | On upload | Resize images, extract video frames |
| `send-shift-reminder` | `notifications` | 30 min before shift | FCM notification to employee |
| `geofence-alert` | `notifications` | Real-time (triggered) | Alert when employee exits geofence >5 min |

---

## 9. Critical Design Decisions

### 9.1 PostGIS Geofence Storage
- Sites with simple circular geofences: use `latitude`, `longitude`, `geofenceRadius` columns.
- Sites with polygon geofences: store as `geography(Polygon, 4326)` column (managed via raw SQL since Prisma doesn't natively support PostGIS).
- **Migration approach**: Start with circular geofences for V1 MVP, add polygon support as an enhancement.

### 9.2 Face Recognition Architecture
- **Storage**: Face embedding vector stored as `Float[]` in PostgreSQL.
- **Comparison**: Use cosine similarity on backend. Threshold: 0.85.
- **Mobile captures** the face image, uploads via presigned URL, and sends the URL in the punch request.
- **Backend** extracts embedding from the upload (using a face-api.js or Python microservice), compares with stored embedding.
- **V2 consideration**: Move face comparison to a dedicated Python microservice for GPU acceleration.

### 9.3 Multi-Tenancy
- **Strategy**: Single database, `organizationId` column on all tables.
- **Every query** MUST filter by `organizationId`. Use a NestJS interceptor to inject this automatically.
- **Row-level security** (PostgreSQL RLS) as an additional safety net.

---

## 10. Performance & Scalability (3,000+ Users)

> **Design target**: 3,000 active field employees at launch. System must handle **100 req/s sustained** (location updates) with headroom for bursty punch-ins.

### 10.1 Endpoint Performance Targets

| Endpoint | Target (p95) | Strategy |
|----------|-------------|----------|
| `POST /v1/attendance/punch` | < 500ms | Geofence check is the bottleneck. Use GiST spatial index. |
| `POST /v1/location/update` | < 100ms | Redis write only. PostgreSQL write is async (batched every 5 min, see §6.6). |
| `GET /v1/location/live` | < 200ms | Pure Redis read (SCAN `loc:*` for site). |
| `GET /v1/attendance/report` | < 1s | Indexed queries. Consider materialized views for common reports. |
| `POST /v1/payroll/run` | < 500ms | Returns immediately. BullMQ does the work. |
| `GET /v1/payroll/run/:id/status` | < 50ms | Simple DB read. |

### 10.2 Redis Caching Strategy

Cache hot-path data to reduce PostgreSQL load:

| Cache Key Pattern | Data | TTL | Invalidation |
|-------------------|------|-----|---------------|
| `loc:{employeeId}` | Live location + device info | 120s | Overwritten every 30s by mobile |
| `profile:{employeeId}` | Employee profile (name, site, photo URL) | 10 min | Invalidate on profile update |
| `shift:{employeeId}:{date}` | Today's shift assignment | 1 hour | Invalidate on shift reassignment |
| `site:{siteId}:geofence` | Geofence polygon/radius | 30 min | Invalidate on geofence edit |
| `report:attendance:{siteId}:{date}` | Daily attendance summary (for client portal) | 5 min | Short TTL, auto-expires |

### 10.3 Rate Limiting (Per-User)

Prevent abuse and protect backend at scale:

| Endpoint | Limit | Enforcement |
|----------|-------|-------------|
| `POST /v1/auth/otp/request` | 5 req/min per phone | Redis counter + NestJS throttler |
| `POST /v1/location/update` | 1 req/10s per user | Redis counter (reject if too frequent) |
| `POST /v1/attendance/punch` | 1 req/min per user | Business logic (DUPLICATE_PUNCH check covers this) |
| All other endpoints | 100 req/min per user | NestJS `@nestjs/throttler` global guard |

### 10.4 Connection Pooling

- **Prisma**: `?connection_limit=20&pool_timeout=10` on `DATABASE_URL` per instance
- **2 API instances**: 2 × 20 = 40 connections total
- **PostgreSQL**: Set `max_connections = 60` (40 pool + admin headroom)
- **Redis**: Default single-connection per client is sufficient for V1 (Node.js is single-threaded per process)

---

## 11. Logging & Monitoring

```typescript
// Structured logging format (winston)
{
  "level": "info",
  "timestamp": "2026-03-30T10:30:00+05:30",
  "correlationId": "uuid-v4",
  "module": "attendance",
  "action": "punch_in",
  "userId": "uuid",
  "employeeId": "uuid",
  "siteId": "uuid",
  "duration_ms": 145,
  "metadata": { "isInsideGeofence": true, "faceMatchScore": 0.92 }
}
```

- **Correlation ID**: Generated per request, passed to all downstream services/jobs.
- **Health check**: `GET /v1/health` — returns DB, Redis, and storage connectivity status.
- **Metrics**: Use `prom-client` for Prometheus-compatible metrics if monitoring is set up.

---

## 12. Deployment Must-Dos

The following are cross-cutting concerns identified during tech stack analysis that should be addressed before or during production launch:
- **Database Failover**: Currently a single point of failure. Implement PostgreSQL streaming replication (warm standby) or use a managed database service before production launch.
- **Redis Live Location Lookups**: The `SCAN loc:*` query is O(N) and blocks Redis. Switch to per-site Redis `HASH` or `SET` for O(1) performance to handle 3,000+ users.
- **Database Partitioning**: Implement table partitioning for `location_history` by month to handle unbounded growth (~2.6M rows/month at 3K users).
