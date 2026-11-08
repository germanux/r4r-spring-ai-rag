# AWS CLI — R4R / S3 Vectors

Guía operativa (8 de noviembre de 2026). Comandos principales **en orden de uso**.

| Parámetro | Valor |
| --- | --- |
| Cuenta AWS | `124021120482` |
| Región | `eu-north-1` |
| Usuario IAM de la aplicación | `r4r-s3vectors-user` |
| Perfil **local** de AWS CLI | `r4r-s3vectors` |
| Vector bucket | `amzn-r4r-vectors` |
| Índice | `amzn-r4r-vector-index` |
| Configuración confirmada del índice | `float32`, 768 dimensiones, distancia `cosine` |

Los comandos 1–6 comprueban la configuración **del PC**. Los comandos 7–14 prueban S3 Vectors con el usuario IAM de lectura. Ejecutarlos desde el directorio raíz del repositorio, si procede. No requieren claves de administrador ni deben modificar vectores.

## A. Preparar y comprobar AWS CLI

**1. Versión de AWS CLI.**

```bash
aws --version
```

**2. Perfiles existentes.**

```bash
aws configure list-profiles
```

**3. Crear el perfil local (solo la primera vez, si no existe).** Introducir las dos claves del usuario IAM, no las del usuario root.

```bash
aws configure --profile r4r-s3vectors
```

**4. Fijar la región en ese perfil.**

```bash
aws configure set region eu-north-1 --profile r4r-s3vectors
```

**5. Comprobar el origen de la configuración y las credenciales (sin mostrar el secreto completo).**

```bash
aws configure list --profile r4r-s3vectors
```

**6. Confirmar la identidad AWS efectiva.** Debe terminar en `user/r4r-s3vectors-user`, cuenta `124021120482`.

```bash
aws sts get-caller-identity --profile r4r-s3vectors
```

## B. Verificar índice y permisos de lectura

**7. Obtener los detalles del índice.** Ya funcionó con este usuario.

```bash
aws s3vectors get-index \
  --region eu-north-1 \
  --vector-bucket-name amzn-r4r-vectors \
  --index-name amzn-r4r-vector-index \
  --profile r4r-s3vectors
```

**8. Ver únicamente las propiedades importantes del índice.**

```bash
aws s3vectors get-index \
  --region eu-north-1 \
  --vector-bucket-name amzn-r4r-vectors \
  --index-name amzn-r4r-vector-index \
  --profile r4r-s3vectors \
  --query 'index.{dimension:dimension,metric:distanceMetric,type:dataType}' \
  --output json
```

**9. Contar los vectores remotos.** Es el primer paso pendiente tras añadir `s3vectors:ListVectors` a la política IAM. La CLI pagina automáticamente; no añadas `--no-paginate`.

```bash
aws s3vectors list-vectors \
  --region eu-north-1 \
  --vector-bucket-name amzn-r4r-vectors \
  --index-name amzn-r4r-vector-index \
  --profile r4r-s3vectors \
  --query 'length(vectors)' \
  --output json
```

**10. Ver cinco claves, sin descargar embeddings ni texto de documentos.**

```bash
aws s3vectors list-vectors \
  --region eu-north-1 \
  --vector-bucket-name amzn-r4r-vectors \
  --index-name amzn-r4r-vector-index \
  --profile r4r-s3vectors \
  --max-items 5 \
  --query 'vectors[].key' \
  --output json
```

## C. Probar GetVectors y QueryVectors

**Condición:** el índice remoto debe contener al menos un vector. Estos pasos reutilizan un vector existente; **no** crean nuevos embeddings ni modifican AWS.

**11. Guardar la clave del primer vector remoto en una variable de shell.**

```bash
VECTOR_KEY="$(aws s3vectors list-vectors \
  --region eu-north-1 \
  --vector-bucket-name amzn-r4r-vectors \
  --index-name amzn-r4r-vector-index \
  --profile r4r-s3vectors \
  --max-items 1 \
  --query 'vectors[0].key' \
  --output text)"
```

Si devuelve `None`, no continuar: el índice puede estar vacío.

**12. Comprobar `GetVectors` sin mostrar el vector.**

```bash
aws s3vectors get-vectors \
  --region eu-north-1 \
  --vector-bucket-name amzn-r4r-vectors \
  --index-name amzn-r4r-vector-index \
  --profile r4r-s3vectors \
  --keys "$VECTOR_KEY" \
  --no-return-data \
  --query 'vectors[].key' \
  --output json
```

**13. Obtener el vector para reutilizarlo como consulta sin imprimir 768 valores.**

```bash
QUERY_VECTOR="$(aws s3vectors get-vectors \
  --region eu-north-1 \
  --vector-bucket-name amzn-r4r-vectors \
  --index-name amzn-r4r-vector-index \
  --profile r4r-s3vectors \
  --keys "$VECTOR_KEY" \
  --return-data \
  --query 'vectors[0].data' \
  --output json)"
```

