// No real LAN or LLM traffic: reproduce the NAS's insecure HTTP origin with routed local assets.
const {chromium}=require('playwright');
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
(async()=>{
 const browser=await chromium.launch({headless:true,args:['--no-sandbox'],...(process.env.CHROMIUM_PATH?{executablePath:process.env.CHROMIUM_PATH}:{})});
 try{
  const page=await browser.newPage();const errors=[];let headers;
  page.on('pageerror',error=>errors.push(error.message));
  const root=path.join(__dirname,'dist/r4r-frontend/browser');
  await page.route('**/*',route=>{
   const url=new URL(route.request().url());
   if(url.hostname==='192.0.2.10'){
    const relative=url.pathname.replace(/^\/r4r-rag\//,'')||'index.html';
    const file=path.join(root,relative);
    if(!fs.existsSync(file))return route.fulfill({status:404});
    return route.fulfill({contentType:({'.html':'text/html','.js':'text/javascript','.css':'text/css','.svg':'image/svg+xml','.png':'image/png'})[path.extname(file)]||'application/octet-stream',body:fs.readFileSync(file)});
   }
   if(url.pathname.endsWith('/answers/stream')){
    headers=route.request().headers();
    return route.fulfill({contentType:'text/event-stream',body:'data: '+JSON.stringify({stage:'ANSWER_READY',status:'ANSWERED',answer:'LAN HTTP test answer',abstained:false,citations:[]})+'\n\n'});
   }
   return route.abort();
  });
  await page.goto('http://192.0.2.10/r4r-rag/?prerender=1');
  assert.equal(await page.evaluate(()=>isSecureContext),false);
  assert.equal(await page.evaluate(()=>typeof crypto.randomUUID),'undefined');
  assert.equal(await page.evaluate(()=>typeof crypto.getRandomValues),'function');
  await page.locator('#question').fill('How should I prepare a renovation?');
  await page.locator('#sendButton').click();
  await page.locator('#answerText').getByText('LAN HTTP test answer').waitFor();
  const uuid=/^[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$/;
  assert.match(headers['x-r4r-request-id'],uuid);assert.match(headers['x-r4r-view-id'],uuid);
  assert.notEqual(headers['x-r4r-request-id'],headers['x-r4r-view-id']);
  assert.deepEqual(errors,[]);console.log('PASS: non-secure LAN HTTP renders and answers with CSPRNG UUID v4 references');
 }finally{await browser.close();}
})().catch(error=>{console.error(error);process.exitCode=1;});
