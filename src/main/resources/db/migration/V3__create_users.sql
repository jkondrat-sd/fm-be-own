-- V3: Create users table — authentication identity for all roles

CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    phone           VARCHAR(20)  UNIQUE,
    email           VARCHAR(255) UNIQUE,
    password_hash   VARCHAR(255),
    role            VARCHAR(50)  NOT NULL DEFAULT 'EMPLOYEE',
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    preferred_lang  VARCHAR(10)  NOT NULL DEFAULT 'en',
    organization_id UUID         NOT NULL REFERENCES organizations(id),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    -- At least one login method must exist
    CONSTRAINT chk_user_login CHECK (phone IS NOT NULL OR email IS NOT NULL)
);
