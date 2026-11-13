# RAG ES/EN después del corpus bilingüe — evaluación pgvector del 13 de noviembre de 2026

## Resultado y alcance efectivo

Se completaron **35 consultas reales e independientes contra pgvector del NAS**,
según la reducción de alcance solicitada durante la ejecución: **pares en español
(17) e impares en inglés (18)**. Se cubren los números 1–35 una sola vez. La batería
original de 70 no se presenta como ejecutada. Las fixtures conservan 35 casos por
idioma para usos posteriores.

Revisión documental: **24 PASS, 7 FAIL y 4 INCONCLUSIVE**.
Un PASS exige responder de forma pertinente y respaldada, o reconocer un límite
legítimo; no equivale simplemente a HTTP 200, texto fluido o presencia de `[Sx]`.
No se ha ajustado el sistema durante la captura. Las advertencias específicas de
cada respuesta se conservan, incluso cuando su valoración global es PASS.

S3 es un pendiente secundario independiente: su reconstrucción quedó bloqueada
por `PutVectors` denegado, y mantiene sus 282 vectores anteriores. No se utilizó
S3 ni Titan para estas 35 consultas. La decisión inicial de bloquear toda la
batería por AWS fue incorrecta; se corrigió ejecutando contra la base primaria.

Este es el único informe nuevo; se conserva intacto
[el histórico](rag-generalization-2026-11-10.md). Sin commits, push, modificaciones
funcionales del backend, frontend, PDFs, PostgreSQL, IAM ni configuración privada.

## Configuración y ejecución real

- Copia inmutable del JAR del worktree `/tmp/r4r-s3-evaluation`, base `f3d5aa9a7a77b5dac68af59e2574f18814048a9f`.
- SHA-256 del JAR: `f38f6dcc992ff89e8215345aa9c817af4ce83f1d722267734abb57fb98234490`.
- Servidor de evaluación independiente en loopback `127.0.0.1:18793`; peticiones
  HTTP SSE al endpoint real `/api/rag/answers/stream`, no llamadas simuladas.
- NAS `192.168.1.203`, base `r4r_rag`, 1.401 vectores Nomic de 768 dimensiones.
  Sesiones JDBC configuradas en solo lectura; Flyway, inicialización SQL/esquema,
  catálogo e ingesta desactivados. No se creó PostgreSQL local.
- `topK=5`, `minScore=0.63`, multiplicador de candidatos 8 y peso léxico 0.10,
  sin cambios de prompts, modelos o thresholds. Generación y embedding usan los
  routers configurados, sin seleccionar un proveedor para favorecer casos.
- Cada petición incluye la pregunta y el idioma; no se suministra
  `retrievalQuestion`, no hay memoria conversacional ni traducción externa. EN
  pasa por la traducción habitual del backend: esto **no es una prueba de búsqueda
  directa en inglés sin traducción**, ni una comparación de estrategias ES/EN.
- Cuota conservada: mínimo 21 segundos entre inicios, sin concurrencia de consultas,
  sin cambiar el límite de 20 segundos/IP ni las 12 peticiones/minuto.
- El runtime confirmó `qwen3:8b`, `nomic-embed-text:latest`, 768 dimensiones,
  topK 5 y score 0.63. El fallback remoto estaba configurado como
  `{'enabled': True, 'model': 'gpt-6-luna'}`; no se cambió esa opción.
  El proveedor/modelo de cada resultado aparece en el registro individual.
- Inicio de captura: `2026-11-13T17:08:14Z`. Fin: `2026-11-13T17:23:42Z`.

La primera tanda había completado 13 ES cuando se redujo el alcance. Se conservaron
los 6 pares aprovechables; las 7 consultas impares ES quedaron como extras, fuera
del denominador principal. Una petición ES14 quedó interrumpida por la detención
ordenada, sin resultado final, y se ejecutó de nuevo al reanudar. No se repitieron
casos para sustituir fallos por éxitos. Se completaron en total 42 respuestas
(35 seleccionadas + 7 extras) y hubo una petición cancelada por el cambio de alcance.

## Integridad del NAS

Antes y después de la batería se consultaron recuento y fingerprint en una
conexión independiente con `default_transaction_read_only=on`:

| Dato | Antes | Después |
|---|---|---|
| Base | `r4r_rag` | `r4r_rag` |
| IP interna PostgreSQL | `172.20.0.5` | `172.20.0.5` |
| Vectores | 1401 | 1401 |
| Fingerprint de UUID+contenido+metadatos+embedding | `a4684036054934fcce1dc8b6785dd74d` | `a4684036054934fcce1dc8b6785dd74d` |

**Fingerprint idéntico**: las consultas no modificaron el índice PostgreSQL.
Los metadatos fuente/ordinal/encabezados de todas las citas devueltas se
contrastaron con el export actual; sus rutas resuelven al corpus local.
Esto prueba trazabilidad, no respaldo semántico: se detectaron respuestas con
etiquetas correctas que atribuían a sus fuentes afirmaciones no documentadas.

## Métricas de la selección

Latencias E2E medidas desde el cliente SSE, incluyendo traducción cuando existe,
embedding, recuperación, generación y validación; no incluyen pausas de cuota.
P95 por rango más próximo. Las muestras ES y EN contienen preguntas diferentes:
**no permiten atribuir sus diferencias de calidad o latencia solo al idioma**.

| Grupo | N | PASS | FAIL | INCONCLUSIVE | `abstained=true` | Media s | Mediana s | P95 s |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| ES pares | 17 | 14 | 3 | 0 | 3 | 3.05 | 3.12 | 4.08 |
| EN impares | 18 | 10 | 4 | 4 | 4 | 3.94 | 4.04 | 5.73 |
| Originales 1–30 | 30 | 21 | 6 | 3 | 6 | 3.45 | 3.40 | 5.51 |
| Nuevos 31–35 | 5 | 3 | 1 | 1 | 1 | 3.87 | 4.13 | 4.65 |
| Total | 35 | 24 | 7 | 4 | 7 | 3.51 | 3.46 | 5.51 |

Razones internas observadas: `answered`: 28, `model_abstention`: 5, `missing_citations`: 2.

Las 35 peticiones terminaron con HTTP 200 y éxito real del adaptador pgvector
registrado en cada traza. El proveedor/modelo devuelto fue `qwen3:8b` en las 35;
no se observó uso de OpenAI. Las **34 citas devueltas apuntan a documentos ES**:
la traducción habitual conduce esta batería hacia evidencia española. Por tanto,
responder en inglés aquí no acredita que se haya recuperado directamente el corpus EN.

Una limitación explícita con respuesta y cita puede ser legítima sin que el
booleano `abstained` sea true. No se convierte automáticamente en fallo por el
valor histórico `evidence=false`. Tampoco se aprueba una conclusión de viabilidad
por añadir después «consulte a un profesional» si la conclusión carece de respaldo.

## Registro individual: preguntas, respuestas y resultados

Las respuestas siguientes son las capturas reales, sin reescritura editorial.
La revisión contrasta afirmaciones con los fragmentos citados y no comprueba de
forma independiente toda la normativa o ingeniería. Los tiempos internos de
recuperación incluyen embeddings; no se inventa una duración SQL aislada ni
scores/candidatos crudos que el endpoint no expone.

### 01 · EN · case-01 — PASS

**Pregunta:**

> If I want initial guidance on my renovation, what details should I provide?

**Respuesta real:**

> To receive initial guidance on your renovation, you should provide details such as location, photographs, approximate measurements, space usage, the problem, desired outcome, and timeline. This information allows for a first orientation, but it does not replace a site visit or measurement. [S4]

- Resultado: **PASS**; semántica `CORRECT`; relevancia `HIGH`; respaldo de citas `SUPPORTED`; idioma `PASS`.
- Revisión: Directly answers the requested initial-assessment information and correctly states that guidance does not replace a visit or measurements; all claims match FAQ-001 (ES ordinal3).
- HTTP 200; `abstained=False`; razón `answered`; proveedor/modelo `ollama-84fbb955-da72-32ae-b1f8-895120bd9cd8 / qwen3:8b`.
- E2E **3.42 s**; embedding 1.04 s; recuperación con embedding 1.15 s; generación 1.68 s.
- [S4] [es/faqs/R4R_40_ALL_FAQ.md](../../knowledge/es/faqs/R4R_40_ALL_FAQ.md) · ordinal 3 · Riansares 4R — todas las preguntas frecuentes / Presupuesto / FAQ-001 — ¿Qué información hace falta para una primera orientación?.

### 02 · ES · case-02 — PASS

**Pregunta:**

> En una obra, ¿en qué se distinguen la base de solera y el acabado de solado?

**Respuesta real:**

> La base de solera se distingue del acabado de solado por su función: la solera es la capa de soporte o base que recibe el pavimento, mientras que el solado es el acabado final transitable, como baldosa o piedra. Esta distinción está documentada en [S1], [S2] y [S4].

- Resultado: **PASS**; semántica `CORRECT`; relevancia `HIGH`; respaldo de citas `SUPPORTED`; idioma `PASS`.
- Revisión: Distingue correctamente base de soporte y acabado transitable; las tres secciones citadas respaldan la distinción y los ejemplos de baldosa/piedra.
- HTTP 200; `abstained=False`; razón `answered`; proveedor/modelo `ollama-84fbb955-da72-32ae-b1f8-895120bd9cd8 / qwen3:8b`.
- E2E **3.39 s**; embedding 1.05 s; recuperación con embedding 1.18 s; generación 2.20 s.
- [S1] [es/guides/R4R_84_PATIOS_SOLERAS_SOLADOS_Y_DRENAJE.md](../../knowledge/es/guides/R4R_84_PATIOS_SOLERAS_SOLADOS_Y_DRENAJE.md) · ordinal 1 · Riansares 4R — patios y pavimentos: solera, solado y drenaje / ¿Qué diferencia hay entre solera y solado?.
- [S2] [es/guides/R4R_105_SOLERA_SOLADO_PATIOS_DRENAJE_Y_PENDIENTES.md](../../knowledge/es/guides/R4R_105_SOLERA_SOLADO_PATIOS_DRENAJE_Y_PENDIENTES.md) · ordinal 1 · Patios y pavimentos — solera, solado, humedad y evacuación / ¿Qué diferencia hay entre solera y solado?.
- [S4] [es/R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md](../../knowledge/es/R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md) · ordinal 4 · Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 6. Caso A — Patio, solera, pavimento y drenaje / 6.3. Distinción entre solera y solado.

### 03 · EN · case-03 — PASS

**Pregunta:**

> Why does access affect the price of a concrete slab?

**Respuesta real:**

> The access affects the price of a concrete slab because manual mixing and transportation increase workdays, coordination, and risk compared to direct discharge or pumping. [S1]

