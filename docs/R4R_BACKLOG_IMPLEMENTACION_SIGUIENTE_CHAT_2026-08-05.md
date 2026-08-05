# R4R — Backlog de implementación para el siguiente chat

Generado: 05/08/2026 02:28:50 CEST  
Objetivo: conservar todas las decisiones y tareas pendientes relevantes antes de cerrar el chat, indicando qué código debe modificarse, cómo validarlo y qué no debe reimplementarse a ciegas.

## 0. Cómo usar este documento

Este documento no afirma que el estado local actual sea idéntico al último log recibido. El siguiente chat debe empezar inspeccionando el repositorio real y clasificando cada tarea como `hecha`, `parcial`, `pendiente` o `ya no aplicable`. No se debe restaurar un archivo completo desde un commit antiguo ni sobrescribir cambios posteriores sin revisar el diff.

Prioridades:

- `P0`: bloquea Ring o puede perder trabajo.
- `P1`: necesario para que Ring, PC y LP funcionen de forma estable y observable.
- `P2`: ahorro de tokens, robustez y mantenimiento.
- `AUDITAR`: la conversación no contiene evidencia suficiente para afirmar si ya quedó implementado.
- `LEGACY`: tarea histórica que no debe mezclarse con el sistema actual salvo que el repositorio todavía la necesite.

**English summary:** Start from the real repository state; never overwrite newer work based only on this handoff.

## 1. Decisiones vigentes y sustituciones

### 1.1. Ejecutor y autenticación

- Mantener `opencode run` como ejecutor para Ring y, cuando sea viable, para PC y LP.
- No migrar el sistema a Codex SDK ni a OpenCode SDK.
- Usar `OPENAI_API_KEY`; no mezclarla con OAuth de ChatGPT.
- Nunca guardar `OPENAI_API_KEY` en Git, `opencode.jsonc`, logs, estados o memorandos.
- No ejecutar dos procesos Ring con permisos de escritura simultáneos sobre el mismo repositorio.

**English summary:** OpenCode CLI and API-key authentication remain authoritative; SDK and OAuth paths are excluded.

### 1.2. Matriz de modelos nueva

Esta tabla reemplaza la propuesta anterior que usaba Terra para Ring. La fila de Ring operativo no incluía razonamiento explícito en el último mensaje; se propone `low` por coherencia con el objetivo de ahorro, pero debe confirmarse con una prueba corta antes de fijarlo definitivamente.

| Agente | Modelo | Razonamiento | Motivo |
|---|---|---|---|
| Ring operativo | `openai/gpt-5.6-luna` | `low` provisional | Supervisión, contexto grande y análisis general con coste bajo |
| Ring GPT diagnóstico | `openai/gpt-5.6-luna` | `low` | Diagnóstico aislado del proveedor y del resolvedor de OpenCode |
| PC | `openai/gpt-5.3-codex` | `low` por defecto; `medium` al atascarse | Implementación y depuración |
| LP | `openai/gpt-5.3-codex` | `low` por defecto; `medium` al atascarse | Implementación y pruebas |
| Tareas realmente baratas | `openai/gpt-5.6-luna` | `low` | Trabajo repetitivo, extracción, clasificación y gran volumen |

Qwen/Ollama deja de ser el Ring principal en esta decisión nueva, pero debe conservarse como fallback [contingency] reversible hasta que Luna complete varios ciclos reales sin fallos. No borrar su configuración ni el identificador exacto que funcionaba antes de `cccd08f`.

La documentación oficial de OpenAI confirma que Luna es el modelo de coste bajo y alto volumen, con ventana de contexto de 1.050.000 tokens. También confirma que `gpt-5.3-codex` sigue disponible mediante API y admite `low`, `medium`, `high` y `xhigh`. En Codex con inicio de sesión ChatGPT aparece como modelo anterior/deprecado, pero esa retirada no equivale a indisponibilidad mediante `OPENAI_API_KEY`.

Fuentes de referencia:

- <https://developers.openai.com/api/docs/models/gpt-5.6-luna>
- <https://developers.openai.com/api/docs/models/gpt-5.3-codex>
- <https://learn.chatgpt.com/docs/models>

**English summary:** Luna now supervises Ring; GPT-5.3 Codex remains the API coding worker, while Ollama is retained only as a reversible fallback.

## 2. Estado confirmado al cerrar el chat

### Confirmado

- OpenCode instalado: versión observada `1.18.9`.
- `opencode models openai --refresh` ya muestra `openai/gpt-5.6-luna` y `openai/gpt-5.3-codex`.
- OpenCode detecta `OPENAI_API_KEY` y no detectó credenciales OAuth activas.
- El intento con `openai/gpt-5.6-sol` terminó antes de llamar a la API: `tokens.input=0`, `cost=0`.
- El error observado fue local al resolvedor:

  ```text
  Model not found: openai/gpt-5.6-sol
  Did you mean: gpt-5.6-sol, gpt-5.6-sol-pro, gpt-5.6-sol-fast?
  ```

