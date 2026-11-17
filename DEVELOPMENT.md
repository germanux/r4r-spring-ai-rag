# R4R — memoria y criterios de desarrollo

Memoria de trabajo, no certificación del despliegue ni sustituto de `AGENTS.md`.
Se conservan decisiones útiles a futuro y se excluyen resultados, PIDs, tiempos y
estados transitorios: esos datos pertenecen a los informes de validación.

**Procedencia:** «Instrucciones explícitas» recoge lo recibido del usuario durante
esta conversación o de `AGENTS.md`, identificado cuando corresponda. «Memoria previa»
conserva criterios del documento anterior sin atribuirlos de nuevo al usuario como
si se hubieran formulado en esta sesión. «Interpretaciones» son conclusiones del
asistente; no autorizan cambios funcionales.

## Failover ordenado — decisión del usuario del 2026-11-16

- Tres listas independientes: `RAG_GENERATION_PROVIDERS`, `RAG_AUDIT_PROVIDERS` y
  `RAG_EMBEDDING_PROVIDERS`, todas con `URL-o-openai|modelo|timeout` por entrada.
  Su orden es la prioridad efectiva; ausencia de OpenAI lo desactiva por función.
- Generación PC → portátil → Luna; auditoría PC → Luna; embeddings portátil → PC →
  NAS en los defaults públicos. El operador puede reordenar sin cambiar código.
- Compartir recorrido y tratamiento técnico, manteniendo validación semántica,
  digest/dimensiones y cooldown específicos. No reintentar inferencia por dictámenes.
- Cuota y reserva OpenAI únicas entre generación y auditoría. No tocar secretos.
  Retirar los flags redundantes mediante migración explícita, sin ignorarlos y
  activar gasto remoto accidentalmente.
- Mantener Qwen3/1024/digest, precarga protegida por instancia, residencia 8h,
  contratos y corpus. No añadir selector público de modelos.
- Trabajar en rama específica y validar con proveedores simulados; dejar merge,
  pruebas live de pago y despliegue para autorización/revisión del usuario.
- La configuración vigente sustituye los flags históricos de OpenAI indicados
  más abajo. Detalles y evidencias en `docs/operations/ordered-provider-failover.md`.

## Nombres de evaluaciones — decisión del usuario del 2026-11-16

- Directorios de ejecución con fecha y hora hasta el minuto: `yyyy-MM-dd_HH-mm`
  en UTC, por ejemplo `2026-11-16_19-22`. Sin prefijo, `T`, segundos ni UUID.
- Conservar `results.json` y `summary.json`, con `runId` consistente con la carpeta.
  Una colisión de nombre debe impedir sobrescribir resultados existentes.

## Presentación comercial — decisión del usuario del 2026-11-17

- Esta decisión sustituye la restricción anterior de exportar solo una carpeta
  comercial: ahora se preparan `dist/r4r-rag/` y `dist/asistente-ia/`.
- Un solo Angular y backend. `/r4r-rag/` conserva la demo técnica de Athagon;
  `/asistente-ia/` usa la marca y CSS originales de Riansares, con presentación
  comercial, sin navegación Athagon ni paneles de hardware, modelos o JSON.
- Conservar ejemplos rápidos, demo, tiempos, fuentes, revisión, «Cómo funciona»,
  «Por qué puedes comprobarlo» y opciones de privacidad. No cambiar el RAG.
- Quitar el espacio sobrante de la sección de portada sin tocar otras secciones.
- En todas las variantes, revisión adicional como texto normal, sin desplegable;
  omitirla si solo repite el indicador de referencias revisadas.
- Aviso breve de almacenamiento visible junto a Opciones de privacidad, fuera del
  desplegable y asociado al textarea. Conservar oposición, supresión y base jurídica.
- No renderizar capturas ni desplegar automáticamente. Las copias locales preparadas
  no autorizan publicar en NAS ni en las webs.

## Ajuste comercial — decisión del usuario del 2026-11-17

- Se acepta conservar la vista condicional existente en el mismo Angular; la petición
  inicial del usuario era una versión nueva, no esa elección de arquitectura.
- Copiar literalmente el CSS original `R4R.git/dist/css/home-blueprint.css` al
  frontend y conservar el azul blueprint, fondo y paleta originales. Los ajustes
  SCSS solo adaptan selectores del asistente; no sustituyen la hoja original.
