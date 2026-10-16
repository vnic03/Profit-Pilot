#!/usr/bin/env bash

# Run only after the SQL migration, as root, on the Pi.

set -euo pipefail

cd "$(dirname "$0")"

[[ "$EUID" == 0 ]] || { echo 'Run with sudo.'; exit 1; }

target="${1:?Usage: sudo bash install-pi-package.sh /absolute/path/to/running.jar [base-url]}"

base="${2:-http://localhost:8080}"

# Only the jar argument may be replaced, never the Java executable.
[[ "$target" == /*.jar && -f "$target" && ! -L "$target" ]] || {
  echo 'Expected an existing absolute .jar path, not the Java executable or a symlink.'
  exit 1
}

start_command="$(systemctl show profitpilot.service -p ExecStart --value)"

jar_argument='-jar[[:space:]]+([^[:space:];]+)'

[[ "$start_command" =~ $jar_argument ]] || {
  echo 'Could not identify the service -jar argument. No files changed.'
  exit 1
}

[[ "${BASH_REMATCH[1]}" == "$target" ]] || {
  echo 'Target must exactly match the path immediately after -jar. No files changed.'
  exit 1
}

sha256sum -c SHA256SUMS

backup="${target}.backup.$(date -u +%Y%m%dT%H%M%SZ)"

cp -p -- "$target" "$backup"

# Restore application code only; database migrations are not undone here.
rollback() {
  trap - ERR
  echo "Update failed; restoring $backup"
  systemctl stop profitpilot.service
  cp -p -- "$backup" "$target"
  systemctl restart profitpilot.service
}

trap rollback ERR

systemctl stop profitpilot.service

cp -- profit-pilot.jar "$target"

systemctl start profitpilot.service

ready=false

for attempt in {1..30}; do
  if curl -fsS --max-time 2 "$base/api/operations/build" > /dev/null; then ready=true; break; fi
  sleep 2
done

[[ "$ready" == true ]]

# Check DB-dependent endpoints too. A migration or permission error triggers rollback.
for endpoint in operations/imports finance/data-status finance/import-budget; do
  curl -fsS --max-time 15 "$base/api/$endpoint" > /dev/null
done

trap - ERR

printf 'Updated successfully. Backup: %s\n' "$backup"

bash check-pi.sh "$base"

