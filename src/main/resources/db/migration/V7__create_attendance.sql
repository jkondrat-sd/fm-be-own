-- V7: Create attendance_logs table

CREATE TABLE attendance_logs (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    employee_id         UUID         NOT NULL REFERENCES employees(id),
    punch_type          VARCHAR(10)  NOT NULL,
    punch_time          TIMESTAMPTZ  NOT NULL,
    latitude            DOUBLE PRECISION NOT NULL,
    longitude           DOUBLE PRECISION NOT NULL,
    accuracy            DOUBLE PRECISION,
    face_match_score    DOUBLE PRECISION,
    is_inside_geofence  BOOLEAN      NOT NULL DEFAULT TRUE,
    device_info         JSONB,
    site_id             UUID         NOT NULL REFERENCES sites(id),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
