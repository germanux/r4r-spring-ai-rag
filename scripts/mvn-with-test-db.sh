#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

if (( $# == 0 )); then
  echo "Usage: $0 <maven arguments...>" >&2
  echo "Example: $0 install" >&2
  exit 2
fi

# Spring importa docker-spring/.env; los tests usan exclusivamente la BD de Synology.
exec mvn "$@"
