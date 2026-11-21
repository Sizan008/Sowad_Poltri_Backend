BEGIN;
DO $$
BEGIN
    IF current_database() <> 'nourish_poltri_shop' THEN
        RAISE EXCEPTION 'Connect to nourish_poltri_shop before running this script';
    END IF;
END $$;
CREATE SCHEMA IF NOT EXISTS nourish AUTHORIZATION CURRENT_USER;
CREATE TABLE IF NOT EXISTS nourish.schema_version (
    version INTEGER PRIMARY KEY CHECK (version > 0),
    description VARCHAR(200) NOT NULL CHECK (length(trim(description)) > 0),
    installed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
INSERT INTO nourish.schema_version (version, description)
VALUES (1, 'Phase 1 - project foundation')
ON CONFLICT (version) DO NOTHING;
COMMIT;
SELECT version, description, installed_at
FROM nourish.schema_version ORDER BY version;
