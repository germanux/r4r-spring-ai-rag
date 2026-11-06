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
    if (runtimeInfo) renderRuntimeInfo(runtimeInfo);
    else if (runtimeError) renderRuntimeUnavailable();
    if (apiBaseUrl.validationMessage) apiBaseUrl.setCustomValidity(t("invalidUrl"));
  }
  changeLanguage(initialLanguage);
  if (savedQuestion === null) question.value = t("demoQuestion");
  languageSelect.addEventListener("change", () => {
    if (["es", "en"].includes(languageSelect.value)) changeLanguage(languageSelect.value);
  });
  endpointPreset.addEventListener("change", () => {
    const preset = API_ENDPOINTS[endpointPreset.value];
    if (!preset) { apiBaseUrl.focus(); return; }
    apiBaseUrl.value = preset;
    persistEndpoint();
    loadRuntimeInfo();
  });
  apiBaseUrl.addEventListener("input", () => { apiBaseUrl.setCustomValidity(""); syncPreset(); });
  apiBaseUrl.addEventListener("change", () => { if (persistEndpoint()) loadRuntimeInfo(); });

  updateCounter();

  const tryDemoButton = $("tryDemoButton");
  if (tryDemoButton) {
    tryDemoButton.addEventListener("click", (event) => {
      event.preventDefault();
      question.value = t("demoQuestion");
      updateCounter();
      document.getElementById("demo").scrollIntoView({ behavior: "smooth", block: "start" });
      return sendQuestion();
    });
  }

  let runtimePoll;
  let runtimeRequest = 0;
  loadRuntimeInfo();
  function pollRuntimeWhileLoading(status) {
    clearTimeout(runtimePoll);
    if (status === "Cargando modelo") runtimePoll = setTimeout(async () => { await loadRuntimeInfo(); }, 5000);
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
      pollRuntimeWhileLoading(info.modelStatus);
    } catch (error) {
      if (requestId !== runtimeRequest) return;
      runtimeInfo = null;
      runtimeError = error;
      renderRuntimeUnavailable();
    }
  }

  function renderRuntimeInfo(info) {
    const unavailable = t("unavailable");
    const failed = info.generationState === "failed";
    const used = failed ? null : info.lastUsedProvider;
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
      : used ? modelSummary(used, info.loadedModels)
      : t(info.generationState === "idle" ? "noGeneration" : "informationUnavailable");
    $("runtimeUsedHardware").textContent = used ? hardwareSummary(used) : "";
    $("runtimeUsedHardware").hidden = !used;
    $("runtimeUsedModel").textContent = used?.model || unavailable;
    const states = t("modelStates");
    $("runtimeModelStatus").textContent = !failed && states[info.modelStatus] || t("informationUnavailable");
    $("runtimeEmbeddingModel").textContent = info.embeddingModel || unavailable;
    $("runtimeEmbeddings").textContent = `${info.embeddingDimensions || "—"} ${t("dimensions")}`;
    $("runtimeRetrieval").textContent = `Top-K ${info.topK} · ${t("minimumScore")} ${info.minScore}`;
    $("runtimeVersions").textContent = `Java ${info.javaVersion || "—"} · Spring Boot ${info.springBootVersion || "—"} · Spring AI ${info.springAiVersion || "—"}`;
    const loaded = Array.isArray(info.loadedModels) ? info.loadedModels : [];
    $("runtimeOllama").textContent = loaded.length
      ? loaded.map((model) => `${model.name} (${model.parameterSize && model.parameterSize !== "unknown" ? model.parameterSize : unavailable}; ${formatBytes(model.sizeBytes)} ${t("inMemory")}; ${formatBytes(model.vramBytes)} VRAM)`).join(" · ")
      : t("noModels");
  }
  function renderRuntimeUnavailable() {
    for (const id of ["runtimeEmbeddingModel", "runtimeEmbeddings", "runtimeRetrieval", "runtimeVersions"]) $(id).textContent = t("unavailable");
    $("runtimeUsedHardware").textContent = "";
    $("runtimeUsedHardware").hidden = true;
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
      if (sendButton.disabled) return;
      question.value = button.dataset.question || "";
      updateCounter();
      question.focus();
      await sendQuestion();
    });
  });

  saveEndpoint.addEventListener("click", () => {
    if (!persistEndpoint()) return;
    apiStatus = "saved";
    apiState.textContent = t(apiStatus);
    loadRuntimeInfo();
  });

  clearButton.addEventListener("click", () => {
    question.value = "";
    phase = "idle";
    lastPayload = null;
    lastError = null;
    updateCounter();
    renderEmpty();
    question.focus();
  });

  question.addEventListener("input", updateCounter);

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

  async function sendQuestion() {
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

    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 120000);
    const started = performance.now();

    try {
      const requestBody = { question: text, responseLanguage: requestLanguage };
      if (requestLanguage === "en") {
        try {
          requestBody.retrievalQuestion = await translateToSpanish(text, controller.signal);
        } catch (error) {
          // An absent translation activates the backend's local fallback. Never send raw English.
          if (controller.signal.aborted) throw error;
        }
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
        if (response.ok) throw { kind: "json", status: response.status };
      }

      if (!response.ok) {
        throw { kind: "http", status: response.status, detail: payload?.message || payload?.error || "" };
      }

      phase = "success";
      lastPayload = payload;
      apiStatus = "online";
      renderResult(payload);
      apiState.textContent = t(apiStatus);
      resultMode.textContent = t("result");
      if (isEnglish) resultContent.scrollIntoView({ behavior: "smooth", block: "start" });
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
