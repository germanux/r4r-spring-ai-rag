R4R — CORRECCIÓN DE COMMITS DE ESTADO Y SYNC HORARIA

Contenido:
- apply-r4r-stop-git-churn-hourly-sync.sh
- r4r-stop-git-churn-hourly-sync.patch
- r4r-stop-git-churn-hourly-sync-after-surgical.patch

El aplicador selecciona automáticamente el parche completo para un árbol limpio o
el incremental cuando el parche anterior de Surgical ya está aplicado. No confirma
ni publica nada.

1. Detener escritores:

systemctl --user stop r4r-agent-branch-sync.timer r4r-agent-branch-sync.service
~/Desarrollo/r4r-ring-agent.git/scripts/run-ring-system.sh stop

2. Aplicar desde la carpeta descomprimida:

chmod +x ./apply-r4r-stop-git-churn-hourly-sync.sh
./apply-r4r-stop-git-churn-hourly-sync.sh ~/Desarrollo/r4r-integration.git

3. Revisar y confirmar. Continúa solo si no hay cambios ajenos al parche:

cd ~/Desarrollo/r4r-integration.git
git diff --check
git status --short
git add -u
git commit -m "fix(sync): stop volatile agent commit churn"
git push origin agent/integration

4. Reinstalar y comprobar el timer en cada equipo donde exista esa unidad:

cd ~/Desarrollo/r4r-integration.git
./scripts/install-r4r-branch-sync-systemd.sh install
./scripts/install-r4r-branch-sync-systemd.sh status

5. Reiniciar Ring:

~/Desarrollo/r4r-ring-agent.git/scripts/run-ring-system.sh start

El instalador ejecuta una sincronización inicial. Si falla, no lo oculta: muestra el
estado del servicio. Un hub sucio aparecerá como "integration worktree is dirty" y
el journal listará los ficheros concretos.
