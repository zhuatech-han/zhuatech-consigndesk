#!/usr/bin/env python3
# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
"""全新MySQL实例的真实HTTP寄售、退款、台账及权限验收；不得用于业务库。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。"""
import concurrent.futures
import csv
import datetime
import http.cookiejar
import io
import json
import os
from pathlib import Path
import secrets
from decimal import Decimal
import urllib.error
import urllib.request

root = Path(__file__).resolve().parents[1]
env = dict(line.split('=', 1) for line in (root / '.env').read_text().splitlines()
           if line and not line.startswith('#') and '=' in line)
base = os.environ.get('TEST_URL', 'http://127.0.0.1:' + env.get('WEB_PORT', '8120'))
count = 0


def check(value, message):
    """逐项计数，不在优化模式下跳过断言。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。"""
    global count
    count += 1
    if not value:
        raise AssertionError(message)


class Client:
    """独立真实Cookie/CSRF会话，不共享登录凭证。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。"""
    def __init__(self):
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
        self.csrf = None

    def request(self, path, method='GET', body=None, csrf=True):
        if method != 'GET' and csrf and self.csrf is None:
            self.csrf = self.call('/api/auth/csrf')
        headers = {'Content-Type': 'application/json'}
        if method != 'GET' and csrf:
            headers[self.csrf['header']] = self.csrf['token']
        request = urllib.request.Request(base + path, method=method, headers=headers,
            data=None if body is None else json.dumps(body, default=str).encode())
        try:
            with self.opener.open(request, timeout=30) as response:
                return response.status, response.read()
        except urllib.error.HTTPError as error:
            return error.code, error.read()

    def call(self, path, method='GET', body=None, expected=200, raw=False):
        status, data = self.request(path, method, body)
        check(status == expected, f'{method} {path}: expected {expected}, got {status}: ' + data[:300].decode(errors='replace'))
        return data.decode('utf-8-sig') if raw else json.loads(data, parse_float=Decimal)

    def login(self, name, password):
        self.call('/api/auth/login', 'POST', {'username': name, 'password': password})
        self.csrf = None  # Reload CSRF after the authentication session changes.
        return self


anon = Client()
check(anon.call('/actuator/health')['status'] == 'UP', 'real deployment healthy')
for path in ['/api/items', '/api/sales', '/api/admin/users']:
    anon.call(path, expected=401)
a = Client().login(env.get('ADMIN_USERNAME', 'admin'), env['ADMIN_PASSWORD'])
check(a.request('/api/items', 'POST', {}, csrf=False)[0] == 403, 'missing CSRF denied')
for path in ['/api/items', '/api/sales', '/api/consignors']:
    check(a.call(path) == [], 'fresh business tables: ' + path)
roles = a.call('/api/admin/roles')
check(len(roles) == 4, 'four initial roles')
check(len(a.call('/api/admin/permissions')) == 9, 'nine registered permissions')
check(len(a.call('/api/admin/menus')) == 9, 'nine persistent menus')
password = 'Aa9' + secrets.token_hex(18)
role = lambda name: next(r['id'] for r in roles if name in r['name'])


def consignor(code, dept=1, name=None):
    """生成显式TEST档案，只有本次测试会话使用。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。"""
    return a.call('/api/consignors', 'POST', {'code': code, 'name': name or 'TEST ' + code,
        'departmentId': dept, 'contactNote': 'TEST fixture; no real personal data', 'enabled': True})


def user(name, role_id, dept=1, owner=None):
    return a.call('/api/admin/users', 'POST', {'username': name, 'displayName': 'TEST ' + name,
        'password': password, 'roleId': role_id, 'departmentId': dept, 'consignorId': owner, 'enabled': True})


