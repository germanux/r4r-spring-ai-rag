# Diagnóstico de recuperación RAG en inglés

Revisión del 29 de octubre de 2026. Se consultó PostgreSQL de producción en modo
solo lectura y se hicieron seis llamadas a la API existente. No se reindexó, ni
se cambió el modelo, los prompts o el umbral.

## Cómo funciona la recuperación

1. `RagQueryController.answer()` recibe `POST /api/rag/answers` y llama a
   `CitedRagService.answer()`. `RagConfiguration.citedRagService()` le aporta
   `RagRetrievalProperties.topK()` y `minScore()`: en `application.yml` son 5 y
   0,64 respectivamente.
2. `CitedRagService.retrieveEvidence()` llama a
   `PgVectorKnowledgeStore.search(question, topK, minScore)`. Este construye un
   `SearchRequest` con `similarityThreshold(minScore)` y llama a
   `VectorStore.similaritySearch()`; aquí no se añade un filtro de metadatos.
3. En Spring AI 1.0.0, `PgVectorStore.doSimilaritySearch()` llama a
   `getQueryEmbedding()`, que usa `EmbeddingModel.embed(query)`. El
   `OllamaEmbeddingModel` envía el texto a `/api/embed`. PostgreSQL compara el
   vector con los guardados usando `<=>` (distancia coseno).
4. Spring AI filtra en SQL con `distancia < 1 - minScore`, ordena por distancia
   y limita a `topK`. El score devuelto es `1 - distancia`: tiene que ser
   **mayor** que 0,64. `PgVectorKnowledgeStore.fromDocument()` convierte los
   documentos en chunks; no aplica otro umbral.
5. Si la lista está vacía, `CitedRagService.answer()` devuelve
   `RagResult.abstain()` antes de `ChatModel.call()`. Si hay chunks, construye
   el prompt, llama al modelo generativo y prepara las citas. Después de llamar
   al modelo también puede abstenerse si la respuesta es inválida o indica
   abstención.

El modelo de embeddings configurado localmente era `nomic-embed-text`, con 768
componentes. La API desplegada no expone su configuración efectiva; no se pudo
comprobar desde ella qué modelo y umbral tenía en memoria.

## Qué se midió y cómo

[`r4r-vector-diagnostic.py`](../../py-scripts/r4r-vector-diagnostic.py) usa su
función `embed(text)` para hacer un POST a Ollama `/api/embed` con el modelo del
`.env`; mide el tiempo de la petición completa, incluido el transporte. Su
función `query(sql)` llama a `psql` con `default_transaction_read_only=on`.
Para cada pregunta hizo dos SELECT: primero los cinco vecinos sin umbral y
luego el mismo filtro `distancia < 0,36` que aplica Spring AI con `minScore`
0,64. No utilizó el método Java `similaritySearch()` directamente: reprodujo
su SQL para poder observar los resultados anteriores al filtro.

| Consulta | Idioma | Mejor score | Chunks > 0,64 | Embedding | API real |
|---|---|---:|---:|---:|---|
| Baño | ES | 0,684 | 5 | 147 ms | Respuesta, 5 citas |
| Baño | EN | 0,506 | 0 | 154 ms | Abstención |
| Cocina | ES | 0,751 | 5 | 366 ms | Respuesta, 5 citas |
| Cocina | EN | 0,505 | 0 | 119 ms | Abstención |
| Humedades | ES | 0,698 | 5 | 183 ms | Respuesta, 5 citas |
| Humedades | EN | 0,517 | 0 | 131 ms | Abstención |

Las respuestas positivas prueban una llamada al generador. En las abstenciones,
que no se llamó se infiere del código local y del resultado de la búsqueda;
no se observó directamente el interior de la API desplegada. Algunos intentos
recibieron HTTP 429 y se repitieron con
[`r4r-api-retry.py`](../../py-scripts/r4r-api-retry.py), respetando `Retry-After`.

<details>
<summary>Cinco vecinos, IDs y documentos de cada consulta</summary>

## Baño — ES

Pregunta: Quiero reparar mi baño, ¿cuánto me podría costar?

Embedding: 146.87 ms. Umbral: 0,64. El SQL con umbral devuelve 5 de los cinco resultados.

| Posición | ID | Documento de origen | Score | Supera 0,64 |
|---:|---|---|---:|:---:|
| 1 | `a588ec0f-4d94-5e0e-afc7-ec4155e47ea8` | `sources/R4R_70_WEB_SOURCE_MANIFEST.md` | 0.683677 | Sí |
| 2 | `7bdb5282-ef32-5fc1-b6ab-588437e834ec` | `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` | 0.676116 | Sí |
| 3 | `6f0f1730-6786-5165-a4de-a13cd318d1ac` | `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` | 0.671759 | Sí |
| 4 | `35f85efa-f012-52ea-8e14-4bd416d581e6` | `faqs/R4R_40_ALL_FAQ.md` | 0.667006 | Sí |
| 5 | `da4d0959-0d27-5ba9-9f9f-309c74e6d5c9` | `R4R_20_CASOS_OFICINAS_CUEVAS_BANOS_Y_FILTROS.md` | 0.665188 | Sí |

