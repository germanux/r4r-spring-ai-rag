# Validación final con F — 30/30 PASSED

F: **embeddings portátil ASUS · nomic-embed-text:latest · 768 dimensiones; generación PC · qwen3:8b**.

Se conservan 26 resultados reales sin cambios de la pasada completa y se sustituyen únicamente los casos 6 y 10 ES/EN por cuatro consultas reales con las preguntas corregidas. No se repitieron A–F ni se cambió código de producción durante este cierre. Los asserts son los originales de los IT y siguen exigiendo respuestas con citas en 01–12 y abstención en 13–15.

- Resultado principal: **30 PASSED / 0 FAILED / 0 SKIPPED / 0 NOT RUN**.
- Tiempo medido acumulado de las dos ejecuciones de cierre: **800.86 s**, incluye arranques y pausas de cuota; también las cuatro preguntas anteriores que se sustituyeron.
- Media backend de los 30 resultados definitivos: **2753.36 ms**. Media E2E SSE: **2755.69 ms**.
- Top-K 5; score 0,63; traducción 30 s; think=false. OpenAI y precarga de arranque deshabilitados. Una única petición SSE por resultado; no se duplicó REST para medirlo.

## Reformulaciones autorizadas

- 06: «¿Qué debe revisarse antes de pavimentar un patio?» / «What should be checked before paving a patio?». Se concreta el ámbito documentado por FAQ-046.
- 10: «¿Debe incluir el presupuesto de una reforma los plazos de ejecución?» / «Should a renovation estimate include the execution timeline?». FAQ-003 y el método de presupuestación documentan ese requisito; no se pide una causa no acreditada.
- 11/12 y las abstenciones 13–15 se conservan. La pregunta causal original de plazos queda como `originalTimelineReasonRegression` en cada IT, optativa mediante `RAG_TIMELINE_REGRESSION=true`, fuera de sus 15 casos principales. Admite abstención o respuesta parcial con citas; no exige explicar efectos inexistentes en el contexto. No se ha vuelto a consultar para ejecutar ese método adicional: sus respuestas anteriores se conservan abajo.

## Resultados individuales

