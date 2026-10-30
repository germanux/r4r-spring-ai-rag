(() => {
  "use strict";

  //const DEFAULT_API_BASE_URL = "https://r4r-api.athagon.tech";
   const DEFAULT_API_BASE_URL = "http://192.168.1.203:18080";
  //const DEFAULT_API_BASE_URL = "http://127.0.0.1:8080";
  const ENDPOINT_PATH = "/api/rag/answers";
  const STORAGE_KEY = "r4r-rag-api-base-url";

  const isEnglish = document.documentElement.lang.toLowerCase().startsWith("en");
  const messages = isEnglish ? {
    saved: "endpoint saved", copied: "Copied", copy: "Copy", querying: "querying",
    inProgress: "query in progress", result: "RAG response", failed: "the query failed",
    loading: "Querying…", send: "Ask", waiting: "waiting for a query",
    abstention: "ABSTENTION", answer: "ANSWER",
    noEvidence: "There is not enough evidence to answer reliably.",
    evidence: "Answer generated from retrieved evidence.",
    abstained: "The system abstained from answering.", emptyAnswer: "Empty answer.",
    noCitationsAbstained: "No citations are returned because the system abstained.",
    noCitations: "No citations were returned.", unknownSource: "unknown source",
    noHeading: "No heading path", ordinal: "Chunk ordinal",
    timeout: "The query exceeded the 120 second time limit.",
    invalidJson: (status) => `HTTP ${status}: the response is not valid JSON.`,
    httpError: (status) => `Request failed (HTTP ${status}).`,
    cors: "If the API is on a different domain or subdomain, check HTTPS, DNS and CORS for https://athagon.tech.",
    sources: (count) => `${count} source${count === 1 ? "" : "s"}`
  } : {
    saved: "endpoint guardado", copied: "Copiado", copy: "Copiar", querying: "consultando",
    inProgress: "consulta en curso", result: "respuesta RAG", failed: "la consulta ha fallado",
    loading: "Consultando…", send: "Preguntar", waiting: "esperando una consulta",
    abstention: "ABSTENCIÓN", answer: "RESPUESTA",
    noEvidence: "No hay evidencia suficiente para responder con seguridad.",
    evidence: "Respuesta generada a partir de la evidencia recuperada.",
    abstained: "El sistema se ha abstenido de responder.", emptyAnswer: "Respuesta vacía.",
    noCitationsAbstained: "No se devuelven citas porque el sistema se ha abstenido.",
    noCitations: "No se han devuelto citas.", unknownSource: "fuente desconocida",
    noHeading: "Sin ruta de encabezados", ordinal: "Ordinal del fragmento",
    timeout: "La consulta ha superado el tiempo máximo de 120 segundos.",
    invalidJson: (status) => `HTTP ${status}: la respuesta no es JSON válido.`,
    httpError: (status) => `HTTP ${status}`,
    cors: "Si la API está en otro dominio o subdominio, revisa HTTPS, DNS y CORS para https://athagon.tech.",
    sources: (count) => `${count} fuente${count === 1 ? "" : "s"}`
  };

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
  const questionCount = $("questionCount");
  const copyAnswerButton = $("copyAnswerButton");

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
  const loadingState = $("loadingState");
  const sourcesDetails = $("sourcesDetails");

  const normalizeBaseUrl = (value) => (value || "").trim().replace(/\/+$/, "");

  const storedApi = localStorage.getItem(STORAGE_KEY);
  apiBaseUrl.value = normalizeBaseUrl(storedApi || DEFAULT_API_BASE_URL);

  updateCounter();

  const tryDemoButton = $("tryDemoButton");
  if (tryDemoButton) {
    tryDemoButton.addEventListener("click", (event) => {
      event.preventDefault();
      question.value = "¿Qué tipo de reformas hace esta empresa? ¿Casas y oficinas?";
      updateCounter();
      document.getElementById("demo").scrollIntoView({ behavior: "smooth", block: "start" });
      sendQuestion();
    });
  }

  loadRuntimeInfo();
  let runtimePoll;
  function pollRuntimeWhileLoading(status) {
    clearTimeout(runtimePoll);
    if (status === "Cargando modelo") runtimePoll = setTimeout(async () => { await loadRuntimeInfo(); }, 5000);
  }

  function formatBytes(bytes) {
    if (!bytes) return "0 B";
    const units = ["B", "GB", "TB"];
    let value = bytes, index = 0;
    while (value >= 1024 && index < units.length - 1) { value /= 1024; index++; }
    return `${value.toFixed(index ? 1 : 0)} ${units[index]}`;
  }

  function shortModelName(model) {
    const value = (model || "").toLowerCase();
    if (value.includes("qwen2.5") || value.includes("qwen2-5") || value.includes("qwen 2.5")) return "Qwen 2.5 7B";
    return model || "No disponible";
  }

  async function loadRuntimeInfo() {
    if (!(document.getElementById("runtimeModel"))) return;
    try {
      const response = await fetch(`${normalizeBaseUrl(apiBaseUrl.value)}${ENDPOINT_PATH.replace("/answers", "/runtime")}`, { headers: { Accept: "application/json" } });
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      const info = await response.json();
      $("runtimeModel").textContent = info.configuredModel || info.generationModel || "No disponible";
      $("runtimeModelStatus").textContent = info.modelStatus || "Ollama no disponible";
      pollRuntimeWhileLoading(info.modelStatus);
      $("runtimeModelFull").textContent = info.generationModel || "No disponible";
      $("runtimeEmbeddings").textContent = `${info.embeddingModel || "—"} · ${info.embeddingDimensions || "—"} dimensiones`;
      $("runtimeRetrieval").textContent = `Top-K ${info.topK} · score mínimo ${info.minScore}`;
      $("runtimeVersions").textContent = `Java ${info.javaVersion || "—"} · Spring Boot ${info.springBootVersion || "—"} · Spring AI ${info.springAiVersion || "—"}`;
      $("runtimeDeployment").textContent = info.deployment || "No disponible";
      const loaded = Array.isArray(info.loadedModels) ? info.loadedModels : [];
      $("runtimeOllama").textContent = loaded.length
        ? loaded.map((model) => `${model.name} (${model.parameterSize}; ${formatBytes(model.sizeBytes)} en memoria; ${formatBytes(model.vramBytes)} VRAM)`).join(" · ")
        : "Sin modelos cargados o información no disponible";
    } catch {
      $("runtimeModel").textContent = "No disponible";
      $("runtimeModelStatus").textContent = "Ollama no disponible";
      $("runtimeModelFull").textContent = "No disponible";
      $("runtimeEmbeddings").textContent = "No disponible";
      $("runtimeRetrieval").textContent = "No disponible";
      $("runtimeVersions").textContent = "No disponibles";
      $("runtimeDeployment").textContent = "No disponible";
      $("runtimeOllama").textContent = "Información no disponible";
    }
  }

  document.querySelectorAll(".sample").forEach((button) => {
    button.addEventListener("click", async () => {
      if (sendButton.disabled) return;
      question.value = button.dataset.question || "";
      updateCounter();
      question.focus();
      await sendQuestion();
    });
  });

  saveEndpoint.addEventListener("click", () => {
    const value = normalizeBaseUrl(apiBaseUrl.value);
    if (!value) return;
    localStorage.setItem(STORAGE_KEY, value);
    apiBaseUrl.value = value;
    apiState.textContent = messages.saved;
    loadRuntimeInfo();
  });

  clearButton.addEventListener("click", () => {
    question.value = "";
    updateCounter();
    renderEmpty();
    question.focus();
  });

  question.addEventListener("input", updateCounter);

  question.addEventListener("keydown", (event) => {
    if ((event.ctrlKey || event.metaKey) && event.key === "Enter") {
      sendQuestion();
    }
  });

  sendButton.addEventListener("click", sendQuestion);

  if (copyAnswerButton) {
    copyAnswerButton.addEventListener("click", async () => {
      const text = answerText.textContent.trim();
      if (!text) return;
      try {
        await navigator.clipboard.writeText(text);
        const original = copyAnswerButton.textContent;
        copyAnswerButton.textContent = messages.copied;
        setTimeout(() => {
          copyAnswerButton.textContent = original;
        }, 1200);
      } catch {
        copyAnswerButton.textContent = "Error";
        setTimeout(() => {
          copyAnswerButton.textContent = messages.copy;
        }, 1200);
      }
    });
  }

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
    if (loadingState) {
      emptyState.hidden = true;
      loadingState.hidden = false;
      answerText.textContent = "";
      citations.replaceChildren();
      citationCount.textContent = "";
      sourcesDetails.open = false;
    }
    apiState.textContent = messages.querying;
    resultMode.textContent = messages.inProgress;
    httpStatus.textContent = "…";

    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 120000);
    const started = performance.now();

    try {
      let requestBody = { question: text, responseLanguage: "es" };
      if (isEnglish) {
        let retrievalQuestion = text;
        try {
          retrievalQuestion = (await translateToSpanish(text, "en", controller.signal)).trim() || text;
        } catch (error) {
          if (error?.name === "AbortError") throw error;
        }
        requestBody = { question: text, retrievalQuestion, responseLanguage: "en" };
      }
      const response = await fetch(url, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "Accept": "application/json"
        },
        body: JSON.stringify(requestBody),
        signal: controller.signal
      });

      const duration = Math.round(performance.now() - started);
      elapsed.textContent = `${duration} ms`;
      httpStatus.textContent = `${response.status}`;

      let payload;
      try {
        payload = await response.json();
      } catch {
        throw new Error(messages.invalidJson(response.status));
      }

      if (!response.ok) {
        throw new Error(isEnglish ? messages.httpError(response.status) : (payload?.message || payload?.error || messages.httpError(response.status)));
      }

      renderResult(payload);
      apiState.textContent = "online";
      resultMode.textContent = messages.result;
      if (isEnglish) resultContent.scrollIntoView({ behavior: "smooth", block: "start" });
    } catch (error) {
      const duration = Math.round(performance.now() - started);
      elapsed.textContent = `${duration} ms`;
      apiState.textContent = "error";
      resultMode.textContent = messages.failed;

      renderError(
        error?.name === "AbortError"
          ? messages.timeout
          : `${error?.message || error}\n\n${messages.cors}`
      );
    } finally {
      clearTimeout(timeout);
      setLoading(false);
    }
  }

  function setLoading(loading) {
    sendButton.disabled = loading;
    if (loadingState) {
      clearButton.disabled = loading;
      if (!loading) loadingState.hidden = true;
    }
    sendButton.classList.toggle("is-loading", loading);
    spinner.setAttribute("aria-hidden", loading ? "false" : "true");
    sendLabel.textContent = loading ? messages.loading : messages.send;
  }

  function renderEmpty() {
    emptyState.hidden = false;
    resultContent.hidden = true;
    errorBox.hidden = true;
    if (loadingState) loadingState.hidden = true;
    httpStatus.textContent = "—";
    resultMode.textContent = messages.waiting;
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

    decisionPill.textContent = abstained ? messages.abstention : messages.answer;
    decisionPill.classList.toggle("abstain", abstained);
    decisionText.textContent = abstained
      ? messages.noEvidence
      : messages.evidence;

    answerText.textContent = answer || (abstained ? messages.abstained : messages.emptyAnswer);

    citationCount.textContent = messages.sources(sourceList.length);
    citations.replaceChildren();

    if (!sourceList.length) {
      const note = document.createElement("div");
      note.className = "citation";
      note.textContent = abstained
        ? messages.noCitationsAbstained
        : messages.noCitations;
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
      source.textContent = citation?.source || messages.unknownSource;

      top.append(label, source);

      const path = document.createElement("div");
      path.className = "citation-path";
      const headingPath = Array.isArray(citation?.headingPath) ? citation.headingPath : [];
      path.textContent = headingPath.length ? headingPath.join(" › ") : messages.noHeading;

      const ordinal = document.createElement("div");
      ordinal.className = "citation-ordinal";
      ordinal.textContent = Number.isInteger(citation?.ordinal)
        ? `${messages.ordinal}: ${citation.ordinal}`
        : `${messages.ordinal}: —`;

      card.append(top, path, ordinal);
      citations.appendChild(card);
    });
  }

  function updateCounter() {
    if (!questionCount) return;
    questionCount.textContent = `${question.value.length} / 700`;
  }

  async function translateToSpanish(question, sourceLang = "en", signal) {
    if (sourceLang === "es") return question;

    const params = new URLSearchParams({
      q: question,
      langpair: `${sourceLang}|es`
    });

    const response = await fetch(
        `https://api.mymemory.translated.net/get?${params}`,
        { signal }
    );

    if (!response.ok) {
      throw new Error("Translation service unavailable");
    }

    const data = await response.json();

    if (data.responseStatus !== 200 ||
        !data.responseData?.translatedText) {
      throw new Error("Translation failed");
    }

    return data.responseData.translatedText;
  }
})();
