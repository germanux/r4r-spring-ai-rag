import { chromium } from 'playwright';
import { readFile, mkdir, writeFile } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import assert from 'node:assert/strict';
const base=process.env.R4R_PUBLICATION_BASE || 'http://127.0.0.1:8767/r4r-rag/';
const out=new URL('../docs/validation/angular-migration/publication/',import.meta.url);await mkdir(out,{recursive:true});
const recordings=JSON.parse(await readFile(new URL('./src/app/features/rag/data/recordedExamples.json',import.meta.url)));
const browser=await chromium.launch({headless:true,executablePath:process.env.CHROMIUM_PATH,args:['--no-sandbox']});
const report={base,renders:[],documents:0,consoleErrors:[]};
try {
 for(const scenario of [{name:'angular-technical-es',lang:'es',width:1440,legacy:false,technical:true},{name:'angular-mobile-en',lang:'en',width:390,legacy:false},{name:'legacy-shared-docs-es',lang:'es',width:390,legacy:true}]){
  const page=await browser.newPage({viewport:{width:scenario.width,height:900},locale:scenario.lang,reducedMotion:'reduce'});
  page.on('pageerror',e=>report.consoleErrors.push(e.message));const missing=[];page.on('response',r=>{if(r.status()===404 && r.url().startsWith(base))missing.push(r.url());});
  await page.addInitScript(lang=>localStorage.setItem('r4r.language',lang),scenario.lang);
  await page.route('**/api/**',route=>{if(route.request().url().endsWith('/answers/stream'))return route.fulfill({contentType:'text/event-stream',body:'data: '+JSON.stringify({...recordings.B.es,abstained:false,stage:'ANSWER_READY'})+'\n\n'});return route.fulfill({json:{models:[]}});});
  const response=await page.goto(base+(scenario.legacy?'v1/':'')+'?prerender=1');assert.equal(response.status(),200);
  await page.locator('#question').waitFor();await page.locator('#question').focus();
  if(!scenario.legacy){assert.match(await response.text(),/<h1[^>]*>Asistente de IA para reformas/);assert.equal(await page.locator('base').getAttribute('href'),'./');}
  if(scenario.technical){await page.locator('#runtimeToggle').click();await page.locator('#engineeringDetails').evaluate(el=>el.open=true);await page.locator('#runtimeContent .config-details').last().evaluate(el=>el.open=true);const svg=await page.request.get(new URL('assets/architecture.svg',base).href);const diagram=await svg.text();assert(diagram.includes('v2-ng · Angular · TypeScript · SCSS'));assert.match(await page.locator('#runtimeConfig').textContent(),/v2-ng · Angular · TypeScript · SCSS/);assert.match(await page.locator('#engineeringDetails').textContent(),/v2-ng · Angular · TypeScript · SCSS/);await page.locator('#runtimeContent').screenshot({path:new URL(scenario.name+'.png',out).pathname,style:'.site-header { visibility:hidden !important; }'});}
  else if(scenario.legacy){await page.locator('#question').fill(recordings.B.es.question);await page.locator('#sendButton').click();await page.locator('#resultContent').waitFor({state:'visible'});await page.locator('#sourcesDetails').evaluate(el=>el.open=true);const links=await page.locator('#citations a').evaluateAll(links=>links.map(a=>a.href));assert(links.length>0);for(const url of links){assert(url.startsWith(new URL('docs/',base).href),url);assert.equal((await page.request.get(url)).status(),200);}await page.screenshot({path:new URL(scenario.name+'.png',out).pathname,fullPage:true});}
  else{assert.equal((await page.locator('h1').textContent()).trim(),'AI assistant for renovations');assert.equal(await page.locator('#languageSelect option:checked').textContent(),'🇬🇧 EN');await page.screenshot({path:new URL(scenario.name+'.png',out).pathname});}
  assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));assert.deepEqual(missing,[]);report.renders.push({...scenario,passed:true,missingAssets:missing});
  if(scenario.technical){const catalogue=await (await page.request.get(new URL('docs/catalogue.json',base).href)).json();for(const variants of Object.values(catalogue))for(const doc of Object.values(variants)){const pdf=await page.request.get(new URL(doc.url,base).href);assert.equal(pdf.status(),200,doc.url);const bytes=await pdf.body();assert(bytes.subarray(0,5).toString()==='%PDF-');assert.equal(createHash('sha256').update(bytes).digest('hex'),doc.pdfSha256);const html='docs/'+doc.source.split('/')[0]+'/html/'+doc.source.split('/').pop().replace(/\.md$/,'.html');assert.equal((await page.request.get(new URL(html,base).href)).status(),200,html);report.documents++;}}
  await page.close();
 }
 assert.deepEqual(report.consoleErrors,[]);await writeFile(new URL('results.json',out),JSON.stringify(report,null,2)+'\n');console.log('PASS: '+report.renders.length+' renders, shared legacy links, '+report.documents+' PDF/HTML pairs and root assets');
}finally{await browser.close();}
