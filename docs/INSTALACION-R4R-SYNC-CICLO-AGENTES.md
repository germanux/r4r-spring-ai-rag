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
- La importación se intenta siempre, también con el worktree sucio. Git acepta
  cambios locales disjuntos; solo se aplaza si Git rechaza realmente el merge.
- Antes de un intento sobre cambios locales se crea evidencia recuperable bajo
  `~/Desarrollo/.r4r-runtime/agent-sync-backups/`.
- Ante un rechazo o conflicto, el merge se aborta y no se ejecutan `stash`,
  `reset`, `force-push` ni resoluciones automáticas.
- El temporizador general se conserva como respaldo, no como vía principal.
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

# Versiona solo esta implementación; no incluye los seis documentos pendientes
# de Ring ni otros cambios locales.
git add -- \
  scripts/agent-integration-sync.sh \
  scripts/install-r4r-branch-sync-systemd.sh \
  py-codex-agent/src/r4r_codex_agent/runner.py \
  py-ring-agent/src/r4r_ring_agent/ring_loop.py \
  docs/INSTALACION-R4R-SYNC-CICLO-AGENTES.md

git commit --only \
  -m "feat(sync): synchronize agents at safe lifecycle boundaries" -- \
  scripts/agent-integration-sync.sh \
  scripts/install-r4r-branch-sync-systemd.sh \
  py-codex-agent/src/r4r_codex_agent/runner.py \
  py-ring-agent/src/r4r_ring_agent/ring_loop.py \
  docs/INSTALACION-R4R-SYNC-CICLO-AGENTES.md

git push origin agent/ring-agent-worker

# Reinstala el temporizador, ahora como respaldo cada 15 minutos.
R4R_BRANCH_SYNC_INTERVAL=15min \
  ./scripts/install-r4r-branch-sync-systemd.sh install

# Haz una última propagación con los agentes detenidos. Es el bootstrap que lleva
# runner.py y agent-integration-sync.sh a las ramas PC y LP.
systemctl --user start r4r-agent-branch-sync.service

./scripts/run-ring-system.sh start
```

No hace falta modificar ni reinicializar el manifiesto de Google Drive.

## Comprobación

```bash
systemctl --user list-timers --all |
  grep -E 'r4r-(agent-branch-sync|drive-import-safe)'

journalctl --user \
  -u r4r-agent-branch-sync.service \
  -u r4r-drive-import-safe.service \
  --since '10 minutes ago' \
  --no-pager

pgrep -af 'run-ring|run-worker|opencode|r4r_codex_agent'
ollama ps
```

En las consolas de Ring, PC y LP deben aparecer estas líneas:

```text
[r4r-agent-sync] agent/...: phase=startup
[r4r-agent-sync] agent/...: phase=checkpoint
```

`inbound merge deferred after Git rejected` significa que la sincronización se
intentó y Git la rechazó. El commit ya confirmado queda publicado y centralizado.
La copia previa permite auditar o recuperar el estado local si fuera necesario.

## Prueba manual de los tres agentes

El script es válido desde cualquier worktree de agente:

```bash
cd ~/Desarrollo/r4r-ring-agent.git
./scripts/agent-integration-sync.sh startup
./scripts/agent-integration-sync.sh checkpoint
```

Ring queda conectado automáticamente en `ring_loop.py`: sincroniza al arrancar y
después de cada commit de coordinación. PC y LP hacen lo mismo desde `runner.py`.

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
