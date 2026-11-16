# Failover ordenado de generación, embeddings y auditoría

Rama de revisión: `refactor/ordered-provider-failover`, desde `097e961` de `main`.
Sin merge, push, despliegue ni llamadas live/de pago. No se leyó ni modificó `.env` privado.

## Causa y diseño

La auditoría tenía un único `ChatModel`: agotaba el timeout del PC sin poder usar
Luna. La ejecución guardada por el usuario en
`docs/evaluation/runs/llm-audit-2026-11-16T17-57-31.787194632Z-cafe9639/run.json`
contiene diez respuestas correctas y diez errores de auditoría, con media de
20.000,16 ms del servicio auditor. Es evidencia previa, no una ejecución realizada
durante esta entrega; se conserva su directorio sin modificarlo.

Las tres funciones tenían configuración y recorridos distintos. Se adaptó el
prototipo del ZIP: `OrderedProviderFailover` comparte el recorrido, comprueba también
fallos de elegibilidad, detiene cancelaciones y conserva la causa final, incluso
al agotar presupuesto antes del siguiente proveedor. Sus cinco pruebas originales
se conservan y se añaden pruebas de presupuesto, transporte y cancelación.

`ProviderExecution` comparte clasificación técnica, deadline monotónico anidado y
transporte HTTP limitado por el presupuesto restante. Los clientes Ollama siguen
siendo Spring AI; no se introduce un cliente de inferencia manual ni un framework.

Generación y auditoría usan instancias independientes de `GenerationRouter` con
adaptadores comunes. La auditoría conserva su worker, endpoint, token opaco,
`auditId`, semántica de estados y metadatos; valida el dictamen **después** del
recorrido. Embeddings conserva compatibilidad, validación y cooldown propios:
compartir esas políticas habría acoplado funciones con requisitos diferentes.

Spring AI incluye reintentos internos por defecto en su modelo de chat. Se configura
un solo intento por adaptador: un HTTP 503 no provoca repeticiones o backoff ocultos
antes del failover. Un `PASS`, `FAIL`, `INCONCLUSIVE`, JSON inválido del dictamen o
abstención válida no provoca otra inferencia.

La admisión de auditoría mantiene una sola inferencia activa, incluso si un cliente
ignora la interrupción. Una posición interna de relevo elimina la carrera entre
completar el resultado y reutilizar el worker; no admite una segunda auditoría
activa. Se sigue devolviendo `busy` ante saturación real. El temporizador global
preserva la respuesta original y evita llamadas remotas tardías.

## Configuración pública propuesta y aplicada

Formato único: `URL-o-openai|modelo|timeout-de-lectura`. El orden es prioridad;
OpenAI puede ser primero, intermedio o último. Una lista sin OpenAI lo desactiva
para esa función. Auditoría vacía lo desactiva; generación y embeddings requieren
al menos una entrada.

```dotenv
RAG_GENERATION_PROVIDERS=http://192.168.1.207:11434|qwen3:8b|20s,http://192.168.1.209:11434|qwen2.5:7b|60s,openai|gpt-6-luna|35s
RAG_AUDIT_PROVIDERS=http://192.168.1.207:11434|qwen3:8b|20s,openai|gpt-6-luna|35s
RAG_EMBEDDING_PROVIDERS=http://192.168.1.209:11434|qwen3-embedding:0.6b|1500ms,http://192.168.1.207:11434|qwen3-embedding:0.6b|1500ms,http://192.168.1.203:11434|qwen3-embedding:0.6b|30s
RAG_GENERATION_CONNECT_TIMEOUT=2s
RAG_GENERATION_PROBE_TIMEOUT=2s
RAG_GENERATION_TOTAL_TIMEOUT=180s
RAG_AUDIT_CONNECT_TIMEOUT=400ms
RAG_AUDIT_PROBE_TIMEOUT=600ms
RAG_AUDIT_TOTAL_TIMEOUT=60s
RAG_EMBEDDING_CONNECT_TIMEOUT=400ms
RAG_EMBEDDING_PROBE_TIMEOUT=600ms
RAG_EMBEDDING_TOTAL_TIMEOUT=35s
RAG_EMBEDDING_UNAVAILABLE_COOLDOWN=30s
RAG_EMBEDDING_MODEL=qwen3-embedding:0.6b
RAG_EMBEDDING_DIMENSIONS=1024
RAG_EMBEDDING_EXPECTED_DIGEST=ac6da0dfba84a81fdbfbaf330198c33cd77c4cdfc53e8bc50eb581914a15621d
RAG_OPENAI_MAX_CALLS_PER_DAY=200
```

