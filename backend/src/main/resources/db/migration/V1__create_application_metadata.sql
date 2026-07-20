CREATE TABLE application_metadata (
    id BIGSERIAL PRIMARY KEY,
    property_key VARCHAR(120) NOT NULL UNIQUE,
    property_value VARCHAR(500) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
