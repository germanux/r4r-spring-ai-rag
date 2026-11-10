# Generalización del RAG — evaluación real del 10 de noviembre de 2026

La batería de control funciona mucho mejor que las preguntas independientes. La captura termina sin errores de transporte, pero eso **no equivale a una evaluación semántica aprobada**: aparecen respuestas convincentes sin respaldo, transferencia de consejos entre contextos incompatibles y abstenciones inconsistentes entre ES/EN. No se ha corregido el sistema para superar estas preguntas.

El [registro detallado](rag-generalization-2026-11-10-details.md) conserva las 70 preguntas, sus respuestas completas, abstenciones, citas, consulta efectiva del embedding y las identidades/encabezados de todos los fragmentos finales. La [batería final](../../src/test/resources/evaluation/generalization-30.json) contiene textos ES/EN, orden e identificación estable; la [batería inicial](../../src/test/resources/evaluation/generalization-30-initial.json) conserva el orden anterior a la clasificación documental. Sus `evidence=true` provisionales para independientes **no son juicios de calidad** y no se usan como resultados.

**Revisión posterior de redacción:** las independientes pares tienen ahora una versión más breve para futuras ejecuciones. No se ha evaluado esa revisión. Los resultados de esta página y el registro detallado corresponden a los textos originales; las veinte preguntas históricas se conservan en `independent-questions-es-original.json` y las 30 iniciales en `generalization-30-initial.json`. Véase [redacción vigente](rag-generalization-short-questions.md).

## Ejecución y límites

- Rama `feature/aws-bedrock-embed-titan`, base `cb2065a`. Captura el 2026-11-10, aproximadamente 14:56:55–15:03:32 CET.
- 30 preguntas ES + las mismas 30 EN; además las otras diez independientes originales, ES. Total: **70 consultas y 70 comparaciones PG/S3**, sin cambiar el texto de las veinte independientes originales. Las traducciones de las diez primeras están fijadas antes de ejecutar; no se corrigen después de ver resultados.
- Generación y traducción interna exclusivamente en el **PC `192.168.1.207`, `qwen3:8b`**, mediante los componentes Spring AI existentes. Se filtra el proveedor configurado del PC para esta ejecución, sin fallback al portátil ni a OpenAI. El campo `generationProvider` refleja `lastUsed` del router y puede arrastrar la selección anterior cuando no se genera por falta de evidencia; el aislamiento del PC está impuesto en el router para todas las llamadas, no deducido únicamente de ese campo. Es la única selección deliberada respecto al enrutamiento habitual, solicitada por el usuario. No se editan archivos de configuración.
- Embeddings reales del router Nomic vigente, 768 dimensiones. El proveedor de embeddings puede ser distinto del PC de generación; no confundir ambos roles. No se usa Titan/Bedrock, no hay sondeos de su bloqueo ni mezclas de espacios vectoriales.
- Configuración de recuperación vigente: `topK=5`, `minScore=0.63`, multiplicador de candidatos `4`, peso léxico `0.075`. No se cambian umbral, ranking, prompts, generación, corpus ni índices.
- PG conectado con `default_transaction_read_only=on`, comprobado; 282 filas. Creación de schema desactivada, sin Boot/Flyway/ingesta. S3 consulta exclusivamente `amzn-r4r-vector-index` de `amzn-r4r-vectors`, perfil `r4r-codex`, región `eu-north-1`. No hay escrituras, IAM ni despliegues. Esta ejecución no hace un inventario completo independiente del índice S3.
- Se reutiliza `NomicRetrievalDiagnostic`: adaptadores actuales `PgVectorKnowledgeStore`/`S3VectorsKnowledgeStore`, ranking, ampliación de contexto y `CitedRagService`/`GenerationRouter`. El resultado de cada adaptador se suministra al generador en su orden original. El mismo vector de consulta se reutiliza para comparar ambos almacenes.
- Es una ejecución **en proceso de los componentes del backend**, no una prueba del endpoint desplegado en Synology. No valida HTTP/SSE, cuotas, seguridad, arranque/failover o reconexión PG de producción. No se detiene PG para provocar fallback; se consulta cada adaptador expresamente.
- Si texto/orden/metadatos coinciden, se reutiliza explícitamente la respuesta generada sobre PG: **67 respuestas S3 reutilizadas**, no 67 segundas generaciones. En los tres casos distintos se genera también sobre S3. No se presentan como 140 observaciones independientes del LLM.
- Las 95 rutas del manifiesto congelado de fuentes/configuración/corpus/herramienta/consultas no cambiaron durante la captura; se verificó SHA-256 al finalizar, antes de reordenar los tests. Los cambios posteriores afectan exclusivamente a pruebas, herramientas y documentación de evaluación.

