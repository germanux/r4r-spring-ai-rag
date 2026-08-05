#!/usr/bin/env bash
set -Eeuo pipefail

# ============================================================================
# CONFIGURACION: no se pasan parametros al script.
# ============================================================================
# Remoto de Google Drive que ya existe en ~/.config/rclone/rclone.conf.
# No se crea un segundo remoto con otro nombre.
RCLONE_REMOTE="Riansares4R"

# Este directorio deja de estar gestionado por Insync, pero se conserva como
# espejo local de Drive para no alterar el resto del sistema R4R.
LOCAL_SYNC_DIR="${HOME}/Insync/riansares4r@gmail.com/Google Drive/Agentes R4R/r4r-ring-agent.git"
REMOTE_SYNC_DIR="Agentes R4R/r4r-ring-agent.git"

# Worktree existente alimentado por r4r-drive-import-safe.py. Rclone NO debe
# sincronizar directamente este directorio ni sustituir la importación segura.
LOCAL_GIT_WORKTREE="${HOME}/Desarrollo/r4r-google-drive.git"

SYNC_INTERVAL="1min"
MAX_DELETE_PERCENT="15"
MIN_RCLONE_VERSION="1.71.0"
AUTO_INSTALL_RCLONE="true"
INITIAL_RESYNC_MODE="newer"

UNIT_NAME="r4r-rclone-bisync"
INSTALL_PATH="${HOME}/.local/bin/${UNIT_NAME}"
CONFIG_DIR="${HOME}/.config/${UNIT_NAME}"
STATE_DIR="${HOME}/.local/state/${UNIT_NAME}"
RUNTIME_DIR="${HOME}/Desarrollo/.r4r-runtime"
FILTERS_FILE="${CONFIG_DIR}/filters.txt"
BOOTSTRAP_MARKER="${STATE_DIR}/bootstrap-completed"
LOCK_FILE="${RUNTIME_DIR}/${UNIT_NAME}.lock"
# Rclone exige que cada backup esté en el mismo sistema que su ruta de origen
# y que no se solape con ella. Por eso son directorios separados y nuevos; no
# son raíces de sincronización ni sustituyen ningún directorio actual.
LOCAL_BACKUP_DIR="${RUNTIME_DIR}/rclone-backups/r4r-ring-agent-local"
REMOTE_BACKUP_DIR="Agentes R4R/.rclone-backups/r4r-ring-agent-remote"
ACCESS_TEST_FILE="RCLONE_TEST"

REMOTE_PATH="${RCLONE_REMOTE}:${REMOTE_SYNC_DIR}"
REMOTE_BACKUP_PATH="${RCLONE_REMOTE}:${REMOTE_BACKUP_DIR}"
SERVICE_FILE="${HOME}/.config/systemd/user/${UNIT_NAME}.service"
TIMER_FILE="${HOME}/.config/systemd/user/${UNIT_NAME}.timer"

log() {
  printf '[%s] %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*"
}

die() {
  printf 'ERROR: %s\n' "$*" >&2
  exit 1
}

command_exists() {
  command -v "$1" >/dev/null 2>&1
}

insync_is_running() {
  pgrep -x insync >/dev/null 2>&1 ||
    pgrep -x insync-headless >/dev/null 2>&1
}

version_at_least() {
  local current="$1"
  local required="$2"
  [[ "$(printf '%s\n%s\n' "$required" "$current" | sort -V | head -n1)" == "$required" ]]
}

install_rclone_if_needed() {
  local current_version=""
  local install_required="false"
  local temp_dir=""

  if command_exists rclone; then
    current_version="$(rclone version | sed -n '1s/^rclone v//p')"
    if ! version_at_least "$current_version" "$MIN_RCLONE_VERSION"; then
      log "rclone ${current_version} es demasiado antiguo; se requiere ${MIN_RCLONE_VERSION} o posterior."
      install_required="true"
    fi
  else
    install_required="true"
  fi

  [[ "$install_required" == "true" ]] || return 0
  [[ "$AUTO_INSTALL_RCLONE" == "true" ]] || die "Instala rclone ${MIN_RCLONE_VERSION} o posterior."
  command_exists curl || die "Falta curl. Instálalo y vuelve a ejecutar el script."
  command_exists sudo || die "Falta sudo; no puedo instalar rclone automáticamente."

  temp_dir="$(mktemp -d)"
  trap 'rm -rf -- "$temp_dir"' RETURN
  log "Descargando el instalador oficial de rclone. sudo puede pedir la contraseña."
  curl --proto '=https' --tlsv1.2 --fail --silent --show-error \
    --location https://rclone.org/install.sh \
    --output "${temp_dir}/install-rclone.sh"
  sudo bash "${temp_dir}/install-rclone.sh"
  rm -rf -- "$temp_dir"
  trap - RETURN
  command_exists rclone || die "La instalación de rclone no terminó correctamente."
  current_version="$(rclone version | sed -n '1s/^rclone v//p')"
  version_at_least "$current_version" "$MIN_RCLONE_VERSION" ||
    die "Se instaló rclone ${current_version}, pero se requiere ${MIN_RCLONE_VERSION} o posterior."
}

