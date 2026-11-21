# Swagger and persistent IntelliJ environment setup

This patch targets Sowad_Poltri_Backend(8).zip. It does not change database schema, existing data, app users/passwords, frontend, run.sh, or the corrected phase8-check.py.

## What is already configured

application.properties already reads DB_HOST, DB_PORT, DB_NAME, DB_USER and DB_PASSWORD from the process environment. Terminal `export` creates environment variables for that shell and its child processes; it is not a permanent setting and does not modify an IntelliJ application run configuration. The four non-secret DB fields have local defaults; DB_PASSWORD deliberately has no default. The JDBC URL is assembled from these five values. No extra DB_URL is required.

The phase8-check.py prompts are separate: its URL tells the test script where the running HTTP API is, and its username/password are application credentials (e.g. Sowad). They are not the database credentials and are not required to start the backend.

## Apply and build

Stop the existing backend before restarting a new instance on port 8080.

```bash
cd ~/Documents/Sowad_Poltri_Backend
bash ~/Downloads/NOURISH_Backend_Swagger_Maven_v2.sh --check
bash ~/Downloads/NOURISH_Backend_Swagger_Maven_v2.sh
mvn clean install
```

Installer checks changed-file hashes before changing anything, rejects conflicting edits/symlinks, backs up previous files under patch-backups, and is safe to rerun. It does not write credentials or execute SQL. Python 3 is required. Maven needs network access for the new springdoc dependency on the first build. No SQL needs to be run in DBeaver for this patch.

## IntelliJ — configure once

1. Open ~/Documents/Sowad_Poltri_Backend as the project.
2. Your screenshot shows **Load Maven Project**; click it. Alternatively add the root pom.xml as a Maven project and reload Maven.
3. In File → Project Structure set the Project SDK to **JDK 21**.
4. Open Run → Edit Configurations.
5. Click + → **Application**. This works without requiring a Spring Boot-specific run configuration.
6. Name: `NOURISH Local`.
7. Main class: `com.sowad.nourish.ProviderApplication`.
8. Use classpath of module: `nourish-provider` (its main module if IntelliJ displays `.main`). Select JDK 21 as the runtime.
9. Working directory: `/home/sizan/Documents/Sowad_Poltri_Backend` (or select your actual project root).
10. If Environment variables is hidden, choose Modify options → Environment variables.
11. Open its table/editor and add each row separately:

| Name | Value |
|---|---|
| DB_HOST | 127.0.0.1 |
| DB_PORT | 2001 |
| DB_NAME | nourish_poltri_shop |
| DB_USER | myapp_user |
| DB_PASSWORD | Your actual PostgreSQL password; type it locally |
| SERVER_ADDRESS | 127.0.0.1 |
| SERVER_PORT | 8080 |
| CORS_ALLOWED_ORIGINS | http://localhost:4200,http://127.0.0.1:4200 |
| SWAGGER_ENABLED | true |

Do not put `export`, quotes or `${...}` in the value cells. The table is preferable to a semicolon-separated string, especially when a password contains punctuation. Keep **Store as project file** unchecked for this configuration. Do not share/export this credential-bearing configuration; local IDE configuration is not an encrypted secret vault. `.idea/` is already excluded from Git in the project.

12. Click Apply → OK.
13. Select `NOURISH Local` in the toolbar, replacing `Current File`, then click Run or Debug.

Now this run configuration passes the saved environment variables automatically each time. A separately opened terminal does not inherit these IDE run-configuration variables. `bash scripts/run.sh` still uses its terminal environment, while the IntelliJ Run button uses the configuration above. Use one method at a time to avoid port conflicts.

`config/environment.example` is a reference only. Spring Boot does not automatically load an arbitrary .env file, and this patch does not source shell files. Never put real passwords into the example file or application.properties.

## Swagger usage

With SWAGGER_ENABLED=true, open:
- http://127.0.0.1:8080/swagger-ui/index.html
- http://127.0.0.1:8080/swagger-ui.html (redirect)
- http://127.0.0.1:8080/ (redirect, as in the reference project)
- http://127.0.0.1:8080/v3/api-docs (OpenAPI JSON)

1. Expand Authentication → POST /auth/login → Try it out.
2. Supply your existing application username/password:
```json
{"username":"Sowad","password":"YOUR_APP_PASSWORD"}
```
Replace the placeholder locally; this is not DB_PASSWORD.
3. Execute and copy only the response `accessToken` value.
4. Click Authorize, paste that token without adding `Bearer `, then Authorize → Close.
5. Call a read endpoint such as GET /products. Swagger adds the Authorization header.
6. For logout call POST /auth/logout while still authorized, then clear Authorize. Tokens expire normally; UI authorization is not persisted across reloads.

