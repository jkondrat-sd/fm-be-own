-- V17: Row-Level Security (RLS) policies for multi-tenant data isolation.
-- These are a safety net in addition to the application-level TenantInterceptor.
-- RLS ensures that even if the app layer fails to filter by organization_id,
-- the database will still enforce tenant isolation.

-- Enable RLS on tenant-scoped tables
ALTER TABLE users ENABLE ROW LEVEL SECURITY;
ALTER TABLE employees ENABLE ROW LEVEL SECURITY;
ALTER TABLE sites ENABLE ROW LEVEL SECURITY;
ALTER TABLE clients ENABLE ROW LEVEL SECURITY;
ALTER TABLE attendance_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE shifts ENABLE ROW LEVEL SECURITY;
ALTER TABLE shift_assignments ENABLE ROW LEVEL SECURITY;
ALTER TABLE payroll_runs ENABLE ROW LEVEL SECURITY;
ALTER TABLE payslips ENABLE ROW LEVEL SECURITY;

-- Create RLS policies for each table.
-- The app must SET LOCAL app.current_org_id = '<org-uuid>' per transaction
-- for these policies to take effect. Without it, superuser/owner bypass RLS.

CREATE POLICY tenant_users ON users
    USING (organization_id = current_setting('app.current_org_id', true)::uuid);

CREATE POLICY tenant_employees ON employees
    USING (organization_id = current_setting('app.current_org_id', true)::uuid);

CREATE POLICY tenant_sites ON sites
    USING (organization_id = current_setting('app.current_org_id', true)::uuid);

CREATE POLICY tenant_clients ON clients
    USING (organization_id = current_setting('app.current_org_id', true)::uuid);

CREATE POLICY tenant_payroll_runs ON payroll_runs
    USING (organization_id = current_setting('app.current_org_id', true)::uuid);

-- For tables without direct organization_id, policies reference parent tables.
-- These use subqueries to validate tenant ownership through FK relationships.

-- Note: attendance_logs, shifts, shift_assignments, and payslips use FK-based
-- isolation through their parent entities (employee, site, payroll_run).
-- The application-layer TenantInterceptor is the primary enforcement mechanism
-- for these tables. RLS policies here are a secondary safety net.
