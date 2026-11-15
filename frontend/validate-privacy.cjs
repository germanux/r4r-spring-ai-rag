// Offline browser verification against both actual site sources and the Angular build.
const {chromium}=require('playwright');
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'..');
async function main(){
 const browser=await chromium.launch({headless:true,args:['--no-sandbox'],...(process.env.CHROMIUM_PATH?{executablePath:process.env.CHROMIUM_PATH}:{})});
 try {
  const context=await browser.newContext();const requests=[];const withdrawals=[];let gaLoads=0;
  await context.route('**/*',async route=>{
   const url=new URL(route.request().url());
   if(url.hostname==='www.googletagmanager.com'){gaLoads++;return route.fulfill({contentType:'text/javascript',body:''});}
   if(url.hostname==='r4r-api.athagon.tech'){
    if(url.pathname.endsWith('/usage/withdraw'))withdrawals.push(route.request().postDataJSON());
    if(url.pathname.endsWith('/usage/views')||url.pathname.endsWith('/answers/stream')) requests.push({path:url.pathname,headers:route.request().headers()});
    if(url.pathname.endsWith('/answers/stream')) return route.fulfill({contentType:'text/event-stream',body:'data: '+JSON.stringify({stage:'ANSWER_READY',status:'ANSWERED',answer:'Mock answer',abstained:false,citations:[]})+'\n\n'});
    return route.fulfill({json:{models:[]}});
   }
   if(['athagon.tech','riansares4r.com'].includes(url.hostname)){
    let file;
    if(url.pathname.startsWith('/r4r-rag/')||url.pathname.startsWith('/asistente/')){
     const relative=url.pathname.replace(/^\/(r4r-rag|asistente)\//,'')||'index.html';
     file=path.join(root,'frontend/dist/r4r-frontend/browser',relative);
    }else file=path.join(url.hostname==='athagon.tech'?'/home/german/Desarrollo/athagon-web.git/html-static':'/home/german/Desarrollo/R4R/R4R.git/dist',url.pathname==='/'?'index.html':url.pathname);
    if(fs.existsSync(file)&&fs.statSync(file).isFile())return route.fulfill({contentType:({'.html':'text/html','.js':'text/javascript','.css':'text/css','.png':'image/png','.svg':'image/svg+xml','.ico':'image/x-icon'})[path.extname(file)]||'application/octet-stream',body:fs.readFileSync(file)});
    return route.fulfill({status:404,body:''});
   }
   return route.abort();
  });
  const page=await context.newPage();
  await page.goto('https://athagon.tech/r4r-rag/');
  await page.getByRole('button',{name:/Reject both|Rechazar ambas/}).click();
  assert.equal(await page.evaluate(()=>localStorage.getItem('r4r.analytics.visitor')),null);
  async function ask(scope=page){
   await scope.locator('#question').fill('Test renovation question');await scope.locator('#sendButton').click();
   await scope.locator('#answerText').getByText('Mock answer',{exact:true}).waitFor();
   return requests.filter(r=>r.path.endsWith('/answers/stream')).at(-1).headers;
  }
  let headers=await ask();assert(!headers['x-r4r-visitor']);assert(!headers['x-r4r-history-consent']);
  await page.evaluate(()=>window.r4rPrivacy.open());await page.getByRole('button',{name:/Accept both|Aceptar ambas/}).click();
  headers=await ask();const athagonVisitor=headers['x-r4r-visitor'];assert(athagonVisitor);assert.equal(headers['x-r4r-history-consent'],'2026-11-v1');
  await page.reload();await page.locator('#question').waitFor();
  assert.equal((await page.evaluate(()=>window.r4rPrivacy.headers()))['X-R4R-Visitor'],athagonVisitor);
  await ask(); // Recent references live only in page memory, not persistent tracking storage.
  // A page view uses the same event reference when consent changes: no second visit.
  const views=requests.filter(r=>r.path.endsWith('/usage/views'));
  assert(views.some(v=>v.headers['x-r4r-visitor']===athagonVisitor));
  await page.evaluate(()=>window.r4rPrivacy.withdraw());
  assert.equal(await page.evaluate(()=>localStorage.getItem('r4r.analytics.visitor')),null);
  headers=await ask();assert(!headers['x-r4r-visitor']);assert(!headers['x-r4r-history-consent']);
  assert.equal(await page.evaluate(()=>localStorage.getItem('r4r.lastQuestion')),null);
  await page.waitForTimeout(100);assert(withdrawals.some(w=>w.analytics&&w.history&&w.references.length>0));
  assert(!JSON.stringify(withdrawals).includes('Test renovation question'));
  await page.goto('https://riansares4r.com/');
  assert.equal(gaLoads,0,'GA must not load before consent');
  await page.getByRole('button',{name:'Rechazar ambas'}).click();
  await page.locator('#r4r-assistant').scrollIntoViewIfNeeded();
  const frame=page.frameLocator('#r4r-assistant');await frame.locator('#question').waitFor();
  headers=await ask(frame);assert(!headers['x-r4r-visitor']);assert.equal(gaLoads,0);
  await page.evaluate(()=>window.r4rPrivacy.open());
  // History alone does not authorize analytics or Google Analytics.
  await page.locator('[data-purpose="history"]').check();
  await page.getByRole('button',{name:'Guardar selección'}).click();
  headers=await ask(frame);assert(headers['x-r4r-history-consent']);assert(!headers['x-r4r-visitor']);assert.equal(gaLoads,0);
  await page.evaluate(()=>window.r4rPrivacy.open());await page.getByRole('button',{name:'Aceptar ambas'}).click();
  headers=await ask(frame);assert(headers['x-r4r-visitor']);assert.notEqual(headers['x-r4r-visitor'],athagonVisitor);
  await page.waitForTimeout(100);assert.equal(gaLoads,1);
  await page.evaluate(()=>window.r4rPrivacy.withdraw());
  assert.equal(await page.evaluate(()=>window['ga-disable-G-41CEFW6B5P']),true);
  headers=await ask(frame);assert(!headers['x-r4r-visitor']);assert(!headers['x-r4r-history-consent']);
  await page.setViewportSize({width:390,height:900});
  await page.locator('#asistente').evaluate(el=>el.scrollIntoView({block:'start',behavior:'instant'}));
  await page.screenshot({path:'/tmp/r4r-history-privacy-mobile.png'});
  await context.close();console.log('PASS: reject, accept, independent purposes, withdrawal, recurrence, first-party isolation, GA gating, embedded headers');
 } finally{await browser.close();}
}
main().catch(error=>{console.error(error);process.exitCode=1;});