- Ring capturó estado de PC/LP, lanzó OpenCode y quedó esperando sin que `opencode.console.log` recibiera bytes.
- PC sí generaba eventos JSON, lecturas y uso de herramientas; por tanto, el sistema general de streaming no estaba roto.
- `show-agent-console.sh 3`, ejecutado desde `r4r-lp-worker.git`, no encontró una consola LP y mostró una ruta de estado ligada al worktree equivocado.
- El árbol Ring tenía modificaciones runtime en:

  ```text
  .opencode/current/ring/worker-understanding.md
  .ring-agent/backend-frontend-handoff.md
  .ring-agent/code-lp-review.md
  .ring-agent/code-pc-review.md
  .ring-agent/global-summary.md
  .ring-agent/state.json
  scripts/show-agent-console.sh
  ```

### No confirmado

- Que PC haya terminado la corrección Maven, tenga pruebas verdes y haya hecho commit/push.
- Que LP estuviera trabajando o que solo faltara localizar su log.
- Que la sincronización/merge/push por minuto esté instalada, habilitada y libre de conflictos.
- Que los comandos de consola se hayan añadido definitivamente al README.
- Que la denominada “fase 3” haya quedado completada y validada.

**English summary:** The resolver and Ring are confirmed blockers; PC completion, LP activity, Git automation, README changes, and phase 3 remain unverified.

## 3. P0 — Congelar y auditar antes de editar

Antes de aplicar parches:

1. Detener únicamente Ring y su timer/supervisor si siguen activos; PC/LP no deben matarse sin comprobar qué están haciendo.
2. Guardar `git status --short --branch`, `git diff`, últimos commits y PIDs.
3. Revisar específicamente el diff del commit `cccd08f12e9c77a93caa65139b801d2422768391` y todos los commits posteriores sobre los cuatro archivos modificados por él.
4. Preservar los ficheros de intercambio de `.ring-agent/` y `.opencode/current/ring/`; no son ruido descartable.
5. No hacer `git restore` de los cuatro archivos completos. Preparar un parche inverso mínimo o editar solo las claves defectuosas.

Archivos a inspeccionar primero:

```text
.opencode/agents/r4r-ring.md
opencode.jsonc
py-ring-agent/src/r4r_ring_agent/ring_loop.py
py-ring-agent/tests/test_ring_loop.py
scripts/show-agent-console.sh
scripts/run-ring-system.sh
```

Comandos iniciales sugeridos:

```bash
cd ~/Desarrollo/r4r-ring-agent.git
git status --short --branch
git diff --stat
git log --oneline --decorate -n 20
git show --stat cccd08f12e9c77a93caa65139b801d2422768391
git log --oneline cccd08f12e9c77a93caa65139b801d2422768391..HEAD -- \
  .opencode/agents/r4r-ring.md \
  opencode.jsonc \
  py-ring-agent/src/r4r_ring_agent/ring_loop.py \
  py-ring-agent/tests/test_ring_loop.py
```

Criterio de aceptación: existe una instantánea inequívoca [unambiguous] del estado y ningún trabajo de agente queda sobrescrito.

**English summary:** Freeze and inspect first; preserve runtime exchange files and revert only the defective lines.

## 4. P0 — Corregir el resolvedor de modelos de OpenCode

### Causa más probable

El commit `cccd08f` añadió unas 239 líneas a `opencode.jsonc`. El error “Model not found: `openai/gpt-5.6-sol`; did you mean `gpt-5.6-sol`” encaja con una definición personalizada donde el proveedor `openai` recibe como clave o `id` el nombre completo `openai/gpt-...`. OpenCode ya añade el prefijo del proveedor; dentro de `provider.openai.models` la clave debe ser solo el `model_id`.

La regla oficial de OpenCode es:

- modelo externo: `provider_id/model_id`;
- clave dentro de `provider.<provider_id>.models`: solo `model_id`;
- para un modelo integrado, no hace falta copiar todo el catálogo a `opencode.jsonc`.

Referencia: <https://opencode.ai/docs/models/>

### Código que cambiar

Archivo principal: `opencode.jsonc`.

1. Eliminar las entradas copiadas masivamente del catálogo OpenAI si solo duplican modelos integrados.
2. Eliminar cualquier clave como:

   ```jsonc
   "openai/gpt-5.6-luna": { ... }
   ```

3. Eliminar cualquier `id` incorrecto como:

   ```jsonc
   "id": "openai/gpt-5.6-luna"
   ```

