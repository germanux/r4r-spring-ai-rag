# R4R/RAG — cierre mínimo de código y seguridad (tareas para Codex)

**Revisión consolidada:** 09/11/2026, sobre el remoto `main` en `b7b50093cc6dcf0840284c998ab90b115243e591`.  
**Ruta real:** `docs/security-bugs-update-tasks-codex.md`.  
**Propósito:** ejecutar únicamente correcciones pequeñas de código, dependencias y tests. **El despliegue, Synology, Cloudflare, DNS, túneles, imágenes en producción y pruebas públicas son responsabilidad exclusiva del propietario; NO son tareas de Codex.** Este archivo sustituye, para la siguiente ejecución, la auditoría extensa anterior (conservada en el historial Git).

## 1. Decisión: cerrar, no ampliar el proyecto

No reimplementar RAG, failovers, Cloudflare, Docker, Spring AI ni frontend. No crear nuevos subsistemas ni más documentos de trabajo. El sistema **ya ha superado pruebas reales importantes**; lo único encargado a Codex es un endurecimiento breve del repositorio y su validación aislada. El propietario hará la publicación por separado.

### Evidencia ya obtenida — NO repetir la matriz

- Cambio funcional incorporado en `35eda2c292f48ea7c8e8a6729848ef3233f81b1f`, documentación revisada en `b7b50093cc6dcf0840284c998ab90b115243e591`.
- **48/48 PASS** rutas live de failover; tras la normalización inglesa, **12/12 PASS + 2/2 PASS** focalizadas. No afirmar que se repitieron las 48 tras ese último ajuste.
- Maven final **275 unitarias y 28 integraciones PASSED**; **6 pruebas live opcionales SKIPPED**. Node estático, comprobación Python Compose y `docker compose config --quiet` correctos.
- Se observaron **pgvector → S3 Vectors → pgvector**, embeddings LP→PC→NAS, generación PC→portátil→Luna y recuperación. Citas/abstención y 282 vectores conservaron integridad comprobada. S3 es read-only y snapshot manual, no réplica continua.
- Se corrigió aislamiento del fixture `PublicRagEnvironmentTest` para no heredar `systemEnvironment`, y se redactaron tres informes **locales** que expusieron un valor durante un fallo. No hay evidencia de exposición externa en los artefactos revisados; eso no demuestra su imposibilidad.
- Se mejoraron idioma EN, abstención, normalización «trabajos de renovación»→«reformas» para recuperación, temperatura pública 0 y timeout del portátil 60 s.
- **MyMemory como traductor principal y recordar la última pregunta son requisitos expresos del propietario**. El fallback local se usa si MyMemory falla; la interfaz ya incluye una nota de privacidad ES/EN. **No eliminar ni alterar estos requisitos.**

Fuente detallada y evidencia histórica: `docs/rag-live-failover-validation.md` y `docs/validation/r4r-mymemory-restoration.md`.

### Contexto histórico de publicación, NO tareas de Codex (08/11)

1. `r4r-api.athagon.tech` devolvía **NXDOMAIN**; todavía no consta revisión DNS posterior. **Bloquea la URL pública**, no necesariamente el acceso LAN.
2. El NAS tenía una imagen anterior: `POST /api/rag/answers/stream` respondía **404** (REST funcionaba). El binario Java local sí implementa SSE. **Bloquea la demo SSE** hasta construir, cargar y ejecutar el JAR/imagen adecuados.
3. Una respuesta inglesa EN09 del **NAS antiguo** incluía hechos no respaldados por cita. No debe confundirse con los resultados corregidos del binario local.
4. No hay validación del **navegador a través del túnel público** con esa nueva imagen.

**Estos cuatro asuntos los comprobará y resolverá el propietario.** Codex **no debe hacer comprobaciones DNS/WAN/NAS/Cloudflare, conectar a servicios públicos, preparar su despliegue, escribir instrucciones para acceder a ellos ni solicitar sus credenciales o permisos**. Tampoco debe declarar «publicado y probado» a partir de `mvn verify`.

## 2. Cambios de código permitidos y realmente útiles

### S1 — Protección de entradas HTTP (hacer)

**Evidencia vigente:** `RagQueryController` y `RagStreamController` comprueban pregunta vacía e idioma, pero no tamaño; la UI estática restringe 700 caracteres.

- Compartir una comprobación simple (sin framework de validación nuevo): pregunta obligatoria **1–700 unidades Java `String.length()`**, `retrievalQuestion` opcional **hasta 2.000**, idioma `es`/`en`/ausente. No truncar silenciosamente. Si ya existe otra política efectiva, mantenerla y justificarla.
- Devolver **HTTP 400** sin eco de la pregunta, antes de consultas a LLM/embedding y antes de reservar una sesión SSE. Conservar contratos y estado/errores válidos de REST/SSE.
- Tests enfocados para **ambos** endpoints: vacío, 700/701, retrieval 2.000/2.001, idioma incorrecto, emoji/par sustituto. Verificar que no se invoca proveedor con entrada inválida.
- Un límite HTTP de bytes anterior a deserialización sería un endurecimiento adicional, pero **no implantarlo si exige filtros complejos o cambio de proxy**. No confiar en propiedades de formularios/multipart para acotar JSON.

