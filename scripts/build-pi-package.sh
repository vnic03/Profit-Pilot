#!/usr/bin/env bash

set -euo pipefail

cd "$(dirname "$0")/.."

export PROFITPILOT_BUILD_REVISION=\
"$(git rev-parse --short HEAD)-$(date -u +%Y%m%dT%H%M%SZ)"

# The package includes local edits, so the commit alone may not identify its contents.
if [[ -n "$(git status --porcelain)" ]]; then
  PROFITPILOT_BUILD_REVISION="${PROFITPILOT_BUILD_REVISION}-dirty"
fi

(
  cd web
  npm ci
  npm test
  VITE_API_BASE_URL= npm run build
)

./gradlew test bootJar --console=plain

mkdir -p build/pi-package

cp build/libs/profit-pilot.jar build/pi-package/

cp src/main/resources/db/import-run-status.sql build/pi-package/

cp scripts/install-pi-package.sh scripts/check-pi.sh build/pi-package/

printf '%s\n' "$PROFITPILOT_BUILD_REVISION" > build/pi-package/REVISION

(
  cd build/pi-package && sha256sum profit-pilot.jar > SHA256SUMS
)

printf 'Package ready: build/pi-package\n'
