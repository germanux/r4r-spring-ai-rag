// Reproducible browser checks. All external traffic is intercepted; no LLM calls.
const { chromium } = require('playwright');
const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');

async function main() {
  const root = process.argv[2];
  assert(root, 'Usage: node frontend/validate-commercial.cjs /path/to/static-release');
  const server = http.createServer((req, res) => {
    try {
      let file = path.join(root, decodeURIComponent(req.url.split('?')[0]));
      if (fs.statSync(file).isDirectory()) file = path.join(file, 'index.html');
      res.setHeader('Content-Type', ({'.html':'text/html', '.js':'text/javascript',
        '.css':'text/css', '.pdf':'application/pdf'})[path.extname(file)] || 'application/octet-stream');
      res.end(fs.readFileSync(file));
    } catch { res.statusCode = 404; res.end(); }
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  let browser;
  try {
    browser = await chromium.launch({headless:true, args:['--no-sandbox'],
      ...(process.env.CHROMIUM_PATH ? {executablePath:process.env.CHROMIUM_PATH} : {})});
    const page = await browser.newPage();
    let mode = 'ANSWERED', calls = 0;
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    await page.route('**/*', route => {
      const url = new URL(route.request().url());
      if (url.hostname === '127.0.0.1') return route.continue();
      if (url.pathname.endsWith('/answers/stream')) {
        calls++;
        if (mode === 'ERROR') return route.fulfill({status:429, headers:{'Retry-After':'20'}});
        if (mode === 'REST') return route.fulfill({status:404});
        const result = {stage:'ANSWER_READY', answer:'Orientación de prueba', status:mode,
          abstained:mode === 'ABSTAINED',
          citations:[{label:'[S1]', source:'es/R4R_60_SERVICE_AREA_TOWNS.md', headingPath:[]}]};
        return route.fulfill({contentType:'text/event-stream',
          body:'data: {"stage":"REQUEST_RECEIVED"}\n\ndata: ' + JSON.stringify(result) + '\n\n'});
      }
      if (url.pathname.endsWith('/usage/views')) return route.fulfill({status:202});
      if (url.pathname.endsWith('/answers')) return route.fulfill({json:{
        answer:'Fallback correcto', abstained:false, citations:[]}});
      return route.abort();
    });
    for (const width of [1440, 900, 390]) {
      await page.setViewportSize({width, height:1000});
      await page.goto('http://127.0.0.1:' + server.address().port);
      const frame = page.frameLocator('#r4r-assistant');
      await frame.locator('#question').waitFor();
      const reject=page.getByRole('button',{name:/Reject both|Rechazar ambas|Rechazar Google Analytics/});
      if(await reject.count()) await reject.click();
      assert.equal(await frame.locator('app-header').count(), 0);
      await page.waitForTimeout(100);
      assert.equal(calls, 0, 'Embed must never start inference automatically');
      for (const status of ['ANSWERED', 'PARTIAL', 'ABSTAINED', 'ERROR', 'REST']) {
        mode = status;
        const previous = calls;
        await frame.locator('#question').fill('¿Cómo preparar una reforma?');
        await frame.locator('#sendButton').click();
        if (status === 'ERROR') await frame.locator('#errorBox').waitFor({state:'visible'});
        else await frame.locator('#answerText').getByText(
          status === 'REST' ? 'Fallback correcto' : 'Orientación de prueba').waitFor();
        assert.equal(calls, previous + 1, 'One SSE attempt per submission');
        if (['ANSWERED','PARTIAL'].includes(status)) {
          const source = await frame.locator('#citations a').first().getAttribute('href');
          const response = await page.request.get('http://127.0.0.1:' + server.address().port + '/asistente/' + source);
          assert.equal(response.status(), 200);
        }
      }
      await page.locator('#asistente').evaluate(el => el.scrollIntoView({block:'start', behavior:'instant'}));
      await page.evaluate(() => window.scrollBy({top:-160, behavior:'instant'}));
      await page.screenshot({path:`/tmp/commercial-${width}.png`});
      assert(!(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)));
      const size = await frame.locator('body').evaluate(el => ({height:el.offsetHeight, view:innerHeight}));
      assert(Math.abs(size.height - size.view) <= 1, 'Iframe must not introduce vertical scrolling');
      assert(await page.locator('a[href*="wa.me"]').count() > 0);
      assert.equal(await page.locator('#trabajos').count(), 1);
      console.log('PASS', width, size);
      calls = 0;
    }
    assert.deepEqual(errors, []);
  } finally { await browser?.close(); server.close(); }
}
main().catch(error => { console.error(error); process.exitCode = 1; });
