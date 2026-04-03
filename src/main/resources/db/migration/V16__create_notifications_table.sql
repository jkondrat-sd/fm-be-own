-- V16: Create notifications table for in-app notification tracking

CREATE TABLE notifications (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id         UUID         NOT NULL REFERENCES users(id),
    title           VARCHAR(255) NOT NULL,
    body            TEXT,
    type            VARCHAR(50)  NOT NULL DEFAULT 'GENERAL',
    is_read         BOOLEAN      NOT NULL DEFAULT FALSE,
    metadata        JSONB,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notifications_user_read ON notifications (user_id, is_read);
CREATE INDEX idx_notifications_created ON notifications (created_at DESC);