c1 = consignor('TEST-A')
c2 = consignor('TEST-B')
department = a.call('/api/admin/departments', 'POST', {'name': 'TEST other department'})
c3 = consignor('TEST-C', department['id'])
u1 = user('test-owner', role('Consignor'), owner=c1['id'])
u2 = user('test-other', role('Consignor'), owner=c2['id'])
u3 = user('test-ops', role('Store operations'))
u4 = user('test-finance', role('Finance'))
u5 = user('test-outside', role('Store operations'), department['id'])
b = Client().login('test-owner', password)
other = Client().login('test-other', password)
ops = Client().login('test-ops', password)
finance = Client().login('test-finance', password)
outside = Client().login('test-outside', password)
check(len(b.call('/api/consignors')) == 1, 'portal only own consignor')
check(len(outside.call('/api/consignors')) == 1, 'department restricted owners')
check(len(ops.call('/api/consignors')) == 2, 'operations own department only')
check(b.call('/api/meta')['departments'] == [], 'portal excludes staff departments')
for path in ['/api/admin/users', '/api/audit', '/api/dashboard', '/api/export/items']:
    b.call(path, expected=403)
other.call('/api/accounts/' + str(c1['id']), expected=403)
outside.call('/api/accounts/' + str(c1['id']), expected=403)
ops.call('/api/accounts/' + str(c1['id']), expected=403)
expires = (datetime.datetime.now(datetime.timezone.utc).date() + datetime.timedelta(days=30)).isoformat()


def item_body(owner=c1['id'], hold=0, name='TEST consigned garment'):
    return {'consignorId': owner, 'name': name, 'category': 'CLOTHING', 'conditionNote': 'TEST externally inspected',
        'askingPrice': '100.01', 'minimumPrice': '50.00', 'ownerPercent': '60.00', 'holdDays': hold, 'expiresOn': expires}


def item(i):
    return a.call('/api/items/' + str(i['id']))['item']


def action(client, i, command, expected=200, **changes):
    value = item(i)
    body = {'revision': value['revision'], 'note': 'TEST independent external agreement or handoff',
        'externalReference': 'TEST-IN-' + secrets.token_hex(8), **changes}
    return client.call(f"/api/items/{i['id']}/{command}", 'POST', body, expected)


def available(hold=0, name='TEST consigned garment'):
    i = b.call('/api/items', 'POST', item_body(hold=hold, name=name))
    action(b, i, 'submit')
    action(a, i, 'approve')
    return action(ops, i, 'receive')


def sale_body(i, ref=None, price='100.01'):
    return {'itemReference': i['reference'], 'revision': i['revision'], 'price': price,
        'externalReference': ref or 'TEST-POS-' + secrets.token_hex(8)}


def balance(owner=c1['id']):
    return a.call('/api/accounts/' + str(owner))['balance']


def cash_body(kind, amount, ref=None, owner=c1['id'], **fields):
    return {'consignorId': owner, 'kind': kind, 'amount': amount,
        'externalReference': ref or 'TEST-CASH-' + secrets.token_hex(8),
        'note': 'TEST actual external transaction fixture', **fields}


# Exact snapshots, immutable evidence and serialization of two simultaneous payouts.
i = available()
s = ops.call('/api/sales', 'POST', sale_body(i))
check(s['ownerAmount'] == Decimal('60.01') and s['storeAmount'] == Decimal('40.00'), 'exact rounding and frozen split')
check(item(i)['status'] == 'SOLD', 'sold item persisted')
check(balance()['payable'] == Decimal('60.01'), 'matured owner share')
finance2 = Client().login('test-finance', password)
finance.call('/api/auth/csrf'); finance2.call('/api/auth/csrf')
finance.csrf = finance.call('/api/auth/csrf'); finance2.csrf = finance2.call('/api/auth/csrf')
with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
    futures = [pool.submit(client.request, '/api/cash', 'POST', cash_body('PAYOUT', '40.00')) for client in [finance, finance2]]
    statuses = [future.result()[0] for future in futures]
