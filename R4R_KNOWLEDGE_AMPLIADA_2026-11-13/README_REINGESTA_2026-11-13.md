# R4R — ampliación de knowledge y procedimiento seguro de reingesta

**Fecha:** 13/11/2026. **Estado:** contenido preparado y validado estáticamente; **NO ingestado en PostgreSQL ni AWS**. No se ha cambiado código ni secretos.

## Inventario y cambios

- Markdown originales: 10; Markdown actuales: 25.
- Nuevos Markdown: **15**; archivos existentes modificados: **6**; restantes preservados: **14**.
- FAQ: de 52 a **80**, con índice JSON sincronizado.
- Chunks teóricos máximos de 2000 caracteres según un contador independiente que reproduce las reglas de división: **~287 antes → ~497 ahora**. **No son recuentos SQL de embeddings**.
- Tres exportaciones JSON grandes de S3 Vectors **preservadas byte a byte**; están DESACTUALIZADAS respecto al nuevo corpus y no deben reimportarse como si fueran el índice nuevo.
- Nuevas fuentes: CTE, BOE REBT, IDAE (España), EPA y NARI/NAHB (EE. UU.), HSE (Reino Unido). Se distinguen claramente sus jurisdicciones.
- Nuevos escenarios de cocina, fontanería, azulejos, baños, patología de humedades, casas antiguas, patios, oficinas, eficiencia energética, seguridad, locales y piscinas.
- Casos nuevos **rotulados como hipotéticos**; no representan trabajos ejecutados por Riansares 4R.
- Se han creado dos ayudas en inglés **orientadas a retrieval**, no una traducción íntegra de todos los documentos originales. Se conserva `nomic-embed-text` de 768 dimensiones para una primera evaluación; no se ha cambiado ningún modelo.

## Listado de ficheros nuevos

- `knowledge/bilingual/R4R_95_ENGLISH_KITCHEN_AND_PLUMBING_QUESTIONS.md`
- `knowledge/bilingual/R4R_96_ENGLISH_OTHER_RENOVATIONS_FAQ.md`
- `knowledge/cases/R4R_91_CASOS_HIPOTETICOS_VARIADOS.md`
- `knowledge/guides/R4R_80_COCINAS_FONTANERIA_Y_FUGAS.md`
- `knowledge/guides/R4R_81_BANOS_DUCHAS_Y_SANEAMIENTO.md`
- `knowledge/guides/R4R_82_HUMEDADES_FUGAS_CONDENSACION_Y_MOHO.md`
- `knowledge/guides/R4R_83_VIVIENDAS_ANTIGUAS_Y_REHABILITACION.md`
- `knowledge/guides/R4R_84_PATIOS_SOLERAS_SOLADOS_Y_DRENAJE.md`
- `knowledge/guides/R4R_85_OFICINAS_LOCALES_Y_COMUNIDADES.md`
- `knowledge/guides/R4R_86_PRESUPUESTOS_MEDICIONES_Y_CAMBIOS.md`
- `knowledge/guides/R4R_87_AISLAMIENTO_VENTILACION_Y_REHABILITACION_ENERGETICA.md`
- `knowledge/guides/R4R_88_SEGURIDAD_INSTALACIONES_PERMISOS_Y_MATERIALES.md`
- `knowledge/guides/R4R_89_OTROS_ESPACIOS_Y_OBRAS_ESPECIALES.md`
- `knowledge/guides/R4R_90_INCIDENTES_SOPORTE_Y_ENTREGA.md`
- `knowledge/sources/R4R_71_REFERENCIAS_TECNICAS_INTERNACIONALES_2026.md`

## Ficheros existentes modificados

- `knowledge/R4R_00_IDENTIDAD_CRITERIOS_Y_VOZ_EDITORIAL.md`
- `knowledge/R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md`
- `knowledge/R4R_20_CASOS_OFICINAS_CUEVAS_BANOS_Y_FILTROS.md`
- `knowledge/R4R_30_METODO_PRESUPUESTOS_SEO_CATALOGO_Y_PLANTILLAS.md`
- `knowledge/faqs/R4R_40_ALL_FAQ.md`
- `knowledge/faqs/r4r-faq-index.json`

## Importante: el mecanismo de ingesta real en este repositorio

Comprobado en GitHub, rama principal, a fecha de la preparación:

1. `MarkdownDocumentLoader.loadAll()` lee **todos los `.md` recursivamente** bajo `r4r.knowledge.root`. No filtra el campo YAML `ingest_into_editorial_rag`; por eso no se incluyen notas de operación dentro de `knowledge/`.
2. `HeadingMarkdownChunker` corta por cabeceras y longitud máxima. El valor por defecto es **2000 caracteres**.
3. `KnowledgeIngestionCli` ejecuta `KnowledgeIngestionService`, que mantiene las tablas **`knowledge_sources` y `knowledge_chunks`**. **No escribe en `vector_store` ni invoca embeddings** en el método examinado.
4. `PgVectorKnowledgeStore.index` sí sustituye documentos por `source` y llama a `VectorStore.add` para generar y guardar embeddings, pero no hemos localizado en los componentes examinados un proceso CLI de producción que encadene automáticamente ambos pasos.
5. El flujo de ingesta **NO elimina rutas ausentes** de `knowledge_sources` (`deletedSources=0`), por lo que se han mantenido intactas las rutas originales para evitar duplicados huérfanos. Para la base vectorial también hay que verificar eliminación de fuentes obsoletas antes de mover archivos.
6. S3 Vectors se alimenta mediante una **exportación/importación separada** (región e índice ya existentes), no se actualiza por hacer ingesta SQL.

