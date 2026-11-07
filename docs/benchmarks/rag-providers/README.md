# Benchmark RAG y proveedores — pendiente de conexión al corpus

**No se ha ejecutado todavía la comparación RAG. No hay configuración ganadora.**

NOT RUN: faltan credenciales PostgreSQL disponibles fuera de .env para conectar Spring Boot local al corpus real en solo lectura. No se han leído ni modificado .env o DEVELOPMENT.md. No se han
modificado parámetros RAG, pgvector, proveedores operativos ni credenciales.

## Tabla comparativa

| Configuración | Estado | Media total | Mediana total | Máximo total | Media embedding | Media generación | Correctas/fallidas |
| --- | --- | --- | --- | --- | --- | --- | --- |
| [A: synology → pc](embed-synology_llm-pc.md) | NOT RUN | — | — | — | — | — | Sin muestras |
| [B: pc → pc](embed-pc_llm-pc.md) | NOT RUN | — | — | — | — | — | Sin muestras |
| [C: synology → laptop](embed-synology_llm-laptop.md) | NOT RUN | — | — | — | — | — | Sin muestras |
| [D: laptop → laptop](embed-laptop_llm-laptop.md) | NOT RUN | — | — | — | — | — | Sin muestras |
| [E: pc → laptop](embed-pc_llm-laptop.md) | NOT RUN | — | — | — | — | — | Sin muestras |

## Selección exacta

[RagLiveQuestionsIT.java](../../../src/test/java/com/riansares/r4r/llm_tests/RagLiveQuestionsIT.java) y [RagLiveEnglishQuestionsIT.java](../../../src/test/java/com/riansares/r4r/llm_tests/RagLiveEnglishQuestionsIT.java), casos 02, 05 y 11. Son tres pares de casos existentes en dos clases IT; no se han creado preguntas ni cambiado expectativas.

1. **ES · RagLiveQuestionsIT · caso 2:** En una obra, ¿en qué se distinguen la base de solera y el acabado de solado?
2. **ES · RagLiveQuestionsIT · caso 5:** ¿Qué datos debe aportar el cliente para solicitar un presupuesto?
3. **ES · RagLiveQuestionsIT · caso 11:** ¿Qué datos hacen falta para orientar una reforma de baño?
4. **EN · RagLiveEnglishQuestionsIT · caso 2:** In construction, how does the concrete base differ from the finished flooring?
5. **EN · RagLiveEnglishQuestionsIT · caso 5:** What information should a customer provide to request an estimate?
6. **EN · RagLiveEnglishQuestionsIT · caso 11:** What information is needed to estimate a bathroom renovation?

Se conserva este orden para las cinco configuraciones. Top-K 3, score mínimo 0,63,
modelo nomic de 768 dimensiones y corpus constantes. La petición inglesa reproduce
el IT existente: question original, responseLanguage=en y retrievalQuestion ausente;
se incluye por tanto la normalización local del backend. No mide la traducción
MyMemory del frontend. Las expectativas siguen siendo las de los IT, incluido el
chequeo inglés y la correspondencia de referencias/citas; esto no constituye una
validación semántica exhaustiva del contenido.

## Inventario real y compatibilidad

Se consultaron /api/tags y /api/ps sin modificar modelos. Los tres servidores tienen
nomic-embed-text:latest, F16, digest
`0a109f422b47e3a30ba2b10eca18548e944e8a23073ee3f3e947efcf3c45e59f`
y 768 dimensiones. Qwen3 8B está instalado en el PC y Qwen2.5 7B en el portátil.
Una consulta inicial al portátil devolvió «No route to host»; consultas posteriores
a tags/ps sí funcionaron. No se ocultó esa disponibilidad transitoria.

Los informes previos del repositorio describen corpus de 282 filas y vector(768),
pero eso es evidencia histórica, no una verificación actual del corpus. Falta
comprobar en solo lectura esquema, dimensiones, contenido estable y proveniencia
antes/después. No se ha ejecutado SQL ni reindexación.

## CPU, GPU y co-residencia

- Synology: nomic observado residente en CPU, 389.053.152 bytes, size_vram=0.
- PC: /api/ps inicialmente vacío. RTX 4060 es hardware configurado; nvidia-smi falló
  al contactar con el driver, por lo que no hay medida local de utilización GPU.
- Portátil: qwen2.5:7b observado con 5.015.671.772 bytes de memoria y 3.102.180.638
  bytes de VRAM. Eso indica uso parcial de GPU, no un porcentaje de utilización.
- No se ha observado nomic y Qwen ejecutando estas consultas en la misma GPU.
  No puede concluirse que compartir GPU mejore/empeore el RAG sin los datos.

La comparación deberá separar latencia de carga inicial y co-residencia observada;
una única pasada de seis consultas y orden fijo no constituye una prueba de carga
ni demuestra causalidad aislada del hardware.

## Failover

| Comprobación | Estado y evidencia |
| --- | --- |
| PC disponible → PC | NOT RUN real; regresión de enrutamiento con mocks PASSED. |
| PC falso → portátil | NOT RUN real; regresión de enrutamiento con mocks PASSED. |
| Ambos locales fallan → OpenAI Luna | PASS mediante mocks; ninguna llamada de pago. |

Comando ejecutado, exclusivamente el método existente:
`mvn -Dtest=OpenAiFallbackTest#orderFailureDisableAndAbstentionNeverSpendUnnecessarily test`.
No ejecuta toda la batería de IT ni arranca Spring Boot.

## Ganadora y validación final

La selección exige seis consultas PASSED y menor media total; sin muestras no se
elige ninguna ni se modifica configuración versionada. Se mantiene la configuración
existente de referencia (embeddings Synology, generación preferente PC), sin
presentarla como ganadora. La batería completa ES/EN solo se ejecutará tras obtener
una ganadora elegible. Véase [final-validation.md](final-validation.md).

## Reproducción y bloqueo

Se compiló el código y se preparó una configuración temporal fuera del repositorio,
sin el bloque spring.config.import de dotenv. El arranque deberá usar
--spring.config.location apuntando exclusivamente a esa copia, no una ubicación
adicional que mantenga la importación original.

Las credenciales deben aportarse externamente como SPRING_DATASOURCE_URL,
SPRING_DATASOURCE_USERNAME y SPRING_DATASOURCE_PASSWORD (o fuente explícitamente
autorizada distinta de .env), sin mostrarlas en consola o informes. Cada conexión
usará default_transaction_read_only=on, Flyway desactivado y schema initialization
false. Precarga de arranque desactivada y OpenAI deshabilitado en todas las
mediciones; se registrará cualquier fallo de generación sin sustituirlo por nube.
No habrá reintentos ni llamadas extra de embedding para medir tiempos.

No se inició ningún Spring Boot en esta tarea hasta disponer de dicha conexión.
No se detuvo ningún proceso ajeno, Ollama o PostgreSQL. Sin commit, push o despliegue.
