# CODEX — Integración final de R4R en Athagon Labs y Riansares 4R

## OBJETIVO GENERAL

Quiero finalizar la presentación e integración del asistente RAG de Riansares 4R en dos webs existentes:

1. **https://athagon.tech/** — Portfolio profesional de ingeniería de software y Applied GenAI.
2. **https://riansares4r.com/** — Web comercial de una empresa real de reformas.

Debes trabajar de forma autónoma: inspeccionar los repositorios, investigar el sistema de publicación existente, implementar las mejoras, ejecutar pruebas y preparar los cambios para su publicación.

No quiero limitarme a un análisis ni recibir únicamente propuestas. **Realiza el trabajo completo**, salvo los despliegues que requieran confirmación por afectar a producción.

Las prioridades son:

- Excelente presentación profesional.
- Integración funcional con el RAG existente.
- Reutilización máxima del código.
- Cambios mínimos, mantenibles y verificables.
- Sin introducir nuevas arquitecturas innecesarias.

Son dos fases independientes. Termina primero Athagon y después trabaja en Riansares 4R.

---

# FASE 1 — ATHAGON LABS: MEJORAR LA TARJETA DE R4R

## 1. Localización del proyecto

Repositorio local:

`/home/german/Desarrollo/athagon-web.git/`

La web ya contiene una tarjeta de Riansares 4R en su sección de proyectos.

Revisa su implementación, estructura, CSS responsive, sistema de filtros, imágenes y proceso de publicación.

El sitio está en inglés. Conserva ese idioma.

## 2. Ampliar la tarjeta de R4R a dos columnas

Actualmente la tarjeta de R4R ocupa una sola columna.

Quiero que sea uno de los proyectos más destacados del portfolio.

### Distribución de escritorio

Actualmente las primeras tarjetas son:

- Riansares 4R.
- EU CoR / EESC.
- Toys & Colors.

La nueva distribución debe ser:

**Primera fila:**

- Riansares 4R: ocupa dos columnas.
- EU CoR / EESC: ocupa la tercera columna.

**Segunda fila:**

- Toys & Colors.
- Motivation Game M1.
- Virtual Mirror.

Después continúa el resto de tarjetas conservando su orden relativo.

Revisa la implementación del Grid existente y utiliza `grid-column: span 2` o una solución equivalente. No reconstruyas el catálogo completo.

En tablet y móvil, la tarjeta deberá adaptarse al espacio disponible sin huecos artificiales.

La ampliación no debe romper los filtros por tecnologías, categorías o fechas.

## 3. Rediseñar el contenido de la tarjeta

La tarjeta actual tiene demasiado poco espacio para explicar lo que se ha construido.

### Título recomendado

**AI Renovation Assistant · Riansares 4R**

Subtítulo:

*Applied GenAI solution developed by Athagon Labs.*

Incorporar ambos logotipos, Riansares 4R y Athagon Labs, con una composición profesional que identifique claramente:

- Riansares 4R: empresa y caso de uso.
- Athagon Labs: desarrollo e ingeniería de la solución.

No confundir las marcas ni presentar Riansares como una plataforma tecnológica independiente.

### Descripción

Redactar en inglés una descripción breve, atractiva para recruiters y técnicamente precisa.

Explicar que se trata de una aplicación real de inteligencia artificial aplicada a reformas, con respuestas fundamentadas en documentación técnica y fuentes verificables.

Tecnologías y capacidades relevantes que puedes mencionar:

- Java 21 y Spring Boot.
- Spring AI.
- Angular.
- Retrieval-Augmented Generation (RAG).
- PostgreSQL / pgvector.
- Modelos LLM locales mediante Ollama.
- Infraestructura distribuida y failover.
- AWS Lambda, IAM, S3 Vectors y CloudWatch.
- Auditoría asíncrona de respuestas.
- SSE y comunicación en tiempo real.

**No meter todas las tecnologías indiscriminadamente en la tarjeta.**

Prioriza las más valiosas y resume el resto de forma visual. El espacio ampliado debe servir para comunicar mejor el proyecto, no para crear un bloque de texto interminable.

No mencionar Bedrock como funcionalidad operativa si todavía no está funcionando en producción.

