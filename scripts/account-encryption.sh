#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
[[ -n "${ACCOUNT_DATA_KEY:-}${ACCOUNT_DATA_KEY_FILE:-}" ]] || { echo "Set ACCOUNT_DATA_KEY_FILE or ACCOUNT_DATA_KEY. See docs/ACCOUNT_ENCRYPTION.md" >&2; exit 1; }
jar=nourish-provider/target/nourish-provider.jar
[[ -f "$jar" ]] || { echo 'Build first: mvn -DskipTests clean install' >&2; exit 1; }
exec java -Dloader.main=com.sowad.nourish.common.crypto.AccountProtectionCli -cp "$jar" org.springframework.boot.loader.launch.PropertiesLauncher "$@"
