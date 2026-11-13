const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const root = path.resolve(__dirname, '..');
const html = fs.readFileSync(path.join(root, 'index.html'), 'utf8');
const tick = () => new Promise(resolve => setImmediate(resolve));

// Minimal DOM simulation: parse the real HTML, including translations and text nodes.
// No network, browser dependencies or live model are needed.
class Node {
  constructor(tag = '', text = '') {
    this.tagName = tag; this.nodeType = tag ? 1 : 3; this._text = text;
    this.attributes = {}; this.childNodes = []; this.handlers = {}; this.value = '';
    this.hidden = false; this.disabled = false; this.open = false; this.validationMessage = '';
    this.classList = { toggle() {} };
  }
  querySelector(selector) {
    const match = n => (n.getAttribute('class') || '').split(' ').includes(selector.slice(1));
    for (const child of this.children) { if (match(child)) return child; const found = child.querySelector(selector); if (found) return found; }
    return null;
  }
  get textContent() { return this.nodeType === 3 ? this._text : this.childNodes.map(n => n.textContent).join(''); }
  set textContent(value) { if (this.nodeType === 3) this._text = String(value); else this.replaceChildren(new Node('', String(value))); }
  get children() { return this.childNodes.filter(n => n.nodeType === 1); }
  contains(node) { return this === node || this.children.some(child => child.contains(node)); }
  get dataset() { return { question: this.getAttribute('data-question') }; }
  setAttribute(key, value) { this.attributes[key] = String(value); if (key === 'value') this.value = value; if (key === 'hidden') this.hidden = true; }
  getAttribute(key) { return this.attributes[key] ?? null; }
  appendChild(node) { this.childNodes.push(node); return node; }
  append(...nodes) { this.childNodes.push(...nodes); }
  replaceChildren(...nodes) { this.childNodes = nodes; }
  addEventListener(event, handler) { this.handlers[event] = handler; }
  setCustomValidity(message) { this.validationMessage = message; }
  reportValidity() { return !this.validationMessage; }
  focus() { this.focused = true; }
  scrollIntoView() {}
}
function makeDocument() {
  const doc = new Node('document'), stack = [doc];
  for (const token of html.match(/<!--[\s\S]*?-->|<![^>]*>|<[^>]+>|[^<]+/g)) {
    if (token.startsWith('<!')) continue;
    if (token.startsWith('</')) { stack.pop(); continue; }
    if (!token.startsWith('<')) { stack.at(-1).appendChild(new Node('', token)); continue; }
    const tag = token.match(/^<([\w-]+)/)[1];
    const node = new Node(tag);
    for (const [, key, value] of token.matchAll(/([\w-]+)="([^"]*)"/g)) node.setAttribute(key, value);
    if (/\shidden(?:\s|>)/.test(token)) node.hidden = true;
    stack.at(-1).appendChild(node);
    if (!['meta', 'link', 'input', 'br', 'hr', 'img'].includes(tag)) stack.push(node);
  }
  const all = () => { const nodes = []; const visit = n => { nodes.push(n); n.children.forEach(visit); }; visit(doc); return nodes; };
  doc.getElementById = id => all().find(n => n.getAttribute('id') === id) || null;
  doc.querySelectorAll = selector => all().filter(n => selector.split(',').some(part => {
    part = part.trim();
    if (part.startsWith('.')) return (n.getAttribute('class') || '').split(' ').includes(part.slice(1));
    if (part.startsWith('[')) return n.getAttribute(part.slice(1, -1)) !== null;
    return n.tagName === part;
  }));
  doc.createElement = tag => new Node(tag);
  doc.documentElement = doc.querySelectorAll('html')[0];
  doc.body = doc.querySelectorAll('body')[0];
  Object.defineProperty(doc, 'title', { get: () => doc.querySelectorAll('title')[0].textContent });
  return doc;
}
const host = { type: 'laptop', hostname: 'PC-ASUS-ZORIN', gpu: 'NVIDIA GTX 1050 · 4 GB', ram: '32 GB', cpu: 'Intel i7-7700HQ' };
function openPage(lang = 'es', storage = new Map(), options = {}) {
  const document = makeDocument();
  const requests = [], navigations = [], clipboard = [], timers = new Map();
  let runtimeCalls = 0, nextTimer = 0;
  const context = { document, URL, URLSearchParams, AbortController, TextEncoder, TextDecoder, Uint8Array, performance,
    navigator: { language: lang, clipboard: { writeText: async text => { if (options.copyFailed) throw Error('Clipboard denied'); clipboard.push(text); } } },
    window: { addEventListener(event, handler) { this.handlers[event] = handler; }, handlers: {}, location: { hash: options.hash || '', search: options.search || '', assign: url => navigations.push(url), replace: url => navigations.push(url) } },
    localStorage: { getItem: key => { if (options.storageFails) throw Error('Storage disabled'); return storage.get(key) ?? null; },
      setItem: (key, value) => { if (options.storageFails || options.writeFails) throw Error('Quota exceeded'); storage.set(key, value); } },
    setTimeout: (fn, delay) => { const id = ++nextTimer; timers.set(id, { fn, delay }); return id; },
    clearTimeout: id => timers.delete(id),
    fetch: async (url, init = {}) => {
      requests.push({ url, init });
      if (url.startsWith('https://api.mymemory.translated.net/get?')) {
        if (options.translation) return options.translation(url, init);
        return { ok: true, json: async () => ({ responseStatus: 200, responseData: { translatedText: '¿Qué reformas?' } }) };
      }
      if (url.endsWith('/answers/stream')) {
        if (options.stream) return options.stream(url, init);
        return { ok: false, status: 404, json: async () => ({}) }; // Legacy API regression coverage.
      }
      if (url.endsWith('/answers')) {
        if (options.answer) return options.answer(url, init);
        if (options.networkFails) throw Error('Failed to fetch');
        if (options.timeout) throw { name: 'AbortError' };
        return { ok: !options.failed, status: options.failed ? 503 : 200, json: async () => {
          if (options.invalidJson) throw SyntaxError('Invalid JSON');
          return options.failed ? { message: 'Backend detail' } : options.payload || { answer: 'Answer [S1]', abstained: false,
            citations: [{ label: '[S1]', source: 'manual.pdf', headingPath: ['Renovation'], ordinal: 3 }] };
        } };
      }
      if (url.endsWith('/warmup')) {
        if (options.warmup) return options.warmup(url, init);
        return { ok: true, status: 202 };
      }
      runtimeCalls++;
      if (options.runtimeFails) throw Error('Runtime unavailable');
      if (options.runtimeHttpStatus) return { ok: false, status: options.runtimeHttpStatus, json: async () => { throw SyntaxError('Not JSON'); } };
      const after = runtimeCalls > 1;
      return { ok: true, json: async () => (options.runtime || { configuredModel: 'qwen3:8b', preferredProvider: { model: 'qwen3:8b' },
        generationState: after && options.failed ? 'failed' : after ? 'success' : 'idle',
        lastUsedProvider: after && !options.failed ? { model: 'qwen2.5:7b', inferenceHost: host } : null,
        modelStatus: options.loadingModel ? 'Cargando modelo' : after && options.failed ? 'Ollama no disponible' : 'Modelo cargado',
        loadedModels: [{ name: 'qwen3:8b', parameterSize: '8B', sizeBytes: 1024, vramBytes: 1024 }], topK: 3, minScore: .63 }) };
    } };
  vm.createContext(context);
  for (const file of ['i18n.js', 'app.js']) vm.runInContext(fs.readFileSync(path.join(__dirname, file), 'utf8'), context, { filename: file });
  // Runtime inspection is now an explicit visitor action; opening the page is offline.
  if (!options.offline) { document.getElementById('runtimeContent').hidden=false; document.getElementById("apiBaseUrl").handlers.change(); }
  const node = id => document.getElementById(id);
  const changeLanguage = value => { node('languageSelect').value = value; node('languageSelect').handlers.change(); };
  return { document, node, storage, requests, navigations, clipboard, timers, changeLanguage,
    window: context.window, runtimeCalls: () => runtimeCalls, t: (...args) => vm.runInContext(`R4RI18n.t(...${JSON.stringify(args)})`, context) };
}

