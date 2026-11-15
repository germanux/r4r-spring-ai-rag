# Registro RAG, visitantes y privacidad — informe final

Fecha: 2026-11-15. Implementado y validado **en local**, sin push ni despliegue.
Este informe sustituye las decisiones de estadísticas/conservación de
`assistant-integration-report.md`. Bases de trabajo limpias al inicio: RAG `5ebf8fa`,
Athagon `871d203`, comercial `85c761a`. No se crearon commits.

## 1. Archivos y migración

Se conserva V5 sin alterar su checksum. **V6__assistant_history.sql** amplía la misma
tabla `assistant_usage`; no hay otra tabla permanente, servicio GeoIP, cola externa,
dashboard ni nueva funcionalidad RAG.

Backend: `usage/AssistantUsage.java`, `usage/AssistantViewController.java`,
`config/ClientNetwork.java`, `config/RagApiSecurityFilter.java`,
`config/WebConfiguration.java`, los controladores REST/SSE/auditoría y `application.yml`.
Tests: `AssistantUsageTest`, `ClientNetworkTest`, `RagApiSecurityFilterTest`.

Frontend: `frontend/public/privacy-consent.js` (fuente canónica),
`core/rag/privacy.service.ts`, `rag-api.service.ts` y su test,
formulario de preguntas, componente del asistente, estado del asistente, pie e índice.
Pruebas reproducibles: `frontend/validate-privacy.cjs` y
`frontend/validate-commercial.cjs`. Distribución local:
`py-scripts/sync-privacy-assets.py` y `py-scripts/prepare-commercial-release.py`.

Athagon: `html-static/index.html`, nuevos `privacy.html` y `privacy-consent.js`.
Riansares: `dist/aviso-legal.html`, `politica-privacidad.html`,
`politica-cookies.html`, `privacy-consent.js`; portada, galerías, 404 y presupuesto
cargan el mismo gestor y ya no cargan Google Analytics anticipadamente.
El inventario exacto por repositorio está en `rag-history-validation.json`.

Se retira `assistant-usage-retention.sql`: era una receta de borrado a 30 días;
no se había instalado un cron. No hay ahora cron, scheduler ni purga PostgreSQL.

## 2. Esquema final y funcionamiento

| Campos | Función |
|---|---|
| `id`, `occurred_at`, `origin`, `event_type` | UUID de correlación, tiempo UTC, ATHAGON/RIANSARES4R y VIEW/QUERY; UNKNOWN se conserva para compatibilidad de clientes sin origen reconocido |
| `outcome`, `effective_outcome` | Estado inicial y auditado: ANSWERED/PARTIAL/ABSTAINED/ERROR |
| `visitor_id`, `session_id` | UUID de navegador/sesión, exclusivamente con consentimiento analítico válido y activación administrativa |
| `analytics_consent`, `history_consent`, `consent_at` | Versión de la elección comunicada y fecha de recepción en servidor; no es una firma ni autenticación de identidad |
| `question`, `answer` | Texto completo, sin truncado, solo con consentimiento de historial y activación administrativa |
| `client_ip`, `country_code`, `security_basis`, `network_review_due_at` | IP INET IPv4/IPv6, país ISO estimado o NULL, finalidad de seguridad y fecha de revisión |
| `provider`, `model`, `generation_ms`, `duration_ms`, `total_duration_ms` | Modelo/proveedor y tiempos del servidor; no incluyen el tiempo de lectura ni latencia de renderizado del navegador |
| `audit_id`, `audit_result`, `audit_completed_at` | Correlación y JSON del resultado público de auditoría, sin evidencias recuperadas |
| `review_due_at`, `restricted_at` | Revisión manual y exclusión de registros restringidos de los análisis ordinarios |

Una consulta se inserta al finalizar; su auditoría actualiza **esa fila**. El resultado
original no se pierde si se degrada. Sin consentimiento de texto, la auditoría
almacena solo clasificación/conclusión, omitiendo las notas libres. Los tiempos de
generación pueden ser NULL si no hubo generación o no se dispone de medición.
`total_duration_ms` añade la espera posterior de auditoría al tiempo del RAG;
`occurred_at` es la fecha de persistencia del evento, no una medida de llegada al navegador.

