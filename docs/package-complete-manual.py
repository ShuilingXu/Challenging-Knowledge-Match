"""Package the offline handbook and verify deployable static files."""
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
import runpy, hashlib, json

root=Path(__file__).resolve().parents[1]
output=root/'output'
source=runpy.run_path(str(root/'docs/manual-content.py'))['PAGES']
images=sorted({p[1] for p in source})
bundle=output/'完整图文手册.zip'
with ZipFile(bundle,'w',ZIP_DEFLATED) as z:
    for name in ['用户操作手册.html','用户操作手册.md']:
        z.write(root/'docs'/name,name)
    z.write(root/'output/pdf/知识挑战赛后台操作手册.pdf','知识挑战赛后台操作手册.pdf')
    for name in images:
        z.write(root/f'docs/manual-latest-images/{name}.png',f'manual-latest-images/{name}.png')
    z.writestr('打开方法.txt','请先解压整个压缩包，再双击“用户操作手册.html”。点击图片可查看原图。PDF 可直接转发，Markdown 为可修改的文字版。图片文件夹需与网页版保存在一起。')
with ZipFile(output/'frontend-latest.zip') as z:
    names=z.namelist()
    assert 'index.html' in names and 'knowledge-match-user-manual.pdf' in names
    assert any(n.replace('\\','/').startswith('assets/') and n.endswith('.js') for n in names)
    assert z.read('knowledge-match-user-manual.pdf')==(root/'public/knowledge-match-user-manual.pdf').read_bytes()
    assert z.read('index.html')==(root/'dist/index.html').read_bytes()
    print('Verified frontend ZIP root layout, index and current PDF.')
    print(names)
with ZipFile(bundle) as z:
    assert all(f'manual-latest-images/{name}.png' in z.namelist() for name in images)
    print(f'Offline handbook: {len(images)} referenced images included.')
files=[output/'frontend-latest.zip',bundle,output/'pdf/知识挑战赛后台操作手册.pdf']
manifest={p.name:{'bytes':p.stat().st_size,'sha256':hashlib.sha256(p.read_bytes()).hexdigest()} for p in files}
(output/'交付文件校验.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(manifest,ensure_ascii=False,indent=2))