- Resultado: **PASS**; semántica `CORRECT`; relevancia `HIGH`; respaldo de citas `SUPPORTED`; idioma `PASS`.
- Revisión: Explains the access-related cost mechanism using manual mixing/transport, workdays, coordination and risk exactly supported by FAQ-045; concise and pertinent.
- HTTP 200; `abstained=False`; razón `answered`; proveedor/modelo `ollama-84fbb955-da72-32ae-b1f8-895120bd9cd8 / qwen3:8b`.
- E2E **2.79 s**; embedding 1.05 s; recuperación con embedding 1.14 s; generación 1.15 s.
- [S1] [es/faqs/R4R_40_ALL_FAQ.md](../../knowledge/es/faqs/R4R_40_ALL_FAQ.md) · ordinal 57 · Riansares 4R — todas las preguntas frecuentes / Patios / FAQ-045 — ¿Por qué el acceso cambia el precio de una solera?.

### 04 · ES · case-04 — PASS

**Pregunta:**

> ¿Cuánto orienta una reforma integral de una casa antigua?

**Respuesta real:**

> Una reforma integral de una casa antigua puede oscilar entre 350–450 €/m² sin estructura ni redistribución, y puede elevarse a 533–600 €/m² o más si incluye estructura o cambios. [S3]

- Resultado: **PASS**; semántica `CORRECT_WITH_CAVEAT`; relevancia `HIGH`; respaldo de citas `SUPPORTED`; idioma `PASS`.
- Revisión: Rangos y condicionantes reproducen FAQ-030 dentro del encabezado expandido Casas antiguas. No inventa cifras; debería identificar expresamente la fecha/origen de la referencia para evitar leerla como tarifa actual.
- Advertencias: No explicit date/source qualification for historical indicative prices.
- HTTP 200; `abstained=False`; razón `answered`; proveedor/modelo `ollama-84fbb955-da72-32ae-b1f8-895120bd9cd8 / qwen3:8b`.
- E2E **4.08 s**; embedding 1.04 s; recuperación con embedding 2.20 s; generación 1.87 s.
- [S3] [es/faqs/R4R_40_ALL_FAQ.md](../../knowledge/es/faqs/R4R_40_ALL_FAQ.md) · ordinal 34 · Riansares 4R — todas las preguntas frecuentes / Casas antiguas.

### 05 · EN · case-05 — PASS

**Pregunta:**

> Does initial cost guidance replace a technical site visit?

**Respuesta real:**

> No. Initial cost guidance does not replace a technical site visit. According to [S1], initial orientation is based on available documents and does not substitute for a site visit, measurement, project, or report from a competent technician. Similarly, [S2] states that initial orientation does not replace a site visit or measurement.

- Resultado: **PASS**; semántica `CORRECT`; relevancia `HIGH`; respaldo de citas `SUPPORTED`; idioma `PASS`.
- Revisión: Direct no with site-visit, measurement and professional-assessment limits supported by R4R_114 and FAQ-001.
- HTTP 200; `abstained=False`; razón `answered`; proveedor/modelo `ollama-84fbb955-da72-32ae-b1f8-895120bd9cd8 / qwen3:8b`.
- E2E **3.69 s**; embedding 1.05 s; recuperación con embedding 1.23 s; generación 1.94 s.
- [S1] [es/guides/R4R_114_GUIA_DE_RESPUESTAS_PRUDENTES_Y_LIMITES_DEL_ASESORAMIENTO.md](../../knowledge/es/guides/R4R_114_GUIA_DE_RESPUESTAS_PRUDENTES_Y_LIMITES_DEL_ASESORAMIENTO.md) · ordinal 1 · Orientación al visitante — qué puede responder un asistente de reformas / ¿Qué significa una primera orientación?.
- [S2] [es/faqs/R4R_40_ALL_FAQ.md](../../knowledge/es/faqs/R4R_40_ALL_FAQ.md) · ordinal 3 · Riansares 4R — todas las preguntas frecuentes / Presupuesto / FAQ-001 — ¿Qué información hace falta para una primera orientación?.

### 06 · ES · case-06 — PASS

**Pregunta:**

> ¿Qué debe revisarse antes de pavimentar un patio?

**Respuesta real:**

> Antes de pavimentar un patio debe revisarse la base, compactación, espesor, armadura, pendientes, juntas, arquetas, canaletas, encuentros y acceso. [S1]

- Resultado: **PASS**; semántica `CORRECT`; relevancia `HIGH`; respaldo de citas `SUPPORTED`; idioma `PASS`.
- Revisión: Lista directamente pertinente, fiel a FAQ-046; incluye base, compactación, armadura, drenaje, pendientes, juntas y acceso sin inventar cifras.
- HTTP 200; `abstained=False`; razón `answered`; proveedor/modelo `ollama-84fbb955-da72-32ae-b1f8-895120bd9cd8 / qwen3:8b`.
- E2E **2.79 s**; embedding 1.04 s; recuperación con embedding 1.28 s; generación 1.51 s.
- [S1] [es/faqs/R4R_40_ALL_FAQ.md](../../knowledge/es/faqs/R4R_40_ALL_FAQ.md) · ordinal 58 · Riansares 4R — todas las preguntas frecuentes / Patios / FAQ-046 — ¿Qué debe revisarse antes de pavimentar un patio?.

### 07 · EN · case-07 — FAIL

**Pregunta:**

> When is it advisable to use ready-mix concrete for a slab?

**Respuesta real:**

> [Respuesta vacía; abstención pública]

- Resultado: **FAIL**; semántica `UNNECESSARY_ABSTENTION`; relevancia `INCONCLUSIVE`; respaldo de citas `NOT_APPLICABLE`; idioma `PASS`.
- Revisión: The corpus explicitly covers ready-mix concrete with good external access (R4R_10 §6.4), but this request ended in model_abstention. Returned SSE does not expose the full discarded context, so relevance of those retrieved chunks cannot be confirmed.
- Advertencias: Unnecessary abstention on an answerable documented question; Full retrieved context unavailable for this abstention.
- HTTP 200; `abstained=True`; razón `model_abstention`; proveedor/modelo `ollama-84fbb955-da72-32ae-b1f8-895120bd9cd8 / qwen3:8b`.
- E2E **2.37 s**; embedding 1.04 s; recuperación con embedding 1.13 s; generación 0.60 s.
- Citas: ninguna.

### 08 · ES · case-08 — PASS

**Pregunta:**

> ¿Cómo afecta un pasillo estrecho a la ejecución de una solera?

**Respuesta real:**

> Un pasillo estrecho afecta la ejecución de una solera debido a dificultades como acceso indirecto, transporte con carretillas, mezcla en obra o vertido por fases, así como mayor riesgo de mala ejecución si se intenta correr demasiado. [S1]

- Resultado: **PASS**; semántica `CORRECT`; relevancia `HIGH`; respaldo de citas `SUPPORTED`; idioma `PASS`.
- Revisión: Describe correctamente las restricciones de acceso, carretillas, vertido por fases y riesgo de correr, expresas en el apartado citado R4R_10 §6.4/Patio interior.
- HTTP 200; `abstained=False`; razón `answered`; proveedor/modelo `ollama-84fbb955-da72-32ae-b1f8-895120bd9cd8 / qwen3:8b`.
- E2E **3.04 s**; embedding 1.05 s; recuperación con embedding 1.12 s; generación 1.92 s.
- [S1] [es/R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md](../../knowledge/es/R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md) · ordinal 7 · Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 6. Caso A — Patio, solera, pavimento y drenaje / 6.4. Hormigón de camión y ejecución manual / Patio interior o pasillo estrecho.

### 09 · EN · case-09 — FAIL

**Pregunta:**

> What causes of damp can occur in an old home?

**Respuesta real:**

> Old homes can experience several causes of damp, including capillary action, lateral humidity, leakage, flooding, condensation, accumulated salts, roof or plumbing issues, and incompatible previous treatments. These causes are explicitly listed in the evidence and are documented in [S4].

- Resultado: **FAIL**; semántica `MOSTLY_CORRECT_WITH_UNSUPPORTED_DETAIL`; relevancia `HIGH`; respaldo de citas `PARTIALLY_SUPPORTED`; idioma `PASS`.
- Revisión: Most listed damp causes match cited R4R_00 §4.3, but the answer adds flooding as an explicitly documented cause; the citation lists water ingress/plumbing leakage, not flooding. This is a translation/support error rather than wholly irrelevant retrieval.
- Advertencias: Unsupported addition/translation: flooding is not listed in cited evidence.
- HTTP 200; `abstained=False`; razón `answered`; proveedor/modelo `ollama-84fbb955-da72-32ae-b1f8-895120bd9cd8 / qwen3:8b`.
- E2E **5.51 s**; embedding 1.05 s; recuperación con embedding 2.84 s; generación 1.98 s.
- [S4] [es/R4R_00_IDENTIDAD_CRITERIOS_Y_VOZ_EDITORIAL.md](../../knowledge/es/R4R_00_IDENTIDAD_CRITERIOS_Y_VOZ_EDITORIAL.md) · ordinal 18 · Riansares 4R — identidad, criterios y voz editorial / 4. Reglas de factualidad para el LLM / 4.3. Humedades.

### 10 · ES · case-10 — PASS

**Pregunta:**

> ¿Debe incluir el presupuesto de una reforma los plazos de ejecución?

**Respuesta real:**

> Sí, el presupuesto de una reforma debe incluir los plazos de ejecución, según se indica en [S4].

- Resultado: **PASS**; semántica `CORRECT`; relevancia `HIGH`; respaldo de citas `SUPPORTED`; idioma `PASS`.
- Revisión: Responde directamente que los plazos deben figurar en presupuesto; FAQ-003 lo incluye explícitamente.
- HTTP 200; `abstained=False`; razón `answered`; proveedor/modelo `ollama-84fbb955-da72-32ae-b1f8-895120bd9cd8 / qwen3:8b`.
- E2E **2.26 s**; embedding 1.05 s; recuperación con embedding 1.17 s; generación 1.08 s.
- [S4] [es/faqs/R4R_40_ALL_FAQ.md](../../knowledge/es/faqs/R4R_40_ALL_FAQ.md) · ordinal 5 · Riansares 4R — todas las preguntas frecuentes / Presupuesto / FAQ-003 — ¿Qué debe contener un presupuesto?.

### 11 · EN · case-11 — PASS

**Pregunta:**

> What information should a customer provide to request an estimate?

**Respuesta real:**

> To request an estimate, a customer should provide location, photographs, approximate measurements, space usage, the problem, the desired outcome, and the proposed timeline. This information is necessary for an initial orientation, as it helps in understanding the scope of work and setting expectations. [S3]

