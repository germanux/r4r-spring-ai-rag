#!/usr/bin/env bash
set -euo pipefail

# Raíz del proyecto, independientemente de desde dónde ejecutes el script.
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="$ROOT/docker-spring"

# Nombre/tag de la imagen Docker.
IMAGE_NAME="${IMAGE_NAME:-r4r-api}"
IMAGE_TAG="${IMAGE_TAG:-0.3.0}"
IMAGE="$IMAGE_NAME:$IMAGE_TAG"

# JAR generado por Maven.
JAR="$ROOT/target/r4r-spring-ai-rag-0.3.0-SNAPSHOT.jar"

mkdir -p "$OUT"

echo "==> Compilando y ejecutando tests..."
cd "$ROOT"
mvn clean verify

[[ -f "$JAR" ]] || {
  echo "ERROR: no existe $JAR" >&2
  exit 1
}

# Copiamos el JAR al contexto Docker.
cp "$JAR" "$OUT/app.jar"

# Dockerfile del contenedor Spring.
cat > "$OUT/Dockerfile" <<'EOF'
FROM eclipse-temurin:21-jre

WORKDIR /app

COPY app.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=65.0", "-jar", "/app/app.jar"]
EOF

echo "==> Construyendo imagen $IMAGE..."
docker build \
  -t "$IMAGE" \
  "$OUT"

# Exportamos la imagen completa para poder importarla en Synology.
TAR="$OUT/${IMAGE_NAME}-${IMAGE_TAG}.tar"

echo "==> Exportando imagen a $TAR..."
docker save \
  -o "$TAR" \
  "$IMAGE"

echo
echo "OK"
echo "Imagen Docker: $IMAGE"
echo "Exportada en:  $TAR"
docker image ls "$IMAGE"
ls -lh "$TAR"
