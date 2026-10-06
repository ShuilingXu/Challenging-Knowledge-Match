"""A compact, user-facing guide for everyday event operations."""
from pathlib import Path
from html import escape
from PIL import Image as PILImage
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle
from reportlab.platypus import SimpleDocTemplate, Paragraph, Image, Spacer, PageBreak
from pypdf import PdfReader
import pypdfium2 as pdfium

root=Path(__file__).resolve().parents[1]
out=root/'output/pdf/知识挑战赛简明操作手册.pdf'
tmp=root/'tmp/short-manual'
tmp.mkdir(parents=True,exist_ok=True)
pages=[
('登录与准备会场','venue-home','先选对会场，再做任何操作。',[
'打开后台网址，填写工作账号和密码，点“安全登录”。账号由管理员提供。',
'上方选择自己负责的会场。左侧菜单只显示本会场可用的功能。',
'新建活动时，先建母活动，再建会场；创建会场时勾选答题、海龟汤或抽奖。',
'母活动存放公共题库和汤稿；观众报名、展示和现场操作都在会场进行。'],
'开场前检查：会场名称正确、功能已开启、资料已保存。'),
('题库与题组','sync-sets','公共题目先在母活动准备，再由会场选择同步。',[
'选母活动，进入题库，新增或修改题目，填写题目、选项和答案后保存。',
'题目多时，用标题、题目内容或答案搜索。每页最多十题，可翻到下一页。',
'切回会场，同步需要的题目或题组；确认勾选内容后保存。',
'组卷时选择需要的题目。需要打乱出题顺序时，使用题组中的随机排序功能。'],
'到会场题库再检查一次：本场需要的题目和题组都在。'),
('现场答题','quiz-open','按“开题 → 收题 → 公布答案 → 下一题”的顺序操作。',[
'选会场，进入答题控场，确认题组、题目和计分设置，再开始活动。',
'选择本题并开题，让观众在手机作答。开题后注意倒计时。',
'作答结束后收题，再公布答案；留出时间讲解，然后选择下一题。',
'有人工评分的文本题，到评分页面完成评分；结束前核对积分榜。'],
'开题前看清题目，公布答案前确认作答已结束。'),
('海龟汤','screen-soup-layout','观众在现场提问，主持人回应，不需要手机提交答案。',[
'母活动侧边栏准备汤稿；切到会场，展开“本会场使用的汤稿”，选入本场故事。',
'选择当前汤稿，开始活动，再点“展示汤面”。按现场提问回应“是、否、无关”。',
'需要提示时，在对应线索卡片上公开或撤回。推理结束后点“揭晓汤底”，再点“确认揭晓”。',
'长内容自动分页。讲解时点“暂停播放”，可用“上一页／下一页”翻页，再点“继续播放”。换稿前先回到候场。'],
'“查看汤底（仅主持人）”供主持人参考；确认揭晓后才给观众看。'),
('抽奖与领奖','lottery-control','先准备奖品，再抽奖，最后核对领取。',[
'在本会场的奖品页面创建奖池，检查奖品名称、数量、启用状态和用途。',
'进入抽奖控场，确认本次奖品与参与名单，按页面按钮开始抽奖或摇奖。',
'公布结果后给观众时间确认。自助抽奖需观众有对应会场的抽奖机会。',
'观众领取时进入核销台，查找领取记录并核对信息，确认交付后再核销。'],
'不要给同一条领取记录重复发奖。'),
('让大屏显示内容','screen-pair','投影观众大屏，主持人后台留在工作人员电脑上。',[
'选会场，进入大屏管理，注册实际使用的屏幕。',
'用一次性配对链接在大屏电脑上打开页面，等待配对成功。',
'现场核对会场名称、二维码和题目是否清楚。看不清时在该设备设置里调整字号。',
'海龟汤会自动翻页，无需开启设备自动滚动。播放快慢在主持人端调整，不要修改其他会场的设备。'],
'正式开始前，用实际投影或显示器试播一次。'),
('观众怎么参加','player-answer','用手机扫描现场会场二维码，先核对会场名称。',[
'填写页面要求的姓名和联系方式，完成验证码，再确认登记。',
'等主持人开题。题目开放后选择答案或输入文字，再点提交。',
'提交成功后等待结果，不要连续重复提交。进入积分榜可查看当前成绩。',
'需要领奖时打开奖励页，按领取信息联系工作人员。海龟汤直接按现场安排提问。'],
'一个会场的登记身份不能用来操作另一个会场的活动。'),
('遇到问题先看这里','soup-host-current','先检查会场、状态和提示，再决定怎么处理。',[
'找不到功能：检查是否选对会场，以及该会场是否勾选了这项功能。',
'按钮不能点：看页面提示；活动可能未开始、已暂停，或有未保存的修改。汤稿编辑和切换要先回候场。',
'大屏没有更新：检查配对和网络；海龟汤可点“同步当前展示”。页面提示连接异常时点“重新连接”。',
'找不到题目或人员：清除搜索条件、翻页，并检查所属会场。仍不清楚时联系管理员。'],
'工作结束后退出登录。不要把后台账号或主持人参考汤底发给观众。'),
]
pdfmetrics.registerFont(TTFont('CN','C:/Windows/Fonts/msyh.ttc'))
pdfmetrics.registerFont(TTFont('CNBold','C:/Windows/Fonts/msyhbd.ttc'))
styles={
'title':ParagraphStyle('title',fontName='CNBold',fontSize=23,leading=33,spaceAfter=12,wordWrap='CJK'),
'body':ParagraphStyle('body',fontName='CN',fontSize=12,leading=21,spaceAfter=12,wordWrap='CJK'),
'note':ParagraphStyle('note',fontName='CNBold',fontSize=11,leading=19,spaceAfter=10,wordWrap='CJK'),
}
def p(s,kind='body'):return Paragraph(escape(s),styles[kind])
story=[]
md=['# 知识挑战赛简明操作手册','','版本：2026 年 10 月 6 日。按需要阅读，不必从头背诵。','']
for i,(title,name,intro,steps,note) in enumerate(pages):
 if i: story.append(PageBreak())
 story.extend([p(f'{i+1}. {title}','title'),p(intro)])
 im=PILImage.open(root/f'docs/manual-latest-images/{name}.png').convert('RGB')
 w,h=im.size
 if name=='soup-host-current': im=im.crop((265,0,w-15,h))
 elif name=='quiz-open': im=im.crop((265,90,w-25,min(h,1100)))
 elif name=='lottery-control': im=im.crop((265,90,w-25,min(h,1000)))
 # Full-screen and mobile images retain their context; modest height leaves readable steps.
 path=tmp/f'image-{i+1}.png'; im.save(path)
 w,h=im.size; r=min((A4[0]-84)/w,265/h)
 story.extend([Image(str(path),width=w*r,height=h*r),Spacer(1,14)])
 story.extend(p(f'{j+1}. {s}') for j,s in enumerate(steps))
 story.append(p(note,'note'))
 md.extend([f'## {i+1}. {title}','',intro,'',f'![{title}](manual-latest-images/{name}.png)',''])
 md.extend(f'{j+1}. {s}' for j,s in enumerate(steps));md.extend(['',note,''])
def footer(c,d):
 c.setFont('CN',8);c.drawString(42,25,'知识挑战赛 · 简明操作手册 · 2026-10-06')
 c.drawRightString(A4[0]-42,25,f'{d.page} / 8')
SimpleDocTemplate(str(out),pagesize=A4,leftMargin=42,rightMargin=42,topMargin=42,bottomMargin=50,title='知识挑战赛简明操作手册').build(story,onFirstPage=footer,onLaterPages=footer)
reader=PdfReader(out);assert len(reader.pages)==8,'Unexpected page overflow'
assert '\u767d\u75f4' not in '\n'.join(x.extract_text() for x in reader.pages)
(root/'docs/简明操作手册.md').write_text('\n'.join(md),encoding='utf-8')
doc=pdfium.PdfDocument(out)
for i,page in enumerate(doc):page.render(scale=1.4).to_pil().save(tmp/f'page-{i+1}.png')
print('Created and rendered 8-page short guide.')
