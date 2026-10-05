#!/usr/bin/env python3
# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""检查公开源码品牌、许可、图片、署名和已知秘密格式；不替代人工代码审查。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。"""
from pathlib import Path
import re, hashlib, subprocess, sys
root=Path(__file__).resolve().parents[1]
errors=[]
def verify(condition,message):
    """汇总全部发布问题，图片缺失不会跳过秘密与许可检查。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。"""
    if not condition:errors.append(message)
readme=(root/'README.md').read_text()
for s in ['上海如静知华信息科技有限公司','https://www.zhuatech.cn/','zhuatech2','未经书面授权不得商用']:
    verify(s in readme,'README required text: '+s)
image_count=0
for image in re.findall(r'<img[^>]+src="([^"]+)"|!\[[^\]]*\]\(([^)]+)\)',readme):
    path=next(x for x in image if x)
    verify(not path.startswith(('/', 'http','file:')),'Non-relative image: '+path)
    verify((root/path).is_file(),'Missing README image: '+path)
    image_count+=1
verify(image_count>=8,'At least six page screenshots and both QR images are required')
expected={'wechat-zhuatech.png':'a1205aeec110016ca889693892250a11d449489f64d27c714816b73c3fc645e1','wechat-zhuatech2.png':'98df6f15d17f94b88bc8bc115262b264fab0cfb5e6ca9443aaaf4143c5275215'}
for name,digest in expected.items():
    p=root/'docs/images'/name
    verify(p.is_file() and hashlib.sha256(p.read_bytes()).hexdigest()==digest,'Original QR hash: '+name)
for name,digest in expected.items():
    p=root/'frontend/public/brand'/name
    verify(p.is_file() and hashlib.sha256(p.read_bytes()).hexdigest()==digest,'Original page QR hash: '+name)
verify(readme.count('height="200"')==2,'Both QR images must retain the same display height')
feedback=(root/'frontend/src/consign.js').read_text()
for source in (root/'backend/src/main/java').rglob('*.java'):
    for code in re.findall(r'new Problem\(\d+, "([A-Z_]+)"\)',source.read_text()):
        verify(re.search(r'\b'+code+r':\s*\[',feedback),'Missing business error feedback: '+code)
license_text=(root/'LICENSE').read_text()
verify('上海如静知华信息科技有限公司' in license_text and 'zhuatech2' in license_text and '非商业' in license_text,'LICENSE brand and non-commercial permission')
patterns=[r'gh[pousr]_[A-Za-z0-9]{30,}',r'github_pat_[A-Za-z0-9_]{30,}',r'AKIA[0-9A-Z]{16}',r'-----BEGIN (?:RSA |OPENSSH |EC )?PRIVATE KEY-----']
count=0
paths=subprocess.check_output(['git','ls-files','--cached','--others','--exclude-standard'],cwd=root,text=True).splitlines()
tracked=set(subprocess.check_output(['git','ls-files'],cwd=root,text=True).splitlines())
for name in expected:verify('docs/images/'+name in tracked,'QR not tracked: '+name)
for relative in sorted(set(paths)):
    p=root/relative
    if not p.is_file() or any(x in p.parts for x in ['.git','node_modules','target','dist']):continue
    verify(p.name not in ['.env','consigndesk-private-backup.sql'] and 'quality-state' not in p.name,'Private file in publish set: '+relative)
    if p.suffix not in ['.java','.vue','.js','.py','.sql','.md','.yaml','.yml','.xml','.conf','.json'] and p.name not in ['Dockerfile','LICENSE','.env.example']:continue
    text=p.read_text();count+=1
    if p.suffix in ['.java','.vue','.js','.py','.sql']:verify('zhuatech2' in text[:1200],'Missing own-source attribution: '+relative)
    if p.name!='release-check.py':
        for pattern in patterns:verify(not re.search(pattern,text),'Secret pattern in '+relative)
for line in (root/'.env.example').read_text().splitlines():
    if line.startswith(('MYSQL_ROOT_PASSWORD=','DATABASE_PASSWORD=','ADMIN_PASSWORD=')):verify(line.endswith('='),'Nonempty example credential: '+line.split('=')[0])
if errors:
    print(f'FAILED: {len(errors)} release checks; {count} text files scanned')
    for error in errors:print(' - '+error)
    sys.exit(1)
print(f'PASS: {count} text files scanned; README images, original QR hashes, brand, LICENSE and example credentials verified')
