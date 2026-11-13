const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto');
const {spawnSync}=require('node:child_process');
const root=path.resolve(__dirname,'..');
const catalogue=JSON.parse(fs.readFileSync(path.join(root,'docs/catalogue.json')));
const legacy=JSON.parse(fs.readFileSync(path.join(root,'fixtures/legacy-pdfs.json')));
const hash=bytes=>crypto.createHash('sha256').update(bytes).digest('hex');
function checkBytes(bytes) {
 assert.equal(bytes.subarray(0,5).toString(),'%PDF-','Expected a PDF, not an error or HTML document');
 assert.match(bytes.subarray(-64).toString(),/%%EOF/,'Truncated PDF');
}
function checkLocal(doc) {
 assert.equal(doc.url,'docs/'+doc.source.replace(/\.md$/,'.pdf'));
 assert.ok(!doc.url.includes('..'));
 const file=path.join(root,doc.url),bytes=fs.readFileSync(file);checkBytes(bytes);
 assert.equal(hash(bytes),doc.pdfSha256);
 assert.equal(hash(fs.readFileSync(path.resolve(root,'../knowledge',doc.source))),doc.sha256);
 const result=spawnSync('pdfinfo',[file],{encoding:'utf8'});assert.equal(result.status,0,result.stderr);
 const count=Number(/^Pages:\s+(\d+)/m.exec(result.stdout)?.[1]);assert.ok(count>0);
 for(const page of Object.values(doc.sectionPages))assert.ok(Number.isInteger(page)&&page>=1&&page<=count);
}
async function checkHttp(base,url,expectedHash) {
 const response=await fetch(new URL(url,base.endsWith('/')?base:base+'/'),{signal:AbortSignal.timeout(15000)});
 assert.equal(response.status,200,`${url}: HTTP ${response.status}`);
 assert.match(response.headers.get('content-type')||'',/^application\/pdf\b/i,`${url}: wrong content type`);
 const bytes=Buffer.from(await response.arrayBuffer());checkBytes(bytes);assert.equal(hash(bytes),expectedHash,`${url}: unexpected content`);
}
test('41 stable IDs have matching ES/EN PDF sources, valid content and real page targets',()=>{
 assert.equal(Object.keys(catalogue).length,41);const urls=new Set();
 for(const [id,pair] of Object.entries(catalogue)) {
  assert.deepEqual(Object.keys(pair).sort(),['en','es']);
  for(const [lang,doc] of Object.entries(pair)) {
   assert.ok(doc.source.startsWith(lang+'/'));assert.equal(path.basename(doc.source).match(/^R4R_\d+/)[0],id);
   assert.ok(doc.title && !doc.title.endsWith('.md'));checkLocal(doc);urls.add(doc.url);
  }
 }
 assert.equal(urls.size,82);
 const html=fs.readFileSync(path.join(root,'index.html'),'utf8');
 assert.deepEqual(JSON.parse(/<script id="documentSources"[^>]*>(.*?)<\/script>/s.exec(html)[1]),catalogue);
});
test('legacy public PDFs are unchanged',()=>{
 assert.equal(Object.keys(legacy).length,8);
 for(const [url,digest] of Object.entries(legacy)){const bytes=fs.readFileSync(path.join(root,url));checkBytes(bytes);assert.equal(hash(bytes),digest);}
});
test('broken, HTML and truncated PDF links are rejected',()=>{
 assert.throws(()=>checkBytes(Buffer.from('<html>error</html>')));
 assert.throws(()=>checkBytes(Buffer.from('%PDF-1.7 truncated')));
 assert.throws(()=>checkLocal({...catalogue.R4R_80.en,source:'en/missing.md',url:'docs/en/missing.pdf'}));
});
test('HTTP error pages and misleading PDF MIME types are rejected',async()=>{
 const original=global.fetch;
 try {
  global.fetch=async()=>new Response('not found',{status:404});await assert.rejects(checkHttp('https://test.invalid/','missing.pdf','hash'),/HTTP 404/);
  global.fetch=async()=>new Response('<html>error</html>',{status:200,headers:{'Content-Type':'application/pdf'}});await assert.rejects(checkHttp('https://test.invalid/','error.pdf','hash'),/Expected a PDF/);
 } finally {global.fetch=original;}
});
if(process.env.R4R_PDF_BASE_URL) test('all 90 public PDF URLs return the correct PDF content',async()=>{
 const assets=[...Object.values(catalogue).flatMap(pair=>Object.values(pair).map(doc=>[doc.url,doc.pdfSha256])),...Object.entries(legacy)];
 const failures=[];let next=0;
 await Promise.all(Array.from({length:4},async()=>{while(next<assets.length){const [url,digest]=assets[next++];try{await checkHttp(process.env.R4R_PDF_BASE_URL,url,digest)}catch(error){failures.push({url,error:error.message})}}}));
 const report={base:process.env.R4R_PDF_BASE_URL,total:assets.length,passed:assets.length-failures.length,failures};
 if(process.env.R4R_PDF_REPORT)fs.writeFileSync(process.env.R4R_PDF_REPORT,JSON.stringify(report,null,2)+'\n');
 assert.deepEqual(failures,[]);
});
module.exports={checkBytes,checkLocal,checkHttp};
