# Integración Athagon Labs / Riansares 4R — informe de ejecución

Este informe registra la primera integración. El esquema, privacidad y conservación han sido
sustituidos por `rag-history-privacy-report.md`; no debe utilizarse para configurar el nuevo registro.

Fecha: 2026-11-15. Estado: cambios implementados y validados localmente; sin publicación.
La investigación del publicador externo permanece bloqueada: su configuración no está
versionada en los repositorios inspeccionados. No se declara cumplido el cierre en producción.

## A. Athagon Labs

Repositorio: `/home/german/Desarrollo/athagon-web.git`, rama `main`, base `616ec76`.
Estaba limpio antes de intervenir.

Archivos modificados: `html-static/index.html`, `html-static/styles.css`.

- R4R ocupa dos columnas; CoR/EESC la tercera. Segunda fila: Toys & Colors,
  Motivation Game M1 y Virtual Mirror. Se conserva el orden posterior.
- Motivation pasa de dos columnas a una; se reutilizan el Grid, los breakpoints
  y los filtros existentes, sin reconstruir el catálogo.
- Título y subtítulo en inglés; marcas diferenciadas entre empresa y desarrollador.
- Descripción de aplicación real, documentación verificable, Angular/SSE,
  Java/Spring AI, pgvector, modelos locales y auditoría. Sin atribuir Bedrock operativo.
