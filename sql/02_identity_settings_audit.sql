DO $phase2$
BEGIN
    IF current_database() <> 'nourish_poltri_shop' THEN
        RAISE EXCEPTION 'Connect to nourish_poltri_shop first';
    END IF;
    PERFORM pg_advisory_xact_lock(76201901);
    IF NOT EXISTS (SELECT 1 FROM nourish.schema_version WHERE version = 1) THEN
        RAISE EXCEPTION 'Phase 1 SQL must be completed first';
    END IF;
    IF EXISTS (SELECT 1 FROM nourish.schema_version WHERE version = 2) THEN
        RAISE NOTICE 'Phase 2 already applied; nothing changed';
        RETURN;
    END IF;

    CREATE TABLE nourish.app_role (
        code VARCHAR(20) PRIMARY KEY,
        CONSTRAINT app_role_allowed CHECK (code IN ('admin','operator','viewer'))
    );
    CREATE TABLE nourish.app_user (
        id VARCHAR(64) PRIMARY KEY,
        username VARCHAR(64) NOT NULL UNIQUE,
        password_hash VARCHAR(100) NOT NULL,
        display_name VARCHAR(120) NOT NULL CHECK (length(trim(display_name)) > 0),
        active BOOLEAN NOT NULL DEFAULT TRUE,
        failed_attempts INTEGER NOT NULL DEFAULT 0 CHECK (failed_attempts >= 0),
        locked_until TIMESTAMPTZ,
        created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
        CONSTRAINT username_format CHECK (username ~ '^[a-z0-9._-]{3,64}$'),
        CONSTRAINT password_hash_format CHECK (password_hash ~ '^\$2[aby]\$12\$.{53}$')
    );
    CREATE TABLE nourish.app_user_role (
        user_id VARCHAR(64) NOT NULL REFERENCES nourish.app_user(id),
        role_code VARCHAR(20) NOT NULL REFERENCES nourish.app_role(code),
        PRIMARY KEY (user_id, role_code)
    );
    CREATE TABLE nourish.auth_session (
        token_hash VARCHAR(64) PRIMARY KEY CHECK (token_hash ~ '^[a-f0-9]{64}$'),
        user_id VARCHAR(64) NOT NULL REFERENCES nourish.app_user(id),
        created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
        expires_at TIMESTAMPTZ NOT NULL,
        revoked_at TIMESTAMPTZ,
        CHECK (expires_at > created_at)
    );
    CREATE INDEX auth_session_user_idx ON nourish.auth_session(user_id);
    CREATE INDEX auth_session_expiry_idx ON nourish.auth_session(expires_at);

    CREATE TABLE nourish.business_settings (
        id VARCHAR(20) PRIMARY KEY CHECK (id = 'business'),
        business_name VARCHAR(200) NOT NULL CHECK (length(trim(business_name)) > 0),
        owner_name VARCHAR(120) NOT NULL DEFAULT '',
        phone VARCHAR(40) NOT NULL DEFAULT '',
        email VARCHAR(200) NOT NULL DEFAULT '',
        address VARCHAR(1000) NOT NULL DEFAULT '',
        invoice_footer VARCHAR(500) NOT NULL DEFAULT '',
        low_stock_default NUMERIC(10,3) NOT NULL DEFAULT 5
            CHECK (low_stock_default BETWEEN 0 AND 1000000),
        currency VARCHAR(3) NOT NULL DEFAULT 'BDT' CHECK (currency = 'BDT'),
        ui_language VARCHAR(2) NOT NULL DEFAULT 'en' CHECK (ui_language IN ('bn','en')),
        updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
        updated_by VARCHAR(64) REFERENCES nourish.app_user(id),
        revision BIGINT NOT NULL DEFAULT 0
    );
    CREATE TABLE nourish.activity_log (
        id VARCHAR(64) PRIMARY KEY,
        actor_id VARCHAR(64) REFERENCES nourish.app_user(id),
        actor_name VARCHAR(120) NOT NULL,
        role VARCHAR(20) NOT NULL,
        action VARCHAR(50) NOT NULL,
        entity VARCHAR(50) NOT NULL,
        entity_id VARCHAR(64),
        details VARCHAR(500) NOT NULL,
        occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
    );
    CREATE INDEX activity_log_time_idx ON nourish.activity_log(occurred_at DESC, id DESC);
    CREATE INDEX activity_log_actor_idx ON nourish.activity_log(actor_id, occurred_at DESC);

    INSERT INTO nourish.app_role(code) VALUES ('admin'),('operator'),('viewer');
    INSERT INTO nourish.business_settings(id,business_name) VALUES ('business','NOURISH');
    INSERT INTO nourish.schema_version(version,description)
    VALUES (2,'Phase 2 - identity, settings and audit');
END
$phase2$;