4. Si se quieren opciones globales, dejar solo claves internas:

   ```jsonc
   {
     "$schema": "https://opencode.ai/config.json",
     "provider": {
       "openai": {
         "options": {
           "timeout": 900000,
           "chunkTimeout": 180000
         },
         "models": {
           "gpt-5.6-luna": {
             "options": {
               "reasoningEffort": "low",
               "textVerbosity": "low",
               "reasoningSummary": "auto"
             }
           },
           "gpt-5.3-codex": {
             "options": {
               "reasoningEffort": "low",
               "textVerbosity": "low",
               "reasoningSummary": "auto"
             }
           }
         }
       }
     }
   }
   ```

5. Revisar también la configuración global, porque OpenCode fusiona configuraciones en vez de reemplazarlas:

   ```text
   ~/.config/opencode/config.json
   ~/.config/opencode/opencode.json
   ~/.config/opencode/opencode.jsonc
   ```

   No versionar esos archivos. Corregirlos localmente solo si contienen el mismo catálogo defectuoso.

6. Mantener la configuración del proyecto pequeña; el catálogo debe renovarse con `opencode models --refresh`, no pegarse entero en Git.

### Pruebas

Ejecutar dos smoke tests aislados, primero Luna y después Codex. Usar un directorio temporal, `--pure`, timeout corto, `--format json`, `--print-logs` y `--log-level DEBUG`. No probar contra el repositorio vivo.

Resultados obligatorios:

- Luna devuelve el texto pedido, `exit=0` y tokens mayores que cero.
- Codex devuelve el texto pedido, `exit=0` y tokens mayores que cero.
- No aparece `ProviderModelNotFoundError`.
- El JSON identifica exactamente el modelo usado.
- La clave no aparece en la salida.

**English summary:** Remove the duplicated catalog, keep bare model IDs inside the provider, and validate both API models in isolated runs.

## 5. P0 — Crear una configuración común de modelos

### Ficheros nuevos

```text
config/agent-models.env
scripts/lib/agent-models.sh
```

Contenido propuesto para `config/agent-models.env`:

```bash
# Valores versionados; nunca incluir secretos.
R4R_RING_MODEL=openai/gpt-5.6-luna
R4R_RING_VARIANT=low

R4R_RING_DIAGNOSTIC_MODEL=openai/gpt-5.6-luna
R4R_RING_DIAGNOSTIC_VARIANT=low

R4R_PC_MODEL=openai/gpt-5.3-codex
R4R_PC_VARIANT=low

R4R_LP_MODEL=openai/gpt-5.3-codex
R4R_LP_VARIANT=low

R4R_SMALL_MODEL=openai/gpt-5.6-luna
R4R_SMALL_VARIANT=low

# Rellenar con el ID exacto recuperado de cccd08f^; no inventarlo.
R4R_RING_FALLBACK_MODEL=
R4R_RING_FALLBACK_VARIANT=
```

Contenido funcional que debe aportar `scripts/lib/agent-models.sh`:

1. Resolver `REPO_ROOT` desde la ubicación del script, no desde `$PWD`.
2. Cargar `config/agent-models.env`.
3. Cargar después el override local opcional:

   ```text
   $HOME/.config/r4r/agent-models.env
   ```

4. Validar que cada modelo tenga formato `proveedor/modelo`.
5. Validar variantes permitidas: vacío, `low`, `medium`, `high`, `xhigh`.
6. Exportar las variables para Python y procesos hijos.
7. Fallar con mensaje claro si falta el modelo obligatorio.
8. No imprimir valores de claves o variables sensibles.

Cada lanzador de Ring, PC y LP debe cargar esta librería. Los overrides locales permiten variar `medium` sin ensuciar Git.

Importante: los worktrees están en ramas distintas. Añadir el fichero a la rama base y fusionarlo en las ramas de agentes; no depender de copiar manualmente el fichero cada minuto.

### Tests

- Defaults sin override.
- Override local que cambia solo `R4R_PC_VARIANT`.
- Modelo sin `/` produce error.
- Variante desconocida produce error.
- Ausencia de `OPENAI_API_KEY` produce error solo cuando el modelo seleccionado sea `openai/*`.
- El fallback Ollama no exige clave OpenAI.

**English summary:** Version shared defaults, load local overrides afterwards, validate all values, and keep secrets outside Git.

## 6. P0 — Integrar la configuración en Ring

### `py-ring-agent/src/r4r_ring_agent/ring_loop.py`

Cambios requeridos:

1. Eliminar modelos y variantes hardcoded introducidos por `cccd08f`.
2. Leer `R4R_RING_MODEL` y `R4R_RING_VARIANT` del entorno.
3. Construir la orden como lista de argumentos, nunca como cadena evaluada por shell.
4. Añadir `--model <valor>` siempre; añadir `--variant <valor>` solo si no está vacío.
5. Ejecutar OpenCode con:

   ```text
   --format json
   --print-logs
   --log-level INFO
   ```

   Para diagnóstico se usará `DEBUG`; no dejar `DEBUG` permanente en producción.
