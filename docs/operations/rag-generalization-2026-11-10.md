# Generalización del RAG — evaluación real del 10 de noviembre de 2026

## Configuración congelada — 0.3.4

Por instrucción del usuario se aplica la configuración conocida: `topK=5`, `minScore=0.63`, `candidate-multiplier=8` y `lexical-weight=0.10`. Se actualizan la configuración pública, sus valores de respaldo en Spring/Compose y la versión Maven/imagen a `0.3.4`. Se detiene la comparación adicional; no se ejecutan más pruebas ni compilación para este cambio. La validación completa de críticos S3 no se declara realizada. No se despliega en Synology ni se modifican las preguntas, la lógica RAG o los índices.

## Optimización posterior — una hora, solo PostgreSQL

**Ventana de experimentación: 10-11-2026, 15:55:40–16:55:40 CET (14:55:40–15:55:40 UTC), incluida preparación y revisión intermedia; la última consulta en curso terminó a las 16:55:43 CET.** Se utilizaron las preguntas vigentes, incluidas las independientes abreviadas; no se cambió ningún texto. Esta sesión es posterior a la evaluación histórica de 70 consultas que se conserva debajo.

Se completó un barrido de **95 configuraciones de recuperación × 73 consultas = 6935 búsquedas**. En el flujo de respuesta se registraron **828 consultas** (incluidas abstenciones y casos sin llamada al LLM por falta de fragmentos), en **11 tandas completas de 73** y las repeticiones dirigidas indicadas en la tabla. Son repeticiones de las mismas preguntas, no 828 preguntas independientes. Errores de ejecución registrados: **0**.

Las 73 consultas son las 30 principales ES, las 30 EN, las diez independientes adicionales ES y los tres controles existentes de cueva/bodega/mortero. Se respeta el router local por defecto: PC `qwen3:8b`, LP `qwen2.5:7b`; en las generaciones registradas se utilizó el PC. Nomic conserva su router, modelo, digest y 768 dimensiones. Temperatura configurada: `0`. OpenAI, S3, Titan y failover de almacenes no intervienen en esta sesión.

Para aislar parámetros y aprovechar la hora, las traducciones reales y los 73 embeddings Nomic reales se calcularon una vez y se reutilizaron entre variantes. Las búsquedas de cada variante sí se ejecutan contra PostgreSQL y las respuestas se generan con los componentes actuales. Las latencias de la tabla excluyen ese trabajo inicial y la preparación del contexto Spring; no representan el tiempo completo del endpoint. No se corrigieron las traducciones defectuosas observadas.

PostgreSQL usa `default_transaction_read_only=on`, comprobado, sin Flyway, ingesta ni escrituras. Las 282 filas mantienen el mismo fingerprint antes/después de las ejecuciones. Se verificaron hashes de fuentes, preguntas, corpus y configuración: **sin cambios**. El ejecutor temporal está fuera del repositorio. Solo se actualiza este informe; los valores se probaron como ajustes temporales y no se aplican a una instancia activa.

### Comparación de parámetros

`m` es el multiplicador de candidatos; se recuperan hasta `topK × m` antes de elegir el contexto final. `w` es el peso BM25 léxico añadido al coseno para ordenar candidatos. El umbral se aplica al coseno original, no a la puntuación fusionada. El barrido explora umbrales entre 0,55 y 0,70, contextos de 3–10, multiplicadores 2–10 y pesos 0–0,20; no es un producto cartesiano completo de esos intervalos.

**Anclas** mide si alguna sección documental de referencia está presente en 47 consultas con apoyo total/parcial. Es una medida aproximada de cobertura: no demuestra que el generador use esa fuente ni que cada afirmación sea cierta. **Abst. fuera** cuenta el indicador de abstención en 26 consultas sin respuesta completa o ambiguas; una limitación honesta puede ser una respuesta no vacía y no se considera automáticamente una alucinación. Las tandas dirigidas tienen otro denominador y no se comparan como baterías completas.

| Tanda | N | topK / umbral / m / w | Anclas | Abst. fuera | Contexto medio (caracteres) | Mediana (s) |
|---|---:|---|---:|---:|---:|---:|
| Referencia | 73 | 5 / 0.63 / 4 / 0.075 | 37/47 | 18/26 | 2585 | 3.78 |
| Más contexto | 73 | 8 / 0.63 / 4 / 0.075 | 38/47 | 17/26 | 3816 | 4.05 |
| Solo menor umbral | 73 | 5 / 0.58 / 4 / 0.075 | 37/47 | 17/26 | 2948 | 4.02 |
| 40 candidatos, primera pasada | 73 | 5 / 0.63 / 8 / 0.075 | 38/47 | 17/26 | 2595 | 3.94 |
| Contexto de 3 | 73 | 3 / 0.63 / 8 / 0.075 | 34/47 | 18/26 | 1642 | 3.37 |
| Referencia, repetición | 73 | 5 / 0.63 / 4 / 0.075 | 37/47 | 18/26 | 2585 | 3.90 |
| 40 candidatos, repetición | 73 | 5 / 0.63 / 8 / 0.075 | 38/47 | 18/26 | 2595 | 3.61 |
| Contexto de 10 y umbral menor | 73 | 10 / 0.6 / 4 / 0.075 | 40/47 | 15/26 | 5568 | 5.16 |
| Contexto de 4 y más peso léxico | 73 | 4 / 0.63 / 10 / 0.1 | 38/47 | 18/26 | 2224 | 3.87 |
| Punto intermedio | 73 | 7 / 0.61 / 10 / 0.075 | 40/47 | 15/26 | 3955 | 4.58 |
| 40 candidatos y peso 0,10 | 73 | 5 / 0.63 / 8 / 0.1 | 38/47 | 17/26 | 2718 | 4.00 |
| Repetición crítica de peso 0,10 | 15 | 5 / 0.63 / 8 / 0.1 | 6/10 | 2/5 | 3302 | 3.64 |
| Control final de referencia | 10 | 5 / 0.63 / 4 / 0.075 | 5/9 | 1/1 | 4032 | 5.07 |

### Qué mejora y qué empeora

- **Ampliar candidatos resuelve un caso concreto.** La FAQ-047 de instalaciones antes de acabados está en el puesto 30 para la consulta ES, con coseno `0,63333`. Supera `0,63`, pero no entra entre los 20 candidatos de referencia. Con 40 candidatos y cinco fragmentos llega como S1; las dos pasadas responden correctamente. Las dos referencias daban una secuencia no documentada que incluía instalar laminado al principio.

- **Bajar solo a 0,58 no corrige esa exclusión.** Se mantienen cinco fragmentos y 20 candidatos; la cobertura de anclas sigue en 37/47, el contexto aumenta y aparecen consejos sin respaldo, como espejos para el pasillo. Mejora la orientación sobre la pared deteriorada tras pintarla, pero pierde la abstención de la pregunta ambigua sobre arreglar frente a hacer nuevo, con una comparación de costes no acreditada.

- **Más contexto puede distraer.** Con diez fragmentos y umbral 0,60 el generador llega a recomendar valorar vinílico para una terraza que filtra agua, y aplica un tabique vertical acústico al ruido del vecino de arriba. La FAQ correcta del orden de obra está presente, pero se ignora o se mezcla con una secuencia errónea. Su cobertura documental es mayor y su respuesta puede ser peor.

- **Tres fragmentos pierden la mejora de la cueva.** Con m=8 se examinan 24 candidatos y se descarta el Caso G de la consulta cueva; la bodega termina recibiendo servicios genéricos de la empresa. La menor latencia no compensa esa regresión.

- **Cuatro fragmentos y w=0,10 priorizan el Caso G.** La cueva recibe cal y malla citando el caso específico, y el orden de obra ES se responde bien. Sin embargo, aparece una abstención innecesaria en humedades EN. También se abstiene en grietas EN, evitando el diagnóstico infundado de la referencia: el contador de abstenciones requiere esta revisión semántica.

- **Siete fragmentos / 0,61 / m=10 mejoran el orden de obra EN**, recuperando FAQ-047 con coseno `0,61704`, posición 65. A cambio, vuelven a mezclar el orden ES con laminado y a asegurar aislamiento de techo sin perder altura basándose en un tabique. No se recomienda como mejora global.

- **La fidelidad sigue siendo el límite.** Grietas estructurales, privacidad acústica frente a anonimización editorial, climatización y cubiertas conservan respuestas no acreditadas en varias variantes. La regla estructural §4.4 queda en el puesto 68 ES, coseno `0,59028`; admitirla como candidata no garantiza que el reranking la seleccione. Las pruebas no justifican dar por solucionada la calidad general mediante un umbral único.

### Recomendación

El candidato más interesante de esta sesión es **`topK=5`, `minScore=0,63`, `candidate-multiplier=8` (40 candidatos) y `lexical-weight=0,10`**. Se ejecutó en las 73 consultas y se repitió en 15 casos críticos. La pregunta de la cueva cita el Caso G como S2 y menciona cal y malla; la de mortero da NHL 2/NHL 3.5. Responde correctamente al orden de obra ES y mantiene la respuesta de humedades EN que se perdía con cuatro fragmentos. En grietas EN se abstiene en lugar de improvisar un diagnóstico. El contexto medio aumenta aproximadamente un 5 % respecto a referencia; no se demuestra una mejora de latencia. La pregunta genérica de bodega sigue ofreciendo objetivos de rehabilitación, sin concretar material: no se declara resuelta por recuperar el Caso G.

La alternativa mínima es cambiar solo el multiplicador a 8 y conservar el peso 0,075: en dos pasadas completas corrige el orden de obra ES con prácticamente el mismo contexto. El peso 0,10 prioriza mejor el Caso G, pero reordena más consultas (41/73 frente a 16/73 del cambio de multiplicador solo). No se ha probado que sea un óptimo global. No se recomienda bajar el umbral de forma general: las variantes 0,58–0,61 ensayadas generan regresiones de respaldo o mezclan contextos. La versión EN del orden de obra y la abstención innecesaria de plazos EN siguen pendientes con el candidato recomendado, al igual que varias respuestas no acreditadas sobre grietas ES, privacidad, climatización y cubiertas. No se han modificado archivos de configuración ni parámetros de una instancia activa.

Las repeticiones de referencia conservan exactamente el contexto y las decisiones de abstención, pero difieren en redacción; incluso con temperatura 0 no se ha supuesto identidad literal del texto. Las comparaciones se apoyan en documentos y afirmaciones, no en igualdad de cadenas. El ajuste se hace sobre una batería conocida: no constituye validación ciega de generalización.

### Evidencia y reproducción de esta sesión

Los resultados completos se unifican en `target/pg-tuning-validation/results.json`, ignorado por Git: parámetros efectivos, traducciones, embeddings reales reutilizados, candidatos y scores PG, fragmentos finales, respuestas, citas, métricas, hashes y ejecutor temporal. Un `mvn clean` borra ese artefacto; durante esta sesión existe además la copia `/tmp/r4r-pg-tuning/`. Los nombres internos de trabajos programados pueden diferir del perfil finalmente elegido durante el barrido adaptativo; la tabla usa siempre los parámetros **efectivamente ejecutados**, almacenados en cada resultado.

No se ejecutó Maven de nuevo: no hay cambios de código ni preguntas. Se verificaron la integridad de esos archivos y `git diff --check`. No se hicieron commits, despliegues, cambios IAM, escrituras en índices ni pruebas contra S3. Las diferencias PG/S3 de la evaluación histórica siguen documentadas debajo; en esta hora no se vuelven a medir.

---

## Resumen de la evaluación anterior

El RAG responde bien a las preguntas habituales del corpus, pero **pierde fiabilidad con preguntas independientes**, especialmente cuando recupera textos relacionados que no resuelven la consulta concreta.

Se evaluaron **70 consultas**, usando el PC con `qwen3:8b`:

| Valoración | Respuestas |
|---|---:|
| Correctas | 28 |
| Parciales | 9 |
| Incorrectas o sin respaldo | 13 |
| Genéricas, sin resolver la pregunta | 3 |
| Abstenciones justificadas | 16 |
| Abstención innecesaria | 1 |

Estos recuentos corresponden a las respuestas PG representativas; las tres respuestas S3 generadas por separado reciben las mismas categorías. No se cuentan las respuestas reutilizadas como observaciones independientes.

**PostgreSQL frente a S3:** devolvieron exactamente los mismos fragmentos y orden en **67 de 70 consultas**. Las tres diferencias no cambiaron la valoración de las respuestas. No aparece una ventaja clara de un almacén sobre el otro.

**Español frente a inglés:** hay diferencias relevantes:

- Sobre plazos del presupuesto, ES responde correctamente; EN se abstiene teniendo la información.
- Sobre climatización, ES asegura una solución sin respaldo suficiente; EN se abstiene.
- La traducción interna convierte triple/doble acristalamiento en doble/doble y confunde hormigón de camión con hormigón armado.

Los fallos principales son:

- **Recuperación:** devuelve información de cisternas para una terraza o de pintura de techos para una pared deteriorada por abajo.
- **Generación:** aplica consejos a situaciones que las fuentes no justifican; por ejemplo, un tabique opaco presentado como solución que mantiene la apertura visual de una oficina acristalada.
- **Citación:** las etiquetas apuntan correctamente a fragmentos reales, pero algunas afirmaciones **no están respaldadas por esos fragmentos**.

Las cinco preguntas claramente ajenas o de dimensionamiento ausente —geotermia, cargador eléctrico, fotosíntesis, Júpiter y literatura— provocaron abstención en ambos idiomas. La dificultad aparece sobre todo en preguntas próximas al vocabulario del corpus.

**Estos resultados históricos corresponden a las preguntas anteriores a su abreviación. Las versiones cortas sí se usan en la sesión posterior de optimización PG descrita al principio.** Los tests offline aprobados verifican contratos y funcionamiento, no la corrección semántica de estas respuestas. La captura terminó sin errores de transporte; no se ha corregido el sistema para superar estas preguntas.

## Organización del informe y preguntas

Este es el **único informe de esta evaluación**: resumen, comparación PG/S3 y ES/EN, diagnóstico, comandos y registro completo de respuestas al final. Las preguntas para ejecutar tests tienen solo dos fuentes:

- [questions-es.json](../../src/test/resources/evaluation/questions-es.json): 30 casos principales ES, diez independientes adicionales ES y textos históricos/regresiones.
- [questions-en.json](../../src/test/resources/evaluation/questions-en.json): los 30 casos principales EN alineados por `caseId`, y sus textos históricos/regresiones existentes. No se han inventado traducciones de los diez adicionales que solo existían en ES.

`cases` contiene la redacción **vigente**. `additionalIndependentQuestions` completa los veinte independientes en ES. `history.generalization` contiene las preguntas realmente utilizadas en la captura de 70 consultas; `history.independentOriginal` conserva las veinte independientes originales; `history.nomic` mantiene la correspondencia de preguntas con los vectores antiguos de regresión. Estos históricos son evidencia, no baterías adicionales que se ejecuten automáticamente. No se reasignan vectores ni resultados antiguos a textos nuevos.

**Redacciones abreviadas:** independientes pares 2, 4, 6, 8 y 10 (puestos finales 17, 18, 22, 23 y 25), y adicionales 12, 14, 16, 18 y 20. La captura histórica de 70 consultas que sigue usó las versiones anteriores; la optimización posterior PG del principio utiliza las versiones vigentes. La unificación anterior no cambió textos ni resultados.

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

En la tabla, cada celda indica la categoría **PG / S3**: ambas coinciden en todos los casos. Esto no significa que todas las respuestas se generasen dos veces: en 67 consultas se reutilizó la generación por contexto idéntico. Las diferencias reales de fragmentos o texto están identificadas debajo y en el registro. La comparación **ES frente a EN** se lee horizontalmente. Ejemplos: plazos pasa de correcta ES a abstención innecesaria EN; climatización pasa de conclusión sin respaldo ES a abstención justificada EN.

| Final | Inicial | Tema / independiente | Cobertura | ES: PG / S3 | EN: PG / S3 |
|---:|---:|---|---|---|---|
| 1 | 1 | Primera orientación | SUPPORTED | CORRECTA / CORRECTA | CORRECTA / CORRECTA |
| 2 | 2 | Solera frente a solado | SUPPORTED | CORRECTA / CORRECTA | CORRECTA / CORRECTA |
| 3 | 3 | Precio por acceso | SUPPORTED | CORRECTA / CORRECTA | CORRECTA / CORRECTA |
| 4 | 4 | Casa antigua | SUPPORTED | PARCIAL / PARCIAL | PARCIAL / PARCIAL |
| 5 | 5 | Visita previa | SUPPORTED | CORRECTA / CORRECTA | CORRECTA / CORRECTA |
| 6 | 6 | Patio | SUPPORTED | CORRECTA / CORRECTA | CORRECTA / CORRECTA |
| 7 | 7 | Hormigón de camión | SUPPORTED | CORRECTA / CORRECTA | CORRECTA / CORRECTA |
| 8 | 8 | Pasillo estrecho | SUPPORTED | CORRECTA / CORRECTA | CORRECTA / CORRECTA |
| 9 | 9 | Humedades | SUPPORTED | CORRECTA / CORRECTA | CORRECTA / CORRECTA |
| 10 | 10 | Plazos | SUPPORTED | CORRECTA / CORRECTA | ABSTENCIÓN_INNECESARIA / ABSTENCIÓN_INNECESARIA |
| 11 | 11 | Presupuesto | SUPPORTED | CORRECTA / CORRECTA | CORRECTA / CORRECTA |
| 12 | 12 | Baño | SUPPORTED | CORRECTA / CORRECTA | CORRECTA / CORRECTA |
| 13 | 13 | Servicios de la empresa | SUPPORTED | CORRECTA / CORRECTA | CORRECTA / CORRECTA |
| 14 | 14 | Cal aérea y cal hidráulica | SUPPORTED | CORRECTA / CORRECTA | CORRECTA / CORRECTA |
| 15 | 15 | Baldosa sobre pavimento existente | SUPPORTED | CORRECTA / CORRECTA | CORRECTA / CORRECTA |
| 16 | 16 | Independiente 1 | PARTIAL | INCORRECTA/SIN_RESPALDO / INCORRECTA/SIN_RESPALDO | INCORRECTA/SIN_RESPALDO / INCORRECTA/SIN_RESPALDO |
| 17 | 17 | Independiente 2 | PARTIAL | PARCIAL / PARCIAL | PARCIAL / PARCIAL |
| 18 | 19 | Independiente 4 | PARTIAL | INCORRECTA/SIN_RESPALDO / INCORRECTA/SIN_RESPALDO | INCORRECTA/SIN_RESPALDO / INCORRECTA/SIN_RESPALDO |
| 19 | 22 | Independiente 7 | SUPPORTED | INCORRECTA/SIN_RESPALDO / INCORRECTA/SIN_RESPALDO | GENÉRICA / GENÉRICA |
| 20 | 18 | Independiente 3 | INSUFFICIENT | ABSTENCIÓN_JUSTIFICADA / ABSTENCIÓN_JUSTIFICADA | ABSTENCIÓN_JUSTIFICADA / ABSTENCIÓN_JUSTIFICADA |
| 21 | 20 | Independiente 5 | INSUFFICIENT | PARCIAL / PARCIAL | ABSTENCIÓN_JUSTIFICADA / ABSTENCIÓN_JUSTIFICADA |
| 22 | 21 | Independiente 6 | INSUFFICIENT | GENÉRICA / GENÉRICA | INCORRECTA/SIN_RESPALDO / INCORRECTA/SIN_RESPALDO |
| 23 | 23 | Independiente 8 | INSUFFICIENT | PARCIAL / PARCIAL | ABSTENCIÓN_JUSTIFICADA / ABSTENCIÓN_JUSTIFICADA |
| 24 | 24 | Independiente 9 | INSUFFICIENT | INCORRECTA/SIN_RESPALDO / INCORRECTA/SIN_RESPALDO | ABSTENCIÓN_JUSTIFICADA / ABSTENCIÓN_JUSTIFICADA |
| 25 | 25 | Independiente 10 | INSUFFICIENT | INCORRECTA/SIN_RESPALDO / INCORRECTA/SIN_RESPALDO | INCORRECTA/SIN_RESPALDO / INCORRECTA/SIN_RESPALDO |
| 26 | 26 | Fuera de corpus: geotermia | INSUFFICIENT | ABSTENCIÓN_JUSTIFICADA / ABSTENCIÓN_JUSTIFICADA | ABSTENCIÓN_JUSTIFICADA / ABSTENCIÓN_JUSTIFICADA |
| 27 | 27 | Fuera de corpus: cargador eléctrico | INSUFFICIENT | ABSTENCIÓN_JUSTIFICADA / ABSTENCIÓN_JUSTIFICADA | ABSTENCIÓN_JUSTIFICADA / ABSTENCIÓN_JUSTIFICADA |
| 28 | 28 | Fuera de dominio: fotosíntesis | INSUFFICIENT | ABSTENCIÓN_JUSTIFICADA / ABSTENCIÓN_JUSTIFICADA | ABSTENCIÓN_JUSTIFICADA / ABSTENCIÓN_JUSTIFICADA |
| 29 | 29 | Fuera de dominio: astronomía | INSUFFICIENT | ABSTENCIÓN_JUSTIFICADA / ABSTENCIÓN_JUSTIFICADA | ABSTENCIÓN_JUSTIFICADA / ABSTENCIÓN_JUSTIFICADA |
| 30 | 30 | Fuera de dominio: literatura | INSUFFICIENT | ABSTENCIÓN_JUSTIFICADA / ABSTENCIÓN_JUSTIFICADA | ABSTENCIÓN_JUSTIFICADA / ABSTENCIÓN_JUSTIFICADA |

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

Solo pruebas, herramientas y documentación; producción permanece intacta:

- `EvaluationQuestions` lee los dos JSON. `RagLiveQuestionsIT`, `RagLiveEnglishQuestionsIT` y `TitanDegradedHttpIT` reciben sus casos desde ese lector, incluidas las regresiones optativas.
- `IndependentQuestions` proyecta los veinte independientes desde el JSON ES y conserva su orden por ID, sin otra copia de preguntas.
- `NomicRetrievalRegressionTest` usa `history.nomic`, que mantiene los textos asociados a los vectores históricos; los tres casos cueva/bodega/mortero están en `regressions.cave` del mismo JSON ES. El diagnóstico reutiliza también esa sección.
- Python (`r4r_evaluation_cases.py`) lee directamente esos dos archivos; ya no interpreta código Java con expresiones regulares. Los consumidores de evaluación/failover y Lambda mantienen las mismas 60 entradas.
- Los tests offline verifican IDs, orden, cobertura, emparejamiento ES/EN y conservación de las preguntas impares/históricas. Los 60 casos Titan con mocks verifican contratos/fallback; sus respuestas enlatadas no prueban pertinencia semántica.
- Se eliminan las cinco fixtures dispersas anteriores, las listas literales de las dos clases live y los tres MD complementarios. Su información útil se incorpora aquí y en los dos JSON; no se pierde la captura anterior.

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
questions = []
for lang in ['es', 'en']:
    bank = json.loads((root / f'questions-{lang}.json').read_text())
    questions += [dict(caseId=c['caseId'], question=c['question'], lang=lang)
                  for c in bank['history']['generalization']]
