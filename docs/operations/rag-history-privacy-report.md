# Registro técnico RAG y privacidad — decisiones definitivas

2026-11-15. Implementado y validado en local en los tres repositorios. Sin push,
despliegue, copia SMB ni modificación de modelos/prompts/embeddings/corpus/índices,
S3 Vectors, Lambda, failover o lógica de auditoría. Se mantienen los contratos JSON,
SSE, REST y las protecciones antiabuso. Este informe sustituye las decisiones anteriores.

## Qué se ha eliminado

- Identificador propio de visitante recurrente, session_id analítico, creación de
  `r4r.analytics.visitor` y sus headers/consentimientos. El script limpia los valores
  obsoletos del navegador, sin convertirlos en otro identificador.
- Consentimiento separado de historial y banner de consentimiento en Athagon.
- Campos de tracking y fechas predeterminadas de revisión/conservación en el esquema final.
  No quedan reglas operativas de siete días, 90 días, seis meses o doce meses para el ledger.
- Estadísticas de personas recurrentes. No hay reconstrucción mediante IP, hashes,
  fingerprinting ni otros datos del navegador.

GA comercial **G-41CEFW6B5P** se conserva, con un único gestor de aceptar/rechazar y
retirada. No carga antes de aceptar. Rechazarlo no cambia el registro técnico RAG.
La preferencia de GA y sus cookies tienen ajustes técnicos propios, documentados en
cookies; no son plazos legales ni reglas de eliminación del historial PostgreSQL.

## Evaluación breve del interés legítimo — art. 6.1.f RGPD

**Interés real:** prestar y evaluar esta aplicación de portfolio Applied GenAI;
comprobar precisión, alucinaciones, contradicciones, fuentes citadas, diferencias
RAG/auditoría, carencias del corpus, errores, tiempos, disponibilidad y modelos;
conocer volumen y procedencia técnica e investigar incidentes cuando corresponda.
No hay perfiles comerciales, publicidad basada en preguntas ni entrenamiento automático.

**Necesidad:** métricas agregadas no permiten explicar una respuesta incorrecta o una
abstención; conservar la pareja pregunta/respuesta, citas y auditoría permite estudiar
el fallo concreto. La IP completa no es necesaria para evaluar contenido ni para contar
visitas: su activación es independiente y limitada a una necesidad documentada de
seguridad/disponibilidad. El país permite agregados geográficos sin guardar la IP.

**Proporcionalidad y expectativas:** finalidad ligada al asistente utilizado,
información visible antes de enviar, acceso solo de operadores, sin difusión ni usos
comerciales/cross-site. Se excluye texto con identificadores o material sensible
evidente; no se añaden contexto completo, documentos, embeddings ni cabeceras.
La alternativa de no guardar texto se ofrece mediante oposición, sin impedir usar RAG.

**Riesgos y salvaguardias:** datos personales accidentales, accesos indebidos,
reidentificación y conservación excesiva. Se aplican exclusión conservadora de texto,
restricción inmediata por referencia, separación de IP/país, roles mínimos, ausencia
de logs de texto y administración manual de necesidad/supresión. El filtro no detecta
todo ni garantiza anonimato; los hallazgos posteriores requieren restringir, revisar y
minimizar/suprimir, sin reutilizar datos sensibles para calidad.

**Conclusión delimitada:** resulta defendible implementar el registro técnico y los
textos ordinarios de reforma bajo interés legítimo, con estas salvaguardias y oposición
práctica. No se exige consentimiento de historial. No es una autorización general para
conservar información personal excesiva, categorías especiales o datos penales: art. 6.1.f
no sustituye los requisitos de arts. 9/10. No se pide consentimiento para legitimar su
recogida innecesaria; se excluye/revisa ese material. GA sí mantiene consentimiento.
La necesidad real de guardar IPs debe justificarse antes de habilitar ese campo,
especialmente con poco tráfico; no se inventa un incidente o una finalidad distinta.

**Oposición:** controles en la sección discreta «Opciones de privacidad» bajo el
asistente, enlazada desde la política; estado en memoria, sin cookies ni permiso de GA. Los siguientes envíos omiten texto e IP; se solicita restricción de
referencias recientes y de la apertura, incluso si una consulta sigue en curso. No se
exponen conversaciones. Si falla la red, la exclusión futura permanece y se muestra el
canal manual para los registros anteriores. Otra apertura no identifica a esa persona:
para solicitudes antiguas se facilita referencia/fecha al contacto de privacidad.
Art. 21 exige cesar salvo motivos imperiosos/reclamaciones legalmente aplicables;
no se presuponen en todos los casos. El plazo de respuesta de art. 12.3 (ordinariamente
un mes, con ampliación justificada) sí es un plazo legal, no una política inventada de retención.

