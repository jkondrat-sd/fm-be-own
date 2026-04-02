-- V12: Create device_tokens table for FCM push notifications

CREATE TABLE device_tokens (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id         UUID         NOT NULL REFERENCES users(id),
    fcm_token       TEXT         NOT NULL,
    device_info     VARCHAR(255),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    -- One FCM token per user-device pair
    CONSTRAINT uq_device_token_user_fcm UNIQUE (user_id, fcm_token)
);
