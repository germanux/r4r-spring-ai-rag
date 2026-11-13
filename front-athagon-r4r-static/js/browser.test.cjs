const {chromium}=require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const fs=require('fs'),path=require('path'),assert=require('assert/strict');
(async()=>{
const browser=await chromium.launch({headless:true,...(process.env.CHROMIUM_PATH ? {executablePath:process.env.CHROMIUM_PATH} : {}),args:['--no-sandbox']});
for(const viewport of [{width:1440,height:900},{width:1280,height:900},{width:390,height:844},{width:360,height:800}]) {
const context=await browser.newContext({viewport});
const page=await context.newPage();let external=[],errors=[];
page.on('pageerror',e=>errors.push(e.message));
await page.route('**/*',route=>{const u=new URL(route.request().url());if(u.hostname==='preview.test'){let f=path.join(process.cwd(),'front-athagon-r4r-static',u.pathname==='/'?'index.html':u.pathname);return route.fulfill({body:fs.readFileSync(f),contentType:f.endsWith('.js')?'text/javascript':f.endsWith('.css')?'text/css':'text/html'});}external.push(u.href);return route.abort();});
await page.addInitScript(() => localStorage.setItem('r4r.lastQuestion', 'A previous query'));
await page.goto('http://preview.test/');
const inputVisible = await page.locator('#question').evaluate(el => { const r=el.getBoundingClientRect();return Math.max(0, Math.min(r.bottom,innerHeight)-Math.max(0,r.top))/r.height >= .5; });
if(!inputVisible) {
 await page.waitForTimeout(700);assert.equal(await page.locator('#previewNotice').isVisible(),false);
 await page.locator('#question').scrollIntoViewIfNeeded();
}
await page.waitForFunction(()=>document.querySelector('#previewNotice').hidden===false);
await page.waitForFunction(()=>document.querySelector('#sendButton').classList.contains('is-preview-pressed'),{},{timeout:10000});
assert(await page.locator('#question').inputValue());
await page.waitForFunction(()=>document.querySelector('#resultContent').hidden===false && document.querySelector('#sendLabel').textContent!=='Processing…',{},{timeout:18000});
await page.waitForFunction(()=>document.querySelector('#answerText').textContent === JSON.parse(document.querySelector('#recordedExamples').textContent).C[document.documentElement.lang].answer);assert.equal(external.length,0);assert.equal(errors.length,0,errors.join(','));
const pdfLink=page.locator('#citations a').first();assert((await pdfLink.getAttribute('href')).endsWith('R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.pdf'));
assert.equal(await page.locator('#endpointSettings').isVisible(),false);
assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
await page.screenshot({path:`/tmp/r4r-${viewport.width}.png`,fullPage:true});
await page.locator('#tryDemoButton').click();await page.waitForTimeout(300);await page.locator('#question').fill('Mi pregunta');await page.waitForTimeout(700);assert.equal(await page.locator('#question').inputValue(),'Mi pregunta');
await page.reload();await page.locator('#question').scrollIntoViewIfNeeded();await page.waitForTimeout(800);assert.equal(await page.locator('#previewNotice').isVisible(),false);
console.log('PASS',viewport.width);await context.close();
}
// Reduced motion and real SSE request remain independent from scripted playback.
const context=await browser.newContext({viewport:{width:390,height:844},reducedMotion:'reduce'});
const page=await context.newPage(); let requests=0;
await page.route('**/*',route=>{
 const u=new URL(route.request().url());
 if(u.hostname==='preview.test') { const f=path.join(process.cwd(),'front-athagon-r4r-static',u.pathname==='/'?'index.html':u.pathname);return route.fulfill({body:fs.readFileSync(f),contentType:f.endsWith('.js')?'text/javascript':f.endsWith('.css')?'text/css':'text/html'}); }
 if(u.pathname.endsWith('/answers/stream')) { requests++; return route.fulfill({contentType:'text/event-stream',body:'data: '+JSON.stringify({stage:'ANSWER_READY',answer:'Verified response [S1]',abstained:false,citations:[{label:'[S1]',source:'manual',headingPath:[],ordinal:1}]})+'\n\n'}); }
 return route.abort();
});
await page.goto('http://preview.test/');await page.locator('#tryDemoButton').click();await page.waitForTimeout(1400);
assert.equal(requests,0);await page.locator('#languageSelect').selectOption('es');
assert.equal(await page.locator('#previewNotice').isVisible(),true);
await page.locator('#question').fill('Una pregunta real');await page.locator('#sendButton').click();
await page.waitForFunction(()=>document.querySelector('#answerText').textContent==='Verified response [S1]');
assert.equal(requests,1);const bounds=await page.locator('.response-panel').boundingBox();assert(bounds.y>=0 && bounds.y<844);
await page.locator('#sourcesDetails').click();assert(await page.locator('#citations').isVisible());
await page.route('**/answers/stream',r=>r.fulfill({status:r.request().method()==='OPTIONS'?204:429,headers:{'Retry-After':'20','Access-Control-Allow-Origin':'*','Access-Control-Allow-Headers':'Content-Type','Access-Control-Allow-Methods':'POST, OPTIONS','Access-Control-Expose-Headers':'Retry-After'},contentType:'application/json',body:r.request().method()==='OPTIONS'?'':'{}'}));
await page.locator('#question').fill('Otra pregunta');await page.locator('#sendButton').click();await page.locator('#errorBox').waitFor();assert((await page.locator('#errorBox').innerText()).includes('20 s'),await page.locator('#errorBox').innerText());
await page.goto('http://preview.test/?dev=1');assert(await page.locator('#endpointSettings').isVisible());
console.log('PASS reduced motion, language cancellation, SSE, scroll, citations, 429 and developer mode');
await context.close();await browser.close();
})().catch(e=>{console.error(e);process.exit(1)});
