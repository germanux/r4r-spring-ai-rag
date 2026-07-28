#!/usr/bin/env bash
set -euo pipefail

command -v apt-get >/dev/null 2>&1 || {
  printf 'This installer supports apt-based Ubuntu/Zorin systems only.\n' >&2
  exit 1
}

printf 'This optional script uses sudo to install PostgreSQL and pgvector.\n'
read -r -p 'Continue? [y/N] ' answer
[[ "$answer" =~ ^[Yy]$ ]] || exit 0

sudo apt-get update
pgvector_package=""
for candidate in postgresql-17-pgvector postgresql-16-pgvector postgresql-15-pgvector postgresql-pgvector; do
  if apt-cache show "$candidate" >/dev/null 2>&1; then
    pgvector_package="$candidate"
    break
  fi
done

if [[ -z "$pgvector_package" ]]; then
  printf 'No pgvector package was found in configured apt repositories.\n' >&2
  printf 'Use ./scripts/db/postgres.sh up instead.\n' >&2
  exit 2
fi

sudo apt-get install -y postgresql postgresql-contrib "$pgvector_package"
printf 'Installed PostgreSQL with package %s.\n' "$pgvector_package"
