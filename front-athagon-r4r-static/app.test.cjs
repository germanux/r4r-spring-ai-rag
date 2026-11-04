const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');

for (const lang of ['es', 'en']) {
  for (const failed of [false, true]) {
    test(`${lang}: runtime refresh after ${failed ? 'failed' : 'successful fallback'} RAG request`, async () => {
      const html = fs.readFileSync(path.join(__dirname, `index_${lang}.html`), 'utf8');
      const nodes = new Map();
      const element = () => ({ textContent: '', value: '', hidden: false, disabled: false,
        handlers: {}, classList: { toggle() {} }, setAttribute() {}, setCustomValidity() {}, reportValidity() {}, replaceChildren() {}, appendChild() {}, append() {}, focus() {}, scrollIntoView() {},
        addEventListener(name, handler) { this.handlers[name] = handler; } });
      for (const [, id] of html.matchAll(/id="([^"]+)"/g)) nodes.set(id, element());
      let runtimeCalls = 0;
      const memoryHost = { type: 'laptop', hostname: 'PC-ASUS-ZORIN', gpu: 'NVIDIA GTX 1050 · 4 GB', ram: '32 GB', cpu: 'Intel i7-7700HQ' };
      const context = { document: { documentElement: { lang }, getElementById: id => nodes.get(id) || null,
        querySelectorAll: () => [], createElement: element }, localStorage: { getItem() { return null; } },
        URL, URLSearchParams, AbortController, performance, setTimeout, clearTimeout,
        fetch: async url => {
          if (url.includes('mymemory')) return { ok: true, json: async () => ({ responseStatus: 200, responseData: { translatedText: 'Pregunta' } }) };
          if (url.endsWith('/answers')) return { ok: !failed, status: failed ? 503 : 200,
            json: async () => failed ? { message: 'Unavailable' } : { answer: 'Answer [S1]', abstained: false, citations: [] } };
          runtimeCalls++;
          const after = runtimeCalls > 1;
          return { ok: true, json: async () => ({ configuredModel: 'qwen3:8b', preferredProvider: { model: 'qwen3:8b' },
            generationState: after && failed ? 'failed' : after ? 'success' : 'idle',
            lastUsedProvider: after && !failed ? { model: 'qwen2.5:7b', inferenceHost: memoryHost } : null,
            inferenceHost: after && !failed ? memoryHost : null, modelStatus: after && failed ? 'Ollama no disponible' : 'Modelo cargado',
            loadedModels: [], topK: 3, minScore: .63 }) };
        } };
      vm.runInNewContext(fs.readFileSync(path.join(__dirname, 'app.js'), 'utf8'), context);
      await new Promise(resolve => setImmediate(resolve));
      nodes.get('question').value = 'Question';
      await nodes.get('sendButton').handlers.click();
      assert.equal(runtimeCalls, 2);
      assert.equal(nodes.get('runtimeModel').textContent, 'qwen3:8b');
      if (failed) {
        assert.match(nodes.get('runtimeUsed').textContent, /failed|fallida/);
        assert.equal(nodes.get('runtimeHardware').textContent, '');
      } else {
        assert.match(nodes.get('runtimeUsed').textContent, /qwen2.5:7b.*PC-ASUS-ZORIN/);
        assert.match(nodes.get('runtimeHardware').textContent, /GTX 1050/);
      }
    });
  }
}

