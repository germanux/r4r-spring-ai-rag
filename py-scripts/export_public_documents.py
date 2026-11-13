#!/usr/bin/env python3
"""Publish readable, branded bilingual documents and a stable-ID catalogue; no corpus edits."""
from pathlib import Path
from html import escape
import re,json,hashlib
import markdown
from bs4 import BeautifulSoup
ROOT=Path(__file__).resolve().parents[1]
WEB=ROOT/'front-athagon-r4r-static'
def export():
    catalogue={}
    for lang in ['es','en']:
        for source in sorted((ROOT/'knowledge'/lang).rglob('R4R_*.md')):
            key=re.match(r'R4R_\d+',source.name).group()
            if lang in catalogue.get(key,{}):raise ValueError(f'Duplicate stable ID: {key}/{lang}')
            text=source.read_text();body=re.sub(r'\A---\n.*?\n---\s*\n','',text,flags=re.S)
            soup=BeautifulSoup(markdown.markdown(body,extensions=['tables','fenced_code']),'html.parser')
            for node in soup.find_all(['script','iframe','style','object','embed']):node.decompose()
            for node in soup.find_all(True):
                for attr in list(node.attrs):
                    if attr not in ['href','title']:del node[attr]
                if node.get('href') and not node['href'].startswith(('https://','http://','#')):del node['href']
            title=soup.h1.get_text() if soup.h1 else source.stem
            for heading in soup.find_all(re.compile('^h[1-6]$')):
                n=re.match(r'(\d+(?:\.\d+)*)',heading.get_text())
                if n:heading['id']='section-'+n[1].replace('.','-')
            relative=f'docs/{lang}/{source.stem}.html';target=WEB/relative;target.parent.mkdir(parents=True,exist_ok=True)
            target.write_text(f'''<!doctype html><html lang="{lang}"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta name="robots" content="noindex,nofollow"><title>{escape(title)}</title><style>body{{margin:auto;max-width:900px;padding:24px;font:17px/1.65 system-ui;color:#203445;background:#f6f9fb}}header{{display:flex;align-items:center;gap:24px;border-bottom:2px solid #d76538}}header img{{width:110px}}h1{{line-height:1.25}}h2,h3{{margin-top:2em}}table{{display:block;overflow:auto;border-collapse:collapse}}td,th{{border:1px solid #ccd6df;padding:8px}}pre{{overflow:auto}}a{{color:#165a7e}}@media print{{body{{background:white;font-size:11pt}}}}@media(max-width:600px){{h1{{font-size:1.65em}}}} </style><header><img src="../riansares-4r-logo.png" alt="Riansares 4R"><a href="../../index.html">{'Volver al asistente' if lang=='es' else 'Back to the assistant'}</a></header><main>{soup}</main></html>''')
            catalogue.setdefault(key,{})[lang]={'source':str(source.relative_to(ROOT/'knowledge')),'title':title,'url':relative,'sections':[h.get('id') for h in soup.find_all(re.compile('^h[1-6]$')) if h.get('id')],'sha256':hashlib.sha256(source.read_bytes()).hexdigest()}
    (WEB/'docs/catalogue.json').write_text(json.dumps(catalogue,ensure_ascii=False,indent=2)+'\n')
    p=WEB/'index.html';html=p.read_text();html=re.sub(r'<script id="documentSources".*?</script>\n','',html,flags=re.S)
    html=html.replace('<script id="recordedExamples"','<script id="documentSources" type="application/json">'+json.dumps(catalogue,ensure_ascii=False).replace('<','\\u003c')+'</script>\n<script id="recordedExamples"')
    p.write_text(html);print(f'{sum(map(len,catalogue.values()))} documents, {len(catalogue)} stable IDs')
if __name__=='__main__':export()
