#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
export DB_PORT="${DB_PORT:-2001}"
: "${DB_PASSWORD:?Export DB_PASSWORD first. See README.md.}"
jar=nourish-provider/target/nourish-provider.jar
[[ -f "$jar" ]] || { echo 'Run bash scripts/build.sh first.'; exit 1; }
exec java -jar "$jar" "$@"