check(sorted(statuses) == [200, 409], 'concurrent payouts cannot overpay')
check(balance()['payable'] == Decimal('20.01'), 'one payout committed')
payout = finance.call('/api/cash', 'POST', cash_body('PAYOUT', '20.01', 'TEST-PAID-REST'))
finance.call('/api/cash', 'POST', cash_body('PAYOUT', '0.01'), 409)
ops.call('/api/cash', 'POST', cash_body('PAYOUT', '1'), 403)
finance.call('/api/sales', 'POST', sale_body(i), 403)
refund = {'externalReference': 'TEST-REFUND-1', 'note': 'TEST actual full refund with item returned'}
ops.call(f"/api/sales/{s['id']}/refund", 'POST', refund)
check(item(i)['status'] == 'AVAILABLE', 'refunded physical item can be sold again')
check(balance()['total'] == Decimal('-60.01') and balance()['recoveryDue'] == Decimal('60.01'), 'paid refund creates recovery obligation')
ops.call(f"/api/sales/{s['id']}/refund", 'POST', {**refund, 'externalReference': 'TEST-REFUND-AGAIN'}, 409)
finance.call('/api/cash', 'POST', cash_body('RECOVERY', '60.02'), 409)
finance.call('/api/cash', 'POST', cash_body('RECOVERY', '30.00', 'TEST-RECOVERY-1'))
finance.call('/api/cash', 'POST', cash_body('RECOVERY', '1.00', 'TEST-RECOVERY-1'), 409)
check(balance()['recoveryDue'] == Decimal('30.01'), 'duplicate payment reference rolls back')
finance.call('/api/cash', 'POST', cash_body('RECOVERY', '30.01'))
check(balance()['total'] == 0, 'recovery closes negative balance')
ops.call('/api/sales', 'POST', sale_body(item(i), 'TEST-POS-RESALE'))
reverse_body = cash_body('REVERSAL', '1', reversalOf=payout['id'])
finance.call('/api/cash', 'POST', reverse_body, 400)
reverse_body['amount'] = '20.01'
reverse = finance.call('/api/cash', 'POST', reverse_body)
finance.call('/api/cash', 'POST', {**reverse_body, 'externalReference': 'TEST-REVERSE-AGAIN'}, 409)
finance.call('/api/cash', 'POST', cash_body('REVERSAL', '20.01', reversalOf=reverse['id']), 400)
check(balance()['payable'] == Decimal('80.02'), 'reversal appends exact original amount')
finance.call('/api/cash', 'POST', cash_body('PAYOUT', '80.02'))
held_item = available(hold=7, name='TEST held sale')
held_sale = ops.call('/api/sales', 'POST', sale_body(held_item))
check(balance()['held'] == Decimal('60.01') and balance()['payable'] == 0, 'future share cannot be paid')
finance.call('/api/cash', 'POST', cash_body('PAYOUT', '0.01'), 409)
ops.call(f"/api/sales/{held_sale['id']}/refund", 'POST', {'externalReference': 'TEST-HELD-REFUND', 'note': 'TEST returned before maturity'})
check(balance()['total'] == 0 and balance()['payable'] == 0, 'held refund produces no phantom payable credit')

# Real state/version/precision/evidence failures preserve the current physical piece.
i1 = available(name='TEST imported item 1')
i2 = available(name='TEST imported item 2')
for price in ['49.99', '100.001']:
    ops.call('/api/sales', 'POST', sale_body(i1, price=price), 400)
