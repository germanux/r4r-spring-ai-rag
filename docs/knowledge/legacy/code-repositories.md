# Repositorios de código de referencia

Este documento es simultáneamente legible por humanos y procesable por los scripts R4R.
Los clones son de solo lectura, viven bajo `.r4r/reference-repositories/` y no forman
parte del repositorio principal. Mantén habilitados uno o dos como máximo.

```json r4r-code-repositories
{
  "schemaVersion": 1,
  "workspace": "r4r-code",
  "maxEnabledRepositories": 2,
  "repositories": [
    {
      "id": "spring-ai-1.0.0",
      "enabled": true,
      "url": "https://github.com/spring-projects/spring-ai.git",
      "ref": "v1.0.0",
      "localDirectory": ".r4r/reference-repositories/spring-ai-1.0.0",
      "projectName": "spring-ai-1.0.0",
      "audience": ["PC"],
      "readOnly": true,
      "indexers": {"codeGraph": true, "codeGraphRag": true}
    },
    {
      "id": "angular-17.3.12",
      "enabled": true,
      "url": "https://github.com/angular/angular.git",
      "ref": "17.3.12",
      "localDirectory": ".r4r/reference-repositories/angular-17.3.12",
      "projectName": "angular-17.3.12",
      "audience": ["LP"],
      "readOnly": true,
      "indexers": {"codeGraph": true, "codeGraphRag": true}
    }
  ]
}
```

## Operación

- `npm run repos:list`: muestra el manifiesto sin clonar.
- `npm run repos:sync`: clona o fija cada repositorio al `ref` declarado.
- `npm run code:index`: actualiza CodeGraph y Code-Graph-RAG.
- No uses ramas móviles si necesitas resultados reproducibles.
- No edites ni hagas commits dentro de los clones de referencia.
