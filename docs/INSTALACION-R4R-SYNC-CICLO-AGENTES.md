# Sincronización R4R dirigida por el ciclo de vida de cada agente

El paquete se descomprime en la raíz de `~/Desarrollo/r4r-ring-agent.git`.

## Comportamiento

- Ring, PC y LP llevan sus commits pendientes a `agent/integration` e importan el
  hub antes de comenzar una ejecución.
- Tras cada checkpoint o commit creado por cualquiera de los controladores,
  publican su rama,
  la incorporan a `agent/integration`, publican el hub y reciben el hub resultante.
- `agent-integration-sync.sh`, `runner.py`, el respaldo periódico y Google Drive
  usan el mismo bloqueo:
  `~/Desarrollo/.r4r-runtime/git.lock`.
- La importación se intenta siempre, incluso si el agente está activo y el
  worktree contiene cambios `staged`, `unstaged` o no rastreados. No se ejecuta
  `git restore --staged`, por lo que el índice conserva su estado.
- Git acepta cambios locales disjuntos; solo se aplaza si rechaza realmente el
  merge. Ante un rechazo se aborta la operación y se compara la huella del
  índice/worktree con la anterior.
- Antes de un intento sobre cambios locales se crea evidencia recuperable bajo
  `~/Desarrollo/.r4r-runtime/agent-sync-backups/`.
- Ante un rechazo o conflicto, el merge se aborta y no se ejecutan `unstage`,
  `stash`, `reset`, `force-push` ni resoluciones automáticas.
- El temporizador general se conserva como respaldo, no como vía principal.
- El respaldo se ejecuta una vez durante la instalación y luego cada hora. Los eventos
  de Ring también quedan limitados a una hora y un ciclo sin cambio semántico no crea
  evidencia ni commit.
- Progreso, memoria, `.opencode/current/` y la vista operativa de Ring permanecen
  locales e ignorados. Los controladores solo confirman rutas funcionales de la tarea.
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
  scripts/agent-integration-sync.sh \
  scripts/sync-agent-branches.sh \
  scripts/install-r4r-branch-sync-systemd.sh

# Revisa el conjunto aplicado, confirma únicamente el parche de sincronización y
# publícalo antes de reinstalar. El servicio rechaza deliberadamente un hub sucio.
git diff --check
git status --short

# Continúa solo cuando el commit ya esté publicado y esta salida quede vacía.
test -z "$(git status --porcelain)"

# Reinstala el temporizador como respaldo cada hora. El instalador elimina el
# override antiguo, ejecuta una pasada inicial y falla de forma visible si el hub
# está sucio, falta autenticación o la sincronización no puede completarse.
./scripts/install-r4r-branch-sync-systemd.sh install

./scripts/run-ring-system.sh start
```

No hace falta modificar ni reinicializar el manifiesto de Google Drive.

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

El estado correcto es `enabled` y `active (waiting)` para el timer, con una hora
próxima visible en `list-timers`. Si aparece `integration worktree is dirty`, no es
un fallo silencioso de `systemd`: la protección se ha activado y el propio journal
lista los ficheros que deben confirmarse o retirarse antes de reintentar.

En las consolas de Ring, PC y LP deben aparecer estas líneas:

```text
[r4r-agent-sync] agent/...: phase=startup
[r4r-agent-sync] agent/...: phase=checkpoint
```

`inbound merge deferred after Git rejected` o `merge-rejected` significa que la
sincronización se intentó y Git la rechazó. El commit ya confirmado queda
publicado y centralizado. La copia previa permite auditar el estado local y la
huella comprueba que el rechazo no cambió `staged`, `unstaged` ni los archivos
no rastreados.

## Prueba manual de los tres agentes

El script es válido desde cualquier worktree de agente:

```bash
cd ~/Desarrollo/r4r-ring-agent.git
./scripts/agent-integration-sync.sh startup
./scripts/agent-integration-sync.sh checkpoint
```

Ring queda conectado automáticamente en `ring_loop.py`: sincroniza al arrancar y
después de cada cambio semántico de coordinación. PC y LP hacen lo mismo desde
`runner.py` al crear un checkpoint funcional o cerrar una tarea.

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