El DTO de recuperación no expone puntuaciones; este registro captura los fragmentos finales, **no** las similitudes/candidatos crudos descartados. No atribuye los fallos a un valor concreto del umbral. Tampoco mide latencia de búsqueda PG/S3 por separado. `embeddingMs` externo varió entre 81.9 y 1880.6 ms. Las métricas internas de recuperación del generador utilizan un almacén ya capturado y no son tiempos reales de PG/S3. No son mediciones de Titan ni una prueba de rendimiento.

## Clasificación documental y orden final

«Dentro del dominio» significa que el corpus permite una orientación útil para el núcleo de la pregunta; `PARTIAL` exige declarar límites y no autoriza una respuesta completa. «Sin cobertura suficiente» puede seguir siendo una pregunta de reformas: compartir palabras como cocina, vigas o climatización no acredita el detalle técnico solicitado. Esta clasificación proviene del corpus, **no del acierto del modelo**.

De las diez independientes incorporadas: apoyo completo para la parte de instalaciones antes de acabados (independiente 7), y parcial para grietas/comprobación profesional (1), renovación selectiva de cocina (2), e investigación inicial de humedad/drenaje (4). Las otras seis no tienen cobertura suficiente de lo pedido: techo acústico/altura (3), doble/triple acristalamiento (5), luz/aprovechamiento de pasillo (6), decisión estructural en vigas combadas (8), viabilidad de equilibrar climatización sin sustituirla (9) y obras de apertura de cafetería (10). No se confunde el filtro comercial/licencias general con un proyecto técnico de cafetería.

Quedan **1–19 con apoyo total/parcial; 20–30 sin cobertura suficiente**. Los originales 1–12 no se reformulan; EN de la primera se iguala literalmente a ES. Servicios conserva ambas versiones en 13, las dos nuevas con apoyo están en 14–15. Los diez independientes se mantienen y solo cambian de posición, con ID inicial persistente. Geotermia/cargador permanecen en 26–27; fotosíntesis/Júpiter/literatura, en 28–30.

`evidence` es el contrato binario de los smoke tests preexistentes, no una medición de pertinencia. Las limitaciones honestas de ventanas/vigas en ES se valoran como parciales, aunque no cumplan la abstención vacía que esos smoke tests exigen para preguntas sin cobertura. No se modifica ese contrato para hacer pasar esta captura. Los tests HTTP con LLM real no se han lanzado ni se declaran aprobados.

## Resultados por pregunta

Las valoraciones son una revisión humana del respaldo documental, no una nota de un segundo LLM. `INCORRECTA/SIN_RESPALDO` incluye inferencias no acreditadas por la fuente, sin afirmar que se haya comprobado externamente toda la ingeniería o normativa. `GENÉRICA` reconoce respuestas que aparentan contestar pero no resuelven la consulta. `ABSTENCIÓN_INNECESARIA` tiene evidencia suficiente recuperada. Una abstención por falta de documentación o por ambigüedad irresoluble se valora positivamente.

