#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
command -v mvn >/dev/null || { echo 'Maven is required: sudo apt install maven'; exit 1; }
mvn -B clean verify "$@"
