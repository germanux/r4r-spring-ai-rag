R4R merge workers + restart — phase 2.13
==========================================

Corrige el fallo posterior al merge:

  ModuleNotFoundError: No module named 'r4r_codex_agent'

Causa: py-codex-agent/.venv es local e ignorado por Git. El merge actualiza el
código del controlador, pero no instala ni repara automáticamente el entorno
virtual de cada worktree.

Instalación desde la raíz de ~/Desarrollo/r4r-ring-agent.git:

  unzip -o r4r-merge-workers-restart-phase2.13-dropin.zip
  chmod +x scripts/merge-worker-branches-and-restart.sh
  git add scripts/merge-worker-branches-and-restart.sh \
          R4R-MERGE-WORKERS-PHASE2.13-README.txt
  git commit -m "fix(ring): repair worker Python runtimes before restart"

Comprobación:

  ./scripts/merge-worker-branches-and-restart.sh --dry-run

Ejecución:

  ./scripts/merge-worker-branches-and-restart.sh

Cambios respecto a 2.12:

1. Después del merge y de restaurar los cambios locales, valida en PC y LP:

     py-codex-agent/.venv/bin/python -c 'import r4r_codex_agent.cli'

2. Si falta el virtualenv, lo crea con python3 -m venv.
3. Si el módulo no es importable, crea de forma atómica un fichero .pth en el
   site-packages del virtualenv apuntando a py-codex-agent/src.
4. No usa pip, build isolation ni red; el controlador no tiene dependencias
   Python externas declaradas.
5. Repara PC y LP antes de arrancar el primero, evitando un reinicio parcial.
6. Conserva todo el comportamiento 2.12: stop completo, backup, stash con
   untracked, merge fijado, restore verificado y arranque fresco.

La ejecución anterior ya dejó ambos HEAD en f90228c y restauró correctamente los
cambios sucios. Reejecutar 2.13 hará merges no-op, volverá a preservar/restaurar
los cambios y reparará los runtimes antes del arranque.