| Caso | Pregunta definitiva | IT | Embedding ms | Recuperación incl. embedding ms | Generación ms | Backend ms | E2E SSE ms | Citas |
| --- | --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |
| ES 01 | Si quiero una orientación inicial sobre mi reforma, ¿qué detalles debería facilitar? | PASSED | 175.76 | 281.49 | 2443.25 | 2726.96 | 2738.89 | 1 |
| ES 02 | En una obra, ¿en qué se distinguen la base de solera y el acabado de solado? | PASSED | 257.11 | 406.65 | 2512.68 | 2920.14 | 2922.20 | 2 |
| ES 03 | ¿Por qué el acceso cambia el precio de una solera? | PASSED | 94.86 | 125.86 | 2021.85 | 2148.66 | 2151.01 | 1 |
| ES 04 | ¿Cuánto orienta una reforma integral de una casa antigua? | PASSED | 224.20 | 273.45 | 2678.21 | 2952.70 | 2954.52 | 1 |
| ES 05 | ¿Una primera orientación económica sustituye una visita técnica? | PASSED | 88.05 | 131.36 | 1743.76 | 1875.50 | 1877.46 | 1 |
| ES 06 | ¿Qué debe revisarse antes de pavimentar un patio? | PASSED | 174.25 | 249.27 | 2826.91 | 3079.19 | 3092.01 | 1 |
| ES 07 | ¿Cuándo conviene usar hormigón de camión en una solera? | PASSED | 128.07 | 161.32 | 2376.23 | 2537.98 | 2539.79 | 1 |
| ES 08 | ¿Cómo afecta un pasillo estrecho a la ejecución de una solera? | PASSED | 187.84 | 226.08 | 2467.32 | 2693.76 | 2695.69 | 1 |
| ES 09 | ¿Qué causas de humedad pueden aparecer en una vivienda antigua? | PASSED | 145.88 | 207.30 | 3163.94 | 3371.61 | 3373.61 | 1 |
| ES 10 | ¿Debe incluir el presupuesto de una reforma los plazos de ejecución? | PASSED | 122.53 | 190.45 | 2380.11 | 2571.34 | 2573.75 | 1 |
| ES 11 | ¿Qué datos debe aportar el cliente para solicitar un presupuesto? | PASSED | 278.05 | 329.97 | 2031.17 | 2361.50 | 2362.95 | 1 |
| ES 12 | ¿Qué datos hacen falta para orientar una reforma de baño? | PASSED | 196.36 | 238.25 | 2307.14 | 2545.80 | 2547.41 | 1 |
| ES 13 | ¿De qué manera producen alimento las plantas mediante la fotosíntesis? | PASSED | 242.10 | 263.28 | 641.61 | 905.17 | 906.57 | 0 |
| ES 14 | ¿Cuál es la masa de Júpiter en kilogramos? | PASSED | 237.02 | 255.29 | null | 255.55 | 257.04 | 0 |
| ES 15 | ¿Quién escribió la novela Cien años de soledad? | PASSED | 185.36 | 231.91 | 789.74 | 1021.92 | 1023.21 | 0 |
| EN 01 | What kinds of renovation work does this company do? Does it work on houses and offices? | PASSED | 160.87 | 199.36 | 2331.90 | 3480.34 | 3481.74 | 2 |
| EN 02 | In construction, how does the concrete base differ from the finished flooring? | PASSED | 155.85 | 193.34 | 2412.89 | 3570.36 | 3571.69 | 1 |
| EN 03 | Why does access affect the price of a concrete slab? | PASSED | 299.13 | 339.53 | 1685.37 | 2746.73 | 2748.09 | 1 |
| EN 04 | What is the approximate cost of a complete renovation of an old house? | PASSED | 392.71 | 433.38 | 2583.91 | 3925.07 | 3926.60 | 1 |
| EN 05 | Does initial cost guidance replace a technical site visit? | PASSED | 94.20 | 126.32 | 2172.21 | 3074.62 | 3075.70 | 2 |
| EN 06 | What should be checked before paving a patio? | PASSED | 191.67 | 241.79 | 2441.79 | 3436.66 | 3439.16 | 1 |
| EN 07 | When is it advisable to use ready-mix concrete for a slab? | PASSED | 389.55 | 424.46 | 2136.19 | 3518.76 | 3519.86 | 1 |
| EN 08 | How does a narrow corridor affect the construction of a concrete slab? | PASSED | 258.06 | 290.40 | 2131.47 | 3309.18 | 3310.28 | 1 |
| EN 09 | What causes of damp can occur in an old home? | PASSED | 154.31 | 201.85 | 4006.71 | 5206.13 | 5207.44 | 1 |
| EN 10 | Should a renovation estimate include the execution timeline? | PASSED | 139.16 | 193.56 | 2538.55 | 3641.32 | 3643.72 | 1 |
| EN 11 | What information should a customer provide to request an estimate? | PASSED | 200.48 | 251.07 | 2273.08 | 3285.33 | 3286.57 | 1 |
| EN 12 | What information is needed to estimate a bathroom renovation? | PASSED | 275.88 | 311.76 | 3635.05 | 4773.14 | 4774.50 | 2 |
| EN 13 | How do plants make their food through photosynthesis? | PASSED | 266.20 | 303.61 | 849.09 | 1982.47 | 1983.59 | 0 |
| EN 14 | What is the mass of Jupiter in kilograms? | PASSED | 188.19 | 207.94 | null | 993.40 | 994.65 | 0 |
| EN 15 | Who wrote the novel One Hundred Years of Solitude? | PASSED | 91.18 | 124.67 | 800.93 | 1689.66 | 1690.93 | 0 |

`vectorSearchMs=null`: no existe cronómetro aislado de SQL y no se estima por resta. Los tiempos excluyen las consultas de memoria del adaptador.

<details><summary>Respuestas y fuentes definitivas</summary>

### ES 01

Para una primera orientación sobre tu reforma, deberías facilitar ubicación, fotografías, medidas aproximadas, uso del espacio, problema, resultado esperado y plazo. Esta información es necesaria para una orientación inicial, según [S1].
- [S1] `faqs/R4R_40_ALL_FAQ.md` · Riansares 4R — todas las preguntas frecuentes / Presupuesto / FAQ-001 — ¿Qué información hace falta para una primera orientación?

### ES 02