**14. Probar una búsqueda por similitud en AWS (top 3).** Verifica `QueryVectors`, no la generación de embeddings del RAG. La distancia esperada del mismo vector consigo mismo es cercana a cero.

```bash
aws s3vectors query-vectors \
  --region eu-north-1 \
  --vector-bucket-name amzn-r4r-vectors \
  --index-name amzn-r4r-vector-index \
  --profile r4r-s3vectors \
  --query-vector "$QUERY_VECTOR" \
  --top-k 3 \
  --return-distance \
  --query 'vectors[].{key:key,distance:distance}' \
  --output json
```

**15. Probar que `QueryVectors` también puede devolver metadatos sin imprimirlos.** Comprueba el permiso adicional de `GetVectors`.

```bash
aws s3vectors query-vectors \
  --region eu-north-1 \
  --vector-bucket-name amzn-r4r-vectors \
  --index-name amzn-r4r-vector-index \
  --profile r4r-s3vectors \
  --query-vector "$QUERY_VECTOR" \
  --top-k 3 \
  --return-metadata \
  --query 'length(vectors)' \
  --output json
```

## D. Cómo autenticar Spring Boot en Synology

**El perfil AWS CLI no se despliega en Docker.** `AWS_PROFILE=r4r-s3vectors` solo selecciona el perfil almacenado localmente en `~/.aws/credentials`; **no** es la clave de la aplicación.

AWS S3 Vectors no utiliza una API key única al estilo de OpenAI. El SDK Java autentica las llamadas con una **pareja de claves IAM** y firma las solicitudes AWS (Signature V4). Usa las credenciales de `r4r-s3vectors-user`, limitadas al índice de lectura.

En el `.env` **privado del NAS**, con los nombres que Docker Compose debe inyectar en el servicio Spring Boot:

```dotenv
AWS_ACCESS_KEY_ID=<ACCESS_KEY_ID_DEL_USUARIO_IAM>
AWS_SECRET_ACCESS_KEY=<SECRET_ACCESS_KEY_DEL_USUARIO_IAM>
RAG_S3_VECTORS_ENABLED=true
RAG_VECTOR_PRIMARY=pgvector
RAG_VECTOR_FAILOVER_ENABLED=true
```

La región y el resto de valores públicos deben proceder de `.env.base`, si el Compose del proyecto está preparado para cargar primero `.env.base` y después `.env` como sobrescritura. **Verifica en el Compose real** que estas variables llegan al contenedor Spring; tenerlas en `.env` de Docker Compose **no basta por sí solo**, si el servicio no las declara en `environment:`/`env_file:`.

Para claves temporales de AWS STS hay además `AWS_SESSION_TOKEN`, que no debe ponerse con claves IAM permanentes.

No copies el archivo `~/.aws/credentials` al NAS, no ejecutes `aws login` en el contenedor, no uses credenciales root, no publiques las claves ni las subas a Git. Si todavía no tienes una clave de acceso para este usuario IAM, créala desde IAM → Usuarios → `r4r-s3vectors-user` → Credenciales de seguridad → Crear clave de acceso; almacena el secreto en cuanto se muestre.

**Política mínima comprobada para el usuario lector:** `s3vectors:GetIndex`, `s3vectors:ListVectors`, `s3vectors:GetVectors`, `s3vectors:QueryVectors` sobre el ARN exacto `arn:aws:s3vectors:eu-north-1:124021120482:bucket/amzn-r4r-vectors/index/amzn-r4r-vector-index`. Sin `PutVectors`, `DeleteVectors` ni operaciones de administración. Si `ListVectors` aún responde `AccessDenied`, comprueba que la política actualizada **se guardó y quedó adjuntada** al usuario, y vuelve a probar tras la propagación de IAM.

## E. Estado del MVP y siguiente paso

1. **Compilación y tests:** el log del 08/11/2026 termina en `BUILD SUCCESS`; Failsafe informa `34 tests`, `0 failures`, `0 errors`, `6 skipped`. No equivale a haber probado AWS/Synology en vivo.
2. **Compose local:** `Docker Compose 2.40.3` y `docker compose config --quiet` terminó sin error. Falta validar con la versión y las variables reales de Synology.
3. **Primera acción:** ejecuta el comando **9** y comunica solo el resultado numérico o el error, nunca claves.
4. **Después:** si hay vectores, ejecutar **10–15**; luego validación del failover PostgreSQL → S3, proveedores LLM/embeddings y despliegue en Synology.

Referencias oficiales:

- [AWS CLI: S3 Vectors](https://docs.aws.amazon.com/cli/latest/reference/s3vectors/)
- [AWS CLI: QueryVectors](https://docs.aws.amazon.com/cli/latest/reference/s3vectors/query-vectors.html)
- [AWS CLI: GetVectors](https://docs.aws.amazon.com/cli/latest/reference/s3vectors/get-vectors.html)
- [AWS SDK for Java 2.x: cadena de proveedores de credenciales](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/credentials-chain.html)
