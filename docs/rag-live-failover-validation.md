# Validación real de R4R y sus failovers

Fecha: 8 de noviembre de 2026. Rama: `main`; revisión inicial: `b8c4ff25f62cac5b165a6d1347f1daafb6523e5e` (árbol inicialmente limpio).
Inicio: **14:45:25 UTC**. Límite autorizado: **17:45:25 UTC**. Todas las horas siguientes son UTC, salvo las líneas de Spring, que incluyen `+01:00`.

## Alcance y método

Se ha ejecutado el código de esta rama en un Spring MVC local aislado contra **PostgreSQL, AWS S3 Vectors, los tres Ollama y OpenAI reales**. No se ha desplegado este binario en Synology. Los fallos se provocan cerrando exclusivamente proxies TCP locales de esta prueba, después de arrancar Spring. No se han apagado equipos, contenedores ni servicios existentes, ni cambiado rutas o firewall.

El controlador reproducible es `py-scripts/r4r_live_failover_validation.py`; los auxiliares Java están en `src/test/java/com/riansares/r4r/validation/`. Son herramientas optativas: `mvn verify` no inicia estos procesos ni llamadas de pago. Los resultados individuales, eventos SSE, salidas del modelo y trazas redactadas se conservan localmente en `target/live-failover/` (ignorado por Git). Las tablas de este informe conservan las conclusiones sin depender de esos archivos locales.

### Casos originales, sin cambiar preguntas ni expectativas

La descripción de la tarea no coincide completamente con las clases actuales. **Humedades es el caso 09**, no el 10 (10 es Plazos). El caso EN 01 existente es Servicios, no la traducción de Primera orientación; no existe esa traducción en la batería inglesa. Se conserva y ejecuta EN 01 literalmente, sin inventar otra pregunta. Por tanto, no se afirma haber probado una pareja semánticamente equivalente de Primera orientación ES/EN.

| ID | Idioma | Pregunta literal | Expectativa original |
|---|---|---|---|
| 01 | ES | Si quiero una orientación inicial sobre mi reforma, ¿qué detalles debería facilitar? | Respuesta sustantiva y citada. |
| 09 | ES | ¿Qué causas de humedad pueden aparecer en una vivienda antigua? | Respuesta sustantiva y citada. |
| 13 | ES | ¿De qué manera producen alimento las plantas mediante la fotosíntesis? | Abstención, respuesta vacía, sin citas. |
| 01 | EN | What kinds of renovation work does this company do? Does it work on houses and offices? | Respuesta inglesa sustantiva y citada. |
| 09 | EN | What causes of damp can occur in an old home? | Respuesta inglesa sustantiva y citada. |
| 13 | EN | How do plants make their food through photosynthesis? | Abstención, respuesta vacía, sin citas. |

Fuentes: `RagLiveQuestionsIT.java` y `RagLiveEnglishQuestionsIT.java`. El controlador lee los casos de esos archivos. Las peticiones inglesas de la batería original omiten `retrievalQuestion`, igual que el IT actual: ejercitan la traducción local de respaldo. La ruta de traducción externa del frontend se comprueba por separado. Los tests originales y sus expectativas no se han modificado.

## Configuración y protección de datos

Orden efectivo verificado, no deducido por posición:

- Generación: PC `192.168.1.207` / `qwen3:8b` → portátil `192.168.1.209` / `qwen2.5:7b` → OpenAI / `gpt-6-luna`.
- Embeddings: portátil `192.168.1.209` → PC `192.168.1.207` → NAS `192.168.1.203`, con `nomic-embed-text:latest`, 768 dimensiones y digest configurado obligatorio.
- Lectura vectorial: PostgreSQL/pgvector → AWS S3 Vectors. Índice real: `amzn-r4r-vectors/amzn-r4r-vector-index`, `eu-north-1`, coseno, 768 dimensiones.
- Escrituras: PostgreSQL exclusivamente; S3 permanece como respaldo de lectura. No se ha ejecutado ingesta ni reindexado el corpus productivo.

Spring importa primero `.env.base` y después `.env`; Compose usa los defaults públicos mediante `include.env_file`, con las sobrescrituras privadas habituales y listas explícitas por servicio. Se ha verificado además el entorno efectivo de un contenedor API efímero local, sin iniciar Spring ni dependencias: proveedores públicos, tres flags privados `true`, región correcta y credenciales AWS presentes. No se ha mostrado ningún valor secreto. Las variables comentadas no sobrescriben `.env.base`. El SDK usa su cadena estándar por entorno; no se han leído perfiles AWS ni implementado autenticación propia.

El `.env` privado no se ha abierto ni modificado manualmente. Solo lo carga la configuración habitual de Spring/Compose. `LiveFailoverExecution` transmite los secretos resueltos al entorno del proceso hijo, nunca a argumentos ni archivos; esto permite que la cadena estándar AWS los reciba también al ejecutar Java localmente.

Antes del arranque real se han comprobado los inicializadores y se han forzado: `spring.flyway.enabled=false`, `spring.sql.init.mode=never`, `spring.ai.vectorstore.pgvector.initialize-schema=false`, `r4r.catalog-on-startup=false`, Hikari `read-only=true` y `SET default_transaction_read_only=on`. Se confirma `transaction_read_only=on` mediante JDBC. El pool de prueba tiene máximo 2 conexiones y espera de 3 s. La configuración productiva de Flyway permanece intacta.

Los IT Maven que escriben usan únicamente `192.168.1.203:5433/r4r_rag_test`, protegidos por `TestDatabaseGuard`; no usan la BBDD productiva `:5432/r4r_rag`.

## Fallos encontrados y correcciones