La base de solera se distingue del acabado de solado por su función estructural y preparativa, mientras que el solado es la colocación del pavimento final sobre una base apta. Esta distinción está documentada en [S1] y [S2].
- [S1] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` · Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 6. Caso A — Patio, solera, pavimento y drenaje / 6.3. Distinción entre solera y solado
- [S2] `faqs/R4R_40_ALL_FAQ.md` · Riansares 4R — todas las preguntas frecuentes / Suelos / FAQ-041 — ¿Qué diferencia hay entre solera y solado?

### ES 03

El acceso cambia el precio de una solera porque la mezcla y transporte manual aumentan jornadas, coordinación y riesgo frente a descarga directa o bombeo. [S1]
- [S1] `faqs/R4R_40_ALL_FAQ.md` · Riansares 4R — todas las preguntas frecuentes / Patios / FAQ-045 — ¿Por qué el acceso cambia el precio de una solera?

### ES 04

Una reforma integral de una casa antigua puede oscilar entre 350–450 €/m² sin estructura ni redistribución, y puede elevarse a 533–600 €/m² o más si incluye estructura o cambios. [S1]
- [S1] `faqs/R4R_40_ALL_FAQ.md` · Riansares 4R — todas las preguntas frecuentes / Casas antiguas / FAQ-030 — ¿Cuánto orienta una reforma integral de casa antigua?

### ES 05

La orientación inicial no sustituye visita ni medición, según [S1]. Por lo tanto, una primera orientación económica no sustituye una visita técnica.
- [S1] `faqs/R4R_40_ALL_FAQ.md` · Riansares 4R — todas las preguntas frecuentes / Presupuesto / FAQ-001 — ¿Qué información hace falta para una primera orientación?

### ES 06

Antes de pavimentar un patio, debe revisarse la base, la compactación, el espesor, la armadura, los pendientes, las juntas, las arquetas, las canaletas, los encuentros y el acceso. [S1]
- [S1] `faqs/R4R_40_ALL_FAQ.md` · Riansares 4R — todas las preguntas frecuentes / Patios / FAQ-046 — ¿Qué debe revisarse antes de pavimentar un patio?

### ES 07

Cuando se trata de un patio exterior con acceso, conviene usar hormigón de camión en una solera, ya que ofrece ventajas como descarga cercana, ejecución rápida y menor riesgo de fraguado prematuro. [S4]
- [S4] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` · Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 6. Caso A — Patio, solera, pavimento y drenaje / 6.4. Hormigón de camión y ejecución manual / Patio exterior con acceso

### ES 08

Un pasillo estrecho afecta la ejecución de una solera debido a que el acceso es indirecto y el transporte con carretillas es más difícil, lo cual puede incrementar la complejidad de la obra. [S1]
- [S1] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` · Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 6. Caso A — Patio, solera, pavimento y drenaje / 6.4. Hormigón de camión y ejecución manual / Patio interior o pasillo estrecho

### ES 09

Las causas de humedad que pueden aparecer en una vivienda antigua incluyen humedad ascendente, humedad lateral por terreno o roca, condensación, filtraciones antiguas, fugas y sales. Esto se documenta en [S3].
- [S3] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` · Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 7. Caso B — Vivienda antigua: rehabilitar sin demoler de más

### ES 10

[S2] El presupuesto debe incluir plazos de ejecución, según la base SRC-CM-REFORMAS. La respuesta debe contener plazos como parte del alcance, mediciones, materiales, mano de obra y otros elementos definidos en el presupuesto.
- [S2] `faqs/R4R_40_ALL_FAQ.md` · Riansares 4R — todas las preguntas frecuentes / Presupuesto / FAQ-003 — ¿Qué debe contener un presupuesto?

### ES 11

El cliente debe aportar ubicación, fotografías, medidas aproximadas, uso del espacio, problema, resultado esperado y plazo. Esto se establece en [S5].
- [S5] `faqs/R4R_40_ALL_FAQ.md` · Riansares 4R — todas las preguntas frecuentes / Presupuesto

### ES 12

Para orientar una reforma de baño, se requiere información como ubicación, fotografías, medidas aproximadas, uso del espacio, problema, resultado esperado y plazo. Esta orientación inicial no sustituye una visita ni medición. [S4]
- [S4] `faqs/R4R_40_ALL_FAQ.md` · Riansares 4R — todas las preguntas frecuentes / Presupuesto / FAQ-001 — ¿Qué información hace falta para una primera orientación?

### ES 13

(Abstención: respuesta vacía y sin citas.)

### ES 14

(Abstención: respuesta vacía y sin citas.)

### ES 15

