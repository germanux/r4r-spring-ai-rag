# Instalación de la sincronización R4R no disruptiva

El paquete se descomprime en la raíz de `~/Desarrollo/r4r-ring-agent.git`.

## Qué cambia

- `scripts/sync-agent-branches.sh` ya no detiene agentes ni usa `git stash`.
- Las ramas con worktree activo, sucio o en conflicto se omiten y se reintentan.
- El respaldo `systemd` se ejecuta una vez al instalarse y después cada hora; tras
  reactivar el timer espera dos minutos y añade hasta dos minutos de desfase para que
  dos equipos no golpeen el remoto simultáneamente.
- `.opencode/current/`, las memorias, el progreso y la vista operativa de Ring son
  estado local ignorado. Los SHA-256 de recuperación permanecen solo bajo
  `~/Desarrollo/.r4r-runtime/` y nunca generan commits.
- `runner.py`, el sincronizador de ramas y Google Drive usan el mismo bloqueo:
  `~/Desarrollo/.r4r-runtime/git.lock`.
- `r4r-drive-import-safe.py --bidirectional` sincroniza Insync ↔ Git, exportando
  únicamente ficheros versionados y notificando cambios simultáneos.

## Instalación

```bash
cd ~/Desarrollo/r4r-ring-agent.git

systemctl --user stop \
  r4r-agent-branch-sync.timer \
  r4r-agent-branch-sync.service \
  r4r-drive-import-safe.timer \
  r4r-drive-import-safe.service

./scripts/run-ring-system.sh stop

# Descomprime aquí el ZIP con sustitución de ficheros.

chmod +x \
  scripts/r4r-drive-import-safe.py \
  scripts/sync-agent-branches.sh \
  scripts/install-r4r-branch-sync-systemd.sh \
  scripts/install-r4r-drive-sync-systemd.sh

./scripts/install-r4r-branch-sync-systemd.sh install
./scripts/install-r4r-drive-sync-systemd.sh install

./scripts/run-ring-system.sh start
```

No hace falta reinicializar el manifiesto existente: la versión anterior se
migra automáticamente. No ejecutes `--initialize` sobre un manifiesto ya válido.

## Comprobación

```bash
cd ~/Desarrollo/r4r-integration.git
./scripts/install-r4r-branch-sync-systemd.sh status

git status --short

journalctl --user \
  -u r4r-agent-branch-sync.service \
  -u r4r-drive-import-safe.service \
  --since '10 minutes ago' \
  --no-pager

pgrep -af 'run-ring|run-worker|opencode|r4r_codex_agent'
ollama ps
```

Si `status` muestra el timer inactivo, reinstálalo. Si el journal muestra
`integration worktree is dirty`, el servicio está funcionando y se ha detenido de
forma segura: confirma o retira primero esos cambios reales. El instalador devuelve
error inmediatamente si la pasada inicial no puede sincronizar.

En el journal del sincronizador pueden aparecer ramas aplazadas por una operación Git
pendiente. Es un aplazamiento seguro. Los commits funcionales ya confirmados se
centralizan en `agent/integration`; ningún ciclo crea commits de memoria, progreso,
timestamps o manifiestos SHA-256.

## Prueba de Google Drive sin modificar archivos

```bash
python3 ~/.local/lib/r4r/r4r-drive-import-safe.py \
  --source "$HOME/Insync/riansares4r@gmail.com/Google Drive/Agentes R4R/r4r-ring-agent.git" \
  --destination "$HOME/Desarrollo/r4r-google-drive.git" \
  --manifest "$HOME/Desarrollo/.r4r-runtime/drive-import/state.json" \
  --lock "$HOME/Desarrollo/.r4r-runtime/git.lock" \
  --bidirectional \
  --dry-run
```
