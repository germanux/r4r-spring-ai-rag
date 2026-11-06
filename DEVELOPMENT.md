# Memoria de desarrollo de R4R/RAG

Recopilación del contexto y las instrucciones recibidas durante la conversación. No constituye una auditoría del código ni confirma el estado desplegado. Fecha de recopilación: 2026-11-06.

## 1. Alcance y forma de trabajar

### Instrucciones explícitas del usuario

- Priorizar cambios pequeños, enfocados y mínimos para finalizar el MVP RAG.
- Preservar los cambios existentes y respetar la estructura del proyecto.
- Evitar dependencias nuevas, frameworks adicionales y refactorizaciones ajenas al encargo.
- Revisar los mecanismos existentes antes de implementar alternativas.
- Informar brevemente de archivos modificados, funcionamiento y resultados de verificación.
- Cuando se pide un plan antes de actuar, presentar el alcance y los archivos previstos sin modificarlos hasta recibir autorización.
- No preguntar qué incluir en esta memoria: recopilar únicamente lo recordado del contexto y distinguir instrucciones de interpretaciones.

### Interpretaciones del asistente

- Cada petición autoriza su alcance concreto; las restricciones particulares de una tarea no deben convertirse automáticamente en prohibiciones generales permanentes.
- Una funcionalidad implementada y una comprobación simulada no equivalen a una validación visual o live.
- Es necesario comunicar con precisión qué se ejecutó, especialmente compilación y tests, sin dar por verificado lo que no se comprobó.

## 2. Reglas de ingeniería proporcionadas en AGENTS.md

Estas instrucciones fueron proporcionadas expresamente por el usuario como instrucciones del repositorio.

- RING coordina y nunca implementa. PC y LP son trabajadores fullstack equivalentes con una sola asignación activa cada uno y estados aislados.
- ESCALATION solo actúa ante una decisión `ESCALATE`, es de lectura fuera de la salida preparada por Ring y devuelve una decisión sustitutiva completa.
- La única fuente de tareas es `.opencode/task-plan.json`; Ring genera las asignaciones bajo `runtime/control/PC/assignment.json` y `runtime/control/LP/assignment.json`.
- La configuración de runtime/modelos de esos agentes reside en `config/r4r-agents.json`. Las credenciales son locales o externas al repositorio.
- `allowed_paths` es el único alcance de escritura; Ring no puede publicar alcances solapados. Un trabajador no busca por su cuenta la siguiente tarea.
- Validar asignación, tarea y alcance; leer instrucciones, plan, comando y memoria; ejecutar el gate antes y después del cambio; conservar los diagnósticos del primer fallo actual.
- Usar recuperación focalizada mediante CodeGraph o Code-Graph-RAG cuando ayude materialmente. Los repositorios de referencia y grafos son evidencia de solo lectura.
- `CONTINUE` no desbloquea tareas bloqueadas; `RETRY_AUTHORIZED` requiere autorización válida y solo permite una recuperación adicional.
- Las sesiones de modelos se ejecutan mediante OpenCode. El código productivo no debe invocar ni importar el controlador/CLI Codex histórico archivado.
- El controlador determinista `r4r_worker` valida, crea checkpoints y commits. Las sesiones de modelo no escriben historial Git.
- No ejecutar `git add`, `git commit`, `git reset`, `git checkout`, `git merge` ni `git push` desde una sesión de modelo.
- Rechazar errores de espacios staged y unstaged antes de gates caros. El controlador rechaza cambios fuera de alcance e historial no fast-forward.
- Las decisiones semánticas de Ring requieren un resumen durable y exclusivo en `.ring-agent/evidence/<task-id>/`; timestamps o evidencia sin cambios no justifican commits de coordinación.
- Runtime, progreso, memoria, `.opencode/current/**`, logs completos, binarios, PID y locks son estado generado local ignorado.
- Límites de sesiones: delta de contexto de 64 KiB; aviso a 80.000 tokens; parada a 120.000 tokens o 30 pasos; máximo de 90 minutos; una concesión determinista de recuperación por tarea bloqueada.
- Backend Java 21, Spring Boot y abstracciones Spring AI; no añadir un cliente HTTP Ollama escrito a mano.
- Flyway controla el esquema. Frontend Angular 17 strict para la aplicación Angular del proyecto.
- Tests unitarios y Playwright sin LLM real. PostgreSQL en Docker para los gates del proyecto.
- No buscar recursivamente en `frontend/node_modules/**`, `frontend/dist/**`, `frontend/.angular/**`, `node_modules/**`, `target/**`, `runtime/**`, `.r4r/**` o `.git/**`.
- La aceptación de una tarea requiere su gate determinista exacto verde, alcance limpio y registro del controlador.