### S2 — Evitar nuevas fugas de secretos en tests (hacer una regresión pequeña)

- **Conservar** el aislamiento que el otro Codex ya aplicó a `PublicRagEnvironmentTest`; no crear otro sistema de configuración.
- Con **marcador sintético** `POSTGRES_PASSWORD=R4R_TEST_SECRET_SENTINEL_123` en el proceso padre, probar que el fixture se comporta igual y que ni salida de Maven ni sus reportes incluyen el valor. Automatizarlo de forma sencilla si es factible; **no usar credenciales reales**.
- Revisar superficialmente otros tests de precedencia que mezclen entorno real con aserciones que impriman valores. Si no se reproduce exposición adicional, cerrar.
- **No modificar ni eliminar** `LiveFailoverExecution` o `LiveEvidenceSnapshot` por utilizar claves reales durante una ejecución live explícita: esa es su función. Las llamadas live siguen siendo opcionales y nunca se disparan en `mvn verify`.

### S3 — Parche de dependencias coherente (hacer una sola pasada, con límite)

El POM auditado continúa con **Spring Boot 3.4.5 / Spring AI 1.0.0 / Java 21**. Están desfasados para una API pública. El objetivo es **un parche transitorio que compile y pase tests**, no una migración grande.

1. Verificar POM/BOM reales y avisos oficiales. Probar **Spring AI 1.0.9 + Spring Boot 3.5.16**; si el cambio de Boot 3.4 a 3.5 exige refactor considerable, preferir **Boot 3.4.13 + Spring AI 1.0.9** como parche menor, documentando el compromiso. Mantener Java 21, SDK AWS y resto salvo problema demostrado.
2. Aplicar **solo versiones estables exactas**, una pareja compatible cada vez; ejecutar tests de PGVector, Ollama, S3, REST/SSE y configuración. No editar decenas de dependencias transitivas individualmente.
3. **Importante:** Boot 3.4.x y 3.5.x ya terminaron soporte OSS; 1.0.9 aún figura afectada en advisories de lector PDF y caché ONNX (CVE-2026-47851/47852). Comprobar si esos módulos se usan o son accesibles en R4R, **sin afirmar que una CVE listada sea una ruta explotable**. Los CVE de filtros también requieren comprobar si se usan expresiones controladas por usuario.
4. **No migrar hoy** a Boot 4 / Spring AI 2.0.2 (cambio mayor de APIs) ni Angular 17 a una versión mayor. Si la actualización mínima rompe varias partes o descubre un componente vulnerable realmente expuesto, **parar y reportar el bloqueo**, no improvisar una migración completa.

Fuentes oficiales verificadas:  
- Spring AI 1.0.9: https://spring.io/blog/2026/06/12/spring-ai-1-1-8-1-0-9-avaialble-now/  
- Boot 3.5.16 / fin de OSS: https://spring.io/blog/2026/06/25/spring-boot-3-5-16-available-now/  
- Advisory de filtros: https://spring.io/security/cve-2026-40967/  
- Advisory PDF: https://spring.io/security/cve-2026-47851/  
- Advisory ONNX: https://spring.io/security/cve-2026-47852/  
- AI 2.0.2 estable, futura migración: https://spring.io/blog/2026/10/23/spring-ai-2-1-0-RC1-available-now/

### S4 — Runtime: una corrección pequeña y aislada

- `RagRuntimeController.databaseName` deriva el nombre con `lastIndexOf('/')`; si una JDBC URL ficticia contuviera query o fragmento con `password`, podría reflejarlo. **Si sigue así, excluir parámetros y fragmentos**, con un test sintético. No ocultar modelos/versiones útiles para demostrar el portfolio ni cambiar el JSON que consume la UI.
- Mantener intacta la configuración de red, Origin, rate limits y `RAG_API_TRUST_PROXY_HEADERS`; **Origin no autentica**, pero revisar topología/firewall exige acceso de infraestructura que corresponde al propietario. No acceder ni probar LAN/WAN/NAS.

## 3. Fuera de este encargo: publicación e infraestructura

**Todo lo siguiente lo hará exclusivamente el propietario, fuera de la sesión de Codex**: reconstruir y cargar la imagen en Synology, reiniciar contenedores, ajustar Cloudflare Tunnel/DNS, comprobar accesos LAN/WAN, comprobar CORS y HTTPS públicos y probar REST/SSE en el navegador. Codex no debe abordar ni preparar estas tareas.