Authentication uses **opaque bearer tokens**, not JWT. Reference CloudNet Console's JWT label and login route were not copied because this project's authentication differs. Login and health are public; business APIs still enforce authentication and service-level role permissions. Making docs readable does not make business data public.

The provider module owns the Swagger dependency/configuration. Existing api/service/provider separation is retained. Raw String/JsonNode request bodies are documented with their actual command schemas without changing the strict decoder or transaction behavior. Nested purchase and invoice Line schemas use qualified names to prevent collisions. Runtime response types remain inferred; endpoints returning Map/Object can have generic response schemas, so the existing phase API guides remain the detailed response reference.

For purchase/sale/payment/expense/adjustment POSTs, enter a fresh UUID in Idempotency-Key for each intended operation. Use the same key and identical body only for a retry. Generated schema samples are placeholders, not business-valid test data: choose real referenced IDs, active packages and valid amounts. **Try it out executes real API commands**; successful writes remain in the selected database. Use read endpoints until ready to perform controlled transactions.

## Hosting

Configure environment variables in the hosting service's environment/secret settings. Values on your PC are not automatically uploaded. Keep DB_PASSWORD as a deployment secret. Configure real DB_HOST/DB_PORT/DB_NAME/DB_USER, the API port, and the exact frontend HTTPS origin(s). The current application/migrations expect the database name `nourish_poltri_shop` and schema `nourish`; retain them unless that contract is deliberately changed.

- Set SWAGGER_ENABLED=false for ordinary public deployment (also the default when absent).
- Container/platform exposure generally requires SERVER_ADDRESS=0.0.0.0; a backend behind a local reverse proxy can stay bound to loopback.
- Map the platform's assigned port to SERVER_PORT; this app does not automatically read a variable named PORT.
- Preserve PostgreSQL connectivity/required TLS configuration appropriate to the hosting provider. Do not use local-only DB_HOST/DB_PORT values for a remote database unless the deployment actually uses them.
- Do not supply INITIAL_ADMIN_* bootstrap variables for your existing installation unless intentionally creating an initial admin; this patch does not reset users.

## Verification

The patch includes an integration test for Swagger disabled/enabled behavior, UI resources, OpenAPI JSON, public login/global bearer security, raw command request schemas, resolvable nested schema references and unchanged protection of business endpoints. Regression tests use disposable PostgreSQL, not the user's running database.

Official references:
- https://springdoc.org/v2/ (Spring Boot 3.5 compatibility with springdoc 2.x)
- https://www.jetbrains.com/help/idea/program-arguments-and-environment-variables.html
- https://www.jetbrains.com/help/idea/run-debug-configuration-java-application.html


## Run from nourish-provider (v2)

The project `.mvn/maven.config` explicitly selects `config/maven-public-settings.xml` for both user and global Maven settings. Maven resolves these paths from the working directory. This patch adds the same non-secret public settings under `nourish-provider/config/`, preserving the root configuration and its public-Central policy. Keep these two settings files in sync if changing repository policy later.

After applying, install all modules once from the project root:

```bash
cd ~/Documents/Sowad_Poltri_Backend
mvn clean install
```

Stop any backend running in IntelliJ or another terminal. In the terminal where you will run Maven:

```bash
export DB_HOST=127.0.0.1
export DB_PORT=2001
export DB_NAME=nourish_poltri_shop
export DB_USER=myapp_user
export SERVER_ADDRESS=127.0.0.1
export SERVER_PORT=8080
export SWAGGER_ENABLED=true
read -r -s -p 'PostgreSQL password: ' DB_PASSWORD
printf '\n'
export DB_PASSWORD
cd ~/Documents/Sowad_Poltri_Backend/nourish-provider
mvn spring-boot:run
```

The correct Maven goal is `spring-boot:run`, not `spring-boot`. IntelliJ Application Run Configuration variables are not inherited by its Terminal tab. These exports last for this terminal session. Alternatively use the IntelliJ Run button with the saved environment described above.

After changes to nourish-api or nourish-service, run root `mvn install` again before provider-only startup; otherwise Maven may use older installed sibling-module JARs. From the root, after installing modules, `mvn -pl nourish-provider spring-boot:run` is also supported.

No database SQL or frontend patch is required. Existing business APIs keep their authentication and role checks. Swagger is disabled by default; enable it explicitly for local development.

Validation for this v2 artifact: exact ZIP(8) hashes checked; installer preflight, application, idempotence and conflict rejection checked; root/provider Maven settings resolution checked on an isolated Maven project. Full application build was attempted here but blocked by Maven dependency resolution/network access. Run `mvn clean install` locally before starting. The Swagger implementation and integration test are retained from the previously tested Swagger patch.