## 3. Configuración, modelos y límites del RAG

### Instrucciones explícitas del usuario

- Mantener `top-k: 3` y `min-score: 0.63`.
- No cambiar embeddings, índice PostgreSQL, modelos, endpoints o configuración productiva salvo autorización concreta posterior.
- No modificar ingesta ni reingestar documentos en las tareas de interfaz, runtime o failover.
- Reutilizar el `.env` existente y conservar las variables actuales, sin duplicarlas.
- Configuración solicitada para el failover:
  - `RAG_OLLAMA_BASE_URL=http://192.168.1.209:11434`.
  - `RAG_EMBEDDING_MODEL=nomic-embed-text`.
  - `RAG_GENERATION_MODEL=qwen2.5:7b`.
  - `RAG_PC_OLLAMA_BASE_URL=http://192.168.1.207:11434`.
  - `RAG_PC_GENERATION_MODEL=qwen3:8b`.
- Verificar cómo el `.env` se propaga a Spring y adaptar solo lo necesario.
- Los prompts, retrieval, embeddings y citas deben conservarse durante el trabajo de failover y actualización del runtime.

### Evolución explícita del alcance

- Al principio se pidió no añadir endpoints; posteriormente se autorizó expresamente crear el endpoint de información de runtime.
- Inicialmente se pidió adaptar solo `index_es.html`; posteriormente se autorizó aplicar los cambios a la versión inglesa.
- Se indicó volver al modelo 7B para cerrar el MVP; después se solicitó preferencia por Qwen3 8B en el PC con Qwen2.5 7B en el portátil como respaldo.

## 4. Idioma y calidad de respuestas

### Instrucciones explícitas del usuario

- Enviar siempre `responseLanguage`: `es` desde la página española y `en` desde la inglesa.
- `translateToSpanish()` se usa exclusivamente para consultas inglesas.
- Mantener `retrievalQuestion` para búsqueda vectorial y `question` original para el LLM.
- El idioma solicitado debe determinar explícitamente el idioma de respuesta, sin instrucciones contradictorias. Si falta, conservar compatibilidad con el comportamiento anterior.
- No traducir posteriormente respuestas ni metadatos de citas.
- Responder directamente, en 1–3 frases cuando baste, solo con hechos respaldados por los fragmentos recuperados.
- Evitar información tangencial, repetición, afirmaciones inventadas y citas innecesarias.
- No forzar respuestas sin fundamento ni modificar un test para hacerlo pasar.
- Investigar la falta de recuperación de FAQ-001 para la pregunta de datos necesarios al solicitar un presupuesto. Si la solución requiere completar documentación y reingestarla, indicarlo.

## 5. Citas y abstenciones

### Instrucciones explícitas del usuario

- Normalizar referencias agrupadas como `[S1, S2]` a `[S1][S2]`.
- Validar que los identificadores citados existan entre las fuentes recuperadas.
- Devolver únicamente fuentes realmente referenciadas, conservando sus etiquetas y la compatibilidad de la API.
- Mantener `__R4R_ABSTAIN__` como mecanismo principal de abstención.
- Reconocer también `__R4R_ABSTINERSE__` y `__R4R_ABSTINENT__`.
- Detectar abstenciones inequívocas en español e inglés, incluidas respuestas de varias frases con referencias intercaladas.
- Una abstención debe devolver `abstained: true`, `answer: ""` y `citations: []`.
- Una respuesta útil con advertencias o falta de información sobre un detalle debe conservarse.
- No añadir llamadas al LLM para clasificar abstenciones.
- Añadir pruebas de referencias individuales, agrupadas e inválidas, abstenciones ES/EN y contraejemplos útiles.
- Conservar un test con la respuesta real inglesa sobre falta de evidencia para los datos de solicitud de presupuesto.

