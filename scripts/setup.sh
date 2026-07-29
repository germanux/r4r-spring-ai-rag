#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

if [[ "${EUID}" -eq 0 ]]; then
  SUDO=()
else
  command -v sudo >/dev/null 2>&1 || {
    echo "sudo is required to install missing system packages." >&2
    exit 2
  }
  SUDO=(sudo)
fi

apt_install_missing() {
  command -v apt-get >/dev/null 2>&1 || {
    echo "Automatic installation currently supports Debian/Ubuntu/Zorin systems with apt-get." >&2
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
  if command -v javac >/dev/null 2>&1; then
    javac_version="$(javac -version 2>&1 || true)"
  fi
  [[ "$javac_version" == javac\ 21* ]] || packages+=(openjdk-21-jdk)

  local need_docker=false
  command -v docker >/dev/null 2>&1 || {
    packages+=(docker.io)
    need_docker=true
  }

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
      if apt-cache show docker-compose-v2 >/dev/null 2>&1; then
        packages+=(docker-compose-v2)
      elif apt-cache show docker-compose-plugin >/dev/null 2>&1; then
        packages+=(docker-compose-plugin)
      elif apt-cache show docker-compose >/dev/null 2>&1; then
        packages+=(docker-compose)
      else
        echo "No Docker Compose package is available in the configured apt repositories." >&2
        exit 2
      fi
    fi

    # Remove duplicate package names without reordering them.
    local unique=() package seen
    for package in "${packages[@]}"; do
      seen=false
      for existing in "${unique[@]:-}"; do
        [[ "$existing" == "$package" ]] && { seen=true; break; }
      done
      [[ "$seen" == false ]] && unique+=("$package")
    done

    if (( ${#unique[@]} > 0 )); then
      "${SUDO[@]}" apt-get install -y "${unique[@]}"
    fi
  fi

  if [[ "$need_docker" == true ]] && command -v systemctl >/dev/null 2>&1; then
    "${SUDO[@]}" systemctl enable --now docker
  elif command -v systemctl >/dev/null 2>&1 && ! systemctl is-active --quiet docker; then
    "${SUDO[@]}" systemctl enable --now docker
  fi
}

apt_install_missing

# Prefer Java 21 without changing the user's global alternative when possible.
if [[ -d /usr/lib/jvm/java-21-openjdk-amd64 ]]; then
  export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
  export PATH="$JAVA_HOME/bin:$PATH"
fi

for command in java javac mvn docker npm node python3 git; do
  command -v "$command" >/dev/null 2>&1 || {
    echo "Required command is still unavailable after installation: $command" >&2
    exit 2
  }
done

JAVA_VERSION="$(javac -version 2>&1)"
[[ "$JAVA_VERSION" == javac\ 21* ]] || {
  echo "Java 21 is required; found: $JAVA_VERSION" >&2
  exit 2
}

if ! docker compose version >/dev/null 2>&1 && ! command -v docker-compose >/dev/null 2>&1; then
  echo "Docker Compose is unavailable after installation." >&2
  exit 2
fi

# Add the current user to the docker group for future shells. The current run can
# still use sudo through scripts/db.sh until the user logs out and back in.
if getent group docker >/dev/null 2>&1 && ! id -nG "$USER" | tr ' ' '\n' | grep -qx docker; then
  echo "Adding $USER to the docker group (effective after login/logout)..."
  "${SUDO[@]}" usermod -aG docker "$USER"
fi

[[ -f .env ]] || cp .env.example .env
mkdir -p docker-postgres/data/app docker-postgres/backups runtime/runs runtime/locks

npm --prefix .opencode install
python3 -m venv py-codex-agent/.venv
py-codex-agent/.venv/bin/python -m pip install --upgrade pip
py-codex-agent/.venv/bin/python -m pip install -e py-codex-agent

if command -v codegraph >/dev/null 2>&1; then
  [[ -d .codegraph ]] || codegraph init
  codegraph sync . --quiet || echo "Warning: CodeGraph sync failed" >&2
else
  echo "Warning: CodeGraph is not installed; OpenCode can still run without it" >&2
fi

./scripts/db.sh up
./scripts/verify.sh unit

echo "Setup complete."
echo "Docker and other missing system packages were installed when necessary."
echo "If docker required group membership, log out and back in to stop using sudo for Docker."
echo "Edit .env for machine-specific overrides, then run ./scripts/verify.sh all"
