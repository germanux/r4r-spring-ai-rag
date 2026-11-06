# Auditoría previa al tercer proveedor: R4R

**Alcance.** Cambios en `src/` del miércoles 4 y viernes 6 de noviembre de 2026 en la rama pública `main` de `germanux/r4r-spring-ai-rag`. Base de trabajo `01ff840` (6/11 a las 12:33, hora de Madrid). Se revisó también `front-athagon-r4r-static` y `docker-spring/docker-compose.yml` solo para verificar el contrato y las dependencias. No se ha desplegado, reindexado, escrito en la base de datos ni realizado commit/push.

## Qué cambió realmente

1. **4/11:** CORS más restrictivo (`Content-Type`, `Accept`) y pruebas de origen/preflight; corrección de telemetría de RAM/VRAM que distingue `null` de cero.
2. **6/11:** `EmbeddingModel` con su propia URL de Ollama, independiente de los dos modelos de generación; configuración y pruebas de separación.
3. **6/11:** traducción local de la consulta inglesa a español para recuperar evidencia del corpus; respuesta final conserva la pregunta e idioma original. Tests bilingües y logs de etapas RAG.

## Evaluación

| Prioridad | Hallazgo respaldado por código | Propuesta |
|---|---|---|
| Alta | `RagConfiguration.target()` configura conexión/probe, pero **no un timeout de lectura de generación**. Una conexión aceptada que se queda bloqueada puede no pasar al portátil; el frontend cancela a los 120 s. El comentario en código indica que fue deliberado para permitir inferencias lentas. | Decidir el timeout y presupuesto total por etapa **antes** de añadir un tercero. No lo cambié unilateralmente. |
| Alta | `CitedRagService` permite una respuesta `abstention=false` **sin una sola cita** si el modelo omite `[S1]`. Hay tests que lo aceptan. | Fijar si «RAG verificable» debe exigir al menos una cita en las respuestas positivas. Activarlo ahora puede causar más abstenciones: decisión funcional pendiente. |
| Media | `LocalRetrievalQueryTranslator` dependía de un segundo pase del LLM y solo rechazaba `[S` y `R4R_ABSTAIN` con la capitalización literal. Variantes en minúsculas o salida con `<think>` podían terminar embebidas como consulta. | **Incluido en el ZIP:** validar variantes sin distinción de mayúsculas y rechazar etiquetas de razonamiento. Mantener fallo explícito en vez de declarar falta de evidencia. |
| Media | Los logs `RAG result=...` no marcaban **en qué etapa** fallaba una excepción, impidiendo distinguir traducción, embeddings/PGVector y generación en fallos reales. | **Incluido en el ZIP:** log de etapa, tipo de excepción y duración sin imprimir preguntas ni documentos; se propaga la excepción original. |
| Media | `RagRuntimeController` ofrece un `lastUsedProvider` **global**, no ligado a una petición. Con varios visitantes la interfaz podría mostrar el proveedor de otra petición. | Para producción multiusuario, incluir el proveedor en la respuesta concreta o correlacionar por identificador. No se modifica el contrato ahora. |
| Media | El endpoint permite `retrievalQuestion` suministrado por el cliente, distinto de `question`, lo que puede buscar evidencia no relacionada. La UI nueva ya no lo manda. | Evaluar si debe seguir expuesto públicamente por compatibilidad; no se cambia sin decidirlo. |
| Operativa | La traducción consume una invocación LLM adicional por pregunta inglesa y no garantiza calidad en todas las formulaciones. Los tests live de 15 preguntas son opt-in. | Medir latencias y casos ingleses reales tras despliegue autorizado. No confundir tests simulados con pruebas de producción. |

### Aspectos que se conservan

- URLs independientes para embeddings y generación; sin degradar el modelo de embeddings ni alterar dimensiones (768), Top-K (3) o score mínimo (0,63).
- No hay cambios en el orden PC → portátil ni en la semántica de failover de `GenerationRouter`.
- Los tests nuevos de aislamiento y CORS son relevantes. Los cambios de telemetría para `null`/cero son correctos según su contrato.
- El frontend envía `responseLanguage` y pregunta original, sin servicio externo de traducción.

## Verificación efectuada aquí

- `git diff --check`: correcto.
- 50 pruebas Node del frontend: **50/50 PASS**.
- `node --check` para los dos JavaScript: correcto.
- No se ha podido ejecutar `mvn test` aquí: el entorno disponible tiene Java 21 pero no el ejecutable Maven ni un wrapper en el repositorio. El documento del commit afirma **119 tests** en el entorno del autor, pero ese resultado no constituye una ejecución independiente de esta revisión.
- Los nuevos tests Java en el ZIP quedan **pendientes de ejecución** en un entorno con Maven.
- No he comprobado el estado de cambios **sin subir** en tu PC, ni el despliegue actual de Synology.

## Decisión sugerida antes del tercer failover

1. Aplicar y ejecutar las pruebas del parche de observabilidad/validación si te resulta útil.
2. Acordar un presupuesto de tiempo para traducción, consulta vectorial y generación; el timeout de UI y el del backend deben ser coherentes.
3. Decidir la política de respuestas sin citas.
4. Solo entonces incorporar el tercer `ChatModel` de OpenAI (con API key fuera de Git). Un contenedor de Ollama **no convierte** la clave de OpenAI en un modelo local compatible: el backend necesita un cliente API explícito o una pasarela compatible.

**Conclusión:** lo nuevo de `src/` no muestra una rotura general evidente; la separación de modelos está bien estructurada. Sí hay riesgos de disponibilidad y de verificación que conviene cerrar antes de presentar el tercer proveedor como failover robusto. Esta revisión no certifica el comportamiento real en el NAS.
