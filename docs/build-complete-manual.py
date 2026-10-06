"""Build the current screenshot handbook, editable source and offline HTML."""
from pathlib import Path
import importlib.util, json, math, shutil, hashlib
from html import escape
from PIL import Image as PILImage
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.lib import colors
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle
from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, PageBreak, Image, Flowable
from pypdf import PdfReader

ROOT = Path(__file__).resolve().parents[1]
DOCS = ROOT / 'docs'
IMAGES = DOCS / 'manual-latest-images'
TMP = ROOT / 'tmp/manual-pdf'
TMP.mkdir(parents=True, exist_ok=True)
OUT = ROOT / 'output/pdf'
OUT.mkdir(parents=True, exist_ok=True)
spec = importlib.util.spec_from_file_location('content', DOCS / 'manual-content.py')
content = importlib.util.module_from_spec(spec)
spec.loader.exec_module(content)
PAGES = content.PAGES
DETAILS = [p for p in PAGES if p[1] in {
    'account-create', 'account-edit', 'award-issue', 'create-mother', 'create-venue',
    'entry-config', 'field-config', 'pool-editor', 'scoring-ranked', 'screen-manage',
    'screen-register', 'screen-template', 'set-random', 'set-select', 'soup-editor',
    'sync-questions', 'sync-sets', 'question-single', 'question-multiple',
    'question-text', 'text-grade', 'lottery-entry', 'participant-detail'}]
# Deduplicate shared dialog screenshots.
unique_details = {}
for page in DETAILS:
    unique_details.setdefault(page[1],page)
DETAILS = list(unique_details.values())
pdfmetrics.registerFont(TTFont('CN', 'C:/Windows/Fonts/msyh.ttc'))
pdfmetrics.registerFont(TTFont('CNBold', 'C:/Windows/Fonts/msyhbd.ttc'))
CW = A4[0] - 84
FRAME = A4[1] - 42 - 50 - 12
STYLES = {
    'title': ParagraphStyle('title', fontName='CNBold', fontSize=30, leading=44, spaceAfter=20, wordWrap='CJK'),
    'heading': ParagraphStyle('heading', fontName='CNBold', fontSize=19, leading=29, spaceAfter=9, wordWrap='CJK'),
    'body': ParagraphStyle('body', fontName='CN', fontSize=11, leading=18, spaceAfter=7, wordWrap='CJK'),
    'small': ParagraphStyle('small', fontName='CN', fontSize=9, leading=15, spaceAfter=6, wordWrap='CJK', textColor=colors.HexColor('#555555')),
    'toc': ParagraphStyle('toc', fontName='CN', fontSize=10, leading=18, spaceAfter=3, wordWrap='CJK'),
}

def para(text, style='body'):
    return Paragraph(escape(text), STYLES[style])

def crop_picture(name, title='', detail=False):
    source = IMAGES / (name + '.png')
    im = PILImage.open(source).convert('RGB')
    w, h = im.size
    if name in {p[1] for p in DETAILS}:
        x1, x2 = int(w*.32), int(w*.69)
        ys = []
        for y in range(90, min(h-15, 980)):
            bright = sum(min(im.getpixel((x,y))) > 210 for x in range(x1+25,x2-25,8))
            if bright / len(range(x1+25,x2-25,8)) > .72:
                ys.append(y)
        # The bright central dialog is separated from the dimmed background.
        box = (x1,max(80,min(ys)-15),x2,min(h-10,max(ys)+15)) if ys else (x1,110,x2,min(h-15,920))
    elif name == 'login':
        box = (int(w*.57), 150, w-40, min(h-20, 850))
    elif name == 'settings' and '成员' in title:
        box = (280, 1475, w-25, 1705)
    elif name == 'settings' and '存储' in title:
        box = (660, 420, w-25, 725)
    elif name == 'settings':
        box = (280, 220, w-25, 975)
    elif name == 'soup-host-current':
        box = (265, 0, w-15, h)
    elif name == 'soup-console' and '编辑' in title:
        box = (280, 1760, 885, h-30)
    elif name == 'soup-console':
        box = (275, 150, w-25, min(h-30, 1100))
    elif name.startswith('player-') or name.startswith('screen-'):
        box = (0, 0, w, h)
    else:
        box = (265, 90, w-25, min(h-25, 1500)) if w > 1000 else (0, 0, w, h)
    im = im.crop(box)
    variant = hashlib.sha256(title.encode('utf-8')).hexdigest()[:8]
    path = TMP / (name + '-' + variant + ('-detail' if detail else '') + '.png')
    im.save(path)
    return path, im.size

