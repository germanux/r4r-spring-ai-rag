R4R control patch — no-lock + Task 04
==============================================

Este paquete corrige el plano de control sin sobrescribir código de producto.

Sustituye:
- py-codex-agent completo por la variante no-lock validada;
- launchers y scripts de reparación relacionados;
- perfiles OpenCode r4r-pc y r4r-laptop;
- opencode.jsonc;
- instrucciones de reanudación;
- Task 04 y el paquete runtime/control activo.

No modifica:
- src/main/**;
- src/test/**;
- migraciones;
- base de datos;
- frontend/Angular;
- scripts de galería.

Ejemplo para el repositorio principal:

  ./apply-r4r-control-nolock-task04-v1.sh     --repo /home/german/Desarrollo/r4r-spring-ai-rag.git     --destination PC

Ejemplo para el worktree del portátil:

  ./apply-r4r-control-nolock-task04-v1.sh     --repo /home/german/Desarrollo/r4r-spring-ai-rag-laptop-agent.git     --destination LP

El instalador:
- detiene solo procesos asociados al worktree;
- crea copia de seguridad en /tmp;
- elimina el lock legado;
- mueve artefactos auxiliares conocidos fuera de la raíz;
- valida Bash, Python y la instalación no-lock;
- no inicia ningún agente automáticamente.

El launcher static-only de galería no se modifica, pero no debe utilizarse para la
futura implementación Angular.
