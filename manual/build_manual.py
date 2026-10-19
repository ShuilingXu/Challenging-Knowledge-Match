"""Build the staff quick manual (DOCX) for the quiz / turtle-soup console."""
import os
from docx import Document
from docx.enum.section import WD_SECTION
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.enum.text import WD_ALIGN_PARAGRAPH, WD_LINE_SPACING, WD_TAB_ALIGNMENT
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Mm, Pt, RGBColor

OUT = "manual/现场控场使用手册.docx"
IMG = "manual/final"

INK = RGBColor(0x3B, 0x2C, 0x27)
MUTED = RGBColor(0x6B, 0x5A, 0x52)
TEAL = RGBColor(0x0F, 0x76, 0x6E)
ACCENT_HEX = "0F766E"
CREAM_HEX = "F7F2EC"
FONT = "微软雅黑"


def style_run(run, size=10.5, bold=False, color=INK, font=FONT):
    run.font.name = font
    run.font.size = Pt(size)
    run.font.bold = bold
    run.font.color.rgb = color
    rpr = run._element.get_or_add_rPr()
    rfonts = rpr.find(qn("w:rFonts"))
    if rfonts is None:
        rfonts = OxmlElement("w:rFonts")
        rpr.append(rfonts)
    rfonts.set(qn("w:eastAsia"), font)
    rfonts.set(qn("w:ascii"), font)
    rfonts.set(qn("w:hAnsi"), font)


def para(text="", size=10.5, bold=False, color=INK, align=None, space_before=0, space_after=4,
         line=1.45, indent=0):
    p = doc.add_paragraph()
    pf = p.paragraph_format
    pf.space_before = Pt(space_before)
    pf.space_after = Pt(space_after)
    pf.line_spacing = line
    if indent:
        pf.left_indent = Mm(indent)
    if align is not None:
        p.alignment = align
    if text:
        style_run(p.add_run(text), size, bold, color)
    return p


def rule(color=ACCENT_HEX, size=6, space_after=8):
    p = doc.add_paragraph()
    p.paragraph_format.space_before = Pt(2)
    p.paragraph_format.space_after = Pt(space_after)
    ppr = p._p.get_or_add_pPr()
    pbdr = OxmlElement("w:pBdr")
    bottom = OxmlElement("w:bottom")
    bottom.set(qn("w:val"), "single")
    bottom.set(qn("w:sz"), str(size))
    bottom.set(qn("w:space"), "1")
    bottom.set(qn("w:color"), color)
    pbdr.append(bottom)
    ppr.append(pbdr)
    return p


def eyebrow(text):
    return para(text, size=9, bold=True, color=TEAL, space_before=0, space_after=2, line=1.2)


def h1(text, num=None):
    p = doc.add_paragraph()
    p.paragraph_format.space_before = Pt(14)
    p.paragraph_format.space_after = Pt(2)
    p.paragraph_format.line_spacing = 1.1
    p.paragraph_format.keep_with_next = True
    if num:
        style_run(p.add_run(num + "  "), 21, True, TEAL)
    style_run(p.add_run(text), 21, True, INK)
    return p


def h2(text):
    p = doc.add_paragraph()
    p.paragraph_format.space_before = Pt(10)
    p.paragraph_format.space_after = Pt(3)
    p.paragraph_format.keep_with_next = True
    style_run(p.add_run(text), 12, True, INK)
    return p


def lead(text):
    """One-line summary under a section heading."""
    p = doc.add_paragraph()
    p.paragraph_format.space_before = Pt(2)
    p.paragraph_format.space_after = Pt(8)
    p.paragraph_format.line_spacing = 1.4
    p.paragraph_format.left_indent = Mm(0)
    style_run(p.add_run(text), 10.5, False, MUTED)
    return p


def step(mark, text, note=None):
    p = doc.add_paragraph()
    pf = p.paragraph_format
    pf.space_before = Pt(0)
    pf.space_after = Pt(7)
    pf.line_spacing = 1.45
    pf.left_indent = Mm(9)
    pf.first_line_indent = Mm(-9)
    style_run(p.add_run(mark + "  "), 13, True, TEAL)
    style_run(p.add_run(text), 10.5, False, INK)
    if note:
        style_run(p.add_run("　" + note), 9.5, False, MUTED)
    return p


def bullet(text, color=MUTED):
    p = doc.add_paragraph()
    pf = p.paragraph_format
    pf.space_before = Pt(0)
    pf.space_after = Pt(3)
    pf.line_spacing = 1.4
    pf.left_indent = Mm(6)
    pf.first_line_indent = Mm(-4)
    style_run(p.add_run("· "), 10.5, True, TEAL)
    style_run(p.add_run(text), 10, False, color)
    return p


def shade(cell, hex_color):
    tcpr = cell._tc.get_or_add_tcPr()
    shd = OxmlElement("w:shd")
    shd.set(qn("w:val"), "clear")
    shd.set(qn("w:color"), "auto")
    shd.set(qn("w:fill"), hex_color)
    tcpr.append(shd)