Se mantienen lecturas 20/60/35s de generación y 1,5/1,5/30s de embeddings. Auditoría
conserva 20s locales y 35s remotos; añade un presupuesto de recorrido de 60s,
incluidos sondeos, inferior a los 90s del cliente. La evidencia LAN existente
([disponibilidad de embeddings](embedding-availability.md)) registra `/api/tags`
con máximos 1,74 ms en PC y 264,85 ms en portátil. Justifica usar sondeo de 600ms
y conexión de 400ms también en auditoría; no justifica reducir arbitrariamente la
inferencia. Un host apagado no necesita esperar 20s para pasar a Luna.

Los plazos HTTP se recortan al presupuesto restante. La conexión tiene su límite
separado; el plazo por intento incluye margen para conexión, preservando la lectura
configurada mientras el presupuesto global permita concederla. Como en el transporte
anterior, un read timeout es un límite de inactividad de socket, no un plazo absoluto
para un flujo que entregue bytes indefinidamente. Auditoría añade un temporizador
de resultado y conserva la reserva hasta que salga realmente la llamada.

## Recuperación, privacidad y cuota

- Cada consulta comienza por el primer proveedor. Chat no mantiene caché de caídas:
  vuelve a sondear y puede recuperar el preferido inmediatamente.
- Embeddings mantiene su caché negativa de 30s; al expirar vuelve al preferido. Los
  timeouts de lectura/carga o agotamientos de presupuesto no penalizan al proveedor
  con cooldown. Modelo, digest o dimensiones incompatibles detienen la ejecución.
- Un único `OpenAiResponsesModel` comparte cuota UTC y semáforo entre generación y
  auditoría, también con modelos distintos. Cuenta intentos antes de HTTP y rechaza
  concurrencia remota sin crear otra reserva ni otro contador.
- No se precarga OpenAI. La precarga filtra remotos y protege al primer generador
  local aunque la lista empiece por OpenAI. Conserva serialización por instancia,
  residencia de 8h, observación `/api/ps` y protección de modelos residentes.
- Los logs identifican circuito, ID técnico, proveedor/host público, modelo,
  presupuesto, motivo, duración y cooldown. No incluyen preguntas, respuestas,
  vectores, credenciales ni cuerpos de error del proveedor. La causa local se
  conserva; OpenAI conserva un motivo técnico saneado para evitar filtrar contenido.

## Migración antes del siguiente arranque

1. Revisar manualmente las sobrescrituras existentes sin imprimir secretos. No
   sustituir ni rotar la clave por este cambio.
2. Convertir las listas antiguas de embeddings de dos campos a tres. Convertir el
   auditor singular en `RAG_AUDIT_PROVIDERS`. Trasladar modelo/timeout/posición de
   OpenAI a cada lista. Si antes estaba deshabilitado, retirar `openai` de las listas
   correspondientes; no copiar defaults remotos ciegamente.
3. Retirar `RAG_OPENAI_ENABLED`, `RAG_OPENAI_MODEL`, `RAG_OPENAI_READ_TIMEOUT`,
   `RAG_AUDIT_ENABLED`, `RAG_AUDIT_PROVIDER` y sus antiguos equivalentes Spring.
   El arranque rechaza esos ajustes para no ignorar una antigua desactivación.
   Compose transmite únicamente un marcador interno `RAG_LEGACY_PROVIDER_SETTINGS`
   si detecta variables antiguas en sus entradas; nunca transmite sus valores.
