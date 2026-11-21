DO $phase4$
BEGIN
    IF current_database() <> 'nourish_poltri_shop' THEN
        RAISE EXCEPTION 'Connect to nourish_poltri_shop first';
    END IF;
    PERFORM pg_advisory_xact_lock(76201901);
    IF NOT EXISTS (SELECT 1 FROM nourish.schema_version WHERE version = 3) THEN
        RAISE EXCEPTION 'Phase 3 must be applied first';
    END IF;
    IF EXISTS (SELECT 1 FROM nourish.schema_version WHERE version = 4) THEN
        RAISE NOTICE 'Phase 4 already applied; nothing changed';
        RETURN;
    END IF;

    CREATE TABLE nourish.customer (
        id varchar(64) PRIMARY KEY,
        name_en varchar(120) NOT NULL,
        name_bn varchar(120) NOT NULL DEFAULT '',
        address_en varchar(240) NOT NULL,
        address_bn varchar(240) NOT NULL DEFAULT '',
        phone varchar(11) NOT NULL UNIQUE,
        whatsapp_phone varchar(11) NOT NULL DEFAULT '',
        status varchar(8) NOT NULL DEFAULT 'active',
        note varchar(500) NOT NULL DEFAULT '',
        archived boolean NOT NULL DEFAULT false,
        created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
        updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
        revision bigint NOT NULL DEFAULT 0,
        CHECK (name_en = btrim(name_en) AND name_en ~ '^[ -~]{2,120}$' AND name_en ~ '[A-Za-z]'),
        CHECK (address_en = btrim(address_en) AND address_en ~ '^[ -~]{2,240}$' AND address_en ~ '[A-Za-z]'),
        CHECK (phone ~ '^01[3-9][0-9]{8}$'),
        CHECK (whatsapp_phone = '' OR whatsapp_phone ~ '^01[3-9][0-9]{8}$'),
        CHECK (status IN ('active','inactive')),
        CHECK (NOT archived OR status = 'inactive')
    );
    CREATE INDEX customer_status_name_idx
        ON nourish.customer(archived,status,lower(name_en),id);
    CREATE INDEX customer_name_idx ON nourish.customer(lower(name_en),id);

    CREATE TABLE nourish.supplier (
        id varchar(64) PRIMARY KEY,
        name_en varchar(120) NOT NULL,
        name_bn varchar(120) NOT NULL DEFAULT '',
        address_en varchar(240) NOT NULL,
        address_bn varchar(240) NOT NULL DEFAULT '',
        phone varchar(11) NOT NULL UNIQUE,
        whatsapp_phone varchar(11) NOT NULL DEFAULT '',
        status varchar(8) NOT NULL DEFAULT 'active',
        note varchar(500) NOT NULL DEFAULT '',
        archived boolean NOT NULL DEFAULT false,
        created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
        updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
        revision bigint NOT NULL DEFAULT 0,
        CHECK (name_en = btrim(name_en) AND name_en ~ '^[ -~]{2,120}$' AND name_en ~ '[A-Za-z]'),
        CHECK (address_en = btrim(address_en) AND address_en ~ '^[ -~]{2,240}$' AND address_en ~ '[A-Za-z]'),
        CHECK (phone ~ '^01[3-9][0-9]{8}$'),
        CHECK (whatsapp_phone = '' OR whatsapp_phone ~ '^01[3-9][0-9]{8}$'),
        CHECK (status IN ('active','inactive')),
        CHECK (NOT archived OR status = 'inactive')
    );
    CREATE INDEX supplier_status_name_idx
        ON nourish.supplier(archived,status,lower(name_en),id);
    CREATE INDEX supplier_name_idx ON nourish.supplier(lower(name_en),id);

    ALTER TABLE nourish.product_packaging
        ADD CONSTRAINT product_packaging_id_product_unique UNIQUE(id,product_id);

    CREATE TABLE nourish.customer_product_rate (
        id varchar(64) PRIMARY KEY,
        customer_id varchar(64) NOT NULL REFERENCES nourish.customer(id),
        product_id varchar(64) NOT NULL REFERENCES nourish.product(id),
        price_per_unit numeric(19,6) NOT NULL
            CHECK (price_per_unit > 0 AND price_per_unit <= 1000000),
        revision bigint NOT NULL DEFAULT 0,
        UNIQUE(customer_id,product_id)
    );
    CREATE INDEX customer_product_rate_product_idx
        ON nourish.customer_product_rate(product_id);

    CREATE TABLE nourish.customer_package_rate (
        id varchar(64) PRIMARY KEY,
        customer_id varchar(64) NOT NULL REFERENCES nourish.customer(id),
        product_id varchar(64) NOT NULL REFERENCES nourish.product(id),
        packaging_id varchar(64) NOT NULL,
        price_per_package numeric(19,2) NOT NULL
            CHECK (price_per_package > 0 AND price_per_package <= 1000000000),
        revision bigint NOT NULL DEFAULT 0,
        UNIQUE(customer_id,packaging_id),
        FOREIGN KEY(packaging_id,product_id)
            REFERENCES nourish.product_packaging(id,product_id)
    );
    CREATE INDEX customer_package_rate_product_idx
        ON nourish.customer_package_rate(product_id);
    CREATE INDEX customer_package_rate_packaging_idx
        ON nourish.customer_package_rate(packaging_id,product_id);

    CREATE TABLE nourish.receiving_account (
        id varchar(64) PRIMARY KEY,
        kind varchar(4) NOT NULL CHECK (kind IN ('bank','mfs')),
        label varchar(80) NOT NULL CHECK (length(btrim(label)) >= 2),
        active boolean NOT NULL DEFAULT true,
        provider varchar(10),
        wallet_number varchar(11),
        bank_name varchar(100),
        account_number varchar(40),
        account_name varchar(100),
        created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
        updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
        revision bigint NOT NULL DEFAULT 0,
        CHECK (
            (kind = 'mfs'
             AND provider IS NOT NULL AND provider IN ('bkash','nagad')
             AND wallet_number IS NOT NULL AND wallet_number ~ '^01[3-9][0-9]{8}$'
             AND bank_name IS NULL AND account_number IS NULL AND account_name IS NULL)
            OR
            (kind = 'bank'
             AND provider IS NULL AND wallet_number IS NULL
             AND bank_name IS NOT NULL AND length(btrim(bank_name)) >= 2
             AND account_name IS NOT NULL AND length(btrim(account_name)) >= 2
             AND account_number IS NOT NULL AND account_number ~ '^[A-Z0-9]{4,40}$')
        )
    );
    CREATE UNIQUE INDEX receiving_account_mfs_unique
        ON nourish.receiving_account(provider,wallet_number) WHERE kind = 'mfs';
    CREATE UNIQUE INDEX receiving_account_bank_unique
        ON nourish.receiving_account(
            lower(regexp_replace(btrim(bank_name),'[[:space:]]+',' ','g')),
            account_number
        ) WHERE kind = 'bank';
    CREATE INDEX receiving_account_list_idx
        ON nourish.receiving_account(active,kind,lower(label),id);

    INSERT INTO nourish.schema_version(version,description)
    VALUES (4,'Phase 4 - customers, suppliers, customer rates and receiving accounts');
END
$phase4$;
