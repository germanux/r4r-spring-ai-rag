# Preguntas independientes — preparación de fase 1

**Estado histórico de esta página: preparación de fase 1.** El usuario autorizó posteriormente la fase 2; véase [evaluación real del 10 de noviembre](rag-generalization-2026-11-10.md). Las restricciones de no ejecución descritas abajo corresponden a la entrega anterior. El `RagIndependentQuestionsIT` concreto sigue sin ejecutarse; la captura real se hizo con el diagnóstico PG/S3 y PC descrito en ese informe.

Las veinte preguntas se copian literalmente del mensaje del usuario en
`src/test/resources/evaluation/independent-questions-es.json`, con sus números,
orden y cuatro categorías. No se ha consultado el corpus para seleccionar temas,
reformularlas ni construir respuestas esperadas. No hay traducciones, pistas de
recuperación, palabras clave obligatorias ni etiquetas de respuesta/abstención.
La fixture no forma parte de `knowledge/` ni se ingiere en ningún índice.

## Qué queda preparado

- `IndependentQuestions`: lectura única de la fixture.
- `IndependentQuestionsFixtureTest`: comprobaciones offline de estructura, orden,
  unicidad y categorías; test del registrador con transporte simulado.
- `RecordingKnowledgeStore`: registra la consulta efectiva y los fragmentos finales
  retornados al generador, en su orden y con las etiquetas `[S1]…`, texto, fuente,
  encabezados y ordinal. No cambia los resultados ni admite escrituras.
- `RagIndependentQuestionsIT`: veinte casos dinámicos optativos y secuenciales de
  captura. Reutiliza `CitedRagService`, generación/embeddings Spring AI, los adaptadores
  y router de proveedores existentes. Conserva preguntas originales, respuestas,
  abstenciones, citas, fragmentos, métricas y errores. Guarda JSON después de cada
  caso mediante sustitución atómica para conservar resultados parciales.

No se prescribe que una pregunta deba responderse o abstenerse. Una abstención se
registra como tal sin considerarla fallo. Los errores de transporte/ejecución se
registran como `ERROR` y hacen fallar su caso JUnit; no se presentan como respuestas
correctas. Que un caso termine como `CAPTURED` solo significa que se registró la
respuesta, **no que sea correcta**.

## Alcance y configuración

Esta herramienta ejecuta los componentes actuales del backend **en proceso**;
no es una petición a la API actualmente desplegada en el NAS. No evalúa HTTP,
seguridad, SSE, cuotas del endpoint ni el gate de arranque/migraciones/recuperación
PG. No permite atribuir automáticamente sus resultados a otra versión desplegada.

Carga el `application.yml` de producción y sus imports mediante Spring, sin editar
archivos ni interpretar manualmente secretos. No fuerza `topK`, umbrales, ranking,
modelo, generación ni respuestas. Conserva las normalizaciones internas actuales
del RAG y registra la consulta efectiva además de la pregunta original.

Las conexiones PG fuerzan transacciones de solo lectura; la creación del almacén
usa `initializeSchema=false`, sin Boot/Flyway/ingestas. La selección de lectura se
hace con el router existente y sus flags vigentes. Los clientes AWS de esta
herramienta local utilizan explícitamente `r4r-codex` y la región configurada.
No se habilita Titan ni se altera su estado. Si algún proveedor está habilitado en
la configuración vigente, podrá utilizarse cuando se autorice la ejecución.
**OpenAI no se desactiva ni se habilita artificialmente:** se respeta el valor
vigente, y por tanto una futura ejecución puede generar consumo real.

La comparación específica PG/S3, revisión del respaldo documental, clasificación
correcta/parcial/genérica/incorrecta, diagnóstico y recomendaciones se reservan
para **fase 2**, después de revisar los registros. No se han preparado cambios de
comportamiento para superar estas preguntas.

## Comandos preparados, no ejecutados

Comprobación futura de la fixture/registrador, sin servicios:

```bash
mvn test -Dtest=IndependentQuestionsFixtureTest
```

**Solo después de validar esta preparación y autorizar consultas reales:**

```bash
mvn verify -Dit.test=RagIndependentQuestionsIT \
  -Dr4r.test.independent.enabled=true \
  -Dr4r.test.independent.output=target/independent-rag/phase1.json
```

Sin la propiedad explícita, la integración figura como `NOT RUN/SKIPPED`; no se
activa en el build ordinario. El comando también ejecuta la suite unitaria ordinaria
por el lifecycle Maven; no fuerza los demás perfiles reales.

El JSON inicial contiene las veinte entradas `NOT_RUN`; cada entrada pasa a
`CAPTURED` o `ERROR` según su ejecución. Un fallo global de preparación conserva
`setupErrorType` y deja las preguntas sin ejecutar. No se guardan mensajes SDK,
credenciales ni el volcado del entorno. Los textos y respuestas completos son datos
de evaluación: permanecen bajo `target/` ignorado, sin publicarlos automáticamente.

## Validación de esta preparación

Solo compilación de fuentes/tests (`mvn test-compile`), comprobación estática de la
fixture JSON y revisión de diff. **No se han ejecutado tests ni consultas de estas
veinte preguntas, ni se han consultado servicios o corpus para calificarlas.**
No se modifican código de producción, POM, configuración, índices ni documentos del
corpus. Esta entrega añade exclusivamente fixture, herramientas/tests y esta guía.

## Revisión posterior de las preguntas pares

El usuario pidió acortar las independientes 2, 4, …, 20. La fixture vigente contiene esa redacción; los originales de esta preparación se conservan en `independent-questions-es-original.json`. Véase [redacción vigente](rag-generalization-short-questions.md). Los resultados anteriores no se atribuyen a las preguntas nuevas.