### Dos enlaces diferenciados

La tarjeta debe ofrecer claramente:

**Try AI Assistant**

https://athagon.tech/r4r-rag/

**Riansares 4R**

https://riansares4r.com/

El primero demuestra el producto tecnológico.

El segundo permite conocer la empresa real donde se aplica.

Mantener los enlaces relevantes existentes, especialmente GitHub, si los hay.

## 4. Diseño y validación de Athagon

La tarjeta ampliada debe:

- Integrarse con la estética actual.
- Mantener proporciones y alineaciones.
- Distribuir correctamente imágenes, descripción, badges y enlaces.
- Funcionar en móvil, tablet y escritorio.
- Conservar los filtros y el resto de proyectos.
- No generar huecos ni saltos extraños en el Grid.

Realizar pruebas y capturas representativas.

**Criterio de aceptación:** un recruiter debe entender en aproximadamente diez segundos qué problema resuelve el proyecto, qué tecnología utiliza y que se trata de una implementación real de Applied GenAI.

Termina esta fase antes de comenzar la siguiente.

---

# FASE 2 — RIANSARES 4R: INVESTIGAR EL DESPLIEGUE E INTEGRAR EL RAG

## 5. Repositorio y rutas existentes

Repositorio principal:

`/home/german/Desarrollo/R4R/R4R.git/`

Ruta que aparentemente contiene la demo publicada:

`/home/german/Desarrollo/R4R/R4R.git/dist/r4r-rag-demo/`

Web pública:

https://riansares4r.com/

Existe también el repositorio del asistente RAG:

`/home/german/Desarrollo/R4R/r4r-spring-ai-rag.git/`

La demo tecnológica completa está publicada en:

https://athagon.tech/r4r-rag/

### Hallazgo que debes investigar

He observado algo importante:

Anteriormente, `r4r-rag-demo` no aparecía publicado en riansares4r.com.

Después de hacer `git push` del último commit de `R4R.git`, la demo sí apareció en la web pública.

Por tanto, parece que ese repositorio tiene un mecanismo de despliegue automático que publica contenido relacionado con `dist/`.

**Pero ahora `dist/` parece estar ignorado por Git.**

Quiero que investigues esto antes de modificar la publicación.

## 6. Investigación obligatoria del histórico Git y despliegue

Analiza de forma autónoma:

1. El histórico de commits de `R4R.git`.
2. Los cambios históricos de `.gitignore`.
3. Si `dist/` estuvo versionado anteriormente.
4. Si `dist/r4r-rag-demo/` está actualmente versionado, ignorado o generado.
5. Qué archivos producen ese directorio.
6. Si existe un generador estático, script de copia o etapa de compilación.
7. La configuración de GitLab CI/CD, GitLab Pages o del mecanismo de despliegue realmente utilizado.
8. Las ramas y remotos implicados.
9. Los commits recientes relacionados con la demo.
10. Por qué el último `git push` provocó la aparición de la demo en producción.

Utiliza, entre otros, los comandos necesarios para contrastar estas posibilidades:

```bash
git status --short
git remote -v
git branch -avv
git log --oneline --all -30
git log --all -- .gitignore dist/
git ls-files dist/
git check-ignore -v dist/ dist/r4r-rag-demo/
```

Revisa también `.gitlab-ci.yml`, los scripts de generación y cualquier configuración real de despliegue.

No asumas que `dist/` es el origen porque contenga el resultado compilado.

**Distingue claramente:**

- Código fuente.
- Directorio generado.
- Artefactos CI/CD.
- Contenido versionado.
- Directorio servido realmente por producción.

No soluciones el problema quitando `dist/` de `.gitignore` sin entender su función.

No incorpores archivos generados masivos al repositorio si el despliegue puede regenerarlos.

### Resultado obligatorio

Determina documentalmente cuál es el proceso real:

`Fuente → Build → Artefacto → Publicación → URL pública`

Y deja identificado el comando o proceso correcto para publicar futuras modificaciones.

Esta investigación no debe convertirse en una refactorización completa del generador.

---

# FASE 3 — INTEGRAR EL ASISTENTE RAG EN RIANSARES4R.COM

