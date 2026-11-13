R4R RAG STATIC DEMO
===================

URL prevista
------------
https://athagon.tech/r4r-rag/

Contenido
---------
r4r-rag/
  index.html
  styles/
    styles.css
  js/
    app.js
    i18n.js
    app.test.cjs
  README_DEPLOY.txt

Interfaz
--------
- Español e inglés en una única página, sin recarga al cambiar de idioma.
- Cabecera breve, centrada en IA/RAG.
- Caja de consulta inmediatamente después.
- Cuatro ejemplos que rellenan y lanzan automáticamente la consulta.
- Respuesta, abstención y fuentes.
- Explicación conceptual después de la demo.
- Arquitectura y configuración técnica al final.

API
---
Por defecto la web usa:

https://r4r-api.athagon.tech/api/rag/answers

El endpoint puede cambiarse desde la propia página; el valor se guarda
solo en localStorage del navegador, junto con el idioma y la última pregunta enviada.
Los estilos compartidos de ambos idiomas están en styles/styles.css.

Pruebas simuladas (sin API ni LLM reales):
node front-athagon-r4r-static/js/app.test.cjs

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

Preview y validación UX
----------------------
El autoplay requiere 50% del textarea visible durante 400 ms, también al entrar. Se ejecuta
una vez por sessionStorage; Probar la demo permite repetirlo. No genera tráfico.
El texto es una muestra guionizada basada en la descripción publicada de Riansares,
no una grabación certificada de una respuesta del modelo. Las consultas manuales
siguen usando SSE. La inspección runtime y warmup requieren una acción explícita.

Pruebas desde la raíz del repositorio:
  node front-athagon-r4r-static/js/app.test.cjs
  node front-athagon-r4r-static/js/browser.test.cjs
La segunda requiere Playwright instalado; PLAYWRIGHT_MODULE permite indicar su
ruta y CHROMIUM_PATH un Chromium existente. Usa rutas interceptadas sin LLM real,
viewports 1440x900 y 390x844, y captura PNG en /tmp/r4r-1440.png y /tmp/r4r-390.png.

PDF de las fuentes
------------------
Ocho documentos de knowledge/R4R_*.md (incluidas sus subcarpetas) se exportan
a docs/ conservando exactamente el nombre y cambiando .md por .pdf. No se
exportan sample.md ni el catálogo técnico de repositorios. Las citas conocidas
enlazan al PDF; los nombres desconocidos permanecen como texto seguro.

Regenerar desde la raíz:
  python3 py-scripts/export_sources.py
Dependencias locales: reportlab, markdown, beautifulsoup4 y fuentes DejaVu.
El logo de docs/riansares-4r-logo.png se extrajo de word/media/image1.png del
DOCX de presupuesto facilitado. Los MD originales y el backend no se modifican.

Final public UX release (2026-11-13)
----------------------------------
Quick examples are now recorded ES/EN answers, not automatic live queries.
The solera/solado autoplay uses captured SSE and final-answer data embedded in
index.html; fixtures/recordings.json retains the matching portable JSON.
If updating fixtures, update the embedded JSON as well (tests check equality).
Only explicit submit sends a live query. English translation belongs to the server.
Runtime loads on opening Technical details; endpoint controls require ?dev=1.
Deploy assets/ alongside index.html, js/, styles/ and docs/.
See docs/validation/final-public-ux/README.md from the repository root.