ops.call('/api/sales', 'POST', {**sale_body(i1), 'revision': i1['revision'] + 0.5}, 400)
check(item(i1)['status'] == 'AVAILABLE', 'invalid price and revision write nothing')
prior_sales = len(a.call('/api/sales'))
prior_audit = len(a.call('/api/audit'))
prior_ledger = len(a.call('/api/accounts/' + str(c1['id']))['ledger'])
rows = [sale_body(i1, 'TEST-IMPORT-1'), sale_body(i2, 'TEST-IMPORT-2', '1')]
ops.call('/api/sales/import', 'POST', rows, 400)
check(len(a.call('/api/sales')) == prior_sales, 'batch sale rollback')
check(len(a.call('/api/audit')) == prior_audit, 'batch audit rollback')
check(len(a.call('/api/accounts/' + str(c1['id']))['ledger']) == prior_ledger, 'batch ledger rollback')
check(item(i1)['status'] == 'AVAILABLE' and item(i2)['status'] == 'AVAILABLE', 'batch item rollback')
rows[1]['price'] = '100.01'
check(len(ops.call('/api/sales/import', 'POST', rows)) == 2, 'valid atomic import')
ops.call('/api/sales/import', 'POST', [rows[0], rows[0]], 400)
i3 = available(name='TEST duplicate receipt guard')
ops.call('/api/sales', 'POST', sale_body(i3, 'TEST-IMPORT-1'), 409)
check(item(i3)['status'] == 'AVAILABLE', 'duplicate sales voucher cannot consume another item')
ops.call('/api/items/' + str(i3['id']) + '/renew', 'POST', {'revision': i3['revision'],
    'expiresOn': (datetime.datetime.now(datetime.timezone.utc).date() + datetime.timedelta(days=60)).isoformat(), 'note': 'TEST owner agreed extension'})
action(ops, i3, 'collect')
check(item(i3)['status'] == 'COLLECTED', 'physical return to owner')
ops.call('/api/sales', 'POST', sale_body(item(i3)), 409)
for field, value in [('holdDays', 1.5), ('consignorId', 1.5), ('ownerPercent', '60.001')]:
    b.call('/api/items', 'POST', {**item_body(), field: value}, 400)
x = b.call('/api/items', 'POST', item_body(name='TEST removable draft'))
b.call(f"/api/items/{x['id']}?revision={x['revision']}", 'DELETE')
a.call('/api/items/' + str(x['id']), expected=404)
x = b.call('/api/items', 'POST', item_body(name='TEST rejected and cancelled'))
action(b, x, 'submit'); action(a, x, 'reject')
b.call(f"/api/items/{x['id']}?revision={item(x)['revision']}", 'DELETE', expected=409)
action(b, x, 'submit'); action(b, x, 'withdraw'); action(b, x, 'submit'); action(a, x, 'approve')
b.call('/api/items/' + str(x['id']), 'PUT', {**item_body(), 'revision': item(x)['revision']}, 409)
action(b, x, 'cancel')
check(item(x)['status'] == 'CANCELLED', 'creator cancellation preserves approved history')
self_review = ops.call('/api/items', 'POST', item_body(name='TEST creator cannot approve'))
action(ops, self_review, 'submit'); action(ops, self_review, 'approve', 409)
action(a, self_review, 'approve'); action(ops, self_review, 'receive')

# Cross-owner and department boundaries, live role revocation and sole-admin protection.
foreign = a.call('/api/items', 'POST', item_body(c2['id'], name='TEST other owner piece'))
external = a.call('/api/items', 'POST', item_body(c3['id'], name='TEST other department piece'))
for i in [foreign, external]:
    b.call('/api/items/' + str(i['id']), expected=403)
    b.call('/api/items/' + str(i['id']) + '?revision=' + str(i['revision']), 'DELETE', expected=403)
