#!/usr/bin/env bash
# Run on the deployment server after CD succeeds. Read-only checks.
set -euo pipefail
sha=${1:?Usage: bash verify-deploy.sh SHA PROJECT_DIR BASE_URL}
project_dir=${2:?Expected absolute project directory}
base_url=${3:?Expected externally reachable application URL}
[[ "$sha" =~ ^[a-f0-9]{40}$ ]] || { echo 'Invalid SHA' >&2; exit 1; }
[[ "$project_dir" = /* ]] || { echo 'Expected absolute project directory' >&2; exit 1; }
[[ "$base_url" =~ ^https?:// ]] || { echo 'Expected HTTP(S) URL' >&2; exit 1; }
base_url=${base_url%/}
cd "$project_dir"
[[ "$(pwd -P)" = "$(git rev-parse --show-toplevel)" ]]
[[ "$(git rev-parse HEAD)" = "$sha" ]] || { echo 'Server HEAD differs from expected CI SHA' >&2; exit 1; }
[[ -z "$(git status --porcelain)" ]] || { echo 'Server working tree has local changes' >&2; exit 1; }
state_dir=$(git rev-parse --git-path kanban-deploy-state)
[[ "$(cat "$state_dir/deployed.sha")" = "$sha" ]] || {
  echo 'Expected SHA was not recorded as successfully deployed' >&2; exit 1;
}
docker compose config --quiet
for service in postgres minio backend frontend; do
  container=$(docker compose ps -q "$service")
  [[ -n "$container" ]] || { echo "Missing container: $service" >&2; exit 1; }
  status=$(docker inspect --format '{{.State.Status}}/{{.State.Health.Status}}' "$container")
  [[ "$status" = running/healthy ]] || { echo "$service: $status" >&2; exit 1; }
  echo "PASS: $service running/healthy"
done
for path in / /profile /health /api/system/health; do
  status=$(curl --silent --show-error --output /dev/null --write-out '%{http_code}' \
    --connect-timeout 10 --max-time 30 "$base_url$path")
  [[ "$status" = 200 ]] || { echo "$path returned HTTP $status" >&2; exit 1; }
  echo "PASS: $path HTTP 200"
done
echo "PASS: server checkout and deployment record match CI SHA $sha"
echo 'Container source provenance, browser flows and database persistence need separate verification.'