- Esta decisión sustituye la exportación técnica anterior a Riansares: eliminar
  `R4R.git/dist/r4r-rag/` y no volver a copiarla desde el script. El enlace técnico
  de la portada comercial apunta a `https://athagon.tech/r4r-rag/`.
- Conservar `R4R.git/dist/asistente-ia/` para el asistente comercial. No renderizar
  capturas ni publicar automáticamente.

## IP completa y país — decisión del usuario del 2026-11-17

- Activar registro persistente de IPv4/IPv6 públicas completas por visita y consulta,
  salvo oposición o exclusión legal. No truncar, hashear ni descartar IP públicas
  fiables por una preferencia de minimización.
- Separar país para análisis geográfico técnico de preguntas (incluidos ES/IE) e IP
  para seguridad/abuso/disponibilidad; no perfilar personas ni usar IP como visitante.
- Confiar solo en peers autorizados comprobados, nunca NAS/pasarela/subred/comodines.
  Compose debe transmitir la allowlist; no debilitar rate limiting ni imprimir datos.
- Mantener conservación manual, revisión justificada y derechos, sin purgas automáticas.
- No modificar modelos, embeddings, índices, auditoría o frontend salvo privacidad;
  no desplegar. Distinguir pruebas PostgreSQL locales de validación pública real.

## Alcance, autorización y forma de trabajar

### Decisiones vigentes del usuario: cambios mínimos, formato y validación

Estas instrucciones posteriores prevalecen sobre los procedimientos visuales
históricos recogidos más abajo:

- No cambiar textos, traducciones, ejemplos, saltos de línea, distribución ni
  comportamiento que el usuario no haya pedido modificar. Una corrección de
  formato de código no autoriza a reformatear el contenido mostrado.
- Conservar la pregunta progresiva de cocina en una, dos y tres líneas según
  su etapa, tanto en español como en inglés. Usar saltos reales de línea,
  no `\\n` visible ni todas las frases juntas. Mantener estas tres líneas:

  ```text
  Tengo tuberías rotas en mi cocina. ¿Qué hago?
  Además, quiero reformar la cocina. ¿Qué me recomiendas?
  ¿Puedo cambiar también los azulejos?
  ```

  ```text
  I have broken pipes in my kitchen. What should I do?
  I also want to renovate my kitchen. What do you recommend?
  Can I also change the tiles?
  ```

- Plantillas Angular legibles y compactas: etiquetas con texto o interpolación
  simple en una línea, por ejemplo `<h2>{{i.t('page.your_answer_will_appear_here')}}</h2>`.
  Indentar contenedores y bloques; no convertir todo el componente en una sola
  línea ni separar innecesariamente apertura, contenido y cierre.
- El botón «Probar el asistente / Try the assistant» conserva su acción original:
  enviar al RAG «¿Qué significan las cuatro R de Riansares 4R?» / «What do the four
  Rs in Riansares 4R stand for?», según el idioma. No sustituirlo por la reproducción
  de la demo de cocina ni por otra pregunta. La demo automática es independiente.
- No renderizar páginas, generar imágenes ni hacer capturas salvo petición
  explícita. La revisión visual la hace el usuario. Validar solo lo necesario;
  si pide «no verifiques», no ejecutar pruebas, compilaciones ni comprobaciones
  visuales. Una petición expresa posterior de tests o `mvn verify` sí se ejecuta.
- Commits en inglés, de una a tres líneas y con el formato convencional existente.
  Agrupar por bloques coherentes y hacer primero el commit pendiente cuando se
  solicite. No hacer push ni desplegar sin autorización expresa.
- Evaluaciones RAG: exactamente dos JSON por ejecución: `summary.json` separado
  y `results.json` con todos los casos completos en `cases`. Actualizar ambos tras
  cada consulta; no generar un JSON por pregunta ni mezclar el resumen dentro de
  los resultados. Java y Python usan el mismo formato.
- Hardware declarado del NAS: 6 GB de RAM y CPU de dos núcleos a 2 GHz. No inventar
  modelo de procesador ni GPU; la ausencia de salida de vídeo no certifica su ausencia.
- Privacidad del formulario: una línea discreta y enlace válido también desde
  el NAS; sin casillas ni explicaciones legales extensas. Mantener oposición y
  supresión accesibles desde la política. Athagon sin GA ni banner analítico;
  Riansares conserva `G-41CEFW6B5P` condicionado al consentimiento.