1. **Idioma de Qwen3:** algunas respuestas inglesas copiaban listas técnicas en español. La comprobación original de idioma (presencia de alguna palabra inglesa) no lo detectaba; se registraron esos intentos como FAIL en la revisión adicional. Se ha reforzado la instrucción inglesa en el sistema y al final del mensaje de usuario y fijado temperatura local 0, configurable mediante `RAG_GENERATION_TEMPERATURE`. No se cambian modelos, umbrales, fuentes ni validación de citas.
2. **Qwen2.5 con humedades EN:** se observó una traducción defectuosa (`humedazl`). Se completa el glosario existente con `damp/dampness = humedad` y `old home = vivienda antigua`. Después se capturó una respuesta pertinente con citas que terminaba en `__R4R_ABSTAIN__`; el backend la rechazaba correctamente. Se conserva esa protección y se elimina la instrucción duplicada del token en el mensaje de usuario, manteniéndola y aclarando su exclusividad en el sistema. El problema se reproduce con 2.148 tokens de entrada, por debajo del contexto observado de 4.096: no se atribuye sin evidencia a truncamiento. No se descartan tokens para aparentar éxito.
3. **Portátil en frío:** un intento real excedió 30 s y saltó a Luna. El timeout público del portátil pasa a 60 s. Se mantiene el orden, el límite SSE de 180 s y el timeout de traducción de 30 s. No se han descargado modelos de memoria para fabricar otro arranque en frío; la mejora del caso frío completo queda pendiente de observación natural.
4. **Fixture de precedencia:** `PublicRagEnvironmentTest` estaba heredando variables reales del proceso por encima de sus datos ficticios. Se aísla su fuente de entorno para probar exclusivamente la precedencia entre archivos sintéticos. Un primer fallo de aserción había incluido una contraseña en tres artefactos locales ignorados; se redactaron inmediatamente, sin copiar su valor al informe ni a Git. Las ejecuciones posteriores y el escaneo final comprueban que no queda en los artefactos revisados.
5. **Controlador de prueba:** se añadió espera de disponibilidad antes de consultar y una copia inmutable del JAR bajo `target/live-failover/`. Así una reconstrucción Maven no reemplaza el JAR que está ejecutándose. Los errores anteriores de arranque del controlador y `ClassNotFoundException` por sustitución del JAR se distinguen de fallos del producto.

La versión anterior del prompt es reproducible en el commit inicial citado y en el diff de `CitedRagService.java`; las trazas y salidas de cada variante se conservan separadamente. No se debilitan abstenciones, citas ni filtros para aprobar las pruebas.

6. **Traducción externa y recuperación:** la frase literal de MyMemory `trabajos de renovación` recuperaba cinco fragmentos sin oficinas. Se canoniza esa expresión a `reformas` únicamente para búsqueda; no se pierde la pregunta original ni se cambian proveedor, vectores, umbral o Top-K. Una regresión nueva en `CitedRagMultilingualRetrievalTest` comprueba el embedding de la consulta canonizada, la ausencia de otra traducción y la conservación de la pregunta original.

## Matriz real de validación final

La matriz base de rutas (`acceptance-*`, excluyendo MyMemory) obtuvo **48/48 PASS**. Después de la última normalización léxica se ejecutaron **12/12 PASS** adicionales (`canonical-*`) y **2/2 PASS** de recuperación final. No se repitió innecesariamente toda la matriz: la última corrección solo canoniza una expresión de búsqueda; sus rutas afectadas se comprobaron con PC, portátil, S3 y Luna. Los resultados anteriores permanecen identificados por escenario. Se revisaron respuestas y fuentes; quedan observaciones de calidad terminológica más abajo.

