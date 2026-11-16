# Compilación y distribución del frontend

Desde el repositorio RAG:

```sh
bash scripts/build-frontend-angular.sh
```

Compila Angular en producción, prerenderiza y actualiza `front-athagon-r4r-static/`.
Después copia el resultado completo, incluidos documentos y consentimiento, a:

1. `athagon-web.git/html-static/r4r-rag/`.
2. `R4R.git/dist/asistente-ia/`, único destino comercial, usado por la portada y para pruebas directas.
3. `smb://synox-nas.local/web/r4r-rag`, mediante el recurso SMB ya montado.

También sincroniza `privacy-consent.js` en las raíces estáticas de Athagon y Riansares.
No elimina documentos, archivos históricos ni `v1/` de los destinos. En SMB copia
sin atributos Unix y desreferencia enlaces simbólicos; actualiza `index.html` al final.

**La copia SMB sí actualiza los archivos servidos por el NAS.** No es un dry run.
No ejecuta Git ni publica por Cloudflare/GitLab. El script de despliegue del NAS
llama a este build como última etapa, después de desplegar el backend y completar
sus comprobaciones remotas. Dentro del build, las copias locales preceden a SMB:

```sh
bash scripts/deploy-synology.sh
```

Ese segundo comando despliega realmente el backend y puede aplicar migraciones al arrancar.
No se ha ejecutado como parte de esta modificación.

## Configuración del despliegue NAS

`deploy-synology.sh` requiere y copia **ambos archivos locales**:
`docker-spring/.env.base` y `docker-spring/.env`. Sustituye sus equivalentes en
`/volume1/docker/r4r`; los valores privados locales pasan a ser los del NAS.
Revisar las sobrescrituras locales antes de ejecutar el despliegue: `.env` tiene
prioridad sobre `.env.base` y cambiar una contraseña no cambia automáticamente la
contraseña de un usuario PostgreSQL ya existente.

La transferencia utiliza SSH y un directorio temporal privado (`0700`). Se valida
el Compose con ambos archivos transferidos antes de sustituir la configuración
activa o cargar la imagen. `.env` se instala con permisos `0600`, sin imprimir su
contenido, añadirlo a Git ni generar respaldos del archivo privado. La salida del
script intenta retirar su copia temporal también ante errores; si se pierde la
conexión, comprobar y retirar manualmente el temporal anunciado por el script.
El archivo privado activo queda propiedad de root y Compose lo lee mediante sudo.
La validación previa no garantiza un rollback si el arranque posterior falla.

Se conserva el orden: backend y comprobaciones remotas, después build y copias del
frontend, con SMB en último lugar. La copia de configuración forma parte del
despliegue backend, no del script de compilación de Angular.

## Montaje y rutas

El montaje detectado en esta máquina es:

```text
/run/user/<uid>/gvfs/smb-share:server=synox-nas.local,share=web
```

Abrir `smb://synox-nas.local/web` con Archivos o montarlo mediante:

```sh
gio mount smb://synox-nas.local/web
```

Se reutiliza la sesión/credenciales del montaje; no hay contraseñas en el script.
La disponibilidad SMB se comprueba al final, después de compilar y copiar a los
repositorios locales. Si falla SMB, la etapa frontend devuelve error, pero el backend
ya está desplegado y las copias locales permanecen realizadas; no se revierte el backend.
Si falla una copia posterior, los destinos anteriores pueden haber quedado actualizados;
no hay transacción distribuida ni rollback automático de archivos.

Variables opcionales:

```sh
ATHAGON_WEB_ROOT=/ruta/athagon-web.git \
COMMERCIAL_WEB_ROOT=/ruta/R4R.git \
NAS_FRONTEND_DIR=/ruta/local/al/montaje/web/r4r-rag \
bash scripts/build-frontend-angular.sh
```

`NAS_FRONTEND_DIR` es una ruta local montada, no una URL `smb://`. Su directorio
padre debe existir. Para una comprobación sin escribir en el NAS, usar un directorio
temporal existente como padre; los otros destinos siguen siendo los repositorios indicados.

## Validación de esta modificación

- `bash -n` de ambos scripts: correcto.
- Prueba aislada con destinos temporales y build simulado: copias a los tres
  destinos, documentos y archivos ocultos completos, preservación de `v1/`,
  desreferenciado SMB y error ante montaje ausente: correctos.
- `python3 -m unittest discover -s py-scripts -p test_deploy_synology.py -v`:
  3 pruebas correctas con SSH/Docker simulados y configuración sintética. Verifican
  copia de ambos archivos, permisos privados, retirada del temporal, frontend al
  final y conservación de la configuración activa ante transferencia o validación fallida.
- `python3 -m unittest discover -s py-scripts -p test_compose_configuration.py -v`:
  1 prueba correcta de Compose real sin arrancar servicios, incluidos los 400 ms
  tanto en defaults públicos como en el fallback autónomo de Compose.
- No se copió a SMB real ni se inició el despliegue backend durante la validación.
