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

cd "$ROOT"

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

# Directorio temporal único dentro del home del usuario NAS.
STAGE="$(ssh "$REMOTE" \
  'mktemp -d "$HOME/.r4r-deploy.XXXXXXXX"')"

echo "Destino: $REMOTE"
echo "Temporal: $STAGE"

scp -O "$TAR" "$COMPOSE" "$REMOTE:$STAGE/"

# ============================================================
# 5. IMPORTAR Y DESPLEGAR POR SSH
# ============================================================

echo
echo "==> 4. Importando imagen y actualizando Spring..."

BACKUP_DATE="$(date '+%Y%m%d-%H%M%S')"

# -tt permite introducir la contraseña de sudo.
ssh -tt "$REMOTE" "
  set -e

  export PATH=/usr/local/bin:/var/packages/ContainerManager/target/usr/bin:\$PATH

  DOCKER_BIN=\$(command -v docker)
  COMPOSE_BIN=\$(command -v docker-compose)

  cd '$NAS_DIR'

  # Comprobar que existe la configuración del NAS.
  test -f .env

  echo '==> Autenticación sudo...'
  sudo -v

  # Importar la nueva imagen.
  echo '==> Importando Docker...'
  sudo \"\$DOCKER_BIN\" load -i '$STAGE/$TAR_NAME'

  # Guardar una copia de seguridad del Compose anterior.
  sudo cp -p docker-compose.yml \
    'docker-compose.yml.bak.$BACKUP_DATE'

  # Actualizar Compose.
  sudo cp '$STAGE/docker-compose.yml' docker-compose.yml

  # Validar antes de desplegar el proyecto.
  echo '==> Validando Compose...'

  if ! sudo \"\$COMPOSE_BIN\" \
    --env-file .env \
    -f docker-compose.yml \
    config --quiet; then

    echo 'ERROR: Compose inválido. Restaurando versión anterior.'

    sudo cp -p \
      'docker-compose.yml.bak.$BACKUP_DATE' \
      docker-compose.yml

    exit 1
  fi

  # Crear servicios nuevos y actualizar solo los que hayan cambiado.
  echo '==> Desplegando proyecto R4R...'

  sudo \"\$COMPOSE_BIN\" \
    --env-file .env \
    -f docker-compose.yml \
    up -d

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
  rm '$STAGE/docker-compose.yml'
  rmdir '$STAGE'

  # Estado final.
  echo
  echo '==> Estado de los contenedores R4R:'
  sudo \"\$COMPOSE_BIN\" --env-file .env -f docker-compose.yml ps

  echo
  echo '==> Últimos logs:'
  sudo \"\$DOCKER_BIN\" logs --tail 80 r4r-api
"

echo
echo "=========================================="
echo " Despliegue finalizado: $IMAGE"
echo "=========================================="