- Mostrar el equipo real de embeddings separado del LLM, también tras failover.
  Tras recibir la respuesta, mantener indicadores giratorios de revisión y del
  botón hasta terminar la auditoría; la respuesta permanece legible.
- Distribución frontend: Athagon `html-static/r4r-rag/`, Riansares únicamente
  `dist/asistente-ia/` y NAS `web/r4r-rag`. En el despliegue NAS, el build y las
  copias van después del backend; SMB es la última copia. Python en `py-scripts/`.
  Copiar `.env.base` y `.env` al NAS de forma segura; no leer, imprimir ni versionar
  el archivo privado para resolver fallos de tests. Usar claves ficticias en tests.
- Decisiones operativas autorizadas: solicitar precarga al arrancar Spring y abrir
  la página, coordinada por instancia Ollama. No cargar simultáneamente LLM y
  embeddings en el mismo servidor, repetir cargas de modelos residentes ni
  expulsar otro modelo para anticipar una consulta. En una instancia vacía,
  priorizar generación principal del PC y embeddings frente a generación de
  respaldo; las consultas conservan portátil → PC → NAS para embeddings.
  Residencia solicitada de ambos de 8 h, condicionada a la memoria real.
  Embeddings: TCP 400 ms, `/api/tags` 600 ms, lectura PC/portátil 1500 ms y NAS
  30 s; precarga independiente de 30 s. No modificar estos valores por iniciativa
  propia. Un timeout no debe excluir durante 30 s a un servidor que sigue cargando;
  conservar cooldown para fallos recuperables no clasificados como timeout.
  No asumir que la configuración Ollama del NAS controla PC/portátil.
  Historial PostgreSQL bajo administración manual, sin purgas automáticas
  ni seguimiento persistente de visitantes.

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
- Titulares actualizados: «Pregunta a nuestra IA con RAG» / «Ask our
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

### Actualización del estado inicial de los desplegables

- Por indicación posterior del usuario, «¿Qué tiene de especial?» también debe
  aparecer plegada por defecto, en ES y EN. Esta regla sustituye la decisión anterior
  de mostrar sus tarjetas abiertas. Mantener apertura por botón, teclado y navegación.

### Respaldo RAG remoto autorizado: GPT-6 Luna

- El usuario autoriza un tercer proveedor de generación, distinto de los agentes de
  desarrollo: PC/Ollama → portátil/Ollama → OpenAI Responses API `gpt-6-luna`.
- Solo tras fallos técnicos de ambos locales, nunca por lentitud, abstención o calidad.
  Reutilizar el Prompt RAG y sus protecciones; no cambiar embeddings ni recuperación.
- Configurar modelo con `RAG_OPENAI_MODEL`; `RAG_OPENAI_ENABLED=false` por defecto.
  Mantener la clave existente fuera de Git, logs y frontend. Activación y despliegue
  quedan sujetos a validación manual, sin acciones automáticas al pasar los tests.
- La precarga y la traducción auxiliar no usan OpenAI. Evitar retries remotos y
  registrar consumo sin contenido ni credenciales. Identificar proveedor por petición.
- En la UI, GPT-6 Luna aparece como OpenAI en nube, sin atribuirle hardware local.
  Esta autorización sustituye la anterior ausencia de respaldo remoto; no convierte
  a GPT-5.6 Luna, coordinador de desarrollo, en proveedor de respuestas RAG.
- Configuración, límites y resultados: `docs/operations/openai-fallback.md`.

## Auditoría de seguridad SSE — tarea autorizada el 2026-11-07

### Contexto e instrucciones explícitas de este encargo

- Leer íntegramente esta memoria y revisar Git/SSE antes de editar. Estado inicial:
  árbol e índice limpios; último cambio `2ee9663`, integración SSE ya existente.
- Auditar `/api/rag/answers/stream` reutilizando validación de Origin y cuotas
  REST existentes. No duplicar mecanismos que ya protejan el endpoint.
- Conservar el control independiente de frecuencia/concurrencia de `/warmup`,
  Spring MVC, SseEmitter y el contrato REST. Revisar CORS/OPTIONS y ciclo de vida.
- Cubrir orígenes permitidos/prohibidos/ausentes, cuotas por IP/globales, 403/429,
  Retry-After, desconexión, timeout y saturación. No llamar al OpenAI real.
