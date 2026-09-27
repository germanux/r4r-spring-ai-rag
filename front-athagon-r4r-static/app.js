(() => {
  "use strict";

  const DEFAULT_API_BASE_URL = "https://r4r-api.athagon.tech";
  const ENDPOINT_PATH = "/api/rag/answers";
  const STORAGE_KEY = "r4r-rag-api-base-url";

  const $ = (id) => document.getElementById(id);

  const question = $("question");
  const sendButton = $("sendButton");
  const sendLabel = $("sendLabel");
  const clearButton = $("clearButton");
  const spinner = $("spinner");
  const elapsed = $("elapsed");
  const apiState = $("apiState");
  const apiBaseUrl = $("apiBaseUrl");
  const saveEndpoint = $("saveEndpoint");

  const emptyState = $("emptyState");
  const resultContent = $("resultContent");
  const errorBox = $("errorBox");
  const httpStatus = $("httpStatus");
  const resultMode = $("resultMode");
  const decisionPill = $("decisionPill");
  const decisionText = $("decisionText");
  const answerText = $("answerText");
  const citations = $("citations");
  const citationCount = $("citationCount");

  const normalizeBaseUrl = (value) => (value || "").trim().replace(/\/+$/, "");

  const storedApi = localStorage.getItem(STORAGE_KEY);
  apiBaseUrl.value = normalizeBaseUrl(storedApi || DEFAULT_API_BASE_URL);

  document.querySelectorAll(".sample").forEach((button) => {
    button.addEventListener("click", async () => {
      question.value = button.dataset.question || "";
      question.focus();
      await sendQuestion();
    });
  });

  saveEndpoint.addEventListener("click", () => {
    const value = normalizeBaseUrl(apiBaseUrl.value);
    if (!value) return;
    localStorage.setItem(STORAGE_KEY, value);
    apiBaseUrl.value = value;
    apiState.textContent = "endpoint guardado";
  });

  clearButton.addEventListener("click", () => {
    question.value = "";
    renderEmpty();
    question.focus();
  });

  question.addEventListener("keydown", (event) => {
    if ((event.ctrlKey || event.metaKey) && event.key === "Enter") {
      sendQuestion();
    }
  });

  sendButton.addEventListener("click", sendQuestion);

  async function sendQuestion() {
    if (sendButton.disabled) return;

    const text = question.value.trim();
    if (!text) {
      question.focus();
      return;
    }

    const baseUrl = normalizeBaseUrl(apiBaseUrl.value || DEFAULT_API_BASE_URL);
    const url = `${baseUrl}${ENDPOINT_PATH}`;

    setLoading(true);
    renderEmpty();
    apiState.textContent = "consultando";
    resultMode.textContent = "consulta en curso";
    httpStatus.textContent = "…";

    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 120000);
    const started = performance.now();

    try {
      const response = await fetch(url, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "Accept": "application/json"
        },
        body: JSON.stringify({ question: text }),
        signal: controller.signal
      });

      const duration = Math.round(performance.now() - started);
      elapsed.textContent = `${duration} ms`;
      httpStatus.textContent = `${response.status}`;

      let payload;
      try {
        payload = await response.json();
      } catch {
        throw new Error(`HTTP ${response.status}: la respuesta no es JSON válido.`);
      }

      if (!response.ok) {
        throw new Error(payload?.message || payload?.error || `HTTP ${response.status}`);
      }

      renderResult(payload);
      apiState.textContent = "online";
      resultMode.textContent = "respuesta RAG";
    } catch (error) {
      const duration = Math.round(performance.now() - started);
      elapsed.textContent = `${duration} ms`;
      apiState.textContent = "error";
      resultMode.textContent = "la consulta ha fallado";

      renderError(
        error?.name === "AbortError"
          ? "La consulta ha superado el tiempo máximo de 120 segundos."
          : `${error?.message || error}\n\nSi la API está en otro dominio o subdominio, revisa HTTPS, DNS y CORS para https://athagon.tech.`
      );
    } finally {
      clearTimeout(timeout);
      setLoading(false);
    }
  }

  function setLoading(loading) {
    sendButton.disabled = loading;
    sendButton.classList.toggle("is-loading", loading);
    spinner.setAttribute("aria-hidden", loading ? "false" : "true");
    sendLabel.textContent = loading ? "Consultando…" : "Preguntar";
  }

  function renderEmpty() {
    emptyState.hidden = false;
    resultContent.hidden = true;
    errorBox.hidden = true;
    httpStatus.textContent = "—";
    resultMode.textContent = "esperando una pregunta";
    elapsed.textContent = "";
  }

  function renderError(message) {
    emptyState.hidden = true;
    resultContent.hidden = true;
    errorBox.hidden = false;
    errorBox.textContent = message;
  }

  function renderResult(payload) {
    const abstained = Boolean(payload?.abstained);
    const answer = typeof payload?.answer === "string" ? payload.answer : "";
    const sourceList = Array.isArray(payload?.citations) ? payload.citations : [];

    emptyState.hidden = true;
    errorBox.hidden = true;
    resultContent.hidden = false;

    decisionPill.textContent = abstained ? "ABSTENCIÓN" : "RESPUESTA";
    decisionPill.classList.toggle("abstain", abstained);
    decisionText.textContent = abstained
      ? "No hay evidencia suficiente para responder con seguridad."
      : "Respuesta generada a partir de la evidencia recuperada.";

    answerText.textContent = answer || (abstained ? "El sistema se ha abstenido de responder." : "Respuesta vacía.");

    citationCount.textContent = `${sourceList.length} fuente${sourceList.length === 1 ? "" : "s"}`;
    citations.replaceChildren();

    if (!sourceList.length) {
      const note = document.createElement("div");
      note.className = "citation";
      note.textContent = abstained
        ? "No se devuelven citas porque el sistema se ha abstenido."
        : "No se han devuelto citas.";
      citations.appendChild(note);
      return;
    }

    sourceList.forEach((citation) => {
      const card = document.createElement("article");
      card.className = "citation";

      const top = document.createElement("div");
      top.className = "citation-top";

      const label = document.createElement("span");
      label.className = "citation-label";
      label.textContent = citation?.label || "[S?]";

      const source = document.createElement("span");
      source.className = "citation-source";
      source.textContent = citation?.source || "fuente desconocida";

      top.append(label, source);

      const path = document.createElement("div");
      path.className = "citation-path";
      const headingPath = Array.isArray(citation?.headingPath) ? citation.headingPath : [];
      path.textContent = headingPath.length ? headingPath.join(" › ") : "Sin ruta de encabezados";

      const ordinal = document.createElement("div");
      ordinal.className = "citation-ordinal";
      ordinal.textContent = Number.isInteger(citation?.ordinal)
        ? `Ordinal del fragmento: ${citation.ordinal}`
        : "Ordinal del fragmento: —";

      card.append(top, path, ordinal);
      citations.appendChild(card);
    });
  }
})();