(Abstención: respuesta vacía y sin citas.)

### EN 01

The company works on renovations including houses, offices, and other structures such as bathrooms, kitchens, and small offices. This is supported by [S2] and [S3]. The evidence confirms that the company does work on houses and offices.
- [S2] `R4R_00_IDENTIDAD_CRITERIOS_Y_VOZ_EDITORIAL.md` · Riansares 4R — identidad, criterios y voz editorial / 2. Identidad de Riansares 4R / 2.3. Servicios nucleares / Reformas
- [S3] `R4R_30_METODO_PRESUPUESTOS_SEO_CATALOGO_Y_PLANTILLAS.md` · Riansares 4R — método, presupuestos, SEO, catálogo y plantillas / 21. Web y coherencia editorial / 21.1. Mensaje comprobado en portada

### EN 02

The concrete base, referred to as "solera," is structured to receive the final flooring, with specific preparation, thickness, reinforcement, slopes, and joints. The finished flooring, or "solado," is the placement of the final pavement over a suitable base. [S1]
- [S1] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` · Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 6. Caso A — Patio, solera, pavimento y drenaje / 6.3. Distinción entre solera y solado

### EN 03

The change in access affects the price of a concrete slab because manual mixing and transportation increase working days, coordination, and risk compared to direct discharge or pumping. [S1]
- [S1] `faqs/R4R_40_ALL_FAQ.md` · Riansares 4R — todas las preguntas frecuentes / Patios / FAQ-045 — ¿Por qué el acceso cambia el precio de una solera?

### EN 04

The approximate cost of a complete renovation of an old house ranges from 350–450 €/m² without structure or redistribution, and can go up to 533–600 €/m² or more if there is structural work or changes involved. [S4]
- [S4] `faqs/R4R_40_ALL_FAQ.md` · Riansares 4R — todas las preguntas frecuentes / Casas antiguas / FAQ-030 — ¿Cuánto orienta una reforma integral de casa antigua?

### EN 05

The initial cost guidance does not replace a technical site visit. The orientation initial does not substitute for a visit or measurement [S1]. Additionally, the price orientative must be confirmed with a visit, measurement, location, access, qualities, and taxes [S2].
- [S1] `faqs/R4R_40_ALL_FAQ.md` · Riansares 4R — todas las preguntas frecuentes / Presupuesto / FAQ-001 — ¿Qué información hace falta para una primera orientación?
- [S2] `faqs/R4R_40_ALL_FAQ.md` · Riansares 4R — todas las preguntas frecuentes / Presupuesto / FAQ-006 — ¿Qué significa precio orientativo?

### EN 06

Before paving a patio, the following should be checked: base, compactación, espesor, armadura, pendientes, juntas, arquetas, canaletas, encuentros y acceso. This is documented in [S1].
- [S1] `faqs/R4R_40_ALL_FAQ.md` · Riansares 4R — todas las preguntas frecuentes / Patios / FAQ-046 — ¿Qué debe revisarse antes de pavimentar un patio?

### EN 07

When it is advisable to use ready-mix concrete for a slab is when the access allows for direct unloading, as this enables faster execution, homogeneous mixing, less manipulation, and a lower risk of the concrete setting before placement. [S3]
- [S3] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` · Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 6. Caso A — Patio, solera, pavimento y drenaje / 6.4. Hormigón de camión y ejecución manual / Patio exterior con acceso

### EN 08