for (const lang of ['es', 'en']) {
  for (const failed of [false, true]) {
    test(`${lang}: runtime refresh after ${failed ? 'failed' : 'successful fallback'} RAG request`, async () => {
      const page = openPage(lang, new Map(), { failed });
      await tick();
      page.node('question').value = 'Question';
      await page.node('sendButton').handlers.click();
      assert.equal(page.runtimeCalls(), 2);
      assert.equal(page.node('runtimeModel'), null);
      if (failed) {
        assert.equal(page.node('runtimeUsed').textContent, page.t('generationFailed'));
        assert.equal(page.node('runtimeHardware'), null);
      } else {
        assert.equal(page.node('runtimeUsed').textContent, 'Qwen2.5 7B');
        assert.equal(page.node('runtimeUsedModel').textContent, 'qwen2.5:7b');
      }
      const body = JSON.parse(page.requests.find(r => r.url.endsWith('/answers')).init.body);
      assert.equal(body.question, 'Question');
      assert.equal(body.responseLanguage, lang);
      assert.equal(body.retrievalQuestion, undefined);
      page.changeLanguage(lang === 'en' ? 'es' : 'en');
      assert.equal(page.node('runtimeUsed').textContent, failed ? page.t('generationFailed') : 'Qwen2.5 7B');
    });
  }
  test(`${lang}: startup, metadata, accessibility and language switch without reload or requests`, async () => {
    const p = openPage(`${lang}-XX`);
    await tick();
    assert.equal(p.document.documentElement.lang, lang);
    assert.equal(p.node('languageSelect').value, lang);
    assert.match(p.document.title, lang === 'es' ? /Asistente de IA/ : /AI assistant/);
    const description = p.document.querySelectorAll('meta').find(n => n.getAttribute('name') === 'description');
    assert.match(description.getAttribute('content'), /Athagon Labs/);
    assert.equal(p.node('question').getAttribute('placeholder'), p.t('page.e_g_what_information_is_needed_for_an_initial_assessment_of_a_renovation_in_an_older_house'));
    assert.equal(p.node('languageSelect').getAttribute('aria-label'), lang === 'es' ? 'Idioma' : 'Language');
    assert.equal(p.node('endpointPreset').children.length, 4);
    assert.equal(p.node('apiBaseUrl').value, 'https://r4r-api.athagon.tech');
    p.node('question').value = 'Unsent draft';
    const before = p.requests.length;
    const other = lang === 'es' ? 'en' : 'es';
    p.changeLanguage(other);
    assert.equal(p.node('question').value, 'Unsent draft');
    assert.equal(p.requests.length, before);
    assert.deepEqual(p.navigations, []);
    assert.equal(p.storage.get('r4r.language'), other);
    const restored = openPage(lang, p.storage);
    assert.equal(restored.document.documentElement.lang, other);
    // Every HTML translation key resolves in both languages, including mixed text nodes.
    for (const language of ['es', 'en']) {
      p.changeLanguage(language);
      for (const attr of ['data-i18n', 'data-i18n-placeholder', 'data-i18n-aria-label', 'data-i18n-content', 'data-i18n-data-question', 'data-i18n-text-0', 'data-i18n-text-1']) {
        for (const n of p.document.querySelectorAll(`[${attr}]`)) assert.ok(p.t(n.getAttribute(attr)));
      }
    }
  });
  test(`${lang}: presets and custom endpoint persist and restore, invalid URLs never fetch`, async () => {
    const p = openPage(lang);
    await tick();
    for (const [preset, url] of [['production', 'https://r4r-api.athagon.tech'], ['synology', 'http://192.168.1.203:18080'], ['local', 'http://127.0.0.1:8080'], ['custom', 'https://example.org/base']]) {
      p.node('endpointPreset').value = preset;
      p.node('endpointPreset').handlers.change();
      if (preset === 'custom') { p.node('apiBaseUrl').value = `${url}/`; p.node('saveEndpoint').handlers.click(); }
      assert.equal(p.storage.get('r4r.apiBaseUrl'), url);
      const restored = openPage(lang, p.storage);
      assert.equal(restored.node('apiBaseUrl').value, url);
      assert.equal(restored.node('endpointPreset').value, preset);
      assert.ok(restored.requests.every(r => r.url.startsWith(url + '/')));
    }
    for (const invalid of ['', 'ftp://example.org', 'file:///tmp/api', 'javascript:alert(1)', 'not a URL']) {
      p.node('apiBaseUrl').value = invalid;
      const before = p.requests.length;
      p.node('saveEndpoint').handlers.click();
      p.node('question').value = 'Question';
      await p.node('sendButton').handlers.click();
      assert.equal(p.requests.length, before);
      assert.equal(p.storage.get('r4r.apiBaseUrl'), 'https://example.org/base');
      assert.ok(p.node('apiBaseUrl').validationMessage);
    }
    p.changeLanguage(lang === 'es' ? 'en' : 'es');
    assert.equal(p.node('apiBaseUrl').validationMessage, p.t('invalidUrl'));
  });
  test(`${lang}: restores last submitted question, retains answer, sources and endpoint on language change`, async () => {
    const p = openPage(lang);
    assert.equal(p.node('question').value, p.t('demoQuestion'));
    assert.equal(p.requests.some(r => r.url.endsWith('/answers')), false);
    p.node('question').value = 'Last question';
    await p.node('sendButton').handlers.click();
    assert.equal(p.storage.get('r4r.lastQuestion'), 'Last question');
    p.node('sourcesDetails').open = true;
    const answer = p.node('answerText').textContent, citations = p.node('citations').textContent;
    const status = p.node('httpStatus').textContent, elapsed = p.node('elapsed').textContent;
    const before = p.requests.length;
    p.changeLanguage(lang === 'es' ? 'en' : 'es');
    assert.equal(p.node('answerText').textContent, answer);
    assert.match(p.node('citations').textContent, /Renovation.*manual.pdf/);
    assert.match(citations, /Renovation.*manual.pdf/);
    assert.equal(p.node('sourcesDetails').open, true);
    assert.equal(p.node('resultContent').hidden, false);
    assert.equal(p.node('httpStatus').textContent, status);
    assert.equal(p.node('elapsed').textContent, elapsed);
    assert.equal(p.node('apiBaseUrl').value, 'https://r4r-api.athagon.tech');
    assert.equal(p.requests.length, before);
    const restored = openPage(lang, p.storage);
    assert.equal(restored.node('question').value, 'Last question');
    assert.equal(restored.node('questionCount').textContent, '13 / 700');
    assert.equal(restored.requests.some(r => r.url.endsWith('/answers')), false);
    p.node('clearButton').handlers.click();
    assert.equal(p.node('question').value, '');
    assert.equal(p.node('emptyState').hidden, false);
  });
  test(`${lang}: quick examples, demo action, keyboard submission and clipboard`, async () => {
    const p = openPage(lang);
    assert.equal(p.document.querySelectorAll('.sample').length, 4);
    assert.equal(p.document.querySelectorAll('.sample')[0].dataset.question, p.t('page.renovation_question'));
    for (const button of p.document.querySelectorAll('.sample')) {
      await button.handlers.click();
      assert.equal(p.node('question').value, button.dataset.question);
    }
    p.changeLanguage(lang === 'es' ? 'en' : 'es');
    const sample = p.document.querySelectorAll('.sample')[0];
    await sample.handlers.click();
    assert.equal(p.node('question').value, sample.dataset.question);
    const beforeDemo = p.requests.length;
    const demo = p.node('tryDemoButton').handlers.click({ preventDefault() {} });
    p.node('question').handlers.input();
    await demo;
    assert.ok(p.requests.length > beforeDemo);
    p.node('question').value = 'Keyboard query';
    await p.node('question').handlers.keydown({ ctrlKey: true, key: 'Enter' });
    assert.equal(p.storage.get('r4r.lastQuestion'), 'Keyboard query');
    await p.node('copyAnswerButton').handlers.click();
    assert.deepEqual(p.clipboard, ['Answer [S1]']);
    p.changeLanguage(lang);
    assert.equal(p.node('copyAnswerButton').textContent, p.t('copied'));
    for (const timer of [...p.timers.values()]) if (timer.delay === 1200) timer.fn();
    assert.equal(p.node('copyAnswerButton').textContent, p.t('copy'));
  });
  for (const kind of ['failed', 'invalidJson', 'networkFails', 'timeout']) {
    test(`${lang}: ${kind} errors and states translate after language switch`, async () => {
      const p = openPage(lang, new Map(), { [kind]: true });
      p.node('question').value = 'Question';
      await p.node('sendButton').handlers.click();
      assert.equal(p.node('errorBox').hidden, false);
      assert.equal(p.node('resultMode').textContent, p.t('failed'));
      p.changeLanguage(lang === 'es' ? 'en' : 'es');
      const expected = kind === 'timeout' ? p.t('timeout') : kind === 'networkFails' ? p.t('networkPublic') : kind === 'invalidJson' ? p.t('invalidJson', 200) : p.t('busyPublic');
      assert.ok(p.node('errorBox').textContent.includes(expected));
      assert.equal(p.node('resultMode').textContent, p.t('failed'));
    });
  }
}

