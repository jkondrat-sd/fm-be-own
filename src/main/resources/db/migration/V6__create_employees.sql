-- V6: Create employees and employee_documents tables

CREATE TABLE employees (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id             UUID          NOT NULL UNIQUE REFERENCES users(id),
    full_name           VARCHAR(255)  NOT NULL,
    dob                 DATE,
    aadhaar_encrypted   BYTEA,
    pan_encrypted       BYTEA,
    face_embedding      REAL[],
    site_id             UUID          REFERENCES sites(id),
    organization_id     UUID          NOT NULL REFERENCES organizations(id),
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE TABLE employee_documents (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    employee_id     UUID         NOT NULL REFERENCES employees(id) ON DELETE CASCADE,
    type            VARCHAR(50)  NOT NULL,
    file_url        TEXT         NOT NULL,
    is_verified     BOOLEAN      NOT NULL DEFAULT FALSE,
    verified_by     UUID         REFERENCES users(id),
    verified_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