- Enlaces finales: [Try AI Assistant](https://athagon.tech/r4r-rag/) y
  [Riansares 4R](https://riansares4r.com/). La tarjeta anterior no tenía GitHub.

Validación Chromium: 1440, 900 y 390 px; primera/segunda fila comprobadas mediante
geometría; filtros comprobados; sin desbordamiento horizontal. Capturas locales:
`/tmp/athagon-1440.png`, `/tmp/athagon-900.png`, `/tmp/athagon-390.png`.
El test utilizado queda en `/tmp/verify-athagon.cjs`.

Publicación declarada en `wrangler.jsonc`: Worker `athagon-web`, assets `html-static`.
No hay `.gitlab-ci.yml` en este repositorio; la vinculación externa del push no está probada.

## B. Investigación de R4R.git

Repositorio: `/home/german/Desarrollo/R4R/R4R.git`, rama `master`, HEAD y
`origin/master` local: `9b6d14be977c3d690bf642f01ea80652a32f58d4`.
Remoto: `https://gitlab.com/germancaballero/r4r`.
Cambio preexistente preservado: `.idea/vcs.xml`.
Se inspeccionaron estado, remotos, ramas, 30 commits de todas las referencias locales,
histórico de `.gitignore`/`dist`, archivos versionados, reglas de exclusión,
Webpack, generador y GitLab CI.

### Hechos comprobados

1. `dist/index.html`, CSS, JS, imágenes y la demo están versionados. `dist/` no está
   ignorado. `git check-ignore -v dist/ dist/r4r-rag-demo/` no devuelve reglas.
2. La línea 5 de `dist/.gitignore` (`dist`) ignora **`dist/dist/`**, no su directorio
   padre. Git además conserva archivos ya versionados aunque se añada una exclusión.
3. El commit `114fa5d` trasladó el sitio a `dist/`. El commit `9b6d14b` añadió
   exactamente los tres archivos `dist/r4r-rag-demo/{index.html,app.js,styles.css}`
   (1504 líneas). La demo usa un endpoint HTTP de red privada; no es el frontend
   Angular actual ni una integración comercial funcional segura en HTTPS.
4. `npm run build` usa Webpack con contexto `dist/` y salida **`dist/dist/`**.
   Su configuración no copia todo el sitio ni la demo; no debe confundirse con
   un procedimiento completo para publicar la web existente.
5. `generate-site.sh` genera `dist_gen/*.html` desde `src/templates`, `src/data`
   y el paquete Python `generator`. No genera `dist/r4r-rag-demo/` ni reemplaza
   automáticamente el sitio servido. `GENERATOR_STATUS.md` confirma que era
   un laboratorio separado que no modificaba `dist/`.
6. `.gitlab-ci.yml` declara un job Pages en la rama por defecto, con imagen BusyBox,
   un `echo` y artefacto `public/`. No crea ese directorio, no compila y no copia
   `dist/`; `public/` tampoco está versionado. Ese YAML no explica por sí solo
   la publicación observada del dominio comercial.
7. Existen referencias `static-generator-lab`, `test-aider-ollama` y la rama de
   taxonomía. La rama activa de la web es `master`. No se hizo fetch ni push.

### Explicación respaldada y límite

El último push **sí contenía la demo como fuente versionada**. Un publicador que
sirva `dist/` la incluiría automáticamente, sin generar nada: eso explica el
comportamiento observado. La relación causal con un job concreto no se puede
certificar sin su configuración e historial externos. No se afirma que GitLab Pages
ni Cloudflare sean el pipeline real del dominio por suposición.

La portada pública fue legible con la herramienta web y coincide en estructura
comercial con la fuente. Las comprobaciones HTTP directas a las dos webs recibieron
403; no se pudo comprobar por hash el artefacto desplegado ni sus cabeceras CSP/CORS.

Cadena local comprobada:

`dist/ versionado → sitio estático (sin build obligatorio) → artefacto del publicador externo → publicación no identificada → https://riansares4r.com/`

Cadena nueva reproducible, sin sustituir la configuración existente:

`dist/ + frontend Angular → ng build + prepare-commercial-release.py → directorio estático completo → publicador pendiente de identificar → URL comercial`

No se modificaron `.gitignore`, Webpack, el generador ni `.gitlab-ci.yml` para
acomodar una hipótesis. Pendiente externo: verificar proyecto, directorio servido,
comando de build, rama, logs del despliegue `9b6d14b` y dominio asociado.

## C. Integración comercial

En `dist/index.html`, después del hero y la franja de confianza, antes de trabajos
reales. Se mantienen títulos SEO, textos, galerías, navegación, contacto y enlaces.
Nuevos archivos fuente: `dist/js/assistant-embed.js` y `dist/_headers`.

Se elige **vista embebida del Angular existente** (`asistente/?embed=1`) en un iframe
del mismo dominio. La alternativa nativa duplicaría el parser SSE, fallback,
auditoría, clasificación y catálogo de fuentes. La vista reutiliza esos componentes
y servicios; omite la cabecera, hero, información técnica y pie de Athagon, los
ajustes del endpoint, ejemplos y reproducción automática. No se monta una segunda
página completa con sus menús.

`ResizeObserver` ajusta la altura con `postMessage`; el padre comprueba origen,
ventana emisora, tipo y límites numéricos. Formularios y respuestas mantienen flujo
natural en móvil. El iframe tiene título accesible, carga diferida y enlace alternativo.
La API de producción queda fija en el modo embebido; no se reutiliza una preferencia
local de endpoint ni se guarda la pregunta en localStorage en ese modo.

Se conservan SSE, fallback REST ante endpoint no soportado, auditoría asíncrona,
fuentes PDF/HTML, ANSWERED/PARTIAL/ABSTAINED, errores, timeout de 180 s,
rate limiting y bloqueo de envíos simultáneos. Se añade aviso de orientación,
inspección profesional y precaución con datos personales.

CORS: se amplía la allowlist explícita en `docker-spring/.env.base` con
`https://riansares4r.com`, `https://www.riansares4r.com` y `https://www.athagon.tech`;
se conserva el resto. La lista anterior incluía `https://riansares.com`, que no es
el dominio solicitado. La configuración privada no se modifica. El operador debe
comprobar si el entorno de producción sustituye esta lista.

`_headers` limita `frame-ancestors` a `'self'`, objetos a `'none'` y base a `'self'`,
y añade Referrer-Policy y nosniff para el asistente. Es efectivo solo si el host
soporta ese formato; su aplicación en producción aún no está verificada.

Artefacto final preparado: `/tmp/r4r-commercial-release-final`. Contiene la web,
Angular y los documentos existentes, sin añadir bundles masivos a Git.
**No hacer push de la portada sola**: el publicador debe incluir también `asistente/`
desde el artefacto preparado. De lo contrario el iframe apuntaría a una ruta ausente.

## D. Estadísticas

Migración Flyway: `V5__assistant_usage.sql`. Tabla `assistant_usage`: UUID,
fecha UTC, origen, VIEW/QUERY, resultado y duración. Sin pregunta, respuesta,
contexto, documentos recuperados, IP, cabeceras persistidas ni identificador de visitante.
No existe endpoint de lectura de las métricas ni de preguntas.

- Origen derivado en servidor de la cabecera Origin exacta, no de un parámetro del
  cliente. Como cualquier Origin, no autentica clientes que no sean navegadores.
- El iframe se sirve desde Riansares para que las consultas lleven su origen real.
- QUERY registra el resultado inicial del backend, no una supuesta lectura por el
  visitante. El resultado posterior de la auditoría no genera otra consulta.
- El ticket de finalización es atómico: respuesta, excepción y cancelación no
  duplican eventos. Se registran errores REST/SSE, saturación SSE y rechazos de
  validación/cuota cuando pasan por el filtro de seguridad configurado.
- VIEW: una impresión visible por instancia del componente; sin registrar prerender.
  El endpoint admite únicamente orígenes conocidos y tiene límite global de 120/min.
- La portada reutiliza el GA existente (`G-41CEFW6B5P`) con `assistant_view` al ver
  la sección. GA y PostgreSQL son referencias distintas; no deben sumarse.

Persistencia secundaria: executor de un hilo, cola de 128, descarte al saturarse,
SQL con timeout de 2 s, errores absorbidos sin volcar contenido sensible. Una caída
SQL puede perder eventos y ocupar el escritor, pero no bloquea el hilo del RAG.
No se ha añadido mensajería.

Activación explícita: `RAG_USAGE_ENABLED=true`; por defecto está desactivada.
El almacenamiento del texto de preguntas **no existe ni se activa**: requiere
revisión de privacidad previa y un trabajo expresamente autorizado. No hay FAQ de
preguntas reales disponible mientras no se guarden textos.

Actualización posterior: la política y el procedimiento de conservación de este informe
han sido sustituidos por `rag-history-privacy-report.md`. No hay cron ni purga automática;
la revisión y supresión son manuales. El script de borrado a 30 días fue retirado.

VIEW cuenta impresiones, no personas; QUERY/VIEW mide interacción, no conversión
única. Recargas, varias preguntas, bloqueadores y descartes de cola limitan precisión.

Pruebas: cuatro nuevas pruebas sobre orígenes exactos, finalización única,
fallo SQL, desactivación y cuota de visitas; 39 pruebas existentes backend también
verdes. Migración, inserciones, agregados, percentiles y eliminación ejecutados en
PostgreSQL Docker mediante esquema temporal y rollback, sin cambiar el esquema real.

## E. Estado de publicación y validación

- 43 pruebas backend seleccionadas: verdes (API REST, SSE, seguridad y estadísticas).
- 16 pruebas Angular: verdes.
- Build Angular producción: verde; aproximadamente 371 kB iniciales sin comprimir.
- Chromium: 1440/900/390 px; 5 escenarios por tamaño: ANSWERED, PARTIAL,
  ABSTAINED, 429 y fallback REST. Sin inferencia automática, errores de consola,
  overflow horizontal ni scroll interno; un intento SSE por envío.
- Fuentes: 82 PDFs del catálogo presentes; enlaces de citas también servidos por HTTP local.
- `git diff --check` y `git diff --cached --check`: verdes. Cambios preexistentes preservados.
- No se ejecutó ninguna inferencia real ni se modificaron modelos, prompts,
  corpus, embeddings, índices, Lambda, S3 Vectors o failover.

Evidencias: `assistant-integration-validation.json`, `/tmp/r4r-backend-tests.log`,
`/tmp/r4r-frontend-tests.log`, `/tmp/r4r-angular-build.log`,
`/tmp/r4r-browser-tests.log`, capturas `/tmp/commercial-{1440,900,390}.png`.
Las capturas y logs de /tmp son locales y temporales; no se han añadido a Git.

Reproducción desde el repositorio RAG:

```sh
mvn -Dtest=AssistantUsageTest,RagQueryControllerTest,RagStreamControllerTest,RagApiSecurityFilterTest test
CHROME_BIN=$(bash scripts/resolve-chrome-bin.sh) npm --prefix frontend run test:ci
npm --prefix frontend run build -- --base-href ./
# El destino debe ser NUEVO: el preparador nunca borra una publicación existente.
python3 py-scripts/prepare-commercial-release.py \
  --commercial /home/german/Desarrollo/R4R/R4R.git --output /tmp/r4r-release-review
CHROMIUM_PATH=$(bash scripts/resolve-chrome-bin.sh) \
  node frontend/validate-commercial.cjs /tmp/r4r-release-review
```

No se crearon commits, ni se hizo add, push o despliegue. AGENTS.md reserva Git al
controlador determinista. Los grupos de cambios quedan separados por repositorio:
portfolio, fuente comercial y soporte RAG/estadísticas. No se ha alterado el plan
OpenCode ni se ha simulado una aceptación del controlador.

Para publicar Athagon por el mecanismo declarado, una vez revisado su destino:

```sh
cd /home/german/Desarrollo/athagon-web.git
npx wrangler deploy
```

Ese comando es de producción y **no se ha ejecutado**. Para actualizar además
las visitas PostgreSQL del asistente completo de Athagon, generar su frontend con
`scripts/build-frontend-angular.sh`, que también copia el resultado a
`athagon-web.git/html-static/r4r-rag/` y sincroniza el gestor de consentimiento; la tarjeta no necesita ese paso para su diseño.

Para Riansares no se puede dar honestamente un comando de publicación verificado:
el destino real y la integración externa de Git no se conocen. Primero revisar
la configuración externa y promover el artefacto completo mediante ese publicador,
sin modificar el pipeline por conjetura. Tampoco debe hacerse un push ciego a
`master`: el historial observado indica que puede afectar a producción.

Pendientes reales: identificar el publicador; comprobar CSP/CORS HTTPS efectivos
en ambos dominios y variantes; desplegar backend con V5, CORS y estadísticas
activadas conforme al nuevo procedimiento de revisión manual; publicar artefactos completos; verificar
una consulta controlada en producción. Todo lo anterior permanece local y sin publicar.