6. Combinar o capturar `stdout` y `stderr` de forma que `ProviderModelNotFoundError`, timeout y autenticación aparezcan inmediatamente en `opencode.console.log`.
7. Forzar flush al escribir el log para que `tail -F` reciba eventos sin retraso.
8. Guardar por ejecución un metadato sin secretos:

   ```text
   model
   variant
   pid
   started_at
   last_output_at
   exit_code
   timeout_reason
   session_id
   tokens.input
   tokens.output
   tokens.reasoning
   tokens.cache.read
   cost
   ```

9. Diferenciar:

   - proceso vivo;
   - proceso que está produciendo salida;
   - proceso bloqueado sin primer byte;
   - proceso terminado con error.

10. Límites propuestos, configurables:

    ```text
    R4R_RING_FIRST_OUTPUT_TIMEOUT_SECONDS=180
    R4R_RING_HARD_TIMEOUT_SECONDS=900
    ```

    Si no llega el primer byte en 180 s, terminar el hijo de forma ordenada y registrar la causa. No dejarlo 90 minutos hasta el timeout genérico.

11. En caso de error del proveedor, Ring no debe publicar una revisión vacía como si hubiera razonado.
12. El fallback Qwen/Ollama solo debe activarse tras un error clasificado y nunca ejecutar en paralelo con el Ring OpenAI.

### `py-ring-agent/tests/test_ring_loop.py`

Añadir pruebas con un proceso OpenCode simulado:

- Propaga modelo y variante.
- Omite `--variant` si está vacío.
- Captura stderr aunque stdout esté vacío.
- Mata el hijo al superar el timeout de primer byte.
- Guarda `exit_code` y causa.
- No publica resultados si OpenCode falla.
- Activa fallback una sola vez y no crea dos escritores.
- Redacta cualquier patrón de API key.

### `.opencode/agents/r4r-ring.md`

- Quitar el modelo fijo del frontmatter si el lanzador lo pasa por CLI.
- Mantener rol, permisos y reglas, pero no duplicar la selección de modelo.
- Añadir instrucción de salida breve y estructurada para reducir tokens.
- Prohibir copiar logs completos; usar resúmenes, últimos errores y diffs.

**English summary:** Ring must receive the model from one source, stream both outputs, enforce bounded timeouts, and never publish fabricated progress after failure.

## 7. P1 — Perfil diagnóstico de OpenAI sin segundo Ring escritor

### Fichero nuevo

```text
scripts/diagnose-ring-openai.sh
```

Comportamiento:

1. Cargar `scripts/lib/agent-models.sh`.
2. Usar `R4R_RING_DIAGNOSTIC_MODEL` y variante.
3. Crear un directorio temporal vacío o un worktree de solo diagnóstico.
4. Ejecutar `opencode run --pure --format json --print-logs --log-level DEBUG`.
5. No permitir herramientas de escritura sobre `r4r-ring-agent.git`.
6. Terminar en dos o tres minutos.
7. Guardar log y resumen bajo `runtime/diagnostics/openai/<timestamp>/`.
8. Devolver códigos distintos para:

   - modelo no encontrado;
   - autenticación;
   - rate/spend limit;
   - timeout sin primer byte;
   - éxito.

Este script diagnostica el mismo proveedor/modelo de Ring, pero no inicia un segundo supervisor ni publica instrucciones a PC/LP.

**English summary:** Diagnosis must be isolated, read-only, short-lived, and incapable of competing with the operational Ring.

## 8. P1 — Reparar y unificar `show-agent-console.sh`

Problema confirmado: al ejecutar la opción `3` desde el worktree LP, el script buscó dentro del repositorio equivocado y no encontró consola.

### `scripts/show-agent-console.sh`

Cambios requeridos:

1. No derivar todas las rutas de `$PWD`.
2. Aceptar rutas desde variables:

   ```text
   R4R_RING_ROOT
   R4R_PC_ROOT
   R4R_LP_ROOT
   ```

3. Si no existen, descubrir por defecto los hermanos de `~/Desarrollo/`:

   ```text
   r4r-ring-agent.git
   r4r-pc-worker.git
   r4r-lp-worker.git
   ```

   Los nombres reales deben verificarse; no fijarlos si el sistema ya expone rutas en un estado central.
4. Preferir un registro generado por el sistema, por ejemplo:

   ```text
   runtime/ring-system/agent-paths.env
   ```

5. La opción `1` debe mostrar Ring y supervisor, pero separar visualmente:

   - salida cognitiva de Ring;
   - heartbeat del guardian;
   - edad del último evento;
   - estado del proceso hijo OpenCode.

