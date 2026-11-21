#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
export DB_PORT="${DB_PORT:-2001}"
: "${DB_PASSWORD:?Export your PostgreSQL DB_PASSWORD first.}"
jar=nourish-provider/target/nourish-provider.jar
[[ -f "$jar" ]] || { echo 'Run mvn clean install first.'; exit 1; }
read -r -p 'New admin username: ' NOURISH_ADMIN_USERNAME
read -r -p 'Admin display name: ' NOURISH_ADMIN_DISPLAY_NAME
read -r -s -p 'New login password (12+ characters, max 72 UTF-8 bytes): ' NOURISH_ADMIN_PASSWORD
printf '
'
read -r -s -p 'Confirm login password: ' confirmation
printf '
'
[[ "$NOURISH_ADMIN_PASSWORD" == "$confirmation" ]] || { echo 'Passwords do not match.'; exit 1; }
export NOURISH_ADMIN_USERNAME NOURISH_ADMIN_DISPLAY_NAME NOURISH_ADMIN_PASSWORD
trap 'unset NOURISH_ADMIN_USERNAME NOURISH_ADMIN_DISPLAY_NAME NOURISH_ADMIN_PASSWORD confirmation' EXIT
java -jar "$jar" --spring.main.web-application-type=none --nourish.bootstrap.enabled=true