- Actualizar esta sección al terminar con decisiones, pruebas y resultados.
- El usuario autoriza expresamente un commit exclusivo de esta tarea. Esta
  autorización concreta prevalece sobre la restricción anterior de no hacer
  commits en la sesión; no autoriza push, despliegue ni commits futuros.

### Evidencia inicial y comprobaciones pendientes

- El filtro ya incluye explícitamente la ruta SSE y comparte contadores con REST.
  Warmup valida origen y evita las cuotas de consultas por diseño.
- Revisar equivalencia entre URI cruda del filtro y ruta resuelta por Spring MVC,
  visibilidad CORS de Retry-After y respuestas de capacidad del rate limiter.
- Revisar bloqueos: el scheduler SSE actual ejecuta escrituras de heartbeat y el
  timeout espera el mismo bloqueo de escritura; un cliente lento puede retrasarlos.

### Decisiones y correcciones comprobadas

- No se añadió otro filtro ni otro rate limiter: SSE ya figuraba en la lista de
  rutas protegidas y compartía contadores REST. Se amplió la cobertura existente.
- Se reprodujo en Tomcat real un bypass: `/api/rag/answers/%73tream`,
  `/api/rag/answers;v=1/stream` y `/api/rag/%61nswers` devolvían HTTP 200 sin Origin
  antes del arreglo. El filtro comparaba la URI cruda, mientras MVC resolvía las
  variantes. Se usa UrlPathHelper para comparar rutas decodificadas, relativas al
  contexto y sin parámetros matriz; ahora los tres casos reciben 403 antes del RAG.
- Retry-After se expone mediante CORS también cuando el filtro responde antes de
  MVC. La respuesta 429 por capacidad de IPs también incluye Retry-After.
- El scheduler SSE no hace escrituras de red ni espera un bloqueo del emitter.
  Se mantienen cuatro workers de cálculo y se acota la entrega a cuatro escritores
  y cuatro streams admitidos, con hasta 64 paquetes de salida por sesión.
- Deadline/cancelación detienen el cálculo sin depender de que el cliente lea.
  ERROR de timeout tiene hasta 100 ms para entregarse antes de cancelar la salida.
  Una escritura no interrumpible sigue reteniendo su plaza hasta terminar: no
  se admite trabajo ilimitado mientras sus recursos siguen ocupados.
- Se corrigió la carrera de cierre durante el registro de callbacks, se retiran
  timers cancelados y se conserva el contexto del router en el mismo worker.
  Los campos de métricas leídos por el deadline tienen visibilidad entre hilos.
- Warmup conserva su frecuencia/concurrencia independiente y no consume cuotas
  de respuestas, también con una ruta codificada. No se cambió su implementación.
- Spring MVC, SseEmitter, contratos REST/SSE, generación, failover, traducción,
  frontend, modelos, despliegue y `.env` no se modificaron por esta auditoría.

### Pruebas y resultados de esta tarea

- PASSED: `mvn verify`, 174 unitarios y 28 integraciones; base dedicada de test.
- PASSED: Origin permitido/prohibido/ausente, cuotas por IP y globales para REST
  y SSE, cuotas compartidas entre rutas, 429/Retry-After/CORS y OPTIONS sin consumo.
- PASSED: tres regresiones de URI contra Tomcat real, desconexión de socket real,
  timeout, escritura de heartbeat bloqueada, quinta conexión rechazada con 503,
  registro de callbacks ya desconectado y limpieza de workers, sesiones y timers.
- PASSED: suite de precarga existente y compatibilidad REST original incluidas
  en Maven; 81/81 tests existentes de la demo estática.
- SKIPPED: tres suites live opt-in de LLM, sin activarlas en esta tarea.
- NOT RUN: OpenAI real y despliegue/proxy de producción; no se hicieron llamadas
  reales de pago, push ni despliegue. I/O no interrumpible conserva límites de
  clientes/servidor y plazas acotadas; no se declara cancelación física instantánea.
- Se revisaron diff, estado Git y errores de whitespace antes del commit solicitado.

Archivos de esta auditoría (todos existentes, sin archivos nuevos):