4. Mantener `OPENAI_API_KEY` privado: es obligatorio si cualquiera de las listas
   de chat contiene OpenAI, incluso si la otra es exclusivamente local. Sin OpenAI
   en ambas listas no se necesita clave. Mantener la cuota global compartida.
5. Revisar configuración y después construir/reiniciar la API por el procedimiento
   autorizado. Esta entrega no ejecuta ese paso. Compose sin defaults públicos
   conserva su fallback seguro local y auditoría desactivada.

## Archivos modificados

- Infraestructura nueva: `OrderedProviderFailover.java`, `ProviderExecution.java`.
- Configuración nueva: `ProviderList.java`, `AuditProviderProperties.java`.
- Integración: `GenerationRouter`, `EmbeddingProviderRouter`, `RagAuditService`,
  `RagConfiguration`, `OpenAiResponsesModel`, `OllamaModelPreloader`,
  `RagRuntimeController`, propiedades de generación/embeddings y `PublicRagEnvironment`.
- Retirado: `OpenAiMode.java`; ya no hay un segundo mecanismo de orden remoto.
- Despliegue/configuración: `.env.base`, Compose, `application.yml`; fixtures de test
  con listas locales explícitas y claves ficticias únicamente donde corresponda.
- Pruebas: nuevas `OrderedProviderFailoverTest`, `ProviderExecutionTest`,
  `RagAuditFailoverTest`; actualización de configuración, routers, auditoría, cuota,
  precarga y contratos REST/SSE/runtime/ingesta.
- Herramientas de evaluación: guardia local/remota y formato de listas del capturador,
  adaptadores live opt-in y harness de failover (no ejecutado contra servicios reales).
- Documentación: este informe, guías de auditoría/OpenAI/embeddings/configuración y
  decisiones vigentes en `DEVELOPMENT.md`.

No hay cambios en fuentes Angular, prompts, modelos, dimensiones, corpus, Flyway,
PostgreSQL, S3 Vectors ni Lambda. No se construye selector público ni dashboard.

## Validación

- `python3 py-scripts/test_secret_isolation.py`, que ejecuta **`mvn -B verify`** con
  entorno aislado: **619 unitarias y 86 integraciones offline correctas**;
  **27 entradas externas omitidas explícitamente**. Comprueba también que el secreto
  sintético no aparece en logs/reportes y que no se carga el archivo privado.
- Pruebas HTTP simuladas de auditoría: timeout de sonda, timeout de inferencia y
  HTTP 503 locales → Responses simulado, una sola petición por intento, metadatos
  del modelo remoto y cuota compartida agotada para generación después de auditar.
- Recuperación, posiciones remotas, JSON válido/inválido/inconcluso, degradación,
  saturación real, ausencia de falsos `busy`, cancelación, presupuestos, TLS y
  errores no recuperables, cooldown y compatibilidad/residencia: cubiertos offline.
- Angular `test:ci`: **31 correctas**; build local de producción correcto.
- `test_compose_configuration.py`: correcto, con defaults y sobrescrituras sintéticas,
  incluido antiguo flag remoto `false` que genera el bloqueo de migración.
- `test_r4r_capture_evaluation.py`: **13 correctas**; harness Python compila.
- `git diff --check` y `git diff --cached --check`: correctos.

No se realizaron capturas, renderizados visuales, llamadas de pago ni pruebas live.
La disponibilidad/VRAM real de PC/portátil y los tiempos actuales deben confirmarse
tras la revisión y el despliegue autorizado. No se certifica aquí un despliegue
que no se ha realizado. El único paso operativo previo es migrar posibles ajustes
privados obsoletos y revisar la rama; no hay bloqueos de compilación o pruebas.