| Escenario | Caso | Idioma | Proveedor esperado (almacén / modelo) | Proveedor real observado | Resultado | Tiempo cliente | Evidencia | Observaciones |
|---|---|---|---|---|---|---:|---|---|
| Normal | 01 | EN | pgvector / qwen3:8b | pgvector / qwen3:8b | PASS | 5.711 s | pgvector:success; `acceptance-normal-sse-en-01.json` | 5 fragmentos; 3 citas válidas. |
| Normal | 09 | EN | pgvector / qwen3:8b | pgvector / qwen3:8b | PASS | 4.169 s | pgvector:success; `acceptance-normal-sse-en-09.json` | 5 fragmentos; 1 citas válidas. |
| Normal | 13 | EN | pgvector / qwen3:8b | pgvector / qwen3:8b | PASS | 1.730 s | pgvector:success; `acceptance-normal-sse-en-13.json` | Abstención vacía, sin citas. |
| Normal | 01 | ES | pgvector / qwen3:8b | pgvector / qwen3:8b | PASS | 4.060 s | pgvector:success; `acceptance-normal-sse-es-01.json` | 5 fragmentos; 1 citas válidas. |
| Normal | 09 | ES | pgvector / qwen3:8b | pgvector / qwen3:8b | PASS | 2.748 s | pgvector:success; `acceptance-normal-sse-es-09.json` | 5 fragmentos; 1 citas válidas. |
| Normal | 13 | ES | pgvector / qwen3:8b | pgvector / qwen3:8b | PASS | 1.154 s | pgvector:success; `acceptance-normal-sse-es-13.json` | Abstención vacía, sin citas. |
| PC bloqueado → portátil | 01 | EN | pgvector / qwen2.5:7b | pgvector / qwen2.5:7b | PASS | 10.181 s | pgvector:success; `acceptance-laptop-sse-en-01.json` | 5 fragmentos; 1 citas válidas. |
| PC bloqueado → portátil | 09 | EN | pgvector / qwen2.5:7b | pgvector / qwen2.5:7b | PASS | 10.735 s | pgvector:success; `acceptance-laptop-sse-en-09.json` | 5 fragmentos; 1 citas válidas. |
| PC bloqueado → portátil | 13 | EN | pgvector / qwen2.5:7b | pgvector / qwen2.5:7b | PASS | 6.402 s | pgvector:success; `acceptance-laptop-sse-en-13.json` | Abstención vacía, sin citas. |
| PC bloqueado → portátil | 01 | ES | pgvector / qwen2.5:7b | pgvector / qwen2.5:7b | PASS | 7.170 s | pgvector:success; `acceptance-laptop-sse-es-01.json` | 5 fragmentos; 1 citas válidas. |
| PC bloqueado → portátil | 09 | ES | pgvector / qwen2.5:7b | pgvector / qwen2.5:7b | PASS | 11.747 s | pgvector:success; `acceptance-laptop-sse-es-09.json` | 5 fragmentos; 1 citas válidas. |
| PC bloqueado → portátil | 13 | ES | pgvector / qwen2.5:7b | pgvector / qwen2.5:7b | PASS | 6.347 s | pgvector:success; `acceptance-laptop-sse-es-13.json` | Abstención vacía, sin citas. |
| PG bloqueado → S3 | 01 | EN | s3vectors / qwen3:8b | s3vectors / qwen3:8b | PASS | 8.694 s | pgvector:failed → s3vectors:success; `acceptance-s3-sse-en-01.json` | 5 fragmentos; 3 citas válidas. |
| PG bloqueado → S3 | 09 | EN | s3vectors / qwen3:8b | s3vectors / qwen3:8b | PASS | 8.040 s | pgvector:failed → s3vectors:success; `acceptance-s3-sse-en-09.json` | 5 fragmentos; 1 citas válidas. |
| PG bloqueado → S3 | 13 | EN | s3vectors / qwen3:8b | s3vectors / qwen3:8b | PASS | 4.948 s | pgvector:failed → s3vectors:success; `acceptance-s3-sse-en-13.json` | Abstención vacía, sin citas. |
| PG bloqueado → S3 | 01 | ES | s3vectors / qwen3:8b | s3vectors / qwen3:8b | PASS | 6.037 s | pgvector:failed → s3vectors:success; `acceptance-s3-sse-es-01.json` | 5 fragmentos; 1 citas válidas. |
| PG bloqueado → S3 | 09 | ES | s3vectors / qwen3:8b | s3vectors / qwen3:8b | PASS | 5.955 s | pgvector:failed → s3vectors:success; `acceptance-s3-sse-es-09.json` | 5 fragmentos; 1 citas válidas. |
| PG bloqueado → S3 | 13 | ES | s3vectors / qwen3:8b | s3vectors / qwen3:8b | PASS | 4.357 s | pgvector:failed → s3vectors:success; `acceptance-s3-sse-es-13.json` | Abstención vacía, sin citas. |
| PG restaurado / REST | 01 | EN | pgvector / qwen3:8b | pgvector / qwen3:8b | PASS | 5.378 s | pgvector:success; `acceptance-rest-recovery-rest-en-01.json` | 5 fragmentos; 3 citas válidas. |
| PG restaurado / REST | 09 | EN | pgvector / qwen3:8b | pgvector / qwen3:8b | PASS | 4.199 s | pgvector:success; `acceptance-rest-recovery-rest-en-09.json` | 5 fragmentos; 1 citas válidas. |
| PG restaurado / REST | 13 | EN | pgvector / qwen3:8b | pgvector / qwen3:8b | PASS | 1.603 s | pgvector:success; `acceptance-rest-recovery-rest-en-13.json` | Abstención vacía, sin citas. |
| PG restaurado / REST | 01 | ES | pgvector / qwen3:8b | pgvector / qwen3:8b | PASS | 3.457 s | pgvector:success; `acceptance-rest-recovery-rest-es-01.json` | 5 fragmentos; 1 citas válidas. |
| PG restaurado / REST | 09 | ES | pgvector / qwen3:8b | pgvector / qwen3:8b | PASS | 2.719 s | pgvector:success; `acceptance-rest-recovery-rest-es-09.json` | 5 fragmentos; 1 citas válidas. |
| PG restaurado / REST | 13 | ES | pgvector / qwen3:8b | pgvector / qwen3:8b | PASS | 0.769 s | pgvector:success; `acceptance-rest-recovery-rest-es-13.json` | Abstención vacía, sin citas. |
| Locales bloqueados → Luna | 01 | EN | pgvector / gpt-6-luna | pgvector / gpt-6-luna | PASS | 4.269 s | pgvector:success; `acceptance-luna-sse-en-01.json` | 5 fragmentos; 2 citas válidas. |
| Locales bloqueados → Luna | 09 | EN | pgvector / gpt-6-luna | pgvector / gpt-6-luna | PASS | 4.121 s | pgvector:success; `acceptance-luna-sse-en-09.json` | 5 fragmentos; 2 citas válidas. |
| Locales bloqueados → Luna | 13 | EN | pgvector / gpt-6-luna | pgvector / gpt-6-luna | PASS | 3.233 s | pgvector:success; `acceptance-luna-sse-en-13.json` | Abstención vacía, sin citas. |
| Locales bloqueados → Luna | 01 | ES | pgvector / gpt-6-luna | pgvector / gpt-6-luna | PASS | 2.819 s | pgvector:success; `acceptance-luna-sse-es-01.json` | 5 fragmentos; 1 citas válidas. |
| Locales bloqueados → Luna | 09 | ES | pgvector / gpt-6-luna | pgvector / gpt-6-luna | PASS | 2.147 s | pgvector:success; `acceptance-luna-sse-es-09.json` | 5 fragmentos; 2 citas válidas. |
| Locales bloqueados → Luna | 13 | ES | pgvector / gpt-6-luna | pgvector / gpt-6-luna | PASS | 1.807 s | pgvector:success; `acceptance-luna-sse-es-13.json` | Abstención vacía, sin citas. |
| Embeddings LP bloqueado → PC | 01 | EN | pgvector / qwen3:8b | pgvector / qwen3:8b | PASS | 5.338 s | pgvector:success; `acceptance-embedding-pc-sse-en-01.json` | 5 fragmentos; 3 citas válidas. |
| Embeddings LP bloqueado → PC | 09 | EN | pgvector / qwen3:8b | pgvector / qwen3:8b | PASS | 4.063 s | pgvector:success; `acceptance-embedding-pc-sse-en-09.json` | 5 fragmentos; 1 citas válidas. |
| Embeddings LP bloqueado → PC | 13 | EN | pgvector / qwen3:8b | pgvector / qwen3:8b | PASS | 1.479 s | pgvector:success; `acceptance-embedding-pc-sse-en-13.json` | Abstención vacía, sin citas. |
| Embeddings LP bloqueado → PC | 01 | ES | pgvector / qwen3:8b | pgvector / qwen3:8b | PASS | 2.217 s | pgvector:success; `acceptance-embedding-pc-sse-es-01.json` | 5 fragmentos; 1 citas válidas. |
| Embeddings LP bloqueado → PC | 09 | ES | pgvector / qwen3:8b | pgvector / qwen3:8b | PASS | 2.597 s | pgvector:success; `acceptance-embedding-pc-sse-es-09.json` | 5 fragmentos; 1 citas válidas. |
| Embeddings LP bloqueado → PC | 13 | ES | pgvector / qwen3:8b | pgvector / qwen3:8b | PASS | 0.598 s | pgvector:success; `acceptance-embedding-pc-sse-es-13.json` | Abstención vacía, sin citas. |
| Embeddings LP/PC bloqueados → NAS | 01 | EN | pgvector / qwen3:8b | pgvector / qwen3:8b | PASS | 11.938 s | pgvector:success; `acceptance-embedding-nas-sse-en-01.json` | 5 fragmentos; 3 citas válidas. |
| Embeddings LP/PC bloqueados → NAS | 09 | EN | pgvector / qwen3:8b | pgvector / qwen3:8b | PASS | 9.728 s | pgvector:success; `acceptance-embedding-nas-sse-en-09.json` | 5 fragmentos; 1 citas válidas. |
| Embeddings LP/PC bloqueados → NAS | 13 | EN | pgvector / qwen3:8b | pgvector / qwen3:8b | PASS | 5.887 s | pgvector:success; `acceptance-embedding-nas-sse-en-13.json` | Abstención vacía, sin citas. |
| Embeddings LP/PC bloqueados → NAS | 01 | ES | pgvector / qwen3:8b | pgvector / qwen3:8b | PASS | 8.613 s | pgvector:success; `acceptance-embedding-nas-sse-es-01.json` | 5 fragmentos; 1 citas válidas. |
| Embeddings LP/PC bloqueados → NAS | 09 | ES | pgvector / qwen3:8b | pgvector / qwen3:8b | PASS | 8.313 s | pgvector:success; `acceptance-embedding-nas-sse-es-09.json` | 5 fragmentos; 1 citas válidas. |
| Embeddings LP/PC bloqueados → NAS | 13 | ES | pgvector / qwen3:8b | pgvector / qwen3:8b | PASS | 8.316 s | pgvector:success; `acceptance-embedding-nas-sse-es-13.json` | Abstención vacía, sin citas. |
| Prioridades restauradas / REST | 01 | EN | pgvector / qwen3:8b | pgvector / qwen3:8b | PASS | 5.482 s | pgvector:success; `acceptance-priority-recovery-rest-en-01.json` | 5 fragmentos; 3 citas válidas. |
| Prioridades restauradas / REST | 09 | EN | pgvector / qwen3:8b | pgvector / qwen3:8b | PASS | 4.242 s | pgvector:success; `acceptance-priority-recovery-rest-en-09.json` | 5 fragmentos; 1 citas válidas. |
| Prioridades restauradas / REST | 13 | EN | pgvector / qwen3:8b | pgvector / qwen3:8b | PASS | 1.852 s | pgvector:success; `acceptance-priority-recovery-rest-en-13.json` | Abstención vacía, sin citas. |
| Prioridades restauradas / REST | 01 | ES | pgvector / qwen3:8b | pgvector / qwen3:8b | PASS | 2.341 s | pgvector:success; `acceptance-priority-recovery-rest-es-01.json` | 5 fragmentos; 1 citas válidas. |
| Prioridades restauradas / REST | 09 | ES | pgvector / qwen3:8b | pgvector / qwen3:8b | PASS | 2.601 s | pgvector:success; `acceptance-priority-recovery-rest-es-09.json` | 5 fragmentos; 1 citas válidas. |
| Prioridades restauradas / REST | 13 | ES | pgvector / qwen3:8b | pgvector / qwen3:8b | PASS | 0.709 s | pgvector:success; `acceptance-priority-recovery-rest-es-13.json` | Abstención vacía, sin citas. |
| canonical-mymemory | 01 | EN | pgvector / qwen3:8b | pgvector / qwen3:8b | PASS | 4.325 s | pgvector:success; `canonical-mymemory-sse-en-01.json` | 5 fragmentos; 1 citas válidas. |
| canonical-mymemory | 09 | EN | pgvector / qwen3:8b | pgvector / qwen3:8b | PASS | 3.543 s | pgvector:success; `canonical-mymemory-sse-en-09.json` | 5 fragmentos; 2 citas válidas. |
| canonical-mymemory | 13 | EN | pgvector / qwen3:8b | pgvector / qwen3:8b | PASS | 1.352 s | pgvector:success; `canonical-mymemory-sse-en-13.json` | Abstención vacía, sin citas. |
| canonical-normal | 01 | EN | pgvector / qwen3:8b | pgvector / qwen3:8b | PASS | 6.235 s | pgvector:success; `canonical-normal-sse-en-01.json` | 5 fragmentos; 3 citas válidas. |
| canonical-normal | 09 | EN | pgvector / qwen3:8b | pgvector / qwen3:8b | PASS | 4.442 s | pgvector:success; `canonical-normal-sse-en-09.json` | 5 fragmentos; 1 citas válidas. |
| canonical-normal | 13 | EN | pgvector / qwen3:8b | pgvector / qwen3:8b | PASS | 1.972 s | pgvector:success; `canonical-normal-sse-en-13.json` | Abstención vacía, sin citas. |
| canonical-normal | 01 | ES | pgvector / qwen3:8b | pgvector / qwen3:8b | PASS | 2.263 s | pgvector:success; `canonical-normal-sse-es-01.json` | 5 fragmentos; 1 citas válidas. |
| canonical-normal | 09 | ES | pgvector / qwen3:8b | pgvector / qwen3:8b | PASS | 2.908 s | pgvector:success; `canonical-normal-sse-es-09.json` | 5 fragmentos; 1 citas válidas. |
| canonical-normal | 13 | ES | pgvector / qwen3:8b | pgvector / qwen3:8b | PASS | 0.683 s | pgvector:success; `canonical-normal-sse-es-13.json` | Abstención vacía, sin citas. |
| canonical-s3-mymemory | 01 | EN | s3vectors / qwen3:8b | s3vectors / qwen3:8b | PASS | 7.601 s | pgvector:failed → s3vectors:success; `canonical-s3-mymemory-sse-en-01.json` | 5 fragmentos; 1 citas válidas. |
| canonical-laptop-mymemory | 01 | EN | pgvector / qwen2.5:7b | pgvector / qwen2.5:7b | PASS | 9.084 s | pgvector:success; `canonical-laptop-mymemory-sse-en-01.json` | 5 fragmentos; 1 citas válidas. |
| canonical-luna-mymemory | 01 | EN | pgvector / gpt-6-luna | pgvector / gpt-6-luna | PASS | 4.995 s | pgvector:success; `canonical-luna-mymemory-sse-en-01.json` | 5 fragmentos; 2 citas válidas. |
| final-cleanup-recovery | 09 | EN | pgvector / qwen3:8b | pgvector / qwen3:8b | PASS | 4.100 s | pgvector:success; `final-cleanup-recovery-rest-en-09.json` | 5 fragmentos; 1 citas válidas. |
| final-cleanup-recovery | 01 | ES | pgvector / qwen3:8b | pgvector / qwen3:8b | PASS | 2.320 s | pgvector:success; `final-cleanup-recovery-rest-es-01.json` | 5 fragmentos; 1 citas válidas. |

