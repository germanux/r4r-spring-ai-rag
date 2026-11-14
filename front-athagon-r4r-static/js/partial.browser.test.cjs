const {chromium}=require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const fs=require('fs'),path=require('path'),assert=require('assert/strict');
const root=path.resolve(__dirname,'..');
(async()=>{
 const browser=await chromium.launch({headless:true,...(process.env.CHROMIUM_PATH?{executablePath:process.env.CHROMIUM_PATH}:{}),args:['--no-sandbox']});
 try {
  for(const lang of ['es','en']) for(const [initial,recommended] of [['PARTIAL','PARTIAL'],['ANSWERED','PARTIAL'],['ANSWERED','ANSWERED']]) {
   const context=await browser.newContext({viewport:{width:390,height:844},locale:lang,reducedMotion:'reduce'});
   const page=await context.newPage(), errors=[];let auditCalls=0;
   page.on('pageerror',error=>errors.push(error.message));
   await page.route('**/*',async route=>{
    const u=new URL(route.request().url());
    if(u.hostname==='preview.test') {
     const file=path.join(root,u.pathname==='/'?'index.html':u.pathname);
     return route.fulfill({body:fs.readFileSync(file),contentType:file.endsWith('.js')?'text/javascript':file.endsWith('.css')?'text/css':'text/html'});
    }
    const headers={'Access-Control-Allow-Origin':'*','Access-Control-Allow-Headers':'Content-Type','Access-Control-Allow-Methods':'POST, OPTIONS'};
    if(route.request().method()==='OPTIONS')return route.fulfill({status:204,headers,body:''});
    if(u.pathname.includes('/audits/')) {
     auditCalls++;
     return route.fulfill({headers,contentType:'application/json',body:JSON.stringify({conclusive:true,originalStatus:initial,
      recommendedStatus:recommended,effectiveStatus:recommended,note:''})});
    }
    const answer={stage:'ANSWER_READY',status:initial,abstained:false,answer:'Useful documented answer [S1].',
     auditId:'12345678-1234-1234-1234-123456789abc',
     citations:[{label:'[S1]',source:lang+'/guides/R4R_80_COCINAS_FONTANERIA_Y_FUGAS.md',headingPath:['Guide'],ordinal:0}]};
    return route.fulfill({headers,contentType:'text/event-stream',body:'data: '+JSON.stringify(answer)+'\n\n'});
   });
   await page.goto('http://preview.test/');
   await page.locator('#question').fill('Question');await page.locator('#sendButton').click();
   const partial=lang==='es'?'RESPUESTA PARCIAL':'PARTIAL ANSWER';
   const heading=recommended==='PARTIAL'?partial:lang==='es'?'Respuesta verificada':'Answer verified';
   await page.waitForFunction(label=>document.querySelector('#resultMode').textContent===label,heading);
   await page.waitForFunction(()=>!document.querySelector('#sendButton').disabled);
   if(recommended==='PARTIAL') {
    assert.equal(await page.locator('#decisionPill').innerText(),partial);
    assert((await page.locator('#decisionText').innerText()).includes(lang==='es'?'parte de tu pregunta':'part of your question'));
   }
   if(initial==='ANSWERED') {
    await page.locator('#auditNote[data-state=reviewed]').waitFor({state:'visible'});
    assert(await page.locator('.panel-head #auditNote').isVisible());
    assert(await page.locator('#auditNote').evaluate(el=>parseFloat(getComputedStyle(el).fontSize)>=16));
   }
   assert.equal(await page.locator('#httpStatus').count(),0);
   assert.equal(await page.locator('#previewNotice').isVisible(),false);
   assert((await page.locator('#answerText').innerText()).includes('Useful documented answer'));
   assert.equal(await page.locator('#resultMode').evaluate(el=>el.classList.contains('is-verified')),recommended==='ANSWERED');
   if(!await page.locator('#sourcesDetails').evaluate(el=>el.open)) await page.locator('#sourcesDetails > summary').click();
   await page.locator('#citations a').first().waitFor({state:'visible'});
   assert.equal(auditCalls,1);assert.equal(errors.length,0,errors.join('\n'));
   assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
   await page.screenshot({path:`/tmp/r4r-partial-${lang}-${initial}-${recommended}.png`,fullPage:true});
   console.log('PASS response heading/SSE/audit',lang,initial,recommended);await context.close();
  }
 } finally {await browser.close()}
})().catch(error=>{console.error(error);process.exit(1)});
