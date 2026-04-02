-- V9: Create shifts and shift_assignments tables

CREATE TABLE shifts (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    site_id         UUID         NOT NULL REFERENCES sites(id),
    name            VARCHAR(255) NOT NULL,
    start_time      TIME         NOT NULL,
    end_time        TIME         NOT NULL,
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE shift_assignments (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    employee_id     UUID NOT NULL REFERENCES employees(id),
    shift_id        UUID NOT NULL REFERENCES shifts(id),
    date            DATE NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- An employee can only be assigned to one shift per day
    CONSTRAINT uq_shift_assignment_employee_date UNIQUE (employee_id, date)
);