6. La opción `2` debe resolver el último `opencode.console.log` de PC.
7. La opción `3` debe resolver el último `opencode.console.log` de LP aunque el comando se ejecute desde cualquier worktree.
8. Si no hay ejecución actual, mostrar el último log disponible y decir que es histórico; no decir simplemente “no encuentro consola”.
9. El comando de estado sugerido debe apuntar siempre al `run-ring-system.sh` de Ring, no al worktree desde el que se invoca.
10. Usar `tail -F` y soportar rotación/cambio de fichero cuando empieza un ciclo nuevo.
11. Mostrar un aviso distinto para:

    - fichero inexistente;
    - fichero vacío;
    - fichero sin cambios;
    - proceso inexistente;
    - proceso vivo sin salida.

### Pruebas

Crear fixtures con tres raíces temporales y probar que las opciones `1`, `2` y `3` resuelven el fichero correcto desde cualquier directorio actual.

**English summary:** Console discovery must be root-independent and must distinguish missing logs, stale logs, silent live processes, and historical output.

## 9. P1 — Mejorar guardian/supervisor: vivo no significa productivo

El log repetitivo `PC: healthy` / `LP: healthy` solo prueba liveness. No demuestra que un agente esté generando eventos o modificando código.

### Código a localizar

Buscar la implementación con:

```bash
rg -n 'healthy|r4r-guardian|supervisor.log|last_output|heartbeat' \
  scripts py-ring-agent .opencode
```

El código probablemente está en `scripts/run-ring-system.sh` o en un helper invocado por él. Modificar el fichero real que contenga el bucle; no crear otro guardian duplicado.

### Cambios

- Registrar `healthy` solo en cambio de estado o como resumen cada cinco minutos, no cada 15 segundos.
- Mantener un estado por agente:

  ```text
  process_alive
  child_alive
  last_output_age
  last_git_change_age
  last_commit
  current_task
  stalled_reason
  ```

- Definir `stalled` por ausencia de progreso, no por PID.
- Ring silencioso debe aparecer como `alive-but-no-output`, no `healthy`.
- No matar PC/LP solo por falta de commit; pueden estar compilando o razonando.
- Aplicar restart automático únicamente cuando el error sea clasificable y el árbol esté preservado.
- Usar lock para que el supervisor no arranque dos instancias del mismo agente.

**English summary:** Replace noisy liveness claims with state transitions and measurable progress signals.

## 10. P1 — Integrar PC y LP con la configuración común

Los nombres exactos de todos los lanzadores no llegaron en la salida final. El siguiente chat debe descubrirlos antes de editar:

```bash
cd ~/Desarrollo/r4r-ring-agent.git
rg -l -- '--model|R4R_[A-Z_]*MODEL|R4R_[A-Z_]*VARIANT|opencode run' \
  scripts py-ring-agent .opencode
```

Además, revisar en las ramas/worktrees los candidatos históricos:

```text
runner.py
setup.sh
run-codex-agent.sh
scripts/run-*-agent.sh
scripts/run-*-worker.sh
```

Para cada lanzador real:

1. Cargar `scripts/lib/agent-models.sh`.
2. PC debe usar `R4R_PC_MODEL` / `R4R_PC_VARIANT`.
3. LP debe usar `R4R_LP_MODEL` / `R4R_LP_VARIANT`.
4. Pasar siempre el modelo explícito por CLI para no heredar “last used model”.
5. Registrar modelo y variante al arrancar, sin imprimir claves.
6. Capturar JSON y stderr en el log visible por `show-agent-console.sh`.
7. Permitir override local `medium` para una tarea compleja sin modificar Git.
8. Si el modelo falla, no cambiar silenciosamente a Luna; registrar el error y dejar la decisión al supervisor.

**English summary:** Discover every real launcher, then make model selection explicit, shared, observable, and locally overridable.

## 11. P1 — Control de tokens y coste

El objetivo no debe ser solo elegir un modelo barato; también hay que reducir contexto redundante y medir el consumo real.

### Código que añadir

En el módulo que procesa eventos JSON de OpenCode —preferentemente dentro de `ring_loop.py` o en un módulo pequeño `usage.py`— acumular:

```text
tokens.input
tokens.output
tokens.reasoning
tokens.cache.read
tokens.cache.write
cost
model
agent
task
session_id
timestamp
```

Persistir:

```text
runtime/usage/runs.jsonl
runtime/usage/daily-summary.json
```

No versionar esos ficheros.

### Reducción de contexto

- Ring debe leer diffs, commits, resultados de tests y resúmenes estructurados, no logs completos.
- Limitar el tail de errores y guardar la ruta del log completo en vez de incrustarlo.
- No reenviar en cada ciclo memorias que no hayan cambiado.
- Calcular hashes de entradas y reutilizar el resumen si el contenido es idéntico.
- Separar `global-summary.md` estable de un `cycle-delta.md` pequeño.
- Activar poda/compactación de OpenCode solo tras probarla; documentar los valores de `compaction.prune` y `reserved`.
- `low` es el valor normal; `medium` requiere una causa registrada.

### Límites configurables

Añadir variables sin imponer cifras económicas no autorizadas:

