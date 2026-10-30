const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');

function page(lang, fetch) {
  const elements = new Map();
  function element(id) {
    if (!elements.has(id)) elements.set(id, {
      value: '', textContent: '', disabled: false, hidden: false, dataset: {},
      classList: { toggle() {} }, setAttribute() {}, focus() {}, scrollIntoView() {},
      replaceChildren() {}, appendChild() {}, append() {},
      addEventListener(event, fn) { this[event] = fn; }
    });
    return elements.get(id);
  }
  const document = { documentElement: { lang }, getElementById: element,
    querySelectorAll: () => [], createElement: () => element('generated') };
  vm.runInNewContext(fs.readFileSync('front-athagon-r4r-static/app.js', 'utf8'), {
    document, fetch, localStorage: { getItem: () => null, setItem() {} },
    navigator: { clipboard: { writeText: async () => {} } },
    AbortController, URLSearchParams, performance, setTimeout, clearTimeout
  });
  return element;
}

const ok = (body) => ({ ok: true, status: 200, json: async () => body });
const answer = { answer: 'Answer [S1]', abstained: false, citations: [] };

test('Spanish sends the original question without translation', async () => {
  const calls = [];
  const e = page('es', async (url, opts) => { calls.push({ url, opts }); return ok(answer); });
  e('question').value = '¿Cuánto cuesta?';
  await e('sendButton').click();
  assert.equal(calls.length, 1);
  assert.deepEqual(JSON.parse(calls[0].opts.body), { question: '¿Cuánto cuesta?' });
});

test('English waits for translation and blocks a duplicate submission', async () => {
  const calls = [];
  let finishTranslation;
  const e = page('en', (url, opts) => {
    calls.push({ url, opts });
    if (url.includes('mymemory')) return new Promise((resolve) => { finishTranslation = resolve; });
    return Promise.resolve(ok(answer));
  });
  e('question').value = 'How much?';
  const first = e('sendButton').click();
  await e('sendButton').click();
  assert.equal(calls.length, 1);
  assert.equal(e('sendButton').disabled, true);
  finishTranslation(ok({ responseStatus: 200, responseData: { translatedText: '¿Cuánto cuesta?' } }));
  await first;
  assert.equal(calls.length, 2);
  assert.deepEqual(JSON.parse(calls[1].opts.body), {
    question: 'How much?', retrievalQuestion: '¿Cuánto cuesta?', responseLanguage: 'en'
  });
  assert.equal(e('sendLabel').textContent, 'Ask');
});

test('English falls back to the original question when translation fails', async () => {
  const calls = [];
  const e = page('en', async (url, opts) => {
    calls.push({ url, opts });
    if (url.includes('mymemory')) throw new Error('unavailable');
    return ok({ answer: '', abstained: true, citations: [] });
  });
  e('question').value = 'How much?';
  await e('sendButton').click();
  assert.deepEqual(JSON.parse(calls[1].opts.body), {
    question: 'How much?', retrievalQuestion: 'How much?', responseLanguage: 'en'
  });
  assert.equal(e('decisionPill').textContent, 'ABSTENTION');
  assert.equal(e('decisionText').textContent, 'There is not enough evidence to answer reliably.');
});
