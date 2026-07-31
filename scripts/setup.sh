#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
[[ -f .env ]] || cp .env.example .env
set -a
# shellcheck disable=SC1091
source .env
set +a

if [[ -f .env.r4r.local ]]; then
  set -a
  # shellcheck disable=SC1091
  source .env.r4r.local
  set +a
fi

if [[ "${EUID}" -eq 0 ]]; then
  SUDO=()
else
  command -v sudo >/dev/null 2>&1 || { echo "sudo is required to install missing system packages." >&2; exit 2; }
  SUDO=(sudo)
fi

apt_install_missing() {
  command -v apt-get >/dev/null 2>&1 || {
    echo "Automatic installation supports Debian/Ubuntu/Zorin systems with apt-get." >&2
    exit 2
  }
  local packages=()
  command -v git >/dev/null 2>&1 || packages+=(git)
  command -v mvn >/dev/null 2>&1 || packages+=(maven)
  command -v python3 >/dev/null 2>&1 || packages+=(python3)
  python3 -c 'import venv' >/dev/null 2>&1 || packages+=(python3-venv)
  python3 -m pip --version >/dev/null 2>&1 || packages+=(python3-pip)
  command -v node >/dev/null 2>&1 || packages+=(nodejs)
  command -v npm >/dev/null 2>&1 || packages+=(npm)
  local javac_version=""
  command -v javac >/dev/null 2>&1 && javac_version="$(javac -version 2>&1 || true)"
  [[ "$javac_version" == javac\ 21* ]] || packages+=(openjdk-21-jdk)
  local need_docker=false
  command -v docker >/dev/null 2>&1 || { packages+=(docker.io); need_docker=true; }
  local compose_available=false
  if command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
    compose_available=true
  elif command -v docker-compose >/dev/null 2>&1; then
    compose_available=true
  fi
  if (( ${#packages[@]} > 0 )) || [[ "$compose_available" == false ]]; then
    echo "Installing missing system dependencies with sudo/apt..."
    "${SUDO[@]}" apt-get update
    if [[ "$compose_available" == false ]]; then
      if apt-cache show docker-compose-v2 >/dev/null 2>&1; then packages+=(docker-compose-v2)
      elif apt-cache show docker-compose-plugin >/dev/null 2>&1; then packages+=(docker-compose-plugin)
      elif apt-cache show docker-compose >/dev/null 2>&1; then packages+=(docker-compose)
      else echo "No Docker Compose package is available." >&2; exit 2
      fi
    fi
    local unique=() package existing seen
    for package in "${packages[@]}"; do
      seen=false
      for existing in "${unique[@]:-}"; do [[ "$existing" == "$package" ]] && { seen=true; break; }; done
      [[ "$seen" == false ]] && unique+=("$package")
    done
    (( ${#unique[@]} == 0 )) || "${SUDO[@]}" apt-get install -y "${unique[@]}"
  fi
  if command -v systemctl >/dev/null 2>&1 && ! systemctl is-active --quiet docker; then
    "${SUDO[@]}" systemctl enable --now docker
  fi
}

install_npm_cli() {
  local binary="$1" package="$2"
  command -v "$binary" >/dev/null 2>&1 && return 0
  echo "Installing $binary from npm package $package..."
  npm install -g "$package" || "${SUDO[@]}" npm install -g "$package"
  command -v "$binary" >/dev/null 2>&1 || { echo "Failed to install $binary" >&2; exit 2; }
}

ensure_opencode_capabilities() {
  local binary="${R4R_OPENCODE_BIN:-opencode}" package="${R4R_OPENCODE_NPM_PACKAGE:-opencode-ai}"
  if ! "$binary" run --help 2>&1 | grep -q -- "--format"; then
    echo "Updating $binary because the required non-interactive flags are missing..."
    npm install -g "$package" || "${SUDO[@]}" npm install -g "$package"
  fi
  "$binary" run --help 2>&1 | grep -q -- "--format" || { echo "$binary lacks --format" >&2; exit 2; }
  "$binary" run --help 2>&1 | grep -q -- "--auto" || { echo "$binary lacks --auto" >&2; exit 2; }
}

ensure_codex_capabilities() {
  local binary="${R4R_CODEX_BIN:-codex}" package="${R4R_CODEX_NPM_PACKAGE:-@openai/codex}"
  if ! "$binary" exec --help 2>&1 | grep -q -- "--output-schema"; then
    echo "Updating $binary because --output-schema is missing..."
    npm install -g "$package" || "${SUDO[@]}" npm install -g "$package"
  fi
  "$binary" exec --help 2>&1 | grep -q -- "--output-schema" || { echo "$binary lacks --output-schema" >&2; exit 2; }
  "$binary" exec --help 2>&1 | grep -q -- "--sandbox" || { echo "$binary lacks --sandbox" >&2; exit 2; }
}

ensure_codegraph_capabilities() {
  local package="${R4R_CODEGRAPH_NPM_PACKAGE:-@colbymchenry/codegraph}"
  if ! codegraph serve --help 2>&1 | grep -q -- "--mcp"; then
    echo "Updating codegraph because MCP serving is missing..."
    npm install -g "$package" || "${SUDO[@]}" npm install -g "$package"
  fi
  codegraph serve --help 2>&1 | grep -q -- "--mcp" || { echo "codegraph lacks serve --mcp" >&2; exit 2; }
}

ensure_local_env_var() {
  local key="$1"
  local default_value="$2"
  local local_env="$ROOT/.env.r4r.local"

  touch "$local_env"
  chmod 600 "$local_env" 2>/dev/null || true

  if grep -Eq "^[[:space:]]*(export[[:space:]]+)?${key}=" "$local_env"; then
    return 0
  fi

  printf '%s=%q\n' "$key" "$default_value" >> "$local_env"
}

ensure_worker_git_identities() {
  ensure_local_env_var \
    R4R_PC_GIT_AUTHOR_NAME \
    "${R4R_PC_GIT_AUTHOR_NAME:-GermanGPT PC Agent}"
  ensure_local_env_var \
    R4R_PC_GIT_AUTHOR_EMAIL \
    "${R4R_PC_GIT_AUTHOR_EMAIL:-germanux@gmail.com}"
  ensure_local_env_var \
    R4R_LP_GIT_AUTHOR_NAME \
    "${R4R_LP_GIT_AUTHOR_NAME:-GermanGPT LP Agent}"
  ensure_local_env_var \
    R4R_LP_GIT_AUTHOR_EMAIL \
    "${R4R_LP_GIT_AUTHOR_EMAIL:-germanux@gmail.com}"

  set -a
  # shellcheck disable=SC1091
  source "$ROOT/.env.r4r.local"
  set +a
}


apt_install_missing
if [[ -d /usr/lib/jvm/java-21-openjdk-amd64 ]]; then
  export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
  export PATH="$JAVA_HOME/bin:$PATH"
fi
for command in java javac mvn docker npm node python3 git; do
  command -v "$command" >/dev/null 2>&1 || { echo "Required command unavailable: $command" >&2; exit 2; }
done

ensure_worker_git_identities
[[ "$(javac -version 2>&1)" == javac\ 21* ]] || { echo "Java 21 is required." >&2; exit 2; }
if ! docker compose version >/dev/null 2>&1 && ! command -v docker-compose >/dev/null 2>&1; then
  echo "Docker Compose is unavailable." >&2; exit 2
fi
if getent group docker >/dev/null 2>&1 && ! id -nG "$USER" | tr ' ' '\n' | grep -qx docker; then
  echo "Adding $USER to docker group (effective after login/logout)..."
  "${SUDO[@]}" usermod -aG docker "$USER"
fi

install_npm_cli "${R4R_OPENCODE_BIN:-opencode}" "${R4R_OPENCODE_NPM_PACKAGE:-opencode-ai}"
install_npm_cli "${R4R_CODEX_BIN:-codex}" "${R4R_CODEX_NPM_PACKAGE:-@openai/codex}"
install_npm_cli codegraph "${R4R_CODEGRAPH_NPM_PACKAGE:-@colbymchenry/codegraph}"
ensure_opencode_capabilities
ensure_codex_capabilities
ensure_codegraph_capabilities

mkdir -p docker-postgres/data/app docker-postgres/backups runtime/runs runtime/locks
python3 -m venv py-codex-agent/.venv
py-codex-agent/.venv/bin/python -m pip install --upgrade pip
py-codex-agent/.venv/bin/python -m pip install -e py-codex-agent

if [[ -d .codegraph ]]; then
  codegraph sync . --quiet || echo "Warning: CodeGraph sync failed" >&2
else
  codegraph init .
fi

./scripts/db.sh up
./scripts/verify.sh unit

echo "Setup complete."
printf 'PC Git author: %s <%s>\n' \
  "${R4R_PC_GIT_AUTHOR_NAME:-GermanGPT PC Agent}" \
  "${R4R_PC_GIT_AUTHOR_EMAIL:-germanux@gmail.com}"
printf 'LP Git author: %s <%s>\n' \
  "${R4R_LP_GIT_AUTHOR_NAME:-GermanGPT LP Agent}" \
  "${R4R_LP_GIT_AUTHOR_EMAIL:-germanux@gmail.com}"
echo "PostgreSQL runs only in Docker."
echo "If Docker group membership was added, log out and back in to avoid sudo fallback."
if ! "${R4R_CODEX_BIN:-codex}" login status >/dev/null 2>&1; then
  echo "Codex CLI is installed but may not be authenticated. Run: codex login"
fi
echo "Next: ./scripts/verify.sh all && ./scripts/run-codex-agent.sh"
