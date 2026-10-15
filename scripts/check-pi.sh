#!/usr/bin/env bash

set -euo pipefail

base="${1:-http://localhost:8080}"

systemctl show profitpilot.service -p ActiveState -p SubState -p ActiveEnterTimestamp -p NRestarts

for endpoint in operations/build operations/imports finance/data-status finance/import-budget; do
  printf '\n%s\n' "$endpoint"

  curl --fail-with-body --silent --show-error --max-time 15 "$base/api/$endpoint"

  printf '\n'
done
