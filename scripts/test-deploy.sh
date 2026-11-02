#!/usr/bin/env bash
# Control-flow checks; no SSH, real Docker, or production repository changes.
set -euo pipefail
script_dir=$(cd "$(dirname "$0")" && pwd)
test_dir=$(mktemp -d)
trap 'rm -rf "$test_dir"' EXIT
mkdir -p "$test_dir/bin" "$test_dir/project/state"
touch "$test_dir/project/.env"
export TEST_PROJECT
TEST_PROJECT=$(cd "$test_dir/project" && pwd -P)
export TEST_SHA=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
export TEST_LOG="$test_dir/commands.log"

cat > "$test_dir/bin/git" <<'MOCK'
#!/usr/bin/env bash
case "$*" in
  'rev-parse --show-toplevel') printf '%s\n' "$TEST_PROJECT" ;;
  'rev-parse --git-path kanban-deploy.lock') printf '%s/lock\n' "$TEST_PROJECT" ;;
  'rev-parse --git-path kanban-deploy-state') printf '%s/state\n' "$TEST_PROJECT" ;;
  'remote get-url origin') printf '%s\n' "${TEST_ORIGIN:-https://github.com/example/kanban.git}" ;;
  'status --porcelain') printf '%s' "${TEST_DIRTY:-}" ;;
  'rev-parse FETCH_HEAD') printf '%s\n' "${TEST_LATEST:-$TEST_SHA}" ;;
  'rev-parse HEAD') printf '%s\n' "$TEST_SHA" ;;
  'fetch --no-tags origin main'|'cat-file -e '*|'checkout --detach '*) printf 'git %s\n' "$*" >> "$TEST_LOG" ;;
  *) echo "Unexpected git command: $*" >&2; exit 90 ;;
esac
MOCK
cat > "$test_dir/bin/docker" <<'MOCK'
#!/usr/bin/env bash
printf 'docker %s\n' "$*" >> "$TEST_LOG"
if [[ "${TEST_FAIL:-}" = build && "$*" = 'compose build' ]]; then exit 1; fi
if [[ "${TEST_FAIL:-}" = health && "$*" = *'/api/system/health' ]]; then exit 1; fi
MOCK
cat > "$test_dir/bin/flock" <<'MOCK'
#!/usr/bin/env bash
[[ "${TEST_LOCKED:-}" != yes ]]
MOCK
chmod +x "$test_dir/bin/"*
export PATH="$test_dir/bin:$PATH"

run_deploy() {
  bash "$script_dir/deploy.sh" "$TEST_SHA" "$TEST_PROJECT" example/kanban > "$test_dir/output" 2>&1
}
reset_case() {
  unset TEST_DIRTY TEST_ORIGIN TEST_LATEST TEST_FAIL TEST_LOCKED
  : > "$TEST_LOG"
  rm -f "$TEST_PROJECT/state/"*
}
expect_failure() {
  if run_deploy; then cat "$test_dir/output"; echo 'Expected failure' >&2; exit 1; fi
  [[ ! -f "$TEST_PROJECT/state/deployed.sha" ]]
}

reset_case
printf '%s\n' bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb > "$TEST_PROJECT/state/deployed.sha"
run_deploy
[[ "$(cat "$TEST_PROJECT/state/deployed.sha")" = "$TEST_SHA" ]]
[[ "$(cat "$TEST_PROJECT/state/previous.sha")" = bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb ]]
grep -q 'compose up .*--wait' "$TEST_LOG"
grep -q '/api/system/health' "$TEST_LOG"
echo 'PASS: success records verified SHA and previous version'

reset_case
export TEST_LATEST=bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb
run_deploy
if grep -q 'compose build' "$TEST_LOG"; then echo 'Stale CI started a build' >&2; exit 1; fi
[[ ! -f "$TEST_PROJECT/state/deployed.sha" ]]
echo 'PASS: superseded CI cannot deploy'

reset_case
export TEST_DIRTY=' M tracked-file'
expect_failure
[[ ! -s "$TEST_LOG" ]]
echo 'PASS: local server changes prevent deployment'

reset_case
export TEST_ORIGIN=https://github.com/other/repo.git
expect_failure
echo 'PASS: wrong server repository is rejected'

reset_case
export TEST_LOCKED=yes
expect_failure
echo 'PASS: concurrent deployment is rejected'

reset_case
export TEST_FAIL=build
expect_failure
if grep -q 'compose up' "$TEST_LOG"; then echo 'Failed build replaced containers' >&2; exit 1; fi
echo 'PASS: failed build does not replace running containers'

reset_case
export TEST_FAIL=health
expect_failure
echo 'PASS: failed health check cannot record a successful deployment'