- `DEVELOPMENT.md` y `docs/operations/rag-sse.md`.
- `src/main/java/com/riansares/r4r/config/RagApiSecurityFilter.java`.
- `src/main/java/com/riansares/r4r/config/WebConfiguration.java`.
- `src/main/java/com/riansares/r4r/rag/RagProgress.java`.
- `src/main/java/com/riansares/r4r/rag/api/RagStreamController.java`.
- `src/test/java/com/riansares/r4r/config/RagApiSecurityFilterTest.java`.
- `src/test/java/com/riansares/r4r/config/RagApiCorsTest.java`.
- `src/test/java/com/riansares/r4r/rag/api/RagStreamControllerTest.java`.
- `src/test/java/com/riansares/r4r/rag/api/RagStreamHttpIT.java`.

### Estado de la creación del commit

- El usuario solicitó el commit local, pero la revisión automática rechazó
  `git add` por la prohibición expresa de AGENTS.md para sesiones de modelo.
  No se eludió el rechazo ni se modificó AGENTS.md. Se solicitó una excepción
  explícita para preparar/crear únicamente este commit; pendiente de respuesta.

## Tarea 2 — robustez de precarga y estados (2026-11-07)

- Se ha leído íntegramente esta memoria y revisado el cambio de seguridad SSE
  `70979c7`. Se conserva el cambio previo sin commitear de DEVELOPMENT.md.
- Encargo: no perder warmup cuando runtime observa, no duplicar cargas ni colas,
  conservar cooldown/timeouts, identificar por proveedor y modelo y distinguir
  disponibilidad de memoria observada. Verificar embeddings en el Ollama existente,
  sin descargas, cambios de proveedor o pgvector. No hacer commit, push o despliegue.
- La denegación previa del commit quedó resuelta por el usuario: solicita únicamente
  mensajes de commit en texto y realiza él los commits; no se pide otra excepción.

### Decisiones y resultados de la tarea 2

- Coordinación backend: un único flag de warmup pendiente por Job cuando existe
  observación activa. Se reserva cooldown al aceptar y se continúa en el mismo
  worker, sin bloquear HTTP, nuevas colas ni reintentos periódicos. Mientras se
  ejecuta warmup se rechazan duplicados. Se conservan los tres workers y timeouts.
- Se observa /api/ps antes de cargar para omitir modelos ya residentes. Después
  del warmup se vuelve a observar: loaded solo se asigna con presencia comprobada
  del modelo. available indica modelo instalado sin memoria verificable; unloaded
  requiere /api/ps válido sin el modelo; unknown aún no comprobado, loading tarea
  de preparación activa, unreachable fallo de acceso/modelo ausente.
- El JSON conserva sus campos y añade warmupCompletedAt y memoryObserved. Un éxito
  de precarga queda registrado aunque /api/ps falle o no muestre el modelo, pero
  no se convierte en loaded. Telemetría malformada no equivale a lista vacía.
- Runtime identifica por nombre del proveedor, endpoint interno normalizado y
  modelo canónico. Las direcciones solo se usan dentro del backend y no se añaden
  a respuestas públicas. PC y portátil con igual modelo conservan estados propios.
- PASSED: mvn verify, 179 unitarios y 28 integraciones. Incluye runtime antes de
  warmup, warmup antes de runtime, veinte solicitudes concurrentes, cargado sin
  nueva carga, descargado, inaccesible, modelos homónimos y recuperación posterior
  de proveedor simulado con HTTP 503. Las regresiones SSE/seguridad siguen verdes.
- PASSED: 81/81 tests existentes del frontend, sin cambios en su código.
- PASSED real: entrada vacía /api/embed en el Ollama Synology existente mediante
  comprobación directa y OllamaEmbeddingWarmupLiveIT con Spring AI. Modelo
  nomic-embed-text:latest, respuesta embeddings:[], /api/ps lo confirma cargado.
  Ya estaba cargado antes: NO EJECUTADA la transición desde frío; no se desalojó
  un modelo en uso para forzarla. No se descargaron modelos ni se tocó pgvector.
- SKIPPED por defecto: cuatro suites live opt-in (tres anteriores y la nueva).
  La nueva se ejecutó explícitamente con RAG_WARMUP_LIVE_TEST=true; las de
  generación no se activaron. No se invocó OpenAI real.
- Comandos: mvn verify; node front-athagon-r4r-static/js/app.test.cjs;
  RAG_WARMUP_LIVE_TEST=true mvn -Dtest=NoUnitTests
  -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=OllamaEmbeddingWarmupLiveIT verify.