### Servicio ya desplegado en Synology (sin actualizarlo)

| Escenario | Caso | Idioma | Esperado | Proveedor informado | Resultado | Tiempo | Evidencia | Observaciones |
|---|---|---|---|---|---|---:|---|---|
| NAS rest | 01 | es | Endpoint operativo | qwen3:8b | PASS | 15.706 s | HTTP 200; `deployed-nas-rest-es-01.json` | Almacén/embedding NO VERIFICADOS en ese contenedor; no se atribuyen trazas locales. Contrato, respuesta y citas comprobados. |
| NAS rest | 09 | es | Endpoint operativo | qwen3:8b | PASS | 18.741 s | HTTP 200; `deployed-nas-rest-es-09.json` | Almacén/embedding NO VERIFICADOS en ese contenedor; no se atribuyen trazas locales. Contrato, respuesta y citas comprobados. |
| NAS rest | 13 | es | Endpoint operativo | qwen3:8b | PASS | 11.369 s | HTTP 200; `deployed-nas-rest-es-13.json` | Almacén/embedding NO VERIFICADOS en ese contenedor; no se atribuyen trazas locales. Contrato, respuesta y citas comprobados. |
| NAS sse | 01 | en | Endpoint operativo | — | FAIL | 0.025 s | HTTP 404; `deployed-nas-sse-en-01.json` | Almacén/embedding NO VERIFICADOS en ese contenedor; no se atribuyen trazas locales. SSE ausente. |
| NAS sse | 09 | en | Endpoint operativo | — | FAIL | 0.026 s | HTTP 404; `deployed-nas-sse-en-09.json` | Almacén/embedding NO VERIFICADOS en ese contenedor; no se atribuyen trazas locales. SSE ausente. |
| NAS sse | 13 | en | Endpoint operativo | — | FAIL | 0.018 s | HTTP 404; `deployed-nas-sse-en-13.json` | Almacén/embedding NO VERIFICADOS en ese contenedor; no se atribuyen trazas locales. SSE ausente. |
| NAS rest | 01 | en | Endpoint operativo | qwen3:8b | PASS | 25.197 s | HTTP 200; `deployed-nas-english-rest-rest-en-01.json` | Almacén/embedding NO VERIFICADOS en ese contenedor; no se atribuyen trazas locales. Contrato, respuesta y citas comprobados. |
| NAS rest | 09 | en | Endpoint operativo | qwen3:8b | FAIL | 29.146 s | HTTP 200; `deployed-nas-english-rest-rest-en-09.json` | Almacén/embedding NO VERIFICADOS en ese contenedor; no se atribuyen trazas locales. Revisión semántica: la fuente citada no documenta overflow ni blocked drains. El servicio desplegado no contiene necesariamente las correcciones locales. |
| NAS rest | 13 | en | Endpoint operativo | qwen3:8b | PASS | 26.385 s | HTTP 200; `deployed-nas-english-rest-rest-en-13.json` | Almacén/embedding NO VERIFICADOS en ese contenedor; no se atribuyen trazas locales. Contrato, respuesta y citas comprobados. |