```text
R4R_WARN_COST_PER_RUN
R4R_HARD_COST_PER_RUN
R4R_WARN_TOKENS_PER_RUN
R4R_HARD_TOKENS_PER_RUN
```

Si están vacías, solo medir. Si tienen valor, avisar o abortar de forma controlada. Nunca inventar un presupuesto a partir de “500 créditos”.

**English summary:** Measure every run, send deltas instead of full histories, and make spending limits explicit rather than inferred.

## 12. P1/AUDITAR — Trabajo actual del agente PC

Evidencia disponible: PC tenía un log grande, usaba herramientas y diagnosticó dos fallos Maven. Spring no podía crear `KnowledgeIngestionService` porque faltaba un bean `JdbcTemplate`; el reemplazo/mock parecía registrarse demasiado tarde. Las pruebas esperaban códigos `0` y `4`, pero recibían `5` por fallo al crear el contexto.

### No aplicar una solución a ciegas

Primero revisar:

```bash
git -C ~/Desarrollo/r4r-pc-worker.git status --short --branch
git -C ~/Desarrollo/r4r-pc-worker.git diff --check
git -C ~/Desarrollo/r4r-pc-worker.git diff
git -C ~/Desarrollo/r4r-pc-worker.git log --oneline -n 10
```

Buscar:

```bash
rg -n 'KnowledgeIngestionService|JdbcTemplate|expected.*[04]|exit.*5|@MockBean|@TestConfiguration' \
  ~/Desarrollo/r4r-pc-worker.git
```

### Si el fallo sigue presente

- Verificar que la prueba instala el mock o bean antes de crear el `ApplicationContext`.
- Preferir `@MockBean KnowledgeIngestionService` si la prueba no pretende validar la ingestión real.
- Si sí debe validar el servicio, proporcionar `JdbcTemplate` mediante la auto-configuración JDBC correcta o un `@TestConfiguration` importado antes del arranque.
- No crear un `JdbcTemplate` falso en producción para hacer verde una prueba.
- Revisar el path probable:

  ```text
  src/main/java/com/riansares/rag/service/KnowledgeIngestionService.java
  ```

- Identificar las dos clases de test exactas antes de modificarlas.
- Ejecutar primero los tests afectados y luego toda la suite Maven.

Criterio de aceptación:

- códigos esperados restaurados;
- suite verde;
- `git diff --check` limpio;
- diff pequeño y justificado;
- commit y push confirmados;
- Ring recibe un resumen con hash y pruebas ejecutadas.

**English summary:** PC’s diagnosis is plausible, but inspect its current diff before deciding between an early test mock and real JDBC auto-configuration.

## 13. P1/AUDITAR — LP y su consola

No hay evidencia suficiente para afirmar que LP estuviera parado. La ausencia de consola puede ser solo un fallo de resolución de ruta.

Pasos:

1. Comprobar PID y árbol de procesos LP.
2. Localizar el último `opencode.console.log` bajo todos los runtime conocidos.
3. Comprobar edad y tamaño del fichero.
4. Revisar `git status`, diff, commit y tarea asignada.
5. Solo reiniciar si no existe proceso o si está bloqueado según la nueva definición de progreso.
6. Después validar `show-agent-console.sh 3` desde Ring, PC, LP y `$HOME`.

**English summary:** Missing console output is not proof of a dead LP worker; resolve paths and inspect progress before restarting it.

## 14. P1/AUDITAR — Sincronización, commit, push y merge por minuto

La conversación anterior pidió commit/push/merge automático de ramas `agent/*` cada minuto, incluida la rama de Google Drive. No consta aquí una validación final completa.

Archivos conocidos:

```text
scripts/install-r4r-branch-sync-systemd.sh
scripts/sync-agent-branches.sh
docs/R4R-MERGE-WORKERS-PHASE2.13-README.md
```

Unidad/timer conocido:

```text
r4r-agent-branch-sync.timer
```

### Auditoría

- Verificar timer habilitado y frecuencia real.
- Verificar que solo existe un timer/cron; no duplicar automatismos.
- Confirmar qué repositorio es el integrador canónico.
- Confirmar ramas incluidas y excluidas.
- Revisar logs de commits, pushes, merges y conflictos.
- Confirmar que los ficheros de intercambio de `.ring-agent/` sí se conservan cuando corresponde.

### Endurecimiento si falta

- Usar `flock` para impedir dos sincronizaciones simultáneas.
- No hacer commit de logs, PIDs, claves, `runtime/usage` ni temporales.
- No resolver conflictos automáticamente.
- Ante conflicto: abortar ese merge, conservar ramas y registrar una alerta visible.
- No hacer push forzado.
- No hacer commit vacío.
- Evitar mezclar ramas con un worktree sucio no atribuible al agente.
- Registrar commit origen/destino y resultado.
- Añadir `--dry-run` y una prueba con repositorios temporales.