- Revisados git diff/status y whitespace. Sin git add, commit, push ni despliegue.

Archivos: DEVELOPMENT.md, docs/operations/model-warmup.md, OllamaModelPreloader.java,
RagRuntimeController.java y sus dos tests existentes. Nuevo archivo junto a la suite:
src/test/java/com/riansares/r4r/rag/OllamaEmbeddingWarmupLiveIT.java (opt-in).
Se conserva íntegro el contenido previo de memoria y el commit SSE del usuario.

## Frontend Angular estático

- `front-athagon-r4r-static/` publica Angular; `v1/` conserva la interfaz HTML/JS.
  Ambas versiones utilizan el único directorio documental de la raíz: `docs/`.
- `frontend/src/app/` organiza los componentes standalone OnPush y signals en
  `layout/` y `features/`; servicios y modelos compartidos están en `core/`. El CSS compartido conserva la
  cascada responsive original; los componentes usan estilos encapsulados.
- Los documentos y las grabaciones se conservan en `frontend/public/`; los datos
  visibles capturados y el catálogo de citas se mantienen en los JSON del código
  fuente. La demo no consulta al backend.
- Compilación: `bash scripts/build-frontend-angular.sh`. Usa dependencias locales,
  Angular CLI mediante npx y Chromium únicamente durante la compilación para
  prerenderizar el HTML inicial sin acceso a servicios externos. Puede indicarse
  `CHROMIUM_PATH`. Publica en la raíz de `front-athagon-r4r-static/`, con
  base relativa `./`, sin subdirectorio browser ni servidor Node en producción.
- `frontend/nginx-static.conf` sirve `/r4r-rag/` y su versión legacy `/r4r-rag/v1/`
  como contenido estático, sin redirección a un directorio v2-ng.
- Validación reproducible: servir `front-athagon-r4r-static/` por HTTP local en
  `127.0.0.1:8765`, ejecutar `node frontend/validate-migration.mjs` y
  `node frontend/validate-edge-cases.mjs`, con `CHROMIUM_PATH` si corresponde.
  Las pruebas interceptan la API y usan las respuestas grabadas originales.
  Capturas y resultados: `docs/validation/angular-migration/`.

## Cierre responsive y auditoría del frontend

- Este encargo pide sincronizar primero v1 y después Angular. Es una autorización
  puntual para actualizar la presentación legacy; documentos y grabaciones se
  conservan sin cambios. El snapshot original sigue disponible en Git.
- Al enviar desde móvil, se conserva el formulario y su pregunta completa, se
  presenta un resumen con «Editar pregunta» y se enfoca/posiciona ese resumen una
  sola vez al inicio. Los eventos SSE y la revisión no vuelven a desplazar la página.
- La cabecera se pliega a menú accesible hasta 960 px; el flujo compacto de
  pregunta/resultado se aplica hasta 760 px. En escritorio siguen las dos columnas.
- Clasificación, revisión y duración tienen áreas independientes; la revisión
  inconclusa o no disponible conserva la clasificación original y nunca aparenta
  aprobación. Notas extensas quedan en los detalles posteriores a la respuesta.
- El mensaje PARTIAL tiene un único título y la explicación breve acordada ES/EN.
  Se reserva su espacio adaptable durante la auditoría para evitar saltos al degradar.
- CSS responsive sincronizado: `frontend/src/responsive-ux.scss` y
  `front-athagon-r4r-static/v1/styles/responsive-ux.css`. El build Angular no lee v1.
- Prueba reproducible adicional: `node frontend/validate-responsive.mjs`, con el
  servidor estático local anterior. Evidencias nuevas bajo
  `docs/validation/angular-migration/responsive/`; las evidencias de la migración
  original se mantienen como registro histórico.

## Remate visual móvil: título, espaciado y evidencias

- Se conserva un solo h1: en la vista móvil de resultado el hero se reduce al
  título compacto, situado antes de la pregunta resumida. El posicionamiento
  inicial incluye ese título; SSE y auditoría no vuelven a desplazar la vista.
- Selector visual ES/EN con bandera y nombres accesibles completos. Se mantienen
  la cabecera de una fila y el menú existente.
- Los estados reservan únicamente el tamaño natural de sus etiquetas. Se reducen
  el padding del aviso parcial, la separación entre bloques y el espacio vacío
  del botón copiar/proveedor, conservando la estabilidad de la auditoría.
