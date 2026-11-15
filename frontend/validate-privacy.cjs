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
    if(url.pathname.endsWith('/usage/objections'))withdrawals.push(route.request().postDataJSON());
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
  await page.goto('https://athagon.tech/r4r-rag/');await page.locator('#question').waitFor();
  assert.equal(await page.locator('#r4r-ga-control').count(),0);
  assert.equal(await page.getByRole('button',{name:/Accept both|Aceptar ambas|Aceptar Google Analytics/}).count(),0);
  assert.equal(gaLoads,0);
  async function ask(scope=page){
   await scope.locator('#question').fill('Test renovation question');await scope.locator('#sendButton').click();
   await scope.locator('#answerText').getByText('Mock answer',{exact:true}).waitFor();
   const headers=requests.filter(r=>r.path.endsWith('/answers/stream')).at(-1).headers;
   for(const key of ['x-r4r-visitor','x-r4r-session','x-r4r-analytics-consent','x-r4r-history-consent'])assert(!headers[key]);
   assert(headers['x-r4r-request-id']);assert(headers['x-r4r-view-id']);return headers;
  }
  let headers=await ask();const view=headers['x-r4r-view-id'];assert(!headers['x-r4r-usage-opt-out']);
  await page.locator('.privacy-notice input[type=checkbox]').check();
  await page.waitForTimeout(100);assert(withdrawals.some(w=>w.references.includes(headers['x-r4r-request-id'])));
  headers=await ask();assert.equal(headers['x-r4r-view-id'],view);assert.equal(headers['x-r4r-usage-opt-out'],'1');
  assert(!JSON.stringify(withdrawals).includes('Test renovation question'));
  for(const key of ['r4r.analytics.visitor','r4r.privacy.v1','r4r.gaConsent.v1','r4r.lastQuestion'])
   assert.equal(await page.evaluate(k=>localStorage.getItem(k),key),null);
  assert.equal(await page.evaluate(()=>sessionStorage.getItem('r4r.analytics.session')),null);
  await page.reload();await page.locator('#question').waitFor();
  headers=await ask();assert.notEqual(headers['x-r4r-view-id'],view);assert(!headers['x-r4r-usage-opt-out']);
  assert.equal(gaLoads,0,'Athagon must never load Google Analytics');
  await page.goto('https://riansares4r.com/');
  assert.equal(gaLoads,0);assert.equal(await page.getByRole('button',{name:'Rechazar Google Analytics'}).count(),1);
  await page.getByRole('button',{name:'Rechazar Google Analytics'}).click();
  await page.locator('#r4r-assistant').scrollIntoViewIfNeeded();
  const frame=page.frameLocator('#r4r-assistant');await frame.locator('#question').waitFor();
  headers=await ask(frame);assert(!headers['x-r4r-usage-opt-out']);assert.equal(gaLoads,0);
  await page.locator('#r4r-ga-control').click();await page.getByRole('button',{name:'Aceptar Google Analytics'}).click();
  await page.waitForTimeout(100);assert.equal(gaLoads,1);headers=await ask(frame);
  await page.evaluate(()=>window.r4rPrivacy.withdraw());
  assert.equal(await page.evaluate(()=>window['ga-disable-G-41CEFW6B5P']),true);
  headers=await ask(frame);assert(!headers['x-r4r-usage-opt-out']);
  assert.equal(await page.evaluate(()=>localStorage.getItem('r4r.analytics.visitor')),null);
  await page.setViewportSize({width:390,height:900});
  await page.locator('#asistente').evaluate(el=>el.scrollIntoView({block:'start',behavior:'instant'}));
  await page.screenshot({path:'/tmp/r4r-final-privacy-mobile.png'});
  await context.close();console.log('PASS: no Athagon analytics/banner, no persistent IDs, ephemeral correlation, objection, GA reject/accept/withdraw independent of RAG');
 } finally{await browser.close();}
}
main().catch(error=>{console.error(error);process.exitCode=1;});
