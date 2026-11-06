# R4R — memoria y criterios de desarrollo

Memoria de trabajo, no certificación del despliegue ni sustituto de `AGENTS.md`.
Se conservan decisiones útiles a futuro y se excluyen resultados, PIDs, tiempos y
estados transitorios: esos datos pertenecen a los informes de validación.

**Procedencia:** «Instrucciones explícitas» recoge lo recibido del usuario durante
esta conversación o de `AGENTS.md`, identificado cuando corresponda. «Memoria previa»
conserva criterios del documento anterior sin atribuirlos de nuevo al usuario como
si se hubieran formulado en esta sesión. «Interpretaciones» son conclusiones del
asistente; no autorizan cambios funcionales.

## Alcance, autorización y forma de trabajar

### Instrucciones explícitas

- Revisar primero el estado Git, incluidos los cambios staged, y el último cambio
  del usuario. Preservar el trabajo existente y limitar las escrituras al encargo.
- Hacer modificaciones pequeñas y enfocadas. No aprovechar una corrección para
  refactorizar extensamente o modificar otros apartados.
- No eliminar funcionalidades sin autorización expresa. No sustituir servicios
  externos por modelos locales, ni al contrario, sin consultar.
- No cambiar arquitectura, proveedores, contratos API ni configuración operativa
  fuera del alcance aprobado. Antes de una decisión con un compromiso funcional,
  de coste, rendimiento o dependencia significativo, detenerse y preguntar.
- Si aparecen problemas adicionales, informar y proponer soluciones; no ampliar
  unilateralmente el alcance ni ocultar el fallo para cerrar la tarea.
- Respetar las exclusiones de cada tarea: frontend, CSS, backend, RAG o Docker.
  Las restricciones de una fase no deben confundirse con el objetivo de otra.
- No desplegar automáticamente. Terminar la tarea autorizada y esperar validación
  manual antes de continuar con cambios que requieran aprobación.
- Mantener este archivo como memoria de desarrollo; no crear otro Markdown paralelo.

## Arquitectura, configuración y seguridad

### Instrucciones explícitas

- Según `AGENTS.md`: backend Java 21, Spring Boot y abstracciones de Spring AI;
  no escribir un cliente HTTP Ollama propio. Flyway es responsable del esquema.
- PostgreSQL/pgvector almacena los vectores. Los gates usan PostgreSQL en Docker.
  Angular utiliza Angular 17 estricto; distinguirlo de la demo estática.
- No añadir frameworks ni dependencias a la demo para resolver estos encargos.
- Separar URL y modelo de embeddings de los de generación. Revisar la
  autoconfiguración Spring AI para que una URL global no controle ambos clientes.
- Embeddings confirmados: Synology, `nomic-embed-text:latest`, 768 dimensiones;
  desde el PC, `http://192.168.1.203:11434`; desde Docker en Synology,
  `http://ollama-embeddings:11434`. Aplicarlos a ingesta y consultas.
- Generación configurada por el usuario: PC-GIGA-ZORUX, `qwen3:8b`, conservando el
  fallback existente al portátil. Confirmar la configuración realmente desplegada;
  estos datos de referencia no acreditan por sí mismos disponibilidad.
- Preferir propiedades y variables de entorno independientes. Actualizar ejemplos
  y Compose cuando forme parte del encargo, sin sobrescribir el `.env` activo.
- No borrar ni alterar modelos Ollama o su configuración. Conservar `gpt-oss:120b`
  y `gpt-oss-120b-16k:latest` en el PC para experimentos independientes; no añadirlos
  al flujo normal RAG sin autorización.
- No eliminar datos ni reindexar producción automáticamente. No cambiar el modelo
  de embeddings o reindexar producción sin justificarlo y solicitar autorización.
- Corregir CORS solo para orígenes legítimos de desarrollo y producción. Incluir
  `http://localhost:8000`, `http://127.0.0.1:8000`, los JetBrains aún utilizados y
  `https://athagon.tech`; verificar GET, POST, OPTIONS y cabeceras de preflight.
- No abrir CORS indiscriminadamente con `*` ni desactivar Spring Security para
  resolverlo. Comprobar si Security se utiliza realmente antes de atribuirle el fallo.
- Distinguir CORS, conectividad, URL incorrecta y contenido mixto. Un HTTP 403
  comprobado debe distinguirse de un fallo de conexión en la interfaz.

### Memoria previa conservada

- Reutilizar configuración y variables existentes, evitando duplicados para un
  mismo concepto. Comprobar su propagación a Spring Boot y Docker.
