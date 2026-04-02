-- V13: Create audit_logs table — tracks all admin write operations

CREATE TABLE audit_logs (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id         UUID         NOT NULL REFERENCES users(id),
    action          VARCHAR(100) NOT NULL,
    target_entity   VARCHAR(100) NOT NULL,
    target_id       UUID,
    metadata        JSONB,
    ip_address      VARCHAR(50),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
