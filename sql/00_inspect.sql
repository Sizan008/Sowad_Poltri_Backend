SELECT current_database(), current_user, version();
SELECT schema_name FROM information_schema.schemata ORDER BY schema_name;
SELECT table_schema, table_name FROM information_schema.tables
WHERE table_schema NOT IN ('pg_catalog', 'information_schema')
ORDER BY table_schema, table_name;
