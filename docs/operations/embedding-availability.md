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
- Tras un fallo recuperable de transporte/servicio, se omite ese proveedor durante
  30 s. Al vencer, la siguiente consulta vuelve a probarlo en su orden original.
  Un éxito elimina la marca. No hay pinning permanente al PC ni cambio de prioridad.
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

**La mejora de embeddings no está desplegada en el backend.** Requiere desplegar el
JAR/imagen y la configuración actualizados cuando el usuario pueda hacerlo.
No se reinició ni modificó el backend en ejecución. El despliegue de frontend no
activa este cambio Java. Evidencias: `/tmp/r4r-embedding-cooldown-tests.log`,
`/tmp/r4r-embedding-compose-tests.log`, `/tmp/r4r-front-deploy-20261116.log`.
