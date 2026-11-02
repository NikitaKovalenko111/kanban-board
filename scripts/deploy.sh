#!/usr/bin/env bash
set -euo pipefail

sha=${1:?Expected commit SHA}
project_dir=${2:?Expected absolute project directory}
repository=${3:?Expected owner/repository}
[[ "$sha" =~ ^[a-f0-9]{40}$ ]] || { echo 'Invalid SHA' >&2; exit 1; }
[[ "$project_dir" = /* ]] || { echo 'Project directory must be absolute' >&2; exit 1; }

cd "$project_dir"
[[ "$(pwd -P)" = "$(git rev-parse --show-toplevel)" ]] || {
  echo 'Project directory must be the repository root' >&2; exit 1;
}
command -v flock > /dev/null
exec 9> "$(git rev-parse --git-path kanban-deploy.lock)"
flock -n 9 || { echo 'Another deployment is running' >&2; exit 1; }

origin=$(git remote get-url origin)
case "$origin" in
  "https://github.com/$repository"|"https://github.com/$repository.git"|\
  "git@github.com:$repository"|"git@github.com:$repository.git") ;;
  *) echo 'Server repository origin does not match GitHub repository' >&2; exit 1 ;;
esac

[[ -z "$(git status --porcelain)" ]] || {
  echo 'Server working tree has local changes; refusing to overwrite them' >&2; exit 1;
}
[[ -f .env ]] || { echo 'Create server .env before deployment' >&2; exit 1; }
docker compose version
git fetch --no-tags origin main
[[ "$(git rev-parse FETCH_HEAD)" = "$sha" ]] || {
  echo "Skipping superseded CI commit $sha: main has moved"
  exit 0
}
git cat-file -e "$sha^{commit}"
git checkout --detach "$sha"
[[ "$(git rev-parse HEAD)" = "$sha" ]]

diagnostics() {
  result=$?
  if (( result != 0 )); then
    echo "Deployment of $sha failed. See docs/cd.md for rollback." >&2
    docker compose ps || true
    # Avoid printing application logs, which may contain sensitive data.
  fi
}
trap diagnostics EXIT

docker compose config --quiet
# Build before replacing running containers.
docker compose build
docker compose up -d --remove-orphans --wait --wait-timeout 180
docker compose exec -T frontend wget -q -O /dev/null http://127.0.0.1/
docker compose exec -T frontend wget -q -O /dev/null http://127.0.0.1/health
docker compose exec -T frontend wget -q -O /dev/null http://127.0.0.1/api/system/health

state_dir=$(git rev-parse --git-path kanban-deploy-state)
mkdir -p "$state_dir"
if [[ -f "$state_dir/deployed.sha" ]]; then
  cp "$state_dir/deployed.sha" "$state_dir/previous.sha"
fi
printf '%s\n' "$sha" > "$state_dir/deployed.sha.tmp"
mv "$state_dir/deployed.sha.tmp" "$state_dir/deployed.sha"
echo "Deployment verified: $sha"
docker compose ps
