DO $phase3$
BEGIN
 IF current_database() <> 'nourish_poltri_shop' THEN RAISE EXCEPTION 'Connect to nourish_poltri_shop first'; END IF;
 PERFORM pg_advisory_xact_lock(76201901);
 IF NOT EXISTS (SELECT 1 FROM nourish.schema_version WHERE version=2) THEN RAISE EXCEPTION 'Phase 2 required'; END IF;
 IF EXISTS (SELECT 1 FROM nourish.schema_version WHERE version=3) THEN RAISE NOTICE 'Phase 3 already applied'; RETURN; END IF;
 CREATE TABLE nourish.category (
  id varchar(64) PRIMARY KEY, name varchar(80) NOT NULL, kind varchar(20) NOT NULL UNIQUE,
  description varchar(200) NOT NULL DEFAULT '', sort_order integer NOT NULL,
  UNIQUE(id,kind), CHECK(kind IN ('feed','medicine','chick','fish'))
 );
 INSERT INTO nourish.category(id,name,kind,sort_order) VALUES
 ('cat-feed','Feed','feed',1),('cat-medicine','Medicine','medicine',2),('cat-chick','Chicks','chick',3),('cat-fish','Fish','fish',4);
 CREATE TABLE nourish.product_unit (
  id varchar(64) PRIMARY KEY, code varchar(20) NOT NULL UNIQUE CHECK(code ~ '^[a-z][a-z0-9_-]{0,19}$'),
  name varchar(80) NOT NULL CHECK(length(trim(name))>0), symbol varchar(20) NOT NULL CHECK(length(trim(symbol))>0),
  allow_decimal boolean NOT NULL, system_unit boolean NOT NULL DEFAULT false, revision bigint NOT NULL DEFAULT 0
 );
 INSERT INTO nourish.product_unit(id,code,name,symbol,allow_decimal,system_unit) VALUES
 ('unit-kg','kg','Kilogram','kg',true,true),('unit-g','g','Gram','g',true,true),
 ('unit-ml','ml','Millilitre','ml',true,true),('unit-l','l','Litre','L',true,true),
 ('unit-piece','piece','Piece','pc',false,true),('unit-bag','bag','Bag','bag',false,true),('unit-bottle','bottle','Bottle','bottle',false,true);
 CREATE TABLE nourish.feed_price_catalog (
  id varchar(64) PRIMARY KEY, product_code varchar(30) NOT NULL UNIQUE, name varchar(120) NOT NULL,
  feed_group varchar(100) NOT NULL, feed_type varchar(70) NOT NULL,
  bag_size_kg numeric(12,3) NOT NULL CHECK(bag_size_kg>0),
  agent_price_per_kg numeric(19,6) NOT NULL CHECK(agent_price_per_kg>=0),
  mrp_per_kg numeric(19,6) NOT NULL CHECK(mrp_per_kg>=0), mrp_per_bag numeric(19,2) NOT NULL CHECK(mrp_per_bag>=0),
  effective_date date NOT NULL, source varchar(250) NOT NULL
 );
 CREATE TABLE nourish.product (
  id varchar(64) PRIMARY KEY, name varchar(120) NOT NULL CHECK(length(trim(name))>=2),
  sku varchar(30) NOT NULL UNIQUE CHECK(sku ~ '^[A-Z0-9][A-Z0-9_-]{1,29}$'),
  category_id varchar(64) NOT NULL, kind varchar(20) NOT NULL,
  unit_code varchar(20) NOT NULL REFERENCES nourish.product_unit(code),
  subtype varchar(70) NOT NULL DEFAULT '', grade varchar(8), manufacturer varchar(120) NOT NULL DEFAULT '',
  medicine_form varchar(20), feed_group varchar(100) NOT NULL DEFAULT '', feed_type varchar(70) NOT NULL DEFAULT '',
  feed_catalog_id varchar(64) UNIQUE REFERENCES nourish.feed_price_catalog(id),
  archived boolean NOT NULL DEFAULT false, low_stock_threshold numeric(10,3) NOT NULL DEFAULT 0 CHECK(low_stock_threshold BETWEEN 0 AND 1000000),
  revision bigint NOT NULL DEFAULT 0, FOREIGN KEY(category_id,kind) REFERENCES nourish.category(id,kind),
  CHECK(feed_catalog_id IS NULL OR (kind='feed' AND NOT archived)),
  CHECK((kind='feed' AND unit_code='kg' AND length(trim(feed_group))>0 AND length(trim(feed_type))>0 AND grade IS NULL AND medicine_form IS NULL)
   OR (kind='medicine' AND length(trim(manufacturer))>=2 AND grade IS NULL AND medicine_form IS NOT NULL AND ((medicine_form='powder' AND unit_code='g') OR (medicine_form='liquid' AND unit_code='ml')))
   OR (kind='chick' AND unit_code='piece' AND subtype IN ('broiler','sonali','colorbird','layer') AND grade IS NOT NULL AND grade IN ('A','B+') AND medicine_form IS NULL)
   OR (kind='fish' AND unit_code='piece' AND subtype IN ('tilapia','sarputi') AND grade IS NULL AND medicine_form IS NULL))
 );
 CREATE INDEX product_category_idx ON nourish.product(category_id,archived);
 CREATE INDEX product_feed_cascade_idx ON nourish.product(feed_group,feed_type) WHERE kind='feed' AND NOT archived;
 CREATE INDEX product_medicine_cascade_idx ON nourish.product(name,manufacturer,medicine_form) WHERE kind='medicine' AND NOT archived;
 CREATE TABLE nourish.product_packaging (
  id varchar(64) PRIMARY KEY, product_id varchar(64) NOT NULL REFERENCES nourish.product(id),
  amount numeric(10,3) NOT NULL CHECK(amount BETWEEN 0.001 AND 1000000),
  unit_code varchar(20) NOT NULL REFERENCES nourish.product_unit(code),
  size_base numeric(15,3) NOT NULL CHECK(size_base>0), package_type varchar(10) NOT NULL CHECK(package_type IN ('bag','packet','bottle','piece')),
  active boolean NOT NULL DEFAULT true, revision bigint NOT NULL DEFAULT 0,
  UNIQUE(product_id,size_base),
  CHECK((unit_code IN ('g','ml','piece') AND size_base=amount) OR (unit_code='l' AND size_base=amount*1000) OR (unit_code='kg' AND (size_base=amount OR size_base=amount*1000)))
 );
 CREATE TABLE nourish.catalog_options(id varchar(30) PRIMARY KEY CHECK(id='product-types'),options jsonb NOT NULL CHECK(jsonb_typeof(options)='object'));
 INSERT INTO nourish.catalog_options VALUES ('product-types','{"id":"product-types","feedBagSizesKg":[1,5,10,20,25,40,50],"feedTypes":["starter","grower","finisher","layer","other","Crumble","Pellet","Mash","Powder","Granular","2.6 mm","3.5 mm","4 mm","5 mm","3 mm","Fine Crumble","1.8 mm","2.2 mm","0.5 mm","1.0 mm","1.5 mm","2.0 mm","3.0 mm","5.0 mm","4.0 mm","Fine Powder","0.4 mm","0.8 mm","1.2 mm"],"chickTypes":["broiler","sonali","colorbird","layer"],"fishTypes":["sarputi","tilapia"],"medicineForms":["powder","liquid"],"medicineTypes":["vitamin","antibiotic","vaccine","supplement","other"],"chickGrades":["A","B+"]}'::jsonb);
 INSERT INTO nourish.feed_price_catalog(id,product_code,name,feed_group,feed_type,bag_size_kg,agent_price_per_kg,mrp_per_kg,mrp_per_bag,effective_date,source) VALUES
 ('nourish-3103','3103','Broiler Pre-Starter','Broiler Feed','Crumble',50,71.23,75.64,3782,'2026-09-23','User-supplied NOURISH reference list; values not independently verified'),
 ('nourish-3102','3102','Broiler Starter','Broiler Feed','Crumble',50,71.23,75.64,3782,'2026-09-23','User-supplied NOURISH reference list; values not independently verified'),
 ('nourish-3108','3108','Broiler Grower','Broiler Feed','Pellet',50,71.23,75.64,3782,'2026-09-23','User-supplied NOURISH reference list; values not independently verified'),
 ('nourish-3101','3101','Broiler Finisher','Broiler Feed','Pellet',50,70.29,74.64,3732,'2026-09-23','User-supplied NOURISH reference list; values not independently verified'),
 ('nourish-3109','3109','Sales Center Feed','Broiler Feed','Pellet',25,54.47,57.84,1446,'2026-09-23','User-supplied NOURISH reference list; values not independently verified'),
 ('nourish-3201','3201','Layer Starter','Layer Feed','Crumble',50,63.38,66.77,3338.5,'2026-09-23','User-supplied NOURISH reference list; values not independently verified'),
 ('nourish-3202','3202','Layer Grower','Layer Feed','Crumble',50,60.53,63.77,3188.5,'2026-09-23','User-supplied NOURISH reference list; values not independently verified'),
 ('nourish-3203','3203','Pre-Layer','Layer Feed','Mash',50,60.34,63.57,3178.5,'2026-09-23','User-supplied NOURISH reference list; values not independently verified'),
 ('nourish-3204','3204','Layer Layer - 1','Layer Feed','Mash',50,56.66,59.77,2988.5,'2026-09-23','User-supplied NOURISH reference list; values not independently verified'),
 ('nourish-3205','3205','Layer Layer - 2','Layer Feed','Mash',50,56.18,59.27,2963.5,'2026-09-23','User-supplied NOURISH reference list; values not independently verified'),
 ('nourish-3208','3208','Layer Layer Lite','Layer Feed','Mash',50,54.76,57.75,2887.5,'2026-09-23','User-supplied NOURISH reference list; values not independently verified'),
 ('nourish-3209','3209','Layer Layer Concentrated','Layer Feed','Mash',50,62,66,3300,'2026-09-23','User-supplied NOURISH reference list; values not independently verified'),
 ('nourish-3211','3211','Layer Grower Concentrated','Layer Feed','Mash',50,64.6,68.6,3430,'2026-09-23','User-supplied NOURISH reference list; values not independently verified'),
 ('nourish-3206','3206','Duck Layer Layer Feed','Layer Feed','Pellet',50,56.15,59.3,2965,'2026-09-23','User-supplied NOURISH reference list; values not independently verified'),
 ('nourish-3207','3207','Quail Layer Layer Feed','Layer Feed','Mash',25,56.66,59.77,1494.25,'2026-09-23','User-supplied NOURISH reference list; values not independently verified'),
 ('nourish-3210','3210','Layer Layer Corporate','Layer Feed','Mash',50,52.35,55.5,2775,'2026-09-23','User-supplied NOURISH reference list; values not independently verified'),
 ('nourish-3106','3106','Sonali/Cock Starter','Sonali/Cock Feed','Crumble',50,64.35,68.5,3425,'2026-09-23','User-supplied NOURISH reference list; values not independently verified'),
 ('nourish-3107','3107','Sonali/Cock Grower','Sonali/Cock Feed','Pellet',50,64.35,68.5,3425,'2026-09-23','User-supplied NOURISH reference list; values not independently verified'),
 ('nourish-3110','3110','Sonali/Cock Finisher','Sonali/Cock Feed','Pellet',50,62.35,66.5,3325,'2026-09-23','User-supplied NOURISH reference list; values not independently verified'),
 ('nourish-3701','3701','Prime Fattening Feed','Cattle/Dairy Feed','Pellet',25,56.3,60.43,1510.75,'2026-09-23','User-supplied NOURISH reference list; values not independently verified'),
 ('nourish-3702','3702','Prime Dairy Feed','Cattle/Dairy Feed','Pellet',25,53.4,57.55,1438.75,'2026-09-23','User-supplied NOURISH reference list; values not independently verified'),
 ('nourish-3703','3703','Sumo Fattening Feed','Cattle/Dairy Feed','Pellet',25,45.4,49.55,1238.75,'2026-09-23','User-supplied NOURISH reference list; values not independently verified'),
 ('nourish-3704','3704','Gowala Dairy Feed','Cattle/Dairy Feed','Pellet',25,44,48.15,1203.75,'2026-09-23','User-supplied NOURISH reference list; values not independently verified'),
 ('nourish-3705','3705','Dairy Feed (Economy)','Cattle/Dairy Feed','Pellet',40,44,48.15,1926,'2026-09-23','User-supplied NOURISH reference list; values not independently verified'),
 ('nourish-3708','3708','Advanced Dairy Feed','Cattle/Dairy Feed','Pellet',25,49.25,53.4,1335,'2026-09-23','User-supplied NOURISH reference list; values not independently verified'),
 ('nourish-3706','3706','Calf Feed','Cattle/Dairy Feed','Pellet',5,61.4,68.55,342.75,'2026-09-23','User-supplied NOURISH reference list; values not independently verified'),
 ('nourish-3707','3707','Bhushi Mix','Cattle/Dairy Feed','Mash',25,44.85,47.4,1185,'2026-09-23','User-supplied NOURISH reference list; values not independently verified'),
 ('nourish-3626','3626','Nursery','Common Sinking Fish Feed','Powder',10,90.33,96.8,968,'2026-09-23','User-supplied NOURISH reference list; values not independently verified'),
 ('nourish-3627','3627','Nursery-1','Common Sinking Fish Feed','Granular',20,81.45,87.8,1756,'2026-09-23','User-supplied NOURISH reference list; values not independently verified'),
 ('nourish-3631','3631','Starter-1','Common Sinking Fish Feed','Crumble',25,80.1,86.3,2157.5,'2026-09-23','User-supplied NOURISH reference list; values not independently verified'),
 ('nourish-3632','3632','Starter-2','Common Sinking Fish Feed','2.6 mm',25,71.38,77.02,1925.5,'2026-09-23','User-supplied NOURISH reference list; values not independently verified'),
 ('nourish-3617','3617','Grower','Pangas Sinking Feed','3.5 mm',25,64.99,70.32,1758,'2026-09-23','User-supplied NOURISH reference list; values not independently verified'),
 ('nourish-3639','3639','Grower Lite','Pangas Sinking Feed','3.5 mm',25,54.9,59.58,1489.5,'2026-09-23','User-supplied NOURISH reference list; values not independently verified'),
 ('nourish-3604','3604','Finisher','Pangas Sinking Feed','4 mm',25,63.67,68.92,1723,'2026-09-23','User-supplied NOURISH reference list; values not independently verified'),
 ('nourish-3640','3640','Finisher Lite','Pangas Sinking Feed','5 mm',25,53.96,58.58,1464.5,'2026-09-23','User-supplied NOURISH reference list; values not independently verified'),
 ('nourish-3613','3613','Grower','Tilapia Sinking Feed','3.5 mm',25,64.99,70.32,1758,'2026-09-23','User-supplied NOURISH reference list; values not independently verified'),
 ('nourish-3629','3629','Finisher','Tilapia Sinking Feed','4 mm',25,63.67,68.92,1723,'2026-09-23','User-supplied NOURISH reference list; values not independently verified'),
 ('nourish-3615','3615','Starter','Koi/Shing/Magur Sinking Feed','Crumble',25,82.77,88.82,2220.5,'2026-09-23','User-supplied NOURISH reference list; values not independently verified'),
 ('nourish-3616','3616','Grower','Koi/Shing/Magur Sinking Feed','2.6 mm',25,79.95,85.82,2145.5,'2026-09-23','User-supplied NOURISH reference list; values not independently verified'),
 ('nourish-3609','3609','Grower','Carp Sinking Feed','3 mm',25,57.5,62.36,1559,'2026-09-23','User-supplied NOURISH reference list; values not independently verified'),
 ('nourish-3637','3637','Grower (Economy)','Carp Sinking Feed','3 mm',25,55.4,60.18,1504.5,'2026-09-23','User-supplied NOURISH reference list; values not independently verified'),
 ('nourish-3641','3641','Mix. Grower Lite','Carp Sinking Feed','3 mm',25,52.02,56.58,1414.5,'2026-09-23','User-supplied NOURISH reference list; values not independently verified'),
 ('nourish-3642','3642','Mix. Mash','Carp Sinking Feed','Mash',50,49.67,54.08,2704,'2026-09-23','User-supplied NOURISH reference list; values not independently verified'),
 ('nourish-3630','3630','Finisher','Carp Sinking Feed','4 mm',25,56.92,61.86,1546.5,'2026-09-23','User-supplied NOURISH reference list; values not independently verified'),
 ('nourish-3634','3634','Nursery','Golda Sinking Feed','Fine Crumble',5,106.61,114.9,574.5,'2026-09-23','User-supplied NOURISH reference list; values not independently verified'),
 ('nourish-3635','3635','Starter','Golda Sinking Feed','1.8 mm',10,84.99,91.9,919,'2026-09-23','User-supplied NOURISH reference list; values not independently verified'),
 ('nourish-3633','3633','Grower','Golda Sinking Feed','2.2 mm',25,70.67,76.86,1921.5,'2026-09-23','User-supplied NOURISH reference list; values not independently verified'),
 ('nourish-3636','3636','Starter','Bagda Sinking Feed','1.8 mm',25,151.85,160.9,4022.5,'2026-09-23','User-supplied NOURISH reference list; values not independently verified'),
 ('nourish-3638','3638','Grower','Bagda Sinking Feed','2.2 mm',25,142.45,150.9,3772.5,'2026-09-23','User-supplied NOURISH reference list; values not independently verified'),
 ('nourish-3644','3644','Golda/Bagda Nursery','Golda/Bagda Sinking Feed','Fine Crumble',1,106.85,124,124,'2026-09-23','User-supplied NOURISH reference list; values not independently verified'),
 ('nourish-3801','3801','Nursery','Common Floating Fish Feed','Powder',10,104.4,110.6,1106,'2026-09-23','User-supplied NOURISH reference list; values not independently verified'),
 ('nourish-3823','3823','Nursery-1 (Oil Coated)','Common Floating Fish Feed','0.5 mm',10,134.93,147,1470,'2026-09-23','User-supplied NOURISH reference list; values not independently verified'),
 ('nourish-3824','3824','Nursery-2 (Oil Coated)','Common Floating Fish Feed','1.0 mm',20,112.83,123.5,2470,'2026-09-23','User-supplied NOURISH reference list; values not independently verified'),
 ('nourish-3813','3813','Starter-1 (Oil Coated)','Common Floating Fish Feed','1.5 mm',25,98.54,106.7,2667.5,'2026-09-23','User-supplied NOURISH reference list; values not independently verified'),
 ('nourish-3807','3807','Starter-2 (Oil Coated)','Common Floating Fish Feed','2.0 mm',25,88.14,95.8,2395,'2026-09-23','User-supplied NOURISH reference list; values not independently verified'),
 ('nourish-3803','3803','Grower (Oil Coated)','Pangas Floating Feed','3.0 mm',25,78.62,83.55,2088.75,'2026-09-23','User-supplied NOURISH reference list; values not independently verified'),
 ('nourish-3815','3815','Grower (Semi Coated)','Pangas Floating Feed','3.0 mm',25,73.87,78.55,1963.75,'2026-09-23','User-supplied NOURISH reference list; values not independently verified'),
 ('nourish-3804','3804','Finisher (Oil Coated)','Pangas Floating Feed','5.0 mm',25,76.75,81.55,2038.75,'2026-09-23','User-supplied NOURISH reference list; values not independently verified'),
 ('nourish-3816','3816','Finisher (Semi Coated)','Pangas Floating Feed','5.0 mm',25,72.91,77.55,1938.75,'2026-09-23','User-supplied NOURISH reference list; values not independently verified'),
 ('nourish-3808','3808','Grower (Oil Coated)','Tilapia Floating Feed','3.0 mm',25,80.05,85.15,2128.75,'2026-09-23','User-supplied NOURISH reference list; values not independently verified'),
 ('nourish-3817','3817','Grower (Semi Coated)','Tilapia Floating Feed','3.0 mm',25,74.88,79.55,1988.75,'2026-09-23','User-supplied NOURISH reference list; values not independently verified'),
 ('nourish-3809','3809','Finisher (Oil Coated)','Tilapia Floating Feed','4.0 mm',25,78.05,83.05,2076.25,'2026-09-23','User-supplied NOURISH reference list; values not independently verified'),
 ('nourish-3818','3818','Finisher (Semi Coated)','Tilapia Floating Feed','4.0 mm',25,73.85,78.55,1963.75,'2026-09-23','User-supplied NOURISH reference list; values not independently verified'),
 ('nourish-3836','3836','Pre-Starter','Koi Floating Feed','1.5 mm',25,97.69,103.7,2592.5,'2026-09-23','User-supplied NOURISH reference list; values not independently verified'),
 ('nourish-3837','3837','Starter','Koi Floating Feed','2.0 mm',25,93.39,99,2475,'2026-09-23','User-supplied NOURISH reference list; values not independently verified'),
 ('nourish-3812','3812','Grower (Oil Coated)','Koi Floating Feed','3.0 mm',25,92.89,98.4,2460,'2026-09-23','User-supplied NOURISH reference list; values not independently verified'),
 ('nourish-3814','3814','Grower (Non Coated)','Koi Floating Feed','3.0 mm',25,83.59,88.8,2220,'2026-09-23','User-supplied NOURISH reference list; values not independently verified'),
 ('nourish-3834','3834','Mix Starter Lite','Carp Floating Feed','2.0 mm',25,66.49,71.6,1790,'2026-09-23','User-supplied NOURISH reference list; values not independently verified'),
 ('nourish-3835','3835','Mix Grower Lite','Carp Floating Feed','3.0 mm',25,66.04,70.6,1765,'2026-09-23','User-supplied NOURISH reference list; values not independently verified'),
 ('nourish-3822','3822','Grower (Semi Coated)','Carp Floating Feed','3.0 mm',25,67.1,71.8,1795,'2026-09-23','User-supplied NOURISH reference list; values not independently verified'),
 ('nourish-3833','3833','Finisher (Semi Coated)','Carp Floating Feed','5.0 mm',25,65.3,69.8,1745,'2026-09-23','User-supplied NOURISH reference list; values not independently verified'),
 ('nourish-3828','3828','Catfish Hatchery Powder','Catfish Floating Feed','Fine Powder',5,128.84,137.3,686.5,'2026-09-23','User-supplied NOURISH reference list; values not independently verified'),
 ('nourish-3829','3829','Catfish Pre-Nursery','Catfish Floating Feed','0.4 mm',10,158,171.3,1713,'2026-09-23','User-supplied NOURISH reference list; values not independently verified'),
 ('nourish-3830','3830','Catfish Nursery','Catfish Floating Feed','0.8 mm',20,117.87,129.3,2586,'2026-09-23','User-supplied NOURISH reference list; values not independently verified'),
 ('nourish-3831','3831','Catfish Starter','Catfish Floating Feed','1.2 mm',25,108.97,119.3,2982.5,'2026-09-23','User-supplied NOURISH reference list; values not independently verified'),
 ('nourish-3832','3832','Catfish Grower','Catfish Floating Feed','2.0 mm',25,104.79,114.3,2857.5,'2026-09-23','User-supplied NOURISH reference list; values not independently verified');
 INSERT INTO nourish.product(id,name,sku,category_id,kind,unit_code,subtype,feed_group,feed_type,feed_catalog_id)
 SELECT 'prod-'||id,feed_group||' — '||name,'NOUR-'||product_code,'cat-feed','feed','kg',feed_type,feed_group,feed_type,id FROM nourish.feed_price_catalog;
 -- Supported bag definitions, independent of the historical price-list packing size; no stock.
 INSERT INTO nourish.product_packaging(id,product_id,amount,unit_code,size_base,package_type)
 SELECT 'pack-'||f.product_code||'-'||s,'prod-'||f.id,s,'kg',s,'bag'
 FROM nourish.feed_price_catalog f CROSS JOIN unnest(ARRAY[1,5,10,20,25,40,50]) s;
 INSERT INTO nourish.schema_version(version,description) VALUES(3,'Phase 3 - category, unit, product and catalog');
END
$phase3$;
