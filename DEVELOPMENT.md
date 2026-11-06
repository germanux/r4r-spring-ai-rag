# Criterios de desarrollo de R4R/RAG

Memoria de decisiones duraderas recibidas durante el desarrollo. Distingue instrucciones explícitas del usuario de interpretaciones del asistente. No sustituye a `AGENTS.md` ni acredita el estado desplegado.

## Arquitectura y tecnologías

### Instrucciones explícitas

- Backend con Java 21, Spring Boot y Spring AI. Reutilizar las abstracciones de Spring AI para integrar los modelos; no añadir un cliente HTTP Ollama escrito a mano.
- Flyway es responsable del esquema de la aplicación.
- PostgreSQL con pgvector es el almacén vectorial. Los gates del proyecto utilizan PostgreSQL en Docker.
- La aplicación Angular utiliza Angular 17 en modo estricto. La demo web estática reutiliza HTML, CSS y JavaScript sin dependencias adicionales.
- Respetar la estructura existente y priorizar cambios pequeños y enfocados. Evitar frameworks, dependencias y refactorizaciones ajenas al objetivo.

## Separación de responsabilidades del RAG

### Instrucciones explícitas

- Mantener separados recuperación de contexto, generación y presentación.
- `retrievalQuestion` sirve para la búsqueda vectorial; `question` conserva la pregunta original que recibe el LLM.
- En el failover, recuperar el contexto y construir el prompt una sola vez, reutilizándolos para el proveedor alternativo.
- La generación debe responder directamente con hechos respaldados por los fragmentos recuperados. Evitar invenciones, repeticiones, información tangencial y citas innecesarias.
- No forzar una respuesta cuando la evidencia sea insuficiente.
- No cambiar retrieval, embeddings, prompts, ingesta o índices como efecto secundario de cambios de infraestructura de generación, runtime o interfaz.
- Si una mejora exige completar documentación y reingestarla, comunicar esa necesidad; no modificar un test para hacerlo pasar.

## Idiomas

### Instrucciones explícitas

- La interfaz envía explícitamente `responseLanguage`: `es` o `en`.
- El idioma solicitado determina el idioma de respuesta sin instrucciones contradictorias en el prompt. Cuando no se proporciona, conservar compatibilidad con el comportamiento anterior.
- La traducción al español se utiliza exclusivamente para recuperar contexto de consultas inglesas; la generación recibe la pregunta original.
- No traducir posteriormente las respuestas generadas ni los metadatos de las citas.
- Los componentes compartidos de interfaz deben respetar ambos idiomas y evitar regresiones en una versión al modificar la otra.

## Evidencia, citas y abstenciones

### Instrucciones explícitas

- Normalizar referencias agrupadas al formato individual: `[S1, S2]` pasa a `[S1][S2]`.
- Validar que cada identificador citado exista entre las fuentes recuperadas.
- Devolver únicamente las fuentes referenciadas en la respuesta, conservando sus etiquetas y metadatos.
- Mantener `__R4R_ABSTAIN__` como mecanismo principal de abstención y reconocer variantes inequívocas emitidas por el modelo.
- Detectar también abstenciones inequívocas en lenguaje natural, en español e inglés, incluidas varias frases con referencias intercaladas.
- Una respuesta útil con advertencias o información insuficiente sobre un detalle no debe convertirse automáticamente en abstención.
- Una abstención devuelve `abstained: true`, `answer: ""` y `citations: []`.
- No añadir llamadas al LLM para clasificar abstenciones.
- Preservar el contrato público de respuesta RAG al modificar la gestión interna de proveedores.

## Configuración y credenciales

### Instrucciones explícitas

- Reutilizar el `.env` existente y conservar las variables actuales. Evitar variables duplicadas para el mismo concepto.
- Verificar cómo se propagan las variables a Spring Boot y Docker antes de añadir configuración alternativa.
- Resolver modelos y endpoints desde la configuración; no fijarlos en la lógica de generación o precarga.
- Las credenciales pertenecen fuera del repositorio o a archivos locales ignorados.
- El runtime público expone únicamente información seleccionada: modelos, versiones, parámetros y datos de equipo autorizados. No publicar credenciales ni IP privadas.

## Generación, precarga y failover

### Instrucciones explícitas