- Resultado: **PASS**; semántica `CORRECT`; relevancia `HIGH`; respaldo de citas `SUPPORTED`; idioma `PASS`.
- Revisión: The requested initial quotation inputs match FAQ-001 within the expanded Presupuesto section: location, photos, measurements, use, problem, outcome and timeframe.
- HTTP 200; `abstained=False`; razón `answered`; proveedor/modelo `ollama-84fbb955-da72-32ae-b1f8-895120bd9cd8 / qwen3:8b`.
- E2E **3.83 s**; embedding 1.05 s; recuperación con embedding 1.45 s; generación 1.87 s.
- [S3] [es/faqs/R4R_40_ALL_FAQ.md](../../knowledge/es/faqs/R4R_40_ALL_FAQ.md) · ordinal 2 · Riansares 4R — todas las preguntas frecuentes / Presupuesto.

### 12 · ES · case-12 — PASS

**Pregunta:**

> ¿Qué datos hacen falta para orientar una reforma de baño?

**Respuesta real:**

> Para orientar una reforma de baño, hacen falta ubicación, fotografías, medidas aproximadas, uso del espacio, problema, resultado esperado y plazo. Esto se basa en la información requerida para una primera orientación, según [S4].

- Resultado: **PASS**; semántica `CORRECT`; relevancia `HIGH`; respaldo de citas `SUPPORTED`; idioma `PASS`.
- Revisión: Da una lista básica válida para orientar una reforma de baño y aclara que corresponde a primera orientación. FAQ-001 sustenta cada dato, aunque no profundiza en ventilación, instalaciones ni disponibilidad de otro aseo.
- HTTP 200; `abstained=False`; razón `answered`; proveedor/modelo `ollama-84fbb955-da72-32ae-b1f8-895120bd9cd8 / qwen3:8b`.
- E2E **2.90 s**; embedding 1.04 s; recuperación con embedding 1.17 s; generación 1.73 s.
- [S4] [es/faqs/R4R_40_ALL_FAQ.md](../../knowledge/es/faqs/R4R_40_ALL_FAQ.md) · ordinal 3 · Riansares 4R — todas las preguntas frecuentes / Presupuesto / FAQ-001 — ¿Qué información hace falta para una primera orientación?.

### 13 · EN · case-13 — PASS

**Pregunta:**

> What kinds of renovation work does this company do? Does it work on houses and offices?

**Respuesta real:**

> The company performs renovation work including bathrooms, kitchens, homes, small offices and shops, prudent redistributions, habitability improvements, and partial or full renovation of finishes and installations. It works on both houses and offices. [S1]

- Resultado: **PASS**; semántica `CORRECT_WITH_LANGUAGE_CAVEAT`; relevancia `HIGH`; respaldo de citas `SUPPORTED`; idioma `PASS`.
- Revisión: Correctly confirms houses/offices and reproduces the documented service list. "Prudent redistributions" is awkward English for carefully assessed layout changes, but the factual content is supported.
- Advertencias: Awkward terminology: prudent redistributions.
- HTTP 200; `abstained=False`; razón `answered`; proveedor/modelo `ollama-84fbb955-da72-32ae-b1f8-895120bd9cd8 / qwen3:8b`.
- E2E **4.54 s**; embedding 1.06 s; recuperación con embedding 2.20 s; generación 1.68 s.
- [S1] [es/R4R_00_IDENTIDAD_CRITERIOS_Y_VOZ_EDITORIAL.md](../../knowledge/es/R4R_00_IDENTIDAD_CRITERIOS_Y_VOZ_EDITORIAL.md) · ordinal 5 · Riansares 4R — identidad, criterios y voz editorial / 2. Identidad de Riansares 4R / 2.3. Servicios nucleares / Reformas.

### 14 · ES · case-14 — PASS

**Pregunta:**

> ¿Qué diferencias hay entre la cal aérea y la cal hidráulica natural para trabajar sobre muros antiguos?

**Respuesta real:**

> La cal aérea es más transpirable y tradicional, pero menos adecuada para cargas gruesas sobre soporte húmedo o débil, según [S1]. La cal hidráulica natural NHL 2 o NHL 3.5 es técnicamente más compatible con piedra, tierra y fábricas antiguas, ya que fragua con humedad ambiental y mantiene una rigidez moderada, según [S1].

- Resultado: **PASS**; semántica `CORRECT`; relevancia `HIGH`; respaldo de citas `SUPPORTED`; idioma `PASS`.
- Revisión: Diferencia cal aérea y NHL según R4R_20 §12.2: transpirabilidad, limitación en aplicaciones gruesas/humedad, fraguado y rigidez moderada. Mantiene grados NHL2/3.5 documentados; no inventa dosificación.
- HTTP 200; `abstained=False`; razón `answered`; proveedor/modelo `ollama-84fbb955-da72-32ae-b1f8-895120bd9cd8 / qwen3:8b`.
- E2E **3.62 s**; embedding 1.08 s; recuperación con embedding 1.19 s; generación 2.41 s.
- [S1] [es/R4R_20_CASOS_OFICINAS_CUEVAS_BANOS_Y_FILTROS.md](../../knowledge/es/R4R_20_CASOS_OFICINAS_CUEVAS_BANOS_Y_FILTROS.md) · ordinal 11 · Riansares 4R — casos de oficinas, cuevas, baños y filtros comerciales / 12. Caso G — Cueva o bodega antigua: estabilización con mortero de cal y malla / 12.2. Cal aérea, NHL y morteros híbridos.

### 15 · EN · case-15 — PASS

**Pregunta:**

> What precautions should be taken before laying new tiles over an existing floor?

**Respuesta real:**

> Before laying new tiles over an existing floor, check adherence, flatness, moisture, load capacity, joints, and compatibility of the adhesive system. [S1] If there are loose pieces or the support is degraded, it must be repaired before proceeding. [S1] Do not assume that tiling can always be done over existing surfaces without proper assessment. [S1]

- Resultado: **PASS**; semántica `CORRECT`; relevancia `HIGH`; respaldo de citas `SUPPORTED`; idioma `PASS`.
- Revisión: The cited tile-over-tile checks support adhesion, flatness, moisture, loads, junctions and adhesive compatibility plus removal/repair of loose material; the precautions are applicable to the queried substrate.
- HTTP 200; `abstained=False`; razón `answered`; proveedor/modelo `ollama-84fbb955-da72-32ae-b1f8-895120bd9cd8 / qwen3:8b`.
- E2E **5.73 s**; embedding 1.05 s; recuperación con embedding 2.86 s; generación 2.23 s.
- [S1] [es/guides/R4R_102_REFORMA_COCINA_MUEBLES_AZULEJOS_Y_ELECTRODOMESTICOS.md](../../knowledge/es/guides/R4R_102_REFORMA_COCINA_MUEBLES_AZULEJOS_Y_ELECTRODOMESTICOS.md) · ordinal 5 · Cocinas — alcance, materiales y coordinación de oficios / ¿Qué precauciones hay al colocar azulejos nuevos sobre antiguos?.

### 16 · ES · case-16 — FAIL

**Pregunta:**

> Estoy pensando en comprar un piso bastante antiguo, pero tiene grietas en algunas paredes. ¿Cómo puedo saber si es algo superficial o si me voy a meter en un problema serio?

**Respuesta real:**

> Para determinar si las grietas en las paredes son superficiales o indican un problema serio, debes identificar la ubicación, titularidad y responsable según la documentación y normativa de propiedad, ya que no se debe atribuir culpa sin inspección. Además, es necesario diagnosticar si la grieta proviene de retracción superficial, asiento, daño por agua o un problema estructural. [S1]

- Resultado: **FAIL**; semántica `OFF_TOPIC_AND_UNSUPPORTED`; relevancia `LOW`; respaldo de citas `PARTIALLY_UNSUPPORTED`; idioma `PASS`.
- Revisión: La pregunta pide evaluar gravedad de grietas antes de comprar. Responde con titularidad/responsabilidad por filtraciones de cubierta: S1 R4R_110 ordinal1 es irrelevante. La segunda frase enumera retracción/asiento/daño de agua/problema estructural sin apoyo en S1 y omite una evaluación competente de las grietas.
- Advertencias: Cited roof-leak ownership advice is not crack-risk assessment; Claim about crack causes unsupported by the cited context.
- HTTP 200; `abstained=False`; razón `answered`; proveedor/modelo `ollama-84fbb955-da72-32ae-b1f8-895120bd9cd8 / qwen3:8b`.
- E2E **3.71 s**; embedding 1.09 s; recuperación con embedding 1.20 s; generación 2.50 s.
- [S1] [es/guides/R4R_110_COMUNIDADES_PATIOS_CUBIERTAS_Y_ZONAS_COMUNES.md](../../knowledge/es/guides/R4R_110_COMUNIDADES_PATIOS_CUBIERTAS_Y_ZONAS_COMUNES.md) · ordinal 1 · Comunidades de vecinos — reparaciones y obras en elementos comunes / ¿Una filtración en cubierta afecta solo a un piso?.

### 17 · EN · case-17 — INCONCLUSIVE

**Pregunta:**

> The kitchen cabinets are fine. How can I refresh it without taking everything apart?

**Respuesta real:**

> If the kitchen cabinets are fine, you can refresh the kitchen by replacing tiles without taking everything apart, as long as you first repair any plumbing issues and ensure the support and surfaces are firm and dry. This is possible as part of a kitchen renovation, and the tile replacement will be budgeted as a separate item from plumbing and cabinetry work. [S2]

- Resultado: **INCONCLUSIVE**; semántica `PARTIAL_SUPPORTED`; relevancia `PARTIAL`; respaldo de citas `PARTIALLY_SUPPORTED`; idioma `PASS`.
- Revisión: Tile replacement is a documented optional refresh and drying/repair conditions are supported. However, the cited section does not establish doing it without dismantling sound cabinets or assess access; this central preservation condition is asserted rather than explained.
- Advertencias: Cabinet preservation/access claim not established by the cited tile-replacement section.
- HTTP 200; `abstained=False`; razón `answered`; proveedor/modelo `ollama-84fbb955-da72-32ae-b1f8-895120bd9cd8 / qwen3:8b`.
- E2E **4.03 s**; embedding 1.04 s; recuperación con embedding 1.19 s; generación 2.16 s.
- [S2] [es/guides/R4R_80_COCINAS_FONTANERIA_Y_FUGAS.md](../../knowledge/es/guides/R4R_80_COCINAS_FONTANERIA_Y_FUGAS.md) · ordinal 4 · Riansares 4R — cocinas: tuberías rotas, fugas y decisiones previas a la reforma / Pregunta — ¿Puedo sustituir también los azulejos de la cocina?.

### 18 · ES · case-19 — PASS

**Pregunta:**

> Cuando llueve fuerte, mi terraza cala al piso de abajo. ¿Por dónde empiezo?

**Respuesta real:**

> Cuando llueve fuerte y tu terraza cala al piso de abajo, debes revisar el soporte, filtraciones, pendientes, desagües, juntas, umbrales y remates en encuentros, ya que estos elementos son fundamentales para la impermeabilización. [S1]

