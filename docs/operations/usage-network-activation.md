# Registro persistente de IP completa y país — 2026-11-17

## Configuración preparada, no desplegada

```dotenv
RAG_API_TRUST_PROXY_HEADERS=true
RAG_API_TRUSTED_PROXY_ADDRESSES=172.20.0.6
RAG_USAGE_ENABLED=true
RAG_USAGE_PRIVACY_READY=true
RAG_USAGE_CONTENT_ENABLED=true
RAG_USAGE_SECURITY_ENABLED=true
```

Compose transmite ahora la allowlist y fija `SERVER_FORWARD_HEADERS_STRATEGY=none`.
Así Spring compara el peer del socket, sin reescribirlo previamente desde XFF.
No se leyó ni modificó `.env` privado. Sus posibles sobrescrituras deben revisarse
antes de aplicar la configuración. No se ha reiniciado ni desplegado la API.

El usuario ejecutó `docker inspect` en el NAS y confirmó la red
`r4r_r4r-network`: API `172.20.0.5`, cloudflared `172.20.0.6`, pasarela `172.20.0.1`.
Solo `172.20.0.6` está autorizado. No se confía en el NAS, la pasarela, un rango de
Docker, DNS ni comodines. Las IP de Docker pueden cambiar al recrear contenedores:
volver a comprobarlas antes de cada activación. Una allowlist obsoleta debe fallar
cerrada; no ampliarla a toda la subred para arreglar la pérdida de metadatos.

**Verificación aún pendiente:** la captura `/proc/net/tcp*` aportada solo mostró
el socket en escucha (`0A`) en 8080. No prueba qué peer recibe Spring durante una
consulta. Se solicitó una captura durante tráfico activo. Si el peer es la
pasarela en vez de cloudflared, corregir el servicio del túnel para usar
`http://api:8080` dentro de la red compartida; no autorizar la pasarela como atajo.
El acceso SSH disponible a `capi@192.168.1.203` fue rechazado; no se solicitaron
ni almacenaron contraseñas para sortearlo.

## Qué se conserva

`assistant_usage.client_ip` sigue siendo PostgreSQL `inet`: IP pública IPv4 completa
(/32) o IPv6 completa (/128), no hash, prefijo ni dirección truncada. Se conserva en
cada visita/consulta con origen reconocido, privacidad habilitada y sin oposición,
cuando hay una dirección fiable. Pregunta/respuesta/modelo/tiempos/auditoría siguen
las salvaguardias actuales. No se guardan documentos enteros ni cabeceras.

`country_code` es un ISO alfa-2 validado y separado; ES, IE y otros países se
conservan tal como los proporciona el proxy autorizado, sin GeoIP externo por
consulta. País ausente, `XX`, `T1`, malformado o ambiguo queda NULL. `UNKNOWN` es un
valor de `origin`, no un país ficticio. No se deduce un país a partir del dominio.

`network_basis=security-interest-v1` corresponde a filas con IP para seguridad;
`technical-geography-v1` a metadatos geográficos sin IP, por ejemplo tras oposición
al texto/IP. Sin datos fiables, el campo es NULL. El país puede persistir sin la IP;
la finalidad geográfica no requiere agrupar personas por su dirección.

## Cloudflare y seguridad

- Habilitar/verificar **IP Geolocation** para el hostname de la API. Añade
  `CF-IPCountry`; no dar por existente la cabecera solo porque pase por un túnel.
- Comprobar que no hay una regla de eliminación de IP de visitante o un Worker que
  elimine/altere estos metadatos. El código no puede activar ajustes de una zona de
  Cloudflare sin acceso administrativo a esa cuenta.
- `CF-Connecting-IP` se acepta solo desde el peer autorizado. Si Cloudflare usa
  Pseudo IPv4 en modo Overwrite, se recupera la IPv6 original exclusivamente desde
  `CF-Connecting-IPv6`, bajo el mismo control del peer. La IP pseudo no se persiste.
- Cabeceras duplicadas se rechazan como ambiguas; X-Forwarded-For no se usa.
  Proxies configurados como CIDR, comodín, hostname o dirección indeterminada
  producen error de configuración. Las formas equivalentes IPv6 se normalizan.
- Se mantienen los límites de solicitudes y el límite global. Un peer no autorizado
  no puede rotar CF/XFF para eludir el intervalo. No se añaden logs con IP o textos.
- El registro sigue siendo secundario. El cierre del escritor ahora drena su cola
  con una espera máxima de tres segundos antes de cancelar; antes descartaba
  inmediatamente los registros pendientes al apagar.