## Procedimiento seguro propuesto

### A. Preparación y copia

1. Haz backup de PostgreSQL y guarda una copia del directorio `knowledge/` y los JSON S3 anteriores. No borres datos de producción.
2. Descomprime el ZIP en un directorio temporal. Copia **solo** su carpeta `knowledge/` sobre `knowledge/` del repo, manteniendo rutas; por ejemplo, desde la carpeta descomprimida:

```bash
rsync -av knowledge/ ~/Desarrollo/R4R/r4r-spring-ai-rag.git/knowledge/
```

Ajusta la ruta local real. `rsync` SIN `--delete` preserva originales. Verifica `git diff -- knowledge/` antes de proceder.
3. **NO** cambies a Titan, a 512 dimensiones ni a un embedding multilingüe durante este primer ensayo. El índice PostgreSQL actual es `VECTOR(768)`; un modelo distinto exige reindexar todo y crear un índice compatible.

### B. Catalogar y probar, sin riesgo para producción

4. Valida que los documentos nuevos se descubren, que no hay rutas repetidas y que los archivos cumplen `RAG_MAX_FILE_BYTES` y `RAG_MAX_CHUNK_CHARS`. El recuento esperado de referencia es **25 Markdown** y aproximadamente **497 chunks** (el CLI real tiene la última palabra).
5. Ejecuta primero en una **BD de pruebas**, con configuración de conexión segura y el mismo modelo Nomic. No pases secretos por argumentos ni expongas el `.env`.
6. Para ejecutar el CLI Java no-web **desde un entorno que ya tenga inyectados los valores de configuración correctos** y con Maven instalado, un método genérico sería:

```bash
mvn -q -DskipTests compile dependency:build-classpath -Dmdep.outputFile=/tmp/r4r-runtime-cp.txt
java -cp "target/classes:$(cat /tmp/r4r-runtime-cp.txt)" \
  com.riansares.r4r.ingestion.KnowledgeIngestionCli
```

**Advertencia:** si tus perfiles dependen de `docker compose`, esta ejecución local no hereda automáticamente su `.env`. Ejecutarla sin configuración apropiada fallará; no uses producción como primera prueba.
7. Examina la línea `R4R_INGESTION_RESULT` del CLI. En BD comprueba fuentes/chunks:

```sql
SELECT COUNT(*) FROM knowledge_sources;
SELECT COUNT(*) FROM knowledge_chunks;
SELECT COUNT(*) FROM vector_store;
SELECT COUNT(DISTINCT metadata->>'source') FROM vector_store;
```

**Muy importante:** ver crecer `knowledge_chunks` no demuestra que haya crecido `vector_store`. Si el vector store sigue en el recuento antiguo, **no has completado la reingesta RAG**.

### C. Vectorización para producción

8. En la implementación actual, utiliza el procedimiento de indexación que invoque **`PgVectorKnowledgeStore.index`** sobre los nuevos chunks, respetando `source`, `headingPath` y `ordinal`. Si no existe CLI operativo para ese paso, hay que añadirlo o recuperar el proceso con el que se generaron los vectores previos. No ejecutar un `DELETE` masivo manual sin transacción y backup.
9. Comprueba que el modelo de indexación y consulta sea el **mismo `nomic-embed-text` (digest y 768 dimensiones)**; reproduce la misma forma de preparar el texto y prefijos de tarea que en el índice anterior. No mezclar vectores nuevos producidos por otro modelo.
10. Repite las consultas ES/EN del archivo de evaluación adjunto: mismo `topK`, `minScore`, modelo y corpus. Compara citas y abstenciones, no solo respuesta 200. **Estado inicial: NOT_RUN**.
11. Solo cuando pgvector esté correcto, crea una **nueva exportación** desde la BD actualizada e importa los vectores a S3 Vectors usando el flujo validado del proyecto. Verifica recuento, 768 dimensiones, metadatos y prueba NAS apagado/pgvector caído según corresponda. **No reutilices los JSON viejos para sobreescribir el índice recién actualizado**.
12. Si las citas públicas apuntan a PDF, genera/publica los PDF de **las nuevas fuentes** para que el frontend no muestre enlaces rotos. La ingesta Markdown por sí sola no publica PDFs.

## Advertencias de calidad y límites

- Los documentos nuevos mejoran cobertura, pero no demuestran por sí solos una mejora real del retrieval.
- Nomic Embed v1.5 es una versión enfocada al inglés; tus documentos están principalmente en español. El backend traduce algunas preguntas inglesas al español: comparar esa traducción con la opción de consulta original antes de asumir compatibilidad multilingüe.
- No se ha ejecutado el backend Spring Boot, PostgreSQL, Ollama ni AWS desde este entorno. No atribuir resultados de evaluación inexistentes.
- El archivo `R4R_EVALUACION_ES_EN_2026-11-13.json` contiene **preguntas y criterios**, no respuestas artificiales.
- **No usar los casos inventados como evidencias de proyectos finalizados**; están marcados como didácticos.

## Recomendación posterior

Mantener el mismo modelo y comparar primera/segunda/tercera pregunta de cocina en ES/EN. Si tras ampliar el corpus el inglés sigue absteniéndose para consultas equivalentes, revisar traducción, normalización, umbrales, scores y re-ranking ANTES de cambiar el modelo de embeddings. Un cambio de modelo obliga a regenerar PGVector y S3 Vectors juntos.
