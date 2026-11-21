DO $phase8$
BEGIN
 IF current_database()<>'nourish_poltri_shop' THEN RAISE EXCEPTION 'Connect to nourish_poltri_shop first'; END IF;
 PERFORM pg_advisory_xact_lock(76201901);
 IF NOT EXISTS(SELECT 1 FROM nourish.schema_version WHERE version=7) THEN RAISE EXCEPTION 'Apply Phase 7 first'; END IF;
 IF EXISTS(SELECT 1 FROM nourish.schema_version WHERE version=8) THEN RAISE NOTICE 'Phase 8 already applied'; RETURN; END IF;
 CREATE TABLE nourish.migration_batch (
  source_sha256 varchar(64) PRIMARY KEY CHECK(source_sha256 ~ '^[0-9a-f]{64}$'),
  source_name text NOT NULL, report jsonb NOT NULL CHECK(jsonb_typeof(report)='object'),
  staged_at timestamptz NOT NULL DEFAULT now()
 );
 CREATE TABLE nourish.migration_record (
  source_sha256 varchar(64) NOT NULL REFERENCES nourish.migration_batch,
  collection varchar(80) NOT NULL, ordinal integer NOT NULL CHECK(ordinal>=0),
  legacy_id text, payload jsonb NOT NULL, issues jsonb NOT NULL CHECK(jsonb_typeof(issues)='array'),
  PRIMARY KEY(source_sha256,collection,ordinal)
 );
 CREATE INDEX migration_legacy_idx ON nourish.migration_record(source_sha256,collection,legacy_id);
 CREATE TABLE nourish.migration_mapping (
  source_sha256 varchar(64) NOT NULL, collection varchar(80) NOT NULL, ordinal integer NOT NULL,
  target_id varchar(64) NOT NULL, approved_command jsonb NOT NULL,
  actor varchar(64) NOT NULL REFERENCES nourish.app_user, imported_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY(source_sha256,collection,ordinal),
  UNIQUE(source_sha256,collection,target_id),
  FOREIGN KEY(source_sha256,collection,ordinal) REFERENCES nourish.migration_record
 );
 CREATE FUNCTION nourish.migration_immutable() RETURNS trigger LANGUAGE plpgsql AS $fn$
 BEGIN RAISE EXCEPTION 'Migration provenance is immutable' USING ERRCODE='23514'; END $fn$;
 CREATE TRIGGER migration_batch_immutable BEFORE UPDATE OR DELETE ON nourish.migration_batch FOR EACH ROW EXECUTE FUNCTION nourish.migration_immutable();
 CREATE TRIGGER migration_record_immutable BEFORE UPDATE OR DELETE ON nourish.migration_record FOR EACH ROW EXECUTE FUNCTION nourish.migration_immutable();
 CREATE TRIGGER migration_mapping_immutable BEFORE UPDATE OR DELETE ON nourish.migration_mapping FOR EACH ROW EXECUTE FUNCTION nourish.migration_immutable();
 CREATE VIEW nourish.reconciliation_issue AS
 SELECT 'stock-ledger'::text AS check_name,p.id AS entity_id,coalesce(b.quantity,0) AS actual,coalesce(m.quantity,0) AS expected
 FROM nourish.product p LEFT JOIN nourish.inventory_balance b ON b.product_id=p.id
 LEFT JOIN (SELECT product_id,sum(quantity_change) quantity FROM nourish.stock_movement GROUP BY product_id) m ON m.product_id=p.id
 WHERE coalesce(b.quantity,0)<>coalesce(m.quantity,0)
 UNION ALL
 SELECT 'stock-packages',p.id,coalesce(b.quantity,0),coalesce(pk.quantity,0)
 FROM nourish.product p LEFT JOIN nourish.inventory_balance b ON b.product_id=p.id
 LEFT JOIN (SELECT product_id,sum(quantity) quantity FROM nourish.inventory_package_balance GROUP BY product_id) pk ON pk.product_id=p.id
 WHERE p.kind IN ('feed','medicine') AND coalesce(b.quantity,0)<>coalesce(pk.quantity,0)
 UNION ALL
 SELECT 'invoice-items',i.id,i.subtotal,coalesce(it.total,0) FROM nourish.invoice i
 LEFT JOIN (SELECT invoice_id,sum(line_total) total FROM nourish.invoice_item GROUP BY invoice_id) it ON it.invoice_id=i.id
 WHERE i.subtotal<>coalesce(it.total,0)
 UNION ALL
 SELECT 'invoice-total',id,total_amount,subtotal-discount_amount FROM nourish.invoice WHERE total_amount<>subtotal-discount_amount
 UNION ALL
 SELECT 'purchase-items',p.id,p.total_amount,coalesce(it.total,0) FROM nourish.purchase p
 LEFT JOIN (SELECT purchase_id,sum(line_total) total FROM nourish.purchase_item GROUP BY purchase_id) it ON it.purchase_id=p.id
 WHERE p.total_amount<>coalesce(it.total,0)
 UNION ALL
 SELECT 'overpayment',i.id,p.paid,i.total_amount FROM nourish.invoice i
 JOIN (SELECT invoice_id,sum(amount) paid FROM nourish.payment GROUP BY invoice_id) p ON p.invoice_id=i.id WHERE p.paid>i.total_amount
 UNION ALL
 SELECT 'payment-customer',p.id,1,0 FROM nourish.payment p JOIN nourish.invoice i ON i.id=p.invoice_id WHERE p.customer_id<>i.customer_id
 UNION ALL
 SELECT 'customer-due',s.customer_id,s.due_amount,coalesce(l.balance,0) FROM nourish.customer_financial_summary s
 LEFT JOIN (SELECT customer_id,sum(debit-credit) balance FROM nourish.customer_ledger GROUP BY customer_id) l ON l.customer_id=s.customer_id
 WHERE s.due_amount<>coalesce(l.balance,0);
 INSERT INTO nourish.schema_version(version,description) VALUES(8,'Phase 8 - migration, integration and verification');
END $phase8$;
-- Expected: no rows. Does not repair or rewrite transaction history.
SELECT * FROM nourish.reconciliation_issue ORDER BY check_name,entity_id;
SELECT version,description FROM nourish.schema_version ORDER BY version;