## Métricas observadas

`embeddingMs` es el tiempo instrumentado de llamadas efectivas al modelo compartido; `retrievalIncludingEmbeddingMs` incluye la recuperación y sus embeddings; `generationMs` incluye los intentos y cambios de proveedor de generación; `totalMs` es el total del backend. El tiempo de la matriz es el observado por el cliente. La traducción local previa figura en `normalizationMs` y en eventos de traducción, no se oculta dentro de generación.

`vectorSearchMs` permanece **null**: Spring AI realiza internamente embedding y búsqueda pgvector y no hay un cronómetro separado de la ejecución SQL. No se presenta una resta de tiempos como medida exacta. Al fallar PostgreSQL después de calcular el embedding, su adaptador y el de S3 realizan cada uno su llamada; se observan dos embeddings reales y la métrica los suma. No son consultas repetidas para obtener métricas, pero existe este coste adicional del failover vectorial. No se ha introducido una caché ni cambiado los adaptadores en esta tarea.

| Escenario | Éxitos | Media s | Mediana s | Máximo s |
|---|---:|---:|---:|---:|
| Normal | 6/6 | 3.262 | 3.404 | 5.711 |
| PC bloqueado → portátil | 6/6 | 8.764 | 8.675 | 11.747 |
| PG bloqueado → S3 | 6/6 | 6.338 | 5.996 | 8.694 |
| PG restaurado / REST | 6/6 | 3.021 | 3.088 | 5.378 |
| Locales bloqueados → Luna | 6/6 | 3.066 | 3.026 | 4.269 |
| Embeddings LP bloqueado → PC | 6/6 | 2.715 | 2.407 | 5.338 |
| Embeddings LP/PC bloqueados → NAS | 6/6 | 8.799 | 8.465 | 11.938 |
| Prioridades restauradas / REST | 6/6 | 2.871 | 2.471 | 5.482 |
| canonical-mymemory | 3/3 | 3.073 | 3.543 | 4.325 |
| canonical-normal | 6/6 | 3.084 | 2.585 | 6.235 |
| canonical-s3-mymemory | 1/1 | 7.601 | 7.601 | 7.601 |
| canonical-laptop-mymemory | 1/1 | 9.084 | 9.084 | 9.084 |
| canonical-luna-mymemory | 1/1 | 4.995 | 4.995 | 4.995 |
| final-cleanup-recovery | 2/2 | 3.210 | 3.210 | 4.100 |

Tiempos de backend, en milisegundos; REST conserva su contrato sin estas métricas.

