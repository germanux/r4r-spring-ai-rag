#!/usr/bin/env bash
set -euo pipefail

# Raíz del proyecto.
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="$ROOT/docker-spring"

cd "$ROOT"

# Compilar y ejecutar todas las pruebas.
echo "==> Compilando y ejecutando tests..."
mvn clean verify

# Obtener automáticamente la versión y el nombre del JAR desde Maven.
PROJECT_VERSION="$(mvn -q help:evaluate -Dexpression=project.version -DforceStdout)"
JAR_NAME="$(mvn -q help:evaluate -Dexpression=project.build.finalName -DforceStdout)"

# Configuración de imagen Docker.
IMAGE_NAME="${IMAGE_NAME:-r4r-api}"
IMAGE_TAG="${IMAGE_TAG:-$PROJECT_VERSION}"
IMAGE="$IMAGE_NAME:$IMAGE_TAG"

# Ruta real del JAR generado.
JAR="$ROOT/target/${JAR_NAME}.jar"

[[ -f "$JAR" ]] || {
  echo "ERROR: no existe $JAR" >&2
  exit 1
}

mkdir -p "$OUT"

echo "==> Versión Maven: $PROJECT_VERSION"
echo "==> JAR: $JAR_NAME.jar"
echo "==> Imagen Docker: $IMAGE"

# Copiar JAR al contexto Docker.
cp "$JAR" "$OUT/app.jar"

# Generar Dockerfile.
cat > "$OUT/Dockerfile" <<'EOF'
FROM eclipse-temurin:21-jre

WORKDIR /app

COPY app.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=65.0", "-jar", "/app/app.jar"]
EOF

# Construir imagen.
echo "==> Construyendo imagen..."
docker build -t "$IMAGE" "$OUT"

# Exportar imagen.
TAR="$OUT/${IMAGE_NAME}-${IMAGE_TAG}.tar"

echo "==> Exportando imagen..."
docker save -o "$TAR" "$IMAGE"

echo
echo "OK"
echo "Versión Maven: $PROJECT_VERSION"
echo "Imagen Docker: $IMAGE"
echo "JAR: $JAR"
echo "Exportada en: $TAR"

docker image ls "$IMAGE"
ls -lh "$TAR"
