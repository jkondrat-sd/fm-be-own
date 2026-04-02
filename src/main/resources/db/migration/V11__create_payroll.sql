-- V11: Create payroll tables — salary structures, payroll runs, payslips

CREATE TABLE salary_structures (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    employee_id     UUID             NOT NULL UNIQUE REFERENCES employees(id),
    basic           DECIMAL(12, 2)   NOT NULL,
    hra             DECIMAL(12, 2)   NOT NULL DEFAULT 0,
    conveyance      DECIMAL(12, 2)   DEFAULT 0,
    special         DECIMAL(12, 2)   DEFAULT 0,
    gross_salary    DECIMAL(12, 2)   NOT NULL,
    pf_employee     DECIMAL(12, 2)   NOT NULL DEFAULT 0,
    pf_employer     DECIMAL(12, 2)   NOT NULL DEFAULT 0,
    esic_employee   DECIMAL(12, 2)   DEFAULT 0,
    esic_employer   DECIMAL(12, 2)   DEFAULT 0,
    pt_amount       DECIMAL(12, 2)   DEFAULT 0,
    created_at      TIMESTAMPTZ      NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ      NOT NULL DEFAULT NOW()
);

CREATE TABLE payroll_runs (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    month           INTEGER      NOT NULL,
    year            INTEGER      NOT NULL,
    status          VARCHAR(50)  NOT NULL DEFAULT 'DRAFT',
    processed_by    UUID         REFERENCES users(id),
    approved_by     UUID         REFERENCES users(id),
    bank_file_url   TEXT,
    total_gross     DECIMAL(14, 2),
    total_net       DECIMAL(14, 2),
    total_pf        DECIMAL(14, 2),
    total_esic      DECIMAL(14, 2),
    total_tds       DECIMAL(14, 2),
    organization_id UUID         NOT NULL REFERENCES organizations(id),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_payroll_run_month_year_org UNIQUE (month, year, organization_id)
);

CREATE TABLE payslips (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    payroll_run_id      UUID           NOT NULL REFERENCES payroll_runs(id),
    employee_id         UUID           NOT NULL REFERENCES employees(id),
    days_worked         INTEGER        NOT NULL DEFAULT 0,
    days_absent         INTEGER        NOT NULL DEFAULT 0,
    gross_earnings      DECIMAL(12, 2) NOT NULL,
    pf_deduction        DECIMAL(12, 2) NOT NULL DEFAULT 0,
    esic_deduction      DECIMAL(12, 2) DEFAULT 0,
    tds_deduction       DECIMAL(12, 2) DEFAULT 0,
    pt_deduction        DECIMAL(12, 2) DEFAULT 0,
    other_deductions    DECIMAL(12, 2) DEFAULT 0,
    net_payable         DECIMAL(12, 2) NOT NULL,
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);
