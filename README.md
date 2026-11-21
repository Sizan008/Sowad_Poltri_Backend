Current release: Phase 7 — see PHASE_07_START_HERE.md and docs/PHASE_07_API.md. Backend only.

Current release: Phase 6 — see PHASE_06_START_HERE.md and docs/PHASE_06_API.md. Backend only.

Current release: Phase 5 — see PHASE_05_START_HERE.md and docs/PHASE_05_API.md. Backend only.

# Current release: Phase 4

Start with [PHASE_04_START_HERE.md](PHASE_04_START_HERE.md).
Backend only; no frontend patch is required.

---

## Historical instructions

### Phase 2

Start with [PHASE_02_START_HERE.md](PHASE_02_START_HERE.md).
Phase 1 instructions below are retained as historical reference.



## Setup on Ubuntu

```bash
sudo apt install openjdk-21-jdk maven python3
java -version
mvn -version
cd ~/Documents/Sowad_Poltri_Backend
```

Use Java 21 for both IDE and Maven. Maven downloads dependencies on the first build.

1. Connect to the already-created database as its owner/application user:

```bash
psql -h 127.0.0.1 -U myapp_user -d nourish_poltri_shop -W
```

Run sql/00_inspect.sql, then sql/01_foundation.sql (also supplied in chat).
Execute as myapp_user so it owns the schema/table. The patch never runs SQL.
Version 1 uses CREATE IF NOT EXISTS and INSERT ON CONFLICT; existing incompatible
objects are not silently altered. Inspect any existing nourish objects first.
The migration is transactional. Do not register the version manually without its DDL.

2. Build and test:

```bash
bash scripts/build.sh
```

Tests start a disposable real PostgreSQL instance using test-only embedded-postgres.
No system PostgreSQL installation or Docker is needed for tests; binaries download
through Maven. Run tests as a normal Linux user (PostgreSQL refuses root).
Tests use a random port and never connect to your configured shop database.

3. Set configuration in the SAME terminal and start:

```bash
export DB_HOST=127.0.0.1 DB_PORT=5432
export DB_NAME=nourish_poltri_shop DB_USER=myapp_user
read -r -s -p 'PostgreSQL password: ' DB_PASSWORD
printf '\n'
export DB_PASSWORD
bash scripts/run.sh
```

The password is requested locally, not hardcoded or included in shell history.
config/env.example is documentation; it is not automatically sourced.
For IDE startup set these environment variables in ProviderApplication's run configuration.
Optional SERVER_PORT defaults to 8080. Default bind address is 127.0.0.1.
The current frontend still uses the dummy server; do not switch its full base URL yet.
Phase 1 provides only /health, not business APIs.

4. In another terminal:

```bash
curl -i http://127.0.0.1:8080/health
```

Expected HTTP 200:

```json
{"status":"ok","phase":1,"mode":"postgresql","database":"nourish_poltri_shop","schema":"nourish","schemaVersion":1}
```

Missing/wrong schema version gives HTTP 503. Wrong database credentials or a missing
schema table can stop startup due to JPA validation. No automatic schema creation.

## Module responsibilities
- nourish-api: interfaces, query DTOs, future commands, common response contracts.
- nourish-service: controllers, command/query implementations, validation, JPA/JDBC,
  exceptions, request correlation/logging, CORS.
- nourish-provider: boot entry point, connection configuration and runnable JAR.

Feature commands will use FeatureService.process(CreateFeature command) and
FeatureServiceImpl with @Transactional. Reads use FeatureQueryService.retrieve...,
FeatureQueryServiceImpl and JDBC repositories with parameterized SQL. CommandRepository
is a @NoRepositoryBean base, not a concrete table or exposed generic write API.
SchemaVersionRepository demonstrates JPA without allowing application version writes.

## Database conventions
Manual versioned SQL is the only DDL route. Hibernate ddl-auto=validate;
spring.sql.init.mode=never. One data source for JPA/JDBC; use Spring-managed transactions
and JdbcTemplate rather than raw unmanaged connections. No production data is seeded.

## Next phase
Real authentication, roles and settings. Password hashing and verification are explicitly
included in Phase 2. Never store plaintext passwords or import frontend demo passwords.
No login endpoint or password column is implemented in this foundation patch.

See docs/API_CONTRACT.md and docs/frontend-contracts for exact baseline contracts.