- Resultado: **PASS**; semántica `CORRECT`; relevancia `HIGH`; respaldo de citas `SUPPORTED`; idioma `PASS`.
- Revisión: Ofrece las comprobaciones iniciales correctas para terraza que filtra: soporte, pendientes, drenajes, juntas, umbrales y encuentros, directamente sustentadas por R4R_105 ordinal4.
- HTTP 200; `abstained=False`; razón `answered`; proveedor/modelo `ollama-84fbb955-da72-32ae-b1f8-895120bd9cd8 / qwen3:8b`.
- E2E **3.12 s**; embedding 1.05 s; recuperación con embedding 1.16 s; generación 1.96 s.
- [S1] [es/guides/R4R_105_SOLERA_SOLADO_PATIOS_DRENAJE_Y_PENDIENTES.md](../../knowledge/es/guides/R4R_105_SOLERA_SOLADO_PATIOS_DRENAJE_Y_PENDIENTES.md) · ordinal 4 · Patios y pavimentos — solera, solado, humedad y evacuación / ¿Qué revisar antes de impermeabilizar una terraza?.

### 19 · EN · case-22 — INCONCLUSIVE

**Pregunta:**

> We've just bought a flat and want to renovate it before moving in. In what order should the work be done to avoid having to break things that are already finished?

**Respuesta real:**

> To avoid breaking already finished work, the renovation should follow the order outlined in [S1], which specifies that distribution, installations, slopes, ventilation, and support conditions are reviewed first. Demolitions, plumbing, drainage, waterproofing, testing, tiling, sanitary fixtures, and finishing touches are planned next. This sequence helps prevent the need to reopen areas once they are completed.

- Resultado: **INCONCLUSIVE**; semántica `PARTIAL_SUPPORTED`; relevancia `PARTIAL`; respaldo de citas `PARTIALLY_SUPPORTED`; idioma `PASS`.
- Revisión: Services and testing before finishes are sensible and documented, but the answer applies a bathroom-specific sequence (slopes, sanitary fixtures) to a whole-flat renovation without identifying that limit. It is partial guidance, not a supported complete flat-wide sequence.
- Advertencias: Bathroom-only sequence generalized to whole-flat renovation.
- HTTP 200; `abstained=False`; razón `answered`; proveedor/modelo `ollama-84fbb955-da72-32ae-b1f8-895120bd9cd8 / qwen3:8b`.
- E2E **4.56 s**; embedding 1.05 s; recuperación con embedding 1.15 s; generación 2.25 s.
- [S1] [es/guides/R4R_81_BANOS_DUCHAS_Y_SANEAMIENTO.md](../../knowledge/es/guides/R4R_81_BANOS_DUCHAS_Y_SANEAMIENTO.md) · ordinal 1 · Riansares 4R — baños: fugas, duchas, saneamiento e impermeabilización / ¿En qué orden se reforma un baño?.

### 20 · ES · case-18 — PASS

**Pregunta:**

> En mi casa se oye absolutamente todo lo que hacen los vecinos de arriba. ¿Se puede mejorar el aislamiento acústico sin perder demasiada altura en las habitaciones?

**Respuesta real:**

> Según [S1], el aislamiento acústico busca controlar la transmisión del sonido, pero no se establece si es posible mejorar el aislamiento acústico sin perder altura en las habitaciones. Por lo tanto, no se puede afirmar con base en la evidencia proporcionada si es posible mejorar el aislamiento acústico sin afectar la altura de las habitaciones.

- Resultado: **PASS**; semántica `SAFE_LIMITATION`; relevancia `PARTIAL`; respaldo de citas `SUPPORTED`; idioma `PASS`.
- Revisión: Define aislamiento acústico y declara explícitamente que la evidencia no demuestra viabilidad sin perder altura. No promete una solución ni espesor. Aunque abstained=false y la etiqueta histórica espera insuficiencia, la respuesta parcial con límite documental es honesta y correcta.
- Advertencias: Public abstained=false despite substantive refusal to establish the requested feasibility.
- HTTP 200; `abstained=False`; razón `answered`; proveedor/modelo `ollama-84fbb955-da72-32ae-b1f8-895120bd9cd8 / qwen3:8b`.
- E2E **3.60 s**; embedding 1.07 s; recuperación con embedding 1.17 s; generación 2.43 s.
- [S1] [es/guides/R4R_87_AISLAMIENTO_VENTILACION_Y_REHABILITACION_ENERGETICA.md](../../knowledge/es/guides/R4R_87_AISLAMIENTO_VENTILACION_Y_REHABILITACION_ENERGETICA.md) · ordinal 5 · Riansares 4R — eficiencia energética, aislamiento y ventilación / ¿Qué diferencia hay entre aislamiento térmico y acústico?.

### 21 · EN · case-20 — FAIL

**Pregunta:**

> I want to replace my home's windows, but I don't know whether triple glazing is really worthwhile or whether double glazing is enough.

**Respuesta real:**

> Replacing windows with triple glazing may not be necessary if double glazing is sufficient, as the evidence indicates that the key factors are ventilation, humidity balance, and thermal bridges. [S2] and [S5] suggest that the focus should be on ensuring adequate air renewal and addressing potential sources of moisture accumulation, rather than solely on the number of glazing layers. The effectiveness of the windows depends on these factors, not just their insulation properties.

- Resultado: **FAIL**; semántica `UNSUPPORTED_COMPARISON`; relevancia `PARTIAL`; respaldo de citas `PARTIALLY_SUPPORTED`; idioma `PASS`.
- Revisión: The cited sections discuss airtightness, ventilation and condensation, not double-versus-triple glazing performance or whether the upgrade is worthwhile. The answer nevertheless advances a glazing-choice conclusion and presents condensation factors as the decisive comparison.
- Advertencias: Glazing recommendation/comparison not supported by the cited condensation guidance.
- HTTP 200; `abstained=False`; razón `answered`; proveedor/modelo `ollama-84fbb955-da72-32ae-b1f8-895120bd9cd8 / qwen3:8b`.
- E2E **5.06 s**; embedding 1.05 s; recuperación con embedding 1.37 s; generación 2.37 s.
- [S2] [es/guides/R4R_87_AISLAMIENTO_VENTILACION_Y_REHABILITACION_ENERGETICA.md](../../knowledge/es/guides/R4R_87_AISLAMIENTO_VENTILACION_Y_REHABILITACION_ENERGETICA.md) · ordinal 2 · Riansares 4R — eficiencia energética, aislamiento y ventilación / ¿Cambiar ventanas puede aumentar la condensación?.
- [S5] [es/guides/R4R_111_AISLAMIENTO_VENTANAS_Y_REHABILITACION_ENERGETICA.md](../../knowledge/es/guides/R4R_111_AISLAMIENTO_VENTANAS_Y_REHABILITACION_ENERGETICA.md) · ordinal 3 · Aislamiento y rehabilitación energética — decisiones fundamentadas / ¿Unas ventanas muy herméticas pueden aumentar el moho?.

### 22 · ES · case-21 — FAIL

**Pregunta:**

> Tengo un pasillo largo y oscuro. ¿Cómo gano luz y aprovecho el espacio?

**Respuesta real:**

> Para ganar luz en un pasillo largo y oscuro, se debe garantizar ventilación adecuada según normativa aplicable, como se menciona en [S4]. Para aprovechar el espacio, se deben considerar dificultades como acceso indirecto y transporte con carretillas, tal como se describe en [S3].

- Resultado: **FAIL**; semántica `INCORRECT_OFF_TOPIC`; relevancia `LOW`; respaldo de citas `NON_SUPPORTING`; idioma `PASS`.
- Revisión: Confunde ganar luz con ventilación de un extractor y aprovechar espacio con logística de carretillas al hacer una solera. S3 y S4 existen, pero ninguna evidencia apoya que esas acciones resuelvan iluminación o aprovechamiento espacial.
- Advertencias: Ventilation incorrectly presented as a way to gain light; Wheelbarrow logistics incorrectly presented as interior space planning.
- HTTP 200; `abstained=False`; razón `answered`; proveedor/modelo `ollama-84fbb955-da72-32ae-b1f8-895120bd9cd8 / qwen3:8b`.
- E2E **3.30 s**; embedding 1.05 s; recuperación con embedding 1.11 s; generación 2.18 s.
- [S3] [es/R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md](../../knowledge/es/R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md) · ordinal 7 · Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 6. Caso A — Patio, solera, pavimento y drenaje / 6.4. Hormigón de camión y ejecución manual / Patio interior o pasillo estrecho.
- [S4] [es/guides/R4R_104_DUCHAS_PENDIENTES_IMPERMEABILIZACION_Y_BANOS.md](../../knowledge/es/guides/R4R_104_DUCHAS_PENDIENTES_IMPERMEABILIZACION_Y_BANOS.md) · ordinal 5 · Baños — duchas, pendientes, impermeabilización y accesibilidad / ¿El extractor debe encenderse con la luz?.

### 23 · EN · case-23 — PASS

**Pregunta:**

> The wooden beams in my old house are bowed. Can they be reinforced or do they need replacing?

**Respuesta real:**

> The evidence does not explicitly state whether bowed wooden beams can be reinforced or need replacing. It does, however, mention that when reviewing wooden beams, it is important to check for damage, humidity, biological attacks, and structural integrity. This suggests that a structural evaluation is necessary to determine if reinforcement or replacement is required.
>
> [S2]

- Resultado: **PASS**; semántica `CORRECT_LIMITED_GUIDANCE`; relevancia `HIGH`; respaldo de citas `SUPPORTED`; idioma `PASS`.
- Revisión: Explicitly declines to choose reinforcement versus replacement and recommends structural evaluation for bowed timber, matching R4R_83 on deflections, damage, moisture, biological attack and professional assessment.
- HTTP 200; `abstained=False`; razón `answered`; proveedor/modelo `ollama-84fbb955-da72-32ae-b1f8-895120bd9cd8 / qwen3:8b`.
- E2E **4.05 s**; embedding 1.05 s; recuperación con embedding 1.16 s; generación 1.97 s.
- [S2] [es/guides/R4R_83_VIVIENDAS_ANTIGUAS_Y_REHABILITACION.md](../../knowledge/es/guides/R4R_83_VIVIENDAS_ANTIGUAS_Y_REHABILITACION.md) · ordinal 2 · Riansares 4R — viviendas antiguas: rehabilitar con criterio y seguridad / ¿Cómo se planifica una reforma con madera y muros antiguos?.

### 24 · ES · case-24 — FAIL

**Pregunta:**

> Tenemos una oficina donde hace mucho calor en unas zonas y frío en otras. ¿Se puede solucionar con una reforma de la climatización sin cambiar todo el sistema?

**Respuesta real:**

> Sí, se puede solucionar con una reforma de la climatización sin cambiar todo el sistema, ya que se pueden abordar problemas específicos como puentes térmicos que influyen en las diferencias de temperatura. [S3] Sin embargo, la solución requiere un análisis profesional para garantizar que sea compatible con el edificio.