Referencias primarias:
[Cloudflare HTTP headers](https://developers.cloudflare.com/fundamentals/reference/http-headers/),
[IP geolocation](https://developers.cloudflare.com/network/ip-geolocation/).

## Interés legítimo, acceso y conservación

Interés: proteger el servicio, investigar abuso y disponibilidad, y evaluar qué
preguntas y carencias técnicas aparecen por país, incluido ES/IE. La geografía
usa país y preguntas lícitamente retenidas; no requiere IP completas en los
informes ordinarios. La IP completa se destina separadamente a seguridad,
correlación técnica e identificación de direcciones a bloquear en una incidencia.
El truncado o un hash impedirían conservar la dirección exacta para esas actuaciones.

Ponderación: el usuario espera protección y evaluación del asistente, pero no
seguimiento comercial individual. El riesgo aumenta al vincular dirección y texto:
se limita a operadores autorizados, se excluyen textos personales/sensibles según
las salvaguardias actuales, no hay identificadores persistentes ni historial público,
no se envían estos registros a GA y se respeta la oposición y supresión aplicables.
La IP no prueba la identidad de una persona; el país no prueba nacionalidad o residencia.

Conservación manual, sin cron ni purgas automáticas ni nuevos plazos arbitrarios.
Revisar la necesidad en mantenimiento de seguridad, al cerrar incidentes, al quedar
obsoletos casos técnicos y ante derechos o datos excesivos. Documentar la finalidad
vigente y la decisión. Eliminar/restringir IP o texto cuando cese su necesidad;
la estadística geográfica no permite conservar IP personales indefinidamente.
Pueden conservarse agregados realmente anónimos, con evaluación de reidentificación.
Las obligaciones/reclamaciones concretas pueden requerir bloqueo legal, no uso general.

Se actualizan las políticas canónicas de ambas webs (EN Athagon y ES Riansares),
sin cambiar responsables/contactos, cookies, GA, formularios ni funcionamiento del RAG.
Aplican arts. 5.1.c/e/f, 6.1.f, 13, 17/18/21 RGPD y, cuando corresponda, bloqueo del
art. 32 LOPDGDD. No se declara lícita una conservación personal indefinida.
[RGPD](https://eur-lex.europa.eu/eli/reg/2016/679/oj).

## Validación y pasos pendientes para producción

Completado:
- 41 pruebas focalizadas de resolución, falsificación, país ausente, IPv6,
  Pseudo IPv4, persistencia y rate limiting.
- 7 pruebas con PostgreSQL 16 real en Docker local, base exclusiva
  `usage_network_check`, credenciales ficticias y migraciones V5/V6/V7 sin modificar.
  Comprueban ambos orígenes, ES/IE/GB, igualdad exacta `inet`, máscaras /32 y /128,
  textos/modelo/tiempos y actualización de auditoría. Incluyen el controlador REST
  existente con generación simulada, y casos de oposición/origen desconocido/proxy
  no autorizado. No son consultas reales a los LLM ni tráfico de Cloudflare.
- `mvn verify` completo mediante el gate de aislamiento: 623 unitarias y 86
  integraciones offline correctas; 30 entradas externas omitidas explícitamente.
  Compose con secretos ficticios correcto. Las 7 pruebas PostgreSQL reales anteriores
  se ejecutaron por separado con su habilitación explícita, no como servicios live.
- Todas las consultas de `assistant-usage.sql` se ejecutaron correctamente contra
  PostgreSQL de prueba, sin imprimir filas, IP ni textos. Políticas EN/ES comprobadas
  estructuralmente, sin renderizados ni cambios en el frontend funcional.
- SQL de análisis incorpora cobertura de IP/country, geografía y textos ES/IE sin
  exponer IP en esa proyección. Las herramientas manuales de supresión siguen vigentes.

Pendiente, sin darlo por realizado:
1. Confirmar peer de una conexión activa y el destino privado del túnel.
2. Verificar IP Geolocation y cabeceras efectivas en la zona Cloudflare.
3. Publicar las políticas y aplicar/recrear la API mediante el procedimiento
   autorizado; no basta con editar el archivo local. Revisar las sobrescrituras privadas.
4. Tras aplicar, enviar una consulta normal por cada web, espaciadas al menos 12s
   y respetando Retry-After. Si el failover usa OpenAI, estas pruebas pueden consumir
   cuota; no se han lanzado aquí llamadas de pago ni consultas públicas de generación.
5. Verificar las referencias de esas consultas desde PostgreSQL como operador:

```sql
-- Sustituir los dos UUID por las referencias reales de las consultas de prueba.
-- No mostrar IP/textos en logs: comprobar presencia y exactitud desde la sesión privada.
SELECT id, origin, country_code, network_basis,
       client_ip IS NOT NULL AS ip_present,
       family(client_ip) AS address_family, masklen(client_ip) AS prefix_bits,
       question IS NOT NULL AS question_present, answer IS NOT NULL AS answer_present,
       model IS NOT NULL AS model_present, duration_ms,
       audit_completed_at IS NOT NULL AS audit_finished
FROM assistant_usage
WHERE id IN ('00000000-0000-4000-8000-000000000001'::uuid,
             '00000000-0000-4000-8000-000000000002'::uuid);
```

Para IP fiable, esperar /32 o /128 y basis de seguridad. Para país fiable, un código
ISO; si falta, investigar Cloudflare sin inventarlo. La prueba pública y el estado
actual de la base NAS no se certifican mientras falten acceso y activación autorizada.