| Final | Inicial | Tema / independiente | Cobertura | ES | EN |
|---:|---:|---|---|---|---|
| 1 | 1 | Primera orientación | SUPPORTED | CORRECTA | CORRECTA |
| 2 | 2 | Solera frente a solado | SUPPORTED | CORRECTA | CORRECTA |
| 3 | 3 | Precio por acceso | SUPPORTED | CORRECTA | CORRECTA |
| 4 | 4 | Casa antigua | SUPPORTED | PARCIAL | PARCIAL |
| 5 | 5 | Visita previa | SUPPORTED | CORRECTA | CORRECTA |
| 6 | 6 | Patio | SUPPORTED | CORRECTA | CORRECTA |
| 7 | 7 | Hormigón de camión | SUPPORTED | CORRECTA | CORRECTA |
| 8 | 8 | Pasillo estrecho | SUPPORTED | CORRECTA | CORRECTA |
| 9 | 9 | Humedades | SUPPORTED | CORRECTA | CORRECTA |
| 10 | 10 | Plazos | SUPPORTED | CORRECTA | ABSTENCIÓN_INNECESARIA |
| 11 | 11 | Presupuesto | SUPPORTED | CORRECTA | CORRECTA |
| 12 | 12 | Baño | SUPPORTED | CORRECTA | CORRECTA |
| 13 | 13 | Servicios de la empresa | SUPPORTED | CORRECTA | CORRECTA |
| 14 | 14 | Cal aérea y cal hidráulica | SUPPORTED | CORRECTA | CORRECTA |
| 15 | 15 | Baldosa sobre pavimento existente | SUPPORTED | CORRECTA | CORRECTA |
| 16 | 16 | Independiente 1 | PARTIAL | INCORRECTA/SIN_RESPALDO | INCORRECTA/SIN_RESPALDO |
| 17 | 17 | Independiente 2 | PARTIAL | PARCIAL | PARCIAL |
| 18 | 19 | Independiente 4 | PARTIAL | INCORRECTA/SIN_RESPALDO | INCORRECTA/SIN_RESPALDO |
| 19 | 22 | Independiente 7 | SUPPORTED | INCORRECTA/SIN_RESPALDO | GENÉRICA |
| 20 | 18 | Independiente 3 | INSUFFICIENT | ABSTENCIÓN_JUSTIFICADA | ABSTENCIÓN_JUSTIFICADA |
| 21 | 20 | Independiente 5 | INSUFFICIENT | PARCIAL | ABSTENCIÓN_JUSTIFICADA |
| 22 | 21 | Independiente 6 | INSUFFICIENT | GENÉRICA | INCORRECTA/SIN_RESPALDO |
| 23 | 23 | Independiente 8 | INSUFFICIENT | PARCIAL | ABSTENCIÓN_JUSTIFICADA |
| 24 | 24 | Independiente 9 | INSUFFICIENT | INCORRECTA/SIN_RESPALDO | ABSTENCIÓN_JUSTIFICADA |
| 25 | 25 | Independiente 10 | INSUFFICIENT | INCORRECTA/SIN_RESPALDO | INCORRECTA/SIN_RESPALDO |
| 26 | 26 | Fuera de corpus: geotermia | INSUFFICIENT | ABSTENCIÓN_JUSTIFICADA | ABSTENCIÓN_JUSTIFICADA |
| 27 | 27 | Fuera de corpus: cargador eléctrico | INSUFFICIENT | ABSTENCIÓN_JUSTIFICADA | ABSTENCIÓN_JUSTIFICADA |
| 28 | 28 | Fuera de dominio: fotosíntesis | INSUFFICIENT | ABSTENCIÓN_JUSTIFICADA | ABSTENCIÓN_JUSTIFICADA |
| 29 | 29 | Fuera de dominio: astronomía | INSUFFICIENT | ABSTENCIÓN_JUSTIFICADA | ABSTENCIÓN_JUSTIFICADA |
| 30 | 30 | Fuera de dominio: literatura | INSUFFICIENT | ABSTENCIÓN_JUSTIFICADA | ABSTENCIÓN_JUSTIFICADA |

| Independiente | Cobertura | Valoración ES |
|---:|---|---|
| 11 | PARTIAL | INCORRECTA/SIN_RESPALDO |
| 12 | INSUFFICIENT | INCORRECTA/SIN_RESPALDO |
| 13 | PARTIAL | PARCIAL |
| 14 | PARTIAL | PARCIAL |
| 15 | PARTIAL | CORRECTA |
| 16 | INSUFFICIENT | GENÉRICA |
| 17 | INSUFFICIENT | INCORRECTA/SIN_RESPALDO |
| 18 | SUPPORTED | INCORRECTA/SIN_RESPALDO |
| 19 | PARTIAL | ABSTENCIÓN_JUSTIFICADA |
| 20 | PARTIAL | PARCIAL |

Sobre las 70 respuestas PG representativas: **28 correctas, 9 parciales, 13 incorrectas/sin respaldo, 3 genéricas, 16 abstenciones justificadas y 1 innecesaria**. Son recuentos descriptivos de una ejecución, no una tasa estadística de calidad: mezclan controles conocidos, nuevas preguntas y pares ES/EN relacionados. Las tres respuestas S3 generadas por separado reciben las mismas categorías que sus parejas PG, aunque el texto difiere.

## Recuperación: problemas observados

