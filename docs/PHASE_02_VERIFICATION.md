# Phase 2 verification

Baseline: Sowad_Poltri_Backend(1).zip. Compared against Phase 1: new .mvn/maven.config,
config/maven-public-settings.xml and patch-state metadata only; source code matches.
The patch preserves .mvn, Maven settings, .idea and independently edited configuration.

Maven clean verify: BUILD SUCCESS; 11 tests, 0 failures/errors/skips.
Tests use disposable real PostgreSQL, not the user's database. Migration applied twice.
Covers password hash/session hash storage, authenticated identity, role enforcement,
actor-header spoofing rejection, missing/invalid/expired/revoked tokens, account
lockout, user deactivation, role changes on existing sessions, payload validation,
CORS and atomic settings+audit rollback on database failure.

Packaged JAR smoke test: local admin bootstrap succeeded, repeated bootstrap refused;
real HTTP /health, login, /auth/me, settings read, logout and revoked-token rejection passed.
The environment's JRE lacks ct.sym, so local Maven compilation used explicit Java 21
source/target overrides, as in Phase 1. The delivered POM requires a normal full JDK 21.
The user's local server and database are verified after the supplied installation steps.
