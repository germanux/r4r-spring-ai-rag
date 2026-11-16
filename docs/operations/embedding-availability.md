# Disponibilidad rápida de embeddings

2026-11-16. El portátil .209 estaba apagado (confirmado por el usuario): las lecturas
de estado devolvían `No route to host` y el runtime del NAS marcaba embeddings no
alcanzables. El orden configurado seguía siendo portátil → PC → NAS. El router
anterior volvía a intentar el primero en cada lote, incluso después de un fallo.
La captura aportada mostraba unos 7,6 s en embeddings y 8 s en generación; esos 7,6 s
incluyen todos los intentos/carga/cálculo, no solo el timeout del portátil. Sin logs
SSH (autenticación no disponible) no se atribuye todo ese tiempo a una única causa.

## Cambio

- Se conserva el portátil como primera opción y se consulta como siempre si está sano.
- Conexión TCP de embeddings: 400 ms en los defaults públicos y Docker Compose,
  con margen adicional solicitado para la red Wi-Fi doméstica.
- Tras un fallo recuperable de conexión/servicio, salvo timeouts, se omite ese proveedor durante
  30 s. Al vencer, la siguiente consulta vuelve a probarlo en su orden original.
  Un éxito elimina la marca. No hay pinning permanente al PC ni cambio de prioridad.
- Un `SocketTimeoutException` o HTTP 408 continúa con el siguiente proveedor en
  esa consulta, pero no activa el cooldown: Ollama puede seguir cargando o estar
  ocupado, y la siguiente consulta debe poder volver a usar el portátil/PC.
  Se conservan los 30 s para otros fallos recuperables, incluido rechazo de conexión.
- La comprobación es HTTP Ollama, no ICMP: ping no acredita que el servicio funcione.
  No se introduce un scheduler. La precarga explícita al arrancar/abrir la página
  se describe en [precarga local](model-warmup.md).
- Lectura de consultas: 1,5 s en portátil y PC, 30 s en NAS; probe HTTP de 600 ms.
  Se conservan presupuesto global/SSE, modelo, digest, dimensiones, corpus y validaciones.
- Configuración/digest/autenticación/vectores inválidos siguen fallando explícitamente;
  no se convierten en disponibilidad positiva ni en fallback silencioso.
- Si todos están en cooldown, se devuelve indisponibilidad sin esperar de nuevo.

```properties
RAG_EMBEDDING_CONNECT_TIMEOUT=400ms
RAG_EMBEDDING_PROBE_TIMEOUT=600ms
RAG_EMBEDDING_UNAVAILABLE_COOLDOWN=30s
RAG_WARMUP_EMBEDDING_TIMEOUT=30s
```

Cooldown configurable entre 0 y 5 minutos; `0s` recupera el comportamiento sin caché.
La caché es acotada a los endpoints configurados, vive en memoria y no contiene textos.
La pausa afecta solo a proveedores que han fallado; el portátil sano no espera 30 s.
Esto reduce la espera evitable, no promete un tiempo total inferior a un segundo si
la generación local o la carga de otro modelo tardan más.

## Comprobación del margen de conexión

2026-11-16, desde el PC de desarrollo: 10 conexiones TCP y 5 lecturas HTTP de
`/api/ps` por servidor, sin generar embeddings ni respuestas. El portátil ya estaba
accesible en esta comprobación.

| Servidor | TCP mediana / máximo | HTTP `/api/ps` mediana / máximo |
| --- | --- | --- |
| Portátil `.209` | 4,50 / 63,73 ms | 8,97 / 17,72 ms |
| PC `.207` | 0,04 / 1,31 ms | 0,15 / 0,79 ms |
| NAS `.203` | 3,26 / 5,69 ms | 8,37 / 8,82 ms |

