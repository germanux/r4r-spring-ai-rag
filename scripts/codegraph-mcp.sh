#!/usr/bin/env bash
set -Eeuo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
exec "${R4R_CODEGRAPH_BIN:-codegraph}" serve --mcp
