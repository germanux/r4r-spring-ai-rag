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
  const context = { document, URL, URLSearchParams, AbortController, TextEncoder, performance,
    navigator: { language: lang, clipboard: { writeText: async text => { if (options.copyFailed) throw Error('Clipboard denied'); clipboard.push(text); } } },
    window: { addEventListener(event, handler) { this.handlers[event] = handler; }, handlers: {}, location: { hash: options.hash || '', assign: url => navigations.push(url), replace: url => navigations.push(url) } },
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
      assert.equal(page.node('runtimeModel').textContent, 'qwen3:8b');
      if (failed) {
        assert.equal(page.node('runtimeUsed').textContent, page.t('generationFailed'));
        assert.equal(page.node('runtimeHardware').textContent, '');
      } else {
        assert.equal(page.node('runtimeUsed').textContent, `qwen2.5:7b · ${page.t('hostNames').laptop}`);
        assert.match(page.node('runtimeHardware').textContent, /GTX 1050/);
      }
      const body = JSON.parse(page.requests.find(r => r.url.endsWith('/answers')).init.body);
      assert.equal(body.question, 'Question');
      assert.equal(body.responseLanguage, lang);
      assert.equal(body.retrievalQuestion, lang === 'en' ? '¿Qué reformas?' : undefined);
      page.changeLanguage(lang === 'en' ? 'es' : 'en');
      assert.equal(page.node('runtimeUsed').textContent, failed ? page.t('generationFailed') : `qwen2.5:7b · ${page.t('hostNames').laptop}`);
    });
  }
  test(`${lang}: startup, metadata, accessibility and language switch without reload or requests`, async () => {
    const p = openPage(`${lang}-XX`);
    await tick();
    assert.equal(p.document.documentElement.lang, lang);
    assert.equal(p.node('languageSelect').value, lang);
    assert.match(p.document.title, lang === 'es' ? /IA autoalojada/ : /Self-Hosted AI/);
    const description = p.document.querySelectorAll('meta').find(n => n.getAttribute('name') === 'description');
    assert.match(description.getAttribute('content'), /Spring AI/);
    assert.match(p.node('question').getAttribute('placeholder'), lang === 'es' ? /Qué información/ : /What information/);
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
    p.node('question').value = 'Last question';
    await p.node('sendButton').handlers.click();
    assert.equal(p.storage.get('r4r.lastQuestion'), 'Last question');
    p.node('sourcesDetails').open = true;
    const answer = p.node('answerText').textContent, citations = p.node('citations').textContent;
    const status = p.node('httpStatus').textContent, elapsed = p.node('elapsed').textContent;
    const before = p.requests.length;
    p.changeLanguage(lang === 'es' ? 'en' : 'es');
    assert.equal(p.node('answerText').textContent, answer);
    assert.match(p.node('citations').textContent, /manual.pdf.*Renovation/);
    assert.match(citations, /manual.pdf.*Renovation/);
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
    for (const button of p.document.querySelectorAll('.sample')) {
      await button.handlers.click();
      assert.equal(p.node('question').value, button.dataset.question);
    }
    p.changeLanguage(lang === 'es' ? 'en' : 'es');
    const sample = p.document.querySelectorAll('.sample')[0];
    await sample.handlers.click();
    assert.equal(p.node('question').value, sample.dataset.question);
    await p.node('tryDemoButton').handlers.click({ preventDefault() {} });
    assert.equal(p.node('question').value, p.t('demoQuestion'));
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
      const expected = kind === 'timeout' ? p.t('timeout') : kind === 'networkFails' ? p.t('networkError') : kind === 'invalidJson' ? p.t('invalidJson', 200) : p.t('httpError', 503);
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
    assert.equal(page.node('question').value, '');
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
  assert.ok(p.node('runtimeUsed').textContent.includes(p.t('networkError')));
  p.changeLanguage('en');
  assert.ok(p.node('runtimeUsed').textContent.includes(p.t('networkError')));
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

test('English preserves the original question and uses MyMemory Spanish only for retrieval', async () => {
  const p = openPage('en');
  p.node('question').value = 'Original question';
  await p.node('sendButton').handlers.click();
  const body = JSON.parse(p.requests.find(r => r.url.endsWith('/answers')).init.body);
  assert.deepEqual(body, { question: 'Original question', retrievalQuestion: '¿Qué reformas?', responseLanguage: 'en' });
  const translation = p.requests.find(r => r.url.includes('mymemory'));
  assert.equal(new URL(translation.url).searchParams.get('q'), body.question);
  assert.equal(new URL(translation.url).searchParams.get('langpair'), 'en|es');
  assert.equal(p.requests.filter(r => r.url.includes('mymemory')).length, 1);
  assert.equal(p.node('answerText').textContent, 'Answer [S1]');
  assert.equal(p.node('question').value, 'Original question');
  assert.equal(p.storage.get('r4r.lastQuestion'), 'Original question');
  assert.ok(p.node('citations').textContent.includes('manual.pdf'));
});

test('Spanish does not contact a translator', async () => {
  const p = openPage('es');
  p.node('question').value = '¿Qué reformas?';
  await p.node('sendButton').handlers.click();
  assert.ok(p.requests.every(r => !r.url.includes('mymemory')));
  assert.deepEqual(JSON.parse(p.requests.find(r => r.url.endsWith('/answers')).init.body),
    { question: '¿Qué reformas?', responseLanguage: 'es' });
});

for (const failure of ['network', 'http', 'status', 'quota', 'json', 'empty', 'missing', 'unchanged', 'unchangedWhitespace', 'tooLong']) {
  test(`MyMemory ${failure}: omit retrievalQuestion to activate backend fallback, no retry`, async () => {
    const p = openPage('en', new Map(), { translation: async () => {
      if (failure === 'network') throw TypeError('Failed to fetch');
      return { ok: failure !== 'http', json: async () => {
        if (failure === 'json') throw SyntaxError('Bad JSON');
        return { responseStatus: failure === 'status' ? 429 : 200, quotaFinished: failure === 'quota',
          responseData: failure === 'missing' ? null : { translatedText:
            failure === 'empty' ? ' ' : failure === 'unchanged' ? 'ORIGINAL QUESTION' : failure === 'unchangedWhitespace' ? '  ORIGINAL   QUESTION  ' : failure === 'tooLong' ? 'x'.repeat(2001) : '¿Qué reformas?' } };
      } };
    } });
    p.node('question').value = 'Original question';
    await p.node('sendButton').handlers.click();
    assert.deepEqual(JSON.parse(p.requests.find(r => r.url.endsWith('/answers')).init.body),
      { question: 'Original question', responseLanguage: 'en' });
    assert.equal(p.requests.filter(r => r.url.includes('mymemory')).length, 1);
    assert.equal(p.node('answerText').textContent, 'Answer [S1]');
    assert.equal(p.node('sendButton').disabled, false);
  });
}

test('MyMemory failure followed by backend fallback failure displays an HTTP error, not abstention', async () => {
  const p = openPage('en', new Map(), { failed: true, translation: async () => { throw TypeError('Unavailable'); } });
  p.node('question').value = 'Original question';
  await p.node('sendButton').handlers.click();
  assert.deepEqual(JSON.parse(p.requests.find(r => r.url.endsWith('/answers')).init.body),
    { question: 'Original question', responseLanguage: 'en' });
  assert.equal(p.storage.get('r4r.lastQuestion'), 'Original question');
  assert.equal(p.node('question').value, 'Original question');
  assert.equal(p.node('httpStatus').textContent, '503');
  assert.ok(p.node('errorBox').textContent.includes(p.t('httpError', 503)));
  assert.equal(p.node('resultContent').hidden, true);
  assert.equal(p.node('sendButton').disabled, false);
});

test('MyMemory timeout after 5 seconds activates fallback and blocks duplicate submissions', async () => {
  const p = openPage('en', new Map(), { translation: (url, init) => new Promise((resolve, reject) => {
    init.signal.addEventListener('abort', () => reject({ name: 'AbortError' }));
  }) });
  p.node('question').value = 'Original question';
  const sending = p.node('sendButton').handlers.click();
  await p.node('sendButton').handlers.click();
  assert.equal(p.requests.filter(r => r.url.includes('mymemory')).length, 1);
  [...p.timers.values()].find(t => t.delay === 5000).fn();
  await sending;
  assert.equal(JSON.parse(p.requests.find(r => r.url.endsWith('/answers')).init.body).retrievalQuestion, undefined);
  assert.equal(p.node('sendButton').disabled, false);
  assert.ok([...p.timers.values()].every(t => t.delay !== 5000 && t.delay !== 120000));
});

test('Overall timeout cancels translation and does not submit a RAG request', async () => {
  const p = openPage('en', new Map(), { translation: (url, init) => new Promise((resolve, reject) => {
    init.signal.addEventListener('abort', () => reject({ name: 'AbortError' }));
  }) });
  p.node('question').value = 'Original question';
  const sending = p.node('sendButton').handlers.click();
  [...p.timers.values()].find(t => t.delay === 120000).fn();
  await sending;
  assert.ok(!p.requests.some(r => r.url.endsWith('/answers')));
  assert.ok(p.node('errorBox').textContent.includes(p.t('timeout')));
});

test('Questions over the 500 UTF-8 byte MyMemory limit go intact to the local fallback', async () => {
  const p = openPage('en');
  const text = 'é'.repeat(251);
  p.node('question').value = text;
  await p.node('sendButton').handlers.click();
  assert.ok(!p.requests.some(r => r.url.includes('mymemory')));
  assert.deepEqual(JSON.parse(p.requests.find(r => r.url.endsWith('/answers')).init.body),
    { question: text, responseLanguage: 'en' });
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
      assert.ok(!p.node('errorBox').textContent.includes(p.t('networkError')));
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
    assert.ok(p.node('errorBox').textContent.includes(p.t('networkError')));
    assert.ok(p.node('errorBox').textContent.includes(p.t('cors')));
    assert.ok(!p.node('errorBox').textContent.includes('403'));
    assert.ok(!p.node('runtimeUsed').textContent.includes('403'));
  });
}

for (const lang of ['es', 'en']) {
  test(`${lang}: title, static topology and runtime distinguish configuration from observations`, async () => {
    const p = openPage(lang, new Map(), { runtime: {
      configuredModel: 'qwen3:8b', preferredProvider: { name: 'PC', model: 'qwen3:8b', inferenceHost: { type: 'desktop', hostname: 'PC-GIGA-ZORUX' } },
      lastUsedProvider: { name: 'ASUS', model: 'qwen2.5:7b', inferenceHost: host }, generationState: 'success',
      modelStatus: 'Modelo cargado', loadedModels: [], topK: 3, minScore: .63
    } });
    await tick();
    assert.equal(p.document.querySelectorAll('h1')[0].textContent, lang === 'es' ? 'Pregunta a nuestra IA con RAG' : 'Ask our RAG-powered AI a question');
    assert.equal(p.document.querySelectorAll('.arch-step').length, 4);
    assert.equal(p.document.querySelectorAll('.inference-host').length, 3);
    assert.equal(p.document.querySelectorAll('.primary-host')[0].textContent.includes('NVIDIA GeForce RTX 4060'), true);
    assert.equal(p.node('runtimeModel').textContent, 'qwen3:8b');
    assert.equal(p.node('runtimePreferred').textContent, p.t('hostNames').desktop);
    assert.equal(p.node('runtimeUsed').textContent, `qwen2.5:7b · ${p.t('hostNames').laptop}`);
    assert.equal(p.node('runtimeHost').textContent, p.t('hostNames').laptop);
    assert.doesNotMatch(p.document.body.textContent, /PC-GIGA-ZORUX|PC-ASUS-ZORIN/);
    assert.equal(p.node('runtimeFailover').textContent, p.t('fallbackObserved'));
    assert.equal(p.node('runtimeMemoryScope').textContent, p.t('memoryLastUsed'));
    p.changeLanguage(lang === 'es' ? 'en' : 'es');
    assert.equal(p.node('runtimeFailover').textContent, p.t('fallbackObserved'));
    assert.equal(p.node('runtimeMemoryScope').textContent, p.t('memoryLastUsed'));
    assert.equal(p.node('runtimeUsed').textContent, `qwen2.5:7b · ${p.t('hostNames').laptop}`);
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
      assert.equal(p.node('runtimeHost').textContent, p.t('unknownHost'));
    }
    if (runtime.generationState === 'failed') {
      assert.equal(p.node('runtimeUsed').textContent, p.t('generationFailed'));
      assert.equal(p.node('runtimeHardware').textContent, '');
    }
  }
});