def cell_margins(cell, top=120, bottom=120, left=180, right=180):
    tcpr = cell._tc.get_or_add_tcPr()
    mar = OxmlElement("w:tcMar")
    for tag, val in (("top", top), ("bottom", bottom), ("start", left), ("end", right)):
        el = OxmlElement(f"w:{tag}")
        el.set(qn("w:w"), str(val))
        el.set(qn("w:type"), "dxa")
        mar.append(el)
    tcpr.append(mar)


def tips_box(title, lines):
    table = doc.add_table(rows=1, cols=1)
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    table.autofit = False
    row = table.rows[0]
    trpr = row._tr.get_or_add_trPr()
    cant = OxmlElement("w:cantSplit")
    trpr.append(cant)
    cell = table.cell(0, 0)
    cell.width = Mm(170)
    shade(cell, CREAM_HEX)
    cell_margins(cell)
    p = cell.paragraphs[0]
    p.paragraph_format.space_after = Pt(3)
    p.paragraph_format.line_spacing = 1.2
    style_run(p.add_run(title), 10, True, TEAL)
    for line in lines:
        bp = cell.add_paragraph()
        bp.paragraph_format.space_after = Pt(2)
        bp.paragraph_format.line_spacing = 1.35
        bp.paragraph_format.left_indent = Mm(4)
        bp.paragraph_format.first_line_indent = Mm(-3)
        style_run(bp.add_run("· "), 10, True, TEAL)
        style_run(bp.add_run(line), 9.5, False, INK)
    return table


def picture(path, width_mm, caption, new_page=False):
    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.space_before = Pt(4)
    p.paragraph_format.space_after = Pt(2)
    p.paragraph_format.keep_with_next = True
    if new_page:
        p.paragraph_format.page_break_before = True
    p.add_run().add_picture(os.path.join(IMG, path), width=Mm(width_mm))
    cap = doc.add_paragraph()
    cap.alignment = WD_ALIGN_PARAGRAPH.CENTER
    cap.paragraph_format.space_after = Pt(8)
    style_run(cap.add_run(caption), 8.5, False, MUTED)
    return p


def add_page_number(paragraph):
    run = paragraph.add_run()
    fld_begin = OxmlElement("w:fldChar")
    fld_begin.set(qn("w:fldCharType"), "begin")
    instr = OxmlElement("w:instrText")
    instr.set(qn("xml:space"), "preserve")
    instr.text = " PAGE "
    fld_sep = OxmlElement("w:fldChar")
    fld_sep.set(qn("w:fldCharType"), "separate")
    txt = OxmlElement("w:t")
    txt.text = "1"
    fld_end = OxmlElement("w:fldChar")
    fld_end.set(qn("w:fldCharType"), "end")
    run._r.append(fld_begin)
    run._r.append(instr)
    run._r.append(fld_sep)
    run._r.append(txt)
    run._r.append(fld_end)
    style_run(run, 9, False, MUTED)


def footer(section):
    p = section.footer.paragraphs[0]
    p.paragraph_format.space_before = Pt(4)
    p.paragraph_format.tab_stops.clear_all()
    p.paragraph_format.tab_stops.add_tab_stop(Mm(170), alignment=WD_TAB_ALIGNMENT.RIGHT)
    style_run(p.add_run("答题 · 海龟汤　现场控场使用手册"), 8.5, False, MUTED)
    p.add_run("\t")
    style_run(p.add_run("第 "), 9, False, MUTED)
    add_page_number(p)
    style_run(p.add_run(" 页"), 9, False, MUTED)


doc = Document()
section = doc.sections[0]
section.page_width = Mm(210)
section.page_height = Mm(297)
section.left_margin = Mm(20)
section.right_margin = Mm(20)
section.top_margin = Mm(18)
section.bottom_margin = Mm(16)

base = doc.styles["Normal"]
base.font.name = FONT
base.font.size = Pt(10.5)
base.font.color.rgb = INK
base.element.rPr.rFonts.set(qn("w:eastAsia"), FONT)
base.paragraph_format.space_after = Pt(4)

doc.styles["Footer"].paragraph_format.tab_stops.clear_all()
footer(section)

# ---------------------------------------------------------------- cover
para("", space_after=0, line=1.0)
para("", space_after=0, line=1.0)
eyebrow("现场工作人员 · 使用手册")
para("答题与海龟汤", size=38, bold=True, color=INK, space_before=6, space_after=0, line=0.98)
para("控场手册", size=38, bold=True, color=INK, space_before=0, space_after=10, line=0.98)
rule(size=10, space_after=14)
para("一页一步，照着点就行。", size=14, color=TEAL, space_after=6)
para("只讲两件事：怎么把题发出去、怎么把海龟汤放上大屏。",
     size=10.5, color=MUTED, space_after=34)

# mini table of contents
for num, name, hint in (
    ("01", "答题（抢答）控场", "选题、开题、判分、公布答案"),
    ("02", "海龟汤控场", "展示汤面、公开线索、放汤底"),
):
    p = doc.add_paragraph()
    p.paragraph_format.space_before = Pt(0)
    p.paragraph_format.space_after = Pt(8)
    p.paragraph_format.line_spacing = 1.2
    style_run(p.add_run(num + "　"), 11, True, TEAL)
    style_run(p.add_run(name), 11.5, True, INK)
    style_run(p.add_run("　　" + hint), 9, False, MUTED)

