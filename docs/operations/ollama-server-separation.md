# Embeddings Synology y generación PC/portátil

Spring AI 1.0.0 comparte normalmente `spring.ai.ollama.base-url` entre sus modelos.
R4R crea un `OllamaEmbeddingModel` con una conexión exclusiva para embeddings.
La autoconfiguración de embeddings retrocede ante ese bean. Se desactiva el chat
Ollama automático (`spring.ai.model.chat=none`); el único `ChatModel` de aplicación
es el `GenerationRouter` existente, con clientes Spring AI independientes.

El `VectorStore` recibe el único `EmbeddingModel` tanto para indexar documentos
como para calcular el vector de consulta. No se cambia la ingesta ni la búsqueda.
Flyway sigue gestionando el esquema; se mantiene `vector(768)` y no se reinicializa.

Referencia general: [integración Ollama de Spring AI](https://docs.spring.io/spring-ai/reference/api/embeddings/ollama-embeddings.html).
Los detalles de autoconfiguración se han comprobado en las fuentes locales de
Spring AI 1.0.0, la versión del proyecto.

## Variables

| Variable | Valor para ejecutar Java desde el PC |
|---|---|
| `RAG_EMBEDDING_BASE_URL` | `http://192.168.1.203:11434` |
| `RAG_EMBEDDING_MODEL` | `nomic-embed-text:latest` |
| `RAG_EMBEDDING_DIMENSIONS` | `768` |
| `RAG_PC_OLLAMA_BASE_URL` | `http://192.168.1.207:11434` |
| `RAG_PC_GENERATION_MODEL` | `qwen3:8b` |
| `RAG_FALLBACK_OLLAMA_BASE_URL` | `http://192.168.1.209:11434` |
| `RAG_FALLBACK_GENERATION_MODEL` | `qwen2.5:7b` |

Compose fija `RAG_EMBEDDING_BASE_URL=http://ollama-embeddings:11434` dentro del
contenedor API y espera a que el servicio esté saludable. No usar ese nombre DNS
desde Java ejecutado directamente en el PC.

`RAG_OLLAMA_BASE_URL` y `RAG_GENERATION_MODEL` ya no seleccionan ambos servicios.
Las propiedades `spring.ai.ollama.embedding.options.model` se conservan para el
modelo de embeddings y la información de runtime. Una URL global no determina
los clientes creados explícitamente por R4R.

## Verificación desde el PC

```bash
# Solo muestra nombres de modelos, no genera contenido.
for host in 192.168.1.203 192.168.1.207 192.168.1.209; do
  curl -fsS --max-time 10 "http://$host:11434/api/tags" |
    python3 -c 'import json,sys; print([m["name"] for m in json.load(sys.stdin)["models"]])'
done

# Embedding real: debe imprimir 768.
curl -fsS --max-time 90 http://192.168.1.203:11434/api/embed \
  -H 'Content-Type: application/json' \
  --data '{"model":"nomic-embed-text:latest","input":"Prueba R4R"}' |
  python3 -c 'import json,sys; print(len(json.load(sys.stdin)["embeddings"][0]))'

# Pruebas simuladas: clientes Spring AI reales, sin LLM ni BD externos.
mvn -q -Dtest=OllamaServerSeparationTest,GenerationRouterTest,RagRuntimeControllerTest,CitedRagServiceTest,CitedRagServiceCitationsTest test

# Tras un futuro despliegue autorizado, consulta completa y proveedor utilizado.
curl -fsS --max-time 150 http://192.168.1.203:18080/api/rag/answers \
  -H 'Origin: http://127.0.0.1:8000' -H 'Content-Type: application/json' \
  --data '{"question":"¿Qué información se necesita para orientar una reforma?","responseLanguage":"es"}'
curl -fsS http://192.168.1.203:18080/api/rag/runtime \
  -H 'Origin: http://127.0.0.1:8000' |
  python3 -c 'import json,sys; d=json.load(sys.stdin); print("preferido:",d["preferredProvider"]); print("último:",d["lastUsedProvider"])'
```

En Synology, las siguientes comprobaciones son de lectura y no despliegan:

```bash
cd /volume1/docker/r4r
docker compose exec -T api printenv RAG_EMBEDDING_BASE_URL
docker compose exec -T ollama-embeddings ollama ps
docker compose logs --since 10m api | grep 'completed by provider='
```

`ollama ps` muestra modelos cargados, no demuestra por sí solo qué aplicación los
utilizó. Las pruebas de aislamiento verifican el destino y el cuerpo HTTP de cada
operación. Los logs del router identifican el proveedor que completó la generación.

## Resultado de la validación previa al despliegue

- Synology `/api/embed`: 768 dimensiones con `nomic-embed-text:latest`.
- Contexto Java local con el código actualizado y conexiones reales: recuperación
  sobre `vector(768)` y respuesta con tres citas, generada por `qwen3:8b` en
  `192.168.1.207`. PostgreSQL forzado a `default_transaction_read_only=on`, Flyway
  desactivado; 282 filas antes y después. No se ejecutó ingesta sobre datos reales.
- El fallback se prueba automáticamente con tres servidores Ollama simulados:
  al fallar el principal, genera el portátil; ningún chat llega al servidor de
  embeddings y ningún embedding llega a los servidores de generación.

- Prueba real adicional con URL principal inaccesible únicamente en el proceso
  local: el portátil `192.168.1.209` completó la generación con `qwen2.5:7b`.
  El resultado final RAG fue abstención. No se alteraron los criterios de validación
  ni se detuvo ningún servidor. También se conservaron las 282 filas.
- 43 pruebas automatizadas correctas, incluidas ingesta, selección de proveedores,
  generación, citas y aislamiento de los clientes HTTP. Compose valida con
  `docker compose -f docker-spring/docker-compose.yml config --quiet`.

## Próximo despliegue: únicamente después de validación

No se ha desplegado ni editado ningún `.env` activo. Antes de un despliegue,
revisar o añadir manualmente las variables de la tabla en el `.env` existente,
conservando contraseña, token y demás valores. No copiar `.env.example` encima del
archivo activo. Usar el Compose actualizado para que aplique el DNS interno.
No borrar volúmenes, ejecutar `down -v`, truncar tablas ni reingerir documentos
como parte de esta separación. Revisar también cualquier override externo
`SPRING_AI_*` o `R4R_RAG_*` antes de arrancar.
