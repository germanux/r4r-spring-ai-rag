import {chromium} from 'playwright';
import {readFile,mkdir,writeFile} from 'node:fs/promises';
import assert from 'node:assert/strict';
const out=new URL('../docs/validation/angular-migration/'+(process.env.R4R_RESPONSIVE_OUTPUT||'responsive/'),import.meta.url);await mkdir(out,{recursive:true});
const recordings=JSON.parse(await readFile(new URL('./src/app/features/rag/data/recordedExamples.json',import.meta.url)));
const browser=await chromium.launch({headless:true,executablePath:process.env.CHROMIUM_PATH,args:['--no-sandbox']});const report=[];
const origin='http://127.0.0.1:8765';
async function setup(version,width,lang){
 const context=await browser.newContext({viewport:{width,height:844},locale:lang,reducedMotion:'reduce',permissions:['clipboard-read','clipboard-write']});const page=await context.newPage();const errors=[];page.on('pageerror',e=>errors.push(e.message));
 let auditResolve;let auditMode='pass';let auditStartedResolve;let auditStarted=new Promise(r=>auditStartedResolve=r);
 await page.route('**/api/**',async route=>{const u=new URL(route.request().url());if(u.pathname.includes('/audits/')){auditStartedResolve();await new Promise(r=>auditResolve=r);if(auditMode==='failed')return route.fulfill({status:503,json:{}}).catch(()=>{});const original=page.testStatus||'ANSWERED',effective=auditMode==='downgrade'?'PARTIAL':original;return route.fulfill({json:{conclusive:auditMode!=='inconclusive',originalStatus:original,recommendedStatus:effective,effectiveStatus:effective,note:auditMode==='downgrade'?'Limits documented in the sources.':''}}).catch(()=>{});}if(u.pathname.endsWith('/answers'))return route.fulfill({json:{...recordings.C[lang],status:'ANSWERED'}});return route.fulfill({json:{models:[]}});});
 await page.addInitScript(({lang})=>{
 localStorage.setItem('r4r.language',lang);const nativeFetch=window.fetch;window.testMode='sse';window.fetch=async(url,init)=>{if(String(url).endsWith('/api/rag/answers/stream')){if(window.testMode==='rest')return new Response('',{status:404});if(window.testMode==='network')throw new TypeError('Failed to fetch');if(['429','503'].includes(window.testMode))return new Response('{}',{status:Number(window.testMode)});return new Response(new ReadableStream({start(c){window.testStream=c;const enc=new TextEncoder();c.enqueue(enc.encode('data: {"stage":"REQUEST_RECEIVED","elapsedMs":0}\n\ndata: {"stage":"RETRIEVAL_STARTED","elapsedMs":1}\n\n'));init.signal.addEventListener('abort',()=>c.error(new DOMException('Aborted','AbortError')));}}),{headers:{'Content-Type':'text/event-stream'}});}return nativeFetch(url,init);};
 },{lang});
 await page.goto(`${origin}/${version}/?prerender=1`);await page.locator('#question').waitFor();
 // Stop legacy autoplay before it starts. Angular honours prerender mode.
 await page.locator('#question').focus();
 return {page,context,errors,setAudit(mode){auditMode=mode;},async waitAudit(){await auditStarted;},finishAudit(){auditResolve?.();},resetAudit(){auditStarted=new Promise(r=>auditStartedResolve=r);},};
}
async function visibleStart(page,selector){return page.locator(selector).evaluate(el=>{const r=el.getBoundingClientRect(),h=document.querySelector('.site-header').getBoundingClientRect().bottom;return {top:r.top,bottom:r.bottom,header:h,visible:r.top>=h-2&&r.top<innerHeight-50};});}
async function finish(page,lang,status='ANSWERED',audit=true){page.testStatus=status;const r=status==='ABSTAINED'?recordings.D[lang]:recordings.kitchen[lang][2];await page.evaluate(({r,status,audit})=>{const enc=new TextEncoder();window.testStream.enqueue(enc.encode('data: '+JSON.stringify({...r,capture:undefined,status,stage:'ANSWER_READY',abstained:status==='ABSTAINED',auditId:audit?'12345678-1234-1234-1234-123456789abc':undefined})+'\n\n'));window.testStream.close();},{r,status,audit});await page.locator('#resultContent').waitFor({state:'visible'});}
try{
if(!process.env.R4R_ZOOM_ONLY){
for(const version of ['v1','v2-ng'])for(const width of [320,360,390,430,768,1024,1440])for(const lang of ['es','en']){
 const t=await setup(version,width,lang),{page}=t;
 assert.equal(await page.locator('h1').count(),1);assert.equal((await page.locator('h1').textContent()).trim(),lang==='es'?'Asistente de IA para reformas':'AI assistant for renovations');assert(await page.locator('h1').isVisible());
 assert.equal(await page.locator('#languageSelect option:checked').textContent(),lang==='es'?'🇪🇸 ES':'🇬🇧 EN');
 if(width===390)await page.screenshot({path:new URL(`${version}-${lang}-before.png`,out).pathname});
 assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'initial overflow');
 if(width<=960){assert((await page.locator('.site-header').boundingBox()).height<85,'compact header');await page.locator('#menuToggle').click();assert.equal(await page.locator('#menuToggle').getAttribute('aria-expanded'),'true');await page.keyboard.press('Escape');assert.equal(await page.locator('#menuToggle').getAttribute('aria-expanded'),'false');await page.locator('#menuToggle').click();await page.locator('.nav a[href="#arquitectura"]').click();assert.equal(await page.locator('#menuToggle').getAttribute('aria-expanded'),'false');await page.locator('#architectureContent').waitFor({state:'visible'});}
 if(width>=1024){const a=await page.locator('.query-panel').boundingBox(),b=await page.locator('.response-panel').boundingBox();assert(Math.abs(a.y-b.y)<2&&b.x>a.x,'desktop columns');}
 await page.locator('#question').fill('¿Cómo pinto una pared?');await page.locator('#sendButton').click();await page.locator('#loadingState').waitFor({state:'visible'});await page.waitForTimeout(60);
 if(width<=760){assert((await visibleStart(page,'h1')).visible,'compact h1 stays in viewport');const title=await page.locator('h1').boundingBox(),summary=await page.locator('#querySummary').boundingBox();assert(title.y+title.height<=summary.y,'title before question');const pos=await visibleStart(page,'#loadingState');assert(pos.visible,JSON.stringify({version,width,lang,stage:'progress',pos}));assert.equal(await page.locator('.query-panel').isVisible(),false);}
 if(width===390)await page.screenshot({path:new URL(`${version}-${lang}-loading.png`,out).pathname});
 await finish(page,lang);await t.waitAudit();await page.locator('#auditNote[data-state="pending"]').waitFor();if(width===390)await page.screenshot({path:new URL(`${version}-${lang}-audit-pending.png`,out).pathname});
 if(width<=760){assert((await visibleStart(page,'h1')).visible,'h1 remains visible with final answer');const pos=await visibleStart(page,'#answerText');assert(pos.visible,JSON.stringify({version,width,lang,stage:'answer',pos}));}
 const before=await page.locator('#answerText').boundingBox();t.finishAudit();await page.locator('#auditNote[data-state="reviewed"]').waitFor();const after=await page.locator('#answerText').boundingBox();assert(Math.abs(before.y-after.y)<2,'audit layout shift');
 assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'result overflow');
 const badge=await page.locator('#auditNote').evaluate(el=>({w:el.getBoundingClientRect().width,h:el.getBoundingClientRect().height}));assert(badge.w>100&&badge.h<65,'badge readable');
 const spacing=await page.evaluate(()=>{const head=document.querySelector('.response-panel > .panel-head').getBoundingClientRect(),text=document.querySelector('#answerText').getBoundingClientRect(),box=document.querySelector('.answer-box').getBoundingClientRect();return {headerHeight:head.height,toolbarGap:text.top-box.top};});assert(spacing.toolbarGap<30,'no empty copy/provider toolbar row');
 await page.screenshot({path:new URL(`${version}-${width}-${lang}-answer.png`,out).pathname,fullPage:true});
 if(width<=760){await page.locator('#editQuestion').click();assert.equal(await page.locator('#question').inputValue(),'¿Cómo pinto una pared?');assert(await page.locator('#question').isVisible());}
 assert.deepEqual(t.errors,[]);report.push({version,width,lang,progressInViewport:true,answerInViewport:width<=760?'passed':'desktop',auditShift:after.y-before.y,spacing,consoleErrors:t.errors});await t.context.close();
}
for(const version of ['v1','v2-ng'])for(const lang of ['es','en'])for(const mode of ['partial','abstained','downgrade','inconclusive','failed']){
 const t=await setup(version,390,lang),{page}=t;t.setAudit(mode);await page.locator('#question').fill('¿Cómo pinto una pared?');await page.locator('#sendButton').click();await page.locator('#loadingState').waitFor({state:'visible'});await finish(page,lang,mode==='partial'?'PARTIAL':mode==='abstained'?'ABSTAINED':'ANSWERED');await t.waitAudit();const before=await page.locator('#answerText').boundingBox();t.finishAudit();const state=mode==='inconclusive'?'inconclusive':mode==='failed'?'unavailable':'reviewed';await page.locator(`#auditNote[data-state="${state}"]`).waitFor();const after=await page.locator('#answerText').boundingBox();
 if(mode==='partial'){const gap=await page.evaluate(()=>document.querySelector('.answer-box').getBoundingClientRect().top-document.querySelector('#decisionRow').getBoundingClientRect().bottom);assert(gap<=12,'partial notice immediately precedes answer');const title=lang==='es'?'Respuesta parcial':'Partial answer';assert.equal(await page.getByText(title,{exact:true}).filter({visible:true}).count(),1);assert.equal((await page.locator('#decisionText').textContent()).trim(),lang==='es'?'Las fuentes solo permiten responder parte de la pregunta.':'The sources only cover part of your question.');assert(Math.abs(before.y-after.y)<2);}
 if(mode==='downgrade')assert.match(await page.locator('#resultMode').textContent(),/parcial|Partial/);
 if(['inconclusive','failed'].includes(mode)){assert.match(await page.locator('#resultMode').textContent(),/verificada|verified/);assert(Math.abs(before.y-after.y)<2);}
 await page.screenshot({path:new URL(`${version}-${lang}-${mode}.png`,out).pathname,fullPage:true});assert.deepEqual(t.errors,[]);report.push({version,lang,mode,state,auditShift:after.y-before.y});await t.context.close();
}