- Resolver modelos/endpoints desde configuración, no fijarlos en la lógica.
- Credenciales fuera del repositorio o en archivos locales ignorados.
- El runtime público expone información seleccionada, no credenciales ni IP privadas.

## Traducción, recuperación y respuesta

### Instrucciones explícitas

- **MyMemory desde JavaScript es la vía principal** para traducir preguntas inglesas
  al español antes de recuperar documentos. No sustituirlo por inferencia local.
- Un segundo traductor externo solo puede integrarse tras presentar limitaciones
  y obtener aprobación: debe ser gratuito, suficientemente fiable, compatible con
  CORS y sin exposición de credenciales. No introducir servicios externos de pago.
- Si falla MyMemory y no hay segundo proveedor aprobado, utilizar directamente el
  fallback local existente. Aplicar timeouts razonables y evitar reintentos innecesarios.
- Conservar `question` original, usar `retrievalQuestion` exclusivamente para
  recuperar y mantener `responseLanguage` para la respuesta final. No concatenar
  indiscriminadamente la pregunta original y la traducción en una única búsqueda.
- Si falla la traducción externa, no enviar el original inglés como una traducción
  válida: eso impediría activar el fallback del backend.
- No ejecutar traducción local adicional cuando ya se proporciona una traducción válida.
- Investigar la etapa exacta donde se pierden evidencias: candidatos antes/después
  de filtros, fragmentos, scores, Top-K, score mínimo, idioma y validación posterior.
  No asumir que el generador es la causa de una abstención.
- Comparar consultas semánticamente equivalentes ES/EN sobre el mismo corpus.
- No bajar arbitrariamente umbrales ni eliminar protecciones contra alucinaciones
  para lograr respuestas. Mantener la abstención cuando falte evidencia suficiente.
- Mantener el idioma solicitado en la respuesta. Los cambios de UI, runtime o
  infraestructura no autorizan modificar embeddings, recuperación, generación o failover.

### Memoria previa conservada

- Recuperar contexto y construir el prompt una sola vez para reutilizarlos en el
  proveedor de respaldo; una abstención válida no activa failover.
- Normalizar `[S1, S2]` a `[S1][S2]`, validar identificadores y devolver únicamente
  las fuentes referenciadas, conservando etiquetas y metadatos.
- Mantener el token de abstención y reconocer variantes inequívocas y abstenciones
  naturales ES/EN sin convertir automáticamente una respuesta útil con advertencias
  en abstención. No añadir una inferencia para clasificar abstenciones.
- Contrato de abstención: `abstained: true`, `answer: ""`, `citations: []`.
  Preservar el contrato público y no retraducir respuestas o metadatos de citas.

## Demo estática, idiomas y preferencias

### Instrucciones explícitas

- Estructura: `front-athagon-r4r-static/index.html`, `js/app.js`, `js/i18n.js`,
  `js/app.test.cjs` y `styles/styles.css`. Mantener un único HTML y JavaScript compartido.
- Diccionario ES/EN, `t(key)` y atributos `data-i18n` y equivalentes para textos,
  placeholders y accesibilidad. Traducir también errores, estados y runtime.
- Un solo `languageSelect` en el encabezado, arriba a la derecha. Cambiar idioma
  sin recargar ni perder pregunta, respuesta, fuentes o endpoint. Actualizar `lang`,
  título y descripción; mantener equivalencias naturales ES/EN.
- `localStorage`: `r4r.language`, `r4r.apiBaseUrl`, `r4r.lastQuestion`.
  Restaurar idioma guardado; si falta, `navigator.language`: español para `es`,
  inglés para el resto. Restaurar la última pregunta sin enviarla automáticamente;
  guardarla al enviarla. Si el almacenamiento falla, usar valores predeterminados.
- Endpoint predeterminado: `https://r4r-api.athagon.tech`. Presets: producción,
  Synology (`http://192.168.1.203:18080`), local (`http://127.0.0.1:8080`) y personalizado.
  Mantener URL editable y selector dentro de «Endpoint de la demo».
- Guardar automáticamente presets; validar y guardar personalizados con su botón.
  Admitir HTTP/HTTPS válidos y evitar duplicar configuración entre HTML y JavaScript.
- No introducir fallback automático entre endpoints o entre HTTP y HTTPS: la
  selección debe ser explícita y conservarse como preferencia.
- Arquitectura, runtime/detalles técnicos y «Qué demuestra» plegados inicialmente.
  Controles identificables, chevrón, hover/focus, teclado y `aria-expanded` coherente;
  los enlaces de navegación deben abrir una sección cerrada a la que apunten.
- Conservar funciones existentes: consultas RAG, ejemplos rápidos, copia de
  respuestas, fuentes, preferencias y actualización del runtime.