for (const lang of ['es', 'en']) {
  test(`${lang}: benefits start visible, technical disclosures closed, and controls retain aria-expanded`, () => {
    const p = openPage(lang);
    const before = p.requests.length;
    for (const [buttonId, panelId] of [['architectureToggle', 'architectureContent'], ['runtimeToggle', 'runtimeContent'], ['conceptToggle', 'conceptContent']]) {
      const button = p.node(buttonId), panel = p.node(panelId);
      assert.equal(button.tagName, 'button'); // Native Enter/Space activation; exercised in browser QA.
      assert.equal(button.getAttribute('type'), 'button');
      assert.equal(button.getAttribute('aria-controls'), panelId);
      if (panelId === 'conceptContent') {
        assert.equal(panel.hidden, false);
        assert.equal(button.getAttribute('aria-expanded'), 'true');
        button.handlers.click();
      }
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
    const p = openPage(lang);
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
  for (const hash of ['#arquitectura', '#architectureContent', '#runtime', '#runtimeHost']) {
    const p = openPage('en', new Map(), { hash });
    const architecture = hash === '#arquitectura' || hash === '#architectureContent';
    assert.equal(p.node('architectureContent').hidden, !architecture);
    assert.equal(p.node('runtimeContent').hidden, architecture);
  }
  assert.doesNotThrow(() => openPage('en', new Map(), { hash: '#%ZZ' }));
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
    const p = openPage(lang);
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
    assert.ok(p.document.querySelectorAll('.config-details')[0].contains(role));
    assert.ok(html.indexOf('id="demo"') < html.indexOf('id="como-funciona"'));
    assert.ok(html.indexOf('id="como-funciona"') < html.indexOf('id="arquitectura"'));
    assert.ok(html.indexOf('id="arquitectura"') < html.indexOf('id="runtime"'));
    assert.ok(role.textContent.includes('Synology'));
    assert.ok(!role.textContent.includes('GiB'));
    assert.ok(!role.textContent.includes('VRAM'));
    p.node('question').value = 'Question';
    await p.node('sendButton').handlers.click();
    assert.match(p.node('runtimeUsed').textContent, /qwen2.5:7b/);
    assert.equal(p.node('runtimeModel').textContent, 'qwen3:8b');
    p.changeLanguage(lang === 'es' ? 'en' : 'es');
    assert.match(p.node('runtimeUsed').textContent, /qwen2.5:7b/);
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
    assert.equal(p.node('runtimeModel').textContent, 'qwen3:8b');
  });
}
