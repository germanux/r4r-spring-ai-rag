#!/usr/bin/env bash
set -Eeuo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FRONTEND="$ROOT/frontend"
TASK="${1:-all}"
require_file() { [[ -f "$1" ]] || { echo "Required frontend artifact is missing: ${1#$ROOT/}" >&2; exit 3; }; }
require_base() {
  require_file "$FRONTEND/package.json"
  require_file "$FRONTEND/package-lock.json"
  require_file "$FRONTEND/angular.json"
  require_file "$FRONTEND/src/main.ts"
  node - "$FRONTEND/package.json" <<'NODE'
const fs=require('fs'); const p=JSON.parse(fs.readFileSync(process.argv[2],'utf8'));
const v=(p.dependencies?.['@angular/core']||p.devDependencies?.['@angular/core']||'').replace(/^[^0-9]*/, '');
if (!v.startsWith('17.')) { console.error(`Angular 17 required, found ${v||'<missing>'}`); process.exit(4); }
NODE
}
install_if_needed() {
  [[ -d "$FRONTEND/node_modules" ]] || npm --prefix "$FRONTEND" ci --no-audit --no-fund
}
base_gate() { require_base; install_if_needed; npm --prefix "$FRONTEND" run build; npm --prefix "$FRONTEND" run test:ci; }
case "$TASK" in
  task-fe-01-angular17-bootstrap) base_gate ;;
  task-fe-02-rag-client)
    require_file "$FRONTEND/src/app/core/rag/rag-api.service.ts"
    require_file "$FRONTEND/src/app/core/rag/rag.models.ts"
    base_gate
    ;;
  task-fe-03-rag-ui)
    require_file "$FRONTEND/src/app/features/rag/rag-page.component.ts"
    base_gate
    ;;
  task-fe-04-playwright)
    require_file "$FRONTEND/playwright.config.ts"
    base_gate
    npm --prefix "$FRONTEND" run e2e
    ;;
  all)
    require_file "$FRONTEND/playwright.config.ts"
    base_gate
    npm --prefix "$FRONTEND" run e2e
    ;;
  *) echo "Usage: $0 {task-fe-01-angular17-bootstrap|task-fe-02-rag-client|task-fe-03-rag-ui|task-fe-04-playwright|all}" >&2; exit 2 ;;
esac
