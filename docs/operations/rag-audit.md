# Auditoría posterior del RAG

Desactivada por defecto. No modifica el texto, las citas ni el estado original
guardado del RAG; sólo propone una revisión visual y una nota. Nunca eleva estados.

## Flujo

1. `CitedRagService` congela los chunks definitivos antes de construir el prompt y
   captura sus etiquetas, contenido íntegro y metadatos con el resultado validado.
2. Los controladores REST/SSE registran ese snapshot temporalmente. Si no hay chunks,
   el auditor está desactivado o no hay capacidad, no ofrecen auditoría.
3. `ANSWER_READY` conserva su contrato y sigue terminando el SSE. Sólo puede añadir
   `auditId`. Tras renderizar la respuesta y habilitar el formulario, el JS público
   envía `POST /api/rag/audits/{auditId}`, sin pregunta ni evidencias del navegador.
4. Un worker separado hace una única llamada al modelo configurado. Jackson valida
   el JSON, estado original, recomendación, veredicto, motivo y límites de texto.
   Sólo una degradación coherente con `FAIL` y explicación puede reducir el estado
   visual. Dictámenes inválidos/no concluyentes no producen cambios.

Se eligió otro POST porque el SSE actual cierra y libera recursos en `ANSWER_READY`.
Así la auditoría no prolonga sus timeouts ni su admisión. El frontend cancela su espera
y descarta resultados antiguos cuando cambia la consulta. Preview, grabaciones y
fallbacks guardados no llaman al auditor. Los clientes REST conservan compatibilidad;
pueden ignorar el `auditId` opcional.

## Configuración

Usar la configuración local existente; no guardar claves en archivos versionados.

```env
RAG_AUDIT_ENABLED=true
RAG_AUDIT_PROVIDER=http://192.168.1.207:11434|qwen3:8b
```

Ollama admite `URL|modelo` o `URL|modelo|timeout`, reutilizando los clientes Spring AI,
thinking, temperatura y timeouts de generación. Sin timeout explícito, hereda el
del host local configurado o del primer proveedor. Máximo de auditoría: 90 segundos.
Para un modelo mayor en el portátil, por ejemplo:

```env
RAG_AUDIT_PROVIDER=http://192.168.1.209:11434|modelo-instalado|60s
```

No hay descubrimiento, precarga ni fallback del auditor. Un modelo diferente se
descarga después de su llamada (`keep_alive=0`); si coincide con un modelo generador
del mismo host, conserva la política existente de residencia para no descargarlo.
Compartir host puede generar contención de CPU/VRAM: para modelos grandes conviene
otro servidor. No existe una cola de auditorías que bloquee nuevas respuestas.

Para Luna, habilitar explícitamente el proveedor OpenAI existente y su clave:

```env
RAG_OPENAI_ENABLED=true
RAG_AUDIT_ENABLED=true
RAG_AUDIT_PROVIDER=openai|gpt-6-luna
```

El modo `true` mantiene Ollama como primer generador. `primary` sigue disponible
si se desea Luna como primer generador. El auditor elige su modelo independientemente,
pero comparte **el mismo cliente, cuota diaria y semáforo OpenAI**; no crea otro
presupuesto. Hereda `RAG_OPENAI_READ_TIMEOUT` (debe ser <=90s al activar este auditor).
No hay fallback silencioso a Luna. Reiniciar la API tras cambiar configuración.

## Límites y privacidad

Ocho snapshots pendientes como máximo, TTL de dos minutos y contexto limitado a
100.000 caracteres antes de serializar. Si excede el límite, se omite la auditoría:
no se recortan las fuentes. Tokens opacos de un solo uso, un worker, ninguna cola,
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
node front-athagon-r4r-static/js/app.test.cjs
mvn clean verify
```

Los tests cubren entrega previa de la respuesta, políticas de estados, contexto exacto
incluido el no citado, cuota OpenAI compartida, expiración/capacidad, saturación,
cancelación/timeout, Origin, JSON inválido y consultas consecutivas. No requieren LLM.