class Bookmark(Flowable):
    def __init__(self, key, title):
        super().__init__()
        self.key, self.title = key, title
        self.width = self.height = 0
    def draw(self):
        self.canv.bookmarkPage(self.key)
        self.canv.addOutlineEntry(self.title, self.key, 0, False)

entries_count = len(PAGES) + len(DETAILS)
toc_per_page = 28
toc_pages = math.ceil(entries_count / toc_per_page)
first = 2 + toc_pages
total = 1 + toc_pages + entries_count
detail_page = {p[1]: first + len(PAGES) + i for i, p in enumerate(DETAILS)}
story = [para('知识挑战赛\n完整图文操作手册'.replace('\n', ' · '), 'title'), Spacer(1, 15),
         para('管理员、主持人、大屏工作人员与玩家均可使用。'),
         para('根据当前仓库版本和本地演示界面制作 · 2026 年 10 月 6 日'),
         Spacer(1, 20), para('第一次使用，从这里开始', 'heading'),
         para('准备活动：先登录，创建母活动与会场，勾选现场需要的功能，再准备题目、汤稿、奖品和人员。'),
         para('现场主持：选对会场后，按答题、海龟汤或抽奖章节逐步操作；大屏请提前配对并测试。'),
         para('观众参加：直接阅读玩家章节，用手机扫描工作人员提供的会场二维码。'),
         para('读图方法：每个操作专题都有当前界面截图和按顺序排列的步骤。较复杂的表单另有放大图；点击 PDF 目录可跳转。离线网页版可点击图片查看原图。'),
         para('截图中的“知识嘉年华”“一号会场”和玩家资料均为本地演示数据。操作时请换成自己的活动、会场和资料。'),
         para('重要习惯：每次点击之前，先看上方会场名称；保存后等待成功提示。结束活动前先完成评分和领奖核对。'),
         Spacer(1, 12), para(f'共 {len(PAGES)} 个操作专题 · {len(set(p[1] for p in PAGES))} 张界面截图 · {len(DETAILS)} 页表单放大图', 'small')]
toc_entries = [(f'{i+1:02d}  {p[0]}', f'chapter-{i}', first+i) for i,p in enumerate(PAGES)]
toc_entries += [(f'放大图：{p[0]}', f'detail-{i}', first+len(PAGES)+i) for i,p in enumerate(DETAILS)]
for n in range(toc_pages):
    story += [PageBreak(), para('操作目录' + (f'（{n+1}/{toc_pages}）' if toc_pages>1 else ''), 'heading'), para('点击条目跳转。附录放大图对应正文中的复杂表单。', 'small')]
    for title, key, page in toc_entries[n*toc_per_page:(n+1)*toc_per_page]:
        story.append(Paragraph(f'<link href="#{key}" color="black">{escape(title)}　……　{page}</link>', STYLES['toc']))
for i,(title,name,intro,steps,result) in enumerate(PAGES):
    head = [para(f'{i+1:02d}  {title}', 'heading'), para(intro)]
    tail = [para(f'{j+1}. {s}') for j,s in enumerate(steps)] + [para('完成后：' + result)]
    if name in detail_page:
        tail += [para(f'看不清表单文字时，翻到第 {detail_page[name]} 页查看放大图。', 'small')]
    used = sum(x.wrap(CW,FRAME)[1] + x.getSpaceAfter() + x.getSpaceBefore() for x in head+tail)
    max_h = min(320, FRAME - used - 16)
    if max_h < 90:
        raise ValueError(f'Chapter too long: {title}, available image height {max_h}')
    path,(w,h) = crop_picture(name,title)
    ratio = min(CW/w, max_h/h)
    story += [PageBreak(), Bookmark(f'chapter-{i}',title)] + head + [Image(str(path),width=w*ratio,height=h*ratio),Spacer(1,9)] + tail