- Mantener diseño ATHAGON LABS, colores y tipografía. Verificar escritorio, tablet
  y móvil, sin desbordamientos, solapamientos ni saltos al alternar idiomas.

### Memoria previa conservada

- Retirar respuesta y fuentes anteriores al iniciar otra consulta; mostrar carga
  clara sin porcentajes ficticios ni progreso simulado por temporizadores.
- Diferenciar respuesta, abstención y error. Mantener información de las fuentes
  accesible mediante desplegables y mostrar tiempos medidos.

## Claridad técnica y runtime

### Instrucciones explícitas

- La página debe ser comprensible para reclutadores y visitantes no técnicos,
  además de desarrolladores y arquitectos. Integrar mejoras sin añadir secciones innecesarias.
- Mantener los títulos acordados: «IA autoalojada · RAG con respuestas verificables»
  y «Self-Hosted AI · Verifiable RAG». Mostrar Java, Spring Boot, Spring AI,
  pgvector y Ollama; explicar inferencia local, evidencia, citas y respaldo automático.
- Usar **NVIDIA GeForce RTX 4060** y explicar que es la tarjeta gráfica del equipo
  principal utilizada para acelerar la IA. No limitarse a «RTX 4060».
- Explicar Ollama como software para ejecutar modelos de lenguaje en equipos locales.
  Self-Hosted AI significa modelos en ordenadores propios, sin depender de un proveedor
  externo de inferencia para las generaciones locales. Automatic failover significa
  intentar usar el respaldo si falla el equipo principal, no garantizar su disponibilidad.
- No afirmar que los datos nunca salen a Internet ni que todo funciona sin nube.
  El frontend es público y las consultas inglesas se envían al traductor externo.
- Dos niveles: explicación principal sencilla y detalles técnicos desplegables.
  Evitar tarjetas/datos redundantes; conservar versiones, hardware y parámetros donde aporten valor.
- Reflejar el flujo web → Spring Boot/Spring AI → recuperación vectorial → inferencia,
  distinguiendo Ollama de embeddings en Synology del de generación PC/portátil.
- Distinguir proveedor configurado, modelo de la última respuesta y disponibilidad
  realmente comprobada. No presentar configuración estática como métricas en tiempo real.
- Mostrar el modelo realmente utilizado; indicar failover solo si lo informa el
  backend. No inventar estados dinámicos, métricas, versiones o datos de memoria.
- Interpretar los valores de Ollama `/api/ps` como bytes y convertir correctamente
  a GiB; no estimar métricas ausentes ni confundir consumo con capacidad del equipo.

### Memoria previa conservada

- Consultar memoria del Ollama que respondió; la ausencia en `/api/ps` no demuestra
  que un proveedor o modelo sea inaccesible. Identificar equipo por endpoint real.
- No atribuir una generación fallida al proveedor de una respuesta anterior.
- Actualizar runtime al terminar la petición sin exigir recarga de página.
- Volver al proveedor preferente al recuperarse. Si ambos fallan, devolver error
  controlado. Una inferencia lenta no equivale automáticamente a caída.
- Precarga asíncrona del modelo configurado, sin generar texto, descargar modelos,
  bloquear arranque o añadir calentamientos periódicos. Estado y sondeos son de
  solo lectura; no deben generar inferencias ni forzar modelos a permanecer cargados.

## Pruebas, evidencias y comunicación de resultados

### Instrucciones explícitas

- Compilar o devolver HTTP 200 no acredita un RAG correcto. Comprobar respuesta,
  abstención, idioma, fuentes, citas y evidencia recuperada.
- En validaciones integrales autorizadas, ejecutar unitarios Java, integraciones
  disponibles, tests Maven relevantes y JavaScript. Cubrir controladores, servicios,
  traducción/fallback, errores, timeouts, vacíos, embeddings, recuperación y pgvector.
- Probar MyMemory correcto y fallido, campos enviados, español sin traducción
  innecesaria, inglés con recuperación española y respuesta en el idioma solicitado.
- Probar casos normales, fuera de dominio, sin evidencia, citas, fallo de conexión,
  proveedor principal y respaldo. Verificar la UI y consola además de la API.
- Distinguir **PASSED / FAILED / SKIPPED / NOT RUN**, con causa exacta si falta
  infraestructura. No presentar mocks como integración satisfactoria con servicios reales.
- Registrar latencias ES/EN por etapa cuando sea posible: traducción, embeddings,
  recuperación, contexto y generación. No introducir optimizaciones especulativas.
