-- V15: Create performance-critical indexes

-- ===================== Spatial Indexes =====================

-- GiST index for geofence containment checks (ST_Contains, ST_DWithin)
CREATE INDEX idx_sites_geofence ON sites USING GIST (geofence);

-- Location history spatial index for heatmap queries
CREATE INDEX idx_location_history_coords ON location_history
    USING GIST ((ST_SetSRID(ST_MakePoint(longitude, latitude), 4326)::geography));

-- ===================== Temporal Indexes =====================

-- Location history by recording time (retention cleanup, time-range queries)
CREATE INDEX idx_location_history_recorded ON location_history (recorded_at DESC);

-- Attendance fast lookups by employee + punch time
CREATE INDEX idx_attendance_employee_time ON attendance_logs (employee_id, punch_time DESC);

-- Attendance by site + punch time (site reports)
CREATE INDEX idx_attendance_site_time ON attendance_logs (site_id, punch_time DESC);

-- ===================== Foreign Key / Lookup Indexes =====================

-- Employees by site (site employee listing)
CREATE INDEX idx_employees_site ON employees (site_id);

-- Employees by organization (multi-tenant queries)
CREATE INDEX idx_employees_org ON employees (organization_id);

-- Users by organization
CREATE INDEX idx_users_org ON users (organization_id);

-- Shift assignments by employee + date (schedule lookups)
CREATE INDEX idx_shift_assignments_emp_date ON shift_assignments (employee_id, date);

-- Site updates by site + recorded time (feed queries)
CREATE INDEX idx_site_updates_site_time ON site_updates (site_id, recorded_at DESC);

-- Payslips by payroll run (batch retrieval)
CREATE INDEX idx_payslips_run ON payslips (payroll_run_id);

-- Audit logs by created time (time-range search)
CREATE INDEX idx_audit_logs_created ON audit_logs (created_at DESC);

-- Refresh tokens by user (cleanup on logout)
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);

-- Device tokens by user (notification targeting)
CREATE INDEX idx_device_tokens_user ON device_tokens (user_id);

-- ===================== Partial Indexes =====================

-- Only active sites (most queries filter by is_active = true)
CREATE INDEX idx_active_sites ON sites (id) WHERE is_active = true;

-- Only active users
CREATE INDEX idx_active_users ON users (id) WHERE is_active = true;
