import { chromium } from 'playwright';
import { createServer } from 'node:http';
import { readFile, writeFile, stat } from 'node:fs/promises';
import { resolve, extname } from 'node:path';
const root=resolve(process.argv[2]);
const types={'.html':'text/html','.js':'text/javascript','.css':'text/css','.svg':'image/svg+xml','.json':'application/json','.pdf':'application/pdf'};
const server=createServer(async(req,res)=>{try{const path=decodeURIComponent(new URL(req.url,'http://localhost').pathname).replace(/^\/v2-ng\//,'');const file=resolve(root,path||'index.html');if(!file.startsWith(root+'/'))throw Error();res.setHeader('Content-Type',types[extname(file)]||'application/octet-stream');res.end(await readFile(file));}catch{res.statusCode=404;res.end();}});
await new Promise(r=>server.listen(0,'127.0.0.1',r));
let browser;
try{browser=await chromium.launch({headless:true,...(process.env.CHROMIUM_PATH?{executablePath:process.env.CHROMIUM_PATH}:{}),args:['--no-sandbox']});const page=await browser.newPage({locale:'es'});const errors=[];page.on('pageerror',e=>errors.push(e.message));await page.route('**/*',route=>new URL(route.request().url()).hostname==='127.0.0.1'?route.continue():route.abort());await page.goto(`http://127.0.0.1:${server.address().port}/v2-ng/?prerender=1`);await page.locator('app-root h1').waitFor();if(errors.length)throw Error(errors.join('\n'));const html=await page.content();if(!html.includes('Asistente de IA para reformas')||!html.includes('Aquí aparecerá tu respuesta'))throw Error('Empty prerender');await writeFile(resolve(root,'index.html'),html+'\n');}finally{await browser?.close();server.close();}