para("", space_after=26, line=1.0)
para("适用账号　活动工作人员（event-staff）", size=9.5, color=MUTED, space_after=3)
para("站点　　　match.soyorin.love", size=9.5, color=MUTED, space_after=3)
para("版本　　　2026 年 10 月", size=9.5, color=MUTED, space_after=24)
para("截图取自本场真实控场页面，界面按钮以现场显示为准。", size=8.5, color=MUTED, space_after=0)

doc.add_page_break()

# ---------------------------------------------------------------- before you start
h1("开始之前", "00")
para("三件事确认好，后面就不会点错。", size=10.5, color=MUTED, space_after=8)
step("1", "打开 match.soyorin.love，用工作人员账号登录。")
step("2", "看右上角「选择会场」，确认是本场。手册截图用的是「2026 长崎素世万圣茶会 测试场」。")
step("3", "左侧菜单只要记两个：答题控场、海龟汤控场。其余菜单这次不用管。")
step("4", "看状态：答题控场页顶部有「现场状态」；海龟汤页看左上角标签（候场 / 推理进行中 / 答案已公开）。")

tips_box("先记住这三句", [
    "页面上的按钮点下去就生效，没有“再确认一次”，点之前看清按钮。",
    "所有结果都会显示在顶部「现场状态」，拿不准就先看它。",
    "大屏没反应，先找负责大屏的同事确认：设备是否已配对、模板是否已下发。",
])

# ---------------------------------------------------------------- quiz
h1("答题（抢答）控场", "01")
lead("你在这页做四件事：选题 → 开题 → 看谁先抢到 → 公布答案。")
step("①", "选题。在「待发布题目」下拉里选今天这一题，右边出现“已选题目，可跳题并开始”。")
step("②", "开题。点「开题并计时」。玩家手机和本场大屏同时出现题目，30 秒倒计时开始。")
step("③", "看抢答。「抢答顺序」里按时间排好名字，第 1 位就是最快的人。点「判对并计分」或「判错并计分」给他记分。")
step("④", "公布答案。点「公布答案」，正确答案和答案图会同步到玩家手机和大屏。")
step("⑤", "下一题。点「下一题并开始」按顺序继续。")
para("收尾：整轮答完点「发布积分榜」，排名才会出现在大屏和手机。",
     size=9.5, color=MUTED, space_before=2, space_after=6, indent=9)

picture("quiz-buttons.png", 160, "图 1　底部这一排按钮，就是现场要点的全部操作（编号对应上面步骤）", new_page=True)
picture("quiz-overview.png", 146, "图 2　抢答控场整页：① 选题　② 开题　③ 抢答顺序　④ 公布答案　⑤ 下一题")

tips_box("容易踩的三个坑", [
    "30 秒到了会自动截止，但答案不会自己出来，仍要手动点「公布答案」。",
    "灰按钮不是坏了：活动没开始、已结束，或这一步还没轮到，都会变灰。",
    "跳题：先在「待发布题目」里选另一题，右边出现“已选题目，可跳题并开始”后，「跳题并开始」才会亮。",
])

# ---------------------------------------------------------------- soup
doc.add_page_break()
h1("海龟汤控场", "02")
lead("把故事放上大屏，按现场节奏一条条放线索，最后放汤底。")
step("①", "展示汤面。点「展示汤面」上屏；上方可暂停、翻页，每页停留 8–40 秒。")
step("②", "公开线索。点「公开这条线索」立刻上屏，「撤回这条线索」收回；顺序跟着推理走。")
step("③", "放汤底。打开「答案 · 汤底」开关才显示真相，再点一次收回。")
step("④", "临时补线索。写在「临时添加线索」，点「添加并立即公开」直接上屏。")
step("⑤", "看预览。右侧「大屏内容预览」就是观众看到的画面。")

picture("soup-control.png", 165, "图 3　展示控制：① 展示汤面；下面一行是汤底开关")
picture("soup-board.png", 92, "图 4　线索板：点「公开这条线索」上屏，点「撤回这条线索」收回")

tips_box("海龟汤的三个注意", [
    "线索一次别放太多；一屏放不下会自动轮播，节奏会变快。",
    "「回到候场 / 重置」只清空大屏，不会删故事。",
    "换下一个故事：展开顶部「本会场使用的汤稿」选另一份；切换会先回到候场，并收回已公开的线索。",
])

picture("soup-overview.png", 160, "图 5　海龟汤控场整页：① 展示汤面　② 线索板　③ 汤底开关　④ 临时添加线索　⑤ 大屏预览", new_page=True)

# ---------------------------------------------------------------- ending
rule(size=6, space_after=6)
para("本手册只覆盖答题与海龟汤两块操作。遇到手册没写的情况，先在「现场状态」确认当前进度，再联系活动管理员。",
     size=9, color=MUTED, space_after=0)

doc.core_properties.title = "答题与海龟汤控场手册"
doc.core_properties.author = "活动现场工作组"
doc.save(OUT)
print("saved", OUT)