**English summary:** Audit the one-minute Git automation before changing it; it must serialize runs, preserve exchange files, and stop safely on conflicts.

## 15. P1 — Política de ficheros de intercambio y runtime

No excluir por error los ficheros que permiten saber cómo hablan los agentes.

### Deben tratarse como estado de coordinación versionable o checkpoint explícito

```text
.opencode/current/ring/worker-understanding.md
.ring-agent/backend-frontend-handoff.md
.ring-agent/code-lp-review.md
.ring-agent/code-pc-review.md
.ring-agent/global-summary.md
.ring-agent/state.json
```

### No deben versionarse

```text
runtime/**/opencode.console.log
runtime/**/*.pid
runtime/usage/*
runtime/diagnostics/*
temporales
claves
```

Revisar `.gitignore`, el allowlist del script de sincronización y cualquier filtro de exportación. El usuario ya observó que algunos ficheros principales de intercambio parecían quedarse fuera del commit.

Añadir una prueba que cree un fichero canónico de intercambio y un log runtime, ejecute la selección de cambios y compruebe que solo el primero entra en el checkpoint.

**English summary:** Preserve canonical coordination documents while excluding logs, PIDs, usage data, diagnostics, and secrets.

## 16. P2 — Guardrails del harness recuperados de memorandos anteriores

Estas reglas siguen siendo pertinentes y no consta que estén todas implementadas:

1. Tras tres fallos consecutivos sobre el mismo objetivo, releer `AGENTS.md`.
2. No repetir una llamada de herramienta idéntica después de un error de esquema, permiso, ruta o timeout.
3. Normalizar firmas de llamadas y bloquear la tercera repetición con el mismo error.
4. Gestionar procesos temporales con PID exacto; no usar `pkill -f` como mecanismo normal.
5. Un build verde, árbol limpio, commit o HTML inicial no equivalen a aceptación completa.
6. El supervisor, no el LLM, decide qué fase está terminada mediante validadores deterministas.
7. Cada fase debe guardar hash, tests, diff y criterio de aceptación.
8. Los comandos ruidosos deben resumirse y enlazar al log completo.

Código probable:

- `AGENTS.md` para reglas del agente.
- `runner.py` o el supervisor Python real para circuit breaker y firmas de error.
- `scripts/run-ring-system.sh` para PIDs, locks y reinicios.
- validadores de fase bajo `scripts/` o `py-ring-agent/`.

**English summary:** Add deterministic circuit breakers, exact PID ownership, and validator-driven completion instead of trusting agent claims.

## 17. P2 — Documentación operativa

Actualizar al menos:

```text
docs/R4R-MERGE-WORKERS-PHASE2.13-README.md
README.md o el README operativo equivalente
```

Debe incluir:

- matriz de modelos vigente;
- ubicación de `config/agent-models.env` y override local;
- prohibición de guardar `OPENAI_API_KEY`;
- comandos para Ring, PC y LP desde `cd ~/Desarrollo/...`;
- `show-agent-console.sh 1`, `2` y `3`;
- cómo ejecutar el diagnóstico OpenAI;
- cómo activar fallback Ollama sin dos Rings concurrentes;
- rutas de logs y significado de `alive`, `stalled`, `failed`, `completed`;
- cómo validar timer Git;
- rollback mínimo del cambio de modelos;
- checklist de aceptación.

No copiar claves, PIDs concretos ni rutas temporales con timestamp.

**English summary:** The operator guide must explain models, overrides, consoles, diagnostics, fallback, Git automation, and safe rollback.

## 18. Orden recomendado de implementación

1. Congelar estado y conservar cambios.
2. Corregir `opencode.jsonc` y configuración global defectuosa.
3. Smoke test Luna.
4. Smoke test GPT-5.3 Codex.
5. Crear `config/agent-models.env` y loader común.
6. Integrar Ring y sus tests.
7. Añadir diagnóstico aislado.
8. Reparar consola unificada.
9. Mejorar guardian/supervisor.
10. Integrar lanzadores PC/LP.
11. Añadir métricas de tokens/coste.
12. Auditar y finalizar la tarea Maven de PC.
13. Auditar LP.
14. Auditar sincronización Git y ficheros de intercambio.
15. Actualizar documentación.
16. Ejecutar aceptación completa y hacer commits pequeños.

No mezclar todos los cambios en un solo commit. Secuencia sugerida:

```text
fix(opencode): correct OpenAI model resolution
feat(config): centralize agent model selection
fix(ring): stream provider errors and bound silent runs
feat(ops): add isolated OpenAI diagnostics
fix(console): resolve Ring PC and LP logs from any worktree
feat(supervisor): report progress and stalled states
feat(usage): record per-run tokens and cost
docs(ops): document model matrix and recovery procedures
```

**English summary:** Fix resolution first, then centralize configuration, harden execution, repair observability, and audit workers and Git automation.

## 19. Checklist de aceptación final