test('navigator fallback, invalid saved values, unavailable storage and legacy preferences', async () => {
  assert.equal(openPage('fr-FR').document.documentElement.lang, 'en');
  const invalid = new Map([['r4r.language', 'invalid'], ['r4r.apiBaseUrl', 'file:///tmp/api']]);
  const p = openPage('es-ES', invalid);
  assert.equal(p.document.documentElement.lang, 'es');
  assert.equal(p.node('apiBaseUrl').value, 'https://r4r-api.athagon.tech');
  for (const options of [{ storageFails: true }, { writeFails: true }]) {
    const page = openPage('en', new Map(), options);
    assert.equal(page.node('apiBaseUrl').value, 'https://r4r-api.athagon.tech');
    assert.equal(page.node('question').value, page.t('demoQuestion'));
    page.node('question').value = 'Query without storage';
    await page.node('sendButton').handlers.click();
    assert.equal(page.node('answerText').textContent, 'Answer [S1]');
    page.changeLanguage('es');
    assert.equal(page.document.documentElement.lang, 'es');
  }
  const legacy = new Map([['r4r-rag-language', 'es'], ['r4r-rag-api-base-url', 'http://127.0.0.1:8080'], ['r4r-rag-last-question', 'Old question']]);
  const migrated = openPage('en', legacy);
  assert.equal(migrated.document.documentElement.lang, 'es');
  assert.equal(migrated.node('question').value, 'Old question');
  assert.equal(legacy.get('r4r.apiBaseUrl'), 'http://127.0.0.1:8080');
  assert.equal(legacy.get('r4r.lastQuestion'), 'Old question');
  assert.equal(migrated.requests.some(r => r.url.endsWith('/answers')), false);
});

test('switching language during an in-flight request retains query language and loading state', async () => {
  let resolveAnswer;
  const p = openPage('en', new Map(), { answer: () => new Promise(resolve => { resolveAnswer = resolve; }) });
  p.node('question').value = 'English question';
  const pending = p.node('sendButton').handlers.click();
  await tick();
  assert.equal(p.node('sendButton').disabled, true);
  p.changeLanguage('es');
  assert.equal(p.node('sendLabel').textContent, 'Consultando…');
  assert.equal(p.node('resultMode').textContent, p.t('inProgress'));
  assert.equal(p.node('loadingState').hidden, false);
  assert.equal(p.node('question').value, 'English question');
  assert.equal(JSON.parse(p.requests.find(r => r.url.endsWith('/answers')).init.body).responseLanguage, 'en');
  resolveAnswer({ ok: true, status: 200, json: async () => ({ answer: 'English answer', citations: [] }) });
  await pending;
  assert.equal(p.node('answerText').textContent, 'English answer');
  assert.equal(p.node('resultMode').textContent, p.t('result'));
  assert.equal(p.node('sendLabel').textContent, 'Obtener respuesta');
});

test('runtime errors, polling and clipboard failure are localized without additional language requests', async () => {
  const p = openPage('es', new Map(), { runtimeFails: true, copyFailed: true });
  await tick();
  assert.ok(p.node('runtimeUsed').textContent.includes(p.t('networkPublic')));
  p.changeLanguage('en');
  assert.ok(p.node('runtimeUsed').textContent.includes(p.t('networkPublic')));
  assert.equal(p.node('runtimeModelStatus').textContent, p.t('informationUnavailable'));
  p.node('question').value = 'Query';
  await p.node('sendButton').handlers.click();
  await p.node('copyAnswerButton').handlers.click();
  assert.equal(p.node('copyAnswerButton').textContent, 'error');
  for (const timer of p.timers.values()) if (timer.delay === 1200) timer.fn();
  assert.equal(p.node('copyAnswerButton').textContent, 'Copy');
  const polling = openPage('en', new Map(), { loadingModel: true });
  await tick();
  assert.equal(polling.node('runtimeModelStatus').textContent, 'Loading model');
  const poll = [...polling.timers.values()].find(timer => timer.delay === 5000);
  assert.ok(poll);
  const before = polling.runtimeCalls();
  polling.changeLanguage('es');
  assert.equal(polling.runtimeCalls(), before);
  assert.equal(polling.node('runtimeModelStatus').textContent, 'Cargando modelo');
  await poll.fn();
  assert.equal(polling.runtimeCalls(), before + 1);
});

test('abstention, empty answers and citation fallback labels translate while source content is preserved', async () => {
  for (const payload of [
    { abstained: true, citations: [] }, { answer: '', citations: [] },
    { answer: 'Preserved', citations: [{}] }
  ]) {
    const p = openPage('es', new Map(), { payload });
    p.node('question').value = 'Query';
    await p.node('sendButton').handlers.click();
    p.changeLanguage('en');
    assert.equal(p.node('decisionPill').textContent, p.t(payload.abstained ? 'abstention' : 'answer'));
    assert.equal(p.node('answerText').textContent, payload.answer || p.t(payload.abstained ? 'abstained' : 'emptyAnswer'));
    assert.ok(p.node('citations').textContent.includes(p.t(payload.citations.length ? 'unknownSource' : payload.abstained ? 'noCitationsAbstained' : 'noCitations')));
  }
});

test('English delegates retrieval translation to the server without third-party requests', async () => {
 const p = openPage('en');p.node('question').value='Original question';
 await p.node('sendButton').handlers.click();
 assert.deepEqual(JSON.parse(p.requests.find(r=>r.url.endsWith('/answers')).init.body),{question:'Original question',responseLanguage:'en'});
 assert.ok(!p.requests.some(r=>r.url.includes('mymemory')));
});