## 7. Objetivo funcional

Quiero que un visitante que entre en riansares4r.com pueda consultar el asistente directamente desde la web comercial.

No quiero limitarme a añadir un enlace externo.

El asistente debe integrarse como una funcionalidad adicional de Riansares 4R, manteniendo el protagonismo de la empresa, los servicios, los trabajos realizados y el contacto.

La integración debe transmitir confianza y profesionalidad.

## 8. Ubicación y presentación comercial

Revisa la portada actual y selecciona la ubicación más adecuada.

Mi preferencia es incorporar una sección visible y bien integrada, después de la presentación principal y de la información esencial de la empresa, sin relegarla al final de la página.

Título sugerido:

**¿Tienes dudas sobre tu reforma? Pregunta a nuestro asistente de IA.**

Descripción:

«Consulta dudas sobre materiales, técnicas y trabajos de reforma. Nuestro asistente utiliza documentación especializada para ofrecerte orientación y fuentes que puedes comprobar.»

La sección debe:

- Mantener los colores, tipografía y estética de Riansares 4R.
- Ser completamente responsive.
- Tener un formulario sencillo de consulta.
- Permitir ver el resultado en la misma página.
- Mostrar las fuentes cuando existan.
- Gestionar correctamente respuestas parciales y abstenciones.
- Mostrar un aviso discreto indicando que las respuestas son orientativas y no sustituyen una valoración profesional.
- Incluir un enlace opcional al asistente completo de Athagon Labs.

No presentar el asistente como capaz de elaborar presupuestos definitivos ni diagnosticar problemas estructurales sin inspección.

Conservar el contenido y el SEO de la web comercial.

## 9. Arquitectura de integración: reutilizar, no duplicar

El asistente tecnológico ya existe y funciona.

No quiero crear otro RAG.

Inspecciona primero el frontend Angular actual y las posibilidades reales de integrarlo.

Evalúa brevemente:

**A. Reutilizar un modo embebido del Angular existente.**

Por ejemplo, un componente o vista específica que permita integrar el formulario y las respuestas sin cargar la cabecera completa de Athagon.

**B. Crear un pequeño widget nativo integrado con la API.**

Solo si resulta claramente más simple y mantenible que adaptar Angular.

**C. Utilizar iframe responsive.**

Es aceptable únicamente si se puede integrar bien, sin duplicar menús ni provocar scrolls anidados, problemas de altura, accesibilidad o incoherencias visuales.

### Criterios de selección

Prioriza:

1. Menor duplicación de código.
2. Menor mantenimiento.
3. Experiencia móvil.
4. Compatibilidad con el despliegue estático.
5. Seguridad.
6. Facilidad de pruebas.
7. Velocidad de publicación.

**No quiero incorporar Angular como segunda aplicación completa dentro de la web comercial si un widget pequeño reutilizable resuelve mejor el problema.**

Tampoco quiero duplicar toda la lógica SSE, auditoría y visualización de fuentes si es posible reutilizarla limpiamente.

Implementa la alternativa que técnicamente resulte mejor y explica brevemente por qué.

## 10. Comunicación con la API

Reutilizar la API pública existente.

Conservar las capacidades que correspondan a la integración:

- Streaming SSE.
- Fallback REST.
- Estados `ANSWERED`, `PARTIAL` y `ABSTAINED`.
- Auditoría asíncrona.
- Citas y enlaces documentales.
- Gestión de errores.
- Timeouts.
- Rate limiting.
- Prevención de consultas duplicadas.

Comprobar CORS y CSP para el dominio de Riansares 4R y sus variantes reales.

No ampliar CORS indiscriminadamente a `*`.

No exponer credenciales en JavaScript.

Mantener HTTPS y comprobar que los enlaces a las fuentes funcionan desde ambas webs.

---

# FASE 4 — REGISTRAR USO REAL DEL ASISTENTE

## 11. Objetivo

Quiero poder saber:

- Cuánta gente visita la sección del asistente.
- Cuántas consultas se realizan.
- Qué preguntas hacen los usuarios.
- Desde qué web se utiliza.
- Cuántas respuestas son completas, parciales o abstenciones.
- Cuántas consultas fallan.
- Cuánto tardan en responderse.