La referencia UUID de cada envío viaja en `X-R4R-Request-Id`, se muestra junto al
formulario y se reutiliza en el fallback REST. `ON CONFLICT` evita registros duplicados.
Cada componente visible utiliza una referencia de visita estable: cambiar consentimiento
actualiza esa visita sin sumar otra. Un reintento manual nuevo representa otro envío;
la deduplicación del registro no es una garantía de idempotencia de inferencia.

Índices: tiempo/origen de V5, navegador/origen/fecha, auditoría única y fechas de
revisión general y de seguridad. CHECKs vinculan los textos/identificadores a sus
finalidades y validan estados/país. Se revocan permisos de PUBLIC.

## 3. Visitantes nuevos, recurrentes y retirada

Consentimientos independientes para analítica e historial, ambos desmarcados por defecto.
Aceptar/rechazar tienen igual estilo y están juntos; guardar selección permite
cualquier combinación. El asistente funciona rechazando ambas finalidades.

- `r4r.privacy.v1`: preferencia necesaria para recordar aceptación o rechazo, hasta 6 meses;
  no contiene un identificador de visitante.
- `r4r.analytics.visitor`: UUID aleatorio en localStorage, solo después de aceptar
  analítica y usar el asistente; caduca a los 90 días de creación, sin renovación en cada visita.
- `r4r.analytics.session`: UUID en sessionStorage de la pestaña, solo con analítica.
- Los dominios mantienen almacenes separados. No se usa fingerprinting, IP hasheada
  ni unión de personas entre webs. Un navegador, una IP o un dispositivo no equivalen a una persona.

El iframe del mismo dominio reutiliza las preferencias de la portada. El gestor está
accesible permanentemente y también desde el formulario. La retirada elimina IDs
locales, detiene los headers analíticos y las autorizaciones futuras y solicita
`POST /api/rag/usage/withdraw`: desvinculación del UUID y restricción de historial
referenciable, sin devolver ninguna conversación. Esa operación sigue disponible si
`RAG_USAGE_ENABLED` se desactiva. Las referencias recientes se guardan solo en memoria,
no en un identificador persistente nuevo. El endpoint acepta orígenes exactos y un
máximo de 100 referencias, con cuota global compartida con las visitas.

Una marca restringida con la misma referencia impide que una consulta en curso
reintroduzca su texto después de una retirada. No es un borrado automático: los
registros quedan fuera de los análisis y deben revisarse para la supresión/bloqueo
que proceda. Sin UUID ni referencia anterior no siempre se pueden vincular todos los
datos; el canal manual de derechos sigue siendo necesario. La operación de red es de
mejor esfuerzo, no una promesa de borrado efectuado; ante fallo/offline se usa ese canal.

Google Analytics comercial se carga solo tras aceptar analítica. Sus cookies se
configuran a 90 días sin renovación por visita, sin señales publicitarias; se desactiva
y se eliminan las cookies propias accesibles al retirar. No recibe preguntas ni
respuestas. El portfolio no añade Google Analytics. Las referencias GA/PostgreSQL
no deben sumarse como si fueran una única población.

## 4. IP, país, textos y bases jurídicas

**IP y país**: registro separado de seguridad para investigar abuso, intentos de
eludir cuotas e incidentes de disponibilidad. Está preparado bajo una evaluación de
interés legítimo del art. 6.1.f RGPD; requiere activación administrativa independiente,
no el consentimiento de cookies analíticas. No se reutiliza la IP para medir visitantes
recurrentes. La evaluación propuesta es: finalidad concreta de disponibilidad y
prevención de abusos; acceso interno restringido; alternativa menos invasiva para
recurrencia (UUID consentido); IP completa limitada a una ventana corta de incidentes;
ponderación y derecho de oposición. El responsable debe ratificar su necesidad real
antes de activar `RAG_USAGE_SECURITY_ENABLED`, especialmente con el tráfico reducido.
No se presenta como una autorización general a registrar IPs indefinidamente.

`ClientNetwork` comparte resolución con el limitador existente. Solo confía en
`CF-Connecting-IP`/`CF-IPCountry` cuando están habilitadas las cabeceras proxy **y la
IP del socket está en la lista explícita de proxies**. Se elimina la confianza en
X-Forwarded-For. Los peers son direcciones exactas, no rangos ni nombres DNS.
La infraestructura confiable debe sobrescribir las cabeceras y no permitir acceso
directo que la suplante. El valor del socket se usa si no hay aserción fiable.

