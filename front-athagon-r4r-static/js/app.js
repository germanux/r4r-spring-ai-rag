(() => {
  "use strict";

  const DEFAULT_API_BASE_URL = "https://r4r-api.athagon.tech";
  const API_ENDPOINTS = {
    production: DEFAULT_API_BASE_URL,
    synology: "http://192.168.1.203:18080",
    local: "http://127.0.0.1:8080"
  };
  const STORAGE_KEYS = {
    language: "r4r.language",
    apiBaseUrl: "r4r.apiBaseUrl",
    lastQuestion: "r4r.lastQuestion"
  };
  const LEGACY_KEYS = { language: "r4r-rag-language", apiBaseUrl: "r4r-rag-api-base-url", lastQuestion: "r4r-rag-last-question" };
  const readPreference = (key) => {
    try {
      const value = localStorage.getItem(STORAGE_KEYS[key]);
      if (value !== null) return value;
      const legacy = localStorage.getItem(LEGACY_KEYS[key]);
      if (legacy !== null) localStorage.setItem(STORAGE_KEYS[key], legacy);
      return legacy;
    } catch { return null; }
  };
  const writePreference = (key, value) => { try { localStorage.setItem(STORAGE_KEYS[key], value); } catch { /* Storage may be disabled. */ } };
  const preferredLanguage = readPreference("language");
  const initialLanguage = ["es", "en"].includes(preferredLanguage) ? preferredLanguage
    : (navigator.language || "en").toLowerCase().startsWith("es") ? "es" : "en";
  const ENDPOINT_PATH = "/api/rag/answers";
  const { t } = R4RI18n;
  let isEnglish = false;

  const $ = (id) => document.getElementById(id);

  const disclosures = [
    { button: $("architectureToggle"), panel: $("architectureContent"), anchor: "arquitectura" },
    { button: $("runtimeToggle"), panel: $("runtimeContent"), anchor: "runtime" },
    { button: $("conceptToggle"), panel: $("conceptContent"), anchor: "como-funciona" }
  ];
  function setExpanded(disclosure, expanded) {
    disclosure.panel.hidden = !expanded;
    disclosure.button.setAttribute("aria-expanded", String(expanded));
  }
  for (const disclosure of disclosures) {
    disclosure.button.addEventListener("click", () => setExpanded(disclosure, disclosure.panel.hidden));
  }
  function revealSection(hash) {
    if (!hash || !hash.startsWith("#")) return;
    let id;
    try { id = decodeURIComponent(hash.slice(1)); } catch { return; }
    const target = $(id);
    for (const disclosure of disclosures) {
      if (id === disclosure.anchor || (target && disclosure.panel.contains(target))) setExpanded(disclosure, true);
    }
  }
  document.querySelectorAll("a").forEach(link => {
    const href = link.getAttribute("href");
    if (href?.startsWith("#")) link.addEventListener("click", () => revealSection(href));
  });
  window.addEventListener("hashchange", () => revealSection(window.location.hash));
  revealSection(window.location.hash);

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

  function validBaseUrl(value) {
    const normalized = normalizeBaseUrl(value);
    try {
      const url = new URL(normalized);
      return /^https?:\/\//i.test(normalized) && ["http:", "https:"].includes(url.protocol)
        && url.hostname && !url.username && !url.password && !url.search && !url.hash ? normalized : null;
    } catch { return null; }
  }

  const endpointPreset = $("endpointPreset");
  const languageSelect = $("languageSelect");
  for (const id of [...Object.keys(API_ENDPOINTS), "custom"]) {
    const option = document.createElement("option");
    option.value = id;
    option.setAttribute("data-i18n", id);
    endpointPreset.appendChild(option);
  }
  $("defaultApiBaseUrl").textContent = DEFAULT_API_BASE_URL;
  function syncPreset() {
    endpointPreset.value = Object.keys(API_ENDPOINTS).find(id => API_ENDPOINTS[id] === normalizeBaseUrl(apiBaseUrl.value)) || "custom";
  }
  function persistEndpoint() {
    const value = validBaseUrl(apiBaseUrl.value);
    apiBaseUrl.setCustomValidity(value ? "" : t("invalidUrl"));
    if (!value) { apiBaseUrl.reportValidity(); return null; }
    writePreference("apiBaseUrl", value);
    apiBaseUrl.value = value;
    syncPreset();
    return value;
  }
  apiBaseUrl.value = validBaseUrl(readPreference("apiBaseUrl")) || DEFAULT_API_BASE_URL;
  syncPreset();
  const savedQuestion = readPreference("lastQuestion");
  question.value = savedQuestion || "";
  let lastPayload = null;
  let progressRows = [];
  let lastFailedProvider = null;
  let activeQueryController = null;
  let lastError = null;
  let runtimeInfo = null;
  let runtimeError = null;
  let phase = "idle";
  let apiStatus = "ready";
  let copyStatus = "copy";
  function changeLanguage(language) {
    isEnglish = language === "en";
    R4RI18n.setLanguage(language);
    languageSelect.value = language;
    writePreference("language", language);
    renderDynamicText();
  }
  function renderDynamicText() {
    apiState.textContent = t(apiStatus);
    sendLabel.textContent = t(sendButton.disabled ? "loading" : "send");
    copyAnswerButton.textContent = t(copyStatus);
    resultMode.textContent = t({ idle: "waiting", loading: "inProgress", success: "result", error: "failed" }[phase]);
    if (phase === "success" && lastPayload) renderResult(lastPayload);
    if (phase === "error" && lastError) renderError(errorMessage(lastError));
    renderProgress();
    if (runtimeInfo) renderRuntimeInfo(runtimeInfo);
    else if (runtimeError) renderRuntimeUnavailable();
    if (apiBaseUrl.validationMessage) apiBaseUrl.setCustomValidity(t("invalidUrl"));
  }
  changeLanguage(initialLanguage);
  if (savedQuestion === null) question.value = t("demoQuestion");
  languageSelect.addEventListener("change", () => {
    if (["es", "en"].includes(languageSelect.value)) {
      const hadPreview = !$("previewNotice").hidden;
      engage();
      if (hadPreview) { question.value = ""; lastPayload = null; phase = "idle"; renderEmpty(); $("previewNotice").hidden = true; }
      changeLanguage(languageSelect.value);
    }
  });
  endpointPreset.addEventListener("change", () => {
    const preset = API_ENDPOINTS[endpointPreset.value];
    if (!preset) { apiBaseUrl.focus(); return; }
    apiBaseUrl.value = preset;
    persistEndpoint();
    requestWarmup();
    loadRuntimeInfo();
  });
  apiBaseUrl.addEventListener("input", () => { apiBaseUrl.setCustomValidity(""); syncPreset(); });
  apiBaseUrl.addEventListener("change", () => { if (persistEndpoint()) { requestWarmup(); loadRuntimeInfo(); } });

  updateCounter();

  const tryDemoButton = $("tryDemoButton");
  let preview = false;
  let previewTimer;
  let previewResolve;
  let previewObserver;
  let previewSeen = false;
  let userEngaged = Boolean(savedQuestion);
  const previewKey = "r4r.preview.seen";
  try { previewSeen = sessionStorage.getItem(previewKey) === "1"; } catch { /* Optional storage. */ }
  const reducedMotion = () => window.matchMedia?.("(prefers-reduced-motion: reduce)").matches;
  function cancelPreview() {
    clearTimeout(previewTimer);
    previewResolve?.(false);
    previewResolve = null;
    if (!preview) return;
    preview = false;
    sendButton.classList.toggle("is-preview-pressed", false);
    setLoading(false);
    phase = "idle";
    lastPayload = null;
    progressRows = [];
    renderProgress();
    renderEmpty();
    $("previewNotice").hidden = true;
  }
  function pausePreview(ms) {
    return new Promise(resolve => {
      previewResolve = resolve;
      previewTimer = setTimeout(() => { previewResolve = null; resolve(preview); }, ms);
    });
  }
  async function runPreview() {
    if (activeQueryController) return;
    cancelPreview();
    preview = true;
    renderEmpty();
    previewSeen = true;
    previewObserver?.disconnect();
    try { sessionStorage.setItem(previewKey, "1"); } catch { /* Optional storage. */ }
    const notice = $("previewNotice");
    notice.hidden = false;
    notice.textContent = isEnglish ? "Scripted preview · no API request" : "Vista previa guionizada · sin conexión a la API";
    question.value = "";
    const text = t("demoQuestion");
    if (reducedMotion()) question.value = text;
    else for (const char of text) {
      question.value += char;
      updateCounter();
      if (!await pausePreview(40)) return;
    }
    updateCounter();
    if (!await pausePreview(400)) return;
    sendButton.classList.toggle("is-preview-pressed", true);
    if (!await pausePreview(220)) return;
    sendButton.classList.toggle("is-preview-pressed", false);
    phase = "loading";
    lastPayload = null;
    progressRows = [];
    renderEmpty();
    emptyState.hidden = true;
    setLoading(true);
    // Keep the real submit control available so it can interrupt the preview.
    sendButton.disabled = false;
    loadingState.hidden = false;
    resultMode.textContent = t("inProgress");
    for (const stage of ["REQUEST_RECEIVED", "EMBEDDING_STARTED", "EMBEDDING_COMPLETED", "RETRIEVAL_STARTED", "RETRIEVAL_COMPLETED", "GENERATION_STARTED", "GENERATION_COMPLETED", "VALIDATION_COMPLETED"]) {
      receiveProgress({ stage, documentsFound: 1 });
      if (!await pausePreview(reducedMotion() ? 80 : 450)) return;
    }
    // Grounded in the knowledge-base description already published above the input.
    // This is an illustrative fixture, not a claimed recording of a live model run.
    const answer = isEnglish
      ? "The four Rs stand for Reformas (renovations), Rehabilitación (rehabilitation), Revestimientos (coverings) and Reparaciones (repairs). [S1]"
      : "Las cuatro R significan Reformas, Rehabilitación, Revestimientos y Reparaciones. [S1]";
    const payload = { answer, abstained: false, citations: [{ label: "[S1]", source: "Riansares 4R", headingPath: [isEnglish ? "Knowledge base description" : "Descripción de la base de conocimiento"], ordinal: 1 }] };
    setLoading(false);
    renderResult({ ...payload, answer: " " });
    answerText.setAttribute("aria-live", "off");
    if (!reducedMotion()) for (let i = 0; i < answer.length; i += 6) {
      answerText.textContent = answer.slice(0, i + 6);
      if (!await pausePreview(35)) return;
    }
    lastPayload = payload;
    phase = "success";
    renderResult(payload);
    resultMode.textContent = t("result");
    preview = false;
  }
  function engage() {
    userEngaged = true;
    previewObserver?.disconnect();
    cancelPreview();
  }
  question.addEventListener("focus", engage);
  question.addEventListener("input", () => { engage(); updateCounter(); });
  if (tryDemoButton) tryDemoButton.addEventListener("click", event => {
    event.preventDefault();
    return runPreview();
  });
  if (typeof IntersectionObserver !== "undefined" && !previewSeen && !userEngaged) {
    previewObserver = new IntersectionObserver(entries => {
      clearTimeout(previewTimer);
      if (entries.some(entry => entry.isIntersecting && entry.intersectionRatio >= 0.5)) {
        previewTimer = setTimeout(() => {
          if (!userEngaged && !previewSeen && document.activeElement !== question) runPreview();
        }, 400);
      }
    }, { threshold: 0.5 });
    window.addEventListener("scroll", () => previewObserver?.observe(question), { once: true, passive: true });
  }
  window.addEventListener("pagehide", () => { cancelPreview(); previewObserver?.disconnect(); clearTimeout(runtimePoll); });

  let runtimePoll;
  let runtimeRequest = 0;
  let runtimePollBudget = 18;
  const warmupAttempts = new Map();
  // Backend activity starts only after an explicit visitor action.
  async function requestWarmup() {
    const baseUrl = validBaseUrl(apiBaseUrl.value);
    const now = performance.now();
    if (!baseUrl || now - (warmupAttempts.get(baseUrl) ?? -Infinity) < 60000) return;
    warmupAttempts.set(baseUrl, now);
    runtimePollBudget = 18;
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 5000);
    try {
      // Optional on older APIs: failures must never prevent asking a question.
      await fetch(`${baseUrl}/api/rag/warmup`, { method: "POST", signal: controller.signal, headers: { Accept: "application/json" } });
    } catch { /* Backend cooldown and concurrency limits are authoritative; no retry. */ }
    finally { clearTimeout(timeout); }
  }
  function pollRuntimeWhileLoading(status) {
    clearTimeout(runtimePoll);
    if (status === "Cargando modelo" && phase !== "loading" && runtimePollBudget-- > 0)
      runtimePoll = setTimeout(async () => { await loadRuntimeInfo(); }, 5000);
  }

  function formatBytes(bytes) {
    if (!Number.isSafeInteger(bytes) || bytes < 0) return t("unavailable");
    return `${(bytes / (1024 ** 3)).toFixed(2)} GiB`;
  }

  function providerIdentity(provider) {
    return t("hostNames")[provider?.inferenceHost?.type] || t("unknownHost");
  }

  function readableModel(model) {
    const match = /^qwen(\d+(?:\.\d+)?):(\d+(?:\.\d+)?)b$/i.exec(model || "");
    return match ? `Qwen${match[1]} ${match[2]}B` : model || t("unavailable");
  }

  function modelSummary(provider, loadedModels) {
    const canonical = name => name?.includes(":") ? name : `${name}:latest`;
    const loaded = Array.isArray(loadedModels) ? loadedModels.find(m => canonical(m.name) === canonical(provider.model)) : null;
    const size = /^(\d+(?:\.\d+)?)\s*([BM])$/i.exec(loaded?.parameterSize || "");
    if (!size) return readableModel(provider.model);
    const millions = Number(size[1]) * (size[2].toUpperCase() === "B" ? 1000 : 1);
    const number = isEnglish ? String(millions / 1000) : String(millions).replace(/\B(?=(\d{3})+(?!\d))/g, ".");
    return `${readableModel(provider.model)} · ${number} ${t("parameters")}`;
  }

  function hardwareSummary(provider) {
    const host = provider.inferenceHost || {};
    const cpu = host.cpu?.replace(/Intel\s+(?:Core\s+)?(i[3579])\b.*$/i, "Intel Core $1");
    const ram = /([\d.]+)\s*GB/i.exec(host.ram || "");
    const parts = [host.type === "desktop" ? "PC" : providerIdentity(provider), cpu, ram ? `${ram[1]} GB RAM` : host.ram];
    return cpu || host.ram ? parts.filter(Boolean).join(" · ") : `${parts[0]} · ${t("unknownHardware")}`;
  }

  async function loadRuntimeInfo() {
    if (!(document.getElementById("runtimeUsedModel"))) return;
    const baseUrl = validBaseUrl(apiBaseUrl.value);
    if (!baseUrl) return;
    const requestId = ++runtimeRequest;
    clearTimeout(runtimePoll);
    try {
      const response = await fetch(`${baseUrl}${ENDPOINT_PATH.replace("/answers", "/runtime")}`, { cache: "no-store", headers: { Accept: "application/json" } });
      if (!response.ok) throw { kind: "http", status: response.status };
      let info;
      try {
        info = await response.json();
      } catch {
        throw { kind: "json", status: response.status };
      }
      if (requestId !== runtimeRequest) return;
      runtimeInfo = info;
      runtimeError = null;
      renderRuntimeInfo(info);
      pollRuntimeWhileLoading(info.models?.some(model => model.refreshing || ["loading", "unknown"].includes(model.state))
        ? "Cargando modelo" : info.models?.length ? "" : info.modelStatus);
    } catch (error) {
      if (requestId !== runtimeRequest) return;
      runtimeInfo = null;
      runtimeError = error;
      renderRuntimeUnavailable();
    }
  }

  function renderModelReadiness(models) {
    const labels = t("localModels");
    const states = t("localModelStates");
    $("modelReadiness").textContent = Array.isArray(models) && models.length
      ? models.map(model => `${labels[model.id] || t("unknownHost")}: ${states[model.state] || states.unknown}`).join(" · ")
      : t("informationUnavailable");
  }

  function renderLocalFallback(remote, models = []) {
    document.querySelectorAll("[data-local-provider]").forEach(card => {
      const model = models.find(model => model.id === card.getAttribute("data-local-provider"));
      const unavailable = model ? model.state === "unreachable" : remote;
      card.classList.toggle("is-unavailable", unavailable);
      card.setAttribute("data-unavailable", String(unavailable));
      const label = card.querySelector(".local-provider-status");
      if (label) {
        label.hidden = !model && !remote;
        label.textContent = model ? t("localModelStates")[model.state] || t("localModelStates").unknown : t("localUnavailable");
      }
    });
    $("localFallbackStatus").textContent = remote ? t("localFallbackStatus") : "";
    $("localFallbackStatus").hidden = !remote;
  }

  function renderRuntimeInfo(info) {
    const unavailable = t("unavailable");
    const failed = !lastPayload?.provider && info.generationState === "failed";
    const used = failed ? null : lastPayload && Object.hasOwn(lastPayload, "provider") ? lastPayload.provider : info.lastUsedProvider;
    const remote = used?.name === "OpenAI" || used?.inferenceHost?.type === "cloud";
    renderLocalFallback(remote, Array.isArray(info.models) ? info.models : []);
    renderModelReadiness(info.models);
    const preferred = info.preferredProvider;
    const sameProvider = used?.inferenceHost?.hostname && preferred?.inferenceHost?.hostname
      ? used.inferenceHost.hostname === preferred.inferenceHost.hostname
      : used?.name && preferred?.name ? used.name === preferred.name : null;
    $("runtimeFailover").textContent = failed ? t("generationFailed") : !used ? t("noGeneration")
      : sameProvider === null ? t("fallbackUnknown") : t(sameProvider ? "preferredObserved" : "fallbackObserved");
    $("runtimeFailover").hidden = !used || sameProvider !== false;
    $("runtimePreferred").textContent = `Ollama · ${providerIdentity(preferred)}`;
    $("runtimeMemoryScope").textContent = failed ? t("informationUnavailable") : used ? t("memoryLastUsed")
      : info.generationState === "idle" && preferred ? t("memoryPreferred") : t("memoryUnknown");
    $("runtimeUsed").textContent = failed ? t("generationFailed")
      : remote ? `${used.model === "gpt-6-luna" ? "GPT-6 Luna" : used.model} · ${t("cloud")}`
      : used ? modelSummary(used, info.loadedModels)
      : t(info.generationState === "idle" ? "noGeneration" : "informationUnavailable");
    $("runtimeUsedHardware").textContent = used ? hardwareSummary(used) : "";
    $("runtimeUsedHardware").hidden = !used || remote;
    if ($("runtimeHardwareNote")) $("runtimeHardwareNote").hidden = !used || remote;
    $("runtimeRemote").textContent = info.remoteFallback ? `${info.remoteFallback.model} · ${t(info.remoteFallback.enabled ? "remoteEnabled" : "remoteDisabled")}` : unavailable;
    $("runtimeUsedModel").textContent = used?.model || unavailable;
    const states = t("modelStates");
    $("runtimeModelStatus").textContent = !failed && states[info.modelStatus] || t("informationUnavailable");
    $("runtimeEmbeddingModel").textContent = info.embeddingModel || unavailable;
    $("runtimeEmbeddings").textContent = `${info.embeddingDimensions || "—"} ${t("dimensions")}`;
    $("runtimeRetrieval").textContent = `Top-K ${info.topK} · ${t("minimumScore")} ${info.minScore}`;
    $("runtimeVersions").textContent = `Java ${info.javaVersion || "—"} · Spring Boot ${info.springBootVersion || "—"} · Spring AI ${info.springAiVersion || "—"}`;
    const loaded = Array.isArray(info.loadedModels) ? info.loadedModels : [];
    $("runtimeMemoryScope").hidden = remote;
    $("runtimeModelStatus").hidden = remote;
    $("runtimeOllama").textContent = remote ? t("remoteNoMemory") : loaded.length
      ? loaded.map((model) => `${model.name} (${model.parameterSize && model.parameterSize !== "unknown" ? model.parameterSize : unavailable}; ${formatBytes(model.sizeBytes)} ${t("inMemory")}; ${formatBytes(model.vramBytes)} VRAM)`).join(" · ")
      : t("noModels");
  }
  function renderRuntimeUnavailable() {
    renderLocalFallback(false);
    renderModelReadiness(null);
    for (const id of ["runtimeEmbeddingModel", "runtimeEmbeddings", "runtimeRetrieval", "runtimeVersions"]) $(id).textContent = t("unavailable");
    $("runtimeUsedHardware").textContent = "";
    $("runtimeUsedHardware").hidden = true;
    if ($("runtimeHardwareNote")) $("runtimeHardwareNote").hidden = true;
    $("runtimeRemote").textContent = t("unavailable");
    $("runtimeUsedModel").textContent = t("unavailable");
    $("runtimeUsed").textContent = errorMessage(runtimeError);
    $("runtimeFailover").textContent = t("informationUnavailable");
    $("runtimeFailover").hidden = true;
    $("runtimePreferred").textContent = t("informationUnavailable");
    $("runtimeMemoryScope").textContent = t("informationUnavailable");
    $("runtimeModelStatus").textContent = t("informationUnavailable");
    $("runtimeOllama").textContent = t("informationUnavailable");
  }
  function errorMessage(error) {
    if (error?.kind === "stream") return t("streamError");
    if (error?.kind === "timeout") return t("timeout");
    if (error?.kind === "http") {
      const message = error.status === 403 ? t("forbidden") : t("httpError", error.status);
      return `${message}${error.detail ? `\n${error.detail}` : ""}`;
    }
    if (error?.kind === "json") return t("invalidJson", error.status);
    // Fetch cannot distinguish a network failure from a browser-blocked CORS response.
    return `${t("networkError")}\n\n${t("cors")}`;
  }

  document.querySelectorAll(".sample").forEach((button) => {
    button.addEventListener("click", async () => {
      engage();
      if (sendButton.disabled) return;
      question.value = button.dataset.question || "";
      updateCounter();
      await sendQuestion();
    });
  });

  saveEndpoint.addEventListener("click", () => {
    if (!persistEndpoint()) return;
    apiStatus = "saved";
    apiState.textContent = t(apiStatus);
    requestWarmup();
    loadRuntimeInfo();
  });

  clearButton.addEventListener("click", () => {
    engage();
    $("previewNotice").hidden = true;
    question.value = "";
    phase = "idle";
    lastPayload = null;
    lastError = null;
    updateCounter();
    renderEmpty();
    question.focus();
  });



  question.addEventListener("keydown", (event) => {
    if ((event.ctrlKey || event.metaKey) && event.key === "Enter") {
      return sendQuestion();
    }
  });

  sendButton.addEventListener("click", sendQuestion);

  if (copyAnswerButton) {
    copyAnswerButton.addEventListener("click", async () => {
      const text = answerText.textContent.trim();
      if (!text) return;
      try {
        await navigator.clipboard.writeText(text);
        copyStatus = "copied";
        copyAnswerButton.textContent = t(copyStatus);
        setTimeout(() => {
          copyStatus = "copy";
          copyAnswerButton.textContent = t(copyStatus);
        }, 1200);
      } catch {
        copyStatus = "error";
        copyAnswerButton.textContent = t(copyStatus);
        setTimeout(() => {
          copyStatus = "copy";
          copyAnswerButton.textContent = t(copyStatus);
        }, 1200);
      }
    });
  }

  window.addEventListener("pagehide", () => activeQueryController?.abort());

  function receiveProgress(event) {
    if (!event || typeof event.stage !== "string") return;
    if (event.stage === "ANSWER_READY") return;
    const known = ["REQUEST_RECEIVED", "TRANSLATION_STARTED", "TRANSLATION_COMPLETED", "TRANSLATION_FALLBACK",
      "EMBEDDING_STARTED", "EMBEDDING_COMPLETED", "RETRIEVAL_STARTED", "RETRIEVAL_COMPLETED", "GENERATION_STARTED",
      "PROVIDER_FAILED", "FAILOVER_STARTED", "GENERATION_COMPLETED", "VALIDATION_COMPLETED", "ERROR"];
    if (!known.includes(event.stage)) return;
    if (event.stage === "PROVIDER_FAILED") lastFailedProvider = event.provider;
    if (event.stage === "FAILOVER_STARTED") event = { ...event, previousProvider: lastFailedProvider };
    const root = ["PROVIDER_FAILED", "FAILOVER_STARTED"].includes(event.stage) ? "GENERATION" : event.stage.replace(/_(STARTED|COMPLETED)$/, "");
    const key = `${root}:${event.stage === "FAILOVER_STARTED" ? event.previousProvider || "" : event.provider || ""}`;
    const row = { key, event, pending: event.stage.endsWith("_STARTED") && event.stage !== "FAILOVER_STARTED", failed: ["ERROR", "PROVIDER_FAILED", "FAILOVER_STARTED", "TRANSLATION_FALLBACK"].includes(event.stage) };
    const index = progressRows.findIndex(row => row.key === key);
    if (index >= 0 && event.stage === "RETRIEVAL_COMPLETED") {
      progressRows.splice(index, 1);
      progressRows.push(row);
    } else if (index >= 0) progressRows[index] = row;
    else progressRows.push(row);
    renderProgress();
  }
  function renderProgress() {
    const list = $("ragProgress");
    if (!list) return;
    const providerName = provider => ({ "PC principal": t("localModels").desktop, "Portátil ASUS": t("localModels").laptop, "OpenAI": "OpenAI" }[provider] || t("unknownHost"));
    list.replaceChildren();
    for (const row of progressRows) {
      const event = row.event;
      const li = document.createElement("li");
      let message = t("progressStages")[event.stage] || "";
      message = message.replace("{model}", event.model === "gpt-6-luna" ? "GPT-6 Luna" : event.model ? readableModel(event.model) : t("progressAI"))
        .replace("{count}", String(event.documentsFound ?? 0)).replace("{provider}", providerName(event.provider))
        .replace("{previous}", providerName(event.previousProvider));
      if (event.stage === "RETRIEVAL_COMPLETED" && event.documentsFound === 0) message = t("progressNoEvidence");
      const duration = Number.isFinite(event.durationMs) && event.durationMs >= 0 ? ` (${Math.round(event.durationMs)} ms)` : "";
      li.textContent = `${row.failed ? "!" : row.pending ? "⟳" : "✓"} ${message}${duration}`;
      list.appendChild(li);
    }
  }

  async function readAnswerStream(response) {
    if (!response.body?.getReader || !response.headers?.get("Content-Type")?.includes("text/event-stream")) throw { kind: "stream" };
    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = "";
    let finalAnswer = null;
    function frame(text) {
      if (text.length > 131072) throw { kind: "stream" };
      const data = text.split("\n").filter(line => line.startsWith("data:")).map(line => line.slice(5).trimStart()).join("\n");
      if (!data) return; // SSE comments/heartbeats do not represent processing stages.
      let event;
      try { event = JSON.parse(data); } catch { throw { kind: "stream" }; }
      receiveProgress(event);
      if (event.stage === "ERROR") throw event.message === "timeout" ? { kind: "timeout" } : { kind: "stream" };
      if (event.stage === "ANSWER_READY") {
        if (typeof event.answer !== "string" || typeof event.abstained !== "boolean" || !Array.isArray(event.citations)) throw { kind: "stream" };
        finalAnswer = event;
      }
    }
    try {
      while (!finalAnswer) {
        const { value, done } = await reader.read();
        buffer += decoder.decode(value || new Uint8Array(), { stream: !done });
        // Normalize CRLF only after a complete frame, retaining split CR/LF across chunks.
        let match;
        while ((match = /\r?\n\r?\n/.exec(buffer))) {
          frame(buffer.slice(0, match.index).replace(/\r\n/g, "\n"));
          buffer = buffer.slice(match.index + match[0].length);
          if (finalAnswer) break;
        }
        if (buffer.length > 131072) throw { kind: "stream" };
        if (done) break;
      }
      if (!finalAnswer) throw { kind: "stream" };
      return finalAnswer;
    } catch (error) {
      throw error?.name === "AbortError" || error?.kind ? error : { kind: "stream" };
    } finally {
      try { await reader.cancel(); } catch { /* Connection already closed. */ }
      reader.releaseLock();
    }
  }

  async function sendQuestion() {
    engage();
    $("previewNotice").hidden = true;
    if (sendButton.disabled) return;

    const text = question.value.trim();
    if (!text) {
      question.focus();
      return;
    }

    const baseUrl = persistEndpoint();
    if (!baseUrl) return;
    writePreference("lastQuestion", text);
    const url = `${baseUrl}${ENDPOINT_PATH}`;

    clearTimeout(runtimePoll);
    ++runtimeRequest;
    const requestLanguage = isEnglish ? "en" : "es";
    phase = "loading";
    progressRows = [];
    lastFailedProvider = null;
    renderProgress();
    lastPayload = null;
    lastError = null;
    apiStatus = "querying";
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
    apiState.textContent = t("querying");
    resultMode.textContent = t("inProgress");
    httpStatus.textContent = "…";
    // Position once, before the request. Never move the reader at completion.
    const panel = document.querySelector(".response-panel");
    const bounds = panel?.getBoundingClientRect?.();
    if (bounds && (bounds.top < 0 || bounds.top > window.innerHeight - 120))
      panel.scrollIntoView({ behavior: "instant", block: "start" });

    const controller = new AbortController();
    activeQueryController = controller;
    const timeout = setTimeout(() => controller.abort(), 180000);
    const started = performance.now();

    try {
      const requestBody = { question: text, responseLanguage: requestLanguage };
      if (requestLanguage === "en") {
        const translationStarted = performance.now();
        receiveProgress({ stage: "TRANSLATION_STARTED" });
        try {
          requestBody.retrievalQuestion = await translateToSpanish(text, controller.signal);
          receiveProgress({ stage: "TRANSLATION_COMPLETED", durationMs: performance.now() - translationStarted });
        } catch (error) {
          // An absent translation activates the backend's local fallback. Never send raw English.
          if (controller.signal.aborted) throw error;
          receiveProgress({ stage: "TRANSLATION_FALLBACK" });
        }
      }
      const init = {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "Accept": "text/event-stream"
        },
        body: JSON.stringify(requestBody),
        signal: controller.signal
      };
      let response = await fetch(`${url}/stream`, init);
      const legacy = [404, 405, 406, 501].includes(response.status);
      if (legacy) {
        // Only a definitively unsupported route permits REST fallback. Never replay a started query.
        try { await response.body?.cancel(); } catch { /* Unsupported endpoint body already closed. */ }
        response = await fetch(url, { ...init, headers: { ...init.headers, Accept: "application/json" } });
      }
      httpStatus.textContent = `${response.status}`;
      let payload;
      if (!response.ok || legacy) {
        try { payload = await response.json(); }
        catch { if (response.ok) throw { kind: "json", status: response.status }; }
        if (!response.ok) throw { kind: "http", status: response.status, detail: payload?.message || payload?.error || "" };
      } else payload = await readAnswerStream(response);
      elapsed.textContent = `${Math.round(performance.now() - started)} ms`;

      phase = "success";
      lastPayload = payload;
      apiStatus = "online";
      renderResult(payload);
      apiState.textContent = t(apiStatus);
      resultMode.textContent = t("result");

    } catch (error) {
      const duration = Math.round(performance.now() - started);
      elapsed.textContent = `${duration} ms`;
      phase = "error";
      apiStatus = "error";
      lastError = error?.name === "AbortError" ? { kind: "timeout" } : error;
      apiState.textContent = t(apiStatus);
      resultMode.textContent = t("failed");

      renderError(errorMessage(lastError));
    } finally {
      clearTimeout(timeout);
      activeQueryController = null;
      setLoading(false);
      await loadRuntimeInfo();
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
    sendLabel.textContent = loading ? t("loading") : t("send");
  }

  function renderEmpty() {
    emptyState.hidden = false;
    resultContent.hidden = true;
    errorBox.hidden = true;
    if (loadingState) loadingState.hidden = true;
    httpStatus.textContent = "—";
    resultMode.textContent = t("waiting");
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

    decisionPill.textContent = abstained ? t("abstention") : t("answer");
    decisionPill.classList.toggle("abstain", abstained);
    decisionText.textContent = abstained
      ? t("noEvidence")
      : t("evidence");

    answerText.textContent = answer || (abstained ? t("abstained") : t("emptyAnswer"));

    citationCount.textContent = t("sources", sourceList.length);
    citations.replaceChildren();

    if (!sourceList.length) {
      const note = document.createElement("div");
      note.className = "citation";
      note.textContent = abstained
        ? t("noCitationsAbstained")
        : t("noCitations");
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
      source.textContent = citation?.source || t("unknownSource");

      top.append(label, source);

      const path = document.createElement("div");
      path.className = "citation-path";
      const headingPath = Array.isArray(citation?.headingPath) ? citation.headingPath : [];
      path.textContent = headingPath.length ? headingPath.join(" › ") : t("noHeading");

      const ordinal = document.createElement("div");
      ordinal.className = "citation-ordinal";
      ordinal.textContent = Number.isInteger(citation?.ordinal)
        ? `${t("ordinal")}: ${citation.ordinal}`
        : `${t("ordinal")}: —`;

      card.append(top, path, ordinal);
      citations.appendChild(card);
    });
  }

  function updateCounter() {
    if (!questionCount) return;
    questionCount.textContent = `${question.value.length} / 700`;
  }

  async function translateToSpanish(text, signal) {
    // MyMemory's anonymous GET API accepts at most 500 UTF-8 bytes per segment.
    // Longer questions use the existing local fallback, without truncation or extra calls.
    if (new TextEncoder().encode(text).length > 500) throw new Error(t("translationError"));
    const translationController = new AbortController();
    const abort = () => translationController.abort();
    signal.addEventListener("abort", abort, { once: true });
    if (signal.aborted) abort();
    const timeout = setTimeout(abort, 5000);
    try {
      const params = new URLSearchParams({ q: text, langpair: "en|es" });
      const response = await fetch(`https://api.mymemory.translated.net/get?${params}`, {
        signal: translationController.signal
      });
      if (!response.ok) throw new Error(t("translationError"));
      const data = await response.json();
      const translated = data.responseData?.translatedText?.trim();
      if (Number(data.responseStatus) !== 200 || data.quotaFinished || !translated
          || translated.length > 2000
          || translated.replace(/\s+/g, " ").toLowerCase() === text.trim().replace(/\s+/g, " ").toLowerCase()) {
        throw new Error(t("translationError"));
      }
      return translated;
    } finally {
      clearTimeout(timeout);
      signal.removeEventListener("abort", abort);
    }
  }
})();