## Baño — EN

Pregunta: I want to repair my bathroom, how much can it cost?

Embedding: 154.25 ms. Umbral: 0,64. El SQL con umbral devuelve 0 de los cinco resultados.

| Posición | ID | Documento de origen | Score | Supera 0,64 |
|---:|---|---|---:|:---:|
| 1 | `136b26da-3ccc-5a50-bab4-5fb7e62b5550` | `faqs/R4R_40_ALL_FAQ.md` | 0.505784 | No |
| 2 | `f6e62142-a621-572e-b8b6-cfbe60503f5a` | `R4R_20_CASOS_OFICINAS_CUEVAS_BANOS_Y_FILTROS.md` | 0.493263 | No |
| 3 | `839c9a0c-e4e5-5c01-b1f2-b25a79be0197` | `faqs/R4R_40_ALL_FAQ.md` | 0.489602 | No |
| 4 | `c8a0fd83-c8a4-56bc-bd66-c2ae01111475` | `faqs/R4R_40_ALL_FAQ.md` | 0.485832 | No |
| 5 | `18043c54-f8a6-58ef-a6e6-ef897bb28f73` | `faqs/R4R_40_ALL_FAQ.md` | 0.481106 | No |

## Cocina — ES

Pregunta: Quiero reparar mi cocina, ¿cuánto me podría costar?

Embedding: 366.35 ms. Umbral: 0,64. El SQL con umbral devuelve 5 de los cinco resultados.

| Posición | ID | Documento de origen | Score | Supera 0,64 |
|---:|---|---|---:|:---:|
| 1 | `65d5b12b-231d-5e29-8c60-8918c05a2efe` | `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` | 0.751319 | Sí |
| 2 | `e17d7c6f-636d-53ba-b684-561dba33f968` | `faqs/R4R_40_ALL_FAQ.md` | 0.715846 | Sí |
| 3 | `2feb2185-5abb-51f5-8271-33a2cc5334df` | `faqs/R4R_40_ALL_FAQ.md` | 0.706915 | Sí |
| 4 | `a9d427d6-6d94-51b3-9508-c0eac2d262b3` | `sources/R4R_70_WEB_SOURCE_MANIFEST.md` | 0.702734 | Sí |
| 5 | `3e133226-d24d-5927-8c7d-9c2889ee8328` | `faqs/R4R_40_ALL_FAQ.md` | 0.688619 | Sí |

## Cocina — EN

Pregunta: I want to repair my kitchen, how much can it cost?

Embedding: 118.64 ms. Umbral: 0,64. El SQL con umbral devuelve 0 de los cinco resultados.

| Posición | ID | Documento de origen | Score | Supera 0,64 |
|---:|---|---|---:|:---:|
| 1 | `839c9a0c-e4e5-5c01-b1f2-b25a79be0197` | `faqs/R4R_40_ALL_FAQ.md` | 0.504605 | No |
| 2 | `05dc6036-01f7-501b-837b-d1e56b97bd8e` | `faqs/R4R_40_ALL_FAQ.md` | 0.503018 | No |
| 3 | `f6e62142-a621-572e-b8b6-cfbe60503f5a` | `R4R_20_CASOS_OFICINAS_CUEVAS_BANOS_Y_FILTROS.md` | 0.498045 | No |
| 4 | `18043c54-f8a6-58ef-a6e6-ef897bb28f73` | `faqs/R4R_40_ALL_FAQ.md` | 0.497160 | No |
| 5 | `136b26da-3ccc-5a50-bab4-5fb7e62b5550` | `faqs/R4R_40_ALL_FAQ.md` | 0.492672 | No |

## Humedades — ES

Pregunta: Tengo un problema de humedades en una cueva, ¿es posible solucionarlo?

Embedding: 182.97 ms. Umbral: 0,64. El SQL con umbral devuelve 5 de los cinco resultados.