Solo se persisten direcciones públicas utilizables: direcciones privadas, loopback,
CGNAT, multicast, documentación y formatos inválidos quedan en NULL. No se sustituye
una IP desconocida por la del proxy privado. El país se acepta como ISO de dos letras
solo junto con una IP CF válida del mismo proxy. XX/T1, ausencia o falta de confianza
producen NULL. Un país estimado no es nacionalidad ni localización exacta. Se descarta
GeoIP externo por consulta y una base local adicional por mantenimiento innecesario.

**Texto completo**: consentimiento separado del art. 6.1.a, no condicionado al uso del
servicio. La ejecución de la consulta solicitada se distingue de conservarla para
calidad/mejora; no se ha implementado entrenamiento automático. El aviso previo enlaza
la política y advierte de datos sensibles/personales innecesarios. No hay un detector
que garantice la ausencia de datos personales: ante detección o solicitud, restringir,
revisar y minimizar/suprimir; no reutilizar material sensible para evaluación.

**Metadatos operativos**: soporte a funcionamiento y errores bajo interés legítimo
sujeto a evaluación; los datos de visitantes solo se correlacionan con consentimiento.
La tabla no contiene contexto de recuperación, embeddings, documentos ni cabeceras.
Los errores anteriores a leer una solicitud (p. ej. cuota) no copian su cuerpo al historial.

### Conservación proporcionada, exclusivamente manual

| Dato | Criterio ordinario y revisión |
|---|---|
| IP y país de seguridad | Revisar al menos semanalmente; minimizar dentro de 7 días, salvo incidente concreto justificado con acceso restringido y nueva fecha |
| UUIDs analíticos en servidor | Suprimir/desvincular dentro de 90 días; retirada solicita desvinculación anterior |
| Pregunta, respuesta y auditoría textual | Revisar a los 90 días y mensualmente; suprimir/minimizar dentro de 6 meses salvo caso documentado |
| Metadatos sin textos ni IDs de navegador/red | Máximo ordinario 12 meses; revisión mensual y sin calificar automáticamente todo registro de anónimo |
| Prueba de consentimiento y datos bloqueados | Limitar al plazo de responsabilidades realmente aplicable, separada del uso analítico y con revisión documentada |

Las fechas son obligaciones operativas de revisión, **no tareas de borrado**. La
ausencia de cron no permite incumplirlas. Antes de activar se debe asignar quién revisa
y cumple las supresiones manuales. No consta que se haya instalado ningún job en el host.
Si procede art. 32 LOPDGDD, restringir/bloquear para responsabilidades y destruir después,
no convertir el bloqueo en conservación indefinida. Revisar también copias/restauraciones:
no se fija un plazo ficticio para copias cuya configuración no se ha verificado.

## 5. Páginas legales y responsables

Riansares conserva los datos expresamente facilitados por el usuario: **Riansares 4R –
G. Caballero**, NIF/CIF disponible a solicitud, Calle D. Riansares, CP 16400, Tarancón,
Cuenca, España; `riansares4r@gmail.com`, +34 614 93 29 57. No se inventa una sociedad
ni se sustituye esa identidad por otra inferida de Git. Se actualizan aviso legal,
privacidad y cookies; el aviso no sustituye las dos políticas.

Athagon se presenta como portfolio personal de **Germán Caballero Rodríguez**, sin
empresa ni domicilio postal, conforme a la corrección del usuario. Ubicación: Cuenca,
España. Contacto existente: `german.caballero.rguez@gmail.com`. Nueva política en inglés,
`/privacy.html`, enlazada desde portfolio y formulario; no se recuperan domicilios de archivos.
No se certifica que «NIF disponible a solicitud» cierre todas las obligaciones del art. 10
LSSI que puedan resultar aplicables: es la información aportada, no un dato inventado.

Las políticas describen PostgreSQL/Ollama, Cloudflare y las rutas configuradas de
OpenAI (generación/auditoría) y AWS (recuperación). No prometen que todo sea local:
la configuración inspeccionada permite generación remota. Tampoco afirman una región,
contrato de encargo o garantía de transferencia sin verificarlos. No se cambió ningún
modelo, prompt, índice, corpus ni estrategia de generación/recuperación.

