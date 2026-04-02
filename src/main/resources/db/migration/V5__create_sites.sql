-- V5: Create sites table with PostGIS geofence support

CREATE TABLE sites (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name            VARCHAR(255)  NOT NULL,
    address         TEXT,
    geofence_radius DOUBLE PRECISION,
    latitude        DOUBLE PRECISION,
    longitude       DOUBLE PRECISION,
    geofence        geography(Polygon, 4326),
    is_active       BOOLEAN       NOT NULL DEFAULT TRUE,
    client_id       UUID          REFERENCES clients(id),
    organization_id UUID          NOT NULL REFERENCES organizations(id),
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);
