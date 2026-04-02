-- V10: Create site_updates table — field reports with media

CREATE TABLE site_updates (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    employee_id     UUID         NOT NULL REFERENCES employees(id),
    site_id         UUID         NOT NULL REFERENCES sites(id),
    text            TEXT,
    media_urls      TEXT[],
    recorded_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