Fuentes oficiales revisadas: [CEPD, directrices de interés legítimo](https://www.edpb.europa.eu/documents/guideline/guidelines-12024-on-processing-of-personal-data-based-on-article-61f-gdpr_en),
[RGPD](https://www.boe.es/buscar/doc.php?id=DOUE-L-2016-80807),
[AEPD, adecuación de tratamientos IA](https://www.aepd.es/sites/default/files/2020-02/adecuacion-rgpd-ia.pdf),
[LOPDGDD, incluido art. 32](https://www.boe.es/buscar/act.php?id=BOE-A-2018-16673),
[LSSI, arts. 10 y 22.2](https://www.boe.es/buscar/act.php?id=BOE-A-2002-13758).
No se certifica conformidad absoluta, ni conservación personal indefinida.

## Tabla, migraciones y datos

Una tabla permanente: **assistant_usage**. `id` es eventId y requestId en QUERY;
en VIEW es la referencia de la apertura. `view_id` vincula la apertura con sus consultas,
solo durante esa apertura, sin persistirlo en el navegador entre visitas.

| Grupo | Campos finales relevantes |
|---|---|
| Correlación | id, occurred_at (UTC), origin, event_type, view_id |
| Contenido | question, answer, citations (referencias públicas, no documentos), content_basis, content_exclusion |
| Resultado | outcome original, effective_outcome, audit_id, audit_result público, audit_completed_at, error_code |
| Ejecución | provider, model, generation_ms, duration_ms, total_duration_ms |
| Red | client_ip INET IPv4/IPv6, country_code ISO o NULL, network_basis |
| Control | basis_recorded_at, restricted_at |

Cada consulta tiene una fila; la auditoría la actualiza, conservando ambos estados.
La referencia se reutiliza en fallback REST; insertar dos veces no duplica la fila.
La restricción por oposición prevalece frente a la inserción de una respuesta en curso.
Los valores desconocidos y mediciones no disponibles quedan NULL, no inventados.
ERROR es un resultado del evento QUERY, sin crear otra fila para el mismo envío.
El campo error_code distingue timeout/cancelación/cuota y otros errores controlados;
no guarda mensajes de excepción ni cuerpos rechazados antes de leer la solicitud.
`occurred_at` representa persistencia; los tiempos son del servidor, no del navegador.

**Compatibilidad:** consulta de solo lectura en PostgreSQL Docker local: V5 y V6 no
aplicadas. Se mantienen **ambos archivos intactos** para no alterar checksums ni asumir
el estado de todos los entornos. La nueva **V7__technical_usage_without_tracking.sql**
retira columnas/índices obsoletos y fechas automáticas, añade view_id, citas y códigos
de error. Conserva textos legados con `content_basis=legacy-consent-v1`, sin convertir
retroactivamente consentimiento en interés legítimo; conserva su fecha de base.
Los textos nuevos usan `legitimate-interest-v1`. Los identificadores analíticos obsoletos
se eliminan por la migración expresa, no mediante una tarea de purga periódica.

## Red, acceso y conservación manual

ClientNetwork se mantiene: solo proxies autorizados por IP exacta de socket pueden
aportar CF-Connecting-IP/CF-IPCountry; no confía en X-Forwarded-For. Se excluyen IPs
privadas/reservadas y formatos inválidos. Sin país fiable se guarda NULL; no hay
llamadas GeoIP externas. `security-interest-v1` identifica el uso de IP para seguridad;
`technical-geography-v1`, país sin IP. La IP no se usa para visitantes únicos.

Sin cron, scheduler, pg_cron, jobs ni purgas automáticas. Tampoco hay una fecha límite
uniforme oculta en columnas/defaults. Administración manual no permite retención
indefinida: revisar necesidad al cerrar incidentes, cambiar versiones/corpus, resolver
fallos, recibir derechos o detectar exceso de datos, además de revisiones regulares
proporcionadas al riesgo/volumen. Minimizar o suprimir casos sin utilidad técnica
vigente; retirar la IP al cesar la investigación que la justifica. Solo conservar
estadísticas históricas realmente anonimizadas, evaluando grupos escasos y combinación
de datos, sin textos/IPs/referencias correlacionables. Para obligaciones/reclamaciones
concretas puede proceder bloqueo del art. 32 LOPDGDD y destrucción al cesar esas
responsabilidades; no se inventa un plazo legal uniforme ni se extiende a todo el historial.
Las decisiones deben respetarse al restaurar copias.

SQL de operador en `docs/operations/sql/`:

- `assistant-usage.sql`: las 12 consultas solicitadas, citas, exclusiones y correlación
  efímera; sin marketing. Los análisis ordinarios excluyen restricted_at.
- `assistant-usage-manual-review.sql`: búsqueda por UUID, lectura autorizada de un caso.
- `assistant-usage-manual-export.sql`: COPY a stdout de un solo UUID, para CSV privado
  con umask 077; sin endpoint público, archivo público ni exportación indiscriminada.
- `assistant-usage-manual-erasure.sql`: vista previa y UUID único, confirmación exacta,
  timeouts y ROLLBACK por defecto. Incluye minimización; valorar bloqueo antes de borrar.

El escritor sigue siendo secundario, de un hilo, cola acotada y timeout SQL, sin esperar
por PostgreSQL en el flujo RAG. Fallos/saturación pueden perder registros: no se promete
100% de captura. Los avisos no incluyen textos, IPs ni parámetros. No existe endpoint
público de lectura del historial. El endpoint de oposición solo restringe referencias
aleatorias presentadas del mismo origen; no devuelve datos. Necesita vigilancia de
permisos, TLS, discos/copias protegidos y ausencia de logging de cuerpos/parámetros.
Revocar PUBLIC no restringe por sí solo al propietario/superusuario de PostgreSQL.

## Webs, validación y activación

Athagon: portfolio personal de **Germán Caballero Rodríguez**, Cuenca, España,
`german.caballero.rguez@gmail.com`, sin empresa ni domicilio postal inventado.
Política en inglés y enlace desde portfolio/formulario; sin GA ni banner analítico.
Riansares mantiene la identificación facilitada: **Riansares 4R – G. Caballero**,
NIF disponible a solicitud, Calle D. Riansares, CP 16400, Tarancón, Cuenca, España;
`riansares4r@gmail.com`, +34 614 93 29 57. Se actualizan aviso legal, privacidad y cookies.
No se certifica que «NIF disponible a solicitud» cierre las obligaciones de art. 10 LSSI
que resulten aplicables; no se inventa información del titular.

Se describen los proveedores realmente configurados: PostgreSQL/Ollama, OpenAI en las
rutas de generación/auditoría habilitadas, AWS para recuperación y Cloudflare para red/
alojamiento. No se promete que todo sea local ni contratos/regiones no verificados.
Se conservan los canales de derechos y reclamación AEPD. Los contratos/transferencias
reales deben verificarse al activar; no se han cambiado proveedores ni modelos.

**Resultados:** 58 pruebas Java seleccionadas; 18 Angular; build y prerender correctos.
Navegador: Athagon sin GA/banner/IDs, oposición y referencias efímeras; Riansares GA
rechazado/aceptado/retirado sin condicionar RAG. Regresión comercial 1440/900/390 px,
ANSWERED/PARTIAL/ABSTAINED, 429 y fallback REST. PostgreSQL Docker: V5→V6→V7, textos
legados, sentencias preparadas reales, idempotencia, auditoría degradada, oposición,
SQL de análisis/revisión/exportación y supresión de prueba. Todo en esquema temporal
con rollback, no sobre datos de usuarios. Ninguna inferencia real.
Evidencia: `rag-history-validation.json` y `/tmp/r4r-final-*-tests.log`.

Activación administrativa, independiente de consentimiento de GA:

```properties
RAG_USAGE_ENABLED=true
RAG_USAGE_PRIVACY_READY=true
RAG_USAGE_CONTENT_ENABLED=true
# Solo si la necesidad de persistir IP está justificada:
RAG_USAGE_SECURITY_ENABLED=true
RAG_API_TRUST_PROXY_HEADERS=true
RAG_API_TRUSTED_PROXY_ADDRESSES=<peers reales autorizados>
```

Los flags personales siguen desactivados por defecto. PRIVACY_READY confirma aviso,
finalidad/salvaguardias y operación preparada; no es un consentimiento de visitante.
Con SECURITY_ENABLED=false se puede conservar país fiable sin IP. No usar wildcard
para proxies ni activar una dirección inventada. CORS sigue limitado a las webs.

Para repetir la validación, usar `mvn -Dtest=AssistantUsageTest,ClientNetworkTest,RagQueryControllerTest,RagStreamControllerTest,RagApiSecurityFilterTest,RagAuditControllerTest test`,
`CHROME_BIN=$(bash scripts/resolve-chrome-bin.sh) npm --prefix frontend run test:ci`,
`npm --prefix frontend run build -- --base-href ./` y `frontend/validate-privacy.cjs`
con CHROMIUM_PATH resuelto. El paquete local `/tmp/r4r-final-release-verified` permite
repetir `frontend/validate-commercial.cjs`. **No se ejecutó build-frontend-angular.sh**:
ahora copia a SMB; el build/prerender y las copias de revisión se hicieron explícitamente
solo en local. Athagon tiene el bundle local actualizado; el sitio comercial se prepara
como artefacto estático para no añadir otro árbol masivo innecesario al repositorio.

Pendiente de producción, no de implementación: aplicar Flyway V7 con el backend,
publicar juntos frontend/políticas/assets mediante el procedimiento existente,
verificar proxies, contratos/permisos/copias y activar los flags apropiados. No se
ha contactado el backend de producción ni alterado sus datos. No hay bloqueo técnico
local. Los commits por bloques se enumeran en la entrega; no se hizo push.


## Corrección HTTP LAN — 2026-11-16

El NAS por HTTP no ofrece necesariamente `crypto.randomUUID`, reservado a contextos
seguros. Se centraliza la generación de referencias en `correlationId`: UUID nativo
cuando existe, o UUID v4 con `crypto.getRandomValues` cuando no existe. No se usa
Math.random ni se debilita la aleatoriedad de referencias utilizadas para oposición.
21 pruebas Angular y una prueba Chromium con origen LAN HTTP no seguro: correctas,
con arranque y respuesta simulada sin errores. Build y prerender renovados en
`frontend/dist/r4r-frontend/browser` y `front-athagon-r4r-static`.
No se ejecutó la distribución a los repositorios externos ni la copia SMB; para
actualizarlos se utiliza `bash scripts/build-frontend-angular.sh`, que sí escribe en el NAS.
Documentación: [randomUUID](https://developer.mozilla.org/en-US/docs/Web/API/Crypto/randomUUID)
y [getRandomValues](https://developer.mozilla.org/en-US/docs/Web/API/Crypto/getRandomValues).


## Ajuste de UX — 2026-11-16

El formulario muestra únicamente la frase breve EN/ES solicitada y el enlace legal,
en tipografía secundaria. No contiene checkbox, mensajes de oposición ni referencia
larga. Los controles, estados de oposición y referencia propia quedan dentro de
«Opciones de privacidad», cerrada por defecto y accesible desde la política. La
oposición/supresión conserva sus canales y endpoint; X-R4R-Usage-Opt-Out sigue operativo.
Las políticas mantienen el detalle jurídico y explican cómo volver a la apertura
original para conservar sus referencias; también enlazan las opciones de una apertura
nueva y el contacto para registros anteriores. No se expone historial ajeno ni se
convierte el interés legítimo en consentimiento. El botón de respuesta no se modifica.

Validación de este ajuste: 21 pruebas Angular correctas y ocho combinaciones de
origen/idioma/ancho (Athagon y Riansares, ES/EN, 1440/390 px), enlaces legales reales
locales y acceso desde las políticas a las opciones. El iframe comercial se mantiene
en español; el formulario inglés también se valida en su vista completa del dominio.
Se comprueba el fallo/reintento de oposición fuera del formulario y el header de
exclusión en consultas posteriores. No se cambió backend, migraciones ni base jurídica.
Evidencias: `/tmp/r4r-clean-form-*-tests.log` y capturas `/tmp/r4r-clean-form-*.png`.

## Activación de IP completa y país — 2026-11-17

Se mantiene almacenamiento `inet` completo de IPv4/IPv6, sin hash ni truncado,
separado de `country_code`. La configuración pública habilita cabeceras proxy y
Compose pasa la allowlist concreta `172.20.0.6` identificada por el operador.
La comprobación del salto activo y la aplicación al NAS están pendientes; no se
ha desplegado. Políticas de ambas webs actualizadas para IP persistente por
visita/consulta, geografía, acceso restringido, conservación manual y derechos.
Validación detallada y condiciones de activación: [registro de red](usage-network-activation.md).
