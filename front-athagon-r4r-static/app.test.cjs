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
        handlers: {}, classList: { toggle() {} }, setAttribute() {}, replaceChildren() {}, appendChild() {}, append() {}, focus() {}, scrollIntoView() {},
        addEventListener(name, handler) { this.handlers[name] = handler; } });
      for (const [, id] of html.matchAll(/id="([^"]+)"/g)) nodes.set(id, element());
      let runtimeCalls = 0;
      const memoryHost = { type: 'laptop', hostname: 'PC-ASUS-ZORIN', gpu: 'NVIDIA GTX 1050 · 4 GB', ram: '32 GB', cpu: 'Intel i7-7700HQ' };
      const context = { document: { documentElement: { lang }, getElementById: id => nodes.get(id) || null,
        querySelectorAll: () => [], createElement: element }, localStorage: { getItem() { return null; } },
        AbortController, performance, setTimeout, clearTimeout,
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