| Caso/escenario | embeddingMs | retrievalIncludingEmbeddingMs | vectorSearchMs | generationMs | totalMs |
|---|---:|---:|---|---:|---:|
| acceptance-normal · en01 | 474.43 | 546.58 | null | 4108.74 | 5708.06 |
| acceptance-normal · en09 | 191.92 | 270.75 | null | 2758.71 | 4166.53 |
| acceptance-normal · en13 | 174.91 | 236.65 | null | 597.41 | 1727.73 |
| acceptance-normal · es01 | 1620.23 | 1803.07 | null | 2253.12 | 4057.17 |
| acceptance-normal · es09 | 180.76 | 253.77 | null | 2491.37 | 2745.91 |
| acceptance-normal · es13 | 398.49 | 461.55 | null | 689.79 | 1151.70 |
| acceptance-laptop · en01 | 203.87 | 271.01 | null | 6140.63 | 10177.66 |
| acceptance-laptop · en09 | 380.67 | 464.08 | null | 7060.25 | 10732.86 |
| acceptance-laptop · en13 | 411.34 | 476.63 | null | 3155.69 | 6399.54 |
| acceptance-laptop · es01 | 245.81 | 306.08 | null | 6861.16 | 7167.84 |
| acceptance-laptop · es09 | 1198.06 | 1272.36 | null | 10471.16 | 11743.89 |
| acceptance-laptop · es13 | 976.34 | 1038.39 | null | 5305.84 | 6344.48 |
| acceptance-s3 · en01 | 509.14 | 3621.29 | null | 4043.35 | 8691.77 |
| acceptance-s3 · en09 | 965.63 | 4186.25 | null | 2744.59 | 8038.57 |
| acceptance-s3 · en13 | 392.68 | 3616.24 | null | 422.89 | 4946.02 |
| acceptance-s3 · es01 | 561.69 | 3949.74 | null | 2085.37 | 6035.49 |
| acceptance-s3 · es09 | 403.56 | 3624.45 | null | 2328.53 | 5953.59 |
| acceptance-s3 · es13 | 671.41 | 3895.68 | null | 458.90 | 4354.88 |
| acceptance-luna · en01 | 421.91 | 589.69 | null | 2063.84 | 4266.92 |
| acceptance-luna · en09 | 211.82 | 290.72 | null | 2531.81 | 4118.20 |
| acceptance-luna · en13 | 297.10 | 367.32 | null | 877.00 | 3230.55 |
| acceptance-luna · es01 | 181.17 | 240.46 | null | 2577.01 | 2817.73 |
| acceptance-luna · es09 | 148.73 | 227.39 | null | 1917.13 | 2144.78 |
| acceptance-luna · es13 | 195.39 | 258.43 | null | 1546.26 | 1804.91 |
| acceptance-embedding-pc · en01 | 127.62 | 198.54 | null | 4102.62 | 5335.39 |
| acceptance-embedding-pc · en09 | 123.13 | 200.00 | null | 2758.86 | 4061.67 |
| acceptance-embedding-pc · en13 | 78.69 | 144.11 | null | 434.08 | 1477.62 |
| acceptance-embedding-pc · es01 | 129.74 | 190.05 | null | 2024.94 | 2215.23 |
| acceptance-embedding-pc · es09 | 118.60 | 235.33 | null | 2359.77 | 2595.37 |
| acceptance-embedding-pc · es13 | 79.92 | 139.28 | null | 457.16 | 596.69 |
| acceptance-embedding-nas · en01 | 6729.65 | 6799.30 | null | 4116.07 | 11936.39 |
| acceptance-embedding-nas · en09 | 5799.72 | 5881.07 | null | 2726.44 | 9726.14 |
| acceptance-embedding-nas · en13 | 4478.59 | 4545.55 | null | 434.76 | 5885.78 |
| acceptance-embedding-nas · es01 | 6471.63 | 6534.87 | null | 2076.95 | 8612.16 |
| acceptance-embedding-nas · es09 | 5829.86 | 5941.86 | null | 2369.37 | 8311.55 |
| acceptance-embedding-nas · es13 | 7773.38 | 7836.63 | null | 477.57 | 8314.44 |
| canonical-mymemory · en01 | 201.66 | 318.02 | null | 3403.69 | 3724.85 |
| canonical-mymemory · en09 | 197.81 | 270.12 | null | 2750.24 | 3021.45 |
| canonical-mymemory · en13 | 197.72 | 258.00 | null | 513.36 | 771.93 |
| canonical-normal · en01 | 213.83 | 284.54 | null | 4851.25 | 6233.16 |
| canonical-normal · en09 | 455.48 | 574.69 | null | 2737.77 | 4439.56 |
| canonical-normal · en13 | 191.57 | 252.82 | null | 794.87 | 1970.31 |
| canonical-normal · es01 | 197.35 | 259.90 | null | 1999.21 | 2259.74 |
| canonical-normal · es09 | 452.83 | 567.79 | null | 2336.49 | 2904.82 |
| canonical-normal · es13 | 184.57 | 206.49 | null | 473.58 | 680.39 |
| canonical-s3-mymemory · en01 | 373.46 | 3752.58 | null | 3343.54 | 7096.67 |
| canonical-laptop-mymemory · en01 | 197.83 | 330.17 | null | 8275.34 | 8605.93 |
| canonical-luna-mymemory · en01 | 280.21 | 348.01 | null | 4145.09 | 4493.44 |

## Compatibilidad de embeddings y conservación del corpus

Digest de `nomic-embed-text:latest` verificado en los tres servidores:
`0a109f422b47e3a30ba2b10eca18548e944e8a23073ee3f3e947efcf3c45e59f`.

Además de modelo, digest y 768 dimensiones, se recalculó **una muestra existente** y se comparó contra su vector almacenado, sin escribirlo:

| Proveedor | Coseno frente al vector almacenado | Diferencia absoluta máxima | Tiempo muestra |
|---|---:|---:|---:|
| PC | 1,0000000007 (redondeo float) | 3,7 × 10⁻⁹ | 182 ms |
| NAS | 0,9999957082 | 0,000398 | 47.390 ms |
| Portátil | 0,9999959287 | 0,000400 | 203 ms |

Es evidencia de compatibilidad del mismo espacio vectorial, no únicamente de igual dimensión; no es una recomputación exhaustiva de los 282 vectores. Los fallbacks de consultas se validan además con respuestas y citas reales. La muestra documental larga en NAS tarda más que el timeout de 30 s / presupuesto de embeddings de 35 s: **no se declara validada la ingesta de documentos largos mediante ese respaldo**. No se ha ampliado ese presupuesto ni reindexado datos.

PostgreSQL antes/después: **282 filas**, huella de IDs, contenidos, metadatos y vectores `0577c3880324e1dc0ab760ba31838746`, idéntica. S3 antes/después: **282 claves**, huella `dcaa3496d1c7bac286e24d0e3fdb77a957f44ec02298c05e190ddbf949204433`, idéntica. La huella S3 compara claves y cantidad, no todos los valores internos; las operaciones ejecutadas fueron únicamente lecturas. No se ha modificado el corpus ni su esquema.

