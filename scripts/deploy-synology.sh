#!/usr/bin/env bash
set -euo pipefail

# ============================================================
# CONFIGURACIÓN SYNOLOGY
# ============================================================

NAS_IP="192.168.1.203"
NAS_USER="capi"
NAS_DIR="/volume1/docker/r4r"

# ============================================================
# CONFIGURACIÓN LOCAL
# ============================================================

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REMOTE="${NAS_USER}@${NAS_IP}"

COMPOSE="$ROOT/docker-spring/docker-compose.yml"
ENV_BASE="$ROOT/docker-spring/.env.base"
PRIVATE_ENV="$ROOT/docker-spring/.env"

cd "$ROOT"

# Deploy both local configuration files; never print or version private values.
for config_file in "$ENV_BASE" "$PRIVATE_ENV"; do
  [[ -f "$config_file" && -r "$config_file" ]] || {
    echo "ERROR: falta el archivo de configuración legible: $config_file" >&2
    exit 1
  }
done
[[ "$(sed -n 's/^RAG_EMBEDDING_MODEL=//p' docker-spring/.env.base)" == qwen3-embedding:0.6b ]]
[[ "$(sed -n 's/^RAG_EMBEDDING_DIMENSIONS=//p' docker-spring/.env.base)" == 1024 ]]

echo "=========================================="
echo " R4R - Despliegue Spring Boot en Synology"
echo "=========================================="

# ============================================================
# 1. COMPILAR Y GENERAR IMAGEN
# ============================================================

echo
echo "==> 1. Compilando y generando imagen Docker..."

"$ROOT/scripts/export-docker-spring.sh"

# ============================================================
# 2. LEER VERSIÓN DESDE MAVEN
# ============================================================

VERSION="$(mvn -q help:evaluate \
  -Dexpression=project.version \
  -DforceStdout)"

# Validación para usar la versión en nombres y comandos.
if [[ ! "$VERSION" =~ ^[0-9A-Za-z][0-9A-Za-z._-]*$ ]]; then
  echo "ERROR: versión Maven no válida: $VERSION" >&2
  exit 1
fi

IMAGE="r4r-api:$VERSION"
TAR_NAME="r4r-api-${VERSION}.tar"
TAR="$ROOT/docker-spring/$TAR_NAME"

[[ -s "$TAR" ]] || {
  echo "ERROR: no existe $TAR" >&2
  exit 1
}

[[ -f "$COMPOSE" ]] || {
  echo "ERROR: no existe $COMPOSE" >&2
  exit 1
}

echo "Versión: $VERSION"
echo "Imagen:  $IMAGE"

# ============================================================
# 3. ACTUALIZAR COMPOSE LOCAL
# ============================================================

echo
echo "==> 2. Actualizando versión de la API en Compose..."

# Solo modifica la línea image: r4r-api:<versión>
grep -Eq '^[[:space:]]*image:[[:space:]]*r4r-api:[^[:space:]]+' "$COMPOSE" || {
  echo "ERROR: no se encuentra la imagen r4r-api en Compose." >&2
  exit 1
}

sed -i -E \
  "s|^([[:space:]]*image:[[:space:]]*)r4r-api:[^[:space:]]+|\1$IMAGE|" \
  "$COMPOSE"

grep -Fq "image: $IMAGE" "$COMPOSE" || {
  echo "ERROR: no se pudo actualizar Compose." >&2
  exit 1
}

# ============================================================
# 4. TRANSFERIR ARCHIVOS
# ============================================================

echo
echo "==> 3. Preparando transferencia al Synology..."

# Reutilizar una única sesión SSH para mktemp, scp y despliegue.
# El socket es privado de esta ejecución y se cierra al salir.
SSH_CONTROL_DIR="$(mktemp -d)"
SSH_CONTROL_PATH="$SSH_CONTROL_DIR/master"
SSH_OPTIONS=(
  -o ControlMaster=auto
  -o ControlPersist=10m
  -o "ControlPath=$SSH_CONTROL_PATH"
)

cleanup_ssh() {
  # Remove the private upload even when validation or deployment fails.
  if [[ -n "${STAGE:-}" ]]; then
    ssh "${SSH_OPTIONS[@]}" -o BatchMode=yes -o ConnectTimeout=5 "$REMOTE" \
      "rm -f -- '$STAGE/.env'" >/dev/null 2>&1 || true
  fi
  ssh -o BatchMode=yes -S "$SSH_CONTROL_PATH" -O exit "$REMOTE" >/dev/null 2>&1 || true
  rmdir "$SSH_CONTROL_DIR" 2>/dev/null || true
}
trap cleanup_ssh EXIT

# Directorio temporal único dentro del home del usuario NAS.
STAGE="$(ssh "${SSH_OPTIONS[@]}" "$REMOTE" \
  'mktemp -d "$HOME/.r4r-deploy.XXXXXXXX"')"

echo "Destino: $REMOTE"
echo "Temporal: $STAGE"