## 6. Tests y verificación

### Instrucciones explícitas del usuario

- No ejecutar tests live ni desplegar servicios durante estas tareas; el usuario realiza esas comprobaciones.
- Los tests live contienen 15 preguntas por idioma: 12 de dominio y 3 ajenas.
- Reformular solo las preguntas prácticamente coincidentes con ejemplos de interfaz, manteniendo tema y objetivo.
- En inglés, conservar `retrievalQuestion` en español, equivalente semánticamente y sin traducciones excesivamente literales.
- No cambiar comprobaciones, variables habilitadoras ni límites de frecuencia de esos tests.
- Mantener los 22 segundos entre pruebas: decisión deliberada del usuario que sustituyó el valor anterior visto durante el trabajo.
- El test español debe enviar explícitamente `responseLanguage: "es"`.
- Se han solicitado compilación y tests unitarios en tareas concretas. En otras se reservó la compilación al usuario; posteriormente se autorizó ejecutarla para investigar fallos.
- Para el failover se autorizó expresamente `maven test`, manteniendo excluidos tests live, compilaciones completas y despliegues.
- Añadir pruebas simuladas para PC disponible, caída, recuperación, ambos caídos y actualización del runtime tras consultas.

## 7. Interfaz de demo estática

### Instrucciones explícitas del usuario

- Referencia visual: `front-athagon-r4r-static/r4r-rag-renders/Demo RAG verificable con fuentes.png`.
- Mantener colores, logotipo e identidad Athagon Labs.
- Demo moderna, atractiva, directa y orientada a reclutadores y responsables técnicos con poco tiempo.
- Portada española: «RAG con respuestas verificables» y «Spring Boot · Spring AI · pgvector · Ollama».
- Una frase explicativa, menos tarjetas redundantes y espacios, y demo visible pronto.
- Dos paneles equilibrados: pregunta a la izquierda y respuesta a la derecha; textarea amplio y botón destacado.
- Al enviar, retirar inmediatamente respuesta y fuentes anteriores; mostrar mensaje de preparación, barra indeterminada y tres líneas skeleton suaves.
- Sustituir la carga por el resultado con transición discreta. Diferenciar abstenciones y errores.
- Sin porcentajes ficticios ni temporizadores añadidos para simular progreso.
- Mantener Copiar, responsive de escritorio/móvil y todas las fuentes dentro de un desplegable inicialmente cerrado.
- Unificar el aspecto de los desplegables de endpoint, fuentes y configuración de runtime para que parezcan botones.
- Hacer desplegables los cuatro bloques de arquitectura, con sus explicaciones dentro.
- «Probar la demo» baja al formulario y lanza «¿Qué tipo de reformas hace esta empresa? ¿Casas y oficinas?».
- Esa pregunta pertenece solo al botón principal, no a los ejemplos inferiores.
- Mantener los tres ejemplos inferiores bajo «Más ejemplos rápidos».
- Mostrar el tiempo real de la petición en el detalle de respuesta.
- Reutilizar HTML, CSS y JavaScript existentes sin nuevas dependencias y conservar la lógica de peticiones RAG.
- Adaptar los textos de todos estos elementos a inglés tras la autorización posterior.

## 8. Runtime e identificación de equipos

### Instrucciones explícitas del usuario

