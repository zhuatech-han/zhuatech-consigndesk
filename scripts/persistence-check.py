#!/usr/bin/env python3
# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
"""对比独立恢复实例和原测试库的登录、明细、台账及管理配置，不用于生产数据库。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。"""
import argparse
import http.cookiejar
import json
import os
from pathlib import Path
import urllib.request

root = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description='Capture or compare actual test-instance persistence')
parser.add_argument('--capture', action='store_true')
args = parser.parse_args()
state = json.loads((root / 'output/consigndesk-quality-state.json').read_text())
base = os.environ.get('TEST_URL', state['base'])
count = 0


def check(value, message):
    """核对实际响应且不打印业务载荷或口令。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。"""
    global count
    count += 1
    if not value:
        raise AssertionError(message)


class Reader:
    """独立恢复库只读会话，只在登录时POST。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。"""
    def __init__(self, name, password):
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
        csrf = self.get('/api/auth/csrf')
        req = urllib.request.Request(base + '/api/auth/login', method='POST',
            headers={'Content-Type': 'application/json', csrf['header']: csrf['token']},
            data=json.dumps({'username': name, 'password': password}).encode())
        with self.opener.open(req, timeout=30) as response:
            check(response.status == 200, 'original account login')

    def get(self, path):
        with self.opener.open(base + path, timeout=30) as response:
            check(response.status == 200, 'read ' + path)
            return json.loads(response.read())


def normalize(value):
    """按记录主键规范排序，不把数据库无序返回误认为持久化差异。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。"""
    if isinstance(value, dict):
        return {k: normalize(v) for k, v in value.items()}
    if isinstance(value, list):
        vs = [normalize(v) for v in value]
        if vs and all(isinstance(v, dict) and 'id' in v for v in vs):
            return sorted(vs, key=lambda v: v['id'])
        if vs and all(isinstance(v, str) for v in vs):
            return sorted(vs)
        return vs
    return value


admin = Reader(state['adminUsername'], state['adminPassword'])
owner = Reader(state['ownerUsername'], state['ownerPassword'])
finance = Reader(state['financeUsername'], state['financePassword'])
check(admin.get('/actuator/health')['status'] == 'UP', 'health UP')
values = {}
for path in ['/api/auth/me', '/api/meta', '/api/dashboard', '/api/items', '/api/sales', '/api/consignors', '/api/balances']:
    values['admin' + path] = normalize(admin.get(path))
for kind in ['users', 'roles', 'departments', 'permissions', 'menus', 'dictionaries', 'settings']:
    values['admin/api/admin/' + kind] = normalize(admin.get('/api/admin/' + kind))
for path in ['/api/auth/me', '/api/items', '/api/sales', '/api/consignors', '/api/accounts/' + str(state['ownerId'])]:
    values['owner' + path] = normalize(owner.get(path))
values['finance'] = normalize(finance.get('/api/accounts/' + str(state['ownerId'])))
for value in values['admin/api/items']:
    path = '/api/items/' + str(value['id'])
    values['detail' + path] = normalize(admin.get(path))
snapshot = root / 'output/consigndesk-persistence-snapshot.json'
if args.capture:
    snapshot.write_text(json.dumps(values, ensure_ascii=False, indent=2))
    snapshot.chmod(0o600)
    print(f'PASS: {count} source checks; {len(values)} actual response snapshots captured privately.')
else:
    before = json.loads(snapshot.read_text())
    check(before.keys() == values.keys(), 'restored same record set')
    for key, value in before.items():
        check(value == values[key], 'restored exact data: ' + key)
    print(f'PASS: {count} checks; original accounts, {len(values)} response snapshots and financial histories unchanged.')
