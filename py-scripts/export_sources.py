#!/usr/bin/env python3
"""Export bilingual public knowledge documents. Requires reportlab, markdown, bs4."""
from pathlib import Path
import re
import json
import hashlib
import subprocess
from xml.sax.saxutils import escape
import markdown
from bs4 import BeautifulSoup, NavigableString
from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, Table, TableStyle, HRFlowable
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib import colors
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.lib.utils import ImageReader

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'front-athagon-r4r-static/docs'
SOURCES = sorted(p for p in (ROOT / 'knowledge').rglob('R4R_*.md'))
FONT = Path('/usr/share/fonts/truetype/dejavu')
for name, file in [('Body','DejaVuSans.ttf'),('Body-Bold','DejaVuSans-Bold.ttf'),('Body-Oblique','DejaVuSans-Oblique.ttf'),('Body-BoldOblique','DejaVuSans-BoldOblique.ttf')]:
    pdfmetrics.registerFont(TTFont(name,str(FONT/file)))
pdfmetrics.registerFontFamily('Body',normal='Body',bold='Body-Bold',italic='Body-Oblique',boldItalic='Body-BoldOblique')
styles=getSampleStyleSheet()
for style in styles.byName.values():
    style.fontName='Body'; style.textColor=colors.HexColor('#203445')
styles['BodyText'].fontSize=9; styles['BodyText'].leading=14; styles['BodyText'].spaceAfter=6; styles['BodyText'].allowWidows=0; styles['BodyText'].allowOrphans=0
for name,size,leading in [('Title',23,30),('Heading1',18,24),('Heading2',14,20),('Heading3',11,16),('Heading4',10,15)]:
    styles[name].fontSize=size;styles[name].leading=leading;styles[name].spaceBefore=14;styles[name].spaceAfter=8;styles[name].keepWithNext=True
styles.add(ParagraphStyle('Cell',parent=styles['BodyText'],fontSize=7,leading=10,spaceAfter=0,wordWrap='LTR'))
styles.add(ParagraphStyle('BulletBody',parent=styles['BodyText'],leftIndent=13,firstLineIndent=-9))
logo=ImageReader(str(OUT/'riansares-4r-logo.png'))
def inline(node):
    if isinstance(node,NavigableString):return escape(str(node))
    inner=''.join(inline(c) for c in node.children)
    if node.name in ['strong','b']:return '<b>'+inner+'</b>'
    if node.name in ['em','i']:return '<i>'+inner+'</i>'
    if node.name=='br':return '<br/>'
    if node.name=='a':
        href=node.get('href','')
        return '<a href="'+escape(href,{'"':'&quot;'})+'" color="#087c89">'+inner+'</a>' if href.startswith(('https://','http://')) else inner
    return inner

def header(canvas,doc):
    w,h=doc.pagesize
    canvas.saveState()
    canvas.drawImage(logo,44,h-89,width=82,height=59,preserveAspectRatio=True,mask='auto')
    canvas.setFont('Body-Bold',9);canvas.setFillColor(colors.HexColor('#203445'))
    canvas.drawRightString(w-44,h-48,'RIANSARES 4R')
    canvas.setFont('Body',7.5);canvas.drawRightString(w-44,h-62,'REFERENCE DOCUMENTS · SOURCE-BACKED AI' if doc.language == 'en' else 'BASE DOCUMENTAL · RAG CON FUENTES')
    canvas.setStrokeColor(colors.HexColor('#db6b36'));canvas.line(44,h-100,w-44,h-100)
    canvas.setStrokeColor(colors.HexColor('#dce3e8'));canvas.line(44,39,w-44,39)
    canvas.setFont('Body',7);canvas.setFillColor(colors.HexColor('#607184'))
    canvas.drawString(44,26,'Riansares 4R · Reference documentation' if doc.language == 'en' else 'Riansares 4R · Documentación de referencia')
    canvas.drawRightString(w-44,26,str(doc.page))
    canvas.restoreState()