Derechos: acceso, rectificación, supresión, oposición, limitación y portabilidad cuando
proceda; retirada, contacto y reclamación AEPD. Procedimiento ordinario de respuesta de
un mes y ampliación legal justificada. Verificar solo la identidad/referencias necesarias,
no exigir documentos generales ni identificar más personas para mantener el historial.

Fuentes oficiales contrastadas:
[RGPD, especialmente arts. 5, 6, 7, 11, 12–22, 25 y 32](https://eur-lex.europa.eu/eli/reg/2016/679/oj),
[LOPDGDD, incluido bloqueo del art. 32](https://www.boe.es/buscar/act.php?id=BOE-A-2018-16673),
[LSSI, arts. 10 y 22.2](https://www.boe.es/buscar/act.php?id=BOE-A-2002-13758),
[guía de cookies AEPD](https://www.aepd.es/guias/guia-cookies.pdf).
La consulta pública `/aviso-legal` no fue recuperable con la herramienta web; se
contrastó la fuente local y la identificación facilitada directamente por el usuario.
No se declara conformidad jurídica absoluta ni retención indefinida lícita.

## 6. SQL, acceso y fiabilidad

`sql/assistant-usage.sql`: visitas, cobertura sin seguimiento, navegadores estimados,
nuevos/recurrentes por día (cohortes excluyentes), consultas por navegador/dominio,
estados iniciales/efectivos, auditorías, textos, medias, p50/p95 y periodos día/semana/mes.
Incluye revisión de vencimientos y seguridad. La primera visita es la primera observada
en los datos retenidos, no la primera visita vitalicia; no sumar dispositivos como personas.
La distribución analítica por país filtra consentimiento; las IPs se consultan únicamente
para investigación de seguridad autorizada.

`sql/assistant-usage-manual-erasure.sql`: UUID explícito de un solo evento, vista previa,
confirmación exacta, timeout corto y **ROLLBACK por defecto**. Solo una copia privada
revisada cambiada deliberadamente a COMMIT puede borrar. Incluye minimización de red,
desvinculación y restricción; no exportar textos/IPs a terminales compartidos.
Para una solicitud por navegador, localizar primero sus UUIDs con origen y `visitor_id`
en una sesión PostgreSQL autorizada y revisar la lista, sin DELETE global por fecha.

No hay endpoint de consulta del historial, IPs ni preguntas. El endpoint de retirada
no devuelve datos y se limita al origen y referencias aleatorias presentadas. Los
análisis ordinarios excluyen `restricted_at`. La cuenta de aplicación necesita solo
permisos necesarios de escritura/actualización y lectura de columnas de condición;
los accesos a textos/red deben reservarse a operadores autorizados. La separación
real de cuentas de migración/aplicación/operador debe verificarse en producción:
revocar PUBLIC no restringe por sí solo al propietario de la tabla o al superusuario.
No se modificaron cuentas ni permisos de producción.

Escritor de un hilo, cola de 128, timeout SQL de 2 s y fallo secundario aislado. No se
bloquea RAG por esperar a PostgreSQL. Se avisa de cola no disponible/fallo SQL **sin**
parámetros, textos, IPs ni excepciones que los contengan. Esto sigue siendo un registro
de mejor esfuerzo: caídas, saturación o cierre pueden perder eventos. No se promete
un historial del 100% del tráfico ni se sacrifica la respuesta RAG para conseguirlo.
Configurar PostgreSQL/TLS, cifrado de discos/copias, roles mínimos y ausencia de
logging de parámetros/cuerpos antes de activar datos personales.

## 7. Validación

- 57 pruebas backend seleccionadas: correctas; REST, SSE, auditoría, estados,
  consentimientos, fallos SQL, retirada con colección desactivada, quotas, IPv4/IPv6,
  country NULL, proxies no confiables y cabeceras falsificadas.
- 16 pruebas Angular: correctas. Se conserva el contrato JSON; solo se añaden headers opcionales.
- Build Angular producción: correcto.
- Prueba de navegador de privacidad: acepta/rechaza/retira, finalidades independientes,
  persistencia tras recarga, separación por dominio, gestor del iframe, bloqueo de GA,
  ausencia de pregunta en localStorage y solicitud de retirada sin textos.
- Regresión comercial: 1440/900/390 px, respuestas completas/parciales/abstenciones,
  error 429 y fallback REST; sin scroll interno ni overflow horizontal.
- PostgreSQL Docker: V5+V6 en esquema temporal, sentencias reales preparadas,
  IPv6, una fila ante repetición, auditoría con degradación, marca de retirada antes
  de insertar respuesta, desvinculación y SQL analítico. Todo con ROLLBACK.
- SQL de supresión por defecto: DELETE 0 y ROLLBACK en esquema temporal.
- No se han invocado LLMs reales ni publicado datos de visitantes para las pruebas.

Evidencias locales: `/tmp/r4r-history-{backend,angular,browser,commercial,database,erasure}-tests.log`,
`/tmp/r4r-history-build.log`, `/tmp/r4r-history-privacy-mobile.png` y
`rag-history-validation.json`. Los logs/capturas de /tmp no se añaden a Git.

## 8. Variables, reproducción y pendientes de producción

```properties
RAG_USAGE_ENABLED=true
RAG_USAGE_PRIVACY_READY=true
RAG_USAGE_CONTENT_ENABLED=true
RAG_USAGE_SECURITY_ENABLED=true
RAG_API_TRUST_PROXY_HEADERS=true
RAG_API_TRUSTED_PROXY_ADDRESSES=<IPs exactas de los peers de proxy observados>
```

Las tres nuevas autorizaciones de privacidad/texto/seguridad están desactivadas por
defecto. No habilitarlas sin políticas visibles, evaluación de interés legítimo de
seguridad y medidas/proveedores verificados. La lista vacía de proxies no confía en
cabeceras; no sustituirla por `*`, ni asumir las IPs de Cloudflare si el socket real es
cloudflared/nginx local. Comprobar el peer y la sobrescritura de cabeceras, manteniendo
la allowlist CORS explícita de las dos webs y sus variantes.

El comportamiento del script de build se amplió después: también copia al NAS por SMB.
Consultar `frontend-deployment.md` antes de ejecutar estos comandos; para comprobar
solo archivos locales hay que indicar un destino temporal en `NAS_FRONTEND_DIR`.

```sh
mvn -Dtest=AssistantUsageTest,ClientNetworkTest,RagQueryControllerTest,RagStreamControllerTest,RagApiSecurityFilterTest,RagAuditControllerTest test
CHROME_BIN=$(bash scripts/resolve-chrome-bin.sh) npm --prefix frontend run test:ci
bash scripts/build-frontend-angular.sh
python3 py-scripts/sync-privacy-assets.py --athagon /home/german/Desarrollo/athagon-web.git --commercial /home/german/Desarrollo/R4R/R4R.git
CHROMIUM_PATH=$(bash scripts/resolve-chrome-bin.sh) node frontend/validate-privacy.cjs
python3 py-scripts/prepare-commercial-release.py --commercial /home/german/Desarrollo/R4R/R4R.git --output /tmp/r4r-new-review
CHROMIUM_PATH=$(bash scripts/resolve-chrome-bin.sh) node frontend/validate-commercial.cjs /tmp/r4r-new-review
```

Paquete preparado: `/tmp/r4r-history-release-review`. Las pruebas leen las fuentes
legales de ambos repositorios y el build Angular. El script `build-frontend-angular.sh` compila, prerenderiza y copia automáticamente
el asistente a `athagon-web.git/html-static/r4r-rag/`, además del gestor de consentimiento
de la portada. `ATHAGON_WEB_ROOT` permite indicar otro checkout.
V6 la aplicará Flyway con el backend actualizado. No se ha aplicado a la base de
producción ni se ha cambiado `.env` privado. No hacer push de una portada sin su
bundle embebido y el gestor de consentimiento correspondiente.

Pendientes exclusivamente de puesta en producción: revisar destinos/pipeline existentes,
publicar juntos políticas/assets/frontend/backend, verificar permisos/TLS/copias,
contratos/transferencias, asignar responsable de revisión manual y declarar proxies
reales antes de activar las variables. Verificar una consulta controlada con y sin
consentimiento y los datos de borde efectivos. No hay despliegues, push, commits ni
aceptación de controlador simulada en esta sesión.
