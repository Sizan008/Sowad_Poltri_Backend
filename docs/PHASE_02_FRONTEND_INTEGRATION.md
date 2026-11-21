# Phase 2 Angular integration guide

Backend patch only; it does not edit the Angular project. Phase 2 business features
beyond settings, identity and audit are not implemented yet. Do not redirect every
existing Angular request from JSON Server to Spring Boot at this stage.

## Exact frontend changes for the integration stage
1. Add authApiBaseUrl = 'http://127.0.0.1:8080' to src/environments/environment.ts.
   Keep apiBaseUrl pointing to JSON Server for remaining screens until their phases.
2. In models/auth.model.ts replace demo types with CurrentUser (id, username,
   displayName, role, roles: string[]) and LoginResult (accessToken, tokenType,
   expiresAt, user). Keep role for current sidebar compatibility; check roles for guards.
3. In services/auth.service.ts remove DEMO_ACCOUNTS comparison, readSession demo
   reconstruction, and localStorage demo keys. Login returns Observable<LoginResult>
   from POST /auth/login. Store token/user in memory signals. Password is sent once
   over the login request and never saved in a service/storage/log. Server does hashing.
4. pages/login/login.component.ts must subscribe to the async login result, show the
   pending state, navigate only on success, and show error.message on 400/401/503.
   The old synchronous boolean login flow must be removed.
5. Replace onboarding/interceptors/actor.interceptor.ts with a bearer interceptor.
   Attach Authorization only when URL origin is authApiBaseUrl's origin; never send
   token to JSON Server or arbitrary URLs. Do not attach a stale token to /auth/login.
   Remove X-Demo-Actor, X-Demo-Actor-Name and X-Demo-Actor-Role. They have no authority.
6. Register the bearer interceptor in app.config.ts alongside loading/error interceptors.
   On 401 clear in-memory identity/token and navigate to login; on 403 show permission
   denied without automatically logging out. Avoid a login/401 redirect loop.
7. Logout POSTs /auth/logout with the current bearer token, then clears local state.
   If offline, local state can clear, but server revocation is not confirmed and the
   session remains usable until expiry; tell the user when logout could not reach server.
8. Remove AuthService.auditSession() calls to /activity-events/session. Server writes
   login/logout automatically. That legacy demo-write endpoint is intentionally absent.
9. Send settings service calls and activity-log service calls to authApiBaseUrl using
   the routes below. Their current lower-camelCase direct object/array shapes are retained.
10. On reload, memory-only tokens are gone: require login again. No automatic refresh
    token exists in Phase 2. Do not restore authority from the old demo localStorage ID.
    UI permissions are convenience; backend permissions remain authoritative.

## API contracts
| Method/path | Payload | Response/access |
|---|---|---|
| POST /auth/login | {username,password} | 200 {accessToken,tokenType:'Bearer',expiresAt,user}; public |
| GET /auth/me | Authorization header | 200 CurrentUser; any authenticated role |
| POST /auth/logout | Authorization header | 204; current session revoked |
| POST /users | {username,password,displayName,role} | 201 CurrentUser; admin only |
| GET /users?limit=100&offset=0 | Authorization header | CurrentUser[]; admin only |
| GET /businessSettings/business | Authorization header | BusinessSettings; all 3 roles |
| PUT /transactions/settings | UpdateBusinessSettingsRequest | BusinessSettings; admin only |
| GET /activityLogs?limit=100&offset=0 | Authorization header | ActivityLogEntry[]; admin only |
| GET /health | none | phase/schemaVersion 2; public |

CreateUser assigns one allowed role; DB supports user-role relationships. Role updates,
password reset/change and a user-management UI are not supplied in this phase.
Operator write access for purchase/sale will be added with those feature phases.
Pagination limit is 1..200; arrays are returned. Offset >= 0. No total-count envelope yet.

Login username is case-insensitive. New usernames use [a-z0-9._-], length 3..64.
New passwords require >=12 characters and <=72 UTF-8 bytes; bcrypt cost is 12.
Five consecutive failed attempts lock an existing active account for 15 minutes.
Errors intentionally do not distinguish missing users from invalid credentials.
Bearer tokens have 256 random bits, 8-hour absolute expiry, and SHA-256 hashes in DB.
No cookies authenticate this API; CSRF is disabled specifically for header-only auth.
Use HTTPS when deploying beyond localhost. Do not put bearer tokens into query strings.

Business settings: id='business', currency='BDT', businessName, ownerName, phone,
email, address, invoiceFooter, lowStockDefault, uiLanguage='en'|'bn'. PUT excludes id
and currency. Low stock must be 0..1000000 with <=3 fractional digits.
Activity actor identity is generated from the current session, never from request headers.
