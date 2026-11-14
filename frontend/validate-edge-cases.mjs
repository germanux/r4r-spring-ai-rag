import {chromium} from 'playwright';
import assert from 'node:assert/strict';
import {readFile,writeFile} from 'node:fs/promises';
const browser=await chromium.launch({headless:true,executablePath:process.env.CHROMIUM_PATH,args:['--no-sandbox']});
const saved=JSON.parse(await readFile(new URL('./src/app/migration/recordedExamples.json',import.meta.url)));
const report=[];
try {
const page=await browser.newPage({locale:'es'});let mode='broken',requests=[];
const answer={...saved.C.es,status:'PARTIAL'};
await page.route('**/api/**',async route=>{const path=new URL(route.request().url()).pathname;requests.push(path);if(!path.includes('/answers'))return route.fulfill({json:{models:[]}});if(mode==='broken')return route.fulfill({contentType:'text/event-stream',body:'data: {"stage":"GENERATION_STARTED"}\n\ndata: broken\n\n'});if(mode==='pending'){await new Promise(r=>setTimeout(r,500));return route.fulfill({contentType:'text/event-stream',body:'data: '+JSON.stringify({...answer,stage:'ANSWER_READY'})+'\n\n'}).catch(()=>{});}if(mode==='abstained')return route.fulfill({contentType:'text/event-stream',body:'data: '+JSON.stringify({...saved.D.es,stage:'ANSWER_READY'})+'\n\n'});return route.fulfill({contentType:'text/event-stream',body:'data: '+JSON.stringify({...answer,stage:'ANSWER_READY'})+'\n\n'});});
await page.goto('http://127.0.0.1:8765/v2-ng/?prerender=1');await page.locator('#languageSelect').selectOption('es');
await page.locator('#question').fill('Malformed stream');await page.locator('#sendButton').click();await page.locator('#errorBox').waitFor({state:'visible'});assert.equal(requests.filter(p=>p.endsWith('/answers')).length,0);report.push('Malformed SSE never replays inference through REST');
mode='pending';await page.locator('#question').fill('Cancellation');await page.locator('#sendButton').click();await page.locator('#loadingState').waitFor({state:'visible'});await page.locator('#clearButton').click();await page.waitForTimeout(700);assert.equal(await page.locator('#answerText').textContent(),'');assert.equal(await page.locator('#question').inputValue(),'');report.push('Clear aborts in-flight query and ignores late response');
mode='partial';await page.locator('#question').fill('Partial');await page.locator('#sendButton').click();await page.locator('#resultContent').waitFor({state:'visible'});await page.waitForFunction(()=>document.querySelector('#decisionPill')?.textContent?.includes('PARCIAL'));assert((await page.locator('#citations a').count())>0);report.push('Native PARTIAL preserves answer and citations');
mode='abstained';await page.locator('#question').fill('Out of scope');await page.locator('#sendButton').click();await page.locator('#resultContent').waitFor({state:'visible'});await page.waitForFunction(()=>document.querySelector('#decisionPill')?.textContent?.includes('INSUFICIENTE'));assert.equal(await page.locator('#citations a').count(),0);report.push('ABSTAINED with no citations');
await page.locator('#languageSelect').selectOption('en');assert.equal(await page.locator('h1').textContent(),'Your AI assistant for renovations');report.push('Language changes reset previous query state');
const noJS=await browser.newPage({javaScriptEnabled:false});await noJS.goto('http://127.0.0.1:8765/v2-ng/');assert((await noJS.locator('h1').textContent()).includes('Asistente'));assert.equal(await noJS.locator('#question').count(),1);report.push('Initial HTML renders with JavaScript disabled');
await writeFile(new URL('../docs/validation/angular-migration/edge-results.json',import.meta.url),JSON.stringify(report,null,2));console.log('PASS',report.length,'edge cases');
}finally{await browser.close();}