function openPage(lang, storage = new Map()) {
  const html = fs.readFileSync(path.join(__dirname, `index_${lang}.html`), 'utf8');
  const nodes = new Map();
  const element = () => ({ value: '', textContent: '', handlers: {}, children: [], disabled: false,
    classList: { toggle() {} }, setAttribute() {}, replaceChildren() {}, append() {},
    appendChild(child) { this.children.push(child); }, focus() {}, scrollIntoView() {},
    setCustomValidity(message) { this.validationMessage = message; }, reportValidity() {},
    addEventListener(event, handler) { this.handlers[event] = handler; } });
  for (const [, id] of html.matchAll(/id="([^"]+)"/g)) nodes.set(id, element());
  const requests = [], navigations = [];
  const context = { document: { documentElement: { lang }, getElementById: id => nodes.get(id),
    querySelectorAll: () => [], createElement: element },
    localStorage: { getItem: key => storage.get(key) ?? null, setItem: (key, value) => storage.set(key, value) },
    window: { location: { search: '?demo=1', hash: '#demo', replace: url => navigations.push(url), assign: url => navigations.push(url) } },
    URL, URLSearchParams, AbortController, performance, setTimeout, clearTimeout,
    fetch: async (url, options) => { requests.push({ url, options });
      return { ok: true, status: 200, json: async () => url.includes('mymemory')
        ? { responseStatus: 200, responseData: { translatedText: 'Pregunta' } }
        : url.endsWith('/answers') ? { answer: 'OK', citations: [] } : {} }; } };
  vm.runInNewContext(fs.readFileSync(path.join(__dirname, 'app.js'), 'utf8'), context);
  return { nodes, requests, navigations, storage };
}

for (const lang of ['es', 'en']) {
  test(`${lang}: production default, explicit presets, custom validation and persistence`, async () => {
    const { nodes: n, requests, storage } = openPage(lang);
    assert.equal(n.get('apiBaseUrl').value, 'https://r4r-api.athagon.tech');
    assert.equal(n.get('languageSelect').value, lang);
    assert.equal(n.get('endpointPreset').children.length, 4);
    for (const [preset, url] of [['synology', 'http://192.168.1.203:18080'], ['local', 'http://127.0.0.1:8080'], ['production', 'https://r4r-api.athagon.tech']]) {
      n.get('endpointPreset').value = preset;
      n.get('endpointPreset').handlers.change();
      assert.equal(storage.get('r4r-rag-api-base-url'), url);
    }
    n.get('endpointPreset').value = 'custom';
    n.get('endpointPreset').handlers.change();
    n.get('apiBaseUrl').value = 'https://example.org/api/';
    n.get('saveEndpoint').handlers.click();
    assert.equal(storage.get('r4r-rag-api-base-url'), 'https://example.org/api');
    assert.equal(n.get('endpointPreset').value, 'custom');
    n.get('question').value = 'Last question';
    await n.get('sendButton').handlers.click();
    assert.equal(storage.get('r4r-rag-last-question'), 'Last question');
    assert.equal(JSON.parse(requests.find(r => r.url.endsWith('/answers')).options.body).responseLanguage, lang);
    for (const invalid of ['', 'ftp://example.org', 'javascript:alert(1)', 'not a URL']) {
      n.get('apiBaseUrl').value = invalid;
      const before = requests.length;
      n.get('saveEndpoint').handlers.click();
      await n.get('sendButton').handlers.click();
      assert.equal(requests.length, before);
      assert.equal(storage.get('r4r-rag-api-base-url'), 'https://example.org/api');
      assert.ok(n.get('apiBaseUrl').validationMessage);
    }
    const restored = openPage(lang, storage);
    assert.equal(restored.nodes.get('apiBaseUrl').value, 'https://example.org/api');
    assert.equal(restored.nodes.get('question').value, 'Last question');
    assert.equal(restored.requests.some(r => r.url.endsWith('/answers')), false);
    const other = lang === 'es' ? 'en' : 'es';
    n.get('languageSelect').value = other;
    n.get('languageSelect').handlers.change();
    assert.equal(storage.get('r4r-rag-language'), other);
    const translated = openPage(other, storage);
    assert.equal(translated.nodes.get('question').value, 'Last question');
    assert.equal(translated.nodes.get('apiBaseUrl').value, 'https://example.org/api');
    assert.deepEqual(openPage(lang, storage).navigations, [`index_${other}.html?demo=1#demo`]);
  });
  test(`${lang}: invalid saved endpoint uses production`, () => {
    const page = openPage(lang, new Map([['r4r-rag-api-base-url', 'file:///tmp/api']]));
    assert.equal(page.nodes.get('apiBaseUrl').value, 'https://r4r-api.athagon.tech');
    assert.ok(page.requests.every(r => r.url.startsWith('https://r4r-api.athagon.tech/')));
  });
}