def blocks(nodes,story):
    for node in nodes:
        if isinstance(node,NavigableString):continue
        if node.name in ['h1','h2','h3','h4','h5','h6']:
            style='Title' if node.name=='h1' else 'Heading'+str(min(int(node.name[1]),4))
            paragraph=Paragraph(inline(node),styles[style])
            number=re.match(r'(\d+(?:\.\d+)*)',node.get_text())
            paragraph.section_id='section-'+number[1].replace('.','-') if number else None
            story.append(paragraph)
        elif node.name=='p':story.append(Paragraph(inline(node),styles['BodyText']))
        elif node.name in ['ul','ol']:
            for i,li in enumerate(node.find_all('li',recursive=False),1):
                body=''.join(inline(c) for c in li.children if getattr(c,'name',None) not in ['ul','ol'])
                story.append(Paragraph((str(i)+'. ' if node.name=='ol' else '• ')+body,styles['BulletBody']))
                blocks(li.find_all(['ul','ol'],recursive=False),story)
        elif node.name=='table':
            rows=[[Paragraph(inline(c),styles['Cell']) for c in tr.find_all(['th','td'],recursive=False)] for tr in node.find_all('tr')]
            if not rows:continue
            count=max(map(len,rows));width=507/count
            widths=[40,65,112,58,44,44,44,100] if count==8 else [width]*count
            table=Table(rows,colWidths=widths,repeatRows=1,hAlign='LEFT')
            table.setStyle(TableStyle([('BACKGROUND',(0,0),(-1,0),colors.HexColor('#e9eff3')),('ROWBACKGROUNDS',(0,1),(-1,-1),[colors.white,colors.HexColor('#f7f9fa')]),('VALIGN',(0,0),(-1,-1),'TOP'),('LINEBELOW',(0,0),(-1,0),.7,colors.HexColor('#db6b36')),('LEFTPADDING',(0,0),(-1,-1),6),('RIGHTPADDING',(0,0),(-1,-1),6),('TOPPADDING',(0,0),(-1,-1),6),('BOTTOMPADDING',(0,0),(-1,-1),6)]))
            story.extend([table,Spacer(1,10)])
        elif node.name=='hr':story.append(HRFlowable(width='100%',color=colors.HexColor('#dce3e8')))
        elif node.name=='pre':story.append(Paragraph(escape(node.get_text()).replace('\n','<br/>'),styles['Cell']))
        else:blocks(node.children,story)

class EvidenceDocument(SimpleDocTemplate):
    def afterFlowable(self, flowable):
        key=getattr(flowable,'section_id',None)
        if not key:
            return
        if key in self.section_pages:
            self.ambiguous_sections.add(key)
        else:
            self.section_pages[key]=self.page


def export_all():
    """Publish a PDF-only catalogue only after every generated PDF validates."""
    OUT.mkdir(parents=True,exist_ok=True)
    catalogue={}
    for source in SOURCES:
        relative=source.relative_to(ROOT/'knowledge')
        language=relative.parts[0]
        if language not in ('es','en'):
            raise ValueError(f'Unsupported corpus language: {relative}')
        identity=re.match(r'R4R_\d+',source.name).group()
        if language in catalogue.get(identity,{}):
            raise ValueError(f'Duplicate source ID: {identity}/{language}')
        raw=source.read_text()
        raw=re.sub(r'\A---\n.*?\n---\s*\n','',raw,flags=re.S)
        soup=BeautifulSoup(markdown.markdown(raw,extensions=['tables','fenced_code']),'html.parser')
        title=soup.h1.get_text() if soup.h1 else source.stem
        story=[];blocks(soup.children,story)
        target=OUT/relative.with_suffix('.pdf')
        target.parent.mkdir(parents=True,exist_ok=True)
        doc=EvidenceDocument(str(target),pagesize=(595.28,841.89),leftMargin=44,rightMargin=44,topMargin=116,bottomMargin=54,title=title,author='Riansares 4R',pageCompression=1)
        doc.language=language;doc.section_pages={};doc.ambiguous_sections=set()
        doc.build(story,onFirstPage=header,onLaterPages=header)
        if not target.read_bytes().startswith(b'%PDF-'):
            raise ValueError(f'Invalid PDF signature: {target}')
        subprocess.run(['pdfinfo',str(target)],check=True,stdout=subprocess.DEVNULL,stderr=subprocess.PIPE)
        pages={key:value for key,value in doc.section_pages.items() if key not in doc.ambiguous_sections}
        catalogue.setdefault(identity,{})[language]={
            'source':relative.as_posix(),'title':title,'url':target.relative_to(ROOT/'front-athagon-r4r-static').as_posix(),
            'sections':list(pages),'sectionPages':pages,
            'sha256':hashlib.sha256(source.read_bytes()).hexdigest(),
            'pdfSha256':hashlib.sha256(target.read_bytes()).hexdigest()
        }
        print(target.relative_to(ROOT),flush=True)
    if len(catalogue)!=41 or any(set(pair)!= {'es','en'} for pair in catalogue.values()):
        raise ValueError('Expected 41 unique sources in both ES and EN')
    web=ROOT/'front-athagon-r4r-static'
    serialized=json.dumps(catalogue,ensure_ascii=False,indent=2)+'\n'
    temporary=OUT/'catalogue.json.tmp';temporary.write_text(serialized);temporary.replace(OUT/'catalogue.json')
    entry=web/'index.html';html=entry.read_text()
    embedded=json.dumps(catalogue,ensure_ascii=False).replace('<','\\u003c')
    html,count=re.subn(r'(<script id="documentSources"[^>]*>).*?(</script>)',lambda match:match[1]+embedded+match[2],html,flags=re.S)
    if count!=1:
        raise ValueError('Expected one embedded source catalogue')
    temporary=entry.with_suffix('.html.tmp');temporary.write_text(html);temporary.replace(entry)
    return catalogue


if __name__=='__main__':
    export_all()
