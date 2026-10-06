"""Render every PDF page and make contact sheets for visual inspection."""
from pathlib import Path
import json
import pypdfium2 as pdfium
from PIL import Image, ImageDraw
from pypdf import PdfReader

root=Path(__file__).resolve().parents[1]
out=root/'tmp/manual-pdf/render'
out.mkdir(parents=True,exist_ok=True)
pdfpath=root/'output/pdf/知识挑战赛后台操作手册.pdf'
doc=pdfium.PdfDocument(pdfpath)
for i,page in enumerate(doc):
    im=page.render(scale=1.4).to_pil().convert('RGB')
    im.save(out/f'page-{i+1:03}.png')
for start in range(0,len(doc),12):
    sheet=Image.new('RGB',(1800,1800),'#ddd')
    draw=ImageDraw.Draw(sheet)
    for j in range(start,min(len(doc),start+12)):
        im=Image.open(out/f'page-{j+1:03}.png')
        im.thumbnail((430,560))
        x=((j-start)%4)*450+(450-im.width)//2
        y=((j-start)//4)*600+25
        sheet.paste(im,(x,y))
        draw.text((x,y-20),f'Page {j+1}',fill='black')
    sheet.save(out/f'grid-{start//12:02}.jpg',quality=90)
reader=PdfReader(pdfpath)
text='\n'.join(p.extract_text() or '' for p in reader.pages)
assert all((p.extract_text() or '').strip() for p in reader.pages), 'Empty page'
assert '\u767d\u75f4' not in text
manifest=json.loads((root/'tmp/manual-pdf/manifest.json').read_text(encoding='utf-8'))
assert len(reader.pages)==manifest['pages']
print(json.dumps(manifest,ensure_ascii=False))
print('All pages rendered; no empty pages; user-facing wording checked.')