### Modelos

- [ ] Luna responde mediante `OPENAI_API_KEY` y OpenCode.
- [ ] GPT-5.3 Codex responde mediante `OPENAI_API_KEY` y OpenCode.
- [ ] No existe `ProviderModelNotFoundError`.
- [ ] Ring/PC/LP imprimen modelo y variante no sensibles al arrancar.
- [ ] Override local funciona.
- [ ] Qwen/Ollama fallback conserva su ID exacto.

### Ring

- [ ] Produce eventos visibles antes del timeout de primer byte.
- [ ] Error de proveedor aparece en consola inmediatamente.
- [ ] No publica revisión si OpenCode falla.
- [ ] Un ciclo completo actualiza review, summary y state.
- [ ] No hay dos Rings escritores.
- [ ] Timeout y fallback tienen tests.

### Consolas y supervisor

- [ ] Opciones `1`, `2` y `3` funcionan desde cualquier worktree.
- [ ] Se distingue liveness de progreso.
- [ ] El guardian no inunda el log con `healthy`.
- [ ] Un log vacío o estancado genera un estado preciso.

### PC/LP

- [ ] PC termina suite, diff, commit y push.
- [ ] LP tiene tarea, log y estado verificables.
- [ ] Ambos reciben configuración común.

### Git

- [ ] Timer único y frecuencia confirmada.
- [ ] Lock activo.
- [ ] Sin force push ni resolución automática de conflictos.
- [ ] Ficheros de intercambio incluidos.
- [ ] Logs/PIDs/uso/secretos excluidos.

### Consumo

- [ ] Tokens y coste se registran por ejecución.
- [ ] Ring usa deltas, no logs completos.
- [ ] Límites opcionales funcionan si se configuran.
- [ ] Ninguna clave aparece en logs o Git.

**English summary:** Acceptance requires working models, bounded Ring cycles, reliable consoles, verified workers, safe Git automation, and measurable usage.

## 20. Tareas históricas revisadas pero no incorporadas al backlog principal

Estas aparecían en memorandos antiguos. No deben reactivarse automáticamente porque corresponden a benchmarks o caminos anteriores:

### LEGACY — Benchmark Spring/JPA/REST/Angular de julio

Pendientes históricos detectados:

- pruebas JPA con `flush()` y `clear()`;
- probar persistencia a través del servicio, no solo repositorios;
- eliminar IDs hardcoded en MockMvc;
- reemplazar `String.contains()` sin aserción por `jsonPath`/AssertJ;
- test de `FrontendController` e índice;
- verificar assets JS/CSS y Content-Type;
- usar Reactive Forms si el requisito sigue vigente;
- separar selección de autor de selección de autores del libro;
- usar el endpoint de títulos en vez de filtrar solo en cliente.

Solo convertirlos en tareas actuales si esos mismos módulos siguen formando parte del repositorio R4R vigente.

### LEGACY — Cline 64K, Ornith, OpenClaw y Telegram

- parchear Cline para `num_ctx=65536`;
- repetir benchmarks Ornith/Qwen;
- Brave search;
- OpenClaw remoto/Telegram.

Fueron experimentos anteriores y no pertenecen al cierre actual centrado en Ring/OpenCode/PC/LP.

**English summary:** Old benchmark defects remain documented, but they must not contaminate the current Ring/OpenCode implementation without repository evidence.

## 21. Prompt de arranque para el siguiente chat

Copiar este bloque junto con el presente fichero:

```text
Trabaja sobre el repositorio real y usa R4R_BACKLOG_IMPLEMENTACION_SIGUIENTE_CHAT_2026-08-05.md como handoff, no como prueba del estado actual. Empieza con la auditoría P0 y clasifica cada tarea por evidencia. No sobrescribas cambios posteriores a cccd08f, no uses Codex SDK/OpenCode SDK, no mezcles OAuth con OPENAI_API_KEY y no arranques dos Rings escritores. La matriz vigente usa Luna para Ring y tareas baratas, GPT-5.3 Codex para PC/LP, y conserva Qwen/Ollama solo como fallback. Implementa en commits pequeños, ejecuta las pruebas indicadas y actualiza el checklist con hashes, comandos y resultados reales.
```

**English summary:** The next chat must verify reality first, follow the current model matrix, preserve later work, and implement in small validated commits.

## 22. Fuentes técnicas

- OpenAI Luna: <https://developers.openai.com/api/docs/models/gpt-5.6-luna>
- OpenAI GPT-5.3 Codex: <https://developers.openai.com/api/docs/models/gpt-5.3-codex>
- Modelos Codex: <https://learn.chatgpt.com/docs/models>
- Modelos OpenCode: <https://opencode.ai/docs/models/>
- Configuración OpenCode: <https://opencode.ai/docs/config/>
- CLI OpenCode: <https://opencode.ai/docs/cli/>

Fin del documento.