- Resultado: **FAIL**; semántica `UNSUPPORTED_FEASIBILITY`; relevancia `LOW`; respaldo de citas `NON_SUPPORTING`; idioma `PASS`.
- Revisión: Afirma que se puede solucionar desequilibrio de climatización sin cambiar todo el sistema a partir de una definición de puentes térmicos. S3 no diagnostica esa oficina ni prueba esa viabilidad; la advertencia final de análisis profesional no corrige la afirmación inicial categórica.
- Advertencias: Unjustified affirmative HVAC feasibility conclusion; Thermal-bridge definition substituted for a system diagnosis.
- HTTP 200; `abstained=False`; razón `answered`; proveedor/modelo `ollama-84fbb955-da72-32ae-b1f8-895120bd9cd8 / qwen3:8b`.
- E2E **3.46 s**; embedding 1.07 s; recuperación con embedding 1.39 s; generación 2.07 s.
- [S3] [es/guides/R4R_111_AISLAMIENTO_VENTANAS_Y_REHABILITACION_ENERGETICA.md](../../knowledge/es/guides/R4R_111_AISLAMIENTO_VENTANAS_Y_REHABILITACION_ENERGETICA.md) · ordinal 8 · Aislamiento y rehabilitación energética — decisiones fundamentadas / ¿Qué son puentes térmicos?.

### 25 · EN · case-25 — INCONCLUSIVE

**Pregunta:**

> I want to turn a shop into a café. What building work do I need before opening?

**Respuesta real:**

> Before opening, you need to check **usage and capacity, extraction, sanitation, fire safety, accessibility, electricity, hygiene, and possible gas installations**. This is required for any hospitality space, as noted in [S2].

- Resultado: **INCONCLUSIVE**; semántica `PARTIAL_SUPPORTED`; relevancia `PARTIAL`; respaldo de citas `SUPPORTED_WITH_SCOPE_CAVEAT`; idioma `PASS`.
- Revisión: The hospitality checklist is directly supported and does not promise conversion feasibility. It is useful preliminary guidance, but does not identify the actual building-work scope or clearly state the need for project-specific regulations/specialist assessment; its blanket wording about any hospitality space is broader than the source.
- Advertencias: General preliminary checks rather than actual conversion-work scope; Omitted explicit specialist/project-specific limitation.
- HTTP 200; `abstained=False`; razón `answered`; proveedor/modelo `ollama-84fbb955-da72-32ae-b1f8-895120bd9cd8 / qwen3:8b`.
- E2E **3.27 s**; embedding 1.05 s; recuperación con embedding 1.15 s; generación 1.44 s.
- [S2] [es/guides/R4R_89_OTROS_ESPACIOS_Y_OBRAS_ESPECIALES.md](../../knowledge/es/guides/R4R_89_OTROS_ESPACIOS_Y_OBRAS_ESPECIALES.md) · ordinal 3 · Riansares 4R — otras obras: piscinas, naves, terrazas y equipamientos / ¿Qué comprobar antes de reformar un local de hostelería?.

### 26 · ES · case-26 — PASS

**Pregunta:**

> ¿Qué potencia y qué número de captadores geotérmicos necesito para climatizar una vivienda de 180 m²?

**Respuesta real:**

> [Respuesta vacía; abstención pública]

- Resultado: **PASS**; semántica `CORRECT_ABSTENTION`; relevancia `NOT_APPLICABLE`; respaldo de citas `EMPTY_AS_EXPECTED`; idioma `NOT_APPLICABLE`.
- Revisión: Se abstiene con respuesta y citas vacías ante cálculo específico de potencia y captadores geotérmicos no documentado; no inventa dimensionamiento para 180 m².
- HTTP 200; `abstained=True`; razón `missing_citations`; proveedor/modelo `ollama-84fbb955-da72-32ae-b1f8-895120bd9cd8 / qwen3:8b`.
- E2E **2.93 s**; embedding 1.07 s; recuperación con embedding 1.17 s; generación 1.76 s.
- Citas: ninguna.

### 27 · EN · case-27 — PASS

**Pregunta:**

> What exact cable cross-section and circuit breaker does the REBT require for installing an 11 kW electric car charger in my garage?

**Respuesta real:**

> [Respuesta vacía; abstención pública]

- Resultado: **PASS**; semántica `JUSTIFIED_ABSTENTION`; relevancia `INCONCLUSIVE`; respaldo de citas `NOT_APPLICABLE`; idioma `NOT_APPLICABLE`.
- Revisión: Correctly abstains from prescribing exact charger cable cross-section/breaker sizing that the corpus does not establish; no fabricated electrical design or citations.
- Advertencias: Full discarded retrieved context unavailable.
- HTTP 200; `abstained=True`; razón `model_abstention`; proveedor/modelo `ollama-84fbb955-da72-32ae-b1f8-895120bd9cd8 / qwen3:8b`.
- E2E **3.00 s**; embedding 1.05 s; recuperación con embedding 1.17 s; generación 0.63 s.
- Citas: ninguna.

### 28 · ES · case-28 — PASS

**Pregunta:**

> ¿De qué manera producen alimento las plantas mediante la fotosíntesis?

**Respuesta real:**

> [Respuesta vacía; abstención pública]

- Resultado: **PASS**; semántica `CORRECT_ABSTENTION`; relevancia `NOT_APPLICABLE`; respaldo de citas `EMPTY_AS_EXPECTED`; idioma `NOT_APPLICABLE`.
- Revisión: Se abstiene correctamente de explicar fotosíntesis, fuera del dominio documental de reformas; respuesta y citas vacías.
- HTTP 200; `abstained=True`; razón `model_abstention`; proveedor/modelo `ollama-84fbb955-da72-32ae-b1f8-895120bd9cd8 / qwen3:8b`.
- E2E **1.74 s**; embedding 1.05 s; recuperación con embedding 1.13 s; generación 0.61 s.
- Citas: ninguna.

### 29 · EN · case-29 — PASS

**Pregunta:**

> What is the mass of Jupiter in kilograms?

**Respuesta real:**

> [Respuesta vacía; abstención pública]

- Resultado: **PASS**; semántica `JUSTIFIED_ABSTENTION`; relevancia `INCONCLUSIVE`; respaldo de citas `NOT_APPLICABLE`; idioma `NOT_APPLICABLE`.
- Revisión: Correct blank-answer abstention for Jupiter mass, outside the renovation corpus, with no fabricated citations.
- Advertencias: Full discarded retrieved context unavailable.
- HTTP 200; `abstained=True`; razón `model_abstention`; proveedor/modelo `ollama-84fbb955-da72-32ae-b1f8-895120bd9cd8 / qwen3:8b`.
- E2E **2.04 s**; embedding 1.04 s; recuperación con embedding 1.07 s; generación 0.42 s.
- Citas: ninguna.

### 30 · ES · case-30 — PASS

**Pregunta:**

> ¿Quién escribió la novela Cien años de soledad?

**Respuesta real:**

> [Respuesta vacía; abstención pública]

- Resultado: **PASS**; semántica `CORRECT_ABSTENTION`; relevancia `NOT_APPLICABLE`; respaldo de citas `EMPTY_AS_EXPECTED`; idioma `NOT_APPLICABLE`.
- Revisión: Se abstiene correctamente ante autoría literaria fuera del dominio del corpus; respuesta y citas vacías.
- HTTP 200; `abstained=True`; razón `model_abstention`; proveedor/modelo `ollama-84fbb955-da72-32ae-b1f8-895120bd9cd8 / qwen3:8b`.
- E2E **1.60 s**; embedding 1.04 s; recuperación con embedding 1.09 s; generación 0.50 s.
- Citas: ninguna.

### 31 · EN · case-31 — PASS

**Pregunta:**

> I have broken pipes in my kitchen. What should I do?

**Respuesta real:**

> If you have broken pipes in your kitchen, the first step is to stop the water supply using the accessible shut-off valve, avoid using the affected installation, and contact a professional for locating and repairing the leak. [S2] If there is proximity of water and electricity, avoid manipulating hazardous elements and do not touch electrical components affected by water; electrical intervention should be handled by a qualified professional. [S4]

- Resultado: **PASS**; semántica `CORRECT`; relevancia `HIGH`; respaldo de citas `SUPPORTED`; idioma `PASS`.
- Revisión: Answers the broken-pipe emergency directly: accessible water isolation, avoid using the affected installation, professional leak repair and electrical safety. Both cited R4R_80 contexts support those claims without introducing prices or compulsory full renovation.
- HTTP 200; `abstained=False`; razón `answered`; proveedor/modelo `ollama-84fbb955-da72-32ae-b1f8-895120bd9cd8 / qwen3:8b`.
- E2E **4.33 s**; embedding 1.04 s; recuperación con embedding 1.16 s; generación 2.61 s.
- [S2] [es/guides/R4R_80_COCINAS_FONTANERIA_Y_FUGAS.md](../../knowledge/es/guides/R4R_80_COCINAS_FONTANERIA_Y_FUGAS.md) · ordinal 1 · Riansares 4R — cocinas: tuberías rotas, fugas y decisiones previas a la reforma / Pregunta — Tengo tuberías rotas en la cocina, ¿qué hago primero?.
- [S4] [es/guides/R4R_80_COCINAS_FONTANERIA_Y_FUGAS.md](../../knowledge/es/guides/R4R_80_COCINAS_FONTANERIA_Y_FUGAS.md) · ordinal 0 · Riansares 4R — cocinas: tuberías rotas, fugas y decisiones previas a la reforma.

### 32 · ES · case-32 — PASS

**Pregunta:**

> Tengo tuberías rotas en mi cocina. ¿Qué hago?
> Además, quiero reformar la cocina. ¿Qué me recomiendas?

**Respuesta real:**

> Si tienes tuberías rotas en tu cocina, la prioridad es detener el suministro con la llave de paso accesible, evitar usar esa instalación y solicitar a un profesional la localización y reparación de la fuga [S3]. Además, si quieres reformar la cocina, conviene resolver primero la fuga y revisar fontanería y desagües antes de colocar azulejos, muebles y acabados [S3].

- Resultado: **PASS**; semántica `CORRECT`; relevancia `HIGH`; respaldo de citas `SUPPORTED`; idioma `PASS`.
- Revisión: Responde las dos partes acumulativas: detener suministro/evitar uso/llamar al profesional y resolver la fuga y revisar fontanería-desagües antes de acabados. La expansión del H1 de R4R_80 ordinal0 incluye esas dos preguntas y sustenta exactamente la secuencia. No introduce costes ni reforma obligatoria.
- HTTP 200; `abstained=False`; razón `answered`; proveedor/modelo `ollama-84fbb955-da72-32ae-b1f8-895120bd9cd8 / qwen3:8b`.
- E2E **3.57 s**; embedding 1.06 s; recuperación con embedding 1.18 s; generación 2.38 s.
- [S3] [es/guides/R4R_80_COCINAS_FONTANERIA_Y_FUGAS.md](../../knowledge/es/guides/R4R_80_COCINAS_FONTANERIA_Y_FUGAS.md) · ordinal 0 · Riansares 4R — cocinas: tuberías rotas, fugas y decisiones previas a la reforma.