A narrow corridor affects the construction of a concrete slab by introducing difficulties such as indirect access, transportation with wheelbarrows, mixing in stages, heat and loss of useful time, and more labor. These challenges are explicitly documented in [S1].
- [S1] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` · Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 6. Caso A — Patio, solera, pavimento y drenaje / 6.4. Hormigón de camión y ejecución manual / Patio interior o pasillo estrecho

### EN 09

Multiple causes of damp can occur in an old home, including capilaridad, humedad lateral, filtración, fuga, condensación, sales acumuladas, cubiertas o canalizaciones, tratamientos anteriores incompatibles [S1]. These causes can coexist and require careful assessment to determine the exact source of dampness.
- [S1] `R4R_00_IDENTIDAD_CRITERIOS_Y_VOZ_EDITORIAL.md` · Riansares 4R — identidad, criterios y voz editorial / 4. Reglas de factualidad para el LLM / 4.3. Humedades

### EN 10

Yes, a renovation estimate should include the execution timeline. The definitive estimate must include the plazo, as stated in [S5]. The preliminary estimate should confirm if the order of magnitude fits, which may indirectly relate to the timeline.
- [S5] `R4R_30_METODO_PRESUPUESTOS_SEO_CATALOGO_Y_PLANTILLAS.md` · Riansares 4R — método, presupuestos, SEO, catálogo y plantillas / 15. Presupuestación con criterio / 15.5. Presupuesto preliminar y definitivo

### EN 11

To request an estimate, a customer should provide location, photographs, approximate measurements, space usage, problem description, expected result, and timeline. This information is necessary for an initial orientation, as stated in [S4].
- [S4] `faqs/R4R_40_ALL_FAQ.md` · Riansares 4R — todas las preguntas frecuentes / Presupuesto

### EN 12

To estimate a bathroom renovation, the information needed includes the **alcance**, **mediciones**, **materiales y calidades**, **mano de obra**, **residuos**, **impuestos**, **plazos**, **pagos**, **exclusiones**, **extras** y **garantía**. This is detailed in [S3]. Additionally, the **alcance real** can vary and may exceed the examples provided, as noted in [S4].
- [S3] `faqs/R4R_40_ALL_FAQ.md` · Riansares 4R — todas las preguntas frecuentes / Presupuesto / FAQ-003 — ¿Qué debe contener un presupuesto?
- [S4] `faqs/R4R_40_ALL_FAQ.md` · Riansares 4R — todas las preguntas frecuentes / Baños / FAQ-031 — ¿Cuánto cuesta reformar un baño?

### EN 13

(Abstención: respuesta vacía y sin citas.)

### EN 14

(Abstención: respuesta vacía y sin citas.)

### EN 15

(Abstención: respuesta vacía y sin citas.)

</details>

## Corpus y seguridad

```text
BENCHMARK_SETTINGS topK=5 minScore=0.63 translationTimeout=30s think=false
BENCHMARK_SAFETY readonly=true flyway=false sqlInit=never schemaInit=false catalog=false openai=false startupWarmup=false
BENCHMARK_CORPUS {"snapshot":{"rows":282,"fingerprint":"58059dbac293f4ce2db72d942552610e"},"vectorType":"vector(768)","readonly":"on"}
BENCHMARK_CORPUS_END {"rows":282,"fingerprint":"58059dbac293f4ce2db72d942552610e"}
BENCHMARK_SETTINGS topK=5 minScore=0.63 translationTimeout=30s think=false
BENCHMARK_SAFETY readonly=true flyway=false sqlInit=never schemaInit=false catalog=false openai=false startupWarmup=false
BENCHMARK_CORPUS {"readonly":"on","vectorType":"vector(768)","snapshot":{"rows":282,"fingerprint":"58059dbac293f4ce2db72d942552610e"}}
BENCHMARK_CORPUS_END {"rows":282,"fingerprint":"58059dbac293f4ce2db72d942552610e"}
```

Las 282 filas y el fingerprint permanecen inalterados. Se verificó solo lectura antes de consultar, con Flyway e inicialización SQL/esquema desactivados. No se leyó ni modificó .env o DEVELOPMENT.md. No se hicieron llamadas de pago, commit, push ni despliegue.

<details><summary>Pasada anterior, conservada sin ocultar fallos</summary>

La pasada con las formulaciones anteriores obtuvo 28/30 según sus asserts. Las preguntas sustituidas y sus salidas reales fueron:

- ES 06: ¿Qué hay que estudiar antes de reformar un patio? — PASSED; 2656.17 ms. Respuesta: Antes de reformar un patio, hay que estudiar la base, la compactación, el espesor, la armadura, los pendientes, las juntas, las arquetas, las canaletas, los encuentros y el acceso. [S1]

- ES 10: ¿Por qué es importante conocer el plazo previsto antes de presupuestar una reforma? — FAILED; 2666.67 ms. Respuesta: (abstención)

- EN 06: What should be assessed before renovating a patio? — FAILED; 2888.47 ms. Respuesta: (abstención)

- EN 10: Why is it important to know the expected timeline before preparing a renovation estimate? — PASSED; 4747.45 ms. Respuesta: The expected timeline is part of the information needed for a preliminary orientation, as it helps define the scope and scope of work. [S5]
However, the evidence does not explicitly state why knowing the timeline is important beyond its role in the preliminary orientation.

</details>