- No modificar expectativas o protecciones para ocultar fallos.
- Según `AGENTS.md`, unitarios y Playwright no deben depender de un LLM real.
  Separar las pruebas con servicios reales y ejecutarlas solo dentro de la autorización.
- Conservar tests nuevos junto a la suite. Si ya están integrados, no duplicarlos
  ni inventar otros por la instrucción de conservarlos. No desperdiciar pruebas
  temporales que aporten cobertura ausente.
- Entregar archivos modificados/creados, resultados reales, fallos pendientes y
  comandos de comprobación; distinguir código validado de despliegue comprobado.

## Git, agentes y preferencias de entrega

### Instrucciones explícitas

- No hacer commit, push ni despliegue sin autorización. `AGENTS.md` prohíbe a las
  sesiones de modelo `git add`, `commit`, `reset`, `checkout`, `merge` y `push`;
  el controlador determinista gestiona checkpoints e historial.
- En trabajo coordinado por OpenCode, cumplir asignación fresca, alcance
  `allowed_paths`, gate y evidencias de `AGENTS.md`. No duplicar aquí los detalles
  de perfiles/modelos o la configuración de `config/r4r-agents.json`.
- Tratar referencias y grafos como solo lectura. No buscar recursivamente en rutas
  pesadas/generadas indicadas en `AGENTS.md`; logs, binarios y estado generado
  permanecen en almacenamiento local ignorado.
- Proporcionar siempre mensaje de commit propuesto **en inglés, de dos o tres líneas**,
  preferentemente separadas por puntos y con prefijo convencional como `fix(rag):`
  o `feat(ui):`. Proponer texto no autoriza ejecutar el commit.

## Interpretaciones del asistente y límites de esta memoria

- La conversación se mantiene en español; lo interpreto como idioma preferido para
  la comunicación, salvo la preferencia explícita de mensajes de commit en inglés.
- Una restricción puntual no se convierte en prohibición permanente: por ejemplo,
  la fase sin CSS precedió a una tarea CSS autorizada. La antigua indicación de dejar
  los tests live al usuario no prevalece sobre su autorización posterior para ejecutarlos.
- Las decisiones concretas de implementación, como un timeout o una invalidación de
  caché, no son preferencias expresas del usuario si este no fijó esos valores.
- Chromium headless no abre una ventana visible. Emular móvil no equivale a usar
  un dispositivo físico; declarar qué navegador y método se utilizaron.
- Una medición aislada no es una media ni un benchmark. Una configuración de referencia
  o un test simulado tampoco acredita el funcionamiento desplegado.
- Los informes de validación contienen hechos de una ejecución, no reglas permanentes.
  Consultar `docs/validation/r4r-mymemory-restoration.md` para las pruebas reales y
  fallos pendientes de esta sesión; no dar esos fallos por corregidos ni extrapolar
  resultados anteriores a un despliegue nuevo.

## Directrices de UX y presentación

Sección añadida por el encargo posterior de UX. La memoria anterior se conserva
intacta; estas instrucciones nuevas no se atribuyen retrospectivamente a lo recordado.
En presentación, las actualizaciones de este encargo prevalecen sobre decisiones
anteriores incompatibles, como mantener «Qué demuestra» cerrado o usar un titular
centrado en terminología de infraestructura.

### Instrucciones explícitas del usuario

- Priorizar visitantes no técnicos: deben entender qué hace la aplicación, cómo
  funciona a grandes rasgos y qué ventajas ofrece. Desarrolladores y reclutadores
  técnicos deben poder explorar arquitectura, tecnologías, modelos y hardware.
- Mostrar desde el primer vistazo que existe ingeniería real, con detalle progresivo.
  Evitar tanto infantilizar la interfaz como convertirla en diagnóstico de servidores.
- Orden de contenido: presentación → demo interactiva → qué demuestra → arquitectura
  → detalles técnicos. Mantener la demo como elemento principal y dar mayor visibilidad
  a las capacidades. Mejorar jerarquía sin rediseñar la identidad ni dejar huecos excesivos.
- No mostrar nombres internos de máquinas como `PC-GIGA-ZORUX` o `PC-ASUS-ZORIN`.
  Usar «PC de sobremesa / PC-Desktop», «Portátil ASUS / Laptop ASUS» y «NAS Synology».
- Mostrar equipos y hardware cuando aporten valor. NVIDIA GeForce RTX 4060 acelera
  la generación; NVIDIA GeForce GTX 1050 pertenece al portátil de respaldo. CPU,
  RAM y memoria gráfica corresponden al nivel técnico.