## Pruebas automatizadas y comprobaciones adicionales

- **PASSED:** `mvn -q verify` final: **275 unitarias**, **28 IT**, cero fallos/errores. Las integraciones PostgreSQL utilizan exclusivamente la base de pruebas guardada.
- **SKIPPED (preexistentes):** 6 entradas optativas de Failsafe: fábricas/reportes live ES y EN (4), warmup live (1) y Luna live (1). No se modificaron sus condiciones ni se excluyeron pruebas. Sus escenarios reducidos se ejecutaron realmente mediante el controlador; no se ejecutaron las 15 preguntas por idioma, fuera del alcance indicado.
- **PASSED:** suite Node existente `node --test front-athagon-r4r-static/js/app.test.cjs` (1 archivo de prueba), prueba Python de Compose y `docker compose config --quiet` habitual. Compose instalado: 2.40.3. No se imprimió configuración resuelta.
- **Mocks / HTTP simulado:** failover vectorial 25 tests, embeddings 22, routing generación 5, seguridad 20, stream controller 9, streaming 6, warmup 11, runtime 10; incluidos en las cifras Maven anteriores, no sumarlos otra vez. `RagStreamHttpIT` aporta 5 pruebas HTTP reales con generación simulada, incluidas cancelación y timeout.
- **REAL, aplicación aislada:** los tres embeddings inaccesibles producen REST **503** y SSE **ERROR sin ANSWER_READY**; no activan indebidamente S3. Nueve preflights REST/SSE/runtime aceptan los dos orígenes legítimos y rechazan el prohibido con 403.
- **Integración con fallos de transporte simulados:** PC y portátil bloqueados, más CONNECT OpenAI rechazado antes de TLS: REST **503**, SSE **ERROR**, fallos observados de los tres modelos, sin ANSWER_READY. Dos CONNECT observados; no se enviaron claves ni se contactó OpenAI en esa simulación. Los tres flags siguen true.
- **REAL:** desconexión del cliente después de GENERATION_STARTED y posterior consulta correcta. Cuatro workers SSE finales en WAITING, ociosos en su cola; sin tareas bloqueadas. No se demuestra que Ollama interrumpa instantáneamente su cómputo remoto al cerrar el cliente; los timeouts siguen limitando el transporte.
- Los eventos llegan antes de ANSWER_READY y contienen los cambios de proveedor. La respuesta textual completa se emite al final, no en tokens: no existe una respuesta textual parcialmente entregada que haya que reconstruir durante el failover.
- **PASSED:** limpieza de los ocho listeners propios (API, control y seis proxies), procesos propios detenidos, tres Ollama disponibles al cierre, flags reales true, huellas sin cambios y `git diff --check`.
- **PASSED:** escaneo final de valores secretos en los archivos de esta tarea y artefactos concretos, sin abrir `.env`: ninguna coincidencia en fuentes y ninguna redacción adicional necesaria.


## Incidencias históricas y limitaciones

No se afirma que todos los intentos hayan pasado. Se conservan los fallos iniciales, las variantes intermedias y los fallos del servicio desplegado. Los seis errores `normal-fixed` fueron del controlador sin API lista; el primer gate restringido también falló por sockets/attach Mockito bloqueados por el sandbox y pasó al ejecutarlo con los permisos necesarios. Un intento del portátil usó legítimamente embeddings PC por timeout de la sonda LP: el requisito adicional de proveedor fijo del controlador lo marcó FAIL aunque la recuperación por respaldo era correcta. Lo mismo ocurrió en `both-local-proxies-down`. No se atribuyeron esos tiempos al LP.

Las revisiones de idioma adicionales son más estrictas que el IT original, cuyo comentario permite términos técnicos en español. Las respuestas finales satisfacen el contrato y son comprensibles, pero Qwen aún puede citar expresiones españolas o traducir literalmente términos como “covers/channels” o “fugues”. **Queda pendiente pulir esa terminología**, sin sustituir proveedores por calidad ni añadir otra inferencia automática. La normalización final sí corrige la ausencia de oficinas en la ruta MyMemory: antes 0/5 fragmentos con oficinas; después 3/5, con las mismas dimensiones, Top-K y score. Se revalidó la respuesta completa con citas en PC, portátil, S3 y Luna.

| Intento histórico o servicio desplegado | Fallo observado |
|---|---|
| `acceptance-mymemory-sse-en-01.json` | Respuesta parcial: no recuperó oficinas pese a existir en el corpus; corregido por normalización léxica. |
| `both-local-proxies-down-sse-es-01.json` | Expected embedding provider not observed |
| `consolidated-english-check-sse-en-09.json` | Unexpected abstention; No substantive answer; Missing citations; English language check failed; Missing/unresolved citation references |
| `deployed-nas-english-rest-rest-en-09.json` | Revisión semántica: la fuente citada no documenta overflow ni blocked drains. El servicio desplegado no contiene necesariamente las correcciones locales. |
| `deployed-nas-sse-en-01.json` | HTTPError |
| `deployed-nas-sse-en-09.json` | HTTPError |
| `deployed-nas-sse-en-13.json` | HTTPError |
| `embedding-nas-final-sse-en-01.json` | Unexpected abstention; No substantive answer; Missing citations; English language check failed; Missing/unresolved citation references |
| `embedding-nas-final-sse-en-09.json` | Manual English quality review: source list remains in Spanish |
| `embedding-recovered-final-sse-en-09.json` | Manual English quality review: source list remains in Spanish |
| `laptop-glossary-check-sse-en-09.json` | Unexpected abstention; No substantive answer; Missing citations; English language check failed; Missing/unresolved citation references |
| `laptop-token-check-sse-en-09.json` | Unexpected abstention; No substantive answer; Missing citations; English language check failed; Missing/unresolved citation references |
| `local-laptop-generation-sse-en-01.json` | Expected embedding provider not observed |
| `local-laptop-generation-sse-en-09.json` | Unexpected abstention; No substantive answer; Missing citations; English language check failed; Missing/unresolved citation references |
| `local-laptop-generation-sse-es-01.json` | Unexpected generation model |
| `normal-fixed-sse-en-01.json` | URLError |
| `normal-fixed-sse-en-09.json` | URLError |
| `normal-fixed-sse-en-13.json` | URLError |
| `normal-fixed-sse-es-01.json` | URLError |
| `normal-fixed-sse-es-09.json` | URLError |
| `normal-fixed-sse-es-13.json` | URLError |
| `normal-sse-en-09.json` | Manual language review: untranslated Spanish list |
| `pg-down-s3-sse-en-09.json` | Manual language review: untranslated explanatory list |
| `s3-final-sse-en-09.json` | Manual language review: untranslated explanatory list |
| `system-embedding-nas-sse-en-09.json` | Manual English quality review: source list remains in Spanish |
| `terminology-english-check-sse-en-09.json` | FAIL: Spanish technical list copied into English answer |
| `verified-normal-sse-en-09.json` | FAIL: Spanish technical list copied into English answer |