El límite de 400 ms solo acota el establecimiento de la conexión TCP, no el tiempo
de la llamada completa ni el cálculo del embedding. Los límites de lectura siguen
siendo independientes (probe 600 ms, inferencia 1,5/1,5/30 s). En esta muestra 250 ms no
rozaba el tiempo de conexión; se conservan 400 ms como margen adicional.
No es una medición desde el NAS ni una garantía frente a congestión, pérdida de
paquetes o cambios de cobertura Wi-Fi. No se afirma que la inferencia del portátil
tarde menos de un segundo a partir de una consulta de estado.

### Lectura real de `/api/tags`

Medición posterior del 2026-11-16 desde el PC: 20 peticiones HTTP por equipo,
separadas por 100 ms, incluidas conexión y lectura JSON; 0 errores. Qwen3 instalado
en ambos. PC: mediana 1,01 ms, p95 1,24 ms, máximo 1,74 ms. Portátil: mediana
23,20 ms, p95 102,32 ms, máximo 264,85 ms. Ambos por debajo de 500 ms en esta
muestra: se eligen los 600 ms solicitados, manteniendo conexión TCP de 400 ms.
No es una medición desde el NAS ni un benchmark del cálculo de embeddings.

## Validación y estado

### Corrección de competencia por instancia (2026-11-16)

El commit `4534541` extendió la precarga a todos los destinos mediante trabajos
independientes. Los tests iniciales usaban servidores separados por puerto para
LLM y embeddings: no representaban la competencia de ambos modelos en un mismo
Ollama. Además, se reenviaban cargas vacías aunque el modelo ya estuviera residente.
Una lectura de 1500 ms podía fallar mientras Ollama cargaba/intercambiaba modelos y
convertirse en 30 s de exclusión, favoreciendo repetidamente al NAS.

Corrección mínima: precargas serializadas por instancia, un solo papel de arranque
en frío (generación principal antes que embeddings; embeddings antes que generación
de respaldo), y ninguna carga especulativa sobre otro modelo residente o sin `/api/ps`
fiable. Las consultas reales conservan todos sus candidatos y su orden. Un timeout
ya no se considera evidencia suficiente para mantener un proveedor en cooldown.
No hay retries de inferencia dentro de una consulta ni aumentos de timeout.

Los logs incluyen identificador de operación, proveedor/host público, modelo,
fase, límites de conexión/sonda/lectura, fallo tipado, aplicación/restante del
cooldown, selección exitosa, resultado de la precarga y duración. No se registran
preguntas, vectores, cabeceras, mensajes de excepción ni URLs privadas.

**Comprobaciones reales, solo lectura:** Ollama 0.32.1 en PC y portátil; ambos
anuncian `qwen3-embedding:0.6b`. En la observación inicial el PC mantenía `qwen3:8b`
residente y el portátil no mostraba modelos residentes. El servicio systemd visible
del PC declara `OLLAMA_NUM_PARALLEL=1`, `OLLAMA_FLASH_ATTENTION=1` y su dirección de
escucha, pero no muestra `OLLAMA_MAX_LOADED_MODELS`. El entorno efectivo del proceso
no era legible; no se afirma por ello un límite concreto de modelos residentes.
La GPU del PC notificó 8188 MiB totales y 1495 MiB libres en esa instantánea; no
es una garantía de memoria disponible para otra carga. El contexto máximo anunciado
del embedding era 32768 y su dimensión 1024; no se confunde ese máximo con el
contexto efectivo de una llamada.