### 33 · EN · case-33 — INCONCLUSIVE

**Pregunta:**

> I have broken pipes in my kitchen. What should I do?
> I also want to renovate my kitchen. What do you recommend?
> Can I replace the tiles too?

**Respuesta real:**

> First, address the broken pipes by repairing the leak and ensuring the support is firm and dry before proceeding with any renovations. It is recommended to repair the leak and check the plumbing and drains before installing tiles, fixtures, and finishes. You can replace the tiles as part of the renovation, but this should be done after the plumbing issues are resolved and the support is confirmed to be stable. [S1]

- Resultado: **INCONCLUSIVE**; semántica `MOSTLY_CORRECT_WITH_CITATION_GAP`; relevancia `HIGH`; respaldo de citas `PARTIALLY_SUPPORTED`; idioma `PASS`.
- Revisión: Correctly puts leak repair and plumbing/drain checks before optional tiling/renovation, supported by cited R4R_80 ordinal3. However, firm/dry/stable substrate claims are not stated in that specific cited chunk; they occur elsewhere in the corpus, so the single citation does not support every asserted condition.
- Advertencias: Firm/dry/stable substrate condition absent from the exact cited ordinal3.
- HTTP 200; `abstained=False`; razón `answered`; proveedor/modelo `ollama-84fbb955-da72-32ae-b1f8-895120bd9cd8 / qwen3:8b`.
- E2E **4.65 s**; embedding 1.06 s; recuperación con embedding 1.18 s; generación 2.30 s.
- [S1] [es/guides/R4R_80_COCINAS_FONTANERIA_Y_FUGAS.md](../../knowledge/es/guides/R4R_80_COCINAS_FONTANERIA_Y_FUGAS.md) · ordinal 3 · Riansares 4R — cocinas: tuberías rotas, fugas y decisiones previas a la reforma / Pregunta — Tengo tuberías rotas y también quiero reformar la cocina. ¿Qué me recomiendas?.

### 34 · ES · case-34 — PASS

**Pregunta:**

> Tengo una piscina que pierde agua y algunas piezas del revestimiento se desprenden. ¿Basta con volver a pegarlas?

**Respuesta real:**

> No basta con volver a pegar las piezas desprendidas del revestimiento. Es necesario identificar la causa de la pérdida de agua y valorar la compatibilidad de materiales y sistemas. [S1]

- Resultado: **PASS**; semántica `CORRECT`; relevancia `HIGH`; respaldo de citas `SUPPORTED`; idioma `PASS`.
- Revisión: Responde no y exige diagnosticar pérdida de agua y compatibilidad antes de una reparación superficial. R4R_89 ordinal1 sustenta esa exigencia; no afirma causa concreta ni atribuye el servicio a la empresa.
- HTTP 200; `abstained=False`; razón `answered`; proveedor/modelo `ollama-84fbb955-da72-32ae-b1f8-895120bd9cd8 / qwen3:8b`.
- E2E **2.65 s**; embedding 1.06 s; recuperación con embedding 1.15 s; generación 1.50 s.
- [S1] [es/guides/R4R_89_OTROS_ESPACIOS_Y_OBRAS_ESPECIALES.md](../../knowledge/es/guides/R4R_89_OTROS_ESPACIOS_Y_OBRAS_ESPECIALES.md) · ordinal 1 · Riansares 4R — otras obras: piscinas, naves, terrazas y equipamientos / ¿Se puede reparar una piscina que pierde agua?.

### 35 · EN · case-35 — FAIL

**Pregunta:**

> I want to renovate changing rooms in a sports centre that remains open. What should I check to carry out the work in phases?

**Respuesta real:**

> [Respuesta vacía; abstención pública]

- Resultado: **FAIL**; semántica `UNNECESSARY_VALIDATION_ABSTENTION`; relevancia `INCONCLUSIVE`; respaldo de citas `MISSING_CITATIONS`; idioma `NOT_APPLICABLE`.
- Revisión: The corpus documents phased public changing-room work and checks (R4R_89/R4R_113), yet the request ended in missing_citations and an empty public answer. This is not a justified absence of knowledge; discarded generation/context were not exposed for claim-level review.
- Advertencias: Answer suppressed for missing_citations on a documented question; Full discarded generation/context unavailable.
- HTTP 200; `abstained=True`; razón `missing_citations`; proveedor/modelo `ollama-84fbb955-da72-32ae-b1f8-895120bd9cd8 / qwen3:8b`.
- E2E **4.13 s**; embedding 1.05 s; recuperación con embedding 1.16 s; generación 2.05 s.
- Citas: ninguna.

## Comparación con el histórico: 30 observaciones pertinentes

Se comparan los números 1–30 en el **idioma seleccionado**, es decir, 30 de las
60 observaciones históricas, no una nueva batería de 60. La unión es por `caseId`
y `initialNumber`; no por posición, ya que algunos casos fueron reordenados.
Las preguntas abreviadas antes de esta intervención se señalan como texto distinto.
También cambiaron corpus (282 → 1.401 vectores) y configuración respecto a parte
del histórico. Son diferencias descriptivas, no un experimento causal ni una
estimación estadística de mejora atribuible a una sola variable.

| Nº | Idioma | ID | Categoría histórica | Actual | Revisión actual | ¿Mismo texto histórico? |
|---:|---|---|---|---|---|---|
| 1 | EN | `case-01` | CORRECTA | PASS | CORRECT | Sí |
| 2 | ES | `case-02` | CORRECTA | PASS | CORRECT | Sí |
| 3 | EN | `case-03` | CORRECTA | PASS | CORRECT | Sí |
| 4 | ES | `case-04` | PARCIAL | PASS | CORRECT_WITH_CAVEAT | Sí |
| 5 | EN | `case-05` | CORRECTA | PASS | CORRECT | Sí |
| 6 | ES | `case-06` | CORRECTA | PASS | CORRECT | Sí |
| 7 | EN | `case-07` | CORRECTA | FAIL | UNNECESSARY_ABSTENTION | Sí |
| 8 | ES | `case-08` | CORRECTA | PASS | CORRECT | Sí |
| 9 | EN | `case-09` | CORRECTA | FAIL | MOSTLY_CORRECT_WITH_UNSUPPORTED_DETAIL | Sí |
| 10 | ES | `case-10` | CORRECTA | PASS | CORRECT | Sí |
| 11 | EN | `case-11` | CORRECTA | PASS | CORRECT | Sí |
| 12 | ES | `case-12` | CORRECTA | PASS | CORRECT | Sí |
| 13 | EN | `case-13` | CORRECTA | PASS | CORRECT_WITH_LANGUAGE_CAVEAT | Sí |
| 14 | ES | `case-14` | CORRECTA | PASS | CORRECT | Sí |
| 15 | EN | `case-15` | CORRECTA | PASS | CORRECT | Sí |
| 16 | ES | `case-16` | INCORRECTA/SIN_RESPALDO | FAIL | OFF_TOPIC_AND_UNSUPPORTED | Sí |
| 17 | EN | `case-17` | PARCIAL | INCONCLUSIVE | PARTIAL_SUPPORTED | No |
| 18 | ES | `case-19` | INCORRECTA/SIN_RESPALDO | PASS | CORRECT | No |
| 19 | EN | `case-22` | GENÉRICA | INCONCLUSIVE | PARTIAL_SUPPORTED | Sí |
| 20 | ES | `case-18` | ABSTENCIÓN_JUSTIFICADA | PASS | SAFE_LIMITATION | Sí |
| 21 | EN | `case-20` | ABSTENCIÓN_JUSTIFICADA | FAIL | UNSUPPORTED_COMPARISON | Sí |
| 22 | ES | `case-21` | GENÉRICA | FAIL | INCORRECT_OFF_TOPIC | No |
| 23 | EN | `case-23` | ABSTENCIÓN_JUSTIFICADA | PASS | CORRECT_LIMITED_GUIDANCE | No |
| 24 | ES | `case-24` | INCORRECTA/SIN_RESPALDO | FAIL | UNSUPPORTED_FEASIBILITY | Sí |
| 25 | EN | `case-25` | INCORRECTA/SIN_RESPALDO | INCONCLUSIVE | PARTIAL_SUPPORTED | No |
| 26 | ES | `case-26` | ABSTENCIÓN_JUSTIFICADA | PASS | CORRECT_ABSTENTION | Sí |
| 27 | EN | `case-27` | ABSTENCIÓN_JUSTIFICADA | PASS | JUSTIFIED_ABSTENTION | Sí |
| 28 | ES | `case-28` | ABSTENCIÓN_JUSTIFICADA | PASS | CORRECT_ABSTENTION | Sí |
| 29 | EN | `case-29` | ABSTENCIÓN_JUSTIFICADA | PASS | JUSTIFIED_ABSTENTION | Sí |
| 30 | ES | `case-30` | ABSTENCIÓN_JUSTIFICADA | PASS | CORRECT_ABSTENTION | Sí |

### Problemas y cambios observados

La revisión encuentra cambios favorables y regresiones concretos, sin atribuir causalidad a una única variable. ES18 pasa de una respuesta no respaldada en el histórico a una inspección de terraza pertinente, aunque la pregunta vigente es más corta. EN7 retrocede de correcta a abstención innecesaria, y EN9 incorpora flooding sin respaldo. ES16,22,24 conservan problemas de pertinencia o soporte documental. Los cinco casos nuevos se evalúan aparte en la tabla de métricas; no tienen baseline histórico.

