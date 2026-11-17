import { test } from 'node:test';
import assert from 'node:assert/strict';
import { commercialEntry } from './prepare-commercial-frontend.mjs';

test('commercial package keeps entry bundles but removes technical prerender content', () => {
  const original = '<html lang="es"><head><title>Athagon</title><base href="./"><link rel="icon" href="assets/favicon.svg"><link rel="stylesheet" href="styles-test.css"></head><body><app-root ng-version="17"><header>ATHAGON LABS</header><main>Technical runtime</main></app-root><script src="main-test.js" type="module"></script></body></html>';
  const html = commercialEntry(original);
  assert.ok(html.includes('<app-root></app-root>'));
  assert.ok(!html.includes('ATHAGON LABS'));
  assert.ok(!html.includes('Technical runtime'));
  assert.ok(html.includes('class="commercial-assistant"'));
  assert.ok(html.includes('assets/riansares/home-blueprint.css'));
  assert.ok(html.includes('href="./"'));
  assert.ok(html.includes('src="main-test.js"'));
  assert.ok(html.includes('href="styles-test.css"'));
  assert.ok(html.includes('/favicon.ico'));
  assert.ok(html.includes('Riansares 4R'));
  assert.ok(original.includes('ATHAGON LABS'));
});
