-- V8: Create location_history table — high-volume, 30-day retention

CREATE TABLE location_history (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    employee_id     UUID             NOT NULL REFERENCES employees(id),
    latitude        DOUBLE PRECISION NOT NULL,
    longitude       DOUBLE PRECISION NOT NULL,
    accuracy        DOUBLE PRECISION,
    battery         INTEGER,
    signal          INTEGER,
    airplane_mode   BOOLEAN          NOT NULL DEFAULT FALSE,
    recorded_at     TIMESTAMPTZ      NOT NULL
);
