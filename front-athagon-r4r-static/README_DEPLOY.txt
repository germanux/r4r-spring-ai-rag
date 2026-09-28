R4R RAG STATIC DEMO
===================

URL prevista
------------
https://athagon.tech/r4r-rag/

Contenido
---------
r4r-rag/
  index.html
  styles.css
  app.js
  README_DEPLOY.txt

Interfaz
--------
- Español.
- Cabecera breve, centrada en IA/RAG.
- Caja de consulta inmediatamente después.
- Tres ejemplos que rellenan y lanzan automáticamente la consulta.
- Respuesta, abstención y fuentes.
- Explicación conceptual después de la demo.
- Arquitectura y configuración técnica al final.

API
---
Por defecto la web usa:

https://r4r-api.athagon.tech/api/rag/answers

El endpoint puede cambiarse desde la propia página; el valor se guarda
solo en localStorage del navegador.

CORS
----
Si la API está en un subdominio distinto, Spring debe permitir como origen:

https://athagon.tech

Importante
----------
El meta robots está configurado como noindex,nofollow, pero esto NO es
una medida de seguridad. La seguridad real debe aplicarse en la API /
reverse proxy antes de abrirla públicamente.

PostgreSQL no debe exponerse a Internet.