independent = json.loads((root / 'questions-es.json').read_text())['history']['independentOriginal']
questions += [dict(caseId=f"independent-{c['id']:02d}", question=c['question'], lang='es')
              for c in independent[10:]]
p = Path('/tmp/r4r-generalization/questions-frozen.json')
p.parent.mkdir(parents=True, exist_ok=True)
p.write_text(json.dumps(questions, ensure_ascii=False, indent=2) + '\n')
PY
```

Artefactos locales completos, preservados **después** del `clean`: `target/generalization-validation/live.json` (textos íntegros de fragmentos), `live.log`, `questions-frozen.json`, `frozen-manifest.json`, `adjudication.json` y logs Maven/Python. Copia de seguridad de esta sesión en `/tmp/r4r-generalization/`. `target/` es ignorado y un futuro `clean` lo borra; las respuestas y juicios duraderos quedan en este único informe. No se versionan logs completos ni credenciales.

## Pendiente y posibles siguientes investigaciones

No se han ejecutado pruebas reales Titan/Bedrock, ni despliegue NAS ni HTTP de su instancia. Esta evaluación tampoco prueba fallos reales de proveedores. No hay bloqueo de acceso a PG/S3/PC en esta captura.

Los siguientes trabajos requerirían una tarea de corrección separada: observar candidatos/puntuaciones en los casos de intención fallida; evaluar fidelidad de traducción; medir si el generador distingue apoyo parcial de ausencia de apoyo; y comprobar implicación de cada afirmación por su cita. Se deberían conservar estas preguntas congeladas y usar otro conjunto independiente para medir generalización después de corregir. No se propone cambiar un umbral global, introducir sinónimos de estas preguntas ni editar corpus para aprobarlas.

No se han hecho commits, merges ni pushes. El controlador conserva la propiedad de las operaciones Git.

## Verificación de la unificación

Sobre la base Git `ebc67b4`, se compararon los dos JSON consolidados con los archivos anteriores: **todos los textos vigentes ES/EN, posiciones, etiquetas y asociaciones históricas Nomic permanecen idénticos**. Se ha ejecutado de nuevo `mvn clean verify`: 417 unitarios y 76 integraciones offline aprobados; 26 opcionales skipped. También pasan los 8 tests Python. La ejecución de los 21 tests del módulo Lambda indicada arriba pertenece a la validación anterior; no se ha repetido por este cambio de organización. No se repiten consultas reales, no se cambian resultados y no se modifica producción.

## Cómo usar las preguntas vigentes

Para las 30 preguntas por idioma, Java utiliza `EvaluationQuestions.cases("es")` / `cases("en")`; Python reutiliza `r4r_evaluation_cases.load_cases()`. No hay que sincronizar listas en otras clases. Los metadatos `evidence` y `coverage` no son respuestas ni resultados de la nueva redacción.

La captura de los veinte independientes está disponible en `RagIndependentQuestionsIT` y conserva resultados parciales de forma atómica. Se ejecuta solo con autorización explícita mediante:

```bash
mvn verify -Dit.test=RagIndependentQuestionsIT \
  -Dr4r.test.independent.enabled=true \
  -Dr4r.test.independent.output=target/independent-rag/phase1.json