- Obtener la configuración efectiva desde el backend, aprovechando `pom.xml`, propiedades y entorno.
- Mostrar datos breves en el resumen técnico; versiones y modelo completo dentro de la configuración desplegable.
- Mostrar PostgreSQL y pgvector como tecnología de datos, sin confundirlo con el nombre de una base.
- Informar de Java, Spring Boot, Spring AI, embeddings, recuperación y despliegue cuando estén disponibles.
- Investigar qué datos ofrece Ollama sobre hardware y añadir solo los disponibles.
- Identificar primero por la IP del endpoint real:
  - `192.168.1.209`: Portátil ASUS, `PC-ASUS-ZORIN`, Intel i7-7700HQ, 32 GB RAM, NVIDIA GTX 1050 con 4 GB.
  - `192.168.1.207`: PC principal, `PC-GIGA-ZORUX`, Intel i9-14900KF, 64 GB DDR5, NVIDIA RTX 4060 con 8 GB.
- Solo si la IP no identifica el equipo: Qwen 2.5 implica portátil; Qwen 3 de 8B o superior implica PC principal.
- Proveedor externo por API: «Modelo frontera», mostrando proveedor y modelo sin hardware local. Sin coincidencia: «Equipo no identificado».
- No publicar IP privadas ni añadir sistemas de monitorización.
- Distinguir modelo configurado, último modelo usado, modelos actualmente en memoria y proveedor no disponible.
- Refrescar automáticamente los detalles tras cada petición RAG en español e inglés.
- Obtener memoria/modelos cargados del Ollama que respondió, con comprobaciones mínimas.
- Usar la información directa del router para el proveedor real; no deducirlo del tamaño del modelo cuando ya se conoce.
- Si ambos proveedores fallan, mostrar error sin atribuir una respuesta inexistente a ninguno.

## 9. Precarga, permanencia y failover

### Instrucciones explícitas del usuario

- Precargar de forma asíncrona tras el arranque de Spring, sin bloquearlo y sin impedir que funcione si Ollama está inaccesible.
- Usar el mecanismo oficial de precarga, sin generar una respuesta ni descargar modelos; evitar precargas duplicadas.
- Usar siempre modelo y URL configurados; al reiniciar con otro modelo, no conservar precargas programadas del anterior.
- Permanencia deseada: tres horas de inactividad. Respetar `OLLAMA_KEEP_ALIVE=3h` y ajustar Spring AI si envía un valor que lo sobrescriba.
- No crear tareas periódicas de calentamiento o refresco para impedir la descarga.
- Se permiten consultas moderadas de estado mientras carga, sin inferencia ni recarga.
- Preferir PC para generación y usar portátil si el PC es inaccesible o no tiene el modelo disponible.
- Recuperar automáticamente la preferencia por el PC cuando vuelva, sin reiniciar Spring.
- Timeouts de conexión cortos y configurables; generación lenta no equivale a caída.
- Router sencillo con Spring AI 1.0, sin frameworks adicionales.
- Recuperar contexto y construir prompt una sola vez; reutilizarlos en fallback.
- Una abstención válida no provoca fallback. Ambos proveedores fallidos deben producir un error controlado.
- Adaptar la precarga al proveedor preferente disponible.
- Registrar proveedor y modelo reales por generación sin usar una variable global para atribuir respuestas individuales.
- Mantener separados proveedor preferente y último utilizado en runtime.
- Conservar el contrato público de respuesta RAG y el frontend durante la implementación inicial del failover; posteriormente se autorizó el refresco del frontend y la adaptación del runtime.

## 10. Preferencias de comunicación observadas

### Peticiones explícitas

- El usuario ha pedido mensajes de commit en inglés; ejemplos solicitados se entregaron con formato de commit convencional.
- El usuario pidió saber qué era más fácil para adaptar la interfaz inglesa: se explicó que copiar estructura y traducir permitía reutilizar los identificadores y el JavaScript.

### Interpretaciones del asistente

- Se prefiere una explicación breve y concreta del resultado y de las limitaciones reales.
- Los cambios deben comprobarse cuando se solicita verificación; las restricciones sobre tests live y despliegues deben respetarse incluso si se autoriza `maven test`.
- Los datos no disponibles deben identificarse como tales, sin inventar versiones, disponibilidad o hardware.