- Mantener un enrutador de generación sencillo basado en Spring AI, separado del proveedor de embeddings.
- Utilizar el proveedor preferente y pasar al respaldo si el primero es inaccesible o no dispone del modelo configurado.
- Recuperar automáticamente la preferencia cuando el proveedor vuelva a estar disponible, sin reiniciar Spring.
- Una abstención válida no activa fallback. Si ambos proveedores fallan, devolver un error controlado.
- Usar timeouts de conexión cortos y configurables. Una generación lenta no equivale a una caída.
- Precargar el modelo configurado de forma asíncrona al completar el arranque, usando el mecanismo oficial sin generar texto ni descargar modelos.
- La precarga no debe bloquear el arranque ni impedir el funcionamiento si Ollama está temporalmente inaccesible. Evitar ejecuciones duplicadas y seleccionar el proveedor preferente disponible.
- Respetar la permanencia por inactividad y evitar que Spring AI la sobrescriba con un valor incompatible.
- No crear tareas de calentamiento periódicas ni llamadas de refresco destinadas a impedir la descarga del modelo.
- Las consultas de estado pueden repetirse moderadamente mientras carga; deben ser de solo lectura y no generar inferencias ni recargar modelos.

## Trazabilidad y runtime

### Instrucciones explícitas

- Registrar el proveedor y modelo que realmente generaron cada respuesta. No usar una variable global compartida para atribuir respuestas individuales.
- Mantener separados el proveedor preferente y el último utilizado.
- Distinguir modelo configurado, modelo utilizado, modelos cargados en memoria y proveedor no disponible.
- Consultar modelos cargados y uso de memoria en el Ollama que respondió, evitando comprobaciones de disponibilidad redundantes.
- La ausencia de un modelo en `/api/ps` no implica por sí sola que Ollama o el modelo estén indisponibles.
- Identificar el equipo a partir del endpoint real; usar deducciones por modelo solo como alternativa cuando no exista identificación directa.
- Para proveedores externos, mostrar proveedor y modelo sin atribuir hardware local.
- Si falla la generación, no atribuir una respuesta inexistente a un proveedor anterior.
- Actualizar los detalles técnicos de la interfaz al finalizar cada petición, sin exigir recargar la página.

### Interpretación del asistente

- Las especificaciones conocidas del equipo y la memoria de un modelo cargado son datos distintos; deben presentarse sin confundir capacidad total con consumo.
- La información de runtime debe proceder de configuración o evidencia disponible, sin inventar versiones ni estados.

## Interfaz y experiencia de uso

### Instrucciones explícitas

- Mantener la identidad visual de Athagon Labs y un diseño responsive para escritorio y móvil.
- Priorizar contenido y claridad para personas con poco tiempo; reducir explicaciones redundantes y espacios innecesarios.
- Al iniciar una nueva consulta, retirar inmediatamente la respuesta y las fuentes anteriores y mostrar un estado de carga claro.
- El progreso indeterminado no debe mostrar porcentajes ficticios ni simular avances mediante temporizadores.
- Diferenciar visualmente respuesta, abstención y error.
- Mantener las fuentes accesibles con toda su información, utilizando desplegables para reducir saturación visual.
- Mostrar tiempos medidos de las peticiones cuando se informe de latencia.

## Pruebas y verificación

### Instrucciones explícitas

- Los tests unitarios y Playwright no deben requerir un LLM real.
- Probar el failover con proveedores simulados: disponibilidad, caída, recuperación y fallo de ambos.
- Cubrir citas válidas e inválidas, abstenciones ES/EN y respuestas útiles que contienen advertencias.
- Las preguntas de evaluación deben comprobar distintas formas de expresar el mismo objetivo, evitando copiar los ejemplos de interfaz.
- En los tests ingleses, la pregunta de recuperación en español debe ser semánticamente equivalente y natural.
- No modificar comprobaciones, variables habilitadoras o límites de frecuencia para hacer pasar tests live.
- Durante este trabajo, los tests live, ingestas y despliegues han quedado a cargo del usuario. No ejecutarlos sin autorización expresa.
- Ejecutar las verificaciones autorizadas e informar de qué se comprobó realmente, incluyendo fallos y limitaciones.

### Interpretación del asistente

- Las restricciones sobre compilar o ejecutar pruebas dependen del encargo vigente; no convertir una restricción puntual en una prohibición permanente.
- Un test simulado no acredita disponibilidad real de Ollama, comportamiento desplegado ni calidad de recuperación con el índice de producción.

## Trabajo en el repositorio

### Instrucciones explícitas

- Preservar los cambios existentes y limitar las escrituras al alcance autorizado.
- Cumplir el protocolo de asignaciones, gates y evidencias definido en `AGENTS.md` cuando se trabaje dentro del sistema de agentes del repositorio.
- El controlador determinista es responsable de checkpoints y commits; las sesiones de modelo no escriben historial Git.
- Tratar los repositorios de referencia y grafos como evidencia de solo lectura.
- No realizar búsquedas recursivas en dependencias, builds, runtime o historial Git; consultar la lista concreta de exclusiones en `AGENTS.md`.
- Mantener logs completos, binarios y estado generado en almacenamiento local ignorado.