- Regenerar evidencias completas con el servidor estático local y:
  `R4R_RESPONSIVE_OUTPUT=polish/full/ node frontend/validate-responsive.mjs`.
  Puede indicarse CHROMIUM_PATH como en las pruebas anteriores.
- Las capturas completas bajo `docs/validation/angular-migration/polish/full/`
  permanecen locales e ignoradas. Se seleccionan doce capturas de 390 px en
  `polish/selected/`; informes JSON y scripts permiten reproducir el conjunto.
  No se eliminan imágenes históricas ya versionadas ni se utiliza Git LFS.


## Organización Angular por responsabilidad

- `layout/header` y `layout/footer`: estructura común. `features/home/components`:
  presentación e información. `features/rag/components`: los seis componentes del
  asistente; `services`, `pipes` y `data` contienen su estado, demo y grabaciones.
- Se reutilizan los archivos canónicos `core/rag/rag-api.service.ts` y
  `core/rag/rag.models.ts`. El cliente combina SSE con `query()` REST reutilizado
  como fallback; los modelos conservan los nombres RAGQuestionRequest,
  RAGAnswerResult y RAGCitation, ampliados al contrato activo.
- Auditoría, runtime y catálogo se encuentran en `core/rag`; traducciones y servicio
  de idioma, en `core/i18n`. Los tests están junto al código que verifican.
- Se retiran la página RAG del prototipo, el servicio de prueba `/test`, sus restos
  de plantilla/rutas y la configuración de entorno que no usaba la interfaz activa.
  No se activa ninguna configuración de ejemplo: URL predeterminada, selector de
  endpoint y preferencias del navegador conservan el comportamiento existente.
- No queda carpeta `migration/` en el código Angular. Las rutas de evidencias
  históricas `docs/validation/angular-migration/` mantienen su nombre.

## Cierre definitivo: contexto y scroll natural

- Esta regla sustituye el posicionamiento del remate anterior: título y descripción
  conservan su posición original en el documento y no son sticky. El hero móvil
  es compacto siempre; al consultar solo se retiran sus acciones secundarias.
- ES: «Asistente de IA para reformas» y «Respuestas basadas en documentación de
  Riansares 4R.» EN: «AI assistant for renovations» y «Answers grounded in Riansares
  4R documentation.» Se mantiene un único h1 y el selector visual ES/EN accesible.
- El envío posiciona una sola vez la pregunta resumida bajo la barra sticky. No
  fuerza que todo el contexto permanezca en el viewport; al volver arriba se
  recuperan naturalmente el título y la descripción. SSE y auditoría no desplazan.
- Pruebas reproducibles con el servidor estático local:
  `R4R_RESPONSIVE_OUTPUT=closure/full/ node frontend/validate-responsive.mjs`.
  Las capturas completas quedan locales e ignoradas; doce representativas y los
  informes JSON están bajo `docs/validation/angular-migration/closure/`.
- La estructura Angular por funcionalidades, los servicios, los contratos y el
  comportamiento RAG permanecen sin cambios por este cierre visual.


## Publicación conjunta en la raíz y documentos compartidos

- Angular se publica en `front-athagon-r4r-static/index.html`, con base relativa
  `./`, compatible con `/r4r-rag/`. No existe directorio público `v2-ng/`.
- `front-athagon-r4r-static/docs/` es el único origen de los 82 PDF y sus 82 HTML.
  Se retiran las copias de `frontend/public/docs/` y `v1/docs/`; el build conserva
  la documentación canónica y no la duplica en su salida temporal.
- v1 conserva recursos propios y enlaza los documentos con `../docs/`.
- La denominación «v2-ng» y el stack Angular, TypeScript y SCSS solo se muestran
  en el diagrama y los listados técnicos de la versión Angular.
- `frontend/validate-publication.mjs` hace tres renders y comprueba los enlaces
  compartidos, los recursos y los 82 pares PDF/HTML. Puede configurarse
  R4R_PUBLICATION_BASE y CHROMIUM_PATH; por defecto usa
  `http://127.0.0.1:8767/r4r-rag/`.
- La copia de publicación se mantiene en el repositorio independiente athagon-web,
  bajo `html-static/r4r-rag/`. Los resultados de copia, commit y push se registran
  en `docs/validation/angular-migration/publication/`.
