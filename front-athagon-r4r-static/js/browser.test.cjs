const {chromium}=require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const fs=require('fs'),path=require('path'),assert=require('assert/strict');
const root=path.resolve(__dirname,'..');
const fixtures=JSON.parse(fs.readFileSync(path.join(root,'fixtures/recordings.json')));
const response={stage:'ANSWER_READY',answer:'Safe **answer** [S1]',abstained:false,citations:[{label:'[S1]',source:'es/guides/R4R_80_COCINAS_FONTANERIA_Y_FUGAS.md',headingPath:['Kitchen plumbing'],ordinal:1}]};
(async()=>{
 const browser=await chromium.launch({headless:true,...(process.env.CHROMIUM_PATH?{executablePath:process.env.CHROMIUM_PATH}:{}),args:['--no-sandbox']});
 try {
 for(const lang of ['es','en']) for(const width of [390,768,1280,1920]) {
  const context=await browser.newContext({viewport:{width,height:width<768?844:900},locale:lang,isMobile:width<768,hasTouch:width<768});
  const page=await context.newPage();let requests=[],errors=[],status=200;
  page.on('pageerror',e=>errors.push(e.message));
  await page.addInitScript(()=>{
   const original=window.setTimeout;window.setTimeout=(fn,delay,...args)=>original(fn,delay<10000?Math.min(delay,3):delay,...args);
   window.playback={questions:[],answers:[],pressed:false};
   const value=Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype,'value');
   Object.defineProperty(HTMLTextAreaElement.prototype,'value',{...value,set(v){window.playback.questions.push(v);value.set.call(this,v)}});
   addEventListener('DOMContentLoaded',()=>new MutationObserver(()=>{
    const button=document.querySelector('#sendButton');if(button?.classList.contains('is-preview-pressed'))window.playback.pressed=true;
    const text=document.querySelector('#answerText')?.textContent;if(text)window.playback.answers.push(text);
   }).observe(document.body,{subtree:true,childList:true,characterData:true,attributes:true}));
  });
  await page.route('**/*',async route=>{
   const req=route.request(),u=new URL(req.url());
   if(u.hostname==='preview.test') {
    const filename=path.join(root,u.pathname==='/'?'index.html':u.pathname);
    if(!fs.existsSync(filename))return route.fulfill({status:404,body:'not found'});
    return route.fulfill({body:fs.readFileSync(filename),contentType:filename.endsWith('.js')?'text/javascript':filename.endsWith('.css')?'text/css':filename.endsWith('.png')?'image/png':filename.endsWith('.svg')?'image/svg+xml':filename.endsWith('.json')?'application/json':filename.endsWith('.pdf')?'application/pdf':'text/html'});
   }
   const headers={'Access-Control-Allow-Origin':'*','Access-Control-Allow-Headers':'Content-Type','Access-Control-Allow-Methods':'POST, OPTIONS','Access-Control-Expose-Headers':'Retry-After','Retry-After':'20'};
   if(req.method()==='OPTIONS')return route.fulfill({status:204,headers,body:''});
   if(u.pathname.endsWith('/runtime')) return route.fulfill({headers,contentType:'application/json',body:JSON.stringify({models:[]})});
   requests.push({url:u.pathname,body:req.method()==='POST'?req.postDataJSON():null});
   if(status===0)return route.abort();
   const stages=[{stage:'REQUEST_RECEIVED',elapsedMs:0},{stage:'EMBEDDING_STARTED',model:'nomic-embed-text:latest',provider:'NAS',elapsedMs:2},{stage:'EMBEDDING_COMPLETED',model:'nomic-embed-text:latest',elapsedMs:3,durationMs:1},{stage:'RETRIEVAL_COMPLETED',documentsFound:1,elapsedMs:4},{stage:'GENERATION_STARTED',provider:'PC principal',model:'qwen3:8b',elapsedMs:5},{stage:'VALIDATION_COMPLETED',elapsedMs:6},response];
   return route.fulfill({status,headers,contentType:status===200?'text/event-stream':'application/json',body:status===200?stages.map(e=>'data: '+JSON.stringify(e)+'\n\n').join(''):'{}'});
  });
  await page.goto('http://preview.test/');await page.locator('#question').scrollIntoViewIfNeeded();
  await page.waitForFunction(last=>window.playback.answers.includes(last),fixtures.kitchen[lang][2].answer);
  const playback=await page.evaluate(()=>window.playback);
  for(const data of fixtures.kitchen[lang]) {assert(playback.questions.includes(data.question));assert(playback.answers.includes(data.answer));}
  assert(playback.questions.some(q=>q.length>0 && q.length<fixtures.kitchen[lang][0].question.length));
  assert(playback.pressed);assert.equal(requests.length,0);
  assert.equal(await page.locator('#endpointSettings').isVisible(),false);
  assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
  await page.locator('#sourcesDetails').click();
  const source=page.locator('#citations a').first();assert((await source.getAttribute('href')).includes('/'+lang+'/'));
  const pdf=await page.evaluate(async url=>{const response=await fetch(url);const bytes=new Uint8Array(await response.arrayBuffer());return {status:response.status,type:response.headers.get('content-type'),signature:new TextDecoder().decode(bytes.slice(0,5))}},await source.getAttribute('href'));
  assert.equal(pdf.status,200);assert(pdf.type.startsWith('application/pdf'));assert.equal(pdf.signature,'%PDF-');
  await page.locator('#runtimeToggle').click();await page.locator('#engineeringDetails > summary').click();assert(await page.locator('#engineeringDetails').getAttribute('open')!==null);
  if(width===390 || width===1280) await page.screenshot({path:`/tmp/r4r-technical-${lang}-${width}.png`,fullPage:true});
  await page.locator('#runtimeToggle').click();requests=[];
  await page.evaluate(()=>scrollTo({top:0,behavior:"instant"}));await page.waitForTimeout(100);
  await page.screenshot({path:`/tmp/r4r-final-${lang}-${width}.png`,fullPage:true});
  await page.locator('#tryDemoButton').click();await page.waitForFunction(()=>!document.querySelector('#sendButton').disabled);assert.equal(requests.length,1);
  assert(requests[0].body.question.includes(lang==='es'?'cuatro R':'four Rs'));
  for(const button of await page.locator('.sample').all()) {const n=requests.length;await button.click();await page.waitForFunction(()=>!document.querySelector('#sendButton').disabled);assert.equal(requests.length,n+1);}
  await page.locator('#question').fill('Free query');const n=requests.length;await page.locator('#question').press('Shift+Enter');assert.equal(requests.length,n);await page.locator('#question').press('Enter');await page.waitForFunction(()=>!document.querySelector('#sendButton').disabled);assert.equal(requests.length,n+1);
  assert.equal(await page.locator('#answerText strong').innerText(),'answer');assert(await page.locator('#progressDetails').isVisible());
  await page.locator('#progressDetails > summary').click();assert((await page.locator('#ragProgress').innerText()).includes('GENERATION_STARTED'));assert((await page.locator('#ragProgress').innerText()).includes('ANSWER_READY'));
  status=429;await page.locator('#sendButton').click();await page.locator('#errorBox').waitFor();assert((await page.locator('#errorBox').innerText()).includes('20 s'));
  status=503;await page.locator('#tryDemoButton').click();await page.waitForFunction(()=>!document.querySelector('#sendButton').disabled);assert.equal(await page.locator('#previewNotice').innerText(),lang==='es'?'Respuesta guardada':'Saved response');
  await page.locator('#question').fill('An arbitrary unsupported query');await page.locator('#sendButton').click();await page.waitForFunction(()=>!document.querySelector('#sendButton').disabled);assert.equal(await page.locator('#resultContent').isVisible(),false);
  assert.equal(errors.length,0,errors.join('\n'));console.log('PASS',lang,width);await context.close();
 }
 } finally {await browser.close()}
})().catch(e=>{console.error(e);process.exit(1)});
