const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
const fs=require('fs'),path=require('path'),assert=require('assert/strict');
const root=path.resolve(__dirname,'..');
(async()=>{
 const browser=await chromium.launch({headless:true,...(process.env.CHROMIUM_PATH?{executablePath:process.env.CHROMIUM_PATH}:{}),args:['--no-sandbox']});
 try {
  const context=await browser.newContext({viewport:{width:390,height:844},isMobile:true,hasTouch:true,reducedMotion:'reduce',locale:'es'});
  const page=await context.newPage();let count=0,mode='ok',failed=false;
  page.on('requestfailed',r=>{if(r.url().includes('/answers/stream'))failed=true});
  await page.addInitScript(()=>{const timer=setTimeout;window.setTimeout=(f,ms,...args)=>timer(f,ms===180000?50:ms===200?150:ms<10000?Math.min(ms,4):ms,...args)});
  await page.route('**/*',async r=>{
   const u=new URL(r.request().url());
   if(u.hostname==='preview.test'){const f=path.join(root,u.pathname==='/'?'index.html':u.pathname);return r.fulfill({body:fs.readFileSync(f),contentType:f.endsWith('.js')?'text/javascript':f.endsWith('.css')?'text/css':'text/html'})}
   const headers={'Access-Control-Allow-Origin':'*','Access-Control-Allow-Headers':'Content-Type','Access-Control-Allow-Methods':'POST, OPTIONS'};
   if(r.request().method()==='OPTIONS')return r.fulfill({status:204,headers,body:''});
   count++;
   if(mode==='hang'){await new Promise(resolve=>setTimeout(resolve,250));return r.abort().catch(()=>{});}
   await new Promise(resolve=>setTimeout(resolve,10));
   return r.fulfill({headers,contentType:'text/event-stream',body:'data: '+JSON.stringify({stage:'ANSWER_READY',answer:'Live answer',abstained:false,citations:[]})+'\n\n'}).catch(()=>{});
  });
  await page.goto('http://preview.test/');await page.locator('#question').scrollIntoViewIfNeeded();
  await page.waitForFunction(()=>document.querySelector('#answerText').textContent.includes('[S4]'));
  assert.equal(count,0);assert.notEqual(await page.evaluate(()=>document.activeElement.id),'question');
  await page.locator('#sourcesDetails > summary').click();assert(await page.locator('#resultContent').isVisible());assert(await page.locator('#citations').isVisible());
  await page.reload();await page.locator('#question').scrollIntoViewIfNeeded();await page.locator('#previewNotice').waitFor();
  await page.locator('#question').fill('My interruption');await page.waitForTimeout(100);
  assert.equal(await page.locator('#question').inputValue(),'My interruption');assert.equal(count,0);
  await page.locator('#sendButton').dblclick();await page.waitForFunction(()=>!document.querySelector('#sendButton').disabled);assert.equal(count,1);
  await page.reload();await page.locator('#question').scrollIntoViewIfNeeded();await page.locator('#previewNotice').waitFor();
  await page.locator('#languageSelect').selectOption('en');await page.waitForTimeout(100);assert.equal(await page.locator('#question').inputValue(),'');assert.equal(count,1);
  mode='hang';await page.locator('#tryDemoButton').click();await page.waitForFunction(()=>document.querySelector('#previewNotice').textContent==='Saved response');
  assert((await page.locator('#errorBox').innerText()).includes('180'));assert.equal(count,2);
  mode='hang';await page.locator('#question').fill('Free text should not use a recording');await page.locator('#sendButton').click();await page.waitForFunction(()=>!document.querySelector('#sendButton').disabled);assert.equal(await page.locator('#resultContent').isVisible(),false);
  assert(failed);console.log('PASS reduced motion, mobile focus, interruption, language cancellation, double click, timeout and free-text isolation');
  await context.close();
 } finally {await browser.close()}
})().catch(e=>{console.error(e);process.exit(1)});