| Posición | ID | Documento de origen | Score | Supera 0,64 |
|---:|---|---|---:|:---:|
| 1 | `704d3328-1382-523d-99b2-ee9bae64c87b` | `R4R_00_IDENTIDAD_CRITERIOS_Y_VOZ_EDITORIAL.md` | 0.698318 | Sí |
| 2 | `efbc0eb9-cb65-5da1-bbec-7862ccaf0755` | `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` | 0.685732 | Sí |
| 3 | `827543a6-b257-585d-8b05-7c2439bfee6e` | `faqs/R4R_40_ALL_FAQ.md` | 0.683405 | Sí |
| 4 | `92f2bc0a-15a4-50b4-8816-90ae0c4a94c7` | `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` | 0.672780 | Sí |
| 5 | `b0e5a038-af13-5e05-9762-90f54e8f5726` | `R4R_30_METODO_PRESUPUESTOS_SEO_CATALOGO_Y_PLANTILLAS.md` | 0.670629 | Sí |

## Humedades — EN

Pregunta: I have a damp problem in a cave, is it possible to solve it?

Embedding: 130.57 ms. Umbral: 0,64. El SQL con umbral devuelve 0 de los cinco resultados.

| Posición | ID | Documento de origen | Score | Supera 0,64 |
|---:|---|---|---:|:---:|
| 1 | `462bf25b-81f9-525f-b0a4-d84a6efb72fe` | `faqs/R4R_40_ALL_FAQ.md` | 0.517087 | No |
| 2 | `6d5a3573-c583-55a8-9703-9d7363fdb125` | `R4R_20_CASOS_OFICINAS_CUEVAS_BANOS_Y_FILTROS.md` | 0.505397 | No |
| 3 | `704d3328-1382-523d-99b2-ee9bae64c87b` | `R4R_00_IDENTIDAD_CRITERIOS_Y_VOZ_EDITORIAL.md` | 0.503785 | No |
| 4 | `6332a25e-7cb1-5db2-a657-d76b71773a00` | `R4R_20_CASOS_OFICINAS_CUEVAS_BANOS_Y_FILTROS.md` | 0.495018 | No |
| 5 | `55fecd90-68ef-587d-93b8-9ea0734c30f5` | `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` | 0.480860 | No |

</details>

## Cómo se contrastó el modelo de indexación

El mismo script seleccionó `id` y `content` de tres filas mediante
`SELECT ... FROM vector_store ORDER BY id LIMIT 3`. Para cada texto llamó a
`embed(content)`, es decir, al POST `/api/embed` del modelo configurado; después
comparó el nuevo vector con el guardado **para ese mismo ID** mediante
`SELECT 1 - (embedding <=> vector_nuevo)`. No imprimió los textos.

| ID del vector | Similitud con el embedding recalculado |
|---|---:|
| `015a338d-ba56-5170-8588-6341b8e2ad86` | 0,99999580 |
| `02101a35-5426-5f09-ae88-e5c781f07993` | 0,99999502 |
| `024ed1cd-51c8-5d3f-8d48-cb5d0a807e8f` | 0,99999458 |

Esto es evidencia fuerte de que esos tres se calcularon con el mismo modelo
actual. No demuestra el modelo de todos los registros: el esquema no guarda su
nombre ni su versión. En la indexación real, `PgVectorKnowledgeStore.index()` /
`replacePreparedSource()` llama a `vectorStore.add()`, y
`PgVectorStore.doAdd()` usa `EmbeddingModel.embed()` sobre los documentos.

## Prefijo y conclusiones

[`r4r-prefix-probe.py`](../../py-scripts/r4r-prefix-probe.py) repitió solo los
embeddings de las seis preguntas anteponiendo `search_query: `; no alteró los
documentos del índice. El mejor score inglés subió a 0,542–0,556, aún por debajo
de 0,64. [Nomic recomienda prefijos distintos para consultas y documentos](https://huggingface.co/nomic-ai/nomic-embed-text-v1.5), pero este ensayo no prueba un índice regenerado con el prefijo de documento.

Para estos casos, el descarte por umbral está comprobado. Una menor alineación
entre consultas inglesas y corpus español es una hipótesis plausible; faltan
más ejemplos etiquetados para medir calidad y descartar falsos positivos. Entre
las opciones a evaluar están un umbral menor, prefijos con un índice aislado,
un modelo multilingüe o traducir la consulta antes de recuperar. Ninguna se ha
implementado.

## Repetir el diagnóstico

Desde la raíz y con acceso a Synology y Ollama:

```bash
python3 py-scripts/r4r-vector-diagnostic.py vector
python3 py-scripts/r4r-prefix-probe.py
python3 py-scripts/r4r-vector-diagnostic.py api
```

Los dos primeros envían textos a Ollama y realizan consultas de solo lectura a
la base de producción; el tercero hace llamadas reales y puede invocar el LLM.
Los scripts leen `docker-spring/.env` como líneas `KEY=valor` sin comillas;
requieren `psql` y no deben usarse para sacar conclusiones si la configuración
desplegada difiere. Los tiempos y resultados pueden variar si cambia el índice.