test('single entry point has valid assets, anchors, unique IDs and no legacy file references', () => {
  const ids = [...html.matchAll(/\bid="([^"]+)"/g)].map(m => m[1]);
  assert.equal(new Set(ids).size, ids.length);
  assert.equal(ids.filter(id => id === 'languageSelect').length, 1);
  for (const [, asset] of html.matchAll(/(?:src|href)="([^"#]+)"/g)) {
    if (/^https?:/.test(asset)) continue;
    assert.ok(fs.existsSync(path.join(root, asset.split("?")[0])), `Missing asset: ${asset}`);
  }
  for (const [, anchor] of html.matchAll(/href="#([^"]+)"/g)) assert.ok(ids.includes(anchor));
  assert.ok(html.indexOf('js/i18n.js') < html.indexOf('js/app.js'));
  const scripts = ['app.js', 'i18n.js'].map(file => fs.readFileSync(path.join(__dirname, file), 'utf8')).join('\n');
  assert.doesNotMatch(html + scripts, /index_(?:es|en)\.html/);
  assert.doesNotMatch(html, /(?:src|href)="app\.js"/);
  for (const obsolete of ['index_es.html', 'index_en.html', 'app.js', 'app.test.cjs']) {
    assert.equal(fs.existsSync(path.join(root, obsolete)), false, `Obsolete file: ${obsolete}`);
  }
  assert.doesNotMatch(scripts, /location\.(?:assign|replace|reload)/);
});

test('both languages keep the single shared stylesheet after language changes', () => {
  for (const language of ['es', 'en']) {
    const p = openPage(language);
    for (const next of ['es', 'en']) {
      p.changeLanguage(next);
      const sheets = p.document.querySelectorAll('link').filter(n => n.getAttribute('rel') === 'stylesheet');
      assert.equal(sheets.length, 1);
      assert.match(sheets[0].getAttribute('href'), /^styles\/styles\.css\?v=[a-f0-9]+$/);
      assert.ok(fs.existsSync(path.join(root, sheets[0].getAttribute('href').split('?')[0])));
    }
  }
  for (const obsolete of ['styles.css', 'styles-es.css', 'styles-en.css']) {
    assert.equal(fs.existsSync(path.join(root, obsolete)), false);
  }
});

for (const lang of ['es', 'en']) {
  for (const json of [true, false]) {
    test(`${lang}: HTTP 403 ${json ? 'JSON' : 'plain text'} is not a connection or JSON error`, async () => {
      const p = openPage(lang, new Map(), {
        runtimeHttpStatus: 403,
        answer: async () => ({ ok: false, status: 403, json: async () => {
          if (!json) throw SyntaxError('Invalid CORS request');
          return { error: 'origin_not_allowed', message: 'Origin rejected' };
        } })
      });
      await tick();
      assert.equal(p.node('runtimeUsed').textContent, p.t('forbidden'));
      p.node('question').value = 'Question';
      await p.node('sendButton').handlers.click();
      assert.equal(p.node('httpStatus').textContent, '403');
      assert.ok(p.node('errorBox').textContent.startsWith(p.t('forbidden')));
      assert.ok(!p.node('errorBox').textContent.includes(p.t('networkPublic')));
      assert.ok(!p.node('errorBox').textContent.includes(p.t('invalidJson', 403)));
      p.changeLanguage(lang === 'es' ? 'en' : 'es');
      assert.equal(p.node('runtimeUsed').textContent, p.t('forbidden'));
      assert.ok(p.node('errorBox').textContent.startsWith(p.t('forbidden')));
    });
  }
  test(`${lang}: unreadable response or browser CORS rejection does not claim an HTTP 403`, async () => {
    const p = openPage(lang, new Map(), { networkFails: true, runtimeFails: true });
    p.node('question').value = 'Question';
    await p.node('sendButton').handlers.click();
    assert.ok(p.node('errorBox').textContent.includes(p.t('networkPublic')));
    assert.ok(!p.node('errorBox').textContent.includes(p.t('cors')));
    assert.ok(!p.node('errorBox').textContent.includes('403'));
    assert.ok(!p.node('runtimeUsed').textContent.includes('403'));
  });
}

for (const lang of ['es', 'en']) {
  test(`${lang}: title, static topology and runtime distinguish configuration from observations`, async () => {
    const p = openPage(lang, new Map(), { runtime: {
      configuredModel: 'qwen3:8b', preferredProvider: { name: 'PC', model: 'qwen3:8b', inferenceHost: { type: 'desktop', hostname: 'PC-GIGA-ZORUX' } },
      lastUsedProvider: { name: 'ASUS', model: 'qwen2.5:7b', inferenceHost: host }, generationState: 'success',
      modelStatus: 'Modelo cargado', loadedModels: [{ name: 'qwen2.5:7b', parameterSize: '7.6B' }], topK: 3, minScore: .63
    } });
    await tick();
    assert.equal(p.document.querySelectorAll('h1')[0].textContent, lang === 'es' ? 'Asistente de IA para reformas' : 'Your AI assistant for renovations');
    assert.equal(p.document.querySelectorAll('.arch-step').length, 6);
    assert.equal(p.document.querySelectorAll('.inference-host').length, 3);
    assert.equal(p.document.querySelectorAll('.primary-host')[0].textContent.includes('NVIDIA GeForce RTX 4060'), true);
    assert.equal(p.node('runtimeModel'), null);
    assert.equal(p.node('runtimePreferred').textContent, `Ollama · ${p.t('hostNames').desktop}`);
    const desktop = openPage(lang, new Map(), { runtime: {
      generationState: 'success', embeddingDimensions: 768,
      lastUsedProvider: { model: 'qwen3:8b', inferenceHost: { type: 'desktop', cpu: 'Intel i9-14900KF', ram: '64 GB DDR5' } },
      javaVersion: '22', springBootVersion: '3.4.5', springAiVersion: '1.0.0',
      loadedModels: [{ name: 'qwen3:8b', parameterSize: '8B' }]
    } });
    await tick();
    assert.equal(desktop.node('runtimeUsed').textContent, lang === 'es' ? 'Qwen3 8B · 8.000 millones de parámetros' : 'Qwen3 8B · 8 billion parameters');
    assert.equal(desktop.node('runtimeUsedHardware').textContent, 'PC · Intel Core i9 · 64 GB RAM');
    assert.equal(desktop.node('runtimeVersions').textContent, 'Java 22 · Spring Boot 3.4.5 · Spring AI 1.0.0');
    assert.equal(desktop.document.querySelectorAll('.runtime-summary')[0].children.length, 2);
    assert.equal(desktop.document.querySelectorAll('.config-grid')[0].children.length, 11);
    assert.equal(desktop.node('runtimeUsedModel').textContent, 'qwen3:8b');
    assert.equal(desktop.node('runtimeEmbeddings').textContent, `768 ${desktop.t('dimensions')}`);
    for (const id of ['runtimeHardware', 'runtimeHost', 'runtimeDeployment']) assert.equal(desktop.node(id), null);

    assert.equal(p.node('runtimeUsed').textContent, `Qwen2.5 7B · ${p.document.documentElement.lang === 'es' ? '7.600' : '7.6'} ${p.t('parameters')}`);
    assert.equal(p.node('runtimeUsedHardware').textContent, `${p.t('hostNames').laptop} · Intel Core i7 · 32 GB RAM`);
    assert.equal(p.node('runtimeHost'), null);
    assert.equal(p.node('runtimeUsedModel').textContent, 'qwen2.5:7b');
    assert.doesNotMatch(p.document.body.textContent, /PC-GIGA-ZORUX|PC-ASUS-ZORIN/);
    assert.equal(p.node('runtimeFailover').textContent, p.t('fallbackObserved'));
    assert.equal(p.node('runtimeMemoryScope').textContent, p.t('memoryLastUsed'));
    p.changeLanguage(lang === 'es' ? 'en' : 'es');
    assert.equal(p.node('runtimeFailover').textContent, p.t('fallbackObserved'));
    assert.equal(p.node('runtimeMemoryScope').textContent, p.t('memoryLastUsed'));
    assert.equal(p.node('runtimeUsed').textContent, `Qwen2.5 7B · ${p.document.documentElement.lang === 'es' ? '7.600' : '7.6'} ${p.t('parameters')}`);
  });
  test(`${lang}: bytes become GiB; unavailable values are not presented as zero`, async () => {
    for (const [bytes, expected] of [[1024 ** 3, '1.00 GiB'], [4 * 1024 ** 3, '4.00 GiB'], [5295200000, '4.93 GiB'], [0, '0.00 GiB'], [null, null], [undefined, null], [-1, null], ['4294967296', null], [NaN, null], [Infinity, null]]) {
      const p = openPage(lang, new Map(), { runtime: {
        loadedModels: [{ name: 'test-model', parameterSize: '8B', sizeBytes: bytes, vramBytes: bytes }],
        generationState: 'idle', modelStatus: 'Modelo cargado'
      } });
      await tick();
      assert.ok(p.node('runtimeOllama').textContent.includes(expected || p.t('unavailable')));
      assert.doesNotMatch(p.node('runtimeOllama').textContent, /TB|NaN|Infinity/);
      if (expected === null) assert.doesNotMatch(p.node('runtimeOllama').textContent, /0.00 GiB/);
    }
  });
}

test('runtime never invents failover, a last-used host or a configured model from incomplete data', async () => {
  for (const [runtime, status] of [
    [{ generationState: 'idle', preferredProvider: { name: 'PC', model: 'preferred' } }, 'noGeneration'],
    [{ generationState: 'failed', lastUsedProvider: { name: 'stale', model: 'stale', inferenceHost: host } }, 'generationFailed'],
    [{ generationState: 'success', lastUsedProvider: { model: 'used' }, preferredProvider: { model: 'different' } }, 'fallbackUnknown'],
    [{ generationState: 'success', lastUsedProvider: { name: 'PC', model: 'same' }, preferredProvider: { name: 'PC', model: 'same' } }, 'preferredObserved'],
    [{ generationState: 'success', lastUsedProvider: { model: 'same', inferenceHost: { hostname: 'backup' } }, preferredProvider: { model: 'same', inferenceHost: { hostname: 'main' } } }, 'fallbackObserved']
  ]) {
    const p = openPage('en', new Map(), { runtime });
    await tick();
    assert.equal(p.node('runtimeFailover').textContent, p.t(status));
    if (!runtime.lastUsedProvider?.inferenceHost?.hostname || runtime.generationState === 'failed') {
      assert.equal(p.node('runtimeHost'), null);
    }
    if (runtime.generationState === 'failed') {
      assert.equal(p.node('runtimeUsed').textContent, p.t('generationFailed'));
      assert.equal(p.node('runtimeHardware'), null);
    }
  }
});

for (const lang of ['es', 'en']) {
  test(`${lang}: all sections start closed and controls retain aria-expanded`, () => {
    const p = openPage(lang, new Map(), {offline:true});
    const before = p.requests.length;
    for (const [buttonId, panelId] of [['architectureToggle', 'architectureContent'], ['runtimeToggle', 'runtimeContent'], ['conceptToggle', 'conceptContent']]) {
      const button = p.node(buttonId), panel = p.node(panelId);
      assert.equal(button.tagName, 'button'); // Native Enter/Space activation; exercised in browser QA.
      assert.equal(button.getAttribute('type'), 'button');
      assert.equal(button.getAttribute('aria-controls'), panelId);
      assert.equal(panel.hidden, true);
      assert.equal(button.getAttribute('aria-expanded'), 'false');
      button.handlers.click();
      assert.equal(panel.hidden, false);
      assert.equal(button.getAttribute('aria-expanded'), 'true');
      p.changeLanguage(lang === 'es' ? 'en' : 'es');
      assert.equal(panel.hidden, false);
      assert.equal(button.getAttribute('aria-expanded'), 'true');
      button.handlers.click();
      assert.equal(panel.hidden, true);
      assert.equal(button.getAttribute('aria-expanded'), 'false');
    }
    assert.equal(p.requests.length, before);
  });
  test(`${lang}: navigation opens architecture without preventing the original anchor behavior`, () => {
    const p = openPage(lang, new Map(), {offline:true});
    const link = p.document.querySelectorAll('a').find(n => n.getAttribute('href') === '#arquitectura');
    link.handlers.click();
    assert.equal(link.getAttribute('href'), '#arquitectura');
    assert.equal(p.node('architectureContent').hidden, false);
    assert.equal(p.node('runtimeContent').hidden, true);
    p.node('architectureToggle').handlers.click();
    link.handlers.click();
    assert.equal(p.node('architectureToggle').getAttribute('aria-expanded'), 'true');
    p.window.location.hash = '#runtime';
    p.window.handlers.hashchange();
    assert.equal(p.node('runtimeToggle').getAttribute('aria-expanded'), 'true');
  });
}

test('initial deep links open only their corresponding disclosure', () => {
  for (const hash of ['#arquitectura', '#architectureContent', '#runtime', '#runtimeUsedModel']) {
    const p = openPage('en', new Map(), { hash, offline:true });
    const architecture = hash === '#arquitectura' || hash === '#architectureContent';
    assert.equal(p.node('architectureContent').hidden, !architecture);
    assert.equal(p.node('runtimeContent').hidden, architecture);
  }
  assert.doesNotThrow(() => openPage('en', new Map(), { offline:true, hash: '#%ZZ' }));
});

test('script URLs change with their content so browsers cannot reuse outdated disclosure handlers', () => {
  const { createHash } = require('node:crypto');
  for (const name of ['app.js', 'i18n.js']) {
    const digest = createHash('sha256').update(fs.readFileSync(path.join(__dirname, name))).digest('hex').slice(0, 12);
    assert.ok(html.includes(`src="js/${name}?v=${digest}"`));
  }
});

for (const lang of ['es', 'en']) {
  test(`${lang}: what it demonstrates opens from menu and direct links without affecting other panels`, () => {
    const p = openPage(lang, new Map(), {offline:true});
    const link = p.document.querySelectorAll('a').find(n => n.getAttribute('href') === '#como-funciona');
    link.handlers.click();
    assert.equal(p.node('conceptContent').hidden, false);
    assert.equal(p.node('conceptToggle').getAttribute('aria-expanded'), 'true');
    assert.equal(p.node('architectureContent').hidden, true);
    assert.equal(p.node('runtimeContent').hidden, true);
    p.node('conceptToggle').handlers.click();
    assert.equal(p.node('conceptContent').hidden, true);
    for (const hash of ['#como-funciona', '#conceptContent']) {
      const direct = openPage(lang, new Map(), { hash });
      assert.equal(direct.node('conceptContent').hidden, false);
      assert.equal(direct.node('conceptToggle').getAttribute('aria-expanded'), 'true');
    }
  });
}

for (const lang of ['es', 'en']) {
  test(`${lang}: concise overview separates reported answer from configuration and hides advanced metrics`, async () => {
    const p = openPage(lang);
    await tick();
    assert.equal(p.node('runtimeUsed').textContent, p.t('noGeneration'));
    assert.equal(p.node('runtimeFailover').hidden, true);
    assert.equal(p.document.querySelectorAll('.runtime-grid').length, 0);
    assert.equal(p.document.querySelectorAll('.runtime-summary').length, 1);
    assert.equal(p.document.querySelectorAll('.config-details')[0].open, false);
    assert.equal(p.node('runtimeModelFull'), null);
    const role = p.document.querySelectorAll('.runtime-summary')[0];
    assert.ok(p.document.querySelectorAll('.config-details')[2].contains(role));
    assert.ok(html.indexOf('id="demo"') < html.indexOf('id="como-funciona"'));
    assert.ok(html.indexOf('id="como-funciona"') < html.indexOf('id="arquitectura"'));
    assert.ok(html.indexOf('id="arquitectura"') < html.indexOf('id="runtime"'));
    assert.ok(p.document.querySelectorAll('.config-grid')[0].textContent.includes('Synology'));
    assert.ok(!role.textContent.includes('GiB'));
    assert.ok(!role.textContent.includes('VRAM'));
    p.node('question').value = 'Question';
    await p.node('sendButton').handlers.click();
    assert.match(p.node('runtimeUsed').textContent, /Qwen2.5 7B/);
    assert.equal(p.node('runtimeModel'), null);
    p.changeLanguage(lang === 'es' ? 'en' : 'es');
    assert.match(p.node('runtimeUsed').textContent, /Qwen2.5 7B/);
    assert.equal(p.document.querySelectorAll('.config-details')[0].open, false);
  });
  test(`${lang}: failed generation never claims memory or provider availability`, async () => {
    const p = openPage(lang, new Map(), { runtime: {
      generationState: 'failed', configuredModel: 'qwen3:8b', embeddingModel: 'nomic-embed-text:latest',
      modelStatus: 'Modelo cargado', lastUsedProvider: { model: 'stale' }, loadedModels: []
    } });
    await tick();
    assert.equal(p.node('runtimeUsed').textContent, p.t('generationFailed'));
    assert.equal(p.node('runtimeFailover').hidden, true);
    assert.equal(p.node('runtimeModelStatus').textContent, p.t('informationUnavailable'));
    assert.equal(p.node('runtimeEmbeddingModel').textContent, 'nomic-embed-text:latest');
    assert.equal(p.node('runtimeModel'), null);
  });
}

for (const language of ['es', 'en']) test(`${language}: remote model never displays local hardware`, async () => {
 const runtime = { generationState: 'success',
  lastUsedProvider: { name: 'OpenAI', model: 'gpt-6-luna', inferenceHost: { type: 'cloud' } },
  remoteFallback: { enabled: false, model: 'gpt-6-luna' }, loadedModels: [] };
 const p = openPage(language, new Map(), { runtime });
 await tick();
 assert.equal(p.node('runtimeUsed').textContent, `GPT-6 Luna · ${p.t('cloud')}`);
 assert.equal(p.node('runtimeUsedHardware').hidden,true);
 for (const card of p.document.querySelectorAll('[data-local-provider]')) assert.equal(card.getAttribute('data-unavailable'),'true');
 assert.equal(p.node('localFallbackStatus').hidden,false);
 assert.ok(!html.includes('page.development_agents_details'));
 assert.ok(html.includes('Linux · Docker · Docker Compose'));

 assert.equal(p.node('runtimeOllama').textContent,p.t('remoteNoMemory'));
 assert.ok(p.node('runtimeRemote').textContent.includes(p.t('remoteDisabled')));
 p.changeLanguage(language === 'es' ? 'en' : 'es');
 assert.equal(p.node('localFallbackStatus').textContent, p.t('localFallbackStatus'));
 runtime.lastUsedProvider = { name: 'PC principal', model: 'qwen3:8b', inferenceHost: { type: 'desktop' } };
 p.node('apiBaseUrl').handlers.change();
 await tick();
 assert.equal(p.node('localFallbackStatus').hidden, true);
 for (const card of p.document.querySelectorAll('[data-local-provider]')) assert.equal(card.getAttribute('data-unavailable'), 'false');
 for (const label of p.document.querySelectorAll('.local-provider-status')) assert.equal(label.hidden, true);

});

for (const lang of ['es', 'en']) test(`${lang}: page requests one optional warmup and shows independent observed model states`, async () => {
  const runtime = { generationState: 'idle', models: [
    { id: 'desktop', model: 'qwen3:8b', state: 'unreachable' },
    { id: 'laptop', model: 'qwen2.5:7b', state: 'loading' },
    { id: 'embeddings', model: 'nomic-embed-text:latest', state: 'loaded' }
  ] };
  const p = openPage(lang, new Map(), { runtime });
  await tick();
  assert.equal(p.requests.filter(r => r.url.endsWith('/warmup')).length, 1);
  assert.equal(p.requests.find(r => r.url.endsWith('/warmup')).init.method, 'POST');
  assert.equal(p.requests.filter(r => r.url.endsWith('/answers')).length, 0);
  for (const model of runtime.models) assert.ok(p.node('modelReadiness').textContent.includes(p.t('localModelStates')[model.state]));
  const cards = p.document.querySelectorAll('[data-local-provider]');
  for (const card of cards) assert.equal(card.getAttribute('data-unavailable'), String(card.getAttribute('data-local-provider') === 'desktop'));
  p.changeLanguage(lang === 'en' ? 'es' : 'en');
  assert.ok(p.node('modelReadiness').textContent.includes(p.t('localModelStates').loading));
  p.node('apiBaseUrl').handlers.change();
  await tick();
  assert.equal(p.requests.filter(r => r.url.endsWith('/warmup')).length, 1);
  const poll = [...p.timers.values()].find(t => t.delay === 5000);
  assert.ok(poll);
  runtime.models.forEach(model => { model.state = 'loaded'; });
  await poll.fn();
  assert.equal(p.requests.filter(r => r.url.endsWith('/warmup')).length, 1);
  for (const card of cards) assert.equal(card.getAttribute('data-unavailable'), 'false');
  assert.ok(p.node('modelReadiness').textContent.includes(p.t('localModelStates').loaded));
});

test('warmup failure never blocks RAG and changing endpoint does not send old questions', async () => {
  const p = openPage('es', new Map(), { warmup: async () => { throw Error('Warmup unavailable'); } });
  await tick();
  p.node('endpointPreset').value = 'local';
  p.node('endpointPreset').handlers.change();
  await tick();
  assert.equal(p.requests.filter(r => r.url.endsWith('/warmup')).length, 2);
  assert.equal(p.requests.filter(r => r.url.endsWith('/answers')).length, 0);
  p.node('question').value = 'Consulta';
  await p.node('sendButton').handlers.click();
  assert.equal(p.requests.filter(r => r.url.endsWith('/answers')).length, 1);
  assert.equal(p.node('resultContent').hidden, false);
});

test('warmup timeout is independent from the answer and has no automatic retry', async () => {
  const p = openPage('es', new Map(), { warmup: (url, init) => new Promise((resolve, reject) => {
    init.signal.addEventListener('abort', () => reject({ name: 'AbortError' }));
  }) });
  await tick();
  const timeout = [...p.timers.values()].find(timer => timer.delay === 5000);
  assert.ok(timeout);
  timeout.fn();
  await tick();
  assert.equal(p.requests.filter(r => r.url.endsWith('/warmup')).length, 1);
  p.node('question').value = 'Consulta';
  await p.node('sendButton').handlers.click();
  assert.equal(p.node('resultContent').hidden, false);
  assert.equal(p.requests.filter(r => r.url.endsWith('/warmup')).length, 1);
});

function sseResponse(events, { chunkSize = 11, raw = null } = {}) {
  const text = raw ?? events.map(event => `event: ${event.stage}\r\ndata: ${JSON.stringify(event)}\r\n\r\n`).join('');
  const bytes = new TextEncoder().encode(text);
  return { ok: true, status: 200, headers: { get: () => 'text/event-stream;charset=UTF-8' },
    body: new ReadableStream({ start(controller) {
      for (let offset = 0; offset < bytes.length; offset += chunkSize) controller.enqueue(bytes.slice(offset, offset + chunkSize));
      controller.close();
    } }) };
}
const readyEvent = { stage: 'ANSWER_READY', answer: 'Reforma baños y oficinas. [S1]', abstained: false,
  citations: [{ label: '[S1]', source: 'baño.md', headingPath: ['Reformas'], ordinal: 0 }],
  provider: { name: 'PC principal', model: 'qwen3:8b', inferenceHost: { type: 'desktop' } },
  metrics: { embeddingMs: 15, retrievalIncludingEmbeddingMs: 20, vectorSearchMs: null, generationMs: 30, totalMs: 60 } };

for (const lang of ['es', 'en']) test(`${lang}: SSE renders live phases before answer, then keeps citations and original request fields`, async () => {
  let channel;
  const p = openPage(lang, new Map(), { stream: async (url, init) => {
    assert.equal(init.headers.Accept, 'text/event-stream');
    return { ok: true, status: 200, headers: { get: () => 'text/event-stream' },
      body: new ReadableStream({ start(controller) { channel = controller; } }) };
  } });
  const sendEvent = event => channel.enqueue(new TextEncoder().encode(`event:${event.stage}\ndata:${JSON.stringify(event)}\n\n`));
  p.node('question').value = 'Original question';
  const sending = p.node('sendButton').handlers.click();
  await tick();
  sendEvent({ stage: 'REQUEST_RECEIVED', elapsedMs: 0 });
  sendEvent({ stage: 'EMBEDDING_COMPLETED', durationMs: 15 });
  sendEvent({ stage: 'RETRIEVAL_COMPLETED', documentsFound: 3, durationMs: 20 });
  sendEvent({ stage: 'GENERATION_STARTED', provider: 'PC principal', model: 'qwen3:8b' });
  await tick();
  assert.match(p.node('ragProgress').textContent, /15 ms/);
  assert.match(p.node('ragProgress').textContent, /3/);
  assert.match(p.node('ragProgress').textContent, /Qwen3 8B/);
  assert.equal(p.node('sendButton').disabled, true);
  assert.equal(p.node('resultContent').hidden, true);
  const before = p.requests.length;
  p.changeLanguage(lang === 'es' ? 'en' : 'es');
  assert.equal(p.requests.length, before);
  assert.ok(p.node('ragProgress').textContent.includes(p.t('progressStages').REQUEST_RECEIVED));
  sendEvent({ stage: 'GENERATION_COMPLETED', provider: 'PC principal', model: 'qwen3:8b', durationMs: 30 });
  sendEvent(readyEvent); channel.close();
  await sending;
  assert.equal(p.node('answerText').textContent, readyEvent.answer);
  assert.ok(p.node('citations').textContent.includes('baño.md'));
  assert.equal(p.node('sendButton').disabled, false);
  assert.equal(p.requests.filter(r => r.url.endsWith('/answers')).length, 0);
  const body = JSON.parse(p.requests.find(r => r.url.endsWith('/answers/stream')).init.body);
  assert.equal(body.question, 'Original question');
  assert.equal(body.responseLanguage, lang);
  assert.equal(body.retrievalQuestion, undefined);
});

test('SSE accepts byte-split UTF-8, CRLF, multiline data and comments without inventing phases', async () => {
  const raw = ': keepalive\r\n\r\nevent:ANSWER_READY\r\ndata: ' + JSON.stringify(readyEvent).replace(',"abstained"', ',\r\ndata: "abstained"') + '\r\n\r\n';
  const p = openPage('es', new Map(), { stream: async () => sseResponse([], { raw, chunkSize: 1 }) });
  await p.node('sendButton').handlers.click();
  assert.equal(p.node('answerText').textContent, readyEvent.answer);
  assert.ok(p.node('ragProgress').textContent.includes('ANSWER_READY'));
  assert.equal(p.requests.filter(r => r.url.endsWith('/answers')).length, 0);
});

test('SSE no-evidence answer is an abstention with no simulated generation', async () => {
  const p = openPage('es', new Map(), { stream: async () => sseResponse([
    { stage: 'REQUEST_RECEIVED' }, { stage: 'RETRIEVAL_COMPLETED', documentsFound: 0, durationMs: 12 },
    { stage: 'VALIDATION_COMPLETED', message: 'abstained' }, { stage: 'ANSWER_READY', answer: '', abstained: true, citations: [], provider: null }
  ]) });
  await p.node('sendButton').handlers.click();
  assert.ok(p.node('ragProgress').textContent.includes(p.t('progressNoEvidence')));
  assert.ok(!p.node('ragProgress').textContent.includes('Generando'));
  assert.equal(p.node('decisionPill').textContent, p.t('abstention'));
});

test('SSE reports actual PC failure and laptop failover in both languages', async () => {
  const p = openPage('es', new Map(), { stream: async () => sseResponse([
    { stage: 'PROVIDER_FAILED', provider: 'PC principal', model: 'qwen3:8b' },
    { stage: 'FAILOVER_STARTED', provider: 'Portátil ASUS', model: 'qwen2.5:7b' },
    { stage: 'GENERATION_STARTED', provider: 'Portátil ASUS', model: 'qwen2.5:7b' },
    { ...readyEvent, provider: { model: 'qwen2.5:7b', name: 'Portátil ASUS', inferenceHost: host } }
  ]) });
  await p.node('sendButton').handlers.click();
  assert.ok(p.node('ragProgress').textContent.includes('PC principal no disponible. Probando Portátil ASUS'));
  p.changeLanguage('en');
  assert.ok(p.node('ragProgress').textContent.includes('Main PC unavailable. Trying ASUS laptop'));
});

for (const mode of ['error', 'disconnect', 'invalid-json', 'http503']) test(`SSE ${mode} unblocks the UI and never replays a query via REST`, async () => {
  const p = openPage('es', new Map(), { stream: async () => mode === 'http503'
    ? { ok: false, status: 503, json: async () => ({}) }
    : sseResponse(mode === 'error' ? [{ stage: 'ERROR', message: 'processing_failed' }] : [{ stage: 'REQUEST_RECEIVED' }],
        mode === 'invalid-json' ? { raw: 'data: {invalid}\n\n' } : {}) });
  await p.node('sendButton').handlers.click();
  assert.equal(p.node('sendButton').disabled, false);
  assert.equal(p.node('resultContent').hidden, true);
  assert.equal(p.node('errorBox').hidden, false);
  assert.equal(p.requests.filter(r => r.url.endsWith('/answers/stream')).length, 1);
  assert.equal(p.requests.filter(r => r.url.endsWith('/answers')).length, 0);
});

test('SSE timeout cancels its reader and permits a later query without automatic retry', async () => {
  const p = openPage('es', new Map(), { stream: async (url, init) => ({ ok: true, status: 200,
    headers: { get: () => 'text/event-stream' }, body: new ReadableStream({ start(controller) {
      init.signal.addEventListener('abort', () => controller.error({ name: 'AbortError' }));
    } }) }) });
  const sending = p.node('sendButton').handlers.click();
  await tick();
  [...p.timers.values()].find(t => t.delay === 180000).fn();
  await sending;
  assert.equal(p.node('sendButton').disabled, false);
  assert.ok(p.node('errorBox').textContent.includes(p.t('timeout')));
  assert.equal(p.requests.filter(r => r.url.endsWith('/answers/stream')).length, 1);
});

test('initial page stays offline until an explicit visitor action', async () => {
  const p = openPage('es', new Map(), { offline: true });
  await tick();
  assert.equal(p.requests.length, 0);
});

test('known Markdown sources link to PDFs with the original basename', async () => {
 const p = openPage('es', new Map(), { payload: { answer: 'Evidence [S1]', abstained: false, citations: [{label:'[S1]',source:'faqs/R4R_40_ALL_FAQ.md',headingPath:[],ordinal:1}] }});
 p.node('question').value='Consulta';
 await p.node('sendButton').handlers.click();
 const card = p.node('citations').children[0];
 const source=card.children[0].children[1];
 assert.equal(source.tagName,'a');
 assert.equal(source.getAttribute('href'),'docs/es/faqs/R4R_40_ALL_FAQ.pdf');
 assert.ok(source.textContent.includes('preguntas frecuentes'));assert.ok(source.textContent.includes('Abrir documento'));
});

test('recordings match embedded fixtures and every citation has a published document', () => {
 const fixtures = JSON.parse(fs.readFileSync(path.join(root,'fixtures/recordings.json'),'utf8'));
 const catalogue=JSON.parse(fs.readFileSync(path.join(root,'docs/catalogue.json'),'utf8'));
 const p=openPage('es',new Map(),{offline:true});assert.deepEqual(JSON.parse(p.node('recordedExamples').textContent),fixtures);
 for(const pair of Object.values(fixtures)) for(const lang of ['es','en']) for(const data of [].concat(pair[lang] || [])) {
  assert.ok(data.question);assert.equal(typeof data.abstained,'boolean');
  for(const citation of data.citations) {
   const id=/R4R_\d+/.exec(citation.source)[0];const doc=catalogue[id][lang];
   assert.ok(fs.existsSync(path.join(root,doc.url)));
  }
 }
});
test('endpoint controls require explicit developer mode', () => {
 assert.equal(openPage('es',new Map(),{offline:true}).node('endpointSettings').hidden,true);
 assert.equal(openPage('es',new Map(),{offline:true,search:'?dev=1'}).node('endpointSettings').hidden,false);
});
test('opening runtime explicitly loads telemetry, initial rendering stays offline', async () => {
 const p=openPage('es',new Map(),{offline:true});assert.equal(p.requests.length,0);
 p.node('runtimeToggle').handlers.click();
 await [...p.timers.values()].find(t=>t.delay===0).fn();await tick();
 assert.equal(p.requests.filter(r=>r.url.endsWith('/runtime')).length,1);
});

test('Enter submits, Shift+Enter and IME do not submit', async () => {
 const p=openPage('es',new Map(),{offline:true});p.node('question').value='A question';
 const handler=p.node('question').handlers.keydown;
 handler({key:'Enter',shiftKey:true});handler({key:'Enter',isComposing:true});handler({key:'Enter',keyCode:229});
 assert.equal(p.requests.length,0);let prevented=false;
 await handler({key:'Enter',preventDefault(){prevented=true}});assert(prevented);
 assert.equal(p.requests.filter(r=>r.url.endsWith('/answers/stream')).length,1);
});
test('safe Markdown preserves citations and never creates script or unsafe links', async () => {
 const p=openPage('es',new Map(),{offline:true,payload:{answer:'**Bold** and *italic* [S1]\n\n- One\n- Two\n\n[Safe](https://example.com) [Bad](javascript:alert) <script>bad()</script>',abstained:false,citations:[]}});
 p.node('question').value='Question';await p.node('sendButton').handlers.click();
 const answer=p.node('answerText');const find=(node,tag)=>node.children.flatMap(child=>[...(child.tagName===tag?[child]:[]),...find(child,tag)]);
 assert.equal(find(answer,'strong')[0].textContent,'Bold');assert.equal(find(answer,'em')[0].textContent,'italic');assert.equal(find(answer,'li').length,2);
 assert.equal(find(answer,'a').length,1);assert.equal(find(answer,'script').length,0);assert.ok(answer.textContent.includes('[S1]'));
});
test('English sources use the verified English counterpart and preserve original trace', async () => {
 const original='es/guides/R4R_80_COCINAS_FONTANERIA_Y_FUGAS.md';
 const p=openPage('en',new Map(),{offline:true,payload:{answer:'Answer [S1]',abstained:false,citations:[{label:'[S1]',source:original,headingPath:['Original heading'],ordinal:48}]}});
 p.node('question').value='Question';await p.node('sendButton').handlers.click();
 const card=p.node('citations').children[0];assert.ok(card.children[0].children[1].getAttribute('href').startsWith('docs/en/'));
 assert.ok(card.textContent.includes(original));assert.ok(card.textContent.includes('Original heading'));
});
test('six supplied kitchen recordings retain exact timings and no invented model or SSE',()=>{
 const fixtures=JSON.parse(fs.readFileSync(path.join(root,'fixtures/recordings.json'),'utf8'));
 assert.deepEqual(fixtures.kitchen.es.map(r=>r.durationMs),[3013,4413,4852]);
 assert.deepEqual(fixtures.kitchen.en.map(r=>r.durationMs),[6427,5727,4722]);
 for(const lang of ['es','en']) for(const r of fixtures.kitchen[lang]) {assert.equal(r.events.length,0);assert.equal(r.model,undefined);assert.equal(r.providerName,undefined);}
});

test('saved answers are exact-match fallback for example buttons only', async () => {
 const p=openPage('es',new Map(),{offline:true,networkFails:true});
 await p.node('tryDemoButton').handlers.click({preventDefault(){}});
 assert.equal(p.node('previewNotice').textContent,p.t('savedResponse'));
 assert.equal(p.node('errorBox').hidden,false);
 assert.equal(p.node('httpStatus').textContent==='200',false);
 const free=openPage('es',new Map(),{offline:true,networkFails:true});free.node('question').value=free.t('demoQuestion');
 await free.node('sendButton').handlers.click();assert.equal(free.node('resultContent').hidden,true);
});
test('valid live abstentions and 429 never become automatic saved answers', async () => {
 const abstain=openPage('es',new Map(),{offline:true,payload:{answer:'Not enough evidence',abstained:true,citations:[]}});
 await abstain.node('tryDemoButton').handlers.click({preventDefault(){}});
 assert.equal(abstain.node('previewNotice').textContent,abstain.t('live'));
 assert.equal(abstain.node('decisionPill').textContent,abstain.t('abstention'));
 const limited=openPage('es',new Map(),{offline:true,stream:async()=>({ok:false,status:429,headers:{get:k=>k==='Retry-After'?'25':null},json:async()=>({})})});
 await limited.node('tryDemoButton').handlers.click({preventDefault(){}});
 assert.equal(limited.node('resultContent').hidden,true);assert.ok(limited.node('errorBox').textContent.includes('25 s'));
});

test('completed SSE history remains inspectable outside the hidden loading block', async()=>{
 const p=openPage('es',new Map(),{offline:true,stream:async()=>sseResponse([{stage:'REQUEST_RECEIVED',elapsedMs:0},{stage:'GENERATION_STARTED',provider:'PC principal',model:'qwen3:8b',elapsedMs:2},{stage:'GENERATION_COMPLETED',provider:'PC principal',model:'qwen3:8b',elapsedMs:3},{stage:'ANSWER_READY',answer:'Done',abstained:false,citations:[]}])});
 p.node('question').value='Question';await p.node('sendButton').handlers.click();
 assert.equal(p.node('loadingState').hidden,true);assert.equal(p.node('progressDetails').hidden,false);
 assert.equal(p.node('loadingState').contains(p.node('progressDetails')),false);
 for(const stage of ['REQUEST_RECEIVED','GENERATION_STARTED','GENERATION_COMPLETED','ANSWER_READY'])assert.ok(p.node('ragProgress').textContent.includes(stage));
});

test('numeric source sections use actual PDF page targets and preserve evidence trace',async()=>{
 const catalogue=JSON.parse(fs.readFileSync(path.join(root,'docs/catalogue.json'),'utf8'));
 const source='es/R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md';
 const p=openPage('en',new Map(),{offline:true,payload:{answer:'Evidence [S1]',abstained:false,citations:[{label:'[S1]',source,headingPath:['6.3. Distinción entre solera y solado'],ordinal:5}]}});
 p.node('question').value='Question';await p.node('sendButton').handlers.click();
 const link=p.node('citations').children[0].children[0].children[1];
 assert.equal(link.getAttribute('href'),catalogue.R4R_10.en.url+'#page='+catalogue.R4R_10.en.sectionPages['section-6-3']);
 assert.ok(link.textContent.includes('PDF'));assert.ok(p.node('citations').textContent.includes(source));
});

test('completed answers reveal sources after the visible press delay',async()=>{
 const p=openPage('es',new Map(),{offline:true});p.node('question').value='A question';await p.node('sendButton').handlers.click();
 assert.equal(p.node('sourcesDetails').open,false);
 const timer=[...p.timers.values()].find(t=>t.delay===280);assert.ok(timer);timer.fn();
 assert.equal(p.node('sourcesDetails').open,true);
});
test('new ES/EN quick question includes suelo without reusing a differently worded recording',()=>{
 for(const lang of ['es','en']) {
  const p=openPage(lang,new Map(),{offline:true});
  const button=p.document.querySelectorAll('.sample')[2];assert.ok(button.dataset.question.includes('suelo'));
  assert.ok(button.dataset.question.includes('solera'));assert.ok(button.dataset.question.includes('solado'));
 }
});

test('the updated three-term example has an exact real saved fallback in both languages',async()=>{
 for(const lang of ['es','en']){
  const p=openPage(lang,new Map(),{offline:true,networkFails:true});const button=p.document.querySelectorAll('.sample')[2];
  const recordings=JSON.parse(p.node('recordedExamples').textContent);const saved=recordings.liveExamples[lang].find(r=>r.question===button.dataset.question);
  assert.ok(saved);await button.handlers.click();assert.equal(p.node('previewNotice').textContent,p.t('savedResponse'));
  assert.ok(p.node('answerText').textContent.includes('suelo'));assert.ok(saved.citations.length>0);
 }
});

test('public progress is a visible numbered history with no raw SSE jargon',async()=>{
 const events=[{stage:'REQUEST_RECEIVED'},{stage:'RETRIEVAL_STARTED'},{stage:'EMBEDDING_STARTED'},{stage:'EMBEDDING_COMPLETED'},{stage:'RETRIEVAL_COMPLETED',documentsFound:2},{stage:'GENERATION_STARTED'},{stage:'GENERATION_COMPLETED'},{stage:'VALIDATION_COMPLETED'},{stage:'ANSWER_READY',answer:'Answer',abstained:false,citations:[]}];
 for(const lang of ['es','en']) {
  const p=openPage(lang,new Map(),{offline:true,stream:async()=>sseResponse(events)});
  p.node('question').value='Question';await p.node('sendButton').handlers.click();
  const list=p.node('simpleProgressSteps');assert.equal(list.hidden,false);assert.equal(list.children.length,5);
  ['preparing','searching','generating','verifying','done'].forEach((key,index)=>{
   assert.ok(list.children[index].textContent.includes(p.t(key)));assert.equal(list.children[index].children[0].textContent,String(index+1));assert.equal(list.children[index].getAttribute('data-state'),'complete');
  });
  assert.ok(!list.textContent.includes('EMBEDDING'));assert.equal(p.node('progressDetails').open,false);
  p.changeLanguage(lang==='es'?'en':'es');assert.ok(list.textContent.includes(p.t('searching')));
 }
});
test('public progress does not invent generation for a live abstention',async()=>{
 const p=openPage('es',new Map(),{offline:true,stream:async()=>sseResponse([{stage:'REQUEST_RECEIVED'},{stage:'RETRIEVAL_COMPLETED',documentsFound:0},{stage:'VALIDATION_COMPLETED'},{stage:'ANSWER_READY',answer:'',abstained:true,citations:[]}])});
 p.node('question').value='Question';await p.node('sendButton').handlers.click();
 assert.equal(p.node('simpleProgressSteps').children.length,4);assert.ok(!p.node('simpleProgressSteps').textContent.includes(p.t('generating')));
});
test('query technical details follow the answer and source disclosure',()=>{
 assert.ok(html.indexOf('id="simpleProgressSteps"')<html.indexOf('id="resultContent"'));
 assert.ok(html.indexOf('id="sourcesDetails"')<html.indexOf('id="progressDetails"'));
});

test('response timing is in the header and successful answers have no duplicate title or explanation',async()=>{
 const p=openPage('es',new Map(),{offline:true});p.node('question').value='Question';await p.node('sendButton').handlers.click();
 assert.equal(p.node('decisionRow').hidden,true);assert.equal(p.node('decisionText').textContent,'');
 assert.equal(p.node('resultContent').contains(p.node('elapsed')),false);
 assert.ok(p.document.querySelectorAll('.panel-head').some(node=>node.contains(p.node('elapsed'))));
 assert.equal(p.document.querySelectorAll('[data-i18n]').filter(node=>node.getAttribute('data-i18n')==='page.answer').length,0);
 assert.equal(p.document.querySelectorAll('[data-i18n]').filter(node=>node.getAttribute('data-i18n')==='page.ctrl_enter_to_submit').length,0);
 assert.ok(!html.includes('data-i18n="page.adaptable"'));
 for(const lang of ['es','en']) {p.changeLanguage(lang);assert.ok(p.node('architectureContent').textContent.includes(p.t('page.how_summary')));}
});

test('technical subsections separate diagram, execution flow and configuration in ES/EN',()=>{
 for(const lang of ['es','en']) {
  const p=openPage(lang,new Map(),{offline:true});const sections=p.document.querySelectorAll('.config-details');
  assert.equal(sections.length,3);assert.equal(sections[0].getAttribute('id'),'engineeringDetails');assert.equal(sections[1].getAttribute('id'),'flowDetails');
  for(const section of sections)assert.equal(section.open,false);
  assert.equal(p.node('engineeringDetails').contains(p.document.querySelectorAll('.architecture-diagram')[0]),true);
  assert.equal(p.node('engineeringDetails').contains(p.document.querySelectorAll('.architecture-route')[0]),false);
  assert.equal(p.node('flowDetails').contains(p.document.querySelectorAll('.architecture-route')[0]),true);
  const backup=p.document.querySelectorAll('.arch-step').find(step=>step.textContent.includes('04b'));
  assert.ok(backup.textContent.includes('GPT-6 Luna'));assert.ok(backup.textContent.includes(p.t('page.remote_topology')));
 }
});