El SSH no interactivo al portátil no estaba disponible: queda pendiente comprobar
allí el entorno real del servicio. La API no expone `OLLAMA_MAX_LOADED_MODELS`.
`OLLAMA_MAX_LOADED_MODELS=1` del `.env.base` configura **solo el contenedor NAS**.
`OLLAMA_NUM_PARALLEL` limita solicitudes por modelo, no cuenta modelos residentes.
Según la [documentación oficial de Ollama](https://docs.ollama.com/faq#how-does-ollama-handle-concurrent-requests),
el alojamiento simultáneo también depende de memoria suficiente y las solicitudes
pueden provocar expulsiones para hacer sitio. No se ha modificado ni reiniciado
ningún servicio real ni forzado cargas/descargas para esta comprobación.

Para comprobar el límite en cada equipo sin volcar otras variables del servicio:

```sh
systemctl show ollama.service --property=Environment --value | python3 -c 'import shlex,sys; keys={"OLLAMA_MAX_LOADED_MODELS","OLLAMA_NUM_PARALLEL","OLLAMA_KEEP_ALIVE"}; print("\n".join(p for p in shlex.split(sys.stdin.read()) if p.partition("=")[0] in keys))'
```

Un valor ausente en esta salida no certifica el entorno efectivo si existen otros
orígenes de configuración. No establecer `MAX_LOADED_MODELS=2` a ciegas: no crea VRAM.
Con una sola plaza, peticiones reales a modelos diferentes pueden requerir cambios
de modelo; la corrección evita provocarlos por mera precarga/observación. Una
primera inferencia en frío todavía puede superar 1500 ms y usar el respaldo; se
mantiene ese límite solicitado y se permite recuperarse en la consulta siguiente.

Los resultados de este apartado corresponden a código local y servidores simulados;
no certifican el comportamiento de producción hasta desplegar y observarlo.

**Validación:** referencia inicial, 41 pruebas de precarga/failover correctas.
Tras la corrección, 45 pruebas de esas suites correctas (15 de precarga y 30 de
failover), incluidas instancias compartidas con una sola plaza, pestañas
concurrentes, ausencia de expulsiones por precarga, observación sin cargas,
selección portátil → PC sin consultas al NAS y recuperación sin exclusión de
30 s después de timeouts tanto de sonda como de inferencia.
`mvn verify`, ejecutado mediante `py-scripts/test_secret_isolation.py`, pasó con
597 unitarias y 86 integraciones offline, 0 fallos/errores, y 27 entradas externas
omitidas explícitamente. Se preservaron los timeouts, Qwen3/1024, digest, corpus,
REST/SSE y auditoría. No hubo renders ni despliegues.

Archivos de código: `OllamaModelPreloader.java`, `EmbeddingProviderRouter.java` y
sus dos suites; documentación actualizada en este documento, `model-warmup.md` y
`DEVELOPMENT.md`. Evidencias locales: `/tmp/r4r-ollama-coordination-before.log`,
`/tmp/r4r-ollama-coordination-tests.log`, `/tmp/r4r-ollama-coordination-verify.log`
y `target/local-security-fixture/maven.log`. Las 45 pruebas finales están incluidas
en las 597, no se suman otra vez. La inspección real no hizo inferencias ni cambios
de configuración en PC/portátil.

El ajuste posterior de precarga completa, 8 h y lecturas de 1,5 s/600 ms está
validado en [precarga local](model-warmup.md#validación-del-ajuste-de-8-horas-y-precarga-completa-2026-11-16).
Las cifras siguientes corresponden al cambio inicial de caché de indisponibilidad.

50 pruebas Java correctas: 27 de failover (incluidas caché, expiración, recuperación,
proveedor sano, conexión rechazada y errores no recuperables), 5 de configuración,
8 REST y 10 SSE. Reloj controlado para probar los 30 s sin esperas reales.
Un test offline de Docker Compose correcto. Servidores Ollama simulados; ninguna
inferencia real para validar el cambio.

El frontend sí se distribuyó mediante `build-frontend-angular.sh` y se verificó por
HTTP 200 en `http://192.168.1.203/r4r-rag/`, comparando índice y assets con el build.
No se generaron ni visualizaron imágenes/capturas. Las copias de Athagon/Riansares
quedan en sus repositorios locales; no se hizo push ni publicación Cloudflare/GitLab.

**Esta corrección de coordinación no se ha desplegado en el backend.** Requiere desplegar el
JAR/imagen y la configuración actualizados cuando el usuario pueda hacerlo.
No se reinició ni modificó el backend en ejecución. El despliegue de frontend no
activa este cambio Java. Evidencias: `/tmp/r4r-embedding-cooldown-tests.log`,
`/tmp/r4r-embedding-compose-tests.log`, `/tmp/r4r-front-deploy-20261116.log`.