1. **Coincidencia superficial de tema frente a intención.** Terraza devuelve cisterna; pasillo oscuro devuelve transporte de hormigón; pared deteriorada por abajo devuelve pintura de techo tras una fuga reparada. El contexto es real pero no responde a la situación. Subir simplemente `topK` o bajar un umbral no queda justificado por estos datos.
2. **Evidencia útil que no llega al generador.** Para el orden de obra sí existen [FAQ-047](../../knowledge/faqs/R4R_40_ALL_FAQ.md) y [§7.7 Instalaciones](../../knowledge/R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md). No están en los fragmentos finales de esa pregunta. Para grietas, la regla [§4.4](../../knowledge/R4R_00_IDENTIDAD_CRITERIOS_Y_VOZ_EDITORIAL.md) exige comprobación profesional; tampoco llega. La recuperación limita la respuesta incluso cuando el corpus tiene una orientación apropiada.
3. **Traducción interna que altera la intención.** EN de ventanas pierde `triple` y acaba como doble/doble. EN de hormigón de camión se traduce como hormigón armado. Las preguntas de entrada guardadas son correctas; estas alteraciones se producen en la traducción del sistema, no en la fixture. Se conserva la traducción real en el registro y no se reejecuta con una traducción facilitada para mejorar el resultado.
4. **PG/S3 muy próximos, no idénticos.** Coinciden exactamente en 67/70 consultas. Diferencias:
   - `case-21/es`, pasillo oscuro, final 22: PG añade Caso A padre en S4; S3 devuelve solo S1–S3. Ambos proponen información de acceso/solera sin resolver iluminación.
   - `case-10/en`, final 10: mismos cinco fragmentos; FAQ-003 pasa de S2 a S3. Ambos se abstienen aunque el plazo figura en esa FAQ.
   - `independent-11/es`, clínica: S4 cambia de «Contenido útil para clientes» a «Artículos derivados». Ambos generan conclusiones de viabilidad no acreditadas con SEO/baño/inodoro.

Es plausible que pequeñas diferencias de candidatos o distancias afecten casos próximos, pero no se registraron los valores crudos para demostrar la causa numérica de esas tres divergencias. No se atribuyen a falta de sincronización sin una comprobación adicional. Los fallos comunes no se solucionan cambiando automáticamente PG por S3.

## Generación y citación

- **Transferencias falsas.** EN terraza presenta la válvula de llenado de una cisterna como sistema de drenaje de terraza. Clínica usa una lista SEO de términos como evidencia de viabilidad antes de alquilar. Cubierta industrial recibe mortero transpirable por analogía con humedad de muros. Oficina de cristal recibe un tabique opaco al que se atribuye conservar apertura visual. Son respuestas persuasivas sin respaldo suficiente.
- **Conclusiones no autorizadas.** Grietas EN sugiere que sin ciertos signos pueden ser superficiales; climatización ES asegura reparación sin sustituir el sistema. Las fuentes no acreditan esas decisiones. La pregunta de orden de obra ES llega a incluir instalar laminado dentro del comienzo de demoliciones/estructura sin una secuencia documentada.
- **Abstención variable.** Las cinco preguntas claramente ajenas o de dimensionamiento ausente (26–30) se abstienen en ES y EN. En cambio, consultas cercanas al vocabulario del corpus generan afirmaciones no acreditadas. EN plazos se abstiene pese a tener FAQ-003. No basta que existan cinco fragmentos por encima del umbral para que exista respuesta.
- **Metadatos íntegros, respaldo semántico insuficiente.** No se detectaron discrepancias entre etiqueta devuelta y fuente/ordinal/encabezado del fragmento en esa posición. Esto prueba trazabilidad mecánica, no implicación lógica. EN cafetería cita S2 para ventilación/estudio cuando S2 es solera/solado y la FAQ de estudio de cocina está en S3. La fuente asociada a S2 se conserva correctamente; lo incorrecto es atribuirle esa afirmación.
- El prompt actual ya exige usar solo evidencia. Esta ejecución muestra que esa instrucción y la validación de etiquetas no garantizan obediencia semántica. No se ha aplicado ningún cambio de prompt, sinónimos, reglas de preguntas, reranking o umbrales.

## Cambios de evaluación y verificación

Solo pruebas/herramientas/documentación:

