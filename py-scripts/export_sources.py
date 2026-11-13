#!/usr/bin/env python3
"""Riansares 4R bilingual Markdown → branded PDF + compatible source catalogue.

Drop-in replacement for py-scripts/export_sources.py. The existing
py-scripts/export_public_documents.py entry point continues to work unchanged.

Dependencies: reportlab, markdown, beautifulsoup4; pdfinfo for PDF validation.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import subprocess
from pathlib import Path
from xml.sax.saxutils import escape

import markdown
from bs4 import BeautifulSoup, NavigableString
from reportlab.lib import colors
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.utils import ImageReader
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import (
    HRFlowable, KeepTogether, PageBreak, Paragraph, SimpleDocTemplate,
    Spacer, Table, TableStyle,
)

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'front-athagon-r4r-static' / 'docs'
SOURCES = sorted(p for p in (ROOT / 'knowledge').rglob('R4R_*.md'))

# Official R4R website palette (site-blueprint.css / style.css).
PAPER = colors.HexColor('#EEE7D8')
WHITE = colors.HexColor('#FFFAF0')
INK = colors.HexColor('#201F1B')
BLUEPRINT = colors.HexColor('#1D3346')
MUTED = colors.HexColor('#5C574F')
TERRACOTTA = colors.HexColor('#C22A1B')
TEAL = colors.HexColor('#234D59')
BORDER = colors.HexColor('#DED4BF')

AUTHOR = 'Germán Caballero'
LOCATION_ES = 'Tarancón · C. P. 16400 · Cuenca · España'
LOCATION_EN = 'Tarancón · 16400 · Cuenca · Spain'
LINKS = (('riansares.com', 'https://riansares.com'), ('athagon.tech', 'https://athagon.tech'))

FONT_DIR = Path('/usr/share/fonts/truetype/dejavu')
for name, filename in (
    ('Body', 'DejaVuSans.ttf'), ('Body-Bold', 'DejaVuSans-Bold.ttf'),
    ('Body-Oblique', 'DejaVuSans-Oblique.ttf'),
    ('Body-BoldOblique', 'DejaVuSans-BoldOblique.ttf'),
):
    pdfmetrics.registerFont(TTFont(name, str(FONT_DIR / filename)))
pdfmetrics.registerFontFamily('Body', normal='Body', bold='Body-Bold',
                              italic='Body-Oblique', boldItalic='Body-BoldOblique')

styles = getSampleStyleSheet()
for s in styles.byName.values():
    s.fontName = 'Body'
    s.textColor = INK
    s.wordWrap = 'LTR'
styles['BodyText'].fontSize = 9
styles['BodyText'].leading = 14.5
styles['BodyText'].spaceAfter = 7
styles['BodyText'].allowWidows = 0
styles['BodyText'].allowOrphans = 0
for name, size, leading in [('Title', 18, 26), ('Heading1', 15, 21),
                            ('Heading2', 12, 18), ('Heading3', 10, 15), ('Heading4', 9, 14)]:
    s = styles[name]
    s.fontSize, s.leading = size, leading
    s.spaceBefore, s.spaceAfter, s.keepWithNext = 12, 7, True
    s.textColor = BLUEPRINT
styles.add(ParagraphStyle('R4RCell', parent=styles['BodyText'], fontSize=7, leading=10,
                          spaceAfter=0))
styles.add(ParagraphStyle('R4RBullet', parent=styles['BodyText'], leftIndent=15,
                          firstLineIndent=-11))
styles.add(ParagraphStyle('R4RCode', parent=styles['BodyText'], fontSize=7.2,
                          leading=11, backColor=PAPER, leftIndent=6, rightIndent=6))


def locate_logo(root: Path, out: Path) -> Path:
    # Use the actual transparent corporate asset; don't redraw the logo.
    candidates = [out / 'riansares-4r-logo.png', root / 'front-athagon-r4r-static' / 'docs' / 'riansares-4r-logo.png',
                  root / 'front-athagon-r4r-static' / 'assets' / 'riansares-4r-logo.png',
                  root / 'assets' / 'r4r_logo.png', root / 'r4r_logo.png',
                  Path(__file__).with_name('r4r_logo.png')]
    for candidate in candidates:
        if candidate.is_file():
            return candidate
    raise FileNotFoundError('R4R logo missing. Expected front-athagon-r4r-static/docs/riansares-4r-logo.png')


def inline(node):
    if isinstance(node, NavigableString):
        return escape(str(node))
    inner = ''.join(inline(c) for c in node.children)
    if node.name in ('strong', 'b'):
        return '<b>' + inner + '</b>'
    if node.name in ('em', 'i'):
        return '<i>' + inner + '</i>'
    if node.name == 'code':
        return '<font face="Body">' + inner + '</font>'
    if node.name == 'br':
        return '<br/>'
    if node.name == 'a':
        href = node.get('href', '')
        if href.startswith(('https://', 'http://')):
            return '<a href="' + escape(href, {'"': '&quot;'}) + '" color="#234D59">' + inner + '</a>'
    return inner


def blocks(nodes, story):
    for node in nodes:
        if isinstance(node, NavigableString):
            continue
        if node.name in ('h1', 'h2', 'h3', 'h4', 'h5', 'h6'):
            style = 'Title' if node.name == 'h1' else 'Heading' + str(min(int(node.name[1]), 4))
            p = Paragraph(inline(node), styles[style])
            number = re.match(r'(\d+(?:\.\d+)*)', node.get_text(strip=True))
            p.section_id = 'section-' + number.group(1).replace('.', '-') if number else None
            story.append(p)
        elif node.name == 'p':
            story.append(Paragraph(inline(node), styles['BodyText']))
        elif node.name in ('ul', 'ol'):
            for i, li in enumerate(node.find_all('li', recursive=False), 1):
                body = ''.join(inline(c) for c in li.children
                               if getattr(c, 'name', None) not in ('ul', 'ol'))
                story.append(Paragraph((str(i) + '. ' if node.name == 'ol' else '• ') + body,
                                       styles['R4RBullet']))
                blocks(li.find_all(['ul', 'ol'], recursive=False), story)
        elif node.name == 'table':
            rows = [[Paragraph(inline(c), styles['R4RCell'])
                     for c in tr.find_all(['th', 'td'], recursive=False)]
                    for tr in node.find_all('tr')]
            if not rows:
                continue
            count = max(map(len, rows))
            widths = [40, 65, 112, 58, 44, 44, 44, 100] if count == 8 else [507 / count] * count
            tbl = Table(rows, colWidths=widths, repeatRows=1, hAlign='LEFT')
            tbl.setStyle(TableStyle([
                ('BACKGROUND', (0, 0), (-1, 0), PAPER),
                ('ROWBACKGROUNDS', (0, 1), (-1, -1), [colors.white, WHITE]),
                ('TEXTCOLOR', (0, 0), (-1, 0), BLUEPRINT),
                ('LINEBELOW', (0, 0), (-1, 0), 1, TERRACOTTA),
                ('BOX', (0, 0), (-1, -1), .4, BORDER),
                ('VALIGN', (0, 0), (-1, -1), 'TOP'),
                ('LEFTPADDING', (0, 0), (-1, -1), 6),
                ('RIGHTPADDING', (0, 0), (-1, -1), 6),
                ('TOPPADDING', (0, 0), (-1, -1), 6),
                ('BOTTOMPADDING', (0, 0), (-1, -1), 6),
            ]))
            story.extend((tbl, Spacer(1, 10)))
        elif node.name == 'hr':
            story.append(HRFlowable(width='100%', color=BORDER))
        elif node.name == 'pre':
            content = escape(node.get_text())
            # Preserve intentional whitespace and prevent long code from overflowing the page.
            for rawline in content.splitlines() or ['']:
                for i in range(0, max(len(rawline), 1), 90):
                    story.append(Paragraph(rawline[i:i+90].replace(' ', '&#160;') or '&#160;', styles['R4RCode']))
        else:
            blocks(node.children, story)


def fit_logo(canvas, logo, x, y, max_width, max_height):
    iw, ih = logo.getSize()
    scale = min(max_width / iw, max_height / ih)
    w, h = iw * scale, ih * scale
    canvas.drawImage(logo, x, y + (max_height-h)/2, w, h,
                     preserveAspectRatio=True, mask='auto')


def draw_link(canvas, text, url, x, y, color=TEAL):
    canvas.setFont('Body-Bold', 7.3)
    canvas.setFillColor(color)
    canvas.drawString(x, y, text)
    width = pdfmetrics.stringWidth(text, 'Body-Bold', 7.3)
    canvas.linkURL(url, (x, y-2, x+width, y+10), relative=0)


def cover(canvas, doc):
    w, h = doc.pagesize
    canvas.saveState()
    canvas.setFillColor(WHITE)
    canvas.rect(0, 0, w, h, fill=1, stroke=0)
    canvas.setFillColor(TERRACOTTA)
    canvas.rect(0, 0, 9, h, fill=1, stroke=0)
    canvas.rect(45, h-14, w-90, 5, fill=1, stroke=0)

    fit_logo(canvas, doc.logo, 49, h-180, 151, 126)
    canvas.setFont('Body-Bold', 9)
    canvas.setFillColor(MUTED)
    canvas.drawString(342, h-80, 'DOCUMENTACIÓN TÉCNICA' if doc.language=='es' else 'TECHNICAL DOCUMENTATION')
    canvas.setFillColor(TEAL)
    canvas.setFont('Body-Bold', 12)
    canvas.drawString(342, h-108, 'IA APLICADA · RAG' if doc.language=='es' else 'APPLIED AI · RAG')
    canvas.setFont('Body', 8.5)
    canvas.setFillColor(MUTED)
    canvas.drawString(342, h-135, 'EDICIÓN 2026' if doc.language=='es' else '2026 EDITION')
    canvas.setStrokeColor(TERRACOTTA)
    canvas.setLineWidth(1.7)
    canvas.line(49, h-207, w-48, h-207)

    # Cover title wraps from the actual Markdown H1; no invented replacement title.
    title_style = ParagraphStyle('CoverTitle', fontName='Body-Bold', fontSize=20.5,
                                leading=29, textColor=BLUEPRINT)
    title = Paragraph(escape(doc.document_title), title_style)
    _, ht = title.wrap(w-105, 155)
    title.drawOn(canvas, 49, h-262-ht)
    sub_y = h-290-ht
    canvas.setFillColor(MUTED)
    canvas.setFont('Body', 9.5)
    canvas.drawString(49, sub_y, 'DOCUMENTACIÓN DE REFERENCIA PARA EL ASISTENTE DE IA' if doc.language=='es' else 'REFERENCE DOCUMENTATION FOR THE AI ASSISTANT')
    canvas.setStrokeColor(BORDER)
    canvas.line(49, sub_y-24, w-48, sub_y-24)

    box_top = min(sub_y-75, 268)
    canvas.setFillColor(PAPER)
    canvas.rect(49, 93, w-97, box_top-93, fill=1, stroke=0)
    canvas.setFillColor(TEAL)
    canvas.rect(49, 93, 5, box_top-93, fill=1, stroke=0)
    canvas.setFillColor(BLUEPRINT)
    canvas.setFont('Body-Bold', 10)
    canvas.drawString(66, box_top-27, 'RIANSARES 4R')
    canvas.setFillColor(MUTED)
    canvas.setFont('Body', 8.1)
    canvas.drawString(66, box_top-49, LOCATION_ES if doc.language=='es' else LOCATION_EN)
    canvas.drawString(66, box_top-69, ('Documentación e IA: ' if doc.language=='es' else 'Documentation & AI: ') + AUTHOR)
    draw_link(canvas, LINKS[0][0], LINKS[0][1], 66, 114)
    draw_link(canvas, LINKS[1][0], LINKS[1][1], 213, 114)
    canvas.setFont('Body', 7.5)
    canvas.setFillColor(MUTED)
    canvas.drawString(49, 58, 'R4R_' + doc.identity.split('_',1)[1] + '  |  ' + ('Fuentes verificables' if doc.language=='es' else 'Verifiable sources'))
    canvas.restoreState()


def header(canvas, doc):
    w, h = doc.pagesize
    canvas.saveState()
    canvas.setFillColor(TERRACOTTA)
    canvas.rect(0, 0, 5, h, fill=1, stroke=0)
    fit_logo(canvas, doc.logo, 44, h-90, 79, 64)
    canvas.setFillColor(BLUEPRINT)
    canvas.setFont('Body-Bold', 9)
    canvas.drawRightString(w-44, h-47, 'RIANSARES 4R')
    canvas.setFont('Body', 7.3)
    canvas.setFillColor(MUTED)
    canvas.drawRightString(w-44, h-63,
       'DOCUMENTACIÓN PARA IA · FUENTES CONSULTABLES' if doc.language=='es'
       else 'AI DOCUMENTATION · VERIFIABLE SOURCES')
    canvas.setStrokeColor(TERRACOTTA)
    canvas.setLineWidth(1.2)
    canvas.line(44, h-101, w-44, h-101)
    canvas.setStrokeColor(BORDER)
    canvas.line(44, 49, w-44, 49)
    canvas.setFont('Body', 7)
    canvas.setFillColor(MUTED)
    canvas.drawString(44, 34, AUTHOR + ' · Tarancón (16400), Cuenca')
    draw_link(canvas, LINKS[0][0], LINKS[0][1], 44, 20)
    draw_link(canvas, LINKS[1][0], LINKS[1][1], 154, 20)
    canvas.setFillColor(MUTED)
    canvas.drawRightString(w-44, 28, str(doc.page))
    canvas.restoreState()


class EvidenceDocument(SimpleDocTemplate):
    def afterFlowable(self, flowable):
        key = getattr(flowable, 'section_id', None)
        if key:
            if key in self.section_pages:
                self.ambiguous_sections.add(key)
            else:
                self.section_pages[key] = self.page


def export_one(source: Path, root: Path, output: Path, logo: ImageReader):
    relative = source.relative_to(root/'knowledge')
    language = relative.parts[0]
    if language not in ('es', 'en'):
        raise ValueError(f'Unsupported corpus language: {relative}')
    match = re.match(r'R4R_\d+', source.name)
    if not match:
        raise ValueError(f'Unsupported file name: {source.name}')
    identity = match.group()
    raw = source.read_text(encoding='utf-8')
    raw = re.sub(r'\A---\s*\n.*?\n---\s*\n', '', raw, flags=re.S)
    soup = BeautifulSoup(markdown.markdown(raw, extensions=['tables', 'fenced_code']), 'html.parser')
    title = soup.h1.get_text(' ',strip=True) if soup.h1 else source.stem
    story = [Spacer(1,1), PageBreak()]
    blocks(soup.children, story)
    target = output/relative.with_suffix('.pdf')
    target.parent.mkdir(parents=True, exist_ok=True)
    doc = EvidenceDocument(str(target), pagesize=A4, leftMargin=44, rightMargin=44,
                           topMargin=119, bottomMargin=63, title=title, author=AUTHOR,
                           pageCompression=1)
    doc.logo, doc.identity, doc.document_title, doc.language = logo, identity, title, language
    doc.section_pages, doc.ambiguous_sections = {}, set()
    doc.build(story, onFirstPage=cover, onLaterPages=header)
    if not target.read_bytes().startswith(b'%PDF-'):
        raise ValueError(f'Invalid PDF signature: {target}')
    subprocess.run(['pdfinfo', str(target)], check=True, stdout=subprocess.DEVNULL,
                   stderr=subprocess.PIPE)
    pages = {key:value for key,value in doc.section_pages.items()
             if key not in doc.ambiguous_sections}
    return identity, language, {
        'source': relative.as_posix(), 'title': title,
        'url': target.relative_to(root/'front-athagon-r4r-static').as_posix()
               if target.is_relative_to(root/'front-athagon-r4r-static') else target.as_posix(),
        'sections': list(pages), 'sectionPages': pages,
        'sha256': hashlib.sha256(source.read_bytes()).hexdigest(),
        'pdfSha256': hashlib.sha256(target.read_bytes()).hexdigest(),
    }


def export_all(root: Path | None = None, out: Path | None = None, sources: list[Path] | None = None,
               update_catalogue: bool = True):
    root = (root or ROOT).resolve()
    out = (out or (root/'front-athagon-r4r-static/docs')).resolve()
    sources = sources if sources is not None else sorted((root/'knowledge').rglob('R4R_*.md'))
    logo = ImageReader(str(locate_logo(root, out)))
    catalogue = {}
    for source in sources:
        identity, language, data = export_one(source, root, out, logo)
        if language in catalogue.get(identity, {}):
            raise ValueError(f'Duplicate source ID: {identity}/{language}')
        catalogue.setdefault(identity, {})[language] = data
        print(out / Path(data['source']).with_suffix('.pdf'), flush=True)
    if update_catalogue:
        if len(catalogue) != 41 or any(set(pair) != {'es','en'} for pair in catalogue.values()):
            raise ValueError('Expected 41 unique sources in both ES and EN')
        serialized = json.dumps(catalogue, ensure_ascii=False, indent=2)+'\n'
        temporary = out/'catalogue.json.tmp'
        temporary.write_text(serialized, encoding='utf-8')
        temporary.replace(out/'catalogue.json')
        entry = root/'front-athagon-r4r-static/index.html'
        html = entry.read_text(encoding='utf-8')
        embedded = json.dumps(catalogue,ensure_ascii=False).replace('<','\\u003c')
        html, count = re.subn(r'(<script id="documentSources"[^>]*>).*?(</script>)',
                 lambda match: match[1]+embedded+match[2], html, flags=re.S)
        if count != 1:
            raise ValueError('Expected one embedded source catalogue')
        temporary = entry.with_suffix('.html.tmp')
        temporary.write_text(html, encoding='utf-8')
        temporary.replace(entry)
    return catalogue


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description='Publish R4R Markdown as bilingual branded PDFs.')
    parser.add_argument('--root', type=Path, default=ROOT, help='Repository root')
    parser.add_argument('--single', type=Path, help='Preview a single corpus Markdown file (no catalogue update)')
    parser.add_argument('--output-dir', type=Path, help='Override PDF output directory')
    args = parser.parse_args()
    base = args.root.resolve()
    if args.single:
        src = args.single.resolve()
        export_all(base, args.output_dir, [src], update_catalogue=False)
    else:
        catalogue = export_all(base, args.output_dir)
        print(f'{sum(map(len,catalogue.values()))} PDFs, {len(catalogue)} stable IDs')
