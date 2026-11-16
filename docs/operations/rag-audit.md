# Auditoría posterior del RAG

Se habilita con una lista no vacía `RAG_AUDIT_PROVIDERS`. No modifica el texto, las citas ni el estado original
guardado del RAG; sólo propone una revisión visual y una nota. Nunca eleva estados.

## Flujo

1. `CitedRagService` congela los chunks definitivos antes de construir el prompt y
   captura sus etiquetas, contenido íntegro y metadatos con el resultado validado.
2. Los controladores REST/SSE registran ese snapshot temporalmente. Si no hay chunks,
   el auditor está desactivado o no hay capacidad, no ofrecen auditoría.
3. `ANSWER_READY` conserva su contrato y sigue terminando el SSE. Sólo puede añadir
   `auditId`. Tras renderizar la respuesta, el JS público
   envía `POST /api/rag/audits/{auditId}`, sin pregunta ni evidencias del navegador.
4. Un worker separado recorre los proveedores configurados ante fallos técnicos;
   el primer resultado recibido termina la inferencia. Jackson valida
   el JSON, estado original, recomendación, veredicto, motivo y límites de texto.
   Sólo una degradación coherente con `FAIL` y explicación puede reducir el estado
   visual. Dictámenes inválidos/no concluyentes no producen cambios.

Se eligió otro POST porque el SSE actual cierra y libera recursos en `ANSWER_READY`.
Así la auditoría no prolonga sus timeouts ni su admisión. El frontend cancela su espera
y descarta resultados antiguos cuando cambia la consulta. Preview, grabaciones y
fallbacks guardados no llaman al auditor. Los clientes REST conservan compatibilidad;
pueden ignorar el `auditId` opcional.

## Indicadores mientras se revisan referencias (2026-11-16)

La respuesta y sus fuentes se pueden leer inmediatamente. El listado de pasos
inicial se recoge al recibirla, pero «Revisando referencias… / Checking sources…»
mantiene un círculo giratorio visible y el botón de consulta conserva su indicador
de carga hasta que termine la auditoría. Se marca `aria-busy` y se evita enviar
otra consulta desde ese botón mientras la revisión está pendiente. Los indicadores
terminan tanto con revisión válida y posible degradación como con resultado
inconcluso, indisponibilidad o error. Limpiar la consulta descarta la revisión
pendiente para que su resultado posterior no modifique otra respuesta.
Se respeta la preferencia del navegador de reducir movimiento.

El frontend distingue el equipo y modelo de generación de los del embedding mediante
el campo opcional [embeddingProvider](rag-sse.md). No se atribuye un proveedor
preferido cuando no se ha observado cuál respondió.

Validación de este ajuste: `mvn verify` mediante el gate de aislamiento de secretos,
593 unitarias y 86 integraciones offline correctas, 27 entradas externas omitidas
explícitamente; 31 pruebas Angular correctas y build de producción correcto.
Se comprobaron failover, aislamiento por consulta, serialización REST/SSE y estados
de revisión en español e inglés. Exportación estática local actualizada; sin
despliegue. La revisión visual final queda a cargo del usuario; no se continuaron
las comprobaciones de navegador tras su instrucción de no renderizar.

## Configuración

Usar listas explícitas de tres campos; la lista vacía desactiva auditoría:

```env
RAG_AUDIT_PROVIDERS=http://192.168.1.207:11434|qwen3:8b|20s,openai|gpt-6-luna|35s
RAG_AUDIT_TOTAL_TIMEOUT=60s
RAG_AUDIT_CONNECT_TIMEOUT=400ms
RAG_AUDIT_PROBE_TIMEOUT=600ms
```

El sondeo `/api/tags` distingue modelo instalado de la residencia y no carga nada.
Un PC inaccesible permite avanzar sin esperar su timeout de inferencia. Cada
proveedor tiene un único intento, sin reintentos internos de Spring AI. El límite
global incluye sondeos e inferencias y nunca supera 90s (límite HTTP del cliente).
Se conservan 20s locales y 35s remotos; no se deducen tiempos saludables nuevos
a partir de una prueba de equipos apagados.

Un JSON inválido, `PASS`, `FAIL` o `INCONCLUSIVE` termina el recorrido: no genera
una llamada adicional. Los resultados técnicos distinguen `invalid_json`,
`inconclusive`, `timeout`, `cancelled`, `saturated`, `quota_exhausted`,
`authentication`, `invalid_request`, `provider_unavailable`, `provider_failed` y
`busy`; el resultado original permanece cuando no hay revisión concluyente.
Los metadatos identifican el proveedor/modelo realmente intentado o seleccionado.

No hay precarga del auditor. Un modelo diferente mantiene `keep_alive=0s`; el mismo
modelo generador en el mismo host conserva 8h para no descargarlo. No se cambian
los controles de VRAM de la precarga. Compartir host con modelos distintos puede
causar contención: configurar una lista adecuada sigue siendo responsabilidad del
operador.

OpenAI es explícito y puede ocupar cualquier posición. Comparte un único cliente,
contador diario y semáforo con generación, incluso con otro modelo o timeout.
No requiere OpenAI en la lista de generación. La clave privada es obligatoria si
cualquiera de las dos listas lo incluye. Ver [migración y pruebas](ordered-provider-failover.md).

## Límites y privacidad

Ocho snapshots pendientes como máximo, TTL de dos minutos y contexto limitado a
100.000 caracteres antes de serializar. Si excede el límite, se omite la auditoría:
no se recortan las fuentes. Tokens opacos de un solo uso y un worker, con una única reserva de admisión.
Una posición de relevo interno evita falsos `busy` al completar; no admite una
segunda auditoría mientras exista una inferencia activa. Hay
timeout/cancelación y limpieza al cerrar. Un proveedor que tarde en liberar su llamada
mantiene ese único worker ocupado; no se crean más hilos para compensarlo.

El POST exige la misma política de Origin que las respuestas. No consume otra cuota
de preguntas: requiere un token emitido por una pregunta admitida, y aplica sus propios
límites de capacidad. El token es una credencial temporal: no compartirlo ni registrarlo.
No se publican chunks, direcciones internas, thinking ni errores del proveedor. Sólo
se registran duración, proveedor/modelo, estado efectivo y resultado técnico.
La exportación de evaluaciones a disco permanece desactivada salvo activación explícita.

La recuperación ES/EN es ahora directa: se retiraron el traductor previo, sus modos
`auto/always/never`, su bean y `RAG_TRANSLATION_READ_TIMEOUT` de Java, Compose y defaults.
El antiguo valor por defecto era `never`; ya no hay una ruta de traducción que otra
variable de despliegue pueda activar. `retrievalQuestion` se conserva como consulta
explícita compatible; el idioma de generación sigue independiente de la recuperación.

## Verificar sin modelos reales

```bash
python3 py-scripts/test_secret_isolation.py
CHROME_BIN="$(scripts/resolve-chrome-bin.sh)" npm --prefix frontend run test:ci
```

Los tests cubren entrega previa de la respuesta, políticas de estados, contexto exacto
incluido el no citado, cuota OpenAI compartida, expiración/capacidad, saturación,
cancelación/timeout, Origin, JSON inválido y consultas consecutivas. No requieren LLM.
