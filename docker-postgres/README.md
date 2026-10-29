# PostgreSQL

La configuración vigente es `../docker-spring/docker-compose.yml` en Synology.
Los tests Maven del PC acceden por LAN al puerto 5433; no requieren PostgreSQL local.
Este directorio conserva únicamente archivos históricos de inicialización: no ejecutarlos
contra las bases existentes. No mover ni borrar directorios de datos.