# mktemp creates a private remote directory (0700), including during transfer.
scp -O "${SSH_OPTIONS[@]}" "$TAR" "$COMPOSE" "$ENV_BASE" "$PRIVATE_ENV" "$REMOTE:$STAGE/"

# ============================================================
# 5. IMPORTAR Y DESPLEGAR POR SSH
# ============================================================

echo
echo "==> 4. Importando imagen y actualizando Spring..."

BACKUP_DATE="$(date '+%Y%m%d-%H%M%S')"

# -tt permite introducir la contraseña de sudo.
ssh "${SSH_OPTIONS[@]}" -tt "$REMOTE" "
  set -e

  export PATH=/usr/local/bin:/var/packages/ContainerManager/target/usr/bin:\$PATH

  DOCKER_BIN=\$(command -v docker)
  COMPOSE_BIN=\$(command -v docker-compose)

  cd '$NAS_DIR'

  test -f '$STAGE/.env.base'
  test -f '$STAGE/.env'
  chmod 600 '$STAGE/.env'

  echo '==> Autenticación sudo...'
  sudo -v

  # Validate the uploaded pair before replacing any active configuration.
  # Keep relative Compose paths anchored to the real NAS project directory.
  echo '==> Validando Compose y configuración transferida...'
  sudo \"\$COMPOSE_BIN\" \
    --project-directory '$NAS_DIR' \
    --env-file '$STAGE/.env.base' --env-file '$STAGE/.env' \
    -f '$STAGE/docker-compose.yml' \
    config --quiet

  # Importar la nueva imagen.
  echo '==> Importando Docker...'
  sudo \"\$DOCKER_BIN\" load -i '$STAGE/$TAR_NAME'

  # Guardar una copia de seguridad del Compose anterior.
  sudo cp -p docker-compose.yml \
    'docker-compose.yml.bak.$BACKUP_DATE'

  # Actualizar Compose.
  sudo cp '$STAGE/docker-compose.yml' docker-compose.yml

  # Replace NAS settings with the validated local files. No secret backups.
  sudo install -m 644 '$STAGE/.env.base' .env.base
  sudo install -m 600 '$STAGE/.env' .env
  rm '$STAGE/.env'

  # Crear servicios nuevos y actualizar solo los que hayan cambiado.
  echo '==> Desplegando proyecto R4R...'

  sudo \"\$COMPOSE_BIN\" \
    --env-file .env.base --env-file .env \
    -f docker-compose.yml \
    up -d

  # La etiqueta Maven puede reutilizarse; garantizar que la API toma la
  # imagen recién importada, sin recrear PostgreSQL ni Ollama.
  sudo \"\$COMPOSE_BIN\" \
    --env-file .env.base --env-file .env \
    -f docker-compose.yml \
    up -d --no-deps --force-recreate api

  # Conservar la imagen TAR en el directorio R4R.
  sudo mv '$STAGE/$TAR_NAME' '$NAS_DIR/$TAR_NAME'

  # Archivar historicos; conservar el Compose, el TAR actual y .env activos.
  # Solo se ejecuta despues de un despliegue correcto.
  echo '==> Archivando historicos de Compose e imagenes TAR...'
  sudo mkdir -p backup/old-docker-compose-yml backup/old-r4r-api-tar

  for old_compose in docker-compose.yml.bak.*; do
    test -f \"\$old_compose\" || continue
    if test -e \"backup/old-docker-compose-yml/\$old_compose\"; then
      echo \"Se conserva en raiz (ya existe archivado): \$old_compose\"
    else
      sudo mv \"\$old_compose\" backup/old-docker-compose-yml/
    fi
  done

  for old_tar in r4r-api-*.tar; do
    test -f \"\$old_tar\" || continue
    test \"\$old_tar\" = '$TAR_NAME' && continue
    if test -e \"backup/old-r4r-api-tar/\$old_tar\"; then
      echo \"Se conserva en raiz (ya existe archivado): \$old_tar\"
    else
      sudo mv \"\$old_tar\" backup/old-r4r-api-tar/
    fi
  done

  # Limpiar únicamente los archivos temporales de este despliegue.
  rm '$STAGE/docker-compose.yml' '$STAGE/.env.base'
  rmdir '$STAGE'

  # Estado final.
  echo
  echo '==> Estado de los contenedores R4R:'
  sudo \"\$COMPOSE_BIN\" --env-file .env.base --env-file .env -f docker-compose.yml ps

  echo
  echo '==> Últimos logs:'
  sudo \"\$DOCKER_BIN\" logs --tail 80 r4r-api
"

# Final stage: the backend deployment and its remote checks have already completed.
echo
echo "==> Backend desplegado. Compilando Angular y distribuyendo el frontend (SMB al final)..."
if ! bash "$ROOT/scripts/build-frontend-angular.sh"; then
  echo "ERROR: la distribución del frontend no ha terminado. El backend ya está desplegado y no se revierte." >&2
  exit 1
fi

echo
echo "=========================================="
echo " Despliegue finalizado: $IMAGE"
echo "=========================================="