- **04 ES — PASS**: Rangos y condicionantes reproducen FAQ-030 dentro del encabezado expandido Casas antiguas. No inventa cifras; debería identificar expresamente la fecha/origen de la referencia para evitar leerla como tarifa actual.
- **07 EN — FAIL**: The corpus explicitly covers ready-mix concrete with good external access (R4R_10 §6.4), but this request ended in model_abstention. Returned SSE does not expose the full discarded context, so relevance of those retrieved chunks cannot be confirmed.
- **09 EN — FAIL**: Most listed damp causes match cited R4R_00 §4.3, but the answer adds flooding as an explicitly documented cause; the citation lists water ingress/plumbing leakage, not flooding. This is a translation/support error rather than wholly irrelevant retrieval.
- **13 EN — PASS**: Correctly confirms houses/offices and reproduces the documented service list. "Prudent redistributions" is awkward English for carefully assessed layout changes, but the factual content is supported.
- **16 ES — FAIL**: La pregunta pide evaluar gravedad de grietas antes de comprar. Responde con titularidad/responsabilidad por filtraciones de cubierta: S1 R4R_110 ordinal1 es irrelevante. La segunda frase enumera retracción/asiento/daño de agua/problema estructural sin apoyo en S1 y omite una evaluación competente de las grietas.
- **17 EN — INCONCLUSIVE**: Tile replacement is a documented optional refresh and drying/repair conditions are supported. However, the cited section does not establish doing it without dismantling sound cabinets or assess access; this central preservation condition is asserted rather than explained.
- **19 EN — INCONCLUSIVE**: Services and testing before finishes are sensible and documented, but the answer applies a bathroom-specific sequence (slopes, sanitary fixtures) to a whole-flat renovation without identifying that limit. It is partial guidance, not a supported complete flat-wide sequence.
- **20 ES — PASS**: Define aislamiento acústico y declara explícitamente que la evidencia no demuestra viabilidad sin perder altura. No promete una solución ni espesor. Aunque abstained=false y la etiqueta histórica espera insuficiencia, la respuesta parcial con límite documental es honesta y correcta.
- **21 EN — FAIL**: The cited sections discuss airtightness, ventilation and condensation, not double-versus-triple glazing performance or whether the upgrade is worthwhile. The answer nevertheless advances a glazing-choice conclusion and presents condensation factors as the decisive comparison.
- **22 ES — FAIL**: Confunde ganar luz con ventilación de un extractor y aprovechar espacio con logística de carretillas al hacer una solera. S3 y S4 existen, pero ninguna evidencia apoya que esas acciones resuelvan iluminación o aprovechamiento espacial.
- **24 ES — FAIL**: Afirma que se puede solucionar desequilibrio de climatización sin cambiar todo el sistema a partir de una definición de puentes térmicos. S3 no diagnostica esa oficina ni prueba esa viabilidad; la advertencia final de análisis profesional no corrige la afirmación inicial categórica.
- **25 EN — INCONCLUSIVE**: The hospitality checklist is directly supported and does not promise conversion feasibility. It is useful preliminary guidance, but does not identify the actual building-work scope or clearly state the need for project-specific regulations/specialist assessment; its blanket wording about any hospitality space is broader than the source.
- **27 EN — PASS**: Correctly abstains from prescribing exact charger cable cross-section/breaker sizing that the corpus does not establish; no fabricated electrical design or citations.
- **29 EN — PASS**: Correct blank-answer abstention for Jupiter mass, outside the renovation corpus, with no fabricated citations.
- **33 EN — INCONCLUSIVE**: Correctly puts leak repair and plumbing/drain checks before optional tiling/renovation, supported by cited R4R_80 ordinal3. However, firm/dry/stable substrate claims are not stated in that specific cited chunk; they occur elsewhere in the corpus, so the single citation does not support every asserted condition.
- **35 EN — FAIL**: The corpus documents phased public changing-room work and checks (R4R_89/R4R_113), yet the request ended in missing_citations and an empty public answer. This is not a justified absence of knowledge; discarded generation/context were not exposed for claim-level review.

## Pruebas automatizadas y artefactos

- Fixtures: 35 ES + 35 EN; primeros 30 objetos y todos los históricos intactos;
  saltos de línea conservados en acumulativas 32/33. Loader Python exige 35;
  regresión de plazos separada como número 36.
- Python de fixtures: 2 pruebas PASS. Fixtures Java focalizadas: 5 PASS.
- `mvn verify`: 434 unitarios PASS; Failsafe 113 casos, 0 fallos/errores y 27
  omitidos por perfiles opt-in desactivados.
- `mvn -f lambda-s3-vectors/pom.xml verify`: 9 pruebas PASS, sin despliegue.
- Los primeros intentos aislados fallaron por auto-attach Mockito; la ejecución
  fuera del aislamiento pasó sin modificar el código.
- La captura real utiliza HTTP SSE y los componentes de producción. No se lanzan
  además los IT live que repetirían las mismas consultas; sus pruebas offline no
  se presentan como éxitos semánticos del LLM.
- Artefactos temporales fuera de Git: `/tmp/r4r-s3-evaluation-artifacts/`:
  `benchmark-results.json`, logs, `es-review.json`, `en-review.json`, fingerprints
  PG y export/backup S3. Ejecutor: `/tmp/r4r-post-bilingual-benchmark.py`.
  La limpieza de `/tmp` puede eliminarlos; el registro esencial queda en este MD.

## Consultas adicionales anteriores a la reducción de alcance

Estas siete capturas ES impares **no cuentan entre los 35 resultados principales**.
Se conservan para no ocultar trabajo ya ejecutado; no se sustituyeron por resultados
más favorables. La petición ES14 interrumpida quedó sin respuesta final y fuera de
las métricas; su ejecución completa posterior sí está incluida arriba.

### Extra ES 01

Pregunta: Si quiero una orientación inicial sobre mi reforma, ¿qué detalles debería facilitar?

> Para una orientación inicial sobre tu reforma, deberías facilitar plano o medidas, actividad y horarios, aforo previsto si aplica, instalaciones, accesos, usuarios con necesidades de accesibilidad, urgencia y trabajos que deben mantenerse operativos. Esto se basa en la información proporcionada en [S3].

E2E 3.98 s; citas: es/guides/R4R_118_PREGUNTAS_INICIALES_Y_SEGUIMIENTO_DE_OBRA.md:4.

### Extra ES 03

Pregunta: ¿Por qué el acceso cambia el precio de una solera?

> El acceso cambia el precio de una solera porque la mezcla y transporte manual aumentan jornadas, coordinación y riesgo frente a descarga directa o bombeo. [S1]

E2E 2.54 s; citas: es/faqs/R4R_40_ALL_FAQ.md:57.

### Extra ES 05

Pregunta: ¿Una primera orientación económica sustituye una visita técnica?

> No, una primera orientación económica no sustituye una visita técnica. La primera orientación es una explicación inicial basada en documentos disponibles, pero no sustituye la visita a obra, medición, proyecto, informe de un técnico competente ni presupuestos con alcance confirmado [S1].

E2E 2.99 s; citas: es/guides/R4R_114_GUIA_DE_RESPUESTAS_PRUDENTES_Y_LIMITES_DEL_ASESORAMIENTO.md:1.

### Extra ES 07

Pregunta: ¿Cuándo conviene usar hormigón de camión en una solera?

> Cuando se ejecuta una solera en un patio exterior con acceso, conviene usar hormigón de camión. Esto se debe a que ofrece ventajas como descarga cercana, ejecución rápida, mezcla homogénea, menos manipulación, menor riesgo de que el hormigón fragüe antes de extenderlo, y menos jornadas de carretilla y hormigonera. [S2]

E2E 3.68 s; citas: es/R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md:5.

### Extra ES 09

Pregunta: ¿Qué causas de humedad pueden aparecer en una vivienda antigua?

> Las causas de humedad que pueden aparecer en una vivienda antigua incluyen capilaridad, humedad lateral, filtración, fuga, condensación, sales acumuladas, cubiertas o canalizaciones, y tratamientos anteriores incompatibles. [S4]

E2E 3.39 s; citas: es/R4R_00_IDENTIDAD_CRITERIOS_Y_VOZ_EDITORIAL.md:18.

### Extra ES 11

Pregunta: ¿Qué datos debe aportar el cliente para solicitar un presupuesto?

> El cliente debe aportar ubicación, fotografías, medidas aproximadas, uso del espacio, problema, resultado esperado y plazo. Esta información es necesaria para una primera orientación según [S3].

E2E 2.70 s; citas: es/faqs/R4R_40_ALL_FAQ.md:2.

### Extra ES 13

Pregunta: ¿Qué tipos de reformas realiza esta empresa? ¿Trabaja en viviendas y oficinas?

> La empresa realiza reformas de baños, cocinas, viviendas, pequeñas oficinas y locales, así como redistribuciones prudentes, mejoras de habitabilidad y renovación parcial o integral de acabados e instalaciones. [S1]
> Trabaja en viviendas y oficinas, según se menciona en la lista de tipos de reformas. [S1]

E2E 3.59 s; citas: es/R4R_00_IDENTIDAD_CRITERIOS_Y_VOZ_EDITORIAL.md:5.

## S3 Vectors: pendiente secundario, separado del benchmark pgvector

- Confirmados cuenta `124021120482`, perfil `r4r-codex`, identidad
  `r4r-codex-deployer`, región `eu-north-1`, bucket `amzn-r4r-vectors` e índice
  `amzn-r4r-vector-index`: float32, 768 dimensiones, coseno y `source_text` no filtrable.
- Exportados los 1.401 vectores existentes del NAS, sin regenerar embeddings,
  y respaldados los 282 registros antiguos AWS con sus datos y metadatos.
- Payload validado con `S3VectorMetadataMapper` de producción para los 1.401
  registros. Se ampliaron 172 contextos de encabezado conforme al lector PG,
  conservando embeddings y marcando `pgvector-heading-v1`. El serializador omite
  listas vacías/null; en este export no hubo campos vacíos que omitir.
- `PutVectors` fue denegado antes de cualquier borrado, al probar una escritura
  idéntica de un registro antiguo. `DeleteVectors` no se intentó. Una lectura
  posterior confirmó igualdad completa de los 282 registros respecto al backup.
- No hay importación de 1.401 vectores, QueryVectors sobre el índice actualizado
  ni comprobación live de Lambda que se puedan declarar realizadas. No se
  modificaron bucket, índice, Lambda, IAM o Bedrock/Titan.
- Para completarlo hace falta una identidad autorizada para PutVectors y
  DeleteVectors sobre ese índice. El ejecutor temporal de sincronización es
  `/tmp/r4r-s3-sync.py`, con fases `prepare`, `replace` y `verify`. `replace` comprueba
  el backup vigente, elimina lotes de 100 claves e importa lotes de 50; `verify`
  exige claves, embeddings float32 bit a bit, metadatos y self-query coherentes.

SHA-256 export NAS: `dcd2b6dc342951c030c58b840687d9ae05594ede8cba03f2fc13b109a09611aa`.

SHA-256 payload S3 preparado: `f0d7df8f6dd8e4b29036f47ebd6defd24f305b5e89ac53ae6b926c752d938373`.

## Intervención focalizada posterior — cierre solicitado por el usuario

Se diagnosticaron los 11 números afectados: 07,09,16,17,19,21,22,24,25,33,35.
La repetición prevista era ES/EN (22 consultas), pero se detuvo por petición del
usuario para ahorrar tokens; **no se declara una batería completa ni los 11 problemas resueltos**.

Hallazgos comprobados: EN07 traducía ready-mix como hormigón armado; EN21 perdía
triple y comparaba doble/doble. En ES16 había evidencia de grietas entre los 40
candidatos, pero el ranking elegía fuentes sobre cubierta, sales y baños. ES22
no tenía evidencia de diseño de iluminación: aparecían transporte y extractores.
EN09 añadía flooding a una lista que no lo documentaba. EN35 tenía contexto
deportivo pertinente y fallaba por missing_citations. Los parciales mezclaban
procedimientos de baños con vivienda completa o hechos de distintas fuentes.