- `RagLiveQuestionsIT` y `RagLiveEnglishQuestionsIT`: 30 casos alineados en orden final; EN1 corregida; servicios conservados.
- `generalization-30-initial.json` y `generalization-30.json`: textos congelados, mapeo estable, cobertura y justificación documental.
- `GeneralizationQuestionsFixtureTest`: comprueba que la reordenación no cambia textos, que las diez independientes conservan el texto original y que ambos consumidores ES/EN están alineados.
- `NomicRetrievalDiagnostic --generalization-pc`: comparación de lectura con PC, traducción real y registro de ambas respuestas; guardado después de cada caso.
- `NomicRetrievalRegressionTest` usa `nomic-captured-questions.json` con las preguntas históricas a las que pertenecen los vectores capturados. **No se adjudican embeddings antiguos a las nuevas preguntas** ni se presenta esa reproducción offline como prueba semántica de las 30 actuales.
- Consumidores de batería existentes (`r4r_evaluation_cases.py`, `TitanDegradedHttpIT`, `lambda-titan/test_corpus.py`) actualizados a 30 por idioma. Los 60 casos Titan simulados verifican contrato/fallback; sus respuestas enlatadas no validan la pertinencia de esta evaluación real.

Comandos ejecutados después de preparar/reordenar:

```bash
mvn clean verify
mvn -f lambda-titan/pom.xml verify
python3 -m unittest discover -s lambda-titan -p 'test_*.py'
git diff --check
git diff --cached --check
```

Resultados: backend **417 unitarios aprobados; 76 integraciones offline aprobadas y 26 opcionales skipped** (102 entradas Failsafe). Lambda **21 aprobados**. Python **8 aprobados**. Sin `skipTests`. Esto valida las modificaciones de pruebas y los contratos offline; **no transforma los 13 problemas de respaldo en respuestas aprobadas**. Las pruebas reales opcionales del lifecycle Maven no se activan por esta captura independiente.

Captura real realizada, sin escritura de recursos:

```bash
mvn test-compile dependency:build-classpath -Dmdep.outputFile=/tmp/r4r-generalization/classpath.txt
java -cp "target/classes:target/test-classes:$(cat /tmp/r4r-generalization/classpath.txt)" \
  com.riansares.r4r.vector.NomicRetrievalDiagnostic \
  --run --generalization-pc \
  --questions /tmp/r4r-generalization/questions-frozen.json \
  --output /tmp/r4r-generalization/live.json
```

Es una ejecución explícita que requiere PG/S3/Nomic y el PC accesibles. Para reconstruir el archivo de entrada con la batería inicial congelada y las otras diez independientes (sin respuestas esperadas):

```bash
python3 - <<'PY'
import json
from pathlib import Path
root = Path('src/test/resources/evaluation')
cases = json.loads((root / 'generalization-30-initial.json').read_text())
questions = [dict(caseId=c['caseId'], question=c[lang], lang=lang)
             for lang in ['es', 'en'] for c in cases]
independent = json.loads((root / 'independent-questions-es-original.json').read_text())
questions += [dict(caseId=f"independent-{c['id']:02d}", question=c['question'], lang='es')
              for c in independent[10:]]
p = Path('/tmp/r4r-generalization/questions-frozen.json')
p.parent.mkdir(parents=True, exist_ok=True)
p.write_text(json.dumps(questions, ensure_ascii=False, indent=2) + '\n')
PY
```

Artefactos locales completos, preservados **después** del `clean`: `target/generalization-validation/live.json` (textos íntegros de fragmentos), `live.log`, `questions-frozen.json`, `frozen-manifest.json`, `adjudication.json` y logs Maven/Python. Copia de seguridad de esta sesión en `/tmp/r4r-generalization/`. `target/` es ignorado y un futuro `clean` lo borra; las respuestas y juicios duraderos quedan en este informe y el registro detallado versionable. No se versionan logs completos ni credenciales.

## Pendiente y posibles siguientes investigaciones

No se han ejecutado pruebas reales Titan/Bedrock, ni despliegue NAS ni HTTP de su instancia. Esta evaluación tampoco prueba fallos reales de proveedores. No hay bloqueo de acceso a PG/S3/PC en esta captura.

Los siguientes trabajos requerirían una tarea de corrección separada: observar candidatos/puntuaciones en los casos de intención fallida; evaluar fidelidad de traducción; medir si el generador distingue apoyo parcial de ausencia de apoyo; y comprobar implicación de cada afirmación por su cita. Se deberían conservar estas preguntas congeladas y usar otro conjunto independiente para medir generalización después de corregir. No se propone cambiar un umbral global, introducir sinónimos de estas preguntas ni editar corpus para aprobarlas.

No se han hecho commits, merges ni pushes. El controlador conserva la propiedad de las operaciones Git.