```

Ese comando utiliza la configuración vigente y **no restringe por sí mismo el LLM al PC**. Para repetir la comparación con PC exclusivo se usa `NomicRetrievalDiagnostic --generalization-pc` descrito arriba, preparando un archivo de entrada desde `cases` (en lugar de `history.generalization`). Ninguna de estas ejecuciones reales se ha repetido durante la unificación.

## Registro completo de la captura anterior

Cada consulta conserva la pregunta evaluada, consulta efectiva de embedding, respuesta literal, abstención, citas y fragmentos finales. Los números son los finales; `caseId` mantiene la identidad inicial. En una respuesta S3 reutilizada se declara expresamente; en los tres contextos diferentes se muestran ambas respuestas. Para comparar ES/EN, cada par principal aparece seguido. Los diez adicionales solo tienen captura ES. Los saltos y espacios de las respuestas se conservan como cadenas JSON.

<details>
<summary>Abrir las 70 consultas con respuestas, citas y fragmentos PG/S3</summary>

## 01 — ES — case-01

Si quiero una orientación inicial sobre mi reforma, ¿qué detalles debería facilitar?

**Cobertura:** SUPPORTED. **Valoración:** CORRECTA.

FAQ-001 respalda los datos solicitados. EN conserva visita/medición como límite.

**Consulta enviada al embedding:** Si quiero una orientación inicial sobre mi reforma, ¿qué detalles debería facilitar?

**POSTGRES — respuesta:**

```json
"Para una orientación inicial sobre tu reforma, deberías facilitar ubicación, fotografías, medidas aproximadas, uso del espacio, problema, resultado esperado y plazo. [S1]"
```

Abstención: `false`.

Citas devueltas: [S1] `faqs/R4R_40_ALL_FAQ.md` ordinal 4 — Riansares 4R — todas las preguntas frecuentes / Presupuesto / FAQ-001 — ¿Qué información hace falta para una primera orientación?.

Fragmentos finales, en orden:

- [S1] `faqs/R4R_40_ALL_FAQ.md` ordinal 4 — Riansares 4R — todas las preguntas frecuentes / Presupuesto / FAQ-001 — ¿Qué información hace falta para una primera orientación?.
- [S2] `R4R_20_CASOS_OFICINAS_CUEVAS_BANOS_Y_FILTROS.md` ordinal 3 — Riansares 4R — casos de oficinas, cuevas, baños y filtros comerciales / 11. Caso F — Reforma de oficinas y aseos corporativos / 11.1. Contexto.
- [S3] `faqs/R4R_40_ALL_FAQ.md` ordinal 37 — Riansares 4R — todas las preguntas frecuentes / Casas antiguas / FAQ-030 — ¿Cuánto orienta una reforma integral de casa antigua?.
- [S4] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 14 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 7. Caso B — Vivienda antigua: rehabilitar sin demoler de más / 7.2. Principio: negociar con la estructura existente.
- [S5] `R4R_30_METODO_PRESUPUESTOS_SEO_CATALOGO_Y_PLANTILLAS.md` ordinal 35 — Riansares 4R — método, presupuestos, SEO, catálogo y plantillas / 21. Web y coherencia editorial / 21.1. Mensaje comprobado en portada.

**S3:** fragmentos idénticos en texto/orden/metadatos a PG; respuesta reutilizada explícitamente, sin segunda generación.

## 01 — EN — case-01

If I want initial guidance on my renovation, what details should I provide?

**Cobertura:** SUPPORTED. **Valoración:** CORRECTA.

FAQ-001 respalda los datos solicitados. EN conserva visita/medición como límite.

**Consulta enviada al embedding:** Si quiero una primera orientación sobre mi reforma, ¿qué detalles debo proporcionar?

**POSTGRES — respuesta:**

```json
"To receive initial guidance on your renovation, you should provide the following details: location, photographs, approximate measurements, space usage, problem description, expected outcome, and deadline. This information is required for the initial orientation, which does not replace a visit or measurement. [S1]"
```

Abstención: `false`.

Citas devueltas: [S1] `faqs/R4R_40_ALL_FAQ.md` ordinal 4 — Riansares 4R — todas las preguntas frecuentes / Presupuesto / FAQ-001 — ¿Qué información hace falta para una primera orientación?.

Fragmentos finales, en orden:

- [S1] `faqs/R4R_40_ALL_FAQ.md` ordinal 4 — Riansares 4R — todas las preguntas frecuentes / Presupuesto / FAQ-001 — ¿Qué información hace falta para una primera orientación?.
- [S2] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 17 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 7. Caso B — Vivienda antigua: rehabilitar sin demoler de más / 7.5. Baño pequeño.
- [S3] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 14 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 7. Caso B — Vivienda antigua: rehabilitar sin demoler de más / 7.2. Principio: negociar con la estructura existente.
- [S4] `R4R_30_METODO_PRESUPUESTOS_SEO_CATALOGO_Y_PLANTILLAS.md` ordinal 35 — Riansares 4R — método, presupuestos, SEO, catálogo y plantillas / 21. Web y coherencia editorial / 21.1. Mensaje comprobado en portada.
- [S5] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 19 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 7. Caso B — Vivienda antigua: rehabilitar sin demoler de más / 7.7. Instalaciones.

**S3:** fragmentos idénticos en texto/orden/metadatos a PG; respuesta reutilizada explícitamente, sin segunda generación.

## 02 — ES — case-02

En una obra, ¿en qué se distinguen la base de solera y el acabado de solado?

**Cobertura:** SUPPORTED. **Valoración:** CORRECTA.

Solera y solado diferenciados en R4R_10 §6.3/FAQ-041.

**Consulta enviada al embedding:** En una obra, ¿en qué se distinguen la base de solera y el acabado de solado?

**POSTGRES — respuesta:**

```json
"La base de solera se distingue del acabado de solado en que la solera es la base de hormigón o mortero estructurada para recibir el pavimento, con preparación, espesores, armadura, pendientes y juntas, mientras que el solado es la colocación del pavimento final sobre una base apta. Esta distinción está documentada en [S1] y [S2]."
```

Abstención: `false`.

Citas devueltas: [S1] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 5 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 6. Caso A — Patio, solera, pavimento y drenaje / 6.3. Distinción entre solera y solado; [S2] `faqs/R4R_40_ALL_FAQ.md` ordinal 51 — Riansares 4R — todas las preguntas frecuentes / Suelos / FAQ-041 — ¿Qué diferencia hay entre solera y solado?.

Fragmentos finales, en orden:

- [S1] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 5 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 6. Caso A — Patio, solera, pavimento y drenaje / 6.3. Distinción entre solera y solado.
- [S2] `faqs/R4R_40_ALL_FAQ.md` ordinal 51 — Riansares 4R — todas las preguntas frecuentes / Suelos / FAQ-041 — ¿Qué diferencia hay entre solera y solado?.
- [S3] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 8 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 6. Caso A — Patio, solera, pavimento y drenaje / 6.4. Hormigón de camión y ejecución manual / Patio interior o pasillo estrecho.
- [S4] `R4R_30_METODO_PRESUPUESTOS_SEO_CATALOGO_Y_PLANTILLAS.md` ordinal 25 — Riansares 4R — método, presupuestos, SEO, catálogo y plantillas / 19. SEO local y GEO / 19.3. Intenciones de búsqueda / Informacional.
- [S5] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 2 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 6. Caso A — Patio, solera, pavimento y drenaje.

**S3:** fragmentos idénticos en texto/orden/metadatos a PG; respuesta reutilizada explícitamente, sin segunda generación.

## 02 — EN — case-02

In construction, how does the concrete base differ from the finished flooring?

**Cobertura:** SUPPORTED. **Valoración:** CORRECTA.

Solera y solado diferenciados en R4R_10 §6.3/FAQ-041.

**Consulta enviada al embedding:** En construcción, ¿cómo difiere la base de concreto de la pavimentación terminada?

**POSTGRES — respuesta:**

```json
"In construction, the concrete base, referred to as **solera**, differs from the finished flooring, referred to as **solado**, in that the solera is the structural base made of concrete or mortar, designed to support the final flooring with specific preparation, thickness, reinforcement, slopes, and joints. The solado is the final placement of the flooring material over a suitable base. [S3]"
```

Abstención: `false`.

Citas devueltas: [S3] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 5 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 6. Caso A — Patio, solera, pavimento y drenaje / 6.3. Distinción entre solera y solado.

Fragmentos finales, en orden:

- [S1] `R4R_20_CASOS_OFICINAS_CUEVAS_BANOS_Y_FILTROS.md` ordinal 36 — Riansares 4R — casos de oficinas, cuevas, baños y filtros comerciales / 14. Caso I — Filtros comerciales: trabajo pequeño claro y obra grande fuera de capacidad / 14.5. Filtro antes de visitar.
- [S2] `R4R_30_METODO_PRESUPUESTOS_SEO_CATALOGO_Y_PLANTILLAS.md` ordinal 18 — Riansares 4R — método, presupuestos, SEO, catálogo y plantillas / 18. Comunicación con clientes / 18.2. Corrección de un error.
- [S3] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 5 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 6. Caso A — Patio, solera, pavimento y drenaje / 6.3. Distinción entre solera y solado.
- [S4] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 8 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 6. Caso A — Patio, solera, pavimento y drenaje / 6.4. Hormigón de camión y ejecución manual / Patio interior o pasillo estrecho.
- [S5] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 14 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 7. Caso B — Vivienda antigua: rehabilitar sin demoler de más / 7.2. Principio: negociar con la estructura existente.

**S3:** fragmentos idénticos en texto/orden/metadatos a PG; respuesta reutilizada explícitamente, sin segunda generación.

## 03 — ES — case-03

¿Por qué el acceso cambia el precio de una solera?

**Cobertura:** SUPPORTED. **Valoración:** CORRECTA.

FAQ-045 documenta aumento de jornadas/coordinación/riesgo por transporte manual.

**Consulta enviada al embedding:** ¿Por qué el acceso cambia el precio de una solera?

**POSTGRES — respuesta:**

```json
"El acceso cambia el precio de una solera porque la mezcla y transporte manual aumentan jornadas, coordinación y riesgo frente a descarga directa o bombeo. [S1]"
```

Abstención: `false`.

Citas devueltas: [S1] `faqs/R4R_40_ALL_FAQ.md` ordinal 58 — Riansares 4R — todas las preguntas frecuentes / Patios / FAQ-045 — ¿Por qué el acceso cambia el precio de una solera?.

Fragmentos finales, en orden:

- [S1] `faqs/R4R_40_ALL_FAQ.md` ordinal 58 — Riansares 4R — todas las preguntas frecuentes / Patios / FAQ-045 — ¿Por qué el acceso cambia el precio de una solera?.
- [S2] `faqs/R4R_40_ALL_FAQ.md` ordinal 51 — Riansares 4R — todas las preguntas frecuentes / Suelos / FAQ-041 — ¿Qué diferencia hay entre solera y solado?.
- [S3] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 2 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 6. Caso A — Patio, solera, pavimento y drenaje.

**S3:** fragmentos idénticos en texto/orden/metadatos a PG; respuesta reutilizada explícitamente, sin segunda generación.

## 03 — EN — case-03

Why does access affect the price of a concrete slab?

**Cobertura:** SUPPORTED. **Valoración:** CORRECTA.

FAQ-045 documenta aumento de jornadas/coordinación/riesgo por transporte manual.

**Consulta enviada al embedding:** ¿Por qué afecta el acceso al precio de una solera?

**POSTGRES — respuesta:**

```json
"The change in access affects the price of a concrete slab because manual mixing and transportation increase working days, coordination, and risk compared to direct discharge or pumping. [S1]"
```

Abstención: `false`.

Citas devueltas: [S1] `faqs/R4R_40_ALL_FAQ.md` ordinal 58 — Riansares 4R — todas las preguntas frecuentes / Patios / FAQ-045 — ¿Por qué el acceso cambia el precio de una solera?.

Fragmentos finales, en orden:

- [S1] `faqs/R4R_40_ALL_FAQ.md` ordinal 58 — Riansares 4R — todas las preguntas frecuentes / Patios / FAQ-045 — ¿Por qué el acceso cambia el precio de una solera?.
- [S2] `R4R_30_METODO_PRESUPUESTOS_SEO_CATALOGO_Y_PLANTILLAS.md` ordinal 7 — Riansares 4R — método, presupuestos, SEO, catálogo y plantillas / 15. Presupuestación con criterio / 15.5. Presupuesto preliminar y definitivo.
- [S3] `R4R_30_METODO_PRESUPUESTOS_SEO_CATALOGO_Y_PLANTILLAS.md` ordinal 19 — Riansares 4R — método, presupuestos, SEO, catálogo y plantillas / 18. Comunicación con clientes / 18.3. Cliente que aprieta precio.
- [S4] `R4R_30_METODO_PRESUPUESTOS_SEO_CATALOGO_Y_PLANTILLAS.md` ordinal 2 — Riansares 4R — método, presupuestos, SEO, catálogo y plantillas / 15. Presupuestación con criterio.

**S3:** fragmentos idénticos en texto/orden/metadatos a PG; respuesta reutilizada explícitamente, sin segunda generación.

## 04 — ES — case-04

¿Cuánto orienta una reforma integral de una casa antigua?

**Cobertura:** SUPPORTED. **Valoración:** PARCIAL.

Las cifras coinciden con FAQ-030, pero se presenta el rango sin aclarar que procede de una guía consultada y debe confirmarse para el alcance concreto. No se han validado precios actuales externos.

**Consulta enviada al embedding:** ¿Cuánto orienta una reforma integral de una casa antigua?

**POSTGRES — respuesta:**

```json
"Una reforma integral de una casa antigua puede oscilar entre 350–450 €/m² sin estructura ni redistribución, y puede elevarse a 533–600 €/m² o más si incluye estructura o cambios. [S1]"
```

Abstención: `false`.

Citas devueltas: [S1] `faqs/R4R_40_ALL_FAQ.md` ordinal 37 — Riansares 4R — todas las preguntas frecuentes / Casas antiguas / FAQ-030 — ¿Cuánto orienta una reforma integral de casa antigua?.

Fragmentos finales, en orden:

- [S1] `faqs/R4R_40_ALL_FAQ.md` ordinal 37 — Riansares 4R — todas las preguntas frecuentes / Casas antiguas / FAQ-030 — ¿Cuánto orienta una reforma integral de casa antigua?.
- [S2] `sources/R4R_70_WEB_SOURCE_MANIFEST.md` ordinal 9 — Fuentes web consultadas / SRC-HAB-ANTIGUA — Reforma integral de casa antigua.
- [S3] `faqs/R4R_40_ALL_FAQ.md` ordinal 35 — Riansares 4R — todas las preguntas frecuentes / Casas antiguas.
- [S4] `sources/R4R_70_WEB_SOURCE_MANIFEST.md` ordinal 8 — Fuentes web consultadas / SRC-HAB-VIVIENDA — ¿Cuánto cuesta reformar una casa? Precios 2026.
- [S5] `R4R_00_IDENTIDAD_CRITERIOS_Y_VOZ_EDITORIAL.md` ordinal 6 — Riansares 4R — identidad, criterios y voz editorial / 2. Identidad de Riansares 4R / 2.3. Servicios nucleares / Reformas.

**S3:** fragmentos idénticos en texto/orden/metadatos a PG; respuesta reutilizada explícitamente, sin segunda generación.

## 04 — EN — case-04

What is the approximate cost of a complete renovation of an old house?

**Cobertura:** SUPPORTED. **Valoración:** PARCIAL.

Las cifras coinciden con FAQ-030, pero se presenta el rango sin aclarar que procede de una guía consultada y debe confirmarse para el alcance concreto. No se han validado precios actuales externos.

**Consulta enviada al embedding:** ¿Cuál es el costo aproximado de una reforma completa de una vivienda antigua?

**POSTGRES — respuesta:**

```json
"The approximate cost of a complete renovation of an old house ranges from 350 to 450 €/m² without structural changes or redistribution, and can increase to 533–600 €/m² or more if structural work or significant changes are involved. [S4]"
```

Abstención: `false`.

Citas devueltas: [S4] `faqs/R4R_40_ALL_FAQ.md` ordinal 37 — Riansares 4R — todas las preguntas frecuentes / Casas antiguas / FAQ-030 — ¿Cuánto orienta una reforma integral de casa antigua?.

Fragmentos finales, en orden:

- [S1] `sources/R4R_70_WEB_SOURCE_MANIFEST.md` ordinal 8 — Fuentes web consultadas / SRC-HAB-VIVIENDA — ¿Cuánto cuesta reformar una casa? Precios 2026.
- [S2] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 14 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 7. Caso B — Vivienda antigua: rehabilitar sin demoler de más / 7.2. Principio: negociar con la estructura existente.
- [S3] `sources/R4R_70_WEB_SOURCE_MANIFEST.md` ordinal 9 — Fuentes web consultadas / SRC-HAB-ANTIGUA — Reforma integral de casa antigua.
- [S4] `faqs/R4R_40_ALL_FAQ.md` ordinal 37 — Riansares 4R — todas las preguntas frecuentes / Casas antiguas / FAQ-030 — ¿Cuánto orienta una reforma integral de casa antigua?.
- [S5] `R4R_30_METODO_PRESUPUESTOS_SEO_CATALOGO_Y_PLANTILLAS.md` ordinal 39 — Riansares 4R — método, presupuestos, SEO, catálogo y plantillas / 23. Clúster: casas antiguas y rehabilitación.

**S3:** fragmentos idénticos en texto/orden/metadatos a PG; respuesta reutilizada explícitamente, sin segunda generación.

## 05 — ES — case-05

¿Una primera orientación económica sustituye una visita técnica?

**Cobertura:** SUPPORTED. **Valoración:** CORRECTA.

FAQ-001/006 niegan que la orientación sustituya visita o medición.

**Consulta enviada al embedding:** ¿Una primera orientación económica sustituye una visita técnica?

**POSTGRES — respuesta:**

```json
"La orientación inicial no sustituye la visita ni medición, según [S1]. Por lo tanto, una primera orientación económica no sustituye una visita técnica."
```

Abstención: `false`.

Citas devueltas: [S1] `faqs/R4R_40_ALL_FAQ.md` ordinal 4 — Riansares 4R — todas las preguntas frecuentes / Presupuesto / FAQ-001 — ¿Qué información hace falta para una primera orientación?.

Fragmentos finales, en orden:

- [S1] `faqs/R4R_40_ALL_FAQ.md` ordinal 4 — Riansares 4R — todas las preguntas frecuentes / Presupuesto / FAQ-001 — ¿Qué información hace falta para una primera orientación?.
- [S2] `R4R_30_METODO_PRESUPUESTOS_SEO_CATALOGO_Y_PLANTILLAS.md` ordinal 11 — Riansares 4R — método, presupuestos, SEO, catálogo y plantillas / 16. Humedades: marco general / 16.1. Diagnóstico antes del producto.
- [S3] `faqs/R4R_40_ALL_FAQ.md` ordinal 9 — Riansares 4R — todas las preguntas frecuentes / Presupuesto / FAQ-006 — ¿Qué significa precio orientativo?.
- [S4] `R4R_20_CASOS_OFICINAS_CUEVAS_BANOS_Y_FILTROS.md` ordinal 30 — Riansares 4R — casos de oficinas, cuevas, baños y filtros comerciales / 13. Caso H — Dos baños contiguos en una vivienda antigua y estrecha / 13.11. Economía histórica y valoración del trabajo.
- [S5] `R4R_20_CASOS_OFICINAS_CUEVAS_BANOS_Y_FILTROS.md` ordinal 36 — Riansares 4R — casos de oficinas, cuevas, baños y filtros comerciales / 14. Caso I — Filtros comerciales: trabajo pequeño claro y obra grande fuera de capacidad / 14.5. Filtro antes de visitar.

**S3:** fragmentos idénticos en texto/orden/metadatos a PG; respuesta reutilizada explícitamente, sin segunda generación.

## 05 — EN — case-05

Does initial cost guidance replace a technical site visit?

**Cobertura:** SUPPORTED. **Valoración:** CORRECTA.

FAQ-001/006 niegan que la orientación sustituya visita o medición.

**Consulta enviada al embedding:** ¿La primera orientación sustituye una visita técnica al lugar?

**POSTGRES — respuesta:**

```json
"No, the initial cost guidance does not replace a technical site visit. The initial cost guidance is a reference that must be confirmed with a visit, measurement, location, access, qualities, and taxes, as stated in [S2]. The initial orientation also does not substitute for a visit or measurement, as noted in [S1]."
```

Abstención: `false`.

Citas devueltas: [S1] `faqs/R4R_40_ALL_FAQ.md` ordinal 4 — Riansares 4R — todas las preguntas frecuentes / Presupuesto / FAQ-001 — ¿Qué información hace falta para una primera orientación?; [S2] `faqs/R4R_40_ALL_FAQ.md` ordinal 9 — Riansares 4R — todas las preguntas frecuentes / Presupuesto / FAQ-006 — ¿Qué significa precio orientativo?.

Fragmentos finales, en orden:

- [S1] `faqs/R4R_40_ALL_FAQ.md` ordinal 4 — Riansares 4R — todas las preguntas frecuentes / Presupuesto / FAQ-001 — ¿Qué información hace falta para una primera orientación?.
- [S2] `faqs/R4R_40_ALL_FAQ.md` ordinal 9 — Riansares 4R — todas las preguntas frecuentes / Presupuesto / FAQ-006 — ¿Qué significa precio orientativo?.
- [S3] `R4R_30_METODO_PRESUPUESTOS_SEO_CATALOGO_Y_PLANTILLAS.md` ordinal 11 — Riansares 4R — método, presupuestos, SEO, catálogo y plantillas / 16. Humedades: marco general / 16.1. Diagnóstico antes del producto.
- [S4] `R4R_30_METODO_PRESUPUESTOS_SEO_CATALOGO_Y_PLANTILLAS.md` ordinal 8 — Riansares 4R — método, presupuestos, SEO, catálogo y plantillas / 15. Presupuestación con criterio / 15.5. Presupuesto preliminar y definitivo / Preliminar.

**S3:** fragmentos idénticos en texto/orden/metadatos a PG; respuesta reutilizada explícitamente, sin segunda generación.

## 06 — ES — case-06

¿Qué debe revisarse antes de pavimentar un patio?

**Cobertura:** SUPPORTED. **Valoración:** CORRECTA.

FAQ-046 enumera las comprobaciones de patio. EN contiene traducciones poco naturales, sin cambiar la lista esencial.

**Consulta enviada al embedding:** ¿Qué debe revisarse antes de pavimentar un patio?

**POSTGRES — respuesta:**

```json
"Antes de pavimentar un patio, debe revisarse la base, la compactación, el espesor, la armadura, los pendientes, las juntas, las arquetas, las canaletas, los encuentros y el acceso. [S1]"
```

Abstención: `false`.

Citas devueltas: [S1] `faqs/R4R_40_ALL_FAQ.md` ordinal 59 — Riansares 4R — todas las preguntas frecuentes / Patios / FAQ-046 — ¿Qué debe revisarse antes de pavimentar un patio?.

Fragmentos finales, en orden:

- [S1] `faqs/R4R_40_ALL_FAQ.md` ordinal 59 — Riansares 4R — todas las preguntas frecuentes / Patios / FAQ-046 — ¿Qué debe revisarse antes de pavimentar un patio?.
- [S2] `faqs/R4R_40_ALL_FAQ.md` ordinal 57 — Riansares 4R — todas las preguntas frecuentes / Patios.
- [S3] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 8 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 6. Caso A — Patio, solera, pavimento y drenaje / 6.4. Hormigón de camión y ejecución manual / Patio interior o pasillo estrecho.
- [S4] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 2 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 6. Caso A — Patio, solera, pavimento y drenaje.
- [S5] `R4R_00_IDENTIDAD_CRITERIOS_Y_VOZ_EDITORIAL.md` ordinal 8 — Riansares 4R — identidad, criterios y voz editorial / 2. Identidad de Riansares 4R / 2.3. Servicios nucleares / Revestimientos.

**S3:** fragmentos idénticos en texto/orden/metadatos a PG; respuesta reutilizada explícitamente, sin segunda generación.

## 06 — EN — case-06

What should be checked before paving a patio?

**Cobertura:** SUPPORTED. **Valoración:** CORRECTA.

FAQ-046 enumera las comprobaciones de patio. EN contiene traducciones poco naturales, sin cambiar la lista esencial.

**Consulta enviada al embedding:** ¿Qué se debe comprobar antes de pavimentar un patio?

**POSTGRES — respuesta:**

```json
"Before paving a patio, the following should be checked: base, compaction, thickness, reinforcement, slopes, joints, manholes, gutters, intersections, and access. This information is explicitly documented in [S1] and [S2]."
```

Abstención: `false`.

Citas devueltas: [S1] `faqs/R4R_40_ALL_FAQ.md` ordinal 59 — Riansares 4R — todas las preguntas frecuentes / Patios / FAQ-046 — ¿Qué debe revisarse antes de pavimentar un patio?; [S2] `faqs/R4R_40_ALL_FAQ.md` ordinal 57 — Riansares 4R — todas las preguntas frecuentes / Patios.

Fragmentos finales, en orden:

- [S1] `faqs/R4R_40_ALL_FAQ.md` ordinal 59 — Riansares 4R — todas las preguntas frecuentes / Patios / FAQ-046 — ¿Qué debe revisarse antes de pavimentar un patio?.
- [S2] `faqs/R4R_40_ALL_FAQ.md` ordinal 57 — Riansares 4R — todas las preguntas frecuentes / Patios.
- [S3] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 7 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 6. Caso A — Patio, solera, pavimento y drenaje / 6.4. Hormigón de camión y ejecución manual / Patio exterior con acceso.
- [S4] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 8 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 6. Caso A — Patio, solera, pavimento y drenaje / 6.4. Hormigón de camión y ejecución manual / Patio interior o pasillo estrecho.
- [S5] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 2 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 6. Caso A — Patio, solera, pavimento y drenaje.

**S3:** fragmentos idénticos en texto/orden/metadatos a PG; respuesta reutilizada explícitamente, sin segunda generación.

## 07 — ES — case-07

¿Cuándo conviene usar hormigón de camión en una solera?

**Cobertura:** SUPPORTED. **Valoración:** CORRECTA.

Ventajas de camión con acceso documentadas en R4R_10 §6.4. La traducción interna EN cambia ready-mix por hormigón armado: fallo de traducción aunque la respuesta final resulte pertinente.

**Consulta enviada al embedding:** ¿Cuándo conviene usar hormigón de camión en una solera?

**POSTGRES — respuesta:**

```json
"Cuando se ejecuta una solera en un patio exterior con acceso, conviene usar hormigón de camión debido a las ventajas como descarga cercana, ejecución rápida, mezcla homogénea, menos manipulación y menor riesgo de que el hormigón fragüe antes de extenderlo. [S3]"
```

Abstención: `false`.

Citas devueltas: [S3] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 7 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 6. Caso A — Patio, solera, pavimento y drenaje / 6.4. Hormigón de camión y ejecución manual / Patio exterior con acceso.

Fragmentos finales, en orden:

- [S1] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 6 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 6. Caso A — Patio, solera, pavimento y drenaje / 6.4. Hormigón de camión y ejecución manual.
- [S2] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 8 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 6. Caso A — Patio, solera, pavimento y drenaje / 6.4. Hormigón de camión y ejecución manual / Patio interior o pasillo estrecho.
- [S3] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 7 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 6. Caso A — Patio, solera, pavimento y drenaje / 6.4. Hormigón de camión y ejecución manual / Patio exterior con acceso.
- [S4] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 5 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 6. Caso A — Patio, solera, pavimento y drenaje / 6.3. Distinción entre solera y solado.
- [S5] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 2 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 6. Caso A — Patio, solera, pavimento y drenaje.

**S3:** fragmentos idénticos en texto/orden/metadatos a PG; respuesta reutilizada explícitamente, sin segunda generación.

## 07 — EN — case-07

When is it advisable to use ready-mix concrete for a slab?

**Cobertura:** SUPPORTED. **Valoración:** CORRECTA.

Ventajas de camión con acceso documentadas en R4R_10 §6.4. La traducción interna EN cambia ready-mix por hormigón armado: fallo de traducción aunque la respuesta final resulte pertinente.

**Consulta enviada al embedding:** ¿Cuándo es aconsejable utilizar hormigón armado para una solera?

**POSTGRES — respuesta:**

```json
"It is advisable to use ready-mix concrete for a slab when the access allows for direct unloading or pumping, as this reduces manual mixing and transportation, minimizing risks and labor days. This is supported by the advantages of using a truck for an outdoor patio with access, which include fewer manual handling tasks and a lower risk of concrete setting before placement. [S4]"
```

Abstención: `false`.

Citas devueltas: [S4] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 7 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 6. Caso A — Patio, solera, pavimento y drenaje / 6.4. Hormigón de camión y ejecución manual / Patio exterior con acceso.

Fragmentos finales, en orden:

- [S1] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 5 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 6. Caso A — Patio, solera, pavimento y drenaje / 6.3. Distinción entre solera y solado.
- [S2] `faqs/R4R_40_ALL_FAQ.md` ordinal 32 — Riansares 4R — todas las preguntas frecuentes / Humedades / FAQ-026 — ¿Se puede pegar placa de yeso a una pared húmeda?.
- [S3] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 2 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 6. Caso A — Patio, solera, pavimento y drenaje.
- [S4] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 7 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 6. Caso A — Patio, solera, pavimento y drenaje / 6.4. Hormigón de camión y ejecución manual / Patio exterior con acceso.
- [S5] `faqs/R4R_40_ALL_FAQ.md` ordinal 58 — Riansares 4R — todas las preguntas frecuentes / Patios / FAQ-045 — ¿Por qué el acceso cambia el precio de una solera?.

**S3:** fragmentos idénticos en texto/orden/metadatos a PG; respuesta reutilizada explícitamente, sin segunda generación.

## 08 — ES — case-08

¿Cómo afecta un pasillo estrecho a la ejecución de una solera?

**Cobertura:** SUPPORTED. **Valoración:** CORRECTA.

R4R_10 §6.4 describe acceso indirecto, carretillas y ejecución por fases.

**Consulta enviada al embedding:** ¿Cómo afecta un pasillo estrecho a la ejecución de una solera?

**POSTGRES — respuesta:**

```json
"Un pasillo estrecho afecta la ejecución de una solera debido al acceso indirecto y al transporte con carretillas, lo cual incrementa la dificultad en la obra. [S1]"
```

Abstención: `false`.

Citas devueltas: [S1] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 8 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 6. Caso A — Patio, solera, pavimento y drenaje / 6.4. Hormigón de camión y ejecución manual / Patio interior o pasillo estrecho.

Fragmentos finales, en orden:

- [S1] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 8 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 6. Caso A — Patio, solera, pavimento y drenaje / 6.4. Hormigón de camión y ejecución manual / Patio interior o pasillo estrecho.
- [S2] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 2 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 6. Caso A — Patio, solera, pavimento y drenaje.
- [S3] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 5 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 6. Caso A — Patio, solera, pavimento y drenaje / 6.3. Distinción entre solera y solado.
- [S4] `faqs/R4R_40_ALL_FAQ.md` ordinal 51 — Riansares 4R — todas las preguntas frecuentes / Suelos / FAQ-041 — ¿Qué diferencia hay entre solera y solado?.
- [S5] `faqs/R4R_40_ALL_FAQ.md` ordinal 58 — Riansares 4R — todas las preguntas frecuentes / Patios / FAQ-045 — ¿Por qué el acceso cambia el precio de una solera?.

**S3:** fragmentos idénticos en texto/orden/metadatos a PG; respuesta reutilizada explícitamente, sin segunda generación.

## 08 — EN — case-08

How does a narrow corridor affect the construction of a concrete slab?

**Cobertura:** SUPPORTED. **Valoración:** CORRECTA.

R4R_10 §6.4 describe acceso indirecto, carretillas y ejecución por fases.

**Consulta enviada al embedding:** ¿Cómo afecta un pasillo estrecho a la construcción de una solera?

**POSTGRES — respuesta:**

```json
"A narrow corridor affects the construction of a concrete slab by introducing indirect access, requiring the use of wheelbarrows for transportation, and causing mixing or phased pouring of materials. These factors increase the risk of poor execution if too much work is attempted at once. [S1]"
```

Abstención: `false`.

Citas devueltas: [S1] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 8 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 6. Caso A — Patio, solera, pavimento y drenaje / 6.4. Hormigón de camión y ejecución manual / Patio interior o pasillo estrecho.

Fragmentos finales, en orden:

- [S1] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 8 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 6. Caso A — Patio, solera, pavimento y drenaje / 6.4. Hormigón de camión y ejecución manual / Patio interior o pasillo estrecho.
- [S2] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 5 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 6. Caso A — Patio, solera, pavimento y drenaje / 6.3. Distinción entre solera y solado.
- [S3] `faqs/R4R_40_ALL_FAQ.md` ordinal 51 — Riansares 4R — todas las preguntas frecuentes / Suelos / FAQ-041 — ¿Qué diferencia hay entre solera y solado?.

**S3:** fragmentos idénticos en texto/orden/metadatos a PG; respuesta reutilizada explícitamente, sin segunda generación.

## 09 — ES — case-09

¿Qué causas de humedad pueden aparecer en una vivienda antigua?

**Cobertura:** SUPPORTED. **Valoración:** CORRECTA.

R4R_10 §7.3 documenta las causas; ES cita el padre ampliado que contiene esta subsección.

**Consulta enviada al embedding:** ¿Qué causas de humedad pueden aparecer en una vivienda antigua?

**POSTGRES — respuesta:**

```json
"Las causas de humedad que pueden aparecer en una vivienda antigua incluyen humedad ascendente, humedad lateral por terreno o roca, condensación, filtraciones antiguas, fugas y sales acumuladas. [S3]"
```

Abstención: `false`.

Citas devueltas: [S3] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 12 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 7. Caso B — Vivienda antigua: rehabilitar sin demoler de más.

Fragmentos finales, en orden:

- [S1] `R4R_00_IDENTIDAD_CRITERIOS_Y_VOZ_EDITORIAL.md` ordinal 19 — Riansares 4R — identidad, criterios y voz editorial / 4. Reglas de factualidad para el LLM / 4.3. Humedades.
- [S2] `R4R_20_CASOS_OFICINAS_CUEVAS_BANOS_Y_FILTROS.md` ordinal 19 — Riansares 4R — casos de oficinas, cuevas, baños y filtros comerciales / 13. Caso H — Dos baños contiguos en una vivienda antigua y estrecha.
- [S3] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 12 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 7. Caso B — Vivienda antigua: rehabilitar sin demoler de más.
- [S4] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 15 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 7. Caso B — Vivienda antigua: rehabilitar sin demoler de más / 7.3. Humedad en muros antiguos.
- [S5] `faqs/R4R_40_ALL_FAQ.md` ordinal 35 — Riansares 4R — todas las preguntas frecuentes / Casas antiguas.

**S3:** fragmentos idénticos en texto/orden/metadatos a PG; respuesta reutilizada explícitamente, sin segunda generación.

## 09 — EN — case-09

What causes of damp can occur in an old home?

**Cobertura:** SUPPORTED. **Valoración:** CORRECTA.

R4R_10 §7.3 documenta las causas; ES cita el padre ampliado que contiene esta subsección.

**Consulta enviada al embedding:** ¿Cuáles son las causas de humedad que pueden ocurrir en una vivienda antigua?

**POSTGRES — respuesta:**

```json
"In old homes, damp can occur due to several causes, including humidity ascending, lateral humidity from the ground or rock, condensation, old leaks, leaks, and salt accumulation. These causes can coexist, as documented in the evidence. [S5]"
```

Abstención: `false`.

Citas devueltas: [S5] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 15 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 7. Caso B — Vivienda antigua: rehabilitar sin demoler de más / 7.3. Humedad en muros antiguos.

Fragmentos finales, en orden:

- [S1] `R4R_00_IDENTIDAD_CRITERIOS_Y_VOZ_EDITORIAL.md` ordinal 19 — Riansares 4R — identidad, criterios y voz editorial / 4. Reglas de factualidad para el LLM / 4.3. Humedades.
- [S2] `R4R_20_CASOS_OFICINAS_CUEVAS_BANOS_Y_FILTROS.md` ordinal 19 — Riansares 4R — casos de oficinas, cuevas, baños y filtros comerciales / 13. Caso H — Dos baños contiguos en una vivienda antigua y estrecha.
- [S3] `faqs/R4R_40_ALL_FAQ.md` ordinal 35 — Riansares 4R — todas las preguntas frecuentes / Casas antiguas.
- [S4] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 12 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 7. Caso B — Vivienda antigua: rehabilitar sin demoler de más.
- [S5] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 15 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 7. Caso B — Vivienda antigua: rehabilitar sin demoler de más / 7.3. Humedad en muros antiguos.

**S3:** fragmentos idénticos en texto/orden/metadatos a PG; respuesta reutilizada explícitamente, sin segunda generación.

## 10 — ES — case-10

¿Debe incluir el presupuesto de una reforma los plazos de ejecución?

**Cobertura:** SUPPORTED. **Valoración:** CORRECTA.

ES cita FAQ-003 correctamente. EN se abstiene pese a recuperar FAQ-003 con plazos explícitos: fallo de generación/validación, no ausencia de documento. PG/S3 solo intercambian puestos 2 y 3.

**Consulta enviada al embedding:** ¿Debe incluir el presupuesto de una reforma los plazos de ejecución?

**POSTGRES — respuesta:**

```json
"Sí, el presupuesto de una reforma debe incluir los plazos de ejecución. Esto se menciona en [S1], donde se indica que los plazos deben formar parte del contenido del presupuesto."
```

Abstención: `false`.

Citas devueltas: [S1] `faqs/R4R_40_ALL_FAQ.md` ordinal 6 — Riansares 4R — todas las preguntas frecuentes / Presupuesto / FAQ-003 — ¿Qué debe contener un presupuesto?.

Fragmentos finales, en orden:

- [S1] `faqs/R4R_40_ALL_FAQ.md` ordinal 6 — Riansares 4R — todas las preguntas frecuentes / Presupuesto / FAQ-003 — ¿Qué debe contener un presupuesto?.
- [S2] `R4R_30_METODO_PRESUPUESTOS_SEO_CATALOGO_Y_PLANTILLAS.md` ordinal 2 — Riansares 4R — método, presupuestos, SEO, catálogo y plantillas / 15. Presupuestación con criterio.
- [S3] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 19 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 7. Caso B — Vivienda antigua: rehabilitar sin demoler de más / 7.7. Instalaciones.
- [S4] `R4R_20_CASOS_OFICINAS_CUEVAS_BANOS_Y_FILTROS.md` ordinal 2 — Riansares 4R — casos de oficinas, cuevas, baños y filtros comerciales / 11. Caso F — Reforma de oficinas y aseos corporativos.
- [S5] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 10 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 6. Caso A — Patio, solera, pavimento y drenaje / 6.6. Error de presupuesto corregido.

**S3:** fragmentos idénticos en texto/orden/metadatos a PG; respuesta reutilizada explícitamente, sin segunda generación.

## 10 — EN — case-10

Should a renovation estimate include the execution timeline?

**Cobertura:** SUPPORTED. **Valoración:** ABSTENCIÓN_INNECESARIA.

ES cita FAQ-003 correctamente. EN se abstiene pese a recuperar FAQ-003 con plazos explícitos: fallo de generación/validación, no ausencia de documento. PG/S3 solo intercambian puestos 2 y 3.

**Consulta enviada al embedding:** ¿Debería incluir un presupuesto de reformas el cronograma de ejecución?

**POSTGRES — respuesta:**

```json
""
```

Abstención: `true`.

Citas devueltas: ninguna.

Fragmentos finales, en orden:

- [S1] `R4R_30_METODO_PRESUPUESTOS_SEO_CATALOGO_Y_PLANTILLAS.md` ordinal 2 — Riansares 4R — método, presupuestos, SEO, catálogo y plantillas / 15. Presupuestación con criterio.
- [S2] `faqs/R4R_40_ALL_FAQ.md` ordinal 6 — Riansares 4R — todas las preguntas frecuentes / Presupuesto / FAQ-003 — ¿Qué debe contener un presupuesto?.
- [S3] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 10 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 6. Caso A — Patio, solera, pavimento y drenaje / 6.6. Error de presupuesto corregido.
- [S4] `R4R_20_CASOS_OFICINAS_CUEVAS_BANOS_Y_FILTROS.md` ordinal 34 — Riansares 4R — casos de oficinas, cuevas, baños y filtros comerciales / 14. Caso I — Filtros comerciales: trabajo pequeño claro y obra grande fuera de capacidad / 14.3. Presupuesto “ciego” para hostal o bar.
- [S5] `faqs/R4R_40_ALL_FAQ.md` ordinal 7 — Riansares 4R — todas las preguntas frecuentes / Presupuesto / FAQ-004 — ¿Es mejor un presupuesto por partidas?.

**S3 — respuesta:**

```json
""
```

Abstención: `true`.

Citas devueltas: ninguna.

Fragmentos finales, en orden:

- [S1] `R4R_30_METODO_PRESUPUESTOS_SEO_CATALOGO_Y_PLANTILLAS.md` ordinal 2 — Riansares 4R — método, presupuestos, SEO, catálogo y plantillas / 15. Presupuestación con criterio.
- [S2] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 10 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 6. Caso A — Patio, solera, pavimento y drenaje / 6.6. Error de presupuesto corregido.
- [S3] `faqs/R4R_40_ALL_FAQ.md` ordinal 6 — Riansares 4R — todas las preguntas frecuentes / Presupuesto / FAQ-003 — ¿Qué debe contener un presupuesto?.
- [S4] `R4R_20_CASOS_OFICINAS_CUEVAS_BANOS_Y_FILTROS.md` ordinal 34 — Riansares 4R — casos de oficinas, cuevas, baños y filtros comerciales / 14. Caso I — Filtros comerciales: trabajo pequeño claro y obra grande fuera de capacidad / 14.3. Presupuesto “ciego” para hostal o bar.
- [S5] `faqs/R4R_40_ALL_FAQ.md` ordinal 7 — Riansares 4R — todas las preguntas frecuentes / Presupuesto / FAQ-004 — ¿Es mejor un presupuesto por partidas?.

## 11 — ES — case-11

¿Qué datos debe aportar el cliente para solicitar un presupuesto?

**Cobertura:** SUPPORTED. **Valoración:** CORRECTA.

FAQ-001 o padre Presupuesto ampliado respaldan la lista de datos.

**Consulta enviada al embedding:** ¿Qué datos debe aportar el cliente para solicitar un presupuesto?

**POSTGRES — respuesta:**

```json
"El cliente debe aportar ubicación, fotografías, medidas aproximadas, uso del espacio, problema, resultado esperado y plazo. [S3]"
```

Abstención: `false`.

Citas devueltas: [S3] `faqs/R4R_40_ALL_FAQ.md` ordinal 3 — Riansares 4R — todas las preguntas frecuentes / Presupuesto.

Fragmentos finales, en orden:

- [S1] `R4R_30_METODO_PRESUPUESTOS_SEO_CATALOGO_Y_PLANTILLAS.md` ordinal 19 — Riansares 4R — método, presupuestos, SEO, catálogo y plantillas / 18. Comunicación con clientes / 18.3. Cliente que aprieta precio.
- [S2] `R4R_30_METODO_PRESUPUESTOS_SEO_CATALOGO_Y_PLANTILLAS.md` ordinal 14 — Riansares 4R — método, presupuestos, SEO, catálogo y plantillas / 16. Humedades: marco general / 16.4. Contenido útil para clientes.
- [S3] `faqs/R4R_40_ALL_FAQ.md` ordinal 3 — Riansares 4R — todas las preguntas frecuentes / Presupuesto.
- [S4] `R4R_30_METODO_PRESUPUESTOS_SEO_CATALOGO_Y_PLANTILLAS.md` ordinal 2 — Riansares 4R — método, presupuestos, SEO, catálogo y plantillas / 15. Presupuestación con criterio.
- [S5] `R4R_30_METODO_PRESUPUESTOS_SEO_CATALOGO_Y_PLANTILLAS.md` ordinal 46 — Riansares 4R — método, presupuestos, SEO, catálogo y plantillas / 30. Prompt interno para caso práctico.

**S3:** fragmentos idénticos en texto/orden/metadatos a PG; respuesta reutilizada explícitamente, sin segunda generación.

## 11 — EN — case-11

What information should a customer provide to request an estimate?

**Cobertura:** SUPPORTED. **Valoración:** CORRECTA.

FAQ-001 o padre Presupuesto ampliado respaldan la lista de datos.

**Consulta enviada al embedding:** ¿Qué información debe proporcionar un cliente para solicitar un presupuesto?

**POSTGRES — respuesta:**

```json
"To request an estimate, a customer should provide location, photographs, approximate measurements, space usage, problem description, expected result, and deadline. This information is necessary for an initial orientation, which does not replace a visit or measurement. [S1]"
```

Abstención: `false`.

Citas devueltas: [S1] `faqs/R4R_40_ALL_FAQ.md` ordinal 3 — Riansares 4R — todas las preguntas frecuentes / Presupuesto.

Fragmentos finales, en orden:

- [S1] `faqs/R4R_40_ALL_FAQ.md` ordinal 3 — Riansares 4R — todas las preguntas frecuentes / Presupuesto.
- [S2] `R4R_30_METODO_PRESUPUESTOS_SEO_CATALOGO_Y_PLANTILLAS.md` ordinal 19 — Riansares 4R — método, presupuestos, SEO, catálogo y plantillas / 18. Comunicación con clientes / 18.3. Cliente que aprieta precio.
- [S3] `R4R_30_METODO_PRESUPUESTOS_SEO_CATALOGO_Y_PLANTILLAS.md` ordinal 2 — Riansares 4R — método, presupuestos, SEO, catálogo y plantillas / 15. Presupuestación con criterio.
- [S4] `R4R_30_METODO_PRESUPUESTOS_SEO_CATALOGO_Y_PLANTILLAS.md` ordinal 16 — Riansares 4R — método, presupuestos, SEO, catálogo y plantillas / 18. Comunicación con clientes.
- [S5] `R4R_30_METODO_PRESUPUESTOS_SEO_CATALOGO_Y_PLANTILLAS.md` ordinal 14 — Riansares 4R — método, presupuestos, SEO, catálogo y plantillas / 16. Humedades: marco general / 16.4. Contenido útil para clientes.

**S3:** fragmentos idénticos en texto/orden/metadatos a PG; respuesta reutilizada explícitamente, sin segunda generación.

## 12 — ES — case-12

¿Qué datos hacen falta para orientar una reforma de baño?

**Cobertura:** SUPPORTED. **Valoración:** CORRECTA.

ES usa FAQ-001 y datos para croquis; EN recoge contenido de presupuesto (FAQ-003) y rango de baño (FAQ-031). Respuestas distintas pero documentadas; EN ofrece menos datos específicos del cliente.

**Consulta enviada al embedding:** ¿Qué datos hacen falta para orientar una reforma de baño?

**POSTGRES — respuesta:**

```json
"Para orientar una reforma de baño, se requieren datos como ubicación, fotografías, medidas aproximadas, uso del espacio, problema, resultado esperado y plazo, según [S2]. Además, se deben recoger datos específicos como el nombre de cada estancia, orientación estable, longitud de paredes, espesor de muros, puertas, ventanas, altura libre, escalones y instalaciones visibles, según [S5]."
```

Abstención: `false`.

Citas devueltas: [S2] `faqs/R4R_40_ALL_FAQ.md` ordinal 4 — Riansares 4R — todas las preguntas frecuentes / Presupuesto / FAQ-001 — ¿Qué información hace falta para una primera orientación?; [S5] `R4R_30_METODO_PRESUPUESTOS_SEO_CATALOGO_Y_PLANTILLAS.md` ordinal 30 — Riansares 4R — método, presupuestos, SEO, catálogo y plantillas / 20. Mediciones y croquis para reconstruir planos / Datos que deben recogerse.

Fragmentos finales, en orden:

- [S1] `faqs/R4R_40_ALL_FAQ.md` ordinal 39 — Riansares 4R — todas las preguntas frecuentes / Baños / FAQ-031 — ¿Cuánto cuesta reformar un baño?.
- [S2] `faqs/R4R_40_ALL_FAQ.md` ordinal 4 — Riansares 4R — todas las preguntas frecuentes / Presupuesto / FAQ-001 — ¿Qué información hace falta para una primera orientación?.
- [S3] `sources/R4R_70_WEB_SOURCE_MANIFEST.md` ordinal 11 — Fuentes web consultadas / SRC-HAB-BANO — ¿Cuánto cuesta reformar un baño? Precios 2026.
- [S4] `R4R_30_METODO_PRESUPUESTOS_SEO_CATALOGO_Y_PLANTILLAS.md` ordinal 41 — Riansares 4R — método, presupuestos, SEO, catálogo y plantillas / 25. Clúster: baños y fontanería.
- [S5] `R4R_30_METODO_PRESUPUESTOS_SEO_CATALOGO_Y_PLANTILLAS.md` ordinal 30 — Riansares 4R — método, presupuestos, SEO, catálogo y plantillas / 20. Mediciones y croquis para reconstruir planos / Datos que deben recogerse.

**S3:** fragmentos idénticos en texto/orden/metadatos a PG; respuesta reutilizada explícitamente, sin segunda generación.

## 12 — EN — case-12

What information is needed to estimate a bathroom renovation?

**Cobertura:** SUPPORTED. **Valoración:** CORRECTA.

ES usa FAQ-001 y datos para croquis; EN recoge contenido de presupuesto (FAQ-003) y rango de baño (FAQ-031). Respuestas distintas pero documentadas; EN ofrece menos datos específicos del cliente.

**Consulta enviada al embedding:** ¿Qué información se necesita para elaborar un presupuesto de reformas de un baño?

**POSTGRES — respuesta:**

```json
"To estimate a bathroom renovation, the information needed includes the scope, measurements, materials and qualities, labor, waste, taxes, timelines, payments, exclusions, extras, and warranty. This is based on the requirements for a budget as outlined in [S3]. Additionally, the cost range for bathroom renovations varies depending on the size and quality, with examples ranging from 1.560 € for 2 m² to 4.860 € for 6 m² of high quality, as documented in [S2]."
```

Abstención: `false`.

Citas devueltas: [S2] `faqs/R4R_40_ALL_FAQ.md` ordinal 39 — Riansares 4R — todas las preguntas frecuentes / Baños / FAQ-031 — ¿Cuánto cuesta reformar un baño?; [S3] `faqs/R4R_40_ALL_FAQ.md` ordinal 6 — Riansares 4R — todas las preguntas frecuentes / Presupuesto / FAQ-003 — ¿Qué debe contener un presupuesto?.

Fragmentos finales, en orden:

- [S1] `sources/R4R_70_WEB_SOURCE_MANIFEST.md` ordinal 11 — Fuentes web consultadas / SRC-HAB-BANO — ¿Cuánto cuesta reformar un baño? Precios 2026.
- [S2] `faqs/R4R_40_ALL_FAQ.md` ordinal 39 — Riansares 4R — todas las preguntas frecuentes / Baños / FAQ-031 — ¿Cuánto cuesta reformar un baño?.
- [S3] `faqs/R4R_40_ALL_FAQ.md` ordinal 6 — Riansares 4R — todas las preguntas frecuentes / Presupuesto / FAQ-003 — ¿Qué debe contener un presupuesto?.
- [S4] `R4R_00_IDENTIDAD_CRITERIOS_Y_VOZ_EDITORIAL.md` ordinal 6 — Riansares 4R — identidad, criterios y voz editorial / 2. Identidad de Riansares 4R / 2.3. Servicios nucleares / Reformas.
- [S5] `faqs/R4R_40_ALL_FAQ.md` ordinal 7 — Riansares 4R — todas las preguntas frecuentes / Presupuesto / FAQ-004 — ¿Es mejor un presupuesto por partidas?.

**S3:** fragmentos idénticos en texto/orden/metadatos a PG; respuesta reutilizada explícitamente, sin segunda generación.

## 13 — ES — case-13

¿Qué tipos de reformas realiza esta empresa? ¿Trabaja en viviendas y oficinas?

**Cobertura:** SUPPORTED. **Valoración:** CORRECTA.

Servicios de R4R_00 §2.3 y Caso F respaldan viviendas/oficinas.

**Consulta enviada al embedding:** ¿Qué tipos de reformas realiza esta empresa? ¿Trabaja en viviendas y oficinas?

**POSTGRES — respuesta:**

```json
"La empresa realiza reformas de baños, cocinas, viviendas, pequeñas oficinas y locales, según [S1]. También trabaja en reformas de oficinas y aseos corporativos, como se menciona en [S2]."
```

Abstención: `false`.

Citas devueltas: [S1] `R4R_00_IDENTIDAD_CRITERIOS_Y_VOZ_EDITORIAL.md` ordinal 6 — Riansares 4R — identidad, criterios y voz editorial / 2. Identidad de Riansares 4R / 2.3. Servicios nucleares / Reformas; [S2] `R4R_20_CASOS_OFICINAS_CUEVAS_BANOS_Y_FILTROS.md` ordinal 2 — Riansares 4R — casos de oficinas, cuevas, baños y filtros comerciales / 11. Caso F — Reforma de oficinas y aseos corporativos.

Fragmentos finales, en orden:

- [S1] `R4R_00_IDENTIDAD_CRITERIOS_Y_VOZ_EDITORIAL.md` ordinal 6 — Riansares 4R — identidad, criterios y voz editorial / 2. Identidad de Riansares 4R / 2.3. Servicios nucleares / Reformas.
- [S2] `R4R_20_CASOS_OFICINAS_CUEVAS_BANOS_Y_FILTROS.md` ordinal 2 — Riansares 4R — casos de oficinas, cuevas, baños y filtros comerciales / 11. Caso F — Reforma de oficinas y aseos corporativos.
- [S3] `R4R_30_METODO_PRESUPUESTOS_SEO_CATALOGO_Y_PLANTILLAS.md` ordinal 35 — Riansares 4R — método, presupuestos, SEO, catálogo y plantillas / 21. Web y coherencia editorial / 21.1. Mensaje comprobado en portada.
- [S4] `R4R_20_CASOS_OFICINAS_CUEVAS_BANOS_Y_FILTROS.md` ordinal 3 — Riansares 4R — casos de oficinas, cuevas, baños y filtros comerciales / 11. Caso F — Reforma de oficinas y aseos corporativos / 11.1. Contexto.
- [S5] `sources/R4R_70_WEB_SOURCE_MANIFEST.md` ordinal 8 — Fuentes web consultadas / SRC-HAB-VIVIENDA — ¿Cuánto cuesta reformar una casa? Precios 2026.

**S3:** fragmentos idénticos en texto/orden/metadatos a PG; respuesta reutilizada explícitamente, sin segunda generación.

## 13 — EN — case-13

What kinds of renovation work does this company do? Does it work on houses and offices?

**Cobertura:** SUPPORTED. **Valoración:** CORRECTA.

Servicios de R4R_00 §2.3 y Caso F respaldan viviendas/oficinas.

**Consulta enviada al embedding:** ¿Qué tipos de reformas realiza esta empresa? ¿Trabaja en viviendas y oficinas?

**POSTGRES — respuesta:**

```json
"The company performs renovations including bathrooms, kitchens, homes, small offices and shops, and habitability improvements. It also handles partial or full renovation of finishes and installations. The company works on houses and offices, as evidenced by projects involving corporate office renovations and residential home renovations. [S1], [S2], [S3]"
```

Abstención: `false`.

Citas devueltas: [S1] `R4R_00_IDENTIDAD_CRITERIOS_Y_VOZ_EDITORIAL.md` ordinal 6 — Riansares 4R — identidad, criterios y voz editorial / 2. Identidad de Riansares 4R / 2.3. Servicios nucleares / Reformas; [S2] `R4R_20_CASOS_OFICINAS_CUEVAS_BANOS_Y_FILTROS.md` ordinal 2 — Riansares 4R — casos de oficinas, cuevas, baños y filtros comerciales / 11. Caso F — Reforma de oficinas y aseos corporativos; [S3] `R4R_30_METODO_PRESUPUESTOS_SEO_CATALOGO_Y_PLANTILLAS.md` ordinal 35 — Riansares 4R — método, presupuestos, SEO, catálogo y plantillas / 21. Web y coherencia editorial / 21.1. Mensaje comprobado en portada.

Fragmentos finales, en orden:

- [S1] `R4R_00_IDENTIDAD_CRITERIOS_Y_VOZ_EDITORIAL.md` ordinal 6 — Riansares 4R — identidad, criterios y voz editorial / 2. Identidad de Riansares 4R / 2.3. Servicios nucleares / Reformas.
- [S2] `R4R_20_CASOS_OFICINAS_CUEVAS_BANOS_Y_FILTROS.md` ordinal 2 — Riansares 4R — casos de oficinas, cuevas, baños y filtros comerciales / 11. Caso F — Reforma de oficinas y aseos corporativos.
- [S3] `R4R_30_METODO_PRESUPUESTOS_SEO_CATALOGO_Y_PLANTILLAS.md` ordinal 35 — Riansares 4R — método, presupuestos, SEO, catálogo y plantillas / 21. Web y coherencia editorial / 21.1. Mensaje comprobado en portada.
- [S4] `R4R_20_CASOS_OFICINAS_CUEVAS_BANOS_Y_FILTROS.md` ordinal 3 — Riansares 4R — casos de oficinas, cuevas, baños y filtros comerciales / 11. Caso F — Reforma de oficinas y aseos corporativos / 11.1. Contexto.
- [S5] `sources/R4R_70_WEB_SOURCE_MANIFEST.md` ordinal 8 — Fuentes web consultadas / SRC-HAB-VIVIENDA — ¿Cuánto cuesta reformar una casa? Precios 2026.

**S3:** fragmentos idénticos en texto/orden/metadatos a PG; respuesta reutilizada explícitamente, sin segunda generación.

## 14 — ES — case-14

¿Qué diferencias hay entre la cal aérea y la cal hidráulica natural para trabajar sobre muros antiguos?

**Cobertura:** SUPPORTED. **Valoración:** CORRECTA.

R4R_20 §12.2 distingue cal aérea y NHL. ES es breve y omite parte de diferencias, sin añadir un método nuevo.

**Consulta enviada al embedding:** ¿Qué diferencias hay entre la cal aérea y la cal hidráulica natural para trabajar sobre muros antiguos?

**POSTGRES — respuesta:**

```json
"La cal aérea es menos apropiada para cargas gruesas sobre soporte húmedo o débil, mientras que la cal hidráulica natural NHL 2 o NHL 3.5 es técnicamente más compatible con piedra, tierra y fábricas antiguas. [S1]"
```

Abstención: `false`.

Citas devueltas: [S1] `R4R_20_CASOS_OFICINAS_CUEVAS_BANOS_Y_FILTROS.md` ordinal 12 — Riansares 4R — casos de oficinas, cuevas, baños y filtros comerciales / 12. Caso G — Cueva o bodega antigua: estabilización con mortero de cal y malla / 12.2. Cal aérea, NHL y morteros híbridos.

Fragmentos finales, en orden:

- [S1] `R4R_20_CASOS_OFICINAS_CUEVAS_BANOS_Y_FILTROS.md` ordinal 12 — Riansares 4R — casos de oficinas, cuevas, baños y filtros comerciales / 12. Caso G — Cueva o bodega antigua: estabilización con mortero de cal y malla / 12.2. Cal aérea, NHL y morteros híbridos.
- [S2] `R4R_20_CASOS_OFICINAS_CUEVAS_BANOS_Y_FILTROS.md` ordinal 26 — Riansares 4R — casos de oficinas, cuevas, baños y filtros comerciales / 13. Caso H — Dos baños contiguos en una vivienda antigua y estrecha / 13.7. Placa en ducha y mortero de cal en zona seca.
- [S3] `R4R_20_CASOS_OFICINAS_CUEVAS_BANOS_Y_FILTROS.md` ordinal 33 — Riansares 4R — casos de oficinas, cuevas, baños y filtros comerciales / 14. Caso I — Filtros comerciales: trabajo pequeño claro y obra grande fuera de capacidad / 14.2. Trabajo pequeño: jornada frente a tarifa horaria abstracta.

**S3:** fragmentos idénticos en texto/orden/metadatos a PG; respuesta reutilizada explícitamente, sin segunda generación.

## 14 — EN — case-14

What are the differences between air lime and natural hydraulic lime when working on old walls?

**Cobertura:** SUPPORTED. **Valoración:** CORRECTA.

R4R_20 §12.2 distingue cal aérea y NHL. ES es breve y omite parte de diferencias, sin añadir un método nuevo.

**Consulta enviada al embedding:** ¿Cuáles son las diferencias entre la cal aerea y la cal hidráulica natural cuando se trabaja en viejas paredes?

**POSTGRES — respuesta:**

```json
"Air lime is described as very breathable and traditional, but slow to set and less suitable for heavy loads over wet or weak supports, according to [S1]. Natural hydraulic lime (NHL 2 or NHL 3.5) is technically more compatible with stone, earth, and old buildings, and it sets with ambient humidity while maintaining moderate rigidity, as stated in [S1]."
```

Abstención: `false`.

Citas devueltas: [S1] `R4R_20_CASOS_OFICINAS_CUEVAS_BANOS_Y_FILTROS.md` ordinal 12 — Riansares 4R — casos de oficinas, cuevas, baños y filtros comerciales / 12. Caso G — Cueva o bodega antigua: estabilización con mortero de cal y malla / 12.2. Cal aérea, NHL y morteros híbridos.

Fragmentos finales, en orden:

- [S1] `R4R_20_CASOS_OFICINAS_CUEVAS_BANOS_Y_FILTROS.md` ordinal 12 — Riansares 4R — casos de oficinas, cuevas, baños y filtros comerciales / 12. Caso G — Cueva o bodega antigua: estabilización con mortero de cal y malla / 12.2. Cal aérea, NHL y morteros híbridos.
- [S2] `R4R_00_IDENTIDAD_CRITERIOS_Y_VOZ_EDITORIAL.md` ordinal 19 — Riansares 4R — identidad, criterios y voz editorial / 4. Reglas de factualidad para el LLM / 4.3. Humedades.
- [S3] `R4R_20_CASOS_OFICINAS_CUEVAS_BANOS_Y_FILTROS.md` ordinal 26 — Riansares 4R — casos de oficinas, cuevas, baños y filtros comerciales / 13. Caso H — Dos baños contiguos en una vivienda antigua y estrecha / 13.7. Placa en ducha y mortero de cal en zona seca.
- [S4] `R4R_20_CASOS_OFICINAS_CUEVAS_BANOS_Y_FILTROS.md` ordinal 33 — Riansares 4R — casos de oficinas, cuevas, baños y filtros comerciales / 14. Caso I — Filtros comerciales: trabajo pequeño claro y obra grande fuera de capacidad / 14.2. Trabajo pequeño: jornada frente a tarifa horaria abstracta.
- [S5] `faqs/R4R_40_ALL_FAQ.md` ordinal 62 — Riansares 4R — todas las preguntas frecuentes / Instalaciones / FAQ-048 — ¿Debe intervenir un profesional habilitado?.

**S3:** fragmentos idénticos en texto/orden/metadatos a PG; respuesta reutilizada explícitamente, sin segunda generación.

## 15 — ES — case-15

¿Qué precauciones hay que tomar antes de colocar baldosa nueva sobre un pavimento existente?

**Cobertura:** SUPPORTED. **Valoración:** CORRECTA.

FAQ-040 y R4R_10 §7.8 respaldan firmeza/limpieza/planitud/compatibilidad. EN añade comprobaciones de vinilo presentes en la fuente pero accesorias para esta pregunta.

**Consulta enviada al embedding:** ¿Qué precauciones hay que tomar antes de colocar baldosa nueva sobre un pavimento existente?

**POSTGRES — respuesta:**

```json
"Antes de colocar baldosa nueva sobre un pavimento existente, se debe revisar el soporte para que esté firme, limpio, plano y adherido, según [S2]. Además, se deben comprobar la humedad, las cotas, las puertas y las piezas huecas, según [S2]."
```

Abstención: `false`.

Citas devueltas: [S2] `faqs/R4R_40_ALL_FAQ.md` ordinal 50 — Riansares 4R — todas las preguntas frecuentes / Suelos / FAQ-040 — ¿Se puede colocar baldosa sobre baldosa?.

Fragmentos finales, en orden:

- [S1] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 20 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 7. Caso B — Vivienda antigua: rehabilitar sin demoler de más / 7.8. Suelos.
- [S2] `faqs/R4R_40_ALL_FAQ.md` ordinal 50 — Riansares 4R — todas las preguntas frecuentes / Suelos / FAQ-040 — ¿Se puede colocar baldosa sobre baldosa?.
- [S3] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 5 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 6. Caso A — Patio, solera, pavimento y drenaje / 6.3. Distinción entre solera y solado.
- [S4] `faqs/R4R_40_ALL_FAQ.md` ordinal 67 — Riansares 4R — todas las preguntas frecuentes / Garantía / FAQ-051 — ¿La garantía cubre una nueva entrada de agua?.
- [S5] `faqs/R4R_40_ALL_FAQ.md` ordinal 59 — Riansares 4R — todas las preguntas frecuentes / Patios / FAQ-046 — ¿Qué debe revisarse antes de pavimentar un patio?.

**S3:** fragmentos idénticos en texto/orden/metadatos a PG; respuesta reutilizada explícitamente, sin segunda generación.

## 15 — EN — case-15

What precautions should be taken before laying new tiles over an existing floor?

**Cobertura:** SUPPORTED. **Valoración:** CORRECTA.

FAQ-040 y R4R_10 §7.8 respaldan firmeza/limpieza/planitud/compatibilidad. EN añade comprobaciones de vinilo presentes en la fuente pero accesorias para esta pregunta.

**Consulta enviada al embedding:** ¿Qué precauciones se deben tomar antes de colocar nuevos azulejos sobre un suelo existente?

**POSTGRES — respuesta:**

```json
"Before laying new tiles over an existing floor, ensure the base is firm, clean, and flat, and use a compatible system. [S2]  \nCheck for humidity and planeity before considering vinyl options. [S2]  \nDo not assume \"baldosa sobre baldosa\" is automatically a mistake; the key is the support and preparation. [S2]"
```

Abstención: `false`.

Citas devueltas: [S2] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 20 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 7. Caso B — Vivienda antigua: rehabilitar sin demoler de más / 7.8. Suelos.

Fragmentos finales, en orden:

- [S1] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 24 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 8. Caso C — Entrada comunitaria con humedad y soportes antiguos / 8.2. Tratamiento previo.
- [S2] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 20 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 7. Caso B — Vivienda antigua: rehabilitar sin demoler de más / 7.8. Suelos.
- [S3] `R4R_20_CASOS_OFICINAS_CUEVAS_BANOS_Y_FILTROS.md` ordinal 32 — Riansares 4R — casos de oficinas, cuevas, baños y filtros comerciales / 14. Caso I — Filtros comerciales: trabajo pequeño claro y obra grande fuera de capacidad / 14.1. Montaje de mobiliario en un baño local.
- [S4] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 44 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 10. Caso E — Inodoro antiguo, cisterna y reparación mínima viable / 10.2. Piezas que no deben confundirse / Fijación al suelo.
- [S5] `R4R_30_METODO_PRESUPUESTOS_SEO_CATALOGO_Y_PLANTILLAS.md` ordinal 32 — Riansares 4R — método, presupuestos, SEO, catálogo y plantillas / 20. Mediciones y croquis para reconstruir planos / Comprobaciones antes de aceptar el plano.

**S3:** fragmentos idénticos en texto/orden/metadatos a PG; respuesta reutilizada explícitamente, sin segunda generación.

## 16 — ES — case-16

Estoy pensando en comprar un piso bastante antiguo, pero tiene grietas en algunas paredes. ¿Cómo puedo saber si es algo superficial o si me voy a meter en un problema serio?

**Cobertura:** PARTIAL. **Valoración:** INCORRECTA/SIN_RESPALDO.

ES confunde preparación de revestimientos (§8.2) con diagnóstico de grieta estructural. EN inventa una regla de descarte: sin esos signos podrían ser superficiales. Falta recuperar/aplicar R4R_00 §4.4, que exige comprobación profesional. Una etiqueta válida no respalda esa conclusión.

**Consulta enviada al embedding:** Estoy pensando en comprar un piso bastante antiguo, pero tiene grietas en algunas paredes. ¿Cómo puedo saber si es algo superficial o si me voy a meter en un problema serio?

**POSTGRES — respuesta:**

```json
"Para determinar si las grietas en las paredes de un piso antiguo son superficiales o indican un problema serio, debes verificar la firmeza y la adherencia del soporte, así como la presencia de humedad visible, sales o bolsas, según se indica en [S5]. Además, si las grietas están relacionadas con humedad o problemas estructurales, se deben evitar soluciones como pintura antihumedad o yeso sobre muro salino, ya que son consideradas soluciones inadecuadas según [S4]."
```

Abstención: `false`.

Citas devueltas: [S4] `R4R_30_METODO_PRESUPUESTOS_SEO_CATALOGO_Y_PLANTILLAS.md` ordinal 13 — Riansares 4R — método, presupuestos, SEO, catálogo y plantillas / 16. Humedades: marco general / 16.3. Soluciones que no deben venderse como milagro; [S5] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 24 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 8. Caso C — Entrada comunitaria con humedad y soportes antiguos / 8.2. Tratamiento previo.

Fragmentos finales, en orden:

- [S1] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 20 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 7. Caso B — Vivienda antigua: rehabilitar sin demoler de más / 7.8. Suelos.
- [S2] `R4R_30_METODO_PRESUPUESTOS_SEO_CATALOGO_Y_PLANTILLAS.md` ordinal 15 — Riansares 4R — método, presupuestos, SEO, catálogo y plantillas / 17. Diagnóstico y demolición selectiva.
- [S3] `faqs/R4R_40_ALL_FAQ.md` ordinal 70 — Riansares 4R — todas las preguntas frecuentes / Fuentes web.
- [S4] `R4R_30_METODO_PRESUPUESTOS_SEO_CATALOGO_Y_PLANTILLAS.md` ordinal 13 — Riansares 4R — método, presupuestos, SEO, catálogo y plantillas / 16. Humedades: marco general / 16.3. Soluciones que no deben venderse como milagro.
- [S5] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 24 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 8. Caso C — Entrada comunitaria con humedad y soportes antiguos / 8.2. Tratamiento previo.

**S3:** fragmentos idénticos en texto/orden/metadatos a PG; respuesta reutilizada explícitamente, sin segunda generación.

## 16 — EN — case-16

I'm thinking of buying a fairly old flat, but there are cracks in some walls. How can I tell whether they are superficial or whether I'm getting into a serious problem?

**Cobertura:** PARTIAL. **Valoración:** INCORRECTA/SIN_RESPALDO.

ES confunde preparación de revestimientos (§8.2) con diagnóstico de grieta estructural. EN inventa una regla de descarte: sin esos signos podrían ser superficiales. Falta recuperar/aplicar R4R_00 §4.4, que exige comprobación profesional. Una etiqueta válida no respalda esa conclusión.

**Consulta enviada al embedding:** Estoy pensando en comprar un apartamento bastante antiguo, pero hay grietas en algunas paredes. ¿Cómo puedo saber si son superficiales o si estoy metiéndome en un problema serio?

**POSTGRES — respuesta:**

```json
"To determine if cracks in walls are superficial or indicate a serious problem, you should check for signs of structural issues such as **risks to the structure**, **reinforcements**, and **deterioration of installations**. These factors are documented in [S1]. If the cracks are accompanied by these issues, it may signal a more serious problem. If no such signs are present, the cracks may be superficial."
```

Abstención: `false`.

Citas devueltas: [S1] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 14 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 7. Caso B — Vivienda antigua: rehabilitar sin demoler de más / 7.2. Principio: negociar con la estructura existente.

Fragmentos finales, en orden:

- [S1] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 14 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 7. Caso B — Vivienda antigua: rehabilitar sin demoler de más / 7.2. Principio: negociar con la estructura existente.
- [S2] `R4R_30_METODO_PRESUPUESTOS_SEO_CATALOGO_Y_PLANTILLAS.md` ordinal 33 — Riansares 4R — método, presupuestos, SEO, catálogo y plantillas / 20. Mediciones y croquis para reconstruir planos / Artículos derivados.
- [S3] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 8 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 6. Caso A — Patio, solera, pavimento y drenaje / 6.4. Hormigón de camión y ejecución manual / Patio interior o pasillo estrecho.
- [S4] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 24 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 8. Caso C — Entrada comunitaria con humedad y soportes antiguos / 8.2. Tratamiento previo.
- [S5] `R4R_30_METODO_PRESUPUESTOS_SEO_CATALOGO_Y_PLANTILLAS.md` ordinal 12 — Riansares 4R — método, presupuestos, SEO, catálogo y plantillas / 16. Humedades: marco general / 16.2. Materiales compatibles.

**S3:** fragmentos idénticos en texto/orden/metadatos a PG; respuesta reutilizada explícitamente, sin segunda generación.

## 17 — ES — case-17

Quiero reformar mi cocina, pero los muebles todavía están bien. ¿Hay alguna forma de renovarla sin desmontarlo todo?

**Cobertura:** PARTIAL. **Valoración:** PARCIAL.

Recupera §7.6 Cocina y conserva posiciones de agua/desagüe; renovación selectiva documentada. No resuelve específicamente cómo renovar los muebles existentes; EN añade precio no solicitado. No se acredita una técnica de renovar frentes.

**Consulta enviada al embedding:** Quiero reformar mi cocina, pero los muebles todavía están bien. ¿Hay alguna forma de renovarla sin desmontarlo todo?

**POSTGRES — respuesta:**

```json
"Sí, hay formas de renovar la cocina sin desmontar todo, como adaptar la iluminación y los enchufes, y elegir una gama económica o media-baja bien seleccionada. Además, se puede conservar la posición del agua y desagüe si son viables. [S1]"
```

Abstención: `false`.

Citas devueltas: [S1] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 18 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 7. Caso B — Vivienda antigua: rehabilitar sin demoler de más / 7.6. Cocina.

Fragmentos finales, en orden:

- [S1] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 18 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 7. Caso B — Vivienda antigua: rehabilitar sin demoler de más / 7.6. Cocina.
- [S2] `sources/R4R_70_WEB_SOURCE_MANIFEST.md` ordinal 10 — Fuentes web consultadas / SRC-HAB-COCINA — ¿Cuánto cuesta reformar una cocina?.
- [S3] `R4R_00_IDENTIDAD_CRITERIOS_Y_VOZ_EDITORIAL.md` ordinal 6 — Riansares 4R — identidad, criterios y voz editorial / 2. Identidad de Riansares 4R / 2.3. Servicios nucleares / Reformas.
- [S4] `R4R_30_METODO_PRESUPUESTOS_SEO_CATALOGO_Y_PLANTILLAS.md` ordinal 23 — Riansares 4R — método, presupuestos, SEO, catálogo y plantillas / 19. SEO local y GEO / 19.2. Zonas y términos.
- [S5] `faqs/R4R_40_ALL_FAQ.md` ordinal 45 — Riansares 4R — todas las preguntas frecuentes / Cocinas / FAQ-036 — ¿Cuánto cuesta reformar una cocina de 7 m²?.

**S3:** fragmentos idénticos en texto/orden/metadatos a PG; respuesta reutilizada explícitamente, sin segunda generación.

## 17 — EN — case-17

I want to renovate my kitchen, but the cabinets are still in good condition. Is there a way to refresh it without taking everything apart?

**Cobertura:** PARTIAL. **Valoración:** PARCIAL.

Recupera §7.6 Cocina y conserva posiciones de agua/desagüe; renovación selectiva documentada. No resuelve específicamente cómo renovar los muebles existentes; EN añade precio no solicitado. No se acredita una técnica de renovar frentes.

**Consulta enviada al embedding:** Quiero reformar mi cocina, pero los gabinetes aún están en buen estado. ¿Hay alguna manera de refrescarla sin desmontar todo?

**POSTGRES — respuesta:**

```json
"Yes, you can refresh your kitchen without taking everything apart by focusing on updates that enhance functionality and aesthetics while preserving existing cabinets. According to the evidence, you can adapt the placement of electrical outlets and lighting, and choose a simple countertop that complements the existing cabinets [S3]. Additionally, you can consider a mid-range quality renovation, which typically costs between 5,600–9,000 € for a 7 m² kitchen [S4]."
```

Abstención: `false`.

Citas devueltas: [S3] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 18 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 7. Caso B — Vivienda antigua: rehabilitar sin demoler de más / 7.6. Cocina; [S4] `faqs/R4R_40_ALL_FAQ.md` ordinal 45 — Riansares 4R — todas las preguntas frecuentes / Cocinas / FAQ-036 — ¿Cuánto cuesta reformar una cocina de 7 m²?.

Fragmentos finales, en orden:

- [S1] `sources/R4R_70_WEB_SOURCE_MANIFEST.md` ordinal 10 — Fuentes web consultadas / SRC-HAB-COCINA — ¿Cuánto cuesta reformar una cocina?.
- [S2] `R4R_30_METODO_PRESUPUESTOS_SEO_CATALOGO_Y_PLANTILLAS.md` ordinal 35 — Riansares 4R — método, presupuestos, SEO, catálogo y plantillas / 21. Web y coherencia editorial / 21.1. Mensaje comprobado en portada.
- [S3] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 18 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 7. Caso B — Vivienda antigua: rehabilitar sin demoler de más / 7.6. Cocina.
- [S4] `faqs/R4R_40_ALL_FAQ.md` ordinal 45 — Riansares 4R — todas las preguntas frecuentes / Cocinas / FAQ-036 — ¿Cuánto cuesta reformar una cocina de 7 m²?.
- [S5] `R4R_00_IDENTIDAD_CRITERIOS_Y_VOZ_EDITORIAL.md` ordinal 6 — Riansares 4R — identidad, criterios y voz editorial / 2. Identidad de Riansares 4R / 2.3. Servicios nucleares / Reformas.

**S3:** fragmentos idénticos en texto/orden/metadatos a PG; respuesta reutilizada explícitamente, sin segunda generación.

## 18 — ES — case-19

Tengo una terraza que filtra agua al piso de abajo cada vez que llueve fuerte. ¿Por dónde empezaríais para localizar y solucionar el problema?

**Cobertura:** PARTIAL. **Valoración:** INCORRECTA/SIN_RESPALDO.

ES propone tuberías/conexiones sin prueba documental específica. EN usa la válvula de llenado de cisterna [S1] como drenaje de terraza: transferencia falsa. R4R_10 §6.7 y R4R_00 §4.3 podrían dar orientación inicial prudente, pero no llegan al top final.

**Consulta enviada al embedding:** Tengo una terraza que filtra agua al piso de abajo cada vez que llueve fuerte. ¿Por dónde empezaríais para localizar y solucionar el problema?

**POSTGRES — respuesta:**

```json
"Para localizar y solucionar el problema de filtración en la terraza, es importante comprobar si la filtración se debe a una mala instalación o a una fuga en la tubería. Según [S4], la pintura antihumedad sobre una fuga no es una solución adecuada, lo que sugiere que debe analizarse la causa raíz de la humedad. Además, se debe revisar la instalación para asegurar que no haya conexiones deterioradas o problemas de sellado."
```

Abstención: `false`.

Citas devueltas: [S4] `R4R_30_METODO_PRESUPUESTOS_SEO_CATALOGO_Y_PLANTILLAS.md` ordinal 13 — Riansares 4R — método, presupuestos, SEO, catálogo y plantillas / 16. Humedades: marco general / 16.3. Soluciones que no deben venderse como milagro.

Fragmentos finales, en orden:

- [S1] `R4R_20_CASOS_OFICINAS_CUEVAS_BANOS_Y_FILTROS.md` ordinal 31 — Riansares 4R — casos de oficinas, cuevas, baños y filtros comerciales / 14. Caso I — Filtros comerciales: trabajo pequeño claro y obra grande fuera de capacidad.
- [S2] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 41 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 10. Caso E — Inodoro antiguo, cisterna y reparación mínima viable / 10.2. Piezas que no deben confundirse / Mecanismo de descarga.
- [S3] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 20 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 7. Caso B — Vivienda antigua: rehabilitar sin demoler de más / 7.8. Suelos.
- [S4] `R4R_30_METODO_PRESUPUESTOS_SEO_CATALOGO_Y_PLANTILLAS.md` ordinal 13 — Riansares 4R — método, presupuestos, SEO, catálogo y plantillas / 16. Humedades: marco general / 16.3. Soluciones que no deben venderse como milagro.
- [S5] `R4R_20_CASOS_OFICINAS_CUEVAS_BANOS_Y_FILTROS.md` ordinal 36 — Riansares 4R — casos de oficinas, cuevas, baños y filtros comerciales / 14. Caso I — Filtros comerciales: trabajo pequeño claro y obra grande fuera de capacidad / 14.5. Filtro antes de visitar.

**S3:** fragmentos idénticos en texto/orden/metadatos a PG; respuesta reutilizada explícitamente, sin segunda generación.

## 18 — EN — case-19

I have a terrace that leaks water into the flat below whenever it rains heavily. Where would you start to locate and solve the problem?

**Cobertura:** PARTIAL. **Valoración:** INCORRECTA/SIN_RESPALDO.

ES propone tuberías/conexiones sin prueba documental específica. EN usa la válvula de llenado de cisterna [S1] como drenaje de terraza: transferencia falsa. R4R_10 §6.7 y R4R_00 §4.3 podrían dar orientación inicial prudente, pero no llegan al top final.

**Consulta enviada al embedding:** Tengo una terraza que deja pasar agua al piso de abajo cuando llueve mucho. ¿Dónde empezaría para localizar y resolver el problema?

**POSTGRES — respuesta:**

```json
"To locate and solve the water leakage issue, start by checking the terrace's drainage system, as the valve allows water in and stops it when the level is reached, ensuring proper water management. [S1] Additionally, inspect the base and support structure for stability, as improper installation or weak support can lead to water infiltration. [S3]"
```

Abstención: `false`.

Citas devueltas: [S1] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 43 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 10. Caso E — Inodoro antiguo, cisterna y reparación mínima viable / 10.2. Piezas que no deben confundirse / Válvula de llenado o boya; [S3] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 20 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 7. Caso B — Vivienda antigua: rehabilitar sin demoler de más / 7.8. Suelos.

Fragmentos finales, en orden:

- [S1] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 43 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 10. Caso E — Inodoro antiguo, cisterna y reparación mínima viable / 10.2. Piezas que no deben confundirse / Válvula de llenado o boya.
- [S2] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 41 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 10. Caso E — Inodoro antiguo, cisterna y reparación mínima viable / 10.2. Piezas que no deben confundirse / Mecanismo de descarga.
- [S3] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 20 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 7. Caso B — Vivienda antigua: rehabilitar sin demoler de más / 7.8. Suelos.
- [S4] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 5 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 6. Caso A — Patio, solera, pavimento y drenaje / 6.3. Distinción entre solera y solado.
- [S5] `R4R_20_CASOS_OFICINAS_CUEVAS_BANOS_Y_FILTROS.md` ordinal 26 — Riansares 4R — casos de oficinas, cuevas, baños y filtros comerciales / 13. Caso H — Dos baños contiguos en una vivienda antigua y estrecha / 13.7. Placa en ducha y mortero de cal en zona seca.

**S3:** fragmentos idénticos en texto/orden/metadatos a PG; respuesta reutilizada explícitamente, sin segunda generación.

## 19 — ES — case-22

Acabamos de comprar un piso y queremos reformarlo antes de mudarnos. ¿En qué orden conviene hacer los trabajos para evitar tener que romper cosas que ya están terminadas?

**Cobertura:** SUPPORTED. **Valoración:** INCORRECTA/SIN_RESPALDO.

ES afirma un orden e incluye instalar suelos laminados como trabajo inicial de demolición/estructura, citando §6.3, que no documenta esa secuencia. EN ofrece frases de marca sobre demoler/valorar daños sin contestar el orden. FAQ-047 y §7.7 sí responderían a instalaciones antes de acabados, pero faltan en los fragmentos finales.

**Consulta enviada al embedding:** Acabamos de comprar un piso y queremos reformarlo antes de mudarnos. ¿En qué orden conviene hacer los trabajos para evitar tener que romper cosas que ya están terminadas?

**POSTGRES — respuesta:**

```json
"Para evitar romper cosas ya terminadas, se debe realizar la reforma en orden, comenzando con los trabajos que requieren demolición o alteración de estructuras, como la instalación de suelos laminados o la demolición de alicatados, antes de proceder a trabajos que no afectan estructuras terminadas. [S5]"
```

Abstención: `false`.

Citas devueltas: [S5] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 5 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 6. Caso A — Patio, solera, pavimento y drenaje / 6.3. Distinción entre solera y solado.

Fragmentos finales, en orden:

- [S1] `R4R_30_METODO_PRESUPUESTOS_SEO_CATALOGO_Y_PLANTILLAS.md` ordinal 14 — Riansares 4R — método, presupuestos, SEO, catálogo y plantillas / 16. Humedades: marco general / 16.4. Contenido útil para clientes.
- [S2] `R4R_30_METODO_PRESUPUESTOS_SEO_CATALOGO_Y_PLANTILLAS.md` ordinal 35 — Riansares 4R — método, presupuestos, SEO, catálogo y plantillas / 21. Web y coherencia editorial / 21.1. Mensaje comprobado en portada.
- [S3] `faqs/R4R_40_ALL_FAQ.md` ordinal 16 — Riansares 4R — todas las preguntas frecuentes / Contratación / FAQ-012 — ¿Qué ocurre con un trabajo extra?.
- [S4] `faqs/R4R_40_ALL_FAQ.md` ordinal 70 — Riansares 4R — todas las preguntas frecuentes / Fuentes web.
- [S5] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 5 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 6. Caso A — Patio, solera, pavimento y drenaje / 6.3. Distinción entre solera y solado.

**S3:** fragmentos idénticos en texto/orden/metadatos a PG; respuesta reutilizada explícitamente, sin segunda generación.

## 19 — EN — case-22

We've just bought a flat and want to renovate it before moving in. In what order should the work be done to avoid having to break things that are already finished?

**Cobertura:** SUPPORTED. **Valoración:** GENÉRICA.

ES afirma un orden e incluye instalar suelos laminados como trabajo inicial de demolición/estructura, citando §6.3, que no documenta esa secuencia. EN ofrece frases de marca sobre demoler/valorar daños sin contestar el orden. FAQ-047 y §7.7 sí responderían a instalaciones antes de acabados, pero faltan en los fragmentos finales.

**Consulta enviada al embedding:** Hemos comprado un piso y queremos reformarlo antes de mudarnos. ¿En qué orden debería hacerse el trabajo para evitar tener que romper cosas que ya están terminadas?

**POSTGRES — respuesta:**

```json
"To avoid breaking things that are already finished, the work should be done in an order that ensures the integrity of completed elements. According to the evidence, it is important to confirm what is failing before demolition, and to document and value hidden damages before proceeding. This approach helps prevent unnecessary damage to finished work. [S2]"
```

Abstención: `false`.

Citas devueltas: [S2] `R4R_00_IDENTIDAD_CRITERIOS_Y_VOZ_EDITORIAL.md` ordinal 15 — Riansares 4R — identidad, criterios y voz editorial / 3. Voz editorial y comercial / 3.3. Frases de marca reutilizables.

Fragmentos finales, en orden:

- [S1] `faqs/R4R_40_ALL_FAQ.md` ordinal 16 — Riansares 4R — todas las preguntas frecuentes / Contratación / FAQ-012 — ¿Qué ocurre con un trabajo extra?.
- [S2] `R4R_00_IDENTIDAD_CRITERIOS_Y_VOZ_EDITORIAL.md` ordinal 15 — Riansares 4R — identidad, criterios y voz editorial / 3. Voz editorial y comercial / 3.3. Frases de marca reutilizables.
- [S3] `R4R_30_METODO_PRESUPUESTOS_SEO_CATALOGO_Y_PLANTILLAS.md` ordinal 35 — Riansares 4R — método, presupuestos, SEO, catálogo y plantillas / 21. Web y coherencia editorial / 21.1. Mensaje comprobado en portada.
- [S4] `faqs/R4R_40_ALL_FAQ.md` ordinal 70 — Riansares 4R — todas las preguntas frecuentes / Fuentes web.
- [S5] `R4R_30_METODO_PRESUPUESTOS_SEO_CATALOGO_Y_PLANTILLAS.md` ordinal 14 — Riansares 4R — método, presupuestos, SEO, catálogo y plantillas / 16. Humedades: marco general / 16.4. Contenido útil para clientes.

**S3:** fragmentos idénticos en texto/orden/metadatos a PG; respuesta reutilizada explícitamente, sin segunda generación.

## 20 — ES — case-18

En mi casa se oye absolutamente todo lo que hacen los vecinos de arriba. ¿Se puede mejorar el aislamiento acústico sin perder demasiada altura en las habitaciones?

**Cobertura:** INSUFFICIENT. **Valoración:** ABSTENCIÓN_JUSTIFICADA.

No hay solución documentada de aislamiento de techo con límite de altura. Se abstiene aunque llegan fragmentos generales.

**Consulta enviada al embedding:** En mi casa se oye absolutamente todo lo que hacen los vecinos de arriba. ¿Se puede mejorar el aislamiento acústico sin perder demasiada altura en las habitaciones?

**POSTGRES — respuesta:**

```json
""
```

Abstención: `true`.

Citas devueltas: ninguna.

Fragmentos finales, en orden:

- [S1] `R4R_30_METODO_PRESUPUESTOS_SEO_CATALOGO_Y_PLANTILLAS.md` ordinal 33 — Riansares 4R — método, presupuestos, SEO, catálogo y plantillas / 20. Mediciones y croquis para reconstruir planos / Artículos derivados.
- [S2] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 20 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 7. Caso B — Vivienda antigua: rehabilitar sin demoler de más / 7.8. Suelos.
- [S3] `R4R_20_CASOS_OFICINAS_CUEVAS_BANOS_Y_FILTROS.md` ordinal 19 — Riansares 4R — casos de oficinas, cuevas, baños y filtros comerciales / 13. Caso H — Dos baños contiguos en una vivienda antigua y estrecha.

**S3:** fragmentos idénticos en texto/orden/metadatos a PG; respuesta reutilizada explícitamente, sin segunda generación.

## 20 — EN — case-18

In my home, we can hear absolutely everything the upstairs neighbours do. Can the sound insulation be improved without losing too much ceiling height in the rooms?

**Cobertura:** INSUFFICIENT. **Valoración:** ABSTENCIÓN_JUSTIFICADA.

No hay solución documentada de aislamiento de techo con límite de altura. Se abstiene aunque llegan fragmentos generales.

**Consulta enviada al embedding:** En mi vivienda, podemos oír absolutamente todo lo que hacen los vecinos de arriba. ¿Puede mejorarse la aislación acústica sin perder demasiada altura en el techo de las habitaciones?

**POSTGRES — respuesta:**

```json
""
```

Abstención: `true`.

Citas devueltas: ninguna.

Fragmentos finales, en orden:

- [S1] `R4R_20_CASOS_OFICINAS_CUEVAS_BANOS_Y_FILTROS.md` ordinal 19 — Riansares 4R — casos de oficinas, cuevas, baños y filtros comerciales / 13. Caso H — Dos baños contiguos en una vivienda antigua y estrecha.
- [S2] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 14 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 7. Caso B — Vivienda antigua: rehabilitar sin demoler de más / 7.2. Principio: negociar con la estructura existente.
- [S3] `R4R_30_METODO_PRESUPUESTOS_SEO_CATALOGO_Y_PLANTILLAS.md` ordinal 15 — Riansares 4R — método, presupuestos, SEO, catálogo y plantillas / 17. Diagnóstico y demolición selectiva.
- [S4] `R4R_30_METODO_PRESUPUESTOS_SEO_CATALOGO_Y_PLANTILLAS.md` ordinal 33 — Riansares 4R — método, presupuestos, SEO, catálogo y plantillas / 20. Mediciones y croquis para reconstruir planos / Artículos derivados.
- [S5] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 31 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 9. Caso D — Techo manchado después de una fuga ya reparada / 9.1. Contexto.

**S3:** fragmentos idénticos en texto/orden/metadatos a PG; respuesta reutilizada explícitamente, sin segunda generación.

## 21 — ES — case-20

Quiero cambiar las ventanas de mi casa, pero no sé si realmente merece la pena instalar triple acristalamiento o si con doble es suficiente.

**Cobertura:** INSUFFICIENT. **Valoración:** PARCIAL.

ES reconoce explícitamente que no se documenta doble/triple y solo menciona ventanas deficientes: limitación honesta, no comparación técnica. EN se abstiene, pero su traductor cambia triple por doble y repite doble/doble: no prueba una comprensión íntegra de la pregunta.

**Consulta enviada al embedding:** Quiero cambiar las ventanas de mi casa, pero no sé si realmente merece la pena instalar triple acristalamiento o si con doble es suficiente.

**POSTGRES — respuesta:**

```json
"La evidencia indica que se recomienda mejorar el confort y la habitabilidad, incluyendo la sustitución de ventanas deficientes. Sin embargo, no se especifica si el triple acristalamiento es necesario o si el doble es suficiente. Por lo tanto, la decisión depende de factores no documentados en el material proporcionado.  \n[S5]"
```

Abstención: `false`.

Citas devueltas: [S5] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 14 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 7. Caso B — Vivienda antigua: rehabilitar sin demoler de más / 7.2. Principio: negociar con la estructura existente.

Fragmentos finales, en orden:

- [S1] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 31 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 9. Caso D — Techo manchado después de una fuga ya reparada / 9.1. Contexto.
- [S2] `R4R_30_METODO_PRESUPUESTOS_SEO_CATALOGO_Y_PLANTILLAS.md` ordinal 32 — Riansares 4R — método, presupuestos, SEO, catálogo y plantillas / 20. Mediciones y croquis para reconstruir planos / Comprobaciones antes de aceptar el plano.
- [S3] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 8 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 6. Caso A — Patio, solera, pavimento y drenaje / 6.4. Hormigón de camión y ejecución manual / Patio interior o pasillo estrecho.
- [S4] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 20 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 7. Caso B — Vivienda antigua: rehabilitar sin demoler de más / 7.8. Suelos.
- [S5] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 14 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 7. Caso B — Vivienda antigua: rehabilitar sin demoler de más / 7.2. Principio: negociar con la estructura existente.

**S3:** fragmentos idénticos en texto/orden/metadatos a PG; respuesta reutilizada explícitamente, sin segunda generación.

## 21 — EN — case-20

I want to replace my home's windows, but I don't know whether triple glazing is really worthwhile or whether double glazing is enough.

**Cobertura:** INSUFFICIENT. **Valoración:** ABSTENCIÓN_JUSTIFICADA.

ES reconoce explícitamente que no se documenta doble/triple y solo menciona ventanas deficientes: limitación honesta, no comparación técnica. EN se abstiene, pero su traductor cambia triple por doble y repite doble/doble: no prueba una comprensión íntegra de la pregunta.

**Consulta enviada al embedding:** Quiero reemplazar las ventanas de mi casa, pero no sé si realmente vale la pena la doble acristalamiento o si el doble acristalamiento es suficiente.

**POSTGRES — respuesta:**

```json
""
```

Abstención: `true`.

Citas devueltas: ninguna.

Fragmentos finales, en orden:

- [S1] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 17 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 7. Caso B — Vivienda antigua: rehabilitar sin demoler de más / 7.5. Baño pequeño.
- [S2] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 31 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 9. Caso D — Techo manchado después de una fuga ya reparada / 9.1. Contexto.
- [S3] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 13 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 7. Caso B — Vivienda antigua: rehabilitar sin demoler de más / 7.1. Contexto.
- [S4] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 33 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 9. Caso D — Techo manchado después de una fuga ya reparada / 9.3. Reparación del techo.
- [S5] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 20 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 7. Caso B — Vivienda antigua: rehabilitar sin demoler de más / 7.8. Suelos.

**S3:** fragmentos idénticos en texto/orden/metadatos a PG; respuesta reutilizada explícitamente, sin segunda generación.

## 22 — ES — case-21

Mi casa tiene un pasillo larguísimo y bastante oscuro. ¿Qué cambios se podrían hacer para aprovechar mejor el espacio y conseguir más luz?

**Cobertura:** INSUFFICIENT. **Valoración:** GENÉRICA.

ES PG/S3 reconocen falta de documentación, pero rellenan con transporte/acceso de solera. EN recomienda optimizar luz/distribución citando acceso de obra: esa recomendación no consta. Ninguna versión resuelve iluminación/espacio.

**Consulta enviada al embedding:** Mi casa tiene un pasillo larguísimo y bastante oscuro. ¿Qué cambios se podrían hacer para aprovechar mejor el espacio y conseguir más luz?

**POSTGRES — respuesta:**

```json
"Para aprovechar mejor el espacio y conseguir más luz en un pasillo largo y oscuro, se podrían considerar cambios que mejoren la iluminación y la distribución del espacio. Según [S1], el pasillo estrecho presenta dificultades como acceso indirecto y más mano de obra, lo que sugiere que la ejecución de la solera puede influir en la eficiencia del espacio. Sin embargo, no se mencionan específicamente cambios para mejorar la iluminación o el uso del espacio en este contexto. Por lo tanto, la pregunta no está completamente respaldada por la evidencia proporcionada."
```

Abstención: `false`.

Citas devueltas: [S1] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 8 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 6. Caso A — Patio, solera, pavimento y drenaje / 6.4. Hormigón de camión y ejecución manual / Patio interior o pasillo estrecho.

Fragmentos finales, en orden:

- [S1] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 8 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 6. Caso A — Patio, solera, pavimento y drenaje / 6.4. Hormigón de camión y ejecución manual / Patio interior o pasillo estrecho.
- [S2] `R4R_30_METODO_PRESUPUESTOS_SEO_CATALOGO_Y_PLANTILLAS.md` ordinal 33 — Riansares 4R — método, presupuestos, SEO, catálogo y plantillas / 20. Mediciones y croquis para reconstruir planos / Artículos derivados.
- [S3] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 7 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 6. Caso A — Patio, solera, pavimento y drenaje / 6.4. Hormigón de camión y ejecución manual / Patio exterior con acceso.
- [S4] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 2 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 6. Caso A — Patio, solera, pavimento y drenaje.

**S3 — respuesta:**

```json
"Para aprovechar mejor el espacio y conseguir más luz en un pasillo largo y oscuro, se podrían considerar cambios que mejoren el acceso y reduzcan la manipulación de materiales, como menciona [S1]. Sin embargo, no se documentan cambios específicos para mejorar la iluminación o el uso del espacio en este tipo de pasillo."
```

Abstención: `false`.

Citas devueltas: [S1] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 8 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 6. Caso A — Patio, solera, pavimento y drenaje / 6.4. Hormigón de camión y ejecución manual / Patio interior o pasillo estrecho.

Fragmentos finales, en orden:

- [S1] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 8 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 6. Caso A — Patio, solera, pavimento y drenaje / 6.4. Hormigón de camión y ejecución manual / Patio interior o pasillo estrecho.
- [S2] `R4R_30_METODO_PRESUPUESTOS_SEO_CATALOGO_Y_PLANTILLAS.md` ordinal 33 — Riansares 4R — método, presupuestos, SEO, catálogo y plantillas / 20. Mediciones y croquis para reconstruir planos / Artículos derivados.
- [S3] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 7 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 6. Caso A — Patio, solera, pavimento y drenaje / 6.4. Hormigón de camión y ejecución manual / Patio exterior con acceso.

## 22 — EN — case-21

My home has a very long and rather dark corridor. What changes could make better use of the space and bring in more light?

**Cobertura:** INSUFFICIENT. **Valoración:** INCORRECTA/SIN_RESPALDO.

ES PG/S3 reconocen falta de documentación, pero rellenan con transporte/acceso de solera. EN recomienda optimizar luz/distribución citando acceso de obra: esa recomendación no consta. Ninguna versión resuelve iluminación/espacio.

**Consulta enviada al embedding:** Mi vivienda tiene un pasillo muy largo y bastante oscuro. ¿Qué cambios podrían hacer un mejor uso del espacio y traer más luz?

**POSTGRES — respuesta:**

```json
"To improve light and space use in a long and dark corridor, consider optimizing the layout to reduce indirect access and enhance natural light flow. This can involve rethinking the placement of elements to minimize obstructions and improve visibility, as indirect access can contribute to a feeling of confinement. [S1]"
```

Abstención: `false`.

Citas devueltas: [S1] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 8 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 6. Caso A — Patio, solera, pavimento y drenaje / 6.4. Hormigón de camión y ejecución manual / Patio interior o pasillo estrecho.

Fragmentos finales, en orden:

- [S1] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 8 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 6. Caso A — Patio, solera, pavimento y drenaje / 6.4. Hormigón de camión y ejecución manual / Patio interior o pasillo estrecho.
- [S2] `R4R_30_METODO_PRESUPUESTOS_SEO_CATALOGO_Y_PLANTILLAS.md` ordinal 33 — Riansares 4R — método, presupuestos, SEO, catálogo y plantillas / 20. Mediciones y croquis para reconstruir planos / Artículos derivados.
- [S3] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 3 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 6. Caso A — Patio, solera, pavimento y drenaje / 6.1. Contexto.

**S3:** fragmentos idénticos en texto/orden/metadatos a PG; respuesta reutilizada explícitamente, sin segunda generación.

## 23 — ES — case-23

Tengo una vivienda antigua con techos de madera. Algunas vigas están algo combadas. ¿Se pueden reforzar o normalmente hay que sustituirlas?

**Cobertura:** INSUFFICIENT. **Valoración:** PARCIAL.

ES explicita que no hay respuesta a refuerzo/sustitución, y añade secado/encuentros de madera (§13.8). EN se abstiene. La información auxiliar no debe interpretarse como decisión estructural.

**Consulta enviada al embedding:** Tengo una vivienda antigua con techos de madera. Algunas vigas están algo combadas. ¿Se pueden reforzar o normalmente hay que sustituirlas?

**POSTGRES — respuesta:**

```json
"No se menciona explícitamente en el documento cómo tratar vigas de madera combadas en una vivienda antigua. Sin embargo, se indica que las vigas de madera pueden estar parcialmente empotradas y que es importante permitir que la cara visible siga secando, evitando sellar rígidamente la unión madera-cal. [S1]"
```

Abstención: `false`.

Citas devueltas: [S1] `R4R_20_CASOS_OFICINAS_CUEVAS_BANOS_Y_FILTROS.md` ordinal 27 — Riansares 4R — casos de oficinas, cuevas, baños y filtros comerciales / 13. Caso H — Dos baños contiguos en una vivienda antigua y estrecha / 13.8. Vigas de madera.

Fragmentos finales, en orden:

- [S1] `R4R_20_CASOS_OFICINAS_CUEVAS_BANOS_Y_FILTROS.md` ordinal 27 — Riansares 4R — casos de oficinas, cuevas, baños y filtros comerciales / 13. Caso H — Dos baños contiguos en una vivienda antigua y estrecha / 13.8. Vigas de madera.
- [S2] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 14 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 7. Caso B — Vivienda antigua: rehabilitar sin demoler de más / 7.2. Principio: negociar con la estructura existente.
- [S3] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 12 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 7. Caso B — Vivienda antigua: rehabilitar sin demoler de más.
- [S4] `R4R_20_CASOS_OFICINAS_CUEVAS_BANOS_Y_FILTROS.md` ordinal 19 — Riansares 4R — casos de oficinas, cuevas, baños y filtros comerciales / 13. Caso H — Dos baños contiguos en una vivienda antigua y estrecha.
- [S5] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 19 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 7. Caso B — Vivienda antigua: rehabilitar sin demoler de más / 7.7. Instalaciones.

**S3:** fragmentos idénticos en texto/orden/metadatos a PG; respuesta reutilizada explícitamente, sin segunda generación.

## 23 — EN — case-23

I have an old home with wooden ceilings. Some beams are slightly bowed. Can they be reinforced, or do they normally need replacing?

**Cobertura:** INSUFFICIENT. **Valoración:** ABSTENCIÓN_JUSTIFICADA.

ES explicita que no hay respuesta a refuerzo/sustitución, y añade secado/encuentros de madera (§13.8). EN se abstiene. La información auxiliar no debe interpretarse como decisión estructural.

**Consulta enviada al embedding:** Tengo una vivienda antigua con techos de madera. Algunas vigas están ligeramente curvadas. ¿Pueden reforzarse, o normalmente necesitan reemplazarse?

**POSTGRES — respuesta:**

```json
""
```

Abstención: `true`.

Citas devueltas: ninguna.

Fragmentos finales, en orden:

- [S1] `R4R_20_CASOS_OFICINAS_CUEVAS_BANOS_Y_FILTROS.md` ordinal 27 — Riansares 4R — casos de oficinas, cuevas, baños y filtros comerciales / 13. Caso H — Dos baños contiguos en una vivienda antigua y estrecha / 13.8. Vigas de madera.
- [S2] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 14 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 7. Caso B — Vivienda antigua: rehabilitar sin demoler de más / 7.2. Principio: negociar con la estructura existente.
- [S3] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 19 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 7. Caso B — Vivienda antigua: rehabilitar sin demoler de más / 7.7. Instalaciones.
- [S4] `R4R_00_IDENTIDAD_CRITERIOS_Y_VOZ_EDITORIAL.md` ordinal 7 — Riansares 4R — identidad, criterios y voz editorial / 2. Identidad de Riansares 4R / 2.3. Servicios nucleares / Rehabilitación.
- [S5] `R4R_20_CASOS_OFICINAS_CUEVAS_BANOS_Y_FILTROS.md` ordinal 20 — Riansares 4R — casos de oficinas, cuevas, baños y filtros comerciales / 13. Caso H — Dos baños contiguos en una vivienda antigua y estrecha / 13.1. Contexto.

**S3:** fragmentos idénticos en texto/orden/metadatos a PG; respuesta reutilizada explícitamente, sin segunda generación.

## 24 — ES — case-24

Tenemos una oficina donde hace mucho calor en unas zonas y frío en otras. ¿Se puede solucionar con una reforma de la climatización sin cambiar todo el sistema?

**Cobertura:** INSUFFICIENT. **Valoración:** INCORRECTA/SIN_RESPALDO.

ES asegura solución sin sustitución y ajuste de distribución/funcionamiento con [S5] (§7.2 vivienda antigua), que no demuestra esa viabilidad. EN se abstiene correctamente.

**Consulta enviada al embedding:** Tenemos una oficina donde hace mucho calor en unas zonas y frío en otras. ¿Se puede solucionar con una reforma de la climatización sin cambiar todo el sistema?

**POSTGRES — respuesta:**

```json
"Se puede solucionar el problema de calor y frío en diferentes zonas de una oficina con una reforma de la climatización sin cambiar todo el sistema, siempre que se revise y ajuste la distribución y el funcionamiento de los elementos existentes. [S5]"
```

Abstención: `false`.

Citas devueltas: [S5] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 14 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 7. Caso B — Vivienda antigua: rehabilitar sin demoler de más / 7.2. Principio: negociar con la estructura existente.

Fragmentos finales, en orden:

- [S1] `R4R_20_CASOS_OFICINAS_CUEVAS_BANOS_Y_FILTROS.md` ordinal 2 — Riansares 4R — casos de oficinas, cuevas, baños y filtros comerciales / 11. Caso F — Reforma de oficinas y aseos corporativos.
- [S2] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 20 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 7. Caso B — Vivienda antigua: rehabilitar sin demoler de más / 7.8. Suelos.
- [S3] `R4R_30_METODO_PRESUPUESTOS_SEO_CATALOGO_Y_PLANTILLAS.md` ordinal 23 — Riansares 4R — método, presupuestos, SEO, catálogo y plantillas / 19. SEO local y GEO / 19.2. Zonas y términos.
- [S4] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 8 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 6. Caso A — Patio, solera, pavimento y drenaje / 6.4. Hormigón de camión y ejecución manual / Patio interior o pasillo estrecho.
- [S5] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 14 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 7. Caso B — Vivienda antigua: rehabilitar sin demoler de más / 7.2. Principio: negociar con la estructura existente.

**S3:** fragmentos idénticos en texto/orden/metadatos a PG; respuesta reutilizada explícitamente, sin segunda generación.

## 24 — EN — case-24

Our office is very hot in some areas and cold in others. Can this be solved by renovating the climate-control installation without replacing the whole system?

**Cobertura:** INSUFFICIENT. **Valoración:** ABSTENCIÓN_JUSTIFICADA.

ES asegura solución sin sustitución y ajuste de distribución/funcionamiento con [S5] (§7.2 vivienda antigua), que no demuestra esa viabilidad. EN se abstiene correctamente.

**Consulta enviada al embedding:** Nuestra oficina tiene zonas muy calientes y otras muy frías. ¿Puede este problema resolverse mediante la renovación de la instalación de climatización sin reemplazar el sistema completo?

**POSTGRES — respuesta:**

```json
""
```

Abstención: `true`.

Citas devueltas: ninguna.

Fragmentos finales, en orden:

- [S1] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 19 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 7. Caso B — Vivienda antigua: rehabilitar sin demoler de más / 7.7. Instalaciones.
- [S2] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 20 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 7. Caso B — Vivienda antigua: rehabilitar sin demoler de más / 7.8. Suelos.
- [S3] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 14 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 7. Caso B — Vivienda antigua: rehabilitar sin demoler de más / 7.2. Principio: negociar con la estructura existente.
- [S4] `R4R_20_CASOS_OFICINAS_CUEVAS_BANOS_Y_FILTROS.md` ordinal 11 — Riansares 4R — casos de oficinas, cuevas, baños y filtros comerciales / 12. Caso G — Cueva o bodega antigua: estabilización con mortero de cal y malla / 12.1. Contexto y objetivo real.
- [S5] `R4R_20_CASOS_OFICINAS_CUEVAS_BANOS_Y_FILTROS.md` ordinal 25 — Riansares 4R — casos de oficinas, cuevas, baños y filtros comerciales / 13. Caso H — Dos baños contiguos en una vivienda antigua y estrecha / 13.6. Recrecido e impermeabilización.

**S3:** fragmentos idénticos en texto/orden/metadatos a PG; respuesta reutilizada explícitamente, sin segunda generación.

## 25 — ES — case-25

Queremos convertir una tienda pequeña en una cafetería. ¿Qué obras suelen ser necesarias antes de poder abrir?

**Cobertura:** INSUFFICIENT. **Valoración:** INCORRECTA/SIN_RESPALDO.

ES traslada cocina doméstica a obras necesarias de cafetería. EN convierte separar presupuesto de mobiliario en obra y atribuye ventilación/estudio a [S2] solera/solado, aunque FAQ-038 está en [S3]. Fallan pertinencia y respaldo de la cita. El filtro comercial §14.5 no proporciona un listado técnico de apertura.

**Consulta enviada al embedding:** Queremos convertir una tienda pequeña en una cafetería. ¿Qué obras suelen ser necesarias antes de poder abrir?

**POSTGRES — respuesta:**

```json
"Para convertir una tienda pequeña en una cafetería, es necesario considerar obras como la instalación de una cocina funcional, lo cual implica la adaptación de enchufes e iluminación, y la conservación de posiciones de agua y desagüe si son viables [S4]. Además, se deben evaluar posibles riesgos estructurales y de instalaciones, como la fontanería envejecida, que pueden requerir refuerzos o cambios [S1]."
```

Abstención: `false`.

Citas devueltas: [S1] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 14 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 7. Caso B — Vivienda antigua: rehabilitar sin demoler de más / 7.2. Principio: negociar con la estructura existente; [S4] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 18 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 7. Caso B — Vivienda antigua: rehabilitar sin demoler de más / 7.6. Cocina.

Fragmentos finales, en orden:

- [S1] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 14 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 7. Caso B — Vivienda antigua: rehabilitar sin demoler de más / 7.2. Principio: negociar con la estructura existente.
- [S2] `faqs/R4R_40_ALL_FAQ.md` ordinal 46 — Riansares 4R — todas las preguntas frecuentes / Cocinas / FAQ-037 — ¿Qué puede añadir el mobiliario?.
- [S3] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 46 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 10. Caso E — Inodoro antiguo, cisterna y reparación mínima viable / 10.3. Riesgo de una reparación pequeña.
- [S4] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 18 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 7. Caso B — Vivienda antigua: rehabilitar sin demoler de más / 7.6. Cocina.
- [S5] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 13 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 7. Caso B — Vivienda antigua: rehabilitar sin demoler de más / 7.1. Contexto.

**S3:** fragmentos idénticos en texto/orden/metadatos a PG; respuesta reutilizada explícitamente, sin segunda generación.

## 25 — EN — case-25

We want to turn a small shop into a café. What building work is usually needed before opening?

**Cobertura:** INSUFFICIENT. **Valoración:** INCORRECTA/SIN_RESPALDO.

ES traslada cocina doméstica a obras necesarias de cafetería. EN convierte separar presupuesto de mobiliario en obra y atribuye ventilación/estudio a [S2] solera/solado, aunque FAQ-038 está en [S3]. Fallan pertinencia y respaldo de la cita. El filtro comercial §14.5 no proporciona un listado técnico de apertura.

**Consulta enviada al embedding:** Queremos convertir una pequeña tienda en un café. ¿Qué tipo de obras suelen ser necesarias antes de abrir?

**POSTGRES — respuesta:**

```json
"To turn a small shop into a café, building work usually includes separating the furniture from masonry and installations, as documented in [S1]. Additionally, if the kitchen is to be opened to the living area, it requires an assessment to determine if it is a load-bearing wall and to check permissions, ventilation, and installations, as stated in [S2]."
```

Abstención: `false`.

Citas devueltas: [S1] `faqs/R4R_40_ALL_FAQ.md` ordinal 46 — Riansares 4R — todas las preguntas frecuentes / Cocinas / FAQ-037 — ¿Qué puede añadir el mobiliario?; [S2] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 5 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 6. Caso A — Patio, solera, pavimento y drenaje / 6.3. Distinción entre solera y solado.

Fragmentos finales, en orden:

- [S1] `faqs/R4R_40_ALL_FAQ.md` ordinal 46 — Riansares 4R — todas las preguntas frecuentes / Cocinas / FAQ-037 — ¿Qué puede añadir el mobiliario?.
- [S2] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 5 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 6. Caso A — Patio, solera, pavimento y drenaje / 6.3. Distinción entre solera y solado.
- [S3] `faqs/R4R_40_ALL_FAQ.md` ordinal 47 — Riansares 4R — todas las preguntas frecuentes / Cocinas / FAQ-038 — ¿Abrir la cocina al salón requiere estudio?.
- [S4] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 27 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 8. Caso C — Entrada comunitaria con humedad y soportes antiguos / 8.5. Alternativa de trasdosado.
- [S5] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 8 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 6. Caso A — Patio, solera, pavimento y drenaje / 6.4. Hormigón de camión y ejecución manual / Patio interior o pasillo estrecho.

**S3:** fragmentos idénticos en texto/orden/metadatos a PG; respuesta reutilizada explícitamente, sin segunda generación.

## 26 — ES — case-26

¿Qué potencia y qué número de captadores geotérmicos necesito para climatizar una vivienda de 180 m²?

**Cobertura:** INSUFFICIENT. **Valoración:** ABSTENCIÓN_JUSTIFICADA.

No se documenta dimensionamiento geotérmico.

**Consulta enviada al embedding:** ¿Qué potencia y qué número de captadores geotérmicos necesito para climatizar una vivienda de 180 m²?

**POSTGRES — respuesta:**

```json
""
```

Abstención: `true`.

Citas devueltas: ninguna.

Fragmentos finales, en orden:

- [S1] `R4R_30_METODO_PRESUPUESTOS_SEO_CATALOGO_Y_PLANTILLAS.md` ordinal 33 — Riansares 4R — método, presupuestos, SEO, catálogo y plantillas / 20. Mediciones y croquis para reconstruir planos / Artículos derivados.
- [S2] `sources/R4R_70_WEB_SOURCE_MANIFEST.md` ordinal 8 — Fuentes web consultadas / SRC-HAB-VIVIENDA — ¿Cuánto cuesta reformar una casa? Precios 2026.
- [S3] `R4R_30_METODO_PRESUPUESTOS_SEO_CATALOGO_Y_PLANTILLAS.md` ordinal 3 — Riansares 4R — método, presupuestos, SEO, catálogo y plantillas / 15. Presupuestación con criterio / 15.1. El precio por m² se obtiene al final.
- [S4] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 1 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones.
- [S5] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 25 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 8. Caso C — Entrada comunitaria con humedad y soportes antiguos / 8.3. Geometría y mediciones.

**S3:** fragmentos idénticos en texto/orden/metadatos a PG; respuesta reutilizada explícitamente, sin segunda generación.

## 26 — EN — case-26

What capacity and how many geothermal collectors do I need to heat and cool a 180 m² home?

**Cobertura:** INSUFFICIENT. **Valoración:** ABSTENCIÓN_JUSTIFICADA.

No se documenta dimensionamiento geotérmico.

**Consulta enviada al embedding:** ¿Qué capacidad y cuántos colectores geotérmicos necesito para calentar y enfriar una vivienda de 180 m²?

**POSTGRES — respuesta:**

```json
""
```

Abstención: `true`.

Citas devueltas: ninguna.

Fragmentos finales, en orden:

- [S1] `R4R_30_METODO_PRESUPUESTOS_SEO_CATALOGO_Y_PLANTILLAS.md` ordinal 33 — Riansares 4R — método, presupuestos, SEO, catálogo y plantillas / 20. Mediciones y croquis para reconstruir planos / Artículos derivados.
- [S2] `sources/R4R_70_WEB_SOURCE_MANIFEST.md` ordinal 8 — Fuentes web consultadas / SRC-HAB-VIVIENDA — ¿Cuánto cuesta reformar una casa? Precios 2026.
- [S3] `R4R_30_METODO_PRESUPUESTOS_SEO_CATALOGO_Y_PLANTILLAS.md` ordinal 3 — Riansares 4R — método, presupuestos, SEO, catálogo y plantillas / 15. Presupuestación con criterio / 15.1. El precio por m² se obtiene al final.
- [S4] `R4R_20_CASOS_OFICINAS_CUEVAS_BANOS_Y_FILTROS.md` ordinal 20 — Riansares 4R — casos de oficinas, cuevas, baños y filtros comerciales / 13. Caso H — Dos baños contiguos en una vivienda antigua y estrecha / 13.1. Contexto.
- [S5] `R4R_30_METODO_PRESUPUESTOS_SEO_CATALOGO_Y_PLANTILLAS.md` ordinal 51 — Riansares 4R — método, presupuestos, SEO, catálogo y plantillas / 34. Glosario técnico mínimo.

**S3:** fragmentos idénticos en texto/orden/metadatos a PG; respuesta reutilizada explícitamente, sin segunda generación.

## 27 — ES — case-27

¿Qué sección exacta de cable y qué magnetotérmico exige el REBT para instalar un cargador de coche eléctrico de 11 kW en mi garaje?

**Cobertura:** INSUFFICIENT. **Valoración:** ABSTENCIÓN_JUSTIFICADA.

No se documentan secciones de cable ni protecciones de cargador 11 kW.

**Consulta enviada al embedding:** ¿Qué sección exacta de cable y qué magnetotérmico exige el REBT para instalar un cargador de coche eléctrico de 11 kW en mi garaje?

**POSTGRES — respuesta:**

```json
""
```

Abstención: `true`.

Citas devueltas: ninguna.

Fragmentos finales, en orden:

- [S1] `R4R_20_CASOS_OFICINAS_CUEVAS_BANOS_Y_FILTROS.md` ordinal 32 — Riansares 4R — casos de oficinas, cuevas, baños y filtros comerciales / 14. Caso I — Filtros comerciales: trabajo pequeño claro y obra grande fuera de capacidad / 14.1. Montaje de mobiliario en un baño local.
- [S2] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 35 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 9. Caso D — Techo manchado después de una fuga ya reparada / 9.5. Extractor existente.
- [S3] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 30 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 9. Caso D — Techo manchado después de una fuga ya reparada.
- [S4] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 19 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 7. Caso B — Vivienda antigua: rehabilitar sin demoler de más / 7.7. Instalaciones.
- [S5] `R4R_20_CASOS_OFICINAS_CUEVAS_BANOS_Y_FILTROS.md` ordinal 22 — Riansares 4R — casos de oficinas, cuevas, baños y filtros comerciales / 13. Caso H — Dos baños contiguos en una vivienda antigua y estrecha / 13.3. Manguitos, holguras y soldadura química.

**S3:** fragmentos idénticos en texto/orden/metadatos a PG; respuesta reutilizada explícitamente, sin segunda generación.

## 27 — EN — case-27

What exact cable cross-section and circuit breaker does the REBT require for installing an 11 kW electric car charger in my garage?

**Cobertura:** INSUFFICIENT. **Valoración:** ABSTENCIÓN_JUSTIFICADA.

No se documentan secciones de cable ni protecciones de cargador 11 kW.

**Consulta enviada al embedding:** ¿Cuál es la sección exacta de cable y el interruptor de circuito que requiere la REBT para la instalación de un cargador eléctrico para coche de 11 kW en mi garaje?

**POSTGRES — respuesta:**

```json
""
```

Abstención: `true`.

Citas devueltas: ninguna.

Fragmentos finales, en orden:

- [S1] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 35 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 9. Caso D — Techo manchado después de una fuga ya reparada / 9.5. Extractor existente.
- [S2] `faqs/R4R_40_ALL_FAQ.md` ordinal 70 — Riansares 4R — todas las preguntas frecuentes / Fuentes web.
- [S3] `R4R_20_CASOS_OFICINAS_CUEVAS_BANOS_Y_FILTROS.md` ordinal 32 — Riansares 4R — casos de oficinas, cuevas, baños y filtros comerciales / 14. Caso I — Filtros comerciales: trabajo pequeño claro y obra grande fuera de capacidad / 14.1. Montaje de mobiliario en un baño local.
- [S4] `faqs/R4R_40_ALL_FAQ.md` ordinal 47 — Riansares 4R — todas las preguntas frecuentes / Cocinas / FAQ-038 — ¿Abrir la cocina al salón requiere estudio?.
- [S5] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 19 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 7. Caso B — Vivienda antigua: rehabilitar sin demoler de más / 7.7. Instalaciones.

**S3:** fragmentos idénticos en texto/orden/metadatos a PG; respuesta reutilizada explícitamente, sin segunda generación.

## 28 — ES — case-28

¿De qué manera producen alimento las plantas mediante la fotosíntesis?

**Cobertura:** INSUFFICIENT. **Valoración:** ABSTENCIÓN_JUSTIFICADA.

Fotosíntesis ajena al corpus; abstención con fragmentos irrelevantes.

**Consulta enviada al embedding:** ¿De qué manera producen alimento las plantas mediante la fotosíntesis?

**POSTGRES — respuesta:**

```json
""
```

Abstención: `true`.

Citas devueltas: ninguna.

Fragmentos finales, en orden:

- [S1] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 18 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 7. Caso B — Vivienda antigua: rehabilitar sin demoler de más / 7.6. Cocina.

**S3:** fragmentos idénticos en texto/orden/metadatos a PG; respuesta reutilizada explícitamente, sin segunda generación.

## 28 — EN — case-28

How do plants make their food through photosynthesis?

**Cobertura:** INSUFFICIENT. **Valoración:** ABSTENCIÓN_JUSTIFICADA.

Fotosíntesis ajena al corpus; abstención con fragmentos irrelevantes.

**Consulta enviada al embedding:** ¿Cómo hacen las plantas su alimento mediante la fotosíntesis?

**POSTGRES — respuesta:**

```json
""
```

Abstención: `true`.

Citas devueltas: ninguna.

Fragmentos finales, en orden:

- [S1] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 18 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 7. Caso B — Vivienda antigua: rehabilitar sin demoler de más / 7.6. Cocina.
- [S2] `R4R_30_METODO_PRESUPUESTOS_SEO_CATALOGO_Y_PLANTILLAS.md` ordinal 33 — Riansares 4R — método, presupuestos, SEO, catálogo y plantillas / 20. Mediciones y croquis para reconstruir planos / Artículos derivados.
- [S3] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 34 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 9. Caso D — Techo manchado después de una fuga ya reparada / 9.4. Por qué pintar todo el techo.
- [S4] `faqs/R4R_40_ALL_FAQ.md` ordinal 5 — Riansares 4R — todas las preguntas frecuentes / Presupuesto / FAQ-002 — ¿Por qué no se cierra un precio solo con fotografías?.
- [S5] `R4R_00_IDENTIDAD_CRITERIOS_Y_VOZ_EDITORIAL.md` ordinal 19 — Riansares 4R — identidad, criterios y voz editorial / 4. Reglas de factualidad para el LLM / 4.3. Humedades.

**S3:** fragmentos idénticos en texto/orden/metadatos a PG; respuesta reutilizada explícitamente, sin segunda generación.

## 29 — ES — case-29

¿Cuál es la masa de Júpiter en kilogramos?

**Cobertura:** INSUFFICIENT. **Valoración:** ABSTENCIÓN_JUSTIFICADA.

Masa de Júpiter ajena al corpus; no llegan fragmentos.

**Consulta enviada al embedding:** ¿Cuál es la masa de Júpiter en kilogramos?

**POSTGRES — respuesta:**

```json
""
```

Abstención: `true`.

Citas devueltas: ninguna.

Fragmentos finales, en orden:

- Ninguno.

**S3:** fragmentos idénticos en texto/orden/metadatos a PG; respuesta reutilizada explícitamente, sin segunda generación.

## 29 — EN — case-29

What is the mass of Jupiter in kilograms?

**Cobertura:** INSUFFICIENT. **Valoración:** ABSTENCIÓN_JUSTIFICADA.

Masa de Júpiter ajena al corpus; no llegan fragmentos.

**Consulta enviada al embedding:** ¿Cuál es la masa de Júpiter en kilogramos?

**POSTGRES — respuesta:**

```json
""
```

Abstención: `true`.

Citas devueltas: ninguna.

Fragmentos finales, en orden:

- Ninguno.

**S3:** fragmentos idénticos en texto/orden/metadatos a PG; respuesta reutilizada explícitamente, sin segunda generación.

## 30 — ES — case-30

¿Quién escribió la novela Cien años de soledad?

**Cobertura:** INSUFFICIENT. **Valoración:** ABSTENCIÓN_JUSTIFICADA.

Autor literario ajeno al corpus; abstención con fragmentos irrelevantes.

**Consulta enviada al embedding:** ¿Quién escribió la novela Cien años de soledad?

**POSTGRES — respuesta:**

```json
""
```

Abstención: `true`.

Citas devueltas: ninguna.

Fragmentos finales, en orden:

- [S1] `faqs/R4R_40_ALL_FAQ.md` ordinal 58 — Riansares 4R — todas las preguntas frecuentes / Patios / FAQ-045 — ¿Por qué el acceso cambia el precio de una solera?.
- [S2] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 5 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 6. Caso A — Patio, solera, pavimento y drenaje / 6.3. Distinción entre solera y solado.
- [S3] `faqs/R4R_40_ALL_FAQ.md` ordinal 51 — Riansares 4R — todas las preguntas frecuentes / Suelos / FAQ-041 — ¿Qué diferencia hay entre solera y solado?.
- [S4] `R4R_30_METODO_PRESUPUESTOS_SEO_CATALOGO_Y_PLANTILLAS.md` ordinal 25 — Riansares 4R — método, presupuestos, SEO, catálogo y plantillas / 19. SEO local y GEO / 19.3. Intenciones de búsqueda / Informacional.

**S3:** fragmentos idénticos en texto/orden/metadatos a PG; respuesta reutilizada explícitamente, sin segunda generación.

## 30 — EN — case-30

Who wrote the novel One Hundred Years of Solitude?

**Cobertura:** INSUFFICIENT. **Valoración:** ABSTENCIÓN_JUSTIFICADA.

Autor literario ajeno al corpus; abstención con fragmentos irrelevantes.

**Consulta enviada al embedding:** ¿Quién escribió la novela Cien años de soledad?

**POSTGRES — respuesta:**

```json
""
```

Abstención: `true`.

Citas devueltas: ninguna.

Fragmentos finales, en orden:

- [S1] `faqs/R4R_40_ALL_FAQ.md` ordinal 58 — Riansares 4R — todas las preguntas frecuentes / Patios / FAQ-045 — ¿Por qué el acceso cambia el precio de una solera?.
- [S2] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 5 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 6. Caso A — Patio, solera, pavimento y drenaje / 6.3. Distinción entre solera y solado.
- [S3] `faqs/R4R_40_ALL_FAQ.md` ordinal 51 — Riansares 4R — todas las preguntas frecuentes / Suelos / FAQ-041 — ¿Qué diferencia hay entre solera y solado?.
- [S4] `R4R_30_METODO_PRESUPUESTOS_SEO_CATALOGO_Y_PLANTILLAS.md` ordinal 25 — Riansares 4R — método, presupuestos, SEO, catálogo y plantillas / 19. SEO local y GEO / 19.3. Intenciones de búsqueda / Informacional.

**S3:** fragmentos idénticos en texto/orden/metadatos a PG; respuesta reutilizada explícitamente, sin segunda generación.

## Extra independiente 11 — ES — independent-11

Estamos buscando un local para montar una clínica de fisioterapia. ¿Qué deberíamos comprobar antes de firmar el alquiler para no descubrir después que las reformas son inviables?

**Cobertura:** PARTIAL. **Valoración:** INCORRECTA/SIN_RESPALDO.

R4R_20 §14.5 ofrece preclasificación técnica/licencia/cambio de uso; no normativa de clínica. No llega. PG/S3 citan SEO local como prueba de viabilidad y reparación mínima de inodoro como criterio de alquiler: no respaldan la conclusión.

**Consulta enviada al embedding:** Estamos buscando un local para montar una clínica de fisioterapia. ¿Qué deberíamos comprobar antes de firmar el alquiler para no descubrir después que las reformas son inviables?

**POSTGRES — respuesta:**

```json
"Antes de firmar el alquiler, deberíamos comprobar que no haya humedades, ya que estas pueden afectar la viabilidad de las reformas, según se menciona en [S1]. También es importante asegurarse de que no se requiera una reforma completa, ya que en algunos casos se puede optar por una reparación mínima viable, como se explica en [S3]."
```

Abstención: `false`.

Citas devueltas: [S1] `R4R_30_METODO_PRESUPUESTOS_SEO_CATALOGO_Y_PLANTILLAS.md` ordinal 23 — Riansares 4R — método, presupuestos, SEO, catálogo y plantillas / 19. SEO local y GEO / 19.2. Zonas y términos; [S3] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 47 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 10. Caso E — Inodoro antiguo, cisterna y reparación mínima viable / 10.4. Reducir alcance.

Fragmentos finales, en orden:

- [S1] `R4R_30_METODO_PRESUPUESTOS_SEO_CATALOGO_Y_PLANTILLAS.md` ordinal 23 — Riansares 4R — método, presupuestos, SEO, catálogo y plantillas / 19. SEO local y GEO / 19.2. Zonas y términos.
- [S2] `R4R_20_CASOS_OFICINAS_CUEVAS_BANOS_Y_FILTROS.md` ordinal 32 — Riansares 4R — casos de oficinas, cuevas, baños y filtros comerciales / 14. Caso I — Filtros comerciales: trabajo pequeño claro y obra grande fuera de capacidad / 14.1. Montaje de mobiliario en un baño local.
- [S3] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 47 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 10. Caso E — Inodoro antiguo, cisterna y reparación mínima viable / 10.4. Reducir alcance.
- [S4] `R4R_30_METODO_PRESUPUESTOS_SEO_CATALOGO_Y_PLANTILLAS.md` ordinal 14 — Riansares 4R — método, presupuestos, SEO, catálogo y plantillas / 16. Humedades: marco general / 16.4. Contenido útil para clientes.
- [S5] `R4R_30_METODO_PRESUPUESTOS_SEO_CATALOGO_Y_PLANTILLAS.md` ordinal 6 — Riansares 4R — método, presupuestos, SEO, catálogo y plantillas / 15. Presupuestación con criterio / 15.4. Rebajar alcance, no regalar trabajo.

**S3 — respuesta:**

```json
"Antes de firmar el alquiler, deberíamos comprobar que no haya humedades, ya que estas pueden hacer inviables las reformas, según se menciona en [S1]. También es recomendable asegurarse de que no se requiera una fontanería nueva o modificada, ya que esto puede afectar la viabilidad de las reformas, según se indica en [S2]."
```

Abstención: `false`.

Citas devueltas: [S1] `R4R_30_METODO_PRESUPUESTOS_SEO_CATALOGO_Y_PLANTILLAS.md` ordinal 23 — Riansares 4R — método, presupuestos, SEO, catálogo y plantillas / 19. SEO local y GEO / 19.2. Zonas y términos; [S2] `R4R_20_CASOS_OFICINAS_CUEVAS_BANOS_Y_FILTROS.md` ordinal 32 — Riansares 4R — casos de oficinas, cuevas, baños y filtros comerciales / 14. Caso I — Filtros comerciales: trabajo pequeño claro y obra grande fuera de capacidad / 14.1. Montaje de mobiliario en un baño local.

Fragmentos finales, en orden:

- [S1] `R4R_30_METODO_PRESUPUESTOS_SEO_CATALOGO_Y_PLANTILLAS.md` ordinal 23 — Riansares 4R — método, presupuestos, SEO, catálogo y plantillas / 19. SEO local y GEO / 19.2. Zonas y términos.
- [S2] `R4R_20_CASOS_OFICINAS_CUEVAS_BANOS_Y_FILTROS.md` ordinal 32 — Riansares 4R — casos de oficinas, cuevas, baños y filtros comerciales / 14. Caso I — Filtros comerciales: trabajo pequeño claro y obra grande fuera de capacidad / 14.1. Montaje de mobiliario en un baño local.
- [S3] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 47 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 10. Caso E — Inodoro antiguo, cisterna y reparación mínima viable / 10.4. Reducir alcance.
- [S4] `R4R_30_METODO_PRESUPUESTOS_SEO_CATALOGO_Y_PLANTILLAS.md` ordinal 33 — Riansares 4R — método, presupuestos, SEO, catálogo y plantillas / 20. Mediciones y croquis para reconstruir planos / Artículos derivados.
- [S5] `R4R_30_METODO_PRESUPUESTOS_SEO_CATALOGO_Y_PLANTILLAS.md` ordinal 6 — Riansares 4R — método, presupuestos, SEO, catálogo y plantillas / 15. Presupuestación con criterio / 15.4. Rebajar alcance, no regalar trabajo.

## Extra independiente 12 — ES — independent-12

Nuestra oficina tiene muchas paredes de cristal y resulta difícil mantener conversaciones privadas. ¿Cómo mejoraríais la acústica sin perder la sensación de espacio abierto?

**Cobertura:** INSUFFICIENT. **Valoración:** INCORRECTA/SIN_RESPALDO.

§11.2 respalda el tabique opaco de lana mineral/doble placa. No dice que mantenga apertura visual ni resuelve privacidad a través de cristal. La afirmación de mantener apertura visual carece de respaldo.

**Consulta enviada al embedding:** Nuestra oficina tiene muchas paredes de cristal y resulta difícil mantener conversaciones privadas. ¿Cómo mejoraríais la acústica sin perder la sensación de espacio abierto?

**POSTGRES — respuesta:**

```json
"Para mejorar la acústica en una oficina con paredes de cristal sin perder la sensación de espacio abierto, se puede considerar la instalación de un nuevo tabique acústico, que incluye perfilería metálica, lana mineral, doble placa por ambas caras, bandas y sellado, y acabado para pintar. Esta solución permite mantener la apertura visual mientras se mejora la privacidad y la acústica. [S2]"
```

Abstención: `false`.

Citas devueltas: [S2] `R4R_20_CASOS_OFICINAS_CUEVAS_BANOS_Y_FILTROS.md` ordinal 4 — Riansares 4R — casos de oficinas, cuevas, baños y filtros comerciales / 11. Caso F — Reforma de oficinas y aseos corporativos / 11.2. Redistribución.

Fragmentos finales, en orden:

- [S1] `R4R_20_CASOS_OFICINAS_CUEVAS_BANOS_Y_FILTROS.md` ordinal 36 — Riansares 4R — casos de oficinas, cuevas, baños y filtros comerciales / 14. Caso I — Filtros comerciales: trabajo pequeño claro y obra grande fuera de capacidad / 14.5. Filtro antes de visitar.
- [S2] `R4R_20_CASOS_OFICINAS_CUEVAS_BANOS_Y_FILTROS.md` ordinal 4 — Riansares 4R — casos de oficinas, cuevas, baños y filtros comerciales / 11. Caso F — Reforma de oficinas y aseos corporativos / 11.2. Redistribución.
- [S3] `R4R_00_IDENTIDAD_CRITERIOS_Y_VOZ_EDITORIAL.md` ordinal 21 — Riansares 4R — identidad, criterios y voz editorial / 4. Reglas de factualidad para el LLM / 4.5. Privacidad y anonimización.
- [S4] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 8 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 6. Caso A — Patio, solera, pavimento y drenaje / 6.4. Hormigón de camión y ejecución manual / Patio interior o pasillo estrecho.

**S3:** fragmentos idénticos en texto/orden/metadatos a PG; respuesta reutilizada explícitamente, sin segunda generación.

## Extra independiente 13 — ES — independent-13

Tenemos un garaje comunitario donde aparecen charcos después de las lluvias, aunque aparentemente no entra agua por la puerta. ¿Qué posibles causas habría que investigar?

**Cobertura:** PARTIAL. **Valoración:** PARCIAL.

§4.3 y §12.8 permiten hipótesis generales de agua/filtración/humedad lateral. La respuesta las enumera sin diagnóstico definitivo, pero introduce garantía no solicitada y no desarrolla investigación de lluvia/garaje.

**Consulta enviada al embedding:** Tenemos un garaje comunitario donde aparecen charcos después de las lluvias, aunque aparentemente no entra agua por la puerta. ¿Qué posibles causas habría que investigar?

**POSTGRES — respuesta:**

```json
"Deberían investigar posibles entradas activas de agua, ya que la garantía no cubre este tipo de causas. Las posibles causas incluyen filtración, condensación, humedad lateral o incluso una entrada activa de agua. [S2]"
```

Abstención: `false`.

Citas devueltas: [S2] `R4R_20_CASOS_OFICINAS_CUEVAS_BANOS_Y_FILTROS.md` ordinal 18 — Riansares 4R — casos de oficinas, cuevas, baños y filtros comerciales / 12. Caso G — Cueva o bodega antigua: estabilización con mortero de cal y malla / 12.8. Garantía y límites.

Fragmentos finales, en orden:

- [S1] `R4R_00_IDENTIDAD_CRITERIOS_Y_VOZ_EDITORIAL.md` ordinal 19 — Riansares 4R — identidad, criterios y voz editorial / 4. Reglas de factualidad para el LLM / 4.3. Humedades.
- [S2] `R4R_20_CASOS_OFICINAS_CUEVAS_BANOS_Y_FILTROS.md` ordinal 18 — Riansares 4R — casos de oficinas, cuevas, baños y filtros comerciales / 12. Caso G — Cueva o bodega antigua: estabilización con mortero de cal y malla / 12.8. Garantía y límites.
- [S3] `R4R_20_CASOS_OFICINAS_CUEVAS_BANOS_Y_FILTROS.md` ordinal 32 — Riansares 4R — casos de oficinas, cuevas, baños y filtros comerciales / 14. Caso I — Filtros comerciales: trabajo pequeño claro y obra grande fuera de capacidad / 14.1. Montaje de mobiliario en un baño local.
- [S4] `R4R_20_CASOS_OFICINAS_CUEVAS_BANOS_Y_FILTROS.md` ordinal 31 — Riansares 4R — casos de oficinas, cuevas, baños y filtros comerciales / 14. Caso I — Filtros comerciales: trabajo pequeño claro y obra grande fuera de capacidad.
- [S5] `faqs/R4R_40_ALL_FAQ.md` ordinal 67 — Riansares 4R — todas las preguntas frecuentes / Garantía / FAQ-051 — ¿La garantía cubre una nueva entrada de agua?.

**S3:** fragmentos idénticos en texto/orden/metadatos a PG; respuesta reutilizada explícitamente, sin segunda generación.

## Extra independiente 14 — ES — independent-14

Queremos rehabilitar una casa de campo que lleva treinta años abandonada. ¿Qué habría que revisar antes de decidir si merece la pena recuperarla?

**Cobertura:** PARTIAL. **Valoración:** PARCIAL.

Caso B respalda revisión de humedad/revestimientos/fontanería/ventanas; trasladar la estufa de pellets desde ese caso no es una comprobación necesaria de viabilidad. Falta evaluación estructural/profesional y no puede decidir rentabilidad.

**Consulta enviada al embedding:** Queremos rehabilitar una casa de campo que lleva treinta años abandonada. ¿Qué habría que revisar antes de decidir si merece la pena recuperarla?

**POSTGRES — respuesta:**

```json
"Antes de decidir si merece la pena recuperar una casa de campo abandonada durante treinta años, habría que revisar la presencia de humedades en paredes y techos, revestimientos desprendidos, fontanería envejecida, ventanas con bajo aislamiento y la posibilidad de adaptar la chimenea a una estufa de pellets. [S3]"
```

Abstención: `false`.

Citas devueltas: [S3] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 12 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 7. Caso B — Vivienda antigua: rehabilitar sin demoler de más.

Fragmentos finales, en orden:

- [S1] `R4R_00_IDENTIDAD_CRITERIOS_Y_VOZ_EDITORIAL.md` ordinal 7 — Riansares 4R — identidad, criterios y voz editorial / 2. Identidad de Riansares 4R / 2.3. Servicios nucleares / Rehabilitación.
- [S2] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 17 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 7. Caso B — Vivienda antigua: rehabilitar sin demoler de más / 7.5. Baño pequeño.
- [S3] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 12 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 7. Caso B — Vivienda antigua: rehabilitar sin demoler de más.
- [S4] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 47 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 10. Caso E — Inodoro antiguo, cisterna y reparación mínima viable / 10.4. Reducir alcance.
- [S5] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 13 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 7. Caso B — Vivienda antigua: rehabilitar sin demoler de más / 7.1. Contexto.

**S3:** fragmentos idénticos en texto/orden/metadatos a PG; respuesta reutilizada explícitamente, sin segunda generación.

## Extra independiente 15 — ES — independent-15

El suelo de una pista deportiva exterior se está levantando y aparecen grietas. ¿Es suficiente con cambiar el pavimento o habría que investigar qué está pasando debajo?

**Cobertura:** PARTIAL. **Valoración:** CORRECTA.

El Caso A y §7.8 respaldan comprobar base/soporte antes de pavimentar. La respuesta lo presenta como posibilidad que investigar, sin dimensionar una pista ni afirmar causa definitiva; no acredita normativa deportiva.

**Consulta enviada al embedding:** El suelo de una pista deportiva exterior se está levantando y aparecen grietas. ¿Es suficiente con cambiar el pavimento o habría que investigar qué está pasando debajo?

**POSTGRES — respuesta:**

```json
"Para determinar si es suficiente cambiar el pavimento o si se debe investigar debajo, se debe considerar que el levantamiento y las grietas pueden indicar problemas en la base o en la solera. La evidencia indica que la preparación, la base y la solera son fundamentales para una correcta ejecución del pavimento. Por lo tanto, es necesario investigar qué está pasando debajo para asegurar una solución adecuada. [S2]"
```

Abstención: `false`.

Citas devueltas: [S2] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 2 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 6. Caso A — Patio, solera, pavimento y drenaje.

Fragmentos finales, en orden:

- [S1] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 20 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 7. Caso B — Vivienda antigua: rehabilitar sin demoler de más / 7.8. Suelos.
- [S2] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 2 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 6. Caso A — Patio, solera, pavimento y drenaje.
- [S3] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 8 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 6. Caso A — Patio, solera, pavimento y drenaje / 6.4. Hormigón de camión y ejecución manual / Patio interior o pasillo estrecho.
- [S4] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 7 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 6. Caso A — Patio, solera, pavimento y drenaje / 6.4. Hormigón de camión y ejecución manual / Patio exterior con acceso.
- [S5] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 44 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 10. Caso E — Inodoro antiguo, cisterna y reparación mínima viable / 10.2. Piezas que no deben confundirse / Fijación al suelo.

**S3:** fragmentos idénticos en texto/orden/metadatos a PG; respuesta reutilizada explícitamente, sin segunda generación.

## Extra independiente 16 — ES — independent-16

Tenemos una piscina comunitaria que pierde agua, pero no sabemos si está agrietada o si falla alguna tubería. ¿Cómo se puede localizar la avería?

**Cobertura:** INSUFFICIENT. **Valoración:** GENÉRICA.

Regla general de no diagnosticar humedad por foto no enseña a localizar fuga de piscina. No inventa ensayo, pero aparenta responder con una lista genérica y sin reconocer claramente ese límite.

**Consulta enviada al embedding:** Tenemos una piscina comunitaria que pierde agua, pero no sabemos si está agrietada o si falla alguna tubería. ¿Cómo se puede localizar la avería?

**POSTGRES — respuesta:**

```json
"Para localizar la avería en una piscina comunitaria que pierde agua, se debe considerar que nunca se puede afirmar una causa definitiva solo por una foto, y que pueden coexistir varias causas como capilaridad, humedad lateral, filtración, fuga, condensación, etc. [S2]"
```

Abstención: `false`.

Citas devueltas: [S2] `R4R_00_IDENTIDAD_CRITERIOS_Y_VOZ_EDITORIAL.md` ordinal 19 — Riansares 4R — identidad, criterios y voz editorial / 4. Reglas de factualidad para el LLM / 4.3. Humedades.

Fragmentos finales, en orden:

- [S1] `faqs/R4R_40_ALL_FAQ.md` ordinal 27 — Riansares 4R — todas las preguntas frecuentes / Licencias e IVA / FAQ-022 — ¿La reparación de una piscina aplica el 10%?.
- [S2] `R4R_00_IDENTIDAD_CRITERIOS_Y_VOZ_EDITORIAL.md` ordinal 19 — Riansares 4R — identidad, criterios y voz editorial / 4. Reglas de factualidad para el LLM / 4.3. Humedades.
- [S3] `R4R_20_CASOS_OFICINAS_CUEVAS_BANOS_Y_FILTROS.md` ordinal 28 — Riansares 4R — casos de oficinas, cuevas, baños y filtros comerciales / 13. Caso H — Dos baños contiguos en una vivienda antigua y estrecha / 13.9. Alicatado y adhesivos.
- [S4] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 18 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 7. Caso B — Vivienda antigua: rehabilitar sin demoler de más / 7.6. Cocina.
- [S5] `R4R_20_CASOS_OFICINAS_CUEVAS_BANOS_Y_FILTROS.md` ordinal 26 — Riansares 4R — casos de oficinas, cuevas, baños y filtros comerciales / 13. Caso H — Dos baños contiguos en una vivienda antigua y estrecha / 13.7. Placa en ducha y mortero de cal en zona seca.

**S3:** fragmentos idénticos en texto/orden/metadatos a PG; respuesta reutilizada explícitamente, sin segunda generación.

## Extra independiente 17 — ES — independent-17

Una nave industrial tiene una cubierta antigua que produce condensación y goteos. ¿Qué alternativas existen para corregir el problema?

**Cobertura:** INSUFFICIENT. **Valoración:** INCORRECTA/SIN_RESPALDO.

Recomienda mortero transpirable para una cubierta industrial con condensación usando una regla de humedad en muros. La fuente no acredita su adecuación en esa cubierta.

**Consulta enviada al embedding:** Una nave industrial tiene una cubierta antigua que produce condensación y goteos. ¿Qué alternativas existen para corregir el problema?

**POSTGRES — respuesta:**

```json
"Para corregir la condensación y goteos en una cubierta antigua de una nave industrial, se pueden considerar alternativas como el uso de un mortero transpirable, que mejora la compatibilidad y la evacuación de vapor, aunque no elimina una entrada activa de agua [S1]. Además, es necesario analizar la causa de la condensación, como la humedad lateral, filtración o condensación, y abordarla según su origen [S2]."
```

Abstención: `false`.

Citas devueltas: [S1] `R4R_00_IDENTIDAD_CRITERIOS_Y_VOZ_EDITORIAL.md` ordinal 19 — Riansares 4R — identidad, criterios y voz editorial / 4. Reglas de factualidad para el LLM / 4.3. Humedades; [S2] `R4R_30_METODO_PRESUPUESTOS_SEO_CATALOGO_Y_PLANTILLAS.md` ordinal 11 — Riansares 4R — método, presupuestos, SEO, catálogo y plantillas / 16. Humedades: marco general / 16.1. Diagnóstico antes del producto.

Fragmentos finales, en orden:

- [S1] `R4R_00_IDENTIDAD_CRITERIOS_Y_VOZ_EDITORIAL.md` ordinal 19 — Riansares 4R — identidad, criterios y voz editorial / 4. Reglas de factualidad para el LLM / 4.3. Humedades.
- [S2] `R4R_30_METODO_PRESUPUESTOS_SEO_CATALOGO_Y_PLANTILLAS.md` ordinal 11 — Riansares 4R — método, presupuestos, SEO, catálogo y plantillas / 16. Humedades: marco general / 16.1. Diagnóstico antes del producto.
- [S3] `R4R_30_METODO_PRESUPUESTOS_SEO_CATALOGO_Y_PLANTILLAS.md` ordinal 33 — Riansares 4R — método, presupuestos, SEO, catálogo y plantillas / 20. Mediciones y croquis para reconstruir planos / Artículos derivados.
- [S4] `R4R_30_METODO_PRESUPUESTOS_SEO_CATALOGO_Y_PLANTILLAS.md` ordinal 50 — Riansares 4R — método, presupuestos, SEO, catálogo y plantillas / 34. Glosario técnico mínimo.
- [S5] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 14 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 7. Caso B — Vivienda antigua: rehabilitar sin demoler de más / 7.2. Principio: negociar con la estructura existente.

**S3:** fragmentos idénticos en texto/orden/metadatos a PG; respuesta reutilizada explícitamente, sin segunda generación.

## Extra independiente 18 — ES — independent-18

Tengo una pared que se está poniendo feísima por abajo. La pintamos hace seis meses y ya está igual. ¿Qué haríais?

**Cobertura:** SUPPORTED. **Valoración:** INCORRECTA/SIN_RESPALDO.

Responde pintar todo el techo a deterioro recurrente abajo de una pared. §4.3, §7.3 y FAQ-025/026 ofrecen investigación de humedad, pero no recupera la causa; ni siquiera aplica la advertencia de FAQ-026 que sí llega.

**Consulta enviada al embedding:** Tengo una pared que se está poniendo feísima por abajo. La pintamos hace seis meses y ya está igual. ¿Qué haríais?

**POSTGRES — respuesta:**

```json
"Pintar solo los círculos puede dejar diferencias de blanco, cambios de textura, brillos distintos, bordes visibles y un aspecto de parche, por lo que conviene pintar el paño completo cuando sea viable. [S1]"
```

Abstención: `false`.

Citas devueltas: [S1] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 34 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 9. Caso D — Techo manchado después de una fuga ya reparada / 9.4. Por qué pintar todo el techo.

Fragmentos finales, en orden:

- [S1] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 34 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 9. Caso D — Techo manchado después de una fuga ya reparada / 9.4. Por qué pintar todo el techo.
- [S2] `faqs/R4R_40_ALL_FAQ.md` ordinal 32 — Riansares 4R — todas las preguntas frecuentes / Humedades / FAQ-026 — ¿Se puede pegar placa de yeso a una pared húmeda?.
- [S3] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 38 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 10. Caso E — Inodoro antiguo, cisterna y reparación mínima viable / 10.2. Piezas que no deben confundirse.
- [S4] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 18 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 7. Caso B — Vivienda antigua: rehabilitar sin demoler de más / 7.6. Cocina.
- [S5] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 44 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 10. Caso E — Inodoro antiguo, cisterna y reparación mínima viable / 10.2. Piezas que no deben confundirse / Fijación al suelo.

**S3:** fragmentos idénticos en texto/orden/metadatos a PG; respuesta reutilizada explícitamente, sin segunda generación.

## Extra independiente 19 — ES — independent-19

¿Sale más barato arreglar lo que tengo o hacerlo nuevo?

**Cobertura:** PARTIAL. **Valoración:** ABSTENCIÓN_JUSTIFICADA.

Consulta ambigua sin objeto/material/alcance. Abstenerse evita inventar precio; pedir aclaración sería más útil. No se considera fallo por falta de respuesta universal.

**Consulta enviada al embedding:** ¿Sale más barato arreglar lo que tengo o hacerlo nuevo?

**POSTGRES — respuesta:**

```json
""
```

Abstención: `true`.

Citas devueltas: ninguna.

Fragmentos finales, en orden:

- Ninguno.

**S3:** fragmentos idénticos en texto/orden/metadatos a PG; respuesta reutilizada explícitamente, sin segunda generación.

## Extra independiente 20 — ES — independent-20

Quiero convertir un sótano en una habitación. ¿Qué problemas podría encontrar?

**Cobertura:** PARTIAL. **Valoración:** PARCIAL.

Riesgos generales de cambios de distribución/instalaciones y acceso están documentados, pero se trasladan desde vivienda/patio sin desarrollar humedad, ventilación o viabilidad de uso de sótano. No valida habitabilidad ni normativa.

**Consulta enviada al embedding:** Quiero convertir un sótano en una habitación. ¿Qué problemas podría encontrar?

**POSTGRES — respuesta:**

```json
"Al convertir un sótano en una habitación, podría encontrar problemas como riesgo estructural, refuerzos, cambios en las instalaciones, más demolición, más escombros, más incertidumbre, posibles licencias o técnico, pérdida de tiempo y presupuesto en cambios que aportan poco, según [S2]. Además, si el sótano tiene acceso indirecto o es estrecho, podría enfrentar dificultades como transporte con carretillas, mezcla en obra o vertido por fases, calor y pérdida de tiempo útil, según [S4]."
```

Abstención: `false`.

Citas devueltas: [S2] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 14 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 7. Caso B — Vivienda antigua: rehabilitar sin demoler de más / 7.2. Principio: negociar con la estructura existente; [S4] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 8 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 6. Caso A — Patio, solera, pavimento y drenaje / 6.4. Hormigón de camión y ejecución manual / Patio interior o pasillo estrecho.

Fragmentos finales, en orden:

- [S1] `R4R_30_METODO_PRESUPUESTOS_SEO_CATALOGO_Y_PLANTILLAS.md` ordinal 33 — Riansares 4R — método, presupuestos, SEO, catálogo y plantillas / 20. Mediciones y croquis para reconstruir planos / Artículos derivados.
- [S2] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 14 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 7. Caso B — Vivienda antigua: rehabilitar sin demoler de más / 7.2. Principio: negociar con la estructura existente.
- [S3] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 5 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 6. Caso A — Patio, solera, pavimento y drenaje / 6.3. Distinción entre solera y solado.
- [S4] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 8 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 6. Caso A — Patio, solera, pavimento y drenaje / 6.4. Hormigón de camión y ejecución manual / Patio interior o pasillo estrecho.
- [S5] `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` ordinal 26 — Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 8. Caso C — Entrada comunitaria con humedad y soportes antiguos / 8.4. Solución mineral recomendada.

**S3:** fragmentos idénticos en texto/orden/metadatos a PG; respuesta reutilizada explícitamente, sin segunda generación.


</details>
