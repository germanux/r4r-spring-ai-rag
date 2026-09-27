R4R RAG STATIC DEMO
===================

Deployment target
-----------------
Upload this whole `r4r-rag` directory into the web root of athagon.tech.
The resulting URL should be:

https://athagon.tech/r4r-rag/

Files
-----
index.html
styles.css
app.js

API endpoint
------------
The default API base URL is configured at the top of app.js:

const DEFAULT_API_BASE_URL = "https://r4r-api.athagon.tech";

The page calls:

POST https://r4r-api.athagon.tech/api/rag/answers

You can also change the endpoint from the "Demo endpoint" section in the page.
That override is stored only in the current browser's localStorage.

Important for production
------------------------
If the API is hosted on a different origin/subdomain, Spring must allow CORS from:

https://athagon.tech

Do not expose PostgreSQL directly to the Internet.
Publish only the HTTPS API endpoint through your chosen reverse proxy / tunnel.

Expected API response
---------------------
{
  "answer": "...",
  "abstained": false,
  "citations": [
    {
      "label": "[S1]",
      "source": "...",
      "headingPath": ["...", "..."],
      "ordinal": 4
    }
  ]
}
