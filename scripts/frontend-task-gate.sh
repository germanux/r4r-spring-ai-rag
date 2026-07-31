#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FRONTEND="$ROOT/frontend"
TASK="${1:-all}"

fail() {
  printf '%s\n' "$*" >&2
  exit 3
}

require_file() {
  [[ -f "$1" ]] || fail "Required frontend artifact is missing: ${1#$ROOT/}"
}

require_workspace() {
  if [[ ! -f "$FRONTEND/package.json" ]]; then
    printf '%s\n' \
      "Angular workspace has not been created yet." \
      "Create it with: ./scripts/create-angular17-frontend.sh" >&2
    exit 3
  fi

  node - "$FRONTEND/package.json" <<'NODE'
const fs = require('fs');
const file = process.argv[2];
const pkg = JSON.parse(fs.readFileSync(file, 'utf8'));
const raw = pkg.dependencies?.['@angular/core'] ?? pkg.devDependencies?.['@angular/core'] ?? '';
const version = String(raw).replace(/^[^0-9]*/, '');
if (!version.startsWith('17.')) {
  console.error(`Angular 17 required, found ${version || '<missing>'}`);
  process.exit(4);
}
NODE
}

install_if_needed() {
  if [[ ! -d "$FRONTEND/node_modules" ]]; then
    if [[ -f "$FRONTEND/package-lock.json" ]]; then
      npm --prefix "$FRONTEND" ci --no-audit --no-fund
    else
      npm --prefix "$FRONTEND" install --no-audit --no-fund
    fi
  fi
}

build_gate() {
  require_workspace
  install_if_needed
  npm --prefix "$FRONTEND" run build
}

unit_gate() {
  node - "$FRONTEND/package.json" <<'NODE'
const fs = require('fs');
const pkg = JSON.parse(fs.readFileSync(process.argv[2], 'utf8'));
if (pkg.scripts?.['test:ci']) process.exit(0);
console.error('Missing npm script: test:ci');
process.exit(3);
NODE
  npm --prefix "$FRONTEND" run test:ci
}

case "$TASK" in
  task-fe-01-angular17-bootstrap)
    # FE-01 creates the workspace. Once it exists, validate only Angular 17 and build.
    build_gate
    ;;
  task-fe-02-rag-client)
    require_file "$FRONTEND/src/app/core/rag/rag-api.service.ts"
    require_file "$FRONTEND/src/app/core/rag/rag.models.ts"
    build_gate
    unit_gate
    ;;
  task-fe-03-rag-ui)
    require_file "$FRONTEND/src/app/features/rag/rag-page.component.ts"
    build_gate
    unit_gate
    ;;
  task-fe-04-playwright)
    require_file "$FRONTEND/playwright.config.ts"
    build_gate
    unit_gate
    npm --prefix "$FRONTEND" run e2e
    ;;
  all)
    require_file "$FRONTEND/playwright.config.ts"
    build_gate
    unit_gate
    npm --prefix "$FRONTEND" run e2e
    ;;
  *)
    echo "Usage: $0 {task-fe-01-angular17-bootstrap|task-fe-02-rag-client|task-fe-03-rag-ui|task-fe-04-playwright|all}" >&2
    exit 2
    ;;
esac
