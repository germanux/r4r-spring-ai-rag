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
- Conexión TCP de embeddings: 250 ms en los defaults públicos y Docker Compose.
- Tras un fallo recuperable de transporte/servicio, se omite ese proveedor durante
  30 s. Al vencer, la siguiente consulta vuelve a probarlo en su orden original.
  Un éxito elimina la marca. No hay pinning permanente al PC ni cambio de prioridad.
- La comprobación es HTTP Ollama, no ICMP: ping no acredita que el servicio funcione.
  No se introduce un scheduler ni sondeos de fondo/inferencias de calentamiento.
- Se conservan los límites de lectura de embeddings (8/8/30 s), el probe HTTP de 1 s,
  presupuesto global/SSE, modelo, digest, dimensiones, corpus y validaciones.
- Configuración/digest/autenticación/vectores inválidos siguen fallando explícitamente;
  no se convierten en disponibilidad positiva ni en fallback silencioso.
- Si todos están en cooldown, se devuelve indisponibilidad sin esperar de nuevo.

```properties
RAG_EMBEDDING_CONNECT_TIMEOUT=250ms
RAG_EMBEDDING_UNAVAILABLE_COOLDOWN=30s
```

Cooldown configurable entre 0 y 5 minutos; `0s` recupera el comportamiento sin caché.
La caché es acotada a los endpoints configurados, vive en memoria y no contiene textos.
La pausa afecta solo a proveedores que han fallado; el portátil sano no espera 30 s.
Esto reduce la espera evitable, no promete un tiempo total inferior a un segundo si
la generación local o la carga de otro modelo tardan más.

## Validación y estado

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
