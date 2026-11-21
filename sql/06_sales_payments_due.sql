DO $phase6$
BEGIN
 IF current_database()<>'nourish_poltri_shop' THEN RAISE EXCEPTION 'Connect to nourish_poltri_shop first'; END IF;
 PERFORM pg_advisory_xact_lock(76201901);
 IF NOT EXISTS(SELECT 1 FROM nourish.schema_version WHERE version=5) THEN RAISE EXCEPTION 'Apply Phase 5 first'; END IF;
 IF EXISTS(SELECT 1 FROM nourish.schema_version WHERE version=6) THEN RAISE NOTICE 'Phase 6 already applied'; RETURN; END IF;
 CREATE SEQUENCE nourish.invoice_number_seq;
 CREATE TABLE nourish.invoice (
  id varchar(64) PRIMARY KEY,invoice_number varchar(50) NOT NULL UNIQUE,
  customer_id varchar(64) NOT NULL REFERENCES nourish.customer(id),
  issued_at timestamptz NOT NULL,due_date timestamptz NOT NULL,
  subtotal numeric(19,2) NOT NULL CHECK(subtotal>0 AND subtotal<1000000000000000),
  discount_amount numeric(19,2) NOT NULL DEFAULT 0 CHECK(discount_amount>=0),
  total_amount numeric(19,2) NOT NULL CHECK(total_amount>0 AND total_amount=subtotal-discount_amount),
  notes varchar(500) NOT NULL DEFAULT '',last_note varchar(500) NOT NULL DEFAULT '',
  customer_snapshot jsonb NOT NULL CHECK(jsonb_typeof(customer_snapshot)='object'),
  business_snapshot jsonb NOT NULL CHECK(jsonb_typeof(business_snapshot)='object'),
  actor varchar(64) NOT NULL REFERENCES nourish.app_user(id),created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE(id,customer_id),
  CHECK((due_date AT TIME ZONE 'Asia/Dhaka')::date >= (issued_at AT TIME ZONE 'Asia/Dhaka')::date)
 );
 CREATE INDEX invoice_customer_date_idx ON nourish.invoice(customer_id,issued_at,id);
 CREATE INDEX invoice_date_idx ON nourish.invoice(issued_at DESC,id);
 CREATE INDEX invoice_due_date_idx ON nourish.invoice(due_date,customer_id);
 CREATE TABLE nourish.invoice_item (
  id varchar(64) PRIMARY KEY,invoice_id varchar(64) NOT NULL REFERENCES nourish.invoice(id),
  line_number integer NOT NULL CHECK(line_number BETWEEN 1 AND 100),
  product_id varchar(64) NOT NULL REFERENCES nourish.product(id),product_name varchar(160) NOT NULL,sku varchar(80) NOT NULL,
  category_id varchar(64) NOT NULL REFERENCES nourish.category(id),category_name varchar(80) NOT NULL,
  unit_code varchar(20) NOT NULL REFERENCES nourish.product_unit(code),
  quantity numeric(19,3) NOT NULL CHECK(quantity>0 AND quantity<=1000000000),
  unit_price numeric(25,6) NOT NULL CHECK(unit_price>=0),line_total numeric(19,2) NOT NULL CHECK(line_total>0),
  rate_snapshot jsonb NOT NULL CHECK(jsonb_typeof(rate_snapshot)='array'),
  UNIQUE(invoice_id,product_id),UNIQUE(invoice_id,line_number),UNIQUE(id,product_id),
  CHECK(unit_code<>'piece' OR quantity=trunc(quantity))
 );
 CREATE INDEX invoice_item_product_idx ON nourish.invoice_item(product_id);
 CREATE INDEX invoice_item_category_idx ON nourish.invoice_item(category_id,invoice_id);
 CREATE TABLE nourish.invoice_package_detail (
  id varchar(64) PRIMARY KEY,invoice_item_id varchar(64) NOT NULL,product_id varchar(64) NOT NULL,
  packaging_id varchar(64),size_base numeric(19,3) NOT NULL CHECK(size_base>0),
  package_count bigint NOT NULL CHECK(package_count BETWEEN 1 AND 1000000000),
  selling_per_package numeric(19,2) NOT NULL CHECK(selling_per_package>0),
  line_total numeric(19,2) NOT NULL CHECK(line_total=round(selling_per_package*package_count,2)),
  FOREIGN KEY(invoice_item_id,product_id) REFERENCES nourish.invoice_item(id,product_id),
  FOREIGN KEY(packaging_id,product_id,size_base) REFERENCES nourish.product_packaging(id,product_id,size_base),
  UNIQUE(invoice_item_id,packaging_id)
 );
 CREATE UNIQUE INDEX invoice_piece_detail_unique ON nourish.invoice_package_detail(invoice_item_id) WHERE packaging_id IS NULL;
 CREATE INDEX invoice_package_definition_idx ON nourish.invoice_package_detail(packaging_id,product_id,size_base);
 CREATE TABLE nourish.payment (
  id varchar(64) PRIMARY KEY,invoice_id varchar(64) NOT NULL,customer_id varchar(64) NOT NULL,
  amount numeric(19,2) NOT NULL CHECK(amount>0 AND amount<=1000000000),paid_at timestamptz NOT NULL,
  method varchar(20) NOT NULL CHECK(method IN ('cash','bank','mobile-banking')),
  receiving_account_id varchar(64) REFERENCES nourish.receiving_account(id),payment_provider varchar(10),
  sender_number varchar(11) NOT NULL DEFAULT '' CHECK(sender_number='' OR sender_number ~ '^01[3-9][0-9]{8}$'),
  transaction_ref varchar(80) NOT NULL DEFAULT '',account_snapshot jsonb,note varchar(500) NOT NULL DEFAULT '',
  actor varchar(64) NOT NULL REFERENCES nourish.app_user(id),created_at timestamptz NOT NULL DEFAULT now(),
  FOREIGN KEY(invoice_id,customer_id) REFERENCES nourish.invoice(id,customer_id),
  CHECK((method='cash' AND receiving_account_id IS NULL AND payment_provider IS NULL AND account_snapshot IS NULL AND sender_number='' AND transaction_ref='') OR
   (method IN ('bank','mobile-banking') AND receiving_account_id IS NOT NULL AND account_snapshot IS NOT NULL AND jsonb_typeof(account_snapshot)='object' AND payment_provider IS NOT NULL AND
    ((method='bank' AND payment_provider='bank') OR (method='mobile-banking' AND payment_provider IN ('bkash','nagad')))))
 );
 CREATE INDEX payment_invoice_idx ON nourish.payment(invoice_id,paid_at,id);
 CREATE INDEX payment_customer_idx ON nourish.payment(customer_id,paid_at,id);
 CREATE INDEX payment_account_idx ON nourish.payment(receiving_account_id,paid_at);
 CREATE UNIQUE INDEX payment_reference_unique ON nourish.payment(receiving_account_id,lower(btrim(transaction_ref))) WHERE transaction_ref<>'';
 ALTER TABLE nourish.stock_request DROP CONSTRAINT stock_request_operation_type_check;
 ALTER TABLE nourish.stock_request ADD CHECK(operation_type IN ('purchase','adjustment','invoice','payment'));
 ALTER TABLE nourish.stock_movement DROP CONSTRAINT stock_movement_movement_type_check;
 ALTER TABLE nourish.stock_movement ADD CHECK(movement_type IN ('purchase','sale','adjustment','recount'));

 CREATE FUNCTION nourish.check_payment_insert() RETURNS trigger LANGUAGE plpgsql AS $fn$
 DECLARE inv nourish.invoice%ROWTYPE; paid numeric; acct nourish.receiving_account%ROWTYPE;
 BEGIN
  SELECT * INTO inv FROM nourish.invoice WHERE id=NEW.invoice_id FOR UPDATE;
  IF NOT FOUND OR inv.customer_id<>NEW.customer_id THEN RAISE EXCEPTION 'Payment invoice/customer mismatch' USING ERRCODE='23514'; END IF;
  SELECT coalesce(sum(amount),0) INTO paid FROM nourish.payment WHERE invoice_id=NEW.invoice_id;
  IF paid+NEW.amount>inv.total_amount THEN RAISE EXCEPTION 'Overpayment rejected' USING ERRCODE='23514'; END IF;
  IF NEW.paid_at<inv.issued_at THEN RAISE EXCEPTION 'Payment predates invoice' USING ERRCODE='23514'; END IF;
  IF NEW.method<>'cash' THEN
   SELECT * INTO acct FROM nourish.receiving_account WHERE id=NEW.receiving_account_id FOR UPDATE;
   IF NOT FOUND OR NOT acct.active OR (NEW.method='bank' AND acct.kind<>'bank') OR (NEW.method='mobile-banking' AND (acct.kind<>'mfs' OR acct.provider<>NEW.payment_provider)) THEN
    RAISE EXCEPTION 'Invalid receiving account' USING ERRCODE='23514';
   END IF;
  END IF;
  RETURN NEW;
 END $fn$;
 CREATE TRIGGER payment_insert_check BEFORE INSERT ON nourish.payment FOR EACH ROW EXECUTE FUNCTION nourish.check_payment_insert();

 CREATE FUNCTION nourish.check_invoice_totals() RETURNS trigger LANGUAGE plpgsql AS $fn$
 DECLARE iid varchar(64); item nourish.invoice_item%ROWTYPE; q numeric; total numeric;
 BEGIN
  IF TG_TABLE_NAME='invoice' THEN iid=NEW.id;
  ELSIF TG_TABLE_NAME='invoice_item' THEN iid=NEW.invoice_id;
  ELSE SELECT invoice_id INTO iid FROM nourish.invoice_item WHERE id=NEW.invoice_item_id; END IF;
  IF (SELECT subtotal FROM nourish.invoice WHERE id=iid)<>coalesce((SELECT sum(line_total) FROM nourish.invoice_item WHERE invoice_id=iid),0) THEN RAISE EXCEPTION 'Invoice subtotal mismatch' USING ERRCODE='23514'; END IF;
  FOR item IN SELECT * FROM nourish.invoice_item WHERE invoice_id=iid LOOP
   SELECT coalesce(sum(size_base*package_count),0),coalesce(sum(line_total),0) INTO q,total FROM nourish.invoice_package_detail WHERE invoice_item_id=item.id;
   IF q<>item.quantity OR total<>item.line_total OR item.unit_price<>round(item.line_total/item.quantity,6) THEN RAISE EXCEPTION 'Invoice item/package mismatch' USING ERRCODE='23514'; END IF;
   IF EXISTS(SELECT 1 FROM nourish.invoice_package_detail WHERE invoice_item_id=item.id AND ((item.unit_code='piece' AND (packaging_id IS NOT NULL OR size_base<>1)) OR (item.unit_code<>'piece' AND packaging_id IS NULL))) THEN RAISE EXCEPTION 'Invalid invoice packaging' USING ERRCODE='23514'; END IF;
   IF NOT EXISTS(SELECT 1 FROM nourish.stock_movement WHERE movement_type='sale' AND related_id=iid AND product_id=item.product_id AND quantity_change=-item.quantity) THEN RAISE EXCEPTION 'Sale stock movement missing' USING ERRCODE='23514'; END IF;
   IF EXISTS(SELECT 1 FROM nourish.invoice_package_detail d WHERE d.invoice_item_id=item.id AND d.packaging_id IS NOT NULL AND NOT EXISTS(
    SELECT 1 FROM nourish.stock_movement m JOIN nourish.stock_movement_package p ON p.movement_id=m.id WHERE m.movement_type='sale' AND m.related_id=iid AND m.product_id=item.product_id AND p.packaging_id=d.packaging_id AND p.count_before-p.count_after=d.package_count
   )) THEN RAISE EXCEPTION 'Sale package movement missing' USING ERRCODE='23514'; END IF;
  END LOOP; RETURN NULL;
 END $fn$;
 CREATE CONSTRAINT TRIGGER invoice_totals_check AFTER INSERT ON nourish.invoice DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION nourish.check_invoice_totals();
 CREATE CONSTRAINT TRIGGER invoice_item_totals_check AFTER INSERT ON nourish.invoice_item DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION nourish.check_invoice_totals();
 CREATE CONSTRAINT TRIGGER invoice_package_totals_check AFTER INSERT ON nourish.invoice_package_detail DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION nourish.check_invoice_totals();
 CREATE TRIGGER invoice_immutable BEFORE UPDATE OR DELETE ON nourish.invoice FOR EACH ROW EXECUTE FUNCTION nourish.reject_stock_history_edit();
 CREATE TRIGGER invoice_item_immutable BEFORE UPDATE OR DELETE ON nourish.invoice_item FOR EACH ROW EXECUTE FUNCTION nourish.reject_stock_history_edit();
 CREATE TRIGGER invoice_detail_immutable BEFORE UPDATE OR DELETE ON nourish.invoice_package_detail FOR EACH ROW EXECUTE FUNCTION nourish.reject_stock_history_edit();
 CREATE TRIGGER payment_immutable BEFORE UPDATE OR DELETE ON nourish.payment FOR EACH ROW EXECUTE FUNCTION nourish.reject_stock_history_edit();

 CREATE OR REPLACE FUNCTION nourish.check_stock_movement() RETURNS trigger LANGUAGE plpgsql AS $fn$
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
  ELSIF m.movement_type='sale' THEN
   IF NOT EXISTS(SELECT 1 FROM nourish.invoice_item WHERE invoice_id=m.related_id AND product_id=m.product_id AND quantity=-m.quantity_change) THEN RAISE EXCEPTION 'Invalid sale movement' USING ERRCODE='23514'; END IF;
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

 CREATE VIEW nourish.customer_financial_summary AS
 SELECT c.id AS customer_id,coalesce(i.invoice_count,0) AS invoice_count,
 coalesce(i.billed,0) AS billed_amount,coalesce(p.paid,0) AS paid_amount,
 coalesce(i.billed,0)-coalesce(p.paid,0) AS due_amount,
 CASE WHEN coalesce(i.invoice_count,0)=0 THEN 'no-invoices' WHEN coalesce(i.billed,0)>coalesce(p.paid,0) THEN 'due' ELSE 'paid' END AS bill_category,
 coalesce(categories.items,'[]'::jsonb) AS buying_categories
 FROM nourish.customer c
 LEFT JOIN (SELECT customer_id,count(*) AS invoice_count,sum(total_amount) AS billed FROM nourish.invoice GROUP BY customer_id) i ON i.customer_id=c.id
 LEFT JOIN (SELECT customer_id,sum(amount) AS paid FROM nourish.payment GROUP BY customer_id) p ON p.customer_id=c.id
 LEFT JOIN (SELECT customer_id,jsonb_agg(jsonb_build_object('id',category_id,'name',category_name) ORDER BY category_id) AS items
 FROM (SELECT DISTINCT ON (i.customer_id,d.category_id) i.customer_id,d.category_id,d.category_name FROM nourish.invoice i JOIN nourish.invoice_item d ON d.invoice_id=i.id ORDER BY i.customer_id,d.category_id,i.created_at DESC,i.id DESC) chosen GROUP BY customer_id) categories ON categories.customer_id=c.id;
 CREATE VIEW nourish.customer_ledger AS
 SELECT id AS entry_id,'invoice'::text AS entry_type,0 AS entry_order,id AS invoice_id,invoice_number,customer_id,issued_at AS occurred_at,total_amount AS debit,0::numeric AS credit FROM nourish.invoice
 UNION ALL
 SELECT p.id,'payment'::text,1,p.invoice_id,i.invoice_number,p.customer_id,p.paid_at,0::numeric,p.amount FROM nourish.payment p JOIN nourish.invoice i ON i.id=p.invoice_id;
 INSERT INTO nourish.schema_version(version,description) VALUES(6,'Phase 6 - sales invoices, payments and customer due');
END
$phase6$;

-- Existing and fresh installations both pass through this block.
DO $invoice_rate$
BEGIN
 PERFORM pg_advisory_xact_lock(76201901);
 IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='nourish'
     AND table_name='invoice_package_detail' AND column_name='price_adjustment') THEN
   ALTER TABLE nourish.invoice_package_detail
     DROP CONSTRAINT invoice_package_detail_check,
     ALTER COLUMN selling_per_package TYPE numeric(29,9),
     ADD COLUMN price_adjustment numeric(19,2) NOT NULL DEFAULT 0,
     ADD CONSTRAINT invoice_package_charge_check
       CHECK(line_total >= 0 AND line_total = round(selling_per_package*package_count,2) + price_adjustment);
 END IF;
END
$invoice_rate$;
