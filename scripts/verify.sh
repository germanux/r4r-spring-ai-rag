#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
mode="${1:-all}"

verify_java() {
  command -v java >/dev/null 2>&1 || { printf 'java not found\n' >&2; return 1; }
  command -v mvn >/dev/null 2>&1 || { printf 'mvn not found\n' >&2; return 1; }
  java_version="$(java -XshowSettings:properties -version 2>&1 | awk -F'= ' '/java.version =/{print $2; exit}')"
  [[ "$java_version" == 21* ]] || {
    printf 'Java 21 required; found %s\n' "$java_version" >&2
    return 1
  }
  mvn test
}

verify_python() {
  python_bin="python3"
  [[ -x .venv/bin/python ]] && python_bin=".venv/bin/python"
  PYTHONPATH=tools/orchestrator/src "$python_bin" -m unittest discover -s tools/orchestrator/tests -v
}

case "$mode" in
  java) verify_java ;;
  python) verify_python ;;
  all) verify_java; verify_python ;;
  *) printf 'Usage: %s {java|python|all}\n' "$0" >&2; exit 2 ;;
esac