ensure_remote_configured() {
  local remote_type=""

  if rclone listremotes | grep -Fxq "${RCLONE_REMOTE}:"; then
    remote_type="$(
      rclone config show "$RCLONE_REMOTE" 2>/dev/null |
        sed -n 's/^[[:space:]]*type[[:space:]]*=[[:space:]]*//p' |
        head -n1
    )"
    [[ "$remote_type" == "drive" ]] ||
      die "El remoto ${RCLONE_REMOTE} existe, pero es de tipo ${remote_type:-desconocido}; se requiere drive."
    log "Reutilizando el remoto existente ${RCLONE_REMOTE} (Google Drive)."
    return 0
  fi

  printf '\nNo existe el remoto %s. Se abrirá una sola vez el asistente de rclone.\n' "$RCLONE_REMOTE"
  printf 'Crea un remoto llamado exactamente %s, de tipo drive y con acceso completo.\n' "$RCLONE_REMOTE"
  printf 'Google exige autorizarlo en el navegador. Usa un client_id propio; el compartido se retira en 2026.\n\n'
  rclone config
  rclone listremotes | grep -Fxq "${RCLONE_REMOTE}:" ||
    die "No se creó el remoto ${RCLONE_REMOTE}. Vuelve a ejecutar el script cuando esté configurado."
}

ensure_layout() {
  local sync_path
  local worktree_path
  local backup_path

  [[ -d "$LOCAL_SYNC_DIR" ]] ||
    die "No existe el directorio local actual: ${LOCAL_SYNC_DIR}"

  [[ -d "$LOCAL_GIT_WORKTREE" ]] ||
    die "No existe el worktree actual: ${LOCAL_GIT_WORKTREE}"

  sync_path="$(realpath -m "$LOCAL_SYNC_DIR")"
  worktree_path="$(realpath -m "$LOCAL_GIT_WORKTREE")"
  backup_path="$(realpath -m "$LOCAL_BACKUP_DIR")"

  [[ "$sync_path" != "$worktree_path" ]] ||
    die "LOCAL_SYNC_DIR no puede ser el worktree Git; debe seguir siendo el espejo intermedio."
  [[ "$backup_path" != "$sync_path" &&
     "$backup_path" != "${sync_path}/"* &&
     "$sync_path" != "${backup_path}/"* ]] ||
    die "LOCAL_BACKUP_DIR y LOCAL_SYNC_DIR no pueden solaparse."

  [[ "$REMOTE_BACKUP_DIR" != "$REMOTE_SYNC_DIR" &&
     "$REMOTE_BACKUP_DIR" != "${REMOTE_SYNC_DIR}/"* &&
     "$REMOTE_SYNC_DIR" != "${REMOTE_BACKUP_DIR}/"* ]] ||
    die "REMOTE_BACKUP_DIR y REMOTE_SYNC_DIR no pueden solaparse."

  mkdir -p \
    "$(dirname "$INSTALL_PATH")" \
    "$CONFIG_DIR" \
    "$STATE_DIR" \
    "$RUNTIME_DIR" \
    "$LOCAL_BACKUP_DIR" \
    "$(dirname "$SERVICE_FILE")"
}

ensure_filters() {
  local candidate="${STATE_DIR}/filters.candidate"

  # Modificar estos filtros después del bootstrap obliga a revisar y repetir
  # manualmente un --resync. El script se detiene si detecta tal cambio.
  command cat >"$candidate" <<'EOF'
# No sincronizar metadatos Git ni artefactos regenerables.
- **/.git/
- **/.gradle/
- **/node_modules/
- **/target/
- **/dist/
- **/.angular/
- **/__pycache__/
- **/.pytest_cache/
- **/.mypy_cache/
- **/.ruff_cache/
- **/*.log
- **/*.tmp
- **/*~
- **/.DS_Store
- **/Thumbs.db
EOF

  if [[ ! -e "$FILTERS_FILE" ]]; then
    install -m 0600 "$candidate" "$FILTERS_FILE"
  elif ! cmp -s "$candidate" "$FILTERS_FILE"; then
    die "Los filtros cambiaron. Revisa ${candidate} frente a ${FILTERS_FILE}; bisync exige un nuevo --resync controlado."
  fi
  rm -f -- "$candidate"
}

ensure_remote_path_exists() {
  log "Comprobando acceso a ${REMOTE_PATH}."
  rclone lsf "$REMOTE_PATH" --max-depth 1 >/dev/null ||
    die "No se puede leer ${REMOTE_PATH}. Revisa el nombre, OAuth y que la carpeta ya exista en Drive."
}

ensure_access_markers() {
  local local_marker="${LOCAL_SYNC_DIR}/${ACCESS_TEST_FILE}"

  if [[ ! -f "$local_marker" ]]; then
    printf 'R4R rclone bisync access marker. Do not delete.\n' >"$local_marker"
  fi

  if ! rclone lsf "${REMOTE_PATH}/${ACCESS_TEST_FILE}" --files-only 2>/dev/null |
      grep -Fxq "$ACCESS_TEST_FILE"; then
    rclone copyto "$local_marker" "${REMOTE_PATH}/${ACCESS_TEST_FILE}"
  fi
}