outside.call('/api/items/' + str(i1['id']), expected=403)
check(len(outside.call('/api/items')) == 1, 'outside department excludes all other pieces')
check(all(v['consignorId'] == c1['id'] for v in b.call('/api/items')), 'portal filters every item')
check(all(v['consignorId'] == c1['id'] for v in b.call('/api/sales')), 'portal filters every sale')
b.call('/api/admin/users', 'GET', expected=403)
a.call('/api/admin/users/' + str(u1['id']), 'PUT', {**u1, 'consignorId': c2['id']}, 409)
a.call('/api/admin/users/' + str(u1['id']), 'PUT', {**u1, 'roleId': role('Administrator')}, 409)
readonly_role = a.call('/api/admin/roles', 'POST', {'name': 'TEST limited role', 'scope': 'DEPARTMENT', 'permissions': ['read', 'process']})
limited_user = user('test-limited', readonly_role['id'])
limited = Client().login('test-limited', password)
limited_item = limited.call('/api/items', 'POST', item_body(name='TEST revoked role draft'))
a.call('/api/admin/roles/' + str(readonly_role['id']), 'PUT', {**readonly_role, 'permissions': ['read']})
limited.call(f"/api/items/{limited_item['id']}?revision={limited_item['revision']}", 'DELETE', expected=403)
check(item(limited_item)['status'] == 'DRAFT', 'live revocation preserves record')
me = a.call('/api/auth/me')
admin_user = next(x for x in a.call('/api/admin/users') if x['id'] == me['id'])
a.call('/api/admin/users/' + str(me['id']), 'PUT', {**admin_user, 'enabled': False}, 409)
reset_user = user('test-reset', role('Consignor'), owner=c2['id'])
old = Client().login('test-reset', password)
a.call('/api/admin/users/' + str(reset_user['id']), 'PUT', {**reset_user, 'password': 'Bb8' + secrets.token_hex(18)})
old.call('/api/items', expected=401)
for setting in a.call('/api/admin/settings'):
    if setting['code'] == 'currency':
        a.call('/api/admin/settings/' + str(setting['id']), 'PUT', {'value': 'USD'}, 409)
a.call('/api/consignors/' + str(c1['id']), 'PUT', {**c1, 'departmentId': department['id']}, 409)
a.call('/api/consignors/' + str(c1['id']), 'DELETE', expected=409)

# CSV quoting/formula neutralization, numeric negative ledgers and no inserted advertising.
formula_item = available(name='=TEST, "quoted"\nnext line')
formula_sale = ops.call('/api/sales', 'POST', sale_body(formula_item, '@TEST-POS-FORMULA'))
export = ops.call('/api/export/sales', raw=True)
parsed = list(csv.DictReader(io.StringIO(export)))
row = next(v for v in parsed if v['itemId'] == str(formula_item['id']))
check(row['itemName'] == '\'=TEST, "quoted"\nnext line', 'quoted multiline formula field neutralized')
check(row['reference'] == "'@TEST-POS-FORMULA", 'voucher formula field neutralized')
check('zhuatech' not in export, 'no promotion in business CSV')
ledger = finance.call('/api/export/ledger', raw=True)
check(any(Decimal(r['amount']) < 0 for r in csv.DictReader(io.StringIO(ledger))), 'negative numeric ledger preserved')
ops.call('/api/export/ledger', expected=403)
check(external['reference'] not in ops.call('/api/export/items', raw=True), 'CSV respects department boundary')
# Test records stay in the private QA database only. No seed data in the public source.
submitted_id = None
for n in range(1, 9):
    value = b.call('/api/items', 'POST', item_body(name=f'TEST 寄售实物 {n:02d} / Item {n:02d}'))
    if n <= 3:
        action(b, value, 'submit')
    if n == 1:
        action(a, value, 'approve'); action(ops, value, 'receive')
    if n == 2:
        submitted_id = value['id']
c2_current = next(x for x in a.call('/api/consignors') if x['id'] == c2['id'])
a.call('/api/consignors/' + str(c2['id']), 'PUT', {**c2_current, 'enabled': False})
other.call('/api/items', expected=403)
state = {'base': base, 'ownerUsername': 'test-owner', 'ownerPassword': password,
    'adminUsername': env.get('ADMIN_USERNAME', 'admin'), 'adminPassword': env['ADMIN_PASSWORD'],
    'ownerId': c1['id'], 'opsUsername': 'test-ops', 'opsPassword': password,
    'financeUsername': 'test-finance', 'financePassword': password,
    'soldItemId': i1['id'], 'submittedItemId': submitted_id, 'checks': count,
    'formulaSaleId': formula_sale['id']}
state_path = root / 'output' / Path(os.environ.get('QUALITY_STATE_FILE', 'consigndesk-quality-state.json')).name
state_path.parent.mkdir(exist_ok=True)
state_path.write_text(json.dumps(state))
state_path.chmod(0o600)
print(f'PASS: {count} assertions against actual MySQL deployment; fixtures and credentials excluded from Git.')