for i,(title,name,*_) in enumerate(DETAILS):
    path,(w,h) = crop_picture(name,title,True)
    ratio = min(CW/w, 600/h)
    story += [PageBreak(), Bookmark(f'detail-{i}','放大图：'+title), para('表单放大图 · '+title,'heading'),
              para('对应步骤见正文同名章节。长表单需在实际页面向下滚动；本图保留当前可见操作区域。','small'),
              Image(str(path),width=w*ratio,height=h*ratio)]

def footer(canvas,doc):
    canvas.setFont('CN',8)
    canvas.setFillColor(colors.HexColor('#555555'))
    canvas.drawString(42,25,'知识挑战赛 · 完整图文操作手册 · 2026-10-06')
    canvas.drawRightString(A4[0]-42,25,f'{doc.page} / {total}')

pdf = OUT / '知识挑战赛后台操作手册.pdf'
doc = SimpleDocTemplate(str(pdf),pagesize=A4,leftMargin=42,rightMargin=42,topMargin=42,bottomMargin=50,
                        title='知识挑战赛完整图文操作手册',author='知识挑战赛',pageCompression=1)
doc.build(story,onFirstPage=footer,onLaterPages=footer)
actual = len(PdfReader(pdf).pages)
assert actual == total, f'Unexpected overflow: expected {total}, actual {actual}'
shutil.copyfile(pdf,ROOT/'public/knowledge-match-user-manual.pdf')

md = ['# 知识挑战赛完整图文操作手册', '', '版本日期：2026 年 10 月 6 日。截图来自当前版本的本地演示环境。', '',
      '每次操作前先确认会场名称。点击图片可查看原图。前端更新另见《前端替换说明》。', '', '## 目录', '']
html_parts = ['<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">',
              '<title>知识挑战赛完整图文操作手册</title><style>body{font-family:"Microsoft YaHei",sans-serif;max-width:1050px;margin:35px auto;padding:0 24px;font-size:18px;line-height:1.85;color:#222}h1,h2{color:#111;line-height:1.5}img{display:block;max-width:100%;height:auto;margin:20px auto;border:1px solid #ddd}section{margin:45px 0;padding-top:15px;border-top:1px solid #ddd}li{margin:12px 0}a{color:#075d99}nav{columns:2}p.note{font-size:15px;color:#555}@media(max-width:650px){nav{columns:1}body{font-size:17px}}@media print{section{break-before:page}nav{columns:2}}</style>',
              '<h1 id="top">知识挑战赛完整图文操作手册</h1><p>版本日期：2026 年 10 月 6 日。全部截图来自当前版本的本地演示环境。</p><p>点击图片可打开原图并放大。每次操作前先确认会场名称，填写后点击保存并等待成功提示。</p><nav><ol>']
for i,p in enumerate(PAGES):
    md.append(f'{i+1}. [{p[0]}](#操作-{i+1})')
    html_parts.append(f'<li><a href="#chapter-{i}">{escape(p[0])}</a></li>')
html_parts.append('</ol></nav>')
for i,(title,name,intro,steps,result) in enumerate(PAGES):
    link=f'manual-latest-images/{name}.png'
    md += ['',f'<a id="操作-{i+1}"></a>',f'## {i+1:02d} {title}','',intro,'',f'![{title}]({link})','']
    md += [f'{j+1}. {s}' for j,s in enumerate(steps)] + ['', '完成后：'+result, '']
    html_parts += [f'<section id="chapter-{i}"><h2>{i+1:02d} {escape(title)}</h2><p>{escape(intro)}</p>',
                   f'<a href="{link}" target="_blank"><img loading="lazy" src="{link}" alt="{escape(title)}"></a><ol>']
    html_parts += [f'<li>{escape(s)}</li>' for s in steps]
    html_parts += ['</ol><p><strong>完成后：</strong>'+escape(result)+'</p><a href="#top">回到目录</a></section>']
html_parts.append('</html>')
(DOCS/'用户操作手册.md').write_text('\n'.join(md),encoding='utf-8')
(DOCS/'用户操作手册.html').write_text('\n'.join(html_parts),encoding='utf-8')
manifest={'pages':actual,'chapters':len(PAGES),'screenshots':len(set(p[1] for p in PAGES)),'detail_pages':len(DETAILS)}
(TMP/'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(manifest,ensure_ascii=False))