Cambios: glosario fiel en traducción, conservación de comparaciones, palabras
funcionales excluidas de BM25, ámbito de fuente explícito en el prompt y reglas
de soporte por afirmación. TopK5, minScore0.63, modelos y pesos no se cambian.
Se añadió un filtro de pertinencia con el mismo modelo, **opcional y desactivado
por defecto** (`r4r.rag.evidence-relevance.enabled=true` para validarlo). Añade una
inferencia; su evaluación viva es insuficiente y no se activa de forma general.
La respuesta JSON inválida del filtro es un error, no una abstención legítima.

Maven: la versión con filtro pasó 440 unitarios y Failsafe113, con 27 omitidos y
ningún fallo. Los dobles HTTP se adaptaron a la llamada de clasificación sin
relajar sus aserciones. Se verifica de nuevo la configuración opt-in al cerrar.
No hay despliegue, commit, push, reindexado ni escrituras PostgreSQL.

La primera pasada sin filtro y las dos respuestas de prueba con filtro se
conservan debajo; no se ocultan los errores de conexión ni resultados deficientes.
Los artefactos detallados están en `/tmp/r4r-targeted-artifacts/`, incluido el
diagnóstico de traducciones, candidatos con scores y contexto.

### Primera pasada: prompt/ranking/traducción


**07 ES** — CAPTURED

Pregunta: ¿Cuándo conviene usar hormigón de camión en una solera?

Cuando se ejecuta una solera en un patio exterior con acceso, conviene usar hormigón de camión debido a las ventajas como descarga cercana, ejecución rápida, mezcla homogénea y menor riesgo de que el hormigón fragüe antes de extenderlo. [S3]

Citas: es/R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md:6

**09 ES** — CAPTURED

Pregunta: ¿Qué causas de humedad pueden aparecer en una vivienda antigua?

Las causas de humedad que pueden aparecer en una vivienda antigua incluyen fugas sin detectar, humedad lateral, filtración, condensación, sales acumuladas, cubiertas o canalizaciones, y tratamientos anteriores incompatibles [S1]. Además, la capilaridad y la humedad en muros gruesos también pueden contribuir a la presencia de humedad [S4].

Citas: es/guides/R4R_83_VIVIENDAS_ANTIGUAS_Y_REHABILITACION.md:4, es/R4R_00_IDENTIDAD_CRITERIOS_Y_VOZ_EDITORIAL.md:18

**16 ES** — CAPTURED

Pregunta: Estoy pensando en comprar un piso bastante antiguo, pero tiene grietas en algunas paredes. ¿Cómo puedo saber si es algo superficial o si me voy a meter en un problema serio?

Para determinar si las grietas en las paredes de un piso antiguo son superficiales o indican un problema serio, debes documentar las grietas, comprobar posibles movimientos y entradas de agua, y derivar la evaluación estructural cuando proceda. No prometer impermeabilización definitiva por pintar o revestir. [S5]

Citas: es/guides/R4R_83_VIVIENDAS_ANTIGUAS_Y_REHABILITACION.md:6

**17 ES** — CAPTURED

Pregunta: Los muebles de la cocina están bien. ¿Cómo la renuevo sin desmontarlo todo?

Para renovar la cocina sin desmontar los muebles, se debe adaptar la iluminación y los enchufes, y conservar las posiciones de agua y desagüe si son viables. [S1]
Sin embargo, es necesario primero valorar fugas y redes ocultas, y definir la distribución antes de coordinar muebles y acabados. [S3]

Citas: es/R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md:17, es/guides/R4R_122_FORMULACIONES_REALES_DEL_VISITANTE.md:4

**19 ES** — CAPTURED

Pregunta: Acabamos de comprar un piso y queremos reformarlo antes de mudarnos. ¿En qué orden conviene hacer los trabajos para evitar tener que romper cosas que ya están terminadas?

Para evitar tener que romper cosas que ya están terminadas, se debe revisar primero la distribución, instalaciones, pendientes, ventilación y estado de soportes del baño, y luego planificar las demoliciones, fontanería, desagües, impermeabilización, pruebas, alicatado, sanitarios y remates. El orden se adapta a cada obra. [S2]

Citas: es/guides/R4R_81_BANOS_DUCHAS_Y_SANEAMIENTO.md:1

**21 ES** — CAPTURED

Pregunta: Quiero cambiar las ventanas de mi casa, pero no sé si realmente merece la pena instalar triple acristalamiento o si con doble es suficiente.

El cambio de ventanas puede afectar el equilibrio entre infiltración de aire y ventilación, lo cual puede influir en la humedad interior. No se puede concluir automáticamente que las ventanas nuevas sean defectuosas; deben analizarse puentes térmicos, temperatura y ventilación. [S2]

Citas: es/guides/R4R_87_AISLAMIENTO_VENTILACION_Y_REHABILITACION_ENERGETICA.md:2

**22 ES** — CAPTURED

Pregunta: Tengo un pasillo largo y oscuro. ¿Cómo gano luz y aprovecho el espacio?

Para ganar luz en un pasillo largo y oscuro, se debe considerar la ventilación adecuada a uso y normativa aplicables, ya que no confundir el encendido con el cumplimiento de caudal. [S2]
Para aprovechar el espacio, se debe revisar el uso, fricción, impactos, planeidad, humedad del soporte, mantenimiento, seguridad y normativa deportiva aplicable, aunque esta información se refiere a un polideportivo. [S4]
Sin embargo, no se proporcionan detalles específicos sobre cómo mejorar la iluminación o aprovechar el espacio en un pasillo largo y oscuro.

Citas: es/guides/R4R_104_DUCHAS_PENDIENTES_IMPERMEABILIZACION_Y_BANOS.md:5, es/guides/R4R_113_OTROS_ESPACIOS_GARAJES_PISCINAS_POLIDEPORTIVOS.md:0

**24 ES** — CAPTURED

Pregunta: Tenemos una oficina donde hace mucho calor en unas zonas y frío en otras. ¿Se puede solucionar con una reforma de la climatización sin cambiar todo el sistema?

La calificación energética puede mejorar con una reforma si se reducen la demanda y/o consumo, según la metodología de certificación aplicable. [S2]
Sin embargo, la evidencia no aborda directamente la solución de problemas de climatización en una oficina, por lo que no se puede afirmar con base en los documentos proporcionados si una reforma parcial sería suficiente.

Citas: es/guides/R4R_111_AISLAMIENTO_VENTANAS_Y_REHABILITACION_ENERGETICA.md:7

**25 ES** — CAPTURED

Pregunta: Quiero pasar de tienda a cafetería. ¿Qué obras necesito para abrir?

Para pasar de tienda a cafetería y abrir, es necesario estudiar la estabilidad, instalaciones, evacuación, licencia y accesibilidad antes de realizar cualquier obra [S1]. Además, se debe considerar la distribución, materiales deseados, acceso, uso actual y plazos para pedir un presupuesto de reforma [S2].

Citas: es/cases/R4R_91_CASOS_HIPOTETICOS_VARIADOS.md:10, es/guides/R4R_86_PRESUPUESTOS_MEDICIONES_Y_CAMBIOS.md:1

**33 ES** — ERROR

Pregunta: Tengo tuberías rotas en mi cocina. ¿Qué hago?
Además, quiero reformar la cocina. ¿Qué me recomiendas?
¿Puedo cambiar también los azulejos?

Sin respuesta final / error o cancelación

Citas:

**35 ES** — ERROR

Pregunta: Quiero reformar los vestuarios de un polideportivo abierto al público. ¿Qué debo revisar para ejecutar la obra por fases?

Sin respuesta final / error o cancelación

Citas:

**07 EN** — CAPTURED

Pregunta: When is it advisable to use ready-mix concrete for a slab?

It is advisable to use ready-mix concrete for a slab when the work site has direct access, as this allows for quick execution, homogeneous mixing, less handling, and a reduced risk of the concrete setting before spreading. [S2]

Citas: es/R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md:6

**09 EN** — CAPTURED

Pregunta: What causes of damp can occur in an old home?

Water ingress in an old home can be caused by several factors, including capillary action, lateral humidity, leakage, and condensation. These causes are explicitly documented in the evidence and are part of the differential diagnosis for dampness in older buildings. [S4]

Citas: es/R4R_00_IDENTIDAD_CRITERIOS_Y_VOZ_EDITORIAL.md:18

**16 EN** — CAPTURED

Pregunta: I'm thinking of buying a fairly old flat, but there are cracks in some walls. How can I tell whether they are superficial or whether I'm getting into a serious problem?

If you notice cracks in walls, you should stop work in the affected area, secure the environment, and document the evolution of the cracks. This is necessary to determine whether the cracks are superficial or indicate a serious structural issue. [S1]

Citas: es/guides/R4R_88_SEGURIDAD_INSTALACIONES_PERMISOS_Y_MATERIALES.md:7

**17 EN** — CAPTURED

Pregunta: The kitchen cabinets are fine. How can I refresh it without taking everything apart?

To refresh the kitchen without taking everything apart, you can focus on updating finishes and fixtures while preserving the cabinets. Consider replacing the countertops, updating the hardware, and installing new lighting. You may also replace the tiles if the underlying structure is sound, as this can be done as part of a renovation without full demolition. [S4]

Citas: es/guides/R4R_80_COCINAS_FONTANERIA_Y_FUGAS.md:4

### Prueba del filtro: interrumpida por el usuario


**07 ES** — CAPTURED

Pregunta: ¿Cuándo conviene usar hormigón de camión en una solera?

La evidencia indica que conviene usar hormigón de camión en una solera cuando se trata de un **patio exterior con acceso**, debido a las ventajas como descarga cercana, ejecución rápida, mezcla homogénea y menor riesgo de fraguado prematuro. [S1]

Citas: es/R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md:5

**09 ES** — CAPTURED

Pregunta: ¿Qué causas de humedad pueden aparecer en una vivienda antigua?

La evidencia documenta que pueden existir fugas sin detectar, humedad, instalaciones obsoletas, condensación, soportes disgregados, plagas y ventilación inadecuada como riesgos en una vivienda antigua cerrada durante años [S1]. No se establece explícitamente qué causas de humedad pueden aparecer, aunque se mencionan síntomas relacionados con la humedad, como su relación con lluvia o uso de agua, ubicación, estacionalidad, daños vecinos, reformas previas y ventilación [S2].

Citas: es/guides/R4R_83_VIVIENDAS_ANTIGUAS_Y_REHABILITACION.md:4, es/guides/R4R_118_PREGUNTAS_INICIALES_Y_SEGUIMIENTO_DE_OBRA.md:3