No necesito un dashboard complejo.

Prefiero una implementación pequeña con PostgreSQL y consultas SQL.

## 12. Inspeccionar primero lo que ya existe

Antes de añadir tablas o endpoints:

- Revisa si el backend ya registra consultas.
- Revisa las métricas existentes.
- Comprueba si hay identificadores de request reutilizables.
- Revisa si la web comercial ya utiliza Google Analytics.
- Evita duplicar estadísticas existentes.

Si Google Analytics ya registra visitas, utilízalo como referencia para la navegación y añade únicamente lo que falte para medir el uso real del asistente.

No incorpores otro sistema analítico si no es necesario.

## 13. Persistencia PostgreSQL

Si hace falta, incorporar una tabla pequeña de eventos o consultas.

Campos sugeridos, sujetos a revisión del esquema real:

- Identificador.
- Fecha y hora UTC.
- Aplicación de origen (`ATHAGON` / `RIANSARES4R`).
- Tipo de evento.
- Pregunta, cuando proceda y sea adecuado conservarla.
- Estado de respuesta.
- Duración.
- Identificador de correlación.
- Estado de error, si existe.

No almacenar innecesariamente respuestas completas, contextos RAG, embeddings ni documentos recuperados.

No crear una base de datos independiente.

No replicar estas métricas en S3 Vectors.

### Privacidad

El registro de preguntas requiere especial precaución, ya que el usuario puede escribir nombres, direcciones, teléfonos u otros datos personales.

Por tanto:

- No guardar IPs en esta tabla.
- No aplicar fingerprinting.
- No registrar cabeceras ni datos identificativos innecesarios.
- Definir retención limitada.
- Establecer un proceso de eliminación.
- Evitar exponer las consultas públicamente.
- Revisar y actualizar la información de privacidad correspondiente antes de activar el almacenamiento de texto.
- Minimizar o redactar datos personales cuando resulte viable.
- No registrar automáticamente textos sensibles en logs de aplicación.

Si no puede garantizarse una gestión adecuada de las preguntas, implementar primero estadísticas agregadas y dejar preparado el registro de texto sin activarlo.

No asumir que un identificador pseudónimo elimina todas las obligaciones de privacidad.

### Fiabilidad

El registro de estadísticas debe ser secundario:

**Una caída del sistema de estadísticas nunca debe bloquear ni retrasar sustancialmente la respuesta RAG.**

Mantenerlo aislado del flujo principal.

No introducir infraestructura de mensajería adicional para esta funcionalidad.

## 14. Métricas y acceso

Preparar unas consultas SQL o un script sencillo para obtener:

- Visitas al asistente.
- Consultas totales.
- Consultas por origen.
- Consultas por día.
- Distribución `ANSWERED/PARTIAL/ABSTAINED/ERROR`.
- Duración media y percentiles básicos.
- Preguntas frecuentes, si su almacenamiento está habilitado.

Si se mide conversión de visitantes a usuarios que preguntan, definir claramente cómo se calcula y qué limitaciones tiene, sin recurrir a identificación invasiva.

No exponer un endpoint público que permita consultar todas las preguntas almacenadas.

No construir un panel administrativo completo.

---

# FASE 5 — VALIDACIÓN Y PUBLICACIÓN

## 15. Pruebas funcionales

Validar las dos webs y sus integraciones.

### Athagon Labs

- Tarjeta R4R de dos columnas.
- EU CoR/EESC a la derecha.
- Resto de proyectos correctamente redistribuidos.
- Filtros funcionando.
- Ambos enlaces correctos.
- Buena presentación móvil.

### Riansares 4R

- La portada conserva los elementos comerciales.
- El asistente aparece integrado.
- Se puede formular una pregunta.
- El progreso y la respuesta aparecen correctamente.
- Las fuentes son accesibles.
- Los estados RAG se muestran con claridad.
- No hay problemas de scroll móvil.
- Los errores se gestionan adecuadamente.
- No hay regresiones en menús, galerías, contactos ni navegación.

### Estadísticas

Comprobar que:

- Se registran únicamente los eventos previstos.
- El origen de la consulta es correcto y no depende ciegamente de un parámetro arbitrario.
- No se duplican eventos por reintentos o re-renderizados.
- Los errores no se cuentan como respuestas correctas.
- La funcionalidad sigue respondiendo si el registro falla.
- Las consultas almacenadas no son accesibles públicamente.

Utilizar mocks para pruebas RAG repetibles. No lanzar inferencias reales masivas.

## 16. Builds, Git y despliegue

Trabaja en los repositorios reales.

Antes de modificar:

- Comprueba branch, estado Git y remotos.
- Conserva cualquier cambio preexistente del usuario.
- No sobrescribas trabajo ajeno.
- No hagas `reset --hard`, `clean -fd` ni force push.
- No incorpores secretos ni `.env` reales.
- No añadas innecesariamente `dist/`, dependencias, capturas masivas o artefactos generados.

Ejecuta las compilaciones y pruebas necesarias.

Puedes preparar commits separados por fase, con mensajes descriptivos.

**No hagas push ni despliegues a producción de forma automática hasta verificar qué pipeline se dispara y qué cambios se publicarían.** Si todo está preparado pero la publicación requiere una decisión de producción, entrega los comandos exactos y deja el repositorio listo.

No cambies la configuración de despliegue únicamente para acomodar una hipótesis sobre `dist/`.

Si existe un mecanismo de publicación correcto, reutilízalo.

## 17. Restricciones de alcance

No modificar:

- Modelos LLM.
- Prompts RAG.
- Embeddings.
- Corpus documental.
- Índices pgvector.
- S3 Vectors.
- Lambda.
- Estrategias de failover.
- Lógica de recuperación y generación.

La única modificación permitida en el backend debe ser la estrictamente necesaria para integrar la web comercial y registrar estadísticas, preservando todos los contratos existentes.

No introducir nuevas funciones de IA.

No reescribir la web comercial.

No convertir esto en otro proyecto de varias semanas.

Si una funcionalidad secundaria resulta desproporcionada, implementa la alternativa mínima y documenta el límite.

---

# ENTREGA FINAL OBLIGATORIA

Al finalizar, entregar un informe organizado en cinco bloques:

### A. Athagon Labs

- Cambios implementados.
- Archivos modificados.
- Capturas y pruebas.
- Enlaces finales.

### B. Investigación de R4R.git

- Qué genera realmente `dist/`.
- Por qué está ignorado.
- Qué ocurrió tras el último push.
- Qué pipeline publica la web.
- Directorio fuente correcto.
- Procedimiento reproducible para publicar.

Separar hechos comprobados de hipótesis.

### C. Integración comercial

- Dónde se ha integrado el asistente.
- Qué solución técnica se ha elegido y por qué.
- Cómo se reutiliza Angular/API.
- Qué capacidades están disponibles.
- Qué queda pendiente, si corresponde.

### D. Estadísticas

- Tablas y migraciones.
- Eventos registrados.
- Consultas SQL.
- Medidas de privacidad.
- Resultados de pruebas.

### E. Estado de publicación

- Commits preparados.
- Pruebas realizadas.
- Builds correctos.
- Qué está publicado y qué permanece local.
- Comandos exactos para publicar los cambios pendientes.
- Cualquier bloqueo real.

No declarar publicado algo que solamente esté preparado en local.

## CRITERIO DE CIERRE

Este trabajo estará terminado cuando:

**Athagon Labs** presente R4R como uno de sus proyectos principales de ingeniería Applied GenAI, con una tarjeta destacada y enlaces claros.

**Riansares 4R** ofrezca el asistente integrado en su propia web, con una experiencia sencilla para visitantes reales y estadísticas básicas de utilización.

**Git y despliegue** tengan un procedimiento comprobado y reproducible, sin depender de carpetas cuyo origen sea desconocido.

Después de esto, no quiero ampliar R4R con nuevas funcionalidades. La siguiente prioridad es actualizar CV, LinkedIn, Europass y el portfolio técnico para concentrarme en oportunidades profesionales de ingeniería sénior, Java y Applied GenAI.

**Trabaja de forma autónoma, investiga primero, implementa después y valida antes de considerar terminada cada fase.**
