# Compilación y distribución del frontend

Desde el repositorio RAG:

```sh
bash scripts/build-frontend-angular.sh
```

Compila Angular en producción, prerenderiza y actualiza `front-athagon-r4r-static/`.
Después copia el resultado completo, incluidos documentos y consentimiento, a:

1. `athagon-web.git/html-static/r4r-rag/`.
2. `R4R.git/dist/asistente/`, usado por la portada comercial.
3. `R4R.git/dist/r4r-rag-demo/`, para pruebas desde el dominio comercial.
4. `smb://synox-nas.local/web/r4r-rag`, mediante el recurso SMB ya montado.

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
- Prueba aislada con destinos temporales y build simulado: copias a los cuatro
  destinos, documentos y archivos ocultos completos, preservación de `v1/`,
  desreferenciado SMB y error ante montaje ausente: correctos.
- No se copió a SMB real ni se inició el despliegue backend durante la validación.