## Preparación para publicar

**Los failovers de lectura, embeddings y generación han sido observados realmente, pero el MVP no está listo para publicarse por la URL pública sin acciones manuales.**

1. `r4r-api.athagon.tech` devuelve **NXDOMAIN**: fallo local y Status=3 en los resolvedores DNS de Google y Cloudflare. `athagon.tech` sí resuelve. Revisar el registro DNS y el hostname del túnel; no se han modificado.
2. La API actual del NAS (`:18080`) devuelve **404 en POST /api/rag/answers/stream**. REST responde en ambos idiomas, pero la revisión de EN09 detectó “overflow / blocked drains”, no sustentado por la fuente citada. No debe confundirse el servicio antiguo con el binario local corregido. Preparar y revisar la imagen actual y recrear únicamente API cuando el usuario autorice; conservar `.env` y sus tres flags true.
3. Compose usa `image: r4r-api:0.3.2`, sin bloque build. Su Dockerfile copia `docker-spring/app.jar`: preparar ese JAR y construir/cargar la imagen conforme al procedimiento habitual antes de recrear API. No basta con copiar fuentes o ejecutar un `compose build` sin servicio build configurado. Nada de esto se ha ejecutado en Synology.
4. Después del despliegue manual, repetir los seis casos por REST y SSE en NAS y URL pública; verificar eventos sin buffering a través del túnel y revisar citas/idioma en navegador. **NO VERIFICADO:** navegador real y streaming mediante Cloudflare, bloqueados por el estado desplegado/DNS.
5. **NO VERIFICADO:** nueva espera de 60 s con un arranque genuinamente frío del portátil; la validación final fue con modelos cargados. No se descargaron modelos para forzar frío. Tampoco se validó ingesta productiva ni todos los vectores individualmente, por las restricciones de datos.
6. El failover vectorial se probó con Spring ya iniciado. El arranque normal mantiene Flyway; no se garantiza arranque normal sin PostgreSQL. El perfil especial `s3-readonly` conserva sus verificaciones automatizadas, sin modificarlo ni confundirlo con el failover normal.

Comprobaciones manuales tras autorizar el despliegue:

```bash
curl -i -X OPTIONS http://192.168.1.203:18080/api/rag/answers/stream -H 'Origin: http://127.0.0.1:8000' -H 'Access-Control-Request-Method: POST' -H 'Access-Control-Request-Headers: content-type'
curl -N --max-time 190 http://192.168.1.203:18080/api/rag/answers/stream -H 'Origin: http://127.0.0.1:8000' -H 'Content-Type: application/json' -H 'Accept: text/event-stream' --data '{"question":"Si quiero una orientación inicial sobre mi reforma, ¿qué detalles debería facilitar?","responseLanguage":"es"}'
```

Repetir por HTTPS público cuando resuelva DNS. Para REST utilizar `/api/rag/answers`. El frontend HTTPS debe usar la API HTTPS; un navegador puede bloquear un endpoint HTTP privado por contenido mixto, aunque el mismo curl funcione. No se ha añadido fallback automático de protocolos ni desactivado CORS/seguridad.


## Reproducción segura

Desde la raíz del repositorio, con la configuración habitual disponible para Spring:

```bash
mvn -q -DskipTests package dependency:build-classpath -Dmdep.outputFile=target/live-failover/classpath.txt
VALIDATION_CP="target/test-classes:target/classes:$(cat target/live-failover/classpath.txt)"
java -cp "$VALIDATION_CP" com.riansares.r4r.validation.LiveFailoverExecution inventory
java -cp "$VALIDATION_CP" com.riansares.r4r.validation.LiveFailoverExecution python3 py-scripts/r4r_live_failover_validation.py serve
```

En otra terminal, ejecutar `batch` para la batería reducida o importar `batch(..., expected_vector=..., expected_model=...)` para verificar explícitamente la ruta. `control --switches '{"pg":false}'` interrumpe únicamente el proxy propio; `'{"pg":true}'` lo restaura. `Ctrl+C` en el controlador detiene su Java y todos sus proxies. No ejecutar dos controladores a la vez. Los puertos locales reservados son 18781 y 18782; los puertos de los proxies se asignan dinámicamente. El código de prueba mapea URLs reales solo a estos proxies, no cambia la configuración del NAS.

**Estas pruebas pueden consumir OpenAI y consultar el corpus real**: son optativas y no equivalen a un test aislado. No ejecutar `inventory` para imprimir valores secretos: su salida está limitada a parámetros públicos y presencia booleana de credenciales.

No se ha realizado commit, push ni despliegue.

## Archivos entregados

Modificados: `docker-spring/.env.base`, `docker-spring/compose-services.yml`, `src/main/resources/application.yml`, `CitedRagService.java`, `LocalRetrievalQueryTranslator.java`, `OllamaServerSeparationTest.java`, `PublicRagEnvironmentTest.java`, `CitedRagMultilingualRetrievalTest.java`, `CitedRagServiceTest.java`, `LocalRetrievalQueryTranslatorTest.java`.

Nuevos: este informe, `py-scripts/r4r_live_failover_validation.py`, `src/test/java/com/riansares/r4r/validation/LiveFailoverExecution.java` y `LiveEvidenceSnapshot.java`. Sin cambios en frontend, routers de failover, adaptadores S3, corpus, modelos o configuración privada.

Consumo observado en los procesos propios: **40 llamadas OpenAI con uso registrado**, 34109 tokens de entrada y 3038 de salida; incluye las traducciones de respaldo cuando faltaban ambos proveedores locales. No se deriva un importe sin una tarifa verificada. No hubo Luna en las rutas normales finales.

Cierre de ejecuciones reales y limpieza: antes de las 17:43 UTC; documentación y revisión final dentro del límite 17:45:25 UTC. No se han iniciado nuevas pruebas tras reanudar el cierre.
