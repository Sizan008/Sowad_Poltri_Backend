DO $phase5$
BEGIN
 IF current_database()<>'nourish_poltri_shop' THEN RAISE EXCEPTION 'Connect to nourish_poltri_shop first'; END IF;
 PERFORM pg_advisory_xact_lock(76201901);
 IF NOT EXISTS(SELECT 1 FROM nourish.schema_version WHERE version=4) THEN RAISE EXCEPTION 'Apply Phase 4 first'; END IF;
 IF EXISTS(SELECT 1 FROM nourish.schema_version WHERE version=5) THEN RAISE NOTICE 'Phase 5 already applied'; RETURN; END IF;

 CREATE TABLE nourish.product_price (
  product_id varchar(64) PRIMARY KEY REFERENCES nourish.product(id),
  cost_price numeric(25,6) NOT NULL CHECK(cost_price>=0),
  sale_price numeric(19,4) CHECK(sale_price>0 AND sale_price<=1000000000),
  updated_at timestamptz NOT NULL DEFAULT now()
 );
 ALTER TABLE nourish.product_packaging ADD CONSTRAINT packaging_size_identity UNIQUE(id,product_id,size_base);
 CREATE TABLE nourish.inventory_balance (
  product_id varchar(64) PRIMARY KEY REFERENCES nourish.product(id),
  quantity numeric(19,3) NOT NULL DEFAULT 0 CHECK(quantity BETWEEN 0 AND 1000000000),
  revision bigint NOT NULL DEFAULT 0 CHECK(revision>=0),
  updated_at timestamptz NOT NULL DEFAULT now()
 );
 CREATE TABLE nourish.inventory_package_balance (
  packaging_id varchar(64) PRIMARY KEY,
  product_id varchar(64) NOT NULL REFERENCES nourish.inventory_balance(product_id),
  size_base numeric(19,3) NOT NULL CHECK(size_base>0),
  package_count bigint NOT NULL CHECK(package_count BETWEEN 0 AND 1000000000),
  quantity numeric(25,3) GENERATED ALWAYS AS (size_base*package_count) STORED,
  FOREIGN KEY(packaging_id,product_id,size_base) REFERENCES nourish.product_packaging(id,product_id,size_base)
 );
 CREATE INDEX inventory_package_product_idx ON nourish.inventory_package_balance(product_id);
 CREATE TABLE nourish.purchase (
  id varchar(64) PRIMARY KEY,
  purchase_number varchar(50) NOT NULL UNIQUE,
  supplier_id varchar(64) NOT NULL REFERENCES nourish.supplier(id),
  supplier_name varchar(120) NOT NULL,
  purchased_at timestamptz NOT NULL,
  reference varchar(80) NOT NULL DEFAULT '', notes varchar(500) NOT NULL DEFAULT '',
  total_amount numeric(19,2) NOT NULL CHECK(total_amount>0 AND total_amount<1000000000000000),
  created_at timestamptz NOT NULL DEFAULT now()
 );
 CREATE INDEX purchase_supplier_date_idx ON nourish.purchase(supplier_id,purchased_at DESC,id);
 CREATE INDEX purchase_date_idx ON nourish.purchase(purchased_at DESC,id);
 CREATE TABLE nourish.purchase_item (
  id varchar(64) PRIMARY KEY,
  purchase_id varchar(64) NOT NULL REFERENCES nourish.purchase(id),
  line_number integer NOT NULL CHECK(line_number BETWEEN 1 AND 100),
  product_id varchar(64) NOT NULL REFERENCES nourish.product(id),
  product_name varchar(160) NOT NULL, sku varchar(80) NOT NULL,
  unit_code varchar(20) NOT NULL REFERENCES nourish.product_unit(code),
  quantity numeric(19,3) NOT NULL CHECK(quantity>0 AND quantity<=1000000000),
  unit_price numeric(25,6) NOT NULL CHECK(unit_price>=0),
  line_total numeric(19,2) NOT NULL CHECK(line_total>0),
  pricing_snapshot jsonb NOT NULL CHECK(jsonb_typeof(pricing_snapshot)='object'),
  UNIQUE(purchase_id,product_id), UNIQUE(purchase_id,line_number), UNIQUE(id,product_id),
  CHECK(unit_code<>'piece' OR quantity=trunc(quantity))
 );
 CREATE INDEX purchase_item_product_idx ON nourish.purchase_item(product_id);
 CREATE TABLE nourish.purchase_package_detail (
  id varchar(64) PRIMARY KEY,
  purchase_item_id varchar(64) NOT NULL,
  product_id varchar(64) NOT NULL,
  packaging_id varchar(64),
  size_base numeric(19,3) NOT NULL CHECK(size_base>0),
  package_count bigint NOT NULL CHECK(package_count BETWEEN 1 AND 1000000000),
  purchase_per_package numeric(19,2) NOT NULL CHECK(purchase_per_package>0),
  line_total numeric(19,2) NOT NULL CHECK(line_total=round(purchase_per_package*package_count,2)),
  FOREIGN KEY(purchase_item_id,product_id) REFERENCES nourish.purchase_item(id,product_id),
  FOREIGN KEY(packaging_id,product_id,size_base) REFERENCES nourish.product_packaging(id,product_id,size_base),
  UNIQUE(purchase_item_id,packaging_id)
 );
 CREATE UNIQUE INDEX purchase_piece_detail_unique ON nourish.purchase_package_detail(purchase_item_id) WHERE packaging_id IS NULL;
 CREATE INDEX purchase_package_definition_idx ON nourish.purchase_package_detail(packaging_id,product_id,size_base);
 CREATE TABLE nourish.stock_adjustment (
  id varchar(64) PRIMARY KEY,
  product_id varchar(64) NOT NULL REFERENCES nourish.product(id),
  mode varchar(10) NOT NULL CHECK(mode IN ('delta','recount')),
  reason varchar(500) NOT NULL CHECK(length(btrim(reason))>=3),
  expected_revision bigint CHECK(expected_revision>=0),
  created_at timestamptz NOT NULL DEFAULT now(),
  CHECK(mode<>'recount' OR expected_revision IS NOT NULL)
 );
 CREATE INDEX stock_adjustment_product_idx ON nourish.stock_adjustment(product_id,created_at DESC);
 CREATE TABLE nourish.stock_movement (
  id varchar(64) PRIMARY KEY,
  product_id varchar(64) NOT NULL REFERENCES nourish.product(id),
  movement_type varchar(12) NOT NULL CHECK(movement_type IN ('purchase','adjustment','recount')),
  related_id varchar(64) NOT NULL,
  quantity_change numeric(19,3) NOT NULL,
  stock_before numeric(19,3) NOT NULL CHECK(stock_before>=0),
  stock_after numeric(19,3) NOT NULL CHECK(stock_after BETWEEN 0 AND 1000000000),
  stock_revision bigint NOT NULL CHECK(stock_revision>0),
  occurred_at timestamptz NOT NULL, created_at timestamptz NOT NULL DEFAULT now(),
  reason varchar(500) NOT NULL DEFAULT '',actor varchar(64) NOT NULL REFERENCES nourish.app_user(id),
  CHECK(stock_after=stock_before+quantity_change),
  UNIQUE(product_id,stock_revision),UNIQUE(movement_type,related_id,product_id),UNIQUE(id,product_id)
 );
 CREATE INDEX stock_movement_history_idx ON nourish.stock_movement(product_id,created_at DESC,stock_revision DESC);
 CREATE INDEX stock_movement_recent_idx ON nourish.stock_movement(created_at DESC);
 CREATE TABLE nourish.stock_movement_package (
  movement_id varchar(64) NOT NULL,
  packaging_id varchar(64) NOT NULL,
  product_id varchar(64) NOT NULL,
  size_base numeric(19,3) NOT NULL,
  count_before bigint NOT NULL CHECK(count_before BETWEEN 0 AND 1000000000),
  count_after bigint NOT NULL CHECK(count_after BETWEEN 0 AND 1000000000),
  PRIMARY KEY(movement_id,packaging_id),
  FOREIGN KEY(movement_id,product_id) REFERENCES nourish.stock_movement(id,product_id),
  FOREIGN KEY(packaging_id,product_id,size_base) REFERENCES nourish.product_packaging(id,product_id,size_base)
 );
 CREATE INDEX stock_movement_package_definition_idx ON nourish.stock_movement_package(packaging_id,product_id,size_base);
 CREATE TABLE nourish.stock_request (
  request_key varchar(80) PRIMARY KEY,
  operation_type varchar(12) NOT NULL CHECK(operation_type IN ('purchase','adjustment')),
  body_hash char(64) NOT NULL,result_id varchar(64) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
 );

 -- Deferred checks see the completed transaction, not intermediate writes.
 CREATE FUNCTION nourish.check_inventory_consistency() RETURNS trigger LANGUAGE plpgsql AS $fn$
 DECLARE pid varchar(64); q numeric; k text; rev bigint; lastq numeric; lastrev bigint; packq numeric;
 BEGIN
  IF TG_OP='DELETE' THEN pid=OLD.product_id; ELSE pid=NEW.product_id; END IF;
  SELECT p.kind,b.quantity,b.revision INTO k,q,rev FROM nourish.product p LEFT JOIN nourish.inventory_balance b ON b.product_id=p.id WHERE p.id=pid;
  IF q IS NULL THEN RAISE EXCEPTION 'Missing inventory balance' USING ERRCODE='23514'; END IF;
  SELECT coalesce(sum(quantity),0) INTO packq FROM nourish.inventory_package_balance WHERE product_id=pid;
  IF (k IN ('feed','medicine') AND q<>packq) OR (k IN ('chick','fish') AND (q<>trunc(q) OR packq<>0)) THEN
   RAISE EXCEPTION 'Package/piece stock quantity mismatch' USING ERRCODE='23514';
  END IF;
  IF EXISTS(
   SELECT 1 FROM nourish.product_packaging d LEFT JOIN nourish.inventory_package_balance b ON b.packaging_id=d.id
   WHERE d.product_id=pid AND coalesce(b.package_count,0)<>coalesce((
    SELECT mp.count_after FROM nourish.stock_movement_package mp JOIN nourish.stock_movement m ON m.id=mp.movement_id
    WHERE mp.packaging_id=d.id ORDER BY m.stock_revision DESC LIMIT 1),0)
  ) THEN RAISE EXCEPTION 'Package balance differs from ledger' USING ERRCODE='23514'; END IF;
  SELECT stock_after,stock_revision INTO lastq,lastrev FROM nourish.stock_movement WHERE product_id=pid ORDER BY stock_revision DESC LIMIT 1;
  IF q<>coalesce(lastq,0) OR rev<>coalesce(lastrev,0) THEN RAISE EXCEPTION 'Stock ledger mismatch' USING ERRCODE='23514'; END IF;
  RETURN NULL;
 END $fn$;
 CREATE CONSTRAINT TRIGGER inventory_balance_check AFTER INSERT OR UPDATE OR DELETE ON nourish.inventory_balance DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION nourish.check_inventory_consistency();
 CREATE CONSTRAINT TRIGGER inventory_package_check AFTER INSERT OR UPDATE OR DELETE ON nourish.inventory_package_balance DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION nourish.check_inventory_consistency();
 CREATE CONSTRAINT TRIGGER inventory_movement_check AFTER INSERT ON nourish.stock_movement DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION nourish.check_inventory_consistency();

 CREATE FUNCTION nourish.check_stock_movement() RETURNS trigger LANGUAGE plpgsql AS $fn$
 DECLARE m nourish.stock_movement%ROWTYPE; prev numeric; k text; d numeric; before_count bigint;
 BEGIN
  IF TG_TABLE_NAME='stock_movement' THEN m=NEW; ELSE SELECT * INTO m FROM nourish.stock_movement WHERE id=NEW.movement_id; END IF;
  SELECT kind INTO k FROM nourish.product WHERE id=m.product_id;
  IF m.stock_revision=1 THEN prev=0; ELSE SELECT stock_after INTO prev FROM nourish.stock_movement WHERE product_id=m.product_id AND stock_revision=m.stock_revision-1; END IF;
  IF prev IS NULL OR prev<>m.stock_before THEN RAISE EXCEPTION 'Broken stock movement chain' USING ERRCODE='23514'; END IF;
  IF k IN ('feed','medicine') THEN
   SELECT coalesce(sum(size_base*(count_after-count_before)),0) INTO d FROM nourish.stock_movement_package WHERE movement_id=m.id;
   IF d<>m.quantity_change THEN RAISE EXCEPTION 'Package movement mismatch' USING ERRCODE='23514'; END IF;
  END IF;
  IF m.movement_type='purchase' THEN
   IF NOT EXISTS(SELECT 1 FROM nourish.purchase_item WHERE purchase_id=m.related_id AND product_id=m.product_id AND quantity=m.quantity_change) THEN RAISE EXCEPTION 'Invalid purchase movement' USING ERRCODE='23514'; END IF;
  ELSE
   IF NOT EXISTS(SELECT 1 FROM nourish.stock_adjustment WHERE id=m.related_id AND product_id=m.product_id AND (mode='recount')=(m.movement_type='recount')) THEN RAISE EXCEPTION 'Invalid adjustment movement' USING ERRCODE='23514'; END IF;
  END IF;
  IF TG_TABLE_NAME='stock_movement_package' THEN
   SELECT d.count_after INTO before_count FROM nourish.stock_movement_package d JOIN nourish.stock_movement h ON h.id=d.movement_id WHERE d.packaging_id=NEW.packaging_id AND h.stock_revision<m.stock_revision ORDER BY h.stock_revision DESC LIMIT 1;
   IF NEW.count_before<>coalesce(before_count,0) THEN RAISE EXCEPTION 'Broken package movement chain' USING ERRCODE='23514'; END IF;
   SELECT d.count_after INTO before_count FROM nourish.stock_movement_package d JOIN nourish.stock_movement h ON h.id=d.movement_id WHERE d.packaging_id=NEW.packaging_id ORDER BY h.stock_revision DESC LIMIT 1;
   IF before_count<>(SELECT package_count FROM nourish.inventory_package_balance WHERE packaging_id=NEW.packaging_id) THEN RAISE EXCEPTION 'Package ledger balance mismatch' USING ERRCODE='23514'; END IF;
  END IF;
  RETURN NULL;
 END $fn$;
 CREATE CONSTRAINT TRIGGER stock_movement_chain_check AFTER INSERT ON nourish.stock_movement DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION nourish.check_stock_movement();
 CREATE CONSTRAINT TRIGGER stock_package_chain_check AFTER INSERT ON nourish.stock_movement_package DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION nourish.check_stock_movement();

 CREATE FUNCTION nourish.check_purchase_totals() RETURNS trigger LANGUAGE plpgsql AS $fn$
 DECLARE pid varchar(64); item nourish.purchase_item%ROWTYPE; q numeric; total numeric;
 BEGIN
  IF TG_TABLE_NAME='purchase' THEN pid=NEW.id;
  ELSIF TG_TABLE_NAME='purchase_item' THEN pid=NEW.purchase_id;
  ELSE SELECT purchase_id INTO pid FROM nourish.purchase_item WHERE id=NEW.purchase_item_id; END IF;
  IF (SELECT total_amount FROM nourish.purchase WHERE id=pid)<>coalesce((SELECT sum(line_total) FROM nourish.purchase_item WHERE purchase_id=pid),0) THEN RAISE EXCEPTION 'Purchase total mismatch' USING ERRCODE='23514'; END IF;
  FOR item IN SELECT * FROM nourish.purchase_item WHERE purchase_id=pid LOOP
   SELECT coalesce(sum(size_base*package_count),0),coalesce(sum(line_total),0) INTO q,total FROM nourish.purchase_package_detail WHERE purchase_item_id=item.id;
   IF q<>item.quantity OR total<>item.line_total OR item.unit_price<>round(item.line_total/item.quantity,6) THEN RAISE EXCEPTION 'Purchase item/package mismatch' USING ERRCODE='23514'; END IF;
   IF EXISTS(SELECT 1 FROM nourish.purchase_package_detail WHERE purchase_item_id=item.id AND ((item.unit_code='piece' AND (packaging_id IS NOT NULL OR size_base<>1)) OR (item.unit_code<>'piece' AND packaging_id IS NULL))) THEN RAISE EXCEPTION 'Invalid purchase packaging' USING ERRCODE='23514'; END IF;
   IF NOT EXISTS(SELECT 1 FROM nourish.stock_movement WHERE movement_type='purchase' AND related_id=pid AND product_id=item.product_id AND quantity_change=item.quantity) THEN RAISE EXCEPTION 'Purchase stock movement missing' USING ERRCODE='23514'; END IF;
  END LOOP;
  RETURN NULL;
 END $fn$;
 CREATE CONSTRAINT TRIGGER purchase_totals_check AFTER INSERT ON nourish.purchase DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION nourish.check_purchase_totals();
 CREATE CONSTRAINT TRIGGER purchase_item_totals_check AFTER INSERT ON nourish.purchase_item DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION nourish.check_purchase_totals();
 CREATE CONSTRAINT TRIGGER purchase_package_totals_check AFTER INSERT ON nourish.purchase_package_detail DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION nourish.check_purchase_totals();

 CREATE FUNCTION nourish.reject_stock_history_edit() RETURNS trigger LANGUAGE plpgsql AS $fn$
 BEGIN RAISE EXCEPTION 'Posted purchase/stock history is immutable; use a new stock adjustment' USING ERRCODE='23514'; END $fn$;
 CREATE TRIGGER purchase_immutable BEFORE UPDATE OR DELETE ON nourish.purchase FOR EACH ROW EXECUTE FUNCTION nourish.reject_stock_history_edit();
 CREATE TRIGGER purchase_item_immutable BEFORE UPDATE OR DELETE ON nourish.purchase_item FOR EACH ROW EXECUTE FUNCTION nourish.reject_stock_history_edit();
 CREATE TRIGGER purchase_detail_immutable BEFORE UPDATE OR DELETE ON nourish.purchase_package_detail FOR EACH ROW EXECUTE FUNCTION nourish.reject_stock_history_edit();
 CREATE TRIGGER movement_immutable BEFORE UPDATE OR DELETE ON nourish.stock_movement FOR EACH ROW EXECUTE FUNCTION nourish.reject_stock_history_edit();
 CREATE TRIGGER movement_detail_immutable BEFORE UPDATE OR DELETE ON nourish.stock_movement_package FOR EACH ROW EXECUTE FUNCTION nourish.reject_stock_history_edit();
 CREATE TRIGGER adjustment_immutable BEFORE UPDATE OR DELETE ON nourish.stock_adjustment FOR EACH ROW EXECUTE FUNCTION nourish.reject_stock_history_edit();
 CREATE TRIGGER stock_request_immutable BEFORE UPDATE OR DELETE ON nourish.stock_request FOR EACH ROW EXECUTE FUNCTION nourish.reject_stock_history_edit();
 INSERT INTO nourish.schema_version(version,description) VALUES(5,'Phase 5 - purchase and inventory');
END
$phase5$;