- Se puede indicar Linux. Si se precisa el sistema, distinguir Zorin OS de Ubuntu:
  «Linux (Zorin OS, basado en Ubuntu)» no equivale a Ubuntu puro instalado.
- Utilizar expresiones comprensibles: IA en equipos propios, respuestas basadas en
  documentos, fuentes consultables, respaldo automático y reconocimiento de falta
  de información. No explicar lo evidente ni sobrecargar la interfaz.
- Conservar Java, Spring Boot, Spring AI, Ollama, pgvector, RAG y modelos en el nivel
  adecuado. «Búsqueda inteligente en documentos» en la vista general; embeddings,
  nomic-embed-text y dimensiones en la técnica. No repetir el modelo activo por toda
  la página ni confundir configuración con disponibilidad comprobada.
- Mantener la explicación pequeña de traducción sin protagonismo, avisos destacados
  ni nuevas secciones sobre MyMemory o sus mecanismos internos.
- Conservar ES/EN, responsive, accesibilidad y navegación por teclado. Los detalles
  técnicos siguen disponibles mediante desplegables; los estados deben entenderse.
- No modificar RAG, traducción, proveedores ni API por un cambio de presentación.
  Ejecutar los tests existentes afectados sin ampliar innecesariamente la batería.
- Añadir estas reglas separadamente a la memoria existente y entregar cambios,
  archivos y pruebas. No hacer commit ni push sin autorización.

### Decisiones de presentación aplicadas en este encargo

- Titular centrado en respuestas basadas en documentos; tecnologías y NVIDIA siguen
  visibles como señales de valor técnico, con menos peso que el propósito de la demo.
- «Qué demuestra» aparece abierto tras la demo, conservando su control accesible
  de apertura/cierre. Arquitectura y detalles técnicos permanecen cerrados inicialmente.
- El runtime muestra el último modelo informado con un nombre descriptivo del equipo;
  configuración preferente, embeddings, hardware y métricas quedan en el desplegable
  avanzado. Los identificadores internos solo sirven para comparar proveedores,
  sin convertirse en nombres visibles ni alterar las decisiones del backend.
- Mantener intacto el comportamiento funcional es parte de la mejora de UX; una
  simplificación de textos no autoriza a cambiar el sistema que esos textos describen.

### Actualización de comunicación: textos de landing indicados por el usuario

- Aplicar los textos suministrados fielmente en ES/EN, sin improvisar alternativas,
  rediseñar ni modificar el funcionamiento del RAG para acomodarlo al mensaje.
- Visitantes no técnicos primero; detalles de ingeniería después. Cabecera centrada
  en formular una pregunta, comprobar la respuesta y consultar sus fuentes. Tecnologías
  visibles en una línea discreta y detalles reales disponibles más abajo.
- Titulares actualizados: «Haz una pregunta a nuestra IA con RAG» / «Ask our
  RAG-powered AI a question», «¿QUÉ TIENE DE ESPECIAL?» / «WHAT'S SPECIAL ABOUT IT?»,
  «CÓMO FUNCIONA» / «HOW IT WORKS» y «DETALLES TÉCNICOS» / «TECHNICAL DETAILS».
  Estas indicaciones sustituyen los titulares anteriores, sin borrar su memoria.
- Mantener ejemplos rápidos, funcionalidades, responsive, accesibilidad y desplegables.
  Cambiar las etiquetas no autoriza a cambiar RAG, traducción, proveedores o API.
- Verificar en código el failover antes de describirlo. Si una afirmación no está
  implementada, avisar y omitirla; no cambiar la lógica ni publicar información falsa.
- Comprobación en este encargo: `GenerationRouter` y `RagConfiguration` configuran
  PC → portátil, con error controlado si ambos fallan. Luna y Codex pertenecen a los
  agentes de desarrollo de `config/r4r-agents.json`; no hay tercer respaldo RAG con
  Codex Luna. Verificar de nuevo el código si se cambia esa arquitectura en el futuro.
- La tarjeta «IA en nuestros servidores» conserva solo la primera frase verificable
  del texto solicitado, omitiendo la afirmación de respaldo Codex Luna no implementado.
  La línea tecnológica incluye OpenAI Codex y el nivel técnico aclara su papel real.
- Generación y embeddings locales no significan que todos los servicios sean offline.
  Explicar MyMemory discretamente en detalles técnicos, no como característica principal.
- Mostrar solo tecnologías, modelos, CPU, RAM, GPU y sistemas operativos verificados;
  no inferir una distribución Linux por un identificador interno de equipo.
- Ejecutar las suites existentes, incluidas RAG, backend y frontend, e informar de
  omisiones y causas. Reutilizar cobertura existente antes de crear nuevos tests.