for(const version of ['v1','v2-ng']){
 const t=await setup(version,390,'es'),{page}=t;
 async function submit(text){if(await page.locator('#editQuestion').isVisible())await page.locator('#editQuestion').click();await page.locator('#question').fill(text);await page.locator('#sendButton').click();}
 await submit('Regression sources and copy');await page.locator('#loadingState').waitFor({state:'visible'});await finish(page,'es','ANSWERED',false);await page.locator('#copyAnswerButton').click();assert.equal(await page.evaluate(()=>navigator.clipboard.readText()),recordings.kitchen.es[2].answer);
 await page.locator('#sourcesDetails').evaluate(el=>el.open=true);const urls=await page.locator('#citations a').evaluateAll(links=>links.map(a=>a.href));assert(urls.length>0);for(const url of urls)assert.equal((await page.request.get(url)).status(),200,url);
 // A started stream is cancelled from the mobile edit control; no stale answer/error.
 await submit('Cancel in flight');await page.locator('#loadingState').waitFor({state:'visible'});await page.locator('#editQuestion').click();await page.waitForTimeout(80);assert.equal(await page.locator('#resultContent').isVisible(),false);assert.equal(await page.locator('#errorBox').isVisible(),false);
 for(const mode of ['network','429','503']){await page.evaluate(mode=>window.testMode=mode,mode);await submit('Error '+mode);await page.locator('#errorBox').waitFor({state:'visible'});assert((await page.locator('#errorBox').textContent()).trim().length>10);await page.screenshot({path:new URL(`${version}-${mode}.png`,out).pathname});}
 await page.evaluate(()=>window.testMode='sse');await submit('Timeout');await page.locator('#loadingState').waitFor({state:'visible'});await page.evaluate(()=>{window.testStream.enqueue(new TextEncoder().encode('data: {"stage":"ERROR","message":"timeout"}\n\n'));window.testStream.close();});await page.locator('#errorBox').waitFor({state:'visible'});assert.match(await page.locator('#errorBox').textContent(),/180|tiempo/);await page.screenshot({path:new URL(`${version}-timeout.png`,out).pathname});
 await page.evaluate(()=>window.testMode='rest');await submit('REST fallback');await page.locator('#resultContent').waitFor({state:'visible'});assert((await page.locator('#answerText').textContent()).length>10);
 // A late audit from the first query cannot classify a later answer.
 await page.evaluate(()=>window.testMode='sse');await submit('First query');await page.locator('#loadingState').waitFor({state:'visible'});t.resetAudit();t.setAudit('downgrade');await finish(page,'es');await t.waitAudit();await submit('Second query');await page.locator('#loadingState').waitFor({state:'visible'});await finish(page,'es','ANSWERED',false);t.finishAudit();await page.waitForTimeout(100);assert.match(await page.locator('#resultMode').textContent(),/verificada/);assert.equal(await page.locator('#auditNote').isVisible(),false);
 const previousAnswer=await page.locator('#answerText').textContent();await page.locator('#languageSelect').selectOption('en');assert.equal(await page.locator('#question').inputValue(),'Second query');assert.equal(await page.locator('#answerText').textContent(),previousAnswer);assert.equal(await page.locator('#languageSelect').inputValue(),'en');
 assert.deepEqual(t.errors,[]);report.push({version,test:'copy, documents, cancellation, network, 429, 503, timeout, REST, stale audit, dynamic language',passed:true});await t.context.close();
}

}
for(const version of ['v1','v2-ng'])for(const lang of ['es','en'])for(const width of [320,390]){
 const t=await setup(version,width,lang),{page}=t;t.setAudit('downgrade');
 await page.evaluate(()=>document.documentElement.style.fontSize='20px');
 assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'enlarged type overflow');
 assert(await page.evaluate(()=>document.querySelector('.brand').getBoundingClientRect().right<=document.querySelector('#languageSelect').getBoundingClientRect().left),'brand/language overlap');
 await page.locator('#question').fill('¿Cómo pinto una pared?');await page.locator('#sendButton').click();await page.locator('#loadingState').waitFor({state:'visible'});await page.waitForTimeout(60);assert((await visibleStart(page,'#loadingState')).visible,'enlarged progress visible');
 await finish(page,lang);await t.waitAudit();const before=await page.locator('#answerText').boundingBox();t.finishAudit();await page.locator('#auditNote[data-state="reviewed"]').waitFor();const after=await page.locator('#answerText').boundingBox();assert(Math.abs(before.y-after.y)<2,JSON.stringify({version,lang,width,zoom:'125%',shift:after.y-before.y}));
 assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'enlarged response overflow');
 await page.screenshot({path:new URL(`${version}-${width}-${lang}-125percent.png`,out).pathname,fullPage:true});
 report.push({version,lang,width,test:'125% text size',auditShift:after.y-before.y,passed:true});await t.context.close();
}
await writeFile(new URL('results.json',out),JSON.stringify(report,null,2));console.log('PASS',report.length,'responsive scenarios');
}finally{await browser.close();}