bisync_common_args() {
  local backup_suffix
  backup_suffix=".$(date '+%Y%m%d-%H%M%S')"

  BISYNC_ARGS=(
    "$LOCAL_SYNC_DIR"
    "$REMOTE_PATH"
    --filters-file "$FILTERS_FILE"
    --check-access
    --check-filename "$ACCESS_TEST_FILE"
    --check-sync true
    --create-empty-src-dirs
    --resilient
    --recover
    --retries 3
    --retries-sleep 10s
    --max-lock 2m
    --max-delete "$MAX_DELETE_PERCENT"
    --conflict-resolve none
    --conflict-loser pathname
    --backup-dir1 "$LOCAL_BACKUP_DIR"
    --backup-dir2 "$REMOTE_BACKUP_PATH"
    --suffix "$backup_suffix"
    --suffix-keep-extension
    --drive-use-trash=true
    --drive-skip-gdocs
    --fast-list
    --verbose
  )
}

run_bisync() {
  local mode="${1:-normal}"

  insync_is_running &&
    die "Insync sigue ejecutándose. Ciérralo para evitar dos sincronizadores sobre los mismos archivos."

  ensure_layout
  ensure_filters
  ensure_remote_path_exists
  ensure_access_markers
  bisync_common_args

  exec 9>"$LOCK_FILE"
  if ! flock -n 9; then
    log "Ya hay otra sincronización R4R en ejecución; se omite este ciclo."
    return 0
  fi

  if [[ "$mode" == "bootstrap" ]]; then
    log "Primera simulación segura: no modificará archivos."
    rclone bisync "${BISYNC_ARGS[@]}" \
      --resync \
      --resync-mode "$INITIAL_RESYNC_MODE" \
      --dry-run \
      --log-file "${STATE_DIR}/bootstrap-dry-run.log"

    log "La simulación terminó correctamente. Se inicia el bootstrap real en 10 segundos."
    log "Pulsa Ctrl+C ahora si quieres revisar primero ${STATE_DIR}/bootstrap-dry-run.log."
    sleep 10
    rclone bisync "${BISYNC_ARGS[@]}" \
      --resync \
      --resync-mode "$INITIAL_RESYNC_MODE"
    date --iso-8601=seconds >"$BOOTSTRAP_MARKER"
  else
    [[ -f "$BOOTSTRAP_MARKER" ]] ||
      die "Falta el bootstrap. Ejecuta manualmente ${INSTALL_PATH} una vez desde una terminal."
    rclone bisync "${BISYNC_ARGS[@]}"
  fi
}

install_self_and_systemd() {
  local script_path
  script_path="$(realpath "${BASH_SOURCE[0]}")"

  if [[ "$script_path" != "$INSTALL_PATH" ]]; then
    install -m 0755 "$script_path" "$INSTALL_PATH"
  fi

  command cat >"$SERVICE_FILE" <<EOF
[Unit]
Description=R4R bidirectional sync between local mirror and Google Drive
Wants=network-online.target
After=network-online.target

[Service]
Type=oneshot
Environment=R4R_RCLONE_SERVICE_MODE=1
ExecStart=${INSTALL_PATH}
Nice=10
IOSchedulingClass=best-effort
IOSchedulingPriority=7
EOF

  command cat >"$TIMER_FILE" <<EOF
[Unit]
Description=Run R4R rclone bisync periodically

[Timer]
OnBootSec=2min
OnUnitInactiveSec=${SYNC_INTERVAL}
RandomizedDelaySec=10s
Persistent=true
Unit=${UNIT_NAME}.service

[Install]
WantedBy=timers.target
EOF

  systemctl --user daemon-reload
  systemctl --user enable --now "${UNIT_NAME}.timer"
}

main() {
  [[ "$#" -eq 0 ]] || die "Este script no acepta parámetros; edita las variables del principio."

  if [[ "${R4R_RCLONE_SERVICE_MODE:-0}" == "1" ]]; then
    run_bisync normal
    return 0
  fi

  install_rclone_if_needed
  ensure_remote_configured
  ensure_layout

  if insync_is_running; then
    die "Insync está activo. Ciérralo por completo y vuelve a ejecutar este mismo script."
  fi

  if [[ ! -f "$BOOTSTRAP_MARKER" ]]; then
    run_bisync bootstrap
  else
    log "El bootstrap ya existe; se ejecutará una sincronización normal de comprobación."
    run_bisync normal
  fi

  install_self_and_systemd

  printf '\nInstalación terminada.\n'
  printf 'Timer: systemctl --user status %s.timer\n' "$UNIT_NAME"
  printf 'Log:   journalctl --user -u %s.service -f\n' "$UNIT_NAME"
  printf 'Parar: systemctl --user disable --now %s.timer\n' "$UNIT_NAME"
}

main "$@"