**No solicitar autorización para ello, ni pedir accesos.** Al terminar el trabajo de código, limitarse a decir que el despliegue real *no se ha verificado en este encargo*. El propietario decidirá cómo, cuándo y dónde publicarlo.

## 4. Qué se descarta o aplaza expresamente

**NO HACER en este cierre**, salvo encontrar una vulnerabilidad directa grave y demostrada:

- Quitar MyMemory, añadir otro traductor, retirar `lastQuestion` o cambiar la política de almacenamiento solicitada. La nota ES/EN de MyMemory ya existe; no duplicarla.
- Nuevo framework de seguridad OAuth/JWT, Spring Security entero, gateway, Redis, colas, Kubernetes, agentes, endpoints nuevos o reescritura de arquitectura.
- Boot 4 / Spring AI 2, migración mayor de Angular, upgrades masivos del AWS SDK o de imágenes `latest`, pinning de todos los digests, nuevo monitor/observabilidad.
- Sincronización automática de S3 Vectors o pruebas de igualdad exhaustiva de 282 embeddings. El snapshot es manual y de solo lectura; documentar desfase posible.
- Repetir matriz live de tres horas, forzar arranque frío del portátil, probar 15+15 preguntas, reindexar corpus o cambiar umbrales para maquillar calidad.
- Prueba mock de prompt injection adicional solo por ampliar cobertura. Ya existen citas/abstención; el mock no acredita seguridad del modelo. Si se encuentra un defecto real en el manejo de fuentes, entonces sí escribir una regresión focalizada.
- Reescritura de Git, eliminar dumps/JAR históricos, reestructurar docs o limpiar miles de archivos. **Sí** revisar rápidamente si los dumps/exportaciones versionados contienen datos privados antes de promocionar el repositorio: si contienen secretos/PII reales, informar y bloquear publicación; no exponerlos en informes.
- El modelo no debe saltar a Luna por redacción imperfecta o por una respuesta incompleta: conservar failover **técnico**. Calidad terminológica residual de Qwen no exige nueva orquestación.

## 5. Gates, tiempo y entrega de Codex

**Primero** leer `AGENTS.md` y proteger el worktree: `git status --short`, `git log -5 --oneline`, `git diff --check`. Detectar cualquier cambio posterior a la referencia, no sobrescribirlo.

Orden estricto:
1. Revisar si S1/S2/S4 ya están implementadas. Aplicar solo lo pendiente.
2. Probar la pareja compatible de dependencias S3 con Maven; tiempo razonable, sin migración grande.
3. `mvn verify` usando **solo DB test protegida**; reportar unitarios/IT y omitidos. Comprobar credenciales sintéticas, tests REST/SSE y traducción.
4. Node `node --test front-athagon-r4r-static/js/app.test.cjs` y Python Compose según scripts reales; `docker compose config --quiet` sin imprimir secretos. Solo si son relevantes. No usar pruebas saltadas como evidencia.
5. Entregar tabla muy corta `DONE / NO APLICA / BLOQUEADO / DIFERIDO`, versiones finales, número real de pruebas, archivos cambiados y **GO/NO-GO del código**. No atribuir GO a infraestructura no verificada.

**Seguridad y límites absolutos:** No leer/imprimir `.env` ni perfiles AWS, no poner claves en Git, no ejecutar SQL de escritura en producción ni escrituras S3, no tocar servicios ajenos; los tests con credenciales sintéticas deben estar aislados. **No entrar, consultar ni modificar Synology, Cloudflare, DNS, túneles, router o servicios reales. No pedir sus credenciales, permisos ni autorizaciones.** No hacer commit, push ni despliegue. Si algo exige infraestructura, indicarlo como «fuera de alcance» y seguir con las tareas locales independientes.

**Tiempo:** procurar cerrar sin otra sesión extensa; si la actualización de dependencias resulta compleja o un servicio no está disponible, señalarlo claramente en lugar de ampliar alcance.

## 6. Prompt corto para ejecutar

> Lee íntegramente `docs/security-bugs-update-tasks-codex.md` y aplica **solo** sus tareas S1/S2/S3 y el ajuste local S4 que sigan pendientes en el código. Respeta los requisitos existentes (MyMemory principal, última pregunta persistente, PG→S3 read-only, PC→portátil→Luna) y no repitas la matriz de 48/48 failovers reales ya validados. Ejecuta únicamente tests locales seguros y entrega el resultado del código. **Synology, Cloudflare, DNS, túneles, contenedores y publicación los gestionaré yo: no accedas a ellos, no prepares su despliegue ni pidas credenciales o autorización.** No abras secretos, no sobreingenierices y no hagas commit/push/deploy por iniciativa propia.
