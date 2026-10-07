[中文](README.md) | [English](README.en.md)

<p><img src="frontend/public/brand/logo.jpg" height="48" alt="ZhiHua Technology official logo"></p>

# ConsignDesk · Consigned Goods and Consignor Settlement

**1.0.0 · Public source for learning / non-commercial use. Commercial use requires prior written authorization.**

**ZhiHua Technology (Shanghai Rujing Zhihua Information Technology Co., Ltd.)** · [Official website](https://www.zhuatech.cn/)

Consignment stores hold someone else's physical goods and split proceeds after sale. ConsignDesk connects consignor applications, independent approval, receipt, sales evidence, refunds, expiry collection and consignor ledgers. For secondhand clothing, collectibles/home-goods stores and their implementation teams. Java 21 / Spring Boot, Vue 3, MySQL and Flyway provide Chinese/English pages and independent private deployment.

## From physical intake to settlement

1. The store creates consignor profiles/accounts. Owners draft a piece with condition, asking/minimum price, owner percentage, settlement holding days and expiry, then submit.
2. Another authorized employee verifies the offline agreement and approves/returns it; authors cannot self-approve. Approval does not mean physical receipt.
3. Staff verify the actual piece and record a receipt reference/explanation before it becomes available. **Each reference represents one physical piece:** no quantity batches, shared SKU or multiwarehouse transfer.
4. Staff record completed external POS/manual sales. Actual price cannot be below the frozen minimum; expired pieces need a verified extension. Sales retain percentage, actual price, owner proceeds and holding-period snapshots.
5. Finance records actual offline payments, including partial payouts bounded by matured payable balance. References cannot repeat. Full sale refunds reverse the original split and record the returned piece. If already paid, negative balance becomes a recovery obligation; it is neither hidden nor paid again.
6. Unsold goods may be collected; sold pieces cannot be directly collected. Extensions change expiry only, not frozen price/split/holding rules. Incorrect cash records require actual external reversal evidence and appended opposite entries, retaining originals.

Sales, payouts, refunds and recoveries are **manual records of external facts**. The application does not connect to banks, debit accounts, initiate refunds or send notifications. Owner proceeds are actual price × percentage, rounded to two decimals; the store receives the residual so both equal price exactly. It does not authenticate goods, validate contracts or verify receipt of funds.

## Business and administrative pages

Owners see only their own pieces, sales/refunds, splits and cash histories. Store operations maintain department consignors/intake/sales; finance handles department settlement; administrators handle accounts/departments/permissions. APIs recheck the actual account/scope every request; hidden menus do not prevent unauthorized access by themselves.

Screenshots show the existing application's pages. Acceptance uses independent TEST records, without real customer data, and an empty installation creates no business examples.

| Login | Owner goods |
|---|---|
| ![Login](docs/screenshots/login.jpg) | ![Own goods](docs/screenshots/portal.jpg) |
| Physical receipt/details | Consignor settlement |
| ![Item details](docs/screenshots/item.jpg) | ![Account details](docs/screenshots/account.jpg) |
| Accounts | Workspace statistics |
| ![Accounts](docs/screenshots/accounts.jpg) | ![Statistics](docs/screenshots/dashboard.jpg) |
| Roles | Mobile business interface |
| ![Roles](docs/screenshots/roles.jpg) | ![Mobile interface](docs/screenshots/mobile.jpg) |

- Login: real account authentication into an authorized workspace.
- Owner goods: own pieces only.
- Item details: frozen agreement, receipt, sales and retained states.
- Settlement: signed balance, held proceeds, matured payable and cash entries.
- Accounts: departments, roles, fixed consignor bindings and enabled status.
- Statistics: authorized states, net sales and store shares.
- Roles: interface permissions and data scopes.
- Mobile: narrow-screen goods and ledger operations.

## Implemented features and limits

| Module | Available operations |
|---|---|
| Consignors | CRUD, department, enabled state, versions, reference protection and independent linked login |
| Physical goods | Own drafts, submit/withdraw, independent approve/return, actual receipt, frozen agreement, extend, unsold collection, cancel and history |
| Sales/returns | Single-piece sale, unique external evidence, minimum/expiry checks, frozen split, full original-sale refund with returned piece and resale |
| Settlement | Signed total balance, held proceeds, matured payable, partial payout, recovery due/receipt, one reversal per original and immutable ledger |
| Batch/reporting | Atomic import of up to 300 sales, CSV validation/preview, goods/sales/ledger CSV, states, net sales and store proceeds |
| Administration | Accounts, roles, departments, permissions, menus, dictionaries/settings; immutable owner bindings and last-admin protection |
| Shared | Sessions, BCrypt, CSRF, password changes, live permissions, audit, search/state/sort, ten-row pages, health and migrations |

**Not implemented:** automatic POS sync, online payments, partial refunds, tax invoices, bank reconciliation, automatic authentication, electronic signatures, image attachments, barcodes/label printing, email/SMS, public owner registration, outright purchases, booth rental, automatic markdowns, stored value, cross-company multi-tenancy or SaaS subscriptions. Holding periods are ledger rules, not assertions of legal compliance. Split records are not tax invoices or a general ledger.

Core workflows require no third-party keys. CSV supports manual exchange with an existing POS and is not automatic integration. HTTPS certificates/external MySQL are deployment configuration; other interfaces require development.

## Requirements and installation

Python 3, Docker/Compose v2; source development additionally uses Java **21**, Maven **3.9**, Node **24.19.0+**, npm and MySQL **8.4**. Backend: Spring Boot **4.0.7**, Security, JPA and Flyway. Frontend: Vue **3.5.40** / Vite **8.1.5**. MariaDB Java Client **3.5.10** connects to MySQL via `jdbc:mariadb://`. First builds need official images/public dependencies.

```sh
python3 scripts/init-env.py
docker compose config --quiet
docker compose up -d --build --wait
```

Open [http://localhost:8120/](http://localhost:8120/); [health](http://localhost:8120/actuator/health). Username `admin`; read `ADMIN_PASSWORD` in your local ignored `.env`. No shared demo password exists. The script creates independent random passwords with mode 0600 and refuses to overwrite existing configuration. If configuration exists, start directly. Restarts do not reset passwords.

An empty database initializes the administrator, four roles, nine permissions, nine menus, a base department, three categories and three settings. **No consignors, goods, sales, payments or other accounts are seeded.** Isolated TEST fixtures are not customer cases.

### Configuration and source development

| Name | Purpose |
|---|---|
| `MYSQL_ROOT_PASSWORD` | Independent strong database administration password |
| `DATABASE_PASSWORD` | Separate application database password |
| `ADMIN_USERNAME` / `ADMIN_PASSWORD` | Initial identity and 12–72-byte password with upper/lowercase/digits |
| `WEB_PORT` / `BIND_ADDRESS` | Defaults 8120 / 127.0.0.1; override occupied ports |
| `COOKIE_SECURE` | false for local HTTP, true behind HTTPS |
| `DATABASE_URL` / `DATABASE_USER` | Optional external MySQL with verify-full and trusted CA |

[.env.example](.env.example) contains names without secrets. Nginx proxies `/api` by service name; the browser has no hardcoded backend host. MySQL has no published host port. Host backend development needs a separate database or private loopback mapping. Inject a reachable URL/account/password and initial admin through controlled environment configuration:

```sh
mvn -f backend/pom.xml spring-boot:run
```

In another repository-root terminal:

```sh
cd frontend
npm ci --no-audit --no-fund
npm run dev
```

Vite defaults to port 5173, proxying backend 8080. Host source execution does not automatically load `.env`. Do not target actual business databases for tests or type credentials into history.

## Architecture, structure and database initialization

```text
backend/src/main/java/cn/zhuatech/consigndesk/     Authorization, admin, physical states and settlement
backend/src/main/resources/db/migration/         Versioned schemas
backend/src/test/                               HTTP/JPA and business rules
frontend/src/                                  Staff, owner, admin and bilingual pages
scripts/                                       Private passwords, fresh database tests, release checks
frontend/public/brand/ + docs/images/            Original logo and Chinese contact assets
docs/                                          Operations, API, architecture, deployment and security
compose.yaml                                   MySQL, Java and Vue/Nginx
```

Vue → same-origin Nginx → Spring Security/services → JPA/MySQL. [V1 migration](backend/src/main/resources/db/migration/V1__consignment_schema.sql) defines 15 application tables plus Flyway history, foreign keys/indexes, unique evidence references and amount constraints. Server BigDecimal handles amounts; fractional IDs, revisions/holding days are rejected. Item/consignor writes check versions. READ COMMITTED transactions lock the base department to serialize balances, states and configuration. This is a small-store single-instance implementation, not a distributed marketplace.

Lists authorize first, then use client search/sort/pagination, at most 10,000 entries per type. This is bounded client pagination rather than an unlimited server dataset. Instance currency accepts two-decimal ISO currencies and locks after goods exist. Expiry/holding periods use UTC dates, not browser local midnight; facts use UTC timestamps. See [Architecture/database](docs/architecture.md).

## Testing

```sh
mvn -B -f backend/pom.xml spotless:check test package
cd frontend
npm ci --no-audit --no-fund
npm run format:check
npm run lint
npm test
npm run build
cd ..
docker compose config --quiet
docker compose build
python3 scripts/release-check.py
git diff --check
```

Isolated H2 HTTP/JPA tests do not replace actual MySQL. Credentials are dynamically generated in backend tests; image builds execute all tests. Frontend regressions include actual component state, request timeouts, expired-session cleanup, retained failed inputs, committed-but-refresh-failed writes, changed scopes, unknown write outcomes, CSV and signed balances. Component-state tests do not claim visual/browser acceptance.

Only against a **fresh isolated disposable instance**:

```sh
TEST_URL=http://127.0.0.1:8120 python3 scripts/smoke-test.py
python3 scripts/persistence-check.py --capture
```

The smoke test writes TEST owners/accounts/goods/sales/cash evidence and checks real MySQL transactions, concurrent payments, owner/department scopes, exports and states. Never run it against an existing business database. After test/page operations, capture private responses in ignored output; never publish test passwords or responses. After restart, run `python3 scripts/persistence-check.py` without capture; for independent recovery select its port using `TEST_URL`. It compares original accounts, goods, sale snapshots and financial histories. See [Testing](docs/testing.md).

## Deployment, upgrades and recovery

See [Deployment and restoration](docs/deployment.md). Default loopback startup is local. External hosting needs authorization, controlled HTTPS, trusted proxies, secure cookies, least privilege, network isolation, restricted secrets and retained backups. External MySQL uses verified server certificates/trusted CA; internal-network trust examples are not an external-security scheme.

Pause writes and retain application versions, complete database backups and restricted private configuration. Backups contain password hashes/financial histories; keep them mode 0600 outside public source, without printing credentials or contents. Restore to a new Compose project, distinct port and fresh database volume. Start MySQL, import the complete backup, then start matching applications. Check Flyway, original accounts, consignors, goods/sales, signed balances and ledger entries. Compare private response snapshots and restart behavior; a backup file's existence alone does not prove recovery.

Flyway owns migrations; JPA validates only. Append V2/higher, never rewrite applied V1 or delete actual volumes to resolve failure. Validate upgrades on restored copies before switching; rollback requires matching images and a verified preupgrade backup. `docker compose down` retains the database. **Remove volumes only for explicitly disposable test projects after checking project labels.**

## Troubleshooting

- **Startup fails:** configuration, port and three services' health; inspect this instance's logs and preserve volumes.
- **Owner cannot enter:** binding, same department, CONSIGNOR scope with portal-only permission, enabled owner.
- **Self-approval rejected:** another authorized same-department employee must review; independence is enforced.
- **Cannot sell available goods:** expiry, frozen minimum, version and enabled consignor.
- **No payable balance:** holding period, existing payouts, refunds and negative recovery; held proceeds cannot be paid early.
- **CSV rejected:** current template, actual references and original versions; duplicate/expired/invalid rows roll back the batch. Refresh/recheck rather than retrying rows individually.
- **Version conflict:** refresh/review; no automatic overwriting. Referenced master records and submitted history cannot be physically deleted.

See [Operations](docs/manual.md) and [API](docs/api.md); detailed linked manuals are currently in Chinese.

## Security, feedback and license

HTTPS, trusted access, strong credentials, minimum permissions and verified backups remain deployment responsibilities. HttpOnly sessions/CSRF are not a complete hosting security scheme. Login limits are single-instance; no cross-node limits, MFA or SSO. Avoid bank passwords, card/identity numbers and unnecessary sensitive information in notes/imports. See [Security](docs/security.md).

Contributions need reproducible checks; exclude customer data, credentials and databases. Share redacted feedback and privately report vulnerabilities. Software is provided as is without payment, authenticity, legal, tax or compliance guarantees; the operator validates its own workflows/dependencies.

Own code uses [ZhuaTech Non-Commercial Source License 1.0](LICENSE), permitting personal learning, technical research and non-commercial exchange only. **Commercial use requires prior written authorization from Shanghai Rujing Zhihua Information Technology Co., Ltd.** Commercial deployment/customer delivery, private hosting, paid services, resale/SaaS and in-depth customization require corresponding authorization, without automatic copyright transfer. Preserve attribution, website, copyright, license and licensing contacts. This is publicly readable non-commercial source, not an OSI-approved license. Vue/Lucide and other dependencies retain their licenses; see [Third-party notices](docs/third-party.md). No unverified production-readiness claim is made.

## Contact ZhiHua Technology

For commercial source licensing, in-depth custom development, private hosting, POS interface adaptation, historical migration or system integration, contact **ZhiHua Technology (Shanghai Rujing Zhihua Information Technology Co., Ltd.)**:

- Website: [https://www.zhuatech.cn/](https://www.zhuatech.cn/)
- Email: [han@zhuatech.cn](mailto:han@zhuatech.cn)
- Email: [jack@zhuatech.cn](mailto:jack@zhuatech.cn)
- WhatsApp: [+86 17521234993](https://wa.me/8617521234993)
