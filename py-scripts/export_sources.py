#!/usr/bin/env python3
"""Export the eight public knowledge documents. Requires reportlab, markdown, bs4."""
from pathlib import Path
import re
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
    styles[name].fontSize=size;styles[name].leading=leading;styles[name].spaceBefore=14;styles[name].spaceAfter=8
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
    canvas.setFont('Body',7.5);canvas.drawRightString(w-44,h-62,'BASE DOCUMENTAL · RAG CON FUENTES')
    canvas.setStrokeColor(colors.HexColor('#db6b36'));canvas.line(44,h-100,w-44,h-100)
    canvas.setStrokeColor(colors.HexColor('#dce3e8'));canvas.line(44,39,w-44,39)
    canvas.setFont('Body',7);canvas.setFillColor(colors.HexColor('#607184'))
    canvas.drawString(44,26,'Riansares 4R · Documentación de referencia')
    canvas.drawRightString(w-44,26,str(doc.page))
    canvas.restoreState()

def blocks(nodes,story):
    for node in nodes:
        if isinstance(node,NavigableString):continue
        if node.name in ['h1','h2','h3','h4','h5','h6']:
            style='Title' if node.name=='h1' else 'Heading'+str(min(int(node.name[1]),4))
            story.append(Paragraph(inline(node),styles[style]))
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

if __name__=='__main__':
    OUT.mkdir(parents=True,exist_ok=True)
    for source in SOURCES:
        raw=source.read_text()
        # YAML ingestion metadata stays in the MD; all editorial content is preserved.
        raw=re.sub(r'\A---\n.*?\n---\s*\n','',raw,flags=re.S)
        soup=BeautifulSoup(markdown.markdown(raw,extensions=['tables','fenced_code']),'html.parser')
        story=[];blocks(soup.children,story)
        target=OUT/(source.stem+'.pdf')
        doc=SimpleDocTemplate(str(target),pagesize=(595.28,841.89),leftMargin=44,rightMargin=44,topMargin=116,bottomMargin=54,title=soup.h1.get_text() if soup.h1 else source.stem,author='Riansares 4R')
        doc.build(story,onFirstPage=header,onLaterPages=header)
        print(target.relative_to(ROOT))
