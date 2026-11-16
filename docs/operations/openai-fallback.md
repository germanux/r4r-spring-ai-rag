# Proveedores remotos del RAG

La configuración vigente está en [failover ordenado](ordered-provider-failover.md).
Las listas de generación y auditoría habilitan OpenAI independientemente y fijan su
posición, modelo y timeout. La ausencia de `openai` en una lista desactiva su uso
para esa función; ya no existen modos `false/true/primary`.

```dotenv
RAG_GENERATION_PROVIDERS=http://192.168.1.207:11434|qwen3:8b|20s,http://192.168.1.209:11434|qwen2.5:7b|60s,openai|gpt-6-luna|35s
RAG_AUDIT_PROVIDERS=http://192.168.1.207:11434|qwen3:8b|20s,openai|gpt-6-luna|35s
RAG_OPENAI_MAX_CALLS_PER_DAY=200
```

`OPENAI_API_KEY` permanece exclusivamente en el archivo privado ignorado o en una
inyección segura. El arranque falla si cualquier lista contiene OpenAI y falta la
clave. No se consultó ni modificó el archivo privado durante esta refactorización.
Hay que migrar sus posibles sobrescrituras antes del siguiente arranque; no cambiar
credenciales para adaptar la configuración.

Generación y auditoría utilizan adaptadores del mismo `OpenAiResponsesModel`,
incluso con modelos o timeouts diferentes. Comparten un contador diario UTC y un
semáforo de una llamada remota, sin cola. Se reservan intentos antes de hacer HTTP,
incluidos los fallidos. La cuota limita intentos, no sustituye el presupuesto de
cuenta. La saturación y la cuota agotada permiten recorrer el siguiente proveedor
configurado; autenticación y parámetros inválidos detienen el recorrido.

Cada intento remoto hace una sola petición Responses, `store=false`, conexión
máxima 3s y lectura limitada por su entrada y el presupuesto global restante.
Conserva el Prompt de Spring AI, idioma, contexto y protecciones existentes;
32.000 caracteres de entrada y 2.048 tokens de salida. No se reintenta una respuesta
válida por abstención, calidad o dictamen semántico de auditoría.

Los fallos remotos mantienen un motivo técnico saneado sin propagar cuerpos,
credenciales ni contenido del prompt en causas/logs. Los fallos locales conservan
su causa original. Se registran proveedor, modelo, resultado, tiempos y consumo
de tokens, sin contenido de las consultas.

Los contratos públicos REST/SSE y de auditoría se conservan. `provider` corresponde
a la petición; `remoteFallback` de runtime se deriva de la lista de generación.
OpenAI se muestra como nube y no recibe sondeos Ollama, precargas ni hardware local.
No hay cambios en Angular ni en los modelos, documentos o índices.

Validación offline y limitaciones: [informe de la refactorización](ordered-provider-failover.md).
Las pruebas live requieren perfil/configuración explícitos y autorización; no se
han ejecutado llamadas reales de pago en este encargo.
