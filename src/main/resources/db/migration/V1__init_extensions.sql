-- V1: Initialize PostgreSQL extensions required by the FM platform
-- PostGIS: geospatial queries (geofence containment, distance calculation)
-- pgcrypto: PII encryption at rest (Aadhaar, PAN)
-- uuid-ossp: UUID v4 primary key generation

CREATE EXTENSION IF NOT EXISTS "postgis";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
