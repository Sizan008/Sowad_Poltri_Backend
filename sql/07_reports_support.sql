DO $phase7$
BEGIN
 IF current_database()<>'nourish_poltri_shop' THEN RAISE EXCEPTION 'Connect to nourish_poltri_shop first'; END IF;
 PERFORM pg_advisory_xact_lock(76201901);
 IF NOT EXISTS(SELECT 1 FROM nourish.schema_version WHERE version=6) THEN RAISE EXCEPTION 'Apply Phase 6 first'; END IF;
 IF EXISTS(SELECT 1 FROM nourish.schema_version WHERE version=7) THEN RAISE NOTICE 'Phase 7 already applied'; RETURN; END IF;
 CREATE TABLE nourish.expense (
  id varchar(64) PRIMARY KEY,title varchar(120) NOT NULL CHECK(length(btrim(title))>=3),
  category varchar(20) NOT NULL CHECK(category IN ('rent','utilities','salary','transport','maintenance','supplies','other')),
  method varchar(20) NOT NULL CHECK(method IN ('cash','bank','mobile-banking')),
  amount numeric(19,2) NOT NULL CHECK(amount>0 AND amount<=1000000000),spent_at timestamptz NOT NULL,
  reference varchar(80) NOT NULL DEFAULT '',note varchar(500) NOT NULL DEFAULT '',
  deleted boolean NOT NULL DEFAULT false,revision bigint NOT NULL DEFAULT 0 CHECK(revision>=0),
  created_by varchar(64) NOT NULL REFERENCES nourish.app_user(id),updated_by varchar(64) NOT NULL REFERENCES nourish.app_user(id),
  created_at timestamptz NOT NULL DEFAULT now(),updated_at timestamptz NOT NULL DEFAULT now()
 );
 CREATE INDEX expense_date_idx ON nourish.expense(spent_at DESC,id DESC) WHERE NOT deleted;
 CREATE INDEX expense_category_date_idx ON nourish.expense(category,spent_at DESC,id) WHERE NOT deleted;
 CREATE INDEX expense_method_date_idx ON nourish.expense(method,spent_at DESC,id) WHERE NOT deleted;
 CREATE TABLE nourish.expense_history (
  expense_id varchar(64) NOT NULL REFERENCES nourish.expense(id),revision bigint NOT NULL CHECK(revision>=0),
  action varchar(10) NOT NULL CHECK(action IN ('CREATE','UPDATE','DELETE')),
  actor varchar(64) NOT NULL REFERENCES nourish.app_user(id),snapshot jsonb NOT NULL CHECK(jsonb_typeof(snapshot)='object'),
  occurred_at timestamptz NOT NULL DEFAULT now(),PRIMARY KEY(expense_id,revision)
 );
 CREATE FUNCTION nourish.reject_expense_history_edit() RETURNS trigger LANGUAGE plpgsql AS $fn$
 BEGIN RAISE EXCEPTION 'Expense revision history is immutable' USING ERRCODE='23514'; END $fn$;
 CREATE TRIGGER expense_history_immutable BEFORE UPDATE OR DELETE ON nourish.expense_history FOR EACH ROW EXECUTE FUNCTION nourish.reject_expense_history_edit();
 CREATE FUNCTION nourish.check_expense_history() RETURNS trigger LANGUAGE plpgsql AS $fn$
 BEGIN
  IF NOT EXISTS(SELECT 1 FROM nourish.expense_history h WHERE h.expense_id=NEW.id AND h.revision=NEW.revision AND h.snapshot=to_jsonb(NEW)) THEN
   RAISE EXCEPTION 'Expense revision snapshot missing' USING ERRCODE='23514';
  END IF; RETURN NULL;
 END $fn$;
 CREATE CONSTRAINT TRIGGER expense_history_required AFTER INSERT OR UPDATE ON nourish.expense DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION nourish.check_expense_history();
 CREATE SEQUENCE nourish.notebook_serial_seq;
 CREATE TABLE nourish.notebook (
  id varchar(64) PRIMARY KEY,serial bigint NOT NULL DEFAULT nextval('nourish.notebook_serial_seq') UNIQUE,
  title varchar(160) NOT NULL CHECK(length(btrim(title))>=2),content text NOT NULL CHECK(length(btrim(content)) BETWEEN 1 AND 10000),
  author_id varchar(64) NOT NULL REFERENCES nourish.app_user(id),author_name varchar(120) NOT NULL,
  updated_by varchar(64) NOT NULL REFERENCES nourish.app_user(id),deleted boolean NOT NULL DEFAULT false,
  revision bigint NOT NULL DEFAULT 0 CHECK(revision>=0),
  created_at timestamptz NOT NULL DEFAULT now(),updated_at timestamptz NOT NULL DEFAULT now()
 );
 ALTER SEQUENCE nourish.notebook_serial_seq OWNED BY nourish.notebook.serial;
 CREATE INDEX notebook_page_idx ON nourish.notebook(serial DESC) WHERE NOT deleted;
 CREATE INDEX notebook_author_idx ON nourish.notebook(author_id,serial DESC) WHERE NOT deleted;
 ALTER TABLE nourish.stock_request DROP CONSTRAINT stock_request_operation_type_check;
 ALTER TABLE nourish.stock_request ADD CHECK(operation_type IN ('purchase','adjustment','invoice','payment','expense'));
 CREATE VIEW nourish.report_invoice_balance AS
 SELECT i.*,coalesce(p.amount,0) AS paid_amount,i.total_amount-coalesce(p.amount,0) AS due_amount
 FROM nourish.invoice i LEFT JOIN (SELECT invoice_id,sum(amount) AS amount FROM nourish.payment GROUP BY invoice_id) p ON p.invoice_id=i.id;
 CREATE INDEX stock_movement_business_date_idx ON nourish.stock_movement(occurred_at,product_id);
 CREATE INDEX payment_date_report_idx ON nourish.payment(paid_at,invoice_id);
 CREATE INDEX customer_created_report_idx ON nourish.customer(created_at);
 CREATE INDEX activity_actor_date_idx ON nourish.activity_log(actor_id,occurred_at DESC,id DESC);
 CREATE INDEX activity_entity_action_date_idx ON nourish.activity_log(entity,action,occurred_at DESC,id DESC);
 CREATE INDEX activity_entity_id_date_idx ON nourish.activity_log(entity_id,occurred_at DESC,id DESC);
 CREATE INDEX activity_role_date_idx ON nourish.activity_log(role,occurred_at DESC,id DESC);
 CREATE INDEX activity_text_search_idx ON nourish.activity_log USING gin(
  to_tsvector('simple',coalesce(actor_name,'')||' '||coalesce(details,'')||' '||coalesce(entity,'')||' '||coalesce(action,'')||' '||coalesce(entity_id,''))
 );
 INSERT INTO nourish.schema_version(version,description) VALUES(7,'Phase 7 - dashboard, reports, expenses and notebook');
END
$phase7$;
