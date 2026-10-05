<!-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2 -->
<script setup>
import { ref, computed, onMounted, watch, nextTick } from "vue";
import {
  Menu,
  LogOut,
  RefreshCw,
  Plus,
  Search,
  ArrowLeft,
  ChevronLeft,
  ChevronRight,
  Package,
  Users,
  Wallet,
  LayoutDashboard,
  FileDown,
  Settings,
  ClipboardList,
} from "@lucide/vue";
import { api, signIn, resetCsrf } from "./api.js";
import { money, pageRows, searchText } from "./format.js";
import { forms, fieldLimits } from "./schema.js";
import { commitChange, cashDraft, removableDraft } from "./workflow.js";
import {
  states,
  errors,
  importHints,
  parseSales,
  cloneForm,
} from "./consign.js";
const lang = ref(localStorage.getItem("consigndesk-language") || "zh"),
  t = (zh, en) => (lang.value === "en" ? en : zh);
const me = ref(null),
  busy = ref(false),
  error = ref(""),
  notice = ref(""),
  nav = ref(""),
  mobile = ref(false),
  login = ref({ username: "", password: "" });
const items = ref([]),
  sales = ref([]),
  consignors = ref([]),
  meta = ref({ categories: [], departments: [], settings: [] }),
  dash = ref(null),
  balances = ref([]),
  audit = ref([]),
  adminData = ref({});
const adminType = ref("users"),
  portalTab = ref("items"),
  search = ref(""),
  filter = ref(""),
  page = ref(1),
  desc = ref(true),
  detail = ref(null),
  account = ref(null),
  modal = ref(null),
  uncertain = ref(false),
  form = ref({}),
  dialog = ref(null),
  importRows = ref([]);
let priorFocus;
const own = computed(() => me.value?.consignorId != null),
  can = (p) => me.value?.permissions.includes(p);
const setting = (key) =>
  (meta.value.settings.length
    ? meta.value.settings
    : adminData.value.settings || []
  ).find((x) => x.code === key)?.value;
const currency = computed(() => setting("currency") || "CNY"),
  cash = (v) => money(v, currency.value, lang.value),
  status = (v) => states[v]?.[lang.value === "en" ? 1 : 0] || v,
  date = (v) =>
    v
      ? String(v)
          .replace("T", " ")
          .replace(/\.\d+Z$/, " UTC")
          .replace(/Z$/, " UTC")
      : "—";
const admins = {
  users: ["账号", "Accounts"],
  roles: ["角色", "Roles"],
  departments: ["部门", "Departments"],
  permissions: ["权限", "Permissions"],
  menus: ["菜单", "Menus"],
  dictionaries: ["分类字典", "Dictionaries"],
  settings: ["参数", "Settings"],
};
const icons = {
  dashboard: LayoutDashboard,
  consignors: Users,
  items: Package,
  sales: ClipboardList,
  settlements: Wallet,
  portal: Package,
  reports: FileDown,
  admin: Settings,
  audit: ClipboardList,
};
const heading = computed(() => {
  const m = me.value?.menus.find((x) => x.code === nav.value);
  return m ? (lang.value === "en" ? m.nameEn : m.name) : "";
});
const ownerName = (id) =>
  consignors.value.find((x) => x.id === id)?.name || String(id);
const isSales = computed(
  () =>
    nav.value === "sales" ||
    (nav.value === "portal" && portalTab.value === "sales"),
);
const isItems = computed(
  () =>
    !["consignors", "sales", "settlements", "audit", "admin"].includes(
      nav.value,
    ) && !(nav.value === "portal" && portalTab.value !== "items"),
);
const source = computed(() =>
  nav.value === "admin"
    ? adminData.value[adminType.value] || []
    : nav.value === "consignors"
      ? consignors.value
      : nav.value === "settlements"
        ? balances.value.map((x) => ({ ...x, id: x.consignorId }))
        : nav.value === "audit"
          ? audit.value
          : isSales.value
            ? sales.value
            : items.value,
);
const filtered = computed(() =>
    source.value.filter(
      (x) =>
        (!filter.value || x.status === filter.value) &&
        searchText(x).includes(search.value.toLowerCase()),
    ),
  ),
  pages = computed(() => Math.max(1, Math.ceil(filtered.value.length / 10))),
  rows = computed(() =>
    pageRows(filtered.value, "", page.value, 10, desc.value),
  );
function clearWorkspace() {
  modal.value = null;
  uncertain.value = false;
  form.value = {};
  importRows.value = [];
  detail.value = null;
  account.value = null;
  items.value = [];
  sales.value = [];
  consignors.value = [];
  meta.value = { categories: [], departments: [], settings: [] };
  adminData.value = {};
  balances.value = [];
  audit.value = [];
  dash.value = null;
  portalTab.value = "items";
  mobile.value = false;
  search.value = "";
  filter.value = "";
  page.value = 1;
}
function clearSession() {
  me.value = null;
  resetCsrf();
  clearWorkspace();
  nav.value = "";
  login.value.password = "";
}
function fail(e) {
  const k = e.message || "NETWORK_ERROR";
  error.value =
    errors[k]?.[lang.value === "en" ? 1 : 0] ||
    t(
      "操作失败，请核对输入及记录状态",
      "Request failed; review input and state",
    );
  if (e.reason && importHints[e.reason])
    error.value +=
      " · " +
      (e.recordNumber
        ? t(`第${e.recordNumber}条数据：`, `Data record ${e.recordNumber}: `)
        : "") +
      t(...importHints[e.reason]);
  if (k === "RESULT_UNKNOWN") {
    uncertain.value = true;
    if (!modal.value)
      error.value = me.value
        ? t(
            "未收到退出结果，请刷新确认是否已退出。",
            "Sign-out result not received; refresh to confirm the session ended.",
          )
        : t(
            "未收到登录结果，请检查连接后重新登录。",
            "Sign-in result not received; check the connection and sign in again.",
          );
    else if (modal.value.schema === "password")
      error.value = t(
        "未收到改密结果。请关闭窗口、退出后重新登录，先尝试新密码，勿重复改密。",
        "Password-change result not received. Close, sign out and sign in with the new password first; do not repeat the change.",
      );
  }
  if (k === "EXPIRED")
    error.value = ["DRAFT", "SUBMITTED"].includes(modal.value?.item?.status)
      ? t(
          "寄售已到期。请将待审申请撤回或退回草稿，修改到期日后重新提交。",
          "Consignment expired. Withdraw or return the application to a draft, update its expiry and submit again.",
        )
      : t(
          "寄售已到期。请核实货主同意并登记续期后，再收货或销售。",
          "Consignment expired. Verify the owner's agreement and record a renewal before receipt or sale.",
        );
  if (k === "UNAUTHENTICATED") {
    clearSession();
  }
}
async function run(fn) {
  if (busy.value) return;
  busy.value = true;
  error.value = "";
  notice.value = "";
  try {
    await fn();
  } catch (e) {
    fail(e);
  } finally {
    busy.value = false;
  }
}
async function load() {
  if (!own.value && !can("read")) detail.value = null;
  if (!own.value && !can("finance")) account.value = null;
  if (!can("finance")) balances.value = [];
  if (!can("audit")) audit.value = [];
  if (!can("dashboard")) dash.value = null;
  // Custom administration/audit roles may have no permission to load business records.
  if (own.value || can("read")) {
    [items.value, sales.value, consignors.value, meta.value] =
      await Promise.all([
        api("/items"),
        api("/sales"),
        api("/consignors"),
        api("/meta"),
      ]);
  } else {
    items.value = [];
    sales.value = [];
    consignors.value = [];
    meta.value = { categories: [], departments: [], settings: [] };
  }
  if (can("admin"))
    adminData.value = Object.fromEntries(
      await Promise.all(
        Object.keys(admins).map(async (k) => [k, await api("/admin/" + k)]),
      ),
    );
  else adminData.value = {};
  if (["dashboard", "reports"].includes(nav.value) && can("dashboard"))
    dash.value = await api("/dashboard");
  if (nav.value === "settlements") balances.value = await api("/balances");
  if (nav.value === "audit") audit.value = await api("/audit");
  if (detail.value) detail.value = await api("/items/" + detail.value.item.id);
  if (account.value)
    account.value = await api("/accounts/" + account.value.consignor.id);
  page.value = Math.min(page.value, pages.value);
}
async function refreshPage() {
  const profile = await api("/auth/me");
  if (
    JSON.stringify([
      me.value?.id,
      me.value?.departmentId,
      me.value?.consignorId,
      me.value?.scope,
      [...(me.value?.permissions || [])].sort(),
    ]) !==
    JSON.stringify([
      profile.id,
      profile.departmentId,
      profile.consignorId,
      profile.scope,
      [...profile.permissions].sort(),
    ])
  )
    clearWorkspace();
  me.value = profile;
  if (!profile.menus.some((x) => x.code === nav.value))
    nav.value = profile.menus[0]?.code || "";
  await load();
}
async function loginPage() {
  await run(async () => {
    const profile = await signIn(login.value);
    clearSession();
    me.value = profile;
    login.value.password = "";
    nav.value = me.value.menus[0]?.code || "";
    await load();
  });
}
async function logout() {
  await run(async () => {
    await api("/auth/logout", "POST", {});
    clearSession();
    notice.value = "";
  });
}
function language() {
  lang.value = lang.value === "zh" ? "en" : "zh";
  localStorage.setItem("consigndesk-language", lang.value);
}
async function navigate(code) {
  if (busy.value) return;
  nav.value = code;
  mobile.value = false;
  detail.value = null;
  account.value = null;
  search.value = "";
  filter.value = "";
  page.value = 1;
  notice.value = "";
  await run(load);
}
async function viewItem(id) {
  await run(async () => {
    detail.value = await api("/items/" + id);
  });
}
async function viewAccount(id) {
  await run(async () => {
    account.value = await api("/accounts/" + id);
  });
}
function options(f) {
  if (Array.isArray(f.options))
    return f.options.map((v) => ({ value: v[0], label: t(v[1], v[2]) }));
  const vs =
    f.options === "consignors"
      ? consignors.value
      : f.options === "categories"
        ? meta.value.categories
        : adminData.value[f.options] || meta.value[f.options] || [];
  return vs.map((x) => ({
    value: ["permissions", "categories"].includes(f.options) ? x.code : x.id,
    label: lang.value === "en" ? x.nameEn || x.name : x.name,
  }));
}
const fields = computed(() => {
  if (!modal.value) return [];
  if (["submit", "withdraw"].includes(modal.value.action)) return [];
  const fs = forms[modal.value.schema] || [];
  if (modal.value.schema === "settings" && modal.value.row?.code === "currency")
    return fs.map((f) => ({ ...f, type: "text" }));
  if (modal.value.schema === "cash")
    return fs.map((f) =>
      f.key === "kind"
        ? {
            ...f,
            options: f.options.filter(
              ([kind]) =>
                Number(
                  account.value.balance[
                    kind === "PAYOUT" ? "payable" : "recoveryDue"
                  ],
                ) > 0,
            ),
          }
        : f,
    );
  return modal.value.schema === "items" && own.value
    ? fs.filter((x) => x.key !== "consignorId")
    : fs;
});
async function open(schema, row = null, extra = {}) {
  if (busy.value) return;
  if (schema === "items" && !row) {
    if (!own.value && !consignors.value.some((x) => x.enabled))
      return fail(new Error("NO_ACTIVE_CONSIGNOR"));
    if (!meta.value.categories.length)
      return fail(new Error("NO_ACTIVE_CATEGORY"));
  }
  priorFocus = document.activeElement;
  form.value = cloneForm(row);
  if (schema === "items" && !row)
    Object.assign(form.value, {
      consignorId: own.value
        ? me.value.consignorId
        : consignors.value.find((x) => x.enabled)?.id,
      category: meta.value.categories[0]?.code,
      askingPrice: "",
      minimumPrice: "",
      ownerPercent: "50.00",
      holdDays: 7,
      expiresOn: new Date(Date.now() + 30 * 86400000)
        .toISOString()
        .slice(0, 10),
    });
  if (schema === "users" && !row)
    Object.assign(form.value, {
      enabled: true,
      departmentId: me.value.departmentId,
      roleId: adminData.value.roles?.find((x) => x.scope === "DEPARTMENT")?.id,
    });
  if (schema === "roles")
    form.value.permissions = [...(row?.permissions || [])];
  if (schema === "roles" && !row) form.value.scope = "DEPARTMENT";
  if (schema === "consignors" && !row)
    Object.assign(form.value, {
      enabled: true,
      departmentId: me.value.departmentId,
    });
  if (schema === "dictionaries" && !row)
    Object.assign(form.value, { type: "category", enabled: true });
  if (schema === "sale") form.value.price = extra.item.askingPrice;
  if (schema === "cash") {
    const draft = cashDraft(account.value.balance);
    if (!draft) return;
    Object.assign(form.value, draft);
  }
  if (schema === "renew")
    form.value.expiresOn = new Date(
      Math.min(
        Date.now() + 730 * 86400000,
        Math.max(Date.now(), Date.parse(extra.item.expiresOn + "T00:00:00Z")) +
          30 * 86400000,
      ),
    )
      .toISOString()
      .slice(0, 10);
  modal.value = { schema, row, ...extra };
  uncertain.value = false;
  error.value = "";
  importRows.value = [];
  await nextTick();
  dialog.value?.querySelector("input,select,textarea,button")?.focus();
}
function close() {
  if (busy.value) return;
  modal.value = null;
  uncertain.value = false;
  error.value = "";
  nextTick(() => priorFocus?.focus?.());
}
function keyboard(e) {
  if (e.key === "Escape") {
    e.preventDefault();
    close();
  }
  if (e.key === "Tab") {
    const fs = [
      ...dialog.value.querySelectorAll(
        "input:not([disabled]),select:not([disabled]),textarea:not([disabled]),button:not([disabled])",
      ),
    ].filter((x) => x.offsetParent !== null);
    if (e.shiftKey && document.activeElement === fs[0]) {
      e.preventDefault();
      fs.at(-1)?.focus();
    } else if (!e.shiftKey && document.activeElement === fs.at(-1)) {
      e.preventDefault();
      fs[0]?.focus();
    }
  }
}
const modalTitle = computed(() => {
  const m = modal.value;
  if (!m) return "";
  if (m.title) return m.title;
  const labels = {
    about: ["关于 ConsignDesk", "About ConsignDesk"],
    password: ["修改密码", "Change password"],
    sale: ["登记已发生销售", "Record an actual sale"],
    cash: ["登记已发生资金交接", "Record an actual payment"],
    refund: ["登记全额退款及实物归还", "Record full refund and returned item"],
    reversal: ["登记资金冲正", "Record payment reversal"],
    import: ["导入销售凭据", "Import sales"],
  };
  return labels[m.schema]
    ? t(...labels[m.schema])
    : (m.row ? t("编辑", "Edit ") : t("新建", "New ")) +
        (admins[m.schema]?.[lang.value === "en" ? 1 : 0] ||
          t(
            m.schema === "items" ? "寄售商品" : "货主",
            m.schema === "items" ? "item" : "consignor",
          ));
});
function payload() {
  const v = { ...form.value };
  for (const f of fields.value)
    if (
      f.type === "number" ||
      ["roleId", "departmentId", "consignorId"].includes(f.key)
    ) {
      v[f.key] = v[f.key] === "" || v[f.key] == null ? null : Number(v[f.key]);
      if (v[f.key] !== null && !Number.isSafeInteger(v[f.key]))
        throw new Error("INVALID_INPUT");
    }
  if (modal.value.schema === "users")
    v.consignorId =
      v.consignorId === "" || v.consignorId == null
        ? null
        : Number(v.consignorId);
  return v;
}
async function persist(m, v) {
  if (m.schema === "password") {
    await api("/auth/password", "POST", v);
    clearSession();
  } else if (m.schema === "import") {
    if (!importRows.value.length) throw new Error("INVALID_IMPORT");
    await api("/sales/import", "POST", importRows.value);
  } else if (m.schema === "sale")
    await api("/sales", "POST", {
      ...v,
      itemReference: m.item.reference,
      revision: m.item.revision,
    });
  else if (m.schema === "refund")
    await api("/sales/" + m.sale.id + "/refund", "POST", v);
  else if (m.schema === "cash")
    await api("/cash", "POST", {
      ...v,
      consignorId: account.value.consignor.id,
    });
  else if (m.schema === "reversal")
    await api("/cash", "POST", {
      ...v,
      consignorId: account.value.consignor.id,
      kind: "REVERSAL",
      reversalOf: m.cash.id,
      amount: m.cash.amount,
    });
  else if (m.action)
    await api("/items/" + m.item.id + "/" + m.action, "POST", {
      ...v,
      revision: m.item.revision,
    });
  else if (m.schema === "remove") {
    await api(m.path, "DELETE");
    if (m.item) detail.value = null;
  } else {
    const prefix = ["items", "consignors"].includes(m.schema) ? "/" : "/admin/";
    await api(
      prefix + m.schema + (m.row ? "/" + m.row.id : ""),
      m.row ? "PUT" : "POST",
      v,
    );
  }
}
async function save() {
  if (!modal.value || modal.value.schema === "about" || uncertain.value) return;
  await run(async () => {
    const m = modal.value,
      v = payload();
    const refreshError = await commitChange(
      () => persist(m, v),
      () => {
        modal.value = null;
        form.value = {};
        importRows.value = [];
        notice.value =
          m.schema === "password"
            ? t(
                "密码已修改，请使用新密码登录",
                "Password changed; sign in with the new password",
              )
            : t("已保存", "Saved");
      },
      async () => {
        if (!me.value) return;
        await refreshPage();
      },
    );
    if (refreshError) {
      if (refreshError.message === "UNAUTHENTICATED") fail(refreshError);
      else
        error.value = t(
          "记录已保存，但页面更新失败。请点击刷新核对，勿重复登记。",
          "Record saved, but page refresh failed. Refresh to verify; do not record it again.",
        );
    }
  });
}
async function importFile(e) {
  error.value = "";
  importRows.value = [];
  try {
    const f = e.target.files[0];
    if (!f) return;
    if (f.size > 524288) throw new Error("IMPORT_TOO_LARGE");
    const vs = parseSales(await f.text());
    for (const [index, v] of vs.entries()) {
      const i = items.value.find((x) => x.reference === v.itemReference);
      if (!i || i.status !== "AVAILABLE" || i.revision !== v.revision)
        throw Object.assign(new Error("INVALID_IMPORT"), {
          recordNumber: index + 1,
          reason:
            !i || i.status !== "AVAILABLE"
              ? "IMPORT_UNAVAILABLE"
              : "IMPORT_VERSION",
        });
    }
    importRows.value = vs;
  } catch (e) {
    fail(e);
  }
}
function downloadText(text, name) {
  const url = URL.createObjectURL(
    new Blob([text], { type: "text/csv;charset=utf-8" }),
  );
  const a = document.createElement("a");
  a.href = url;
  a.download = name;
  a.click();
  URL.revokeObjectURL(url);
}
async function download(type) {
  await run(async () =>
    downloadText(
      await api("/export/" + type, "GET", undefined, true),
      "consignment-" + type + ".csv",
    ),
  );
}
function template() {
  downloadText(
    "\uFEFFitemReference,price,externalReference,revision\r\n" +
      items.value
        .filter((i) => i.status === "AVAILABLE")
        .map((i) => `"${i.reference}",${i.askingPrice},,${i.revision}`)
        .join("\r\n"),
    "sales-import-template.csv",
  );
}
function action(i, a) {
  const labels = {
    submit: ["提交寄售申请", "Submit consignment"],
    withdraw: ["撤回待审申请", "Withdraw submission"],
    approve: ["审核通过", "Approve terms"],
    reject: ["退回修改", "Return for revision"],
    receive: ["核验实物并收货上架", "Inspect and receive"],
    collect: ["登记实物取回", "Record collection"],
    renew: ["续期登记", "Record renewal"],
    cancel: ["取消寄售", "Cancel consignment"],
  };
  open(["receive", "collect", "renew"].includes(a) ? a : "action", null, {
    action: a,
    item: i,
    title: t(...labels[a]),
  });
}
function mayEdit(i) {
  return (
    i.status === "DRAFT" &&
    i.creatorId === me.value?.id &&
    (own.value || can("process"))
  );
}
const colSet = computed(() => {
  if (isSales.value)
    return [
      ["externalReference", "销售凭据", "Sale reference"],
      ["itemName", "商品", "Item"],
      ["consignorId", "货主", "Consignor"],
      ["status", "状态", "Status"],
      ["price", "售价", "Sale price"],
      ["ownerAmount", "货主分成", "Owner share"],
      ["availableOn", "结算到期", "Payable on"],
    ];
  if (nav.value === "consignors")
    return [
      ["code", "代码", "Code"],
      ["name", "名称", "Name"],
      ["departmentId", "部门", "Department"],
      ["contactNote", "联系备注", "Contact note"],
      ["enabled", "启用", "Enabled"],
    ];
  if (nav.value === "settlements")
    return [
      ["name", "货主", "Consignor"],
      ["total", "应付余额", "Net balance"],
      ["payable", "到期可支付", "Payable now"],
      ["held", "未到期分成", "Held"],
      ["recoveryDue", "待追回", "Recovery due"],
    ];
  if (nav.value === "audit")
    return [
      ["createdAt", "时间", "Time"],
      ["actor", "操作者", "Actor"],
      ["action", "操作", "Action"],
      ["objectId", "记录", "Record"],
      ["departmentId", "部门", "Department"],
    ];
  if (nav.value === "admin")
    return {
      users: [
        ["username", "账号", "Username"],
        ["displayName", "姓名", "Name"],
        ["roleId", "角色", "Role"],
        ["departmentId", "部门", "Department"],
        ["consignorId", "货主绑定", "Consignor"],
        ["enabled", "启用", "Enabled"],
      ],
      roles: [
        ["name", "角色", "Role"],
        ["scope", "范围", "Scope"],
        ["permissions", "权限", "Permissions"],
      ],
      departments: [["name", "部门", "Department"]],
      permissions: [
        ["code", "代码", "Key"],
        ["name", "名称", "Name"],
      ],
      menus: [
        ["code", "代码", "Key"],
        ["name", "菜单", "Menu"],
        ["nameEn", "英文名", "English"],
        ["permissionCode", "权限", "Permission"],
        ["position", "顺序", "Order"],
        ["enabled", "启用", "Enabled"],
      ],
      dictionaries: [
        ["type", "类型", "Type"],
        ["code", "代码", "Key"],
        ["name", "名称", "Name"],
        ["nameEn", "英文名", "English"],
        ["enabled", "启用", "Enabled"],
      ],
      settings: [
        ["code", "参数", "Key"],
        ["value", "值", "Value"],
      ],
    }[adminType.value];
  return [
    ["reference", "商品编号", "Item reference"],
    ["name", "实物", "Item"],
    ["consignorId", "货主", "Consignor"],
    ["status", "状态", "Status"],
    ["askingPrice", "标价", "Asking price"],
    ["expiresOn", "到期日", "Expiry"],
  ];
});
function cell(row, k) {
  const v = row[k];
  if (v == null) return "—";
  if (k === "scope")
    return (
      {
        ALL: t("全部", "All"),
        DEPARTMENT: t("本部门", "Department"),
        CONSIGNOR: t("绑定货主", "Bound consignor"),
      }[v] || v
    );
  if (k === "permissionCode")
    return adminData.value.permissions?.find((p) => p.code === v)?.name || v;
  if (
    [
      "askingPrice",
      "price",
      "ownerAmount",
      "storeAmount",
      "total",
      "payable",
      "held",
      "recoveryDue",
      "amount",
    ].includes(k)
  )
    return cash(v);
  if (k === "consignorId") return ownerName(v);
  if (k === "roleId")
    return adminData.value.roles?.find((x) => x.id === v)?.name || v;
  if (k === "departmentId")
    return (
      (adminData.value.departments || meta.value.departments).find(
        (x) => x.id === v,
      )?.name || v
    );
  if (k === "enabled") return v ? t("启用", "Enabled") : t("停用", "Disabled");
  if (k.endsWith("At")) return date(v);
  if (Array.isArray(v))
    return v
      .map((value) =>
        k === "permissions"
          ? adminData.value.permissions?.find((p) => p.code === value)?.name ||
            value
          : value,
      )
      .join(", ");
  return v;
}
watch([search, filter, desc, adminType, portalTab], () => {
  page.value = 1;
});
watch(portalTab, () => {
  detail.value = null;
  account.value = null;
  if (portalTab.value === "account" && me.value?.consignorId != null)
    run(async () => {
      account.value = await api("/accounts/" + me.value.consignorId);
    });
});
onMounted(async () => {
  try {
    me.value = await api("/auth/me");
    nav.value = me.value.menus[0]?.code || "";
    await run(load);
  } catch (e) {
    clearSession();
    if (e.message !== "UNAUTHENTICATED") fail(e);
  }
});
</script>
<template>
  <div v-if="!me" class="login-page" :inert="modal ? true : undefined">
    <form class="login-box" @submit.prevent="loginPage">
      <div class="brand">
        <img src="/brand/logo.jpg" alt="知华科技" />
        <div>
          <strong>ConsignDesk</strong
          ><span>{{ t("寄售商品与货主结算", "Consignment & payouts") }}</span>
        </div>
      </div>
      <div class="login-title">
        <h1>{{ t("登录寄售工作台", "Sign in") }}</h1>
        <button type="button" class="text-button" @click="language">
          {{ lang === "zh" ? "English" : "中文" }}
        </button>
      </div>
      <label
        >{{ t("账号", "Username")
        }}<input
          v-model="login.username"
          autocomplete="username"
          required
          maxlength="60" /></label
      ><label
        >{{ t("密码", "Password")
        }}<input
          v-model="login.password"
          type="password"
          autocomplete="current-password"
          required
          maxlength="72"
      /></label>
      <p v-if="error" role="alert" class="error">{{ error }}</p>
      <p v-if="notice" role="status" class="success">{{ notice }}</p>
      <button class="primary full" :disabled="busy">
        {{ busy ? t("登录中…", "Signing in…") : t("登录", "Sign in") }}
      </button>
      <footer>
        {{
          t(
            "公开源码学习版 · 商用须书面授权",
            "Non-commercial source · Commercial authorization required",
          )
        }}<br />上海如静知华信息科技有限公司<br /><a
          href="https://www.zhuatech.cn/"
          target="_blank"
          rel="noopener"
          >zhuatech.cn</a
        >
        · {{ t("咨询微信", "WeChat") }} zhuatech / zhuatech2<br />
        <button
          type="button"
          class="text-button"
          :disabled="busy"
          @click="open('about')"
        >
          {{ t("关于与授权", "About & licensing") }}
        </button>
      </footer>
    </form>
  </div>
  <div v-else class="application" :inert="modal ? true : undefined">
    <aside :class="{ visible: mobile }">
      <div class="brand">
        <img src="/brand/logo.jpg" alt="知华科技" />
        <div>
          <strong>ConsignDesk</strong
          ><span>{{ t("寄售商品与货主结算", "Consignment & payouts") }}</span>
        </div>
      </div>
      <nav aria-label="Main">
        <button
          v-for="m in me.menus"
          :key="m.id"
          :class="{ active: nav === m.code }"
          :disabled="busy"
          @click="navigate(m.code)"
        >
          <component :is="icons[m.code] || ClipboardList" :size="18" />{{
            lang === "en" ? m.nameEn : m.name
          }}
        </button>
      </nav>
      <footer>
        <a href="https://www.zhuatech.cn/" target="_blank" rel="noopener">{{
          t("知华科技 · 商业咨询", "ZhuaTech · Commercial enquiries")
        }}</a
        ><br />zhuatech / zhuatech2<br />{{
          t("公开源码学习版 1.0.0", "Non-commercial source 1.0.0")
        }}<br /><button
          type="button"
          class="text-button"
          :disabled="busy"
          @click="open('about')"
        >
          {{ t("关于与授权", "About & licensing") }}
        </button>
      </footer>
    </aside>
    <div v-if="mobile" class="menu-backdrop" @click="mobile = false"></div>
    <div class="main-shell">
      <header>
        <div class="top-title">
          <button
            class="icon mobile-toggle"
            :aria-label="t('打开菜单', 'Open navigation')"
            @click="mobile = !mobile"
          >
            <Menu :size="22" />
          </button>
          <h1>{{ heading }}</h1>
        </div>
        <div class="session">
          <button class="text-button" @click="language">
            {{ lang === "zh" ? "English" : "中文" }}</button
          ><span>{{ me.displayName }}</span
          ><button class="text-button" @click="open('password')">
            {{ t("改密", "Password") }}</button
          ><button
            class="icon"
            :aria-label="t('退出登录', 'Sign out')"
            :disabled="busy"
            @click="logout"
          >
            <LogOut :size="18" />
          </button>
        </div>
      </header>
      <main>
        <p v-if="!me.menus.length" role="alert" class="error">
          {{
            t(
              "当前账号没有可用菜单，请联系管理员检查角色权限及菜单启用状态。",
              "No menus available. Ask an administrator to check role permissions and enabled menus.",
            )
          }}
        </p>
        <p v-if="error" role="alert" class="error">{{ error }}</p>
        <p v-if="notice" role="status" class="success">{{ notice }}</p>
        <div class="page-tools">
          <h2>
            {{
              detail
                ? detail.item.reference
                : account
                  ? account.consignor.name
                  : heading
            }}
          </h2>
          <button :disabled="busy" @click="run(refreshPage)">
            <RefreshCw :size="15" />{{ t("刷新", "Refresh") }}
          </button>
        </div>
        <section
          v-if="['dashboard', 'reports'].includes(nav) && dash && !detail"
          class="metrics"
        >
          <div
            v-for="[k, zh, en] in [
              ['SUBMITTED', '待审核', 'Awaiting review'],
              ['AVAILABLE', '在售实物', 'Available items'],
            ]"
            :key="k"
          >
            <span>{{ t(zh, en) }}</span
            ><strong>{{ dash.counts[k] }}</strong>
          </div>
          <div>
            <span>{{ t("到期未取回", "Expired on shelf") }}</span
            ><strong>{{ dash.expired }}</strong>
          </div>
          <div>
            <span>{{ t("净销售额", "Net sales") }}</span
            ><strong>{{ cash(dash.netSales) }}</strong>
          </div>
          <div>
            <span>{{ t("门店分成", "Store share") }}</span
            ><strong>{{ cash(dash.storeShare) }}</strong>
          </div>
        </section>
        <div v-if="nav === 'portal'" class="tabs">
          <button
            v-for="[k, zh, en] in [
              ['items', '我的商品', 'My items'],
              ['sales', '销售与退款', 'Sales & returns'],
              ['account', '我的结算', 'My account'],
            ]"
            :key="k"
            :class="{ selected: portalTab === k }"
            :disabled="busy"
            @click="portalTab = k"
          >
            {{ t(zh, en) }}
          </button>
        </div>
        <div v-if="nav === 'admin'" class="tabs">
          <button
            v-for="(label, k) in admins"
            :key="k"
            :class="{ selected: adminType === k }"
            @click="adminType = k"
          >
            {{ t(...label) }}
          </button>
        </div>
        <div v-if="nav === 'reports'" class="export-tools">
          <button @click="download('items')">
            <FileDown :size="16" />{{ t("商品CSV", "Items CSV") }}</button
          ><button @click="download('sales')">
            <FileDown :size="16" />{{ t("销售CSV", "Sales CSV") }}</button
          ><button v-if="can('finance')" @click="download('ledger')">
            <FileDown :size="16" />{{ t("结算CSV", "Ledger CSV") }}
          </button>
        </div>
        <section v-if="detail">
          <button class="text-button back" @click="detail = null">
            <ArrowLeft :size="16" />{{ t("返回列表", "Back to list") }}
          </button>
          <div class="detail-head">
            <div>
              <h2>{{ detail.item.name }}</h2>
              <span
                >{{ ownerName(detail.item.consignorId) }} ·
                {{ detail.item.reference }}</span
              >
            </div>
            <span class="badge" :data-state="detail.item.status">{{
              status(detail.item.status)
            }}</span>
          </div>
          <dl class="facts">
            <div>
              <dt>{{ t("标价 / 最低售价", "Asking / minimum price") }}</dt>
              <dd>
                {{ cash(detail.item.askingPrice) }} /
                {{ cash(detail.item.minimumPrice) }}
              </dd>
            </div>
            <div>
              <dt>
                {{ t("货主分成 / 等待期", "Owner share / waiting days") }}
              </dt>
              <dd>
                {{ detail.item.ownerPercent }}% / {{ detail.item.holdDays
                }}{{ t("天", " days") }}
              </dd>
            </div>
            <div>
              <dt>{{ t("到期日（UTC）", "Expiry (UTC)") }}</dt>
              <dd>{{ detail.item.expiresOn }}</dd>
            </div>
            <div>
              <dt>{{ t("实物与成色", "Item condition") }}</dt>
              <dd>{{ detail.item.conditionNote }}</dd>
            </div>
            <div>
              <dt>{{ t("约定依据", "Agreement evidence") }}</dt>
              <dd>{{ detail.item.agreementEvidence || "—" }}</dd>
            </div>
            <div>
              <dt>{{ t("收货凭据 / 时间", "Intake reference / time") }}</dt>
              <dd>
                {{ detail.item.intakeReference || "—" }} /
                {{ date(detail.item.receivedAt) }}
              </dd>
            </div>
          </dl>
          <div class="action-bar">
            <button
              v-if="mayEdit(detail.item)"
              @click="open('items', detail.item)"
            >
              {{ t("编辑草稿", "Edit draft") }}</button
            ><button
              v-if="mayEdit(detail.item)"
              class="primary"
              @click="action(detail.item, 'submit')"
            >
              {{ t("提交审核", "Submit") }}</button
            ><button
              v-if="
                detail.item.status === 'SUBMITTED' &&
                detail.item.creatorId === me.id &&
                (own || can('process'))
              "
              @click="action(detail.item, 'withdraw')"
            >
              {{ t("撤回", "Withdraw") }}</button
            ><template v-if="!own && can('process')"
              ><button
                v-if="
                  detail.item.status === 'SUBMITTED' &&
                  detail.item.creatorId !== me.id
                "
                class="primary"
                @click="action(detail.item, 'approve')"
              >
                {{ t("通过审核", "Approve") }}</button
              ><button
                v-if="
                  detail.item.status === 'SUBMITTED' &&
                  detail.item.creatorId !== me.id
                "
                @click="action(detail.item, 'reject')"
              >
                {{ t("退回", "Return for revision") }}</button
              ><button
                v-if="detail.item.status === 'APPROVED'"
                class="primary"
                @click="action(detail.item, 'receive')"
              >
                {{ t("收货上架", "Receive") }}</button
              ><button
                v-if="['APPROVED', 'AVAILABLE'].includes(detail.item.status)"
                @click="action(detail.item, 'renew')"
              >
                {{ t("续期", "Renew") }}</button
              ><button
                v-if="detail.item.status === 'AVAILABLE'"
                class="primary"
                @click="open('sale', null, { item: detail.item })"
              >
                {{ t("登记销售", "Record sale") }}</button
              ><button
                v-if="detail.item.status === 'AVAILABLE'"
                @click="action(detail.item, 'collect')"
              >
                {{ t("登记取回", "Record collection") }}
              </button></template
            ><button
              v-if="
                ['DRAFT', 'SUBMITTED', 'APPROVED'].includes(
                  detail.item.status,
                ) && (own ? detail.item.creatorId === me.id : can('process'))
              "
              class="danger"
              @click="action(detail.item, 'cancel')"
            >
              {{ t("取消寄售", "Cancel consignment") }}
            </button>
          </div>
          <p
            v-if="
              detail.item.status === 'SUBMITTED' &&
              detail.item.creatorId === me.id
            "
            class="hint"
          >
            {{
              t(
                "申请已提交，需由另一位有审核权限的工作人员处理。",
                "Submitted; another staff member with review permission must process it.",
              )
            }}
          </p>
          <button
            v-if="removableDraft(detail, me)"
            class="danger"
            @click="
              open('remove', null, {
                item: detail.item,
                path:
                  '/items/' +
                  detail.item.id +
                  '?revision=' +
                  detail.item.revision,
                title: t('删除未提交草稿', 'Delete never-submitted draft'),
              })
            "
          >
            {{ t("删除未提交草稿", "Delete draft") }}
          </button>
          <h3 v-if="detail.sales.length">
            {{ t("销售与退款快照", "Sale & refund snapshots") }}
          </h3>
          <div v-for="s in detail.sales" :key="s.id" class="sale-summary">
            <div>
              <strong>{{ s.externalReference }}</strong
              ><span class="badge" :data-state="s.status">{{
                status(s.status)
              }}</span>
            </div>
            <p>
              {{ cash(s.price) }} · {{ t("货主", "Owner") }}
              {{ cash(s.ownerAmount) }} · {{ t("门店", "Store") }}
              {{ cash(s.storeAmount) }} · {{ t("结算到期", "Payable") }}
              {{ s.availableOn }}
            </p>
            <p v-if="s.refundReference">
              {{ s.refundReference }} · {{ s.refundReason }}
            </p>
            <button
              v-if="!own && can('process') && s.status === 'SOLD'"
              class="danger"
              @click="open('refund', null, { sale: s })"
            >
              {{ t("全额退款及归还", "Full refund & return") }}
            </button>
          </div>
          <h3>{{ t("操作历史", "History") }}</h3>
          <ol class="history">
            <li v-for="e in detail.events" :key="e.id">
              <strong>{{ status(e.action) }}</strong
              ><span>{{ e.actor }} · {{ date(e.createdAt) }}</span>
              <p>{{ e.note || "—" }}</p>
            </li>
          </ol>
        </section>
        <section v-else-if="account">
          <button v-if="!own" class="text-button back" @click="account = null">
            <ArrowLeft :size="16" />{{
              t("返回货主列表", "Back to consignors")
            }}
          </button>
          <div class="metrics">
            <div
              v-for="[k, zh, en] in [
                ['total', '应付余额', 'Net balance'],
                ['payable', '到期可支付', 'Payable now'],
                ['held', '未到期分成', 'Held'],
                ['recoveryDue', '待追回', 'Recovery due'],
              ]"
              :key="k"
            >
              <span>{{ t(zh, en) }}</span
              ><strong>{{ cash(account.balance[k]) }}</strong>
            </div>
          </div>
          <button
            v-if="!own && can('finance') && cashDraft(account.balance)"
            class="primary"
            @click="open('cash')"
          >
            <Plus :size="16" />{{ t("资金交接登记", "Record payment") }}
          </button>
          <p
            v-if="!own && can('finance') && !cashDraft(account.balance)"
            class="hint"
          >
            {{
              t(
                "当前没有到期可支付或待追回余额，暂不能登记资金交接。",
                "No matured payable or recovery balance; no payment can be recorded yet.",
              )
            }}
          </p>
          <h3>{{ t("分成与资金台账", "Share and payment ledger") }}</h3>
          <div class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>{{ t("时间", "Time") }}</th>
                  <th>{{ t("类型", "Kind") }}</th>
                  <th>{{ t("应付变化", "Balance change") }}</th>
                  <th>{{ t("到期日", "Available on") }}</th>
                  <th>{{ t("来源", "Source") }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="e in [...account.ledger].reverse()" :key="e.id">
                  <td>{{ date(e.createdAt) }}</td>
                  <td>{{ status(e.kind) }}</td>
                  <td>{{ cash(e.amount) }}</td>
                  <td>{{ e.availableOn }}</td>
                  <td>
                    {{
                      e.saleId
                        ? t("销售 #", "Sale #") + e.saleId
                        : t("资金 #", "Cash #") + e.cashId
                    }}
                  </td>
                </tr>
                <tr v-if="!account.ledger.length">
                  <td colspan="5" class="empty">
                    {{ t("暂无台账", "No entries") }}
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <h3>{{ t("外部资金凭据", "External payment records") }}</h3>
          <div class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>{{ t("凭据", "Reference") }}</th>
                  <th>{{ t("类型", "Kind") }}</th>
                  <th>{{ t("金额", "Amount") }}</th>
                  <th>{{ t("依据", "Evidence") }}</th>
                  <th>{{ t("操作", "Action") }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="m in [...account.movements].reverse()" :key="m.id">
                  <td>
                    {{ m.externalReference
                    }}<small>{{ date(m.createdAt) }} · {{ m.actor }}</small>
                  </td>
                  <td>
                    {{ status(m.kind)
                    }}<small v-if="m.reversalOf">#{{ m.reversalOf }}</small>
                  </td>
                  <td>{{ cash(m.amount) }}</td>
                  <td>{{ m.note }}</td>
                  <td>
                    <button
                      v-if="
                        !own &&
                        can('finance') &&
                        m.kind !== 'REVERSAL' &&
                        !account.movements.some((x) => x.reversalOf === m.id)
                      "
                      @click="open('reversal', null, { cash: m })"
                    >
                      {{ t("登记冲正", "Reverse record") }}</button
                    ><span v-else>—</span>
                  </td>
                </tr>
                <tr v-if="!account.movements.length">
                  <td colspan="5" class="empty">
                    {{ t("暂无凭据", "No records") }}
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>
        <section v-else>
          <div class="list-tools">
            <div class="search">
              <Search :size="17" /><input
                v-model="search"
                :placeholder="
                  t('搜索编号、名称或凭据', 'Search reference, name or receipt')
                "
                :aria-label="t('搜索记录', 'Search records')"
              />
            </div>
            <select
              v-if="isItems || isSales"
              v-model="filter"
              :aria-label="t('状态筛选', 'Filter status')"
            >
              <option value="">{{ t("全部状态", "All statuses") }}</option>
              <option
                v-for="k in isSales
                  ? ['SOLD', 'REFUNDED']
                  : [
                      'DRAFT',
                      'SUBMITTED',
                      'APPROVED',
                      'AVAILABLE',
                      'SOLD',
                      'COLLECTED',
                      'CANCELLED',
                    ]"
                :key="k"
                :value="k"
              >
                {{ status(k) }}
              </option></select
            ><select v-model="desc" :aria-label="t('排序', 'Sort')">
              <option :value="true">{{ t("最新在前", "Newest first") }}</option>
              <option :value="false">
                {{ t("最早在前", "Oldest first") }}
              </option></select
            ><button
              v-if="nav === 'consignors' && can('master')"
              class="primary"
              @click="open('consignors')"
            >
              <Plus :size="16" />{{ t("新建货主", "New consignor") }}</button
            ><button
              v-if="
                ['items', 'portal'].includes(nav) &&
                isItems &&
                (own || can('process'))
              "
              class="primary"
              @click="open('items')"
            >
              <Plus :size="16" />{{ t("新增寄售实物", "New item") }}</button
            ><button
              v-if="
                nav === 'admin' &&
                ['users', 'roles', 'departments', 'dictionaries'].includes(
                  adminType,
                )
              "
              class="primary"
              @click="open(adminType)"
            >
              <Plus :size="16" />{{ t("新建", "New") }}</button
            ><button
              v-if="nav === 'sales' && can('process')"
              @click="open('import')"
            >
              <FileDown :size="16" />{{ t("导入销售", "Import sales") }}
            </button>
          </div>
          <div class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th v-for="c in colSet" :key="c[0]">{{ t(c[1], c[2]) }}</th>
                  <th v-if="nav !== 'audit'">{{ t("操作", "Actions") }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="r in rows" :key="r.id">
                  <td v-for="c in colSet" :key="c[0]">
                    <span
                      v-if="c[0] === 'status'"
                      class="badge"
                      :data-state="r.status"
                      >{{ status(r.status) }}</span
                    ><span v-else>{{ cell(r, c[0]) }}</span>
                  </td>
                  <td v-if="nav !== 'audit'" class="row-actions">
                    <template v-if="nav === 'admin'"
                      ><button @click="open(adminType, r)">
                        {{ t("编辑", "Edit") }}</button
                      ><button
                        v-if="
                          [
                            'users',
                            'roles',
                            'departments',
                            'dictionaries',
                          ].includes(adminType) &&
                          !(adminType === 'departments' && r.id === 1)
                        "
                        class="danger"
                        @click="
                          open('remove', null, {
                            path: '/admin/' + adminType + '/' + r.id,
                            title: t(
                              '删除未引用记录',
                              'Delete unreferenced record',
                            ),
                          })
                        "
                      >
                        {{ t("删除", "Delete") }}
                      </button></template
                    ><template v-else-if="nav === 'consignors'"
                      ><button
                        v-if="can('master')"
                        @click="open('consignors', r)"
                      >
                        {{ t("编辑", "Edit") }}</button
                      ><button
                        v-if="can('master')"
                        class="danger"
                        @click="
                          open('remove', null, {
                            path: '/consignors/' + r.id,
                            title: t(
                              '删除未引用货主',
                              'Delete unreferenced consignor',
                            ),
                          })
                        "
                      >
                        {{ t("删除", "Delete") }}
                      </button></template
                    ><button
                      v-else-if="nav === 'settlements'"
                      @click="viewAccount(r.consignorId)"
                    >
                      {{ t("结算明细", "Account details") }}</button
                    ><button v-else-if="isSales" @click="viewItem(r.itemId)">
                      {{ t("查看商品", "View item") }}</button
                    ><template v-else
                      ><button @click="viewItem(r.id)">
                        {{ t("详情", "Details") }}</button
                      ><button v-if="mayEdit(r)" @click="open('items', r)">
                        {{ t("编辑", "Edit") }}
                      </button></template
                    >
                  </td>
                </tr>
                <tr v-if="!rows.length">
                  <td :colspan="colSet.length + 1" class="empty">
                    {{ t("暂无符合条件的记录", "No matching records") }}
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <div class="pagination">
            <span>{{ filtered.length }}{{ t("条记录", " records") }}</span>
            <div>
              <button
                class="icon"
                :aria-label="t('上一页', 'Previous page')"
                :disabled="page <= 1"
                @click="page--"
              >
                <ChevronLeft :size="18" /></button
              ><span>{{ page }} / {{ pages }}</span
              ><button
                class="icon"
                :aria-label="t('下一页', 'Next page')"
                :disabled="page >= pages"
                @click="page++"
              >
                <ChevronRight :size="18" />
              </button>
            </div>
          </div>
        </section>
      </main>
    </div>
  </div>
  <div v-if="modal" class="overlay" @keydown="keyboard">
    <form
      ref="dialog"
      class="dialog"
      role="dialog"
      aria-modal="true"
      aria-labelledby="dialog-title"
      @submit.prevent="save"
    >
      <div class="dialog-head">
        <h2 id="dialog-title">{{ modalTitle }}</h2>
        <button
          type="button"
          class="icon"
          :aria-label="t('关闭', 'Close')"
          :disabled="busy"
          @click="close"
        >
          ×
        </button>
      </div>
      <section v-if="modal.schema === 'about'" class="about-info">
        <div class="brand">
          <img src="/brand/logo.jpg" alt="知华科技" />
          <div>
            <strong>ConsignDesk 1.0.0</strong
            ><span>{{
              t("知华寄售商品与货主结算", "ZhuaTech consignment & payouts")
            }}</span>
          </div>
        </div>
        <p>
          {{
            t(
              "公开源码学习版，仅限个人学习、技术研究与非商业交流。商业使用须取得书面授权。",
              "Non-commercial source for personal learning, research and exchange. Commercial use requires written authorization.",
            )
          }}
        </p>
        <p>© 2026 上海如静知华信息科技有限公司</p>
        <p v-if="setting('companyName')">
          {{ t("门店", "Store") }}：{{ setting("companyName") }}
        </p>
        <p>
          <a href="https://www.zhuatech.cn/" target="_blank" rel="noopener">{{
            t("官网及商业咨询", "Website & commercial enquiries")
          }}</a>
          · zhuatech / zhuatech2
        </p>
        <div class="contact-codes">
          <figure v-for="code in ['zhuatech', 'zhuatech2']" :key="code">
            <img
              :src="'/brand/wechat-' + code + '.png'"
              :alt="'知华科技微信 ' + code"
            />
            <figcaption>{{ t("咨询微信", "WeChat") }}：{{ code }}</figcaption>
          </figure>
        </div>
      </section>
      <p v-if="modal.schema === 'remove'" class="hint">
        {{
          t(
            "被历史记录引用的内容不能删除。",
            "Referenced history cannot be deleted.",
          )
        }}
      </p>
      <p
        v-if="['cash', 'reversal', 'refund', 'sale'].includes(modal.schema)"
        class="hint"
      >
        {{
          t(
            "请填写已经发生的外部凭据；此操作不会执行收款、付款或退款。",
            "Record an actual external reference; this action does not transfer money.",
          )
        }}
      </p>
      <p
        v-if="modal.schema === 'password' || modal.schema === 'users'"
        class="hint"
      >
        {{
          t(
            "新密码须为12至72个字符，UTF-8不超过72字节，并包含大小写字母和数字。",
            "New password: 12–72 characters, at most 72 UTF-8 bytes, including uppercase, lowercase and digits.",
          )
        }}
      </p>
      <p v-if="modal.schema === 'cash'" class="hint">
        {{ t("到期可支付", "Payable now") }}
        {{ cash(account.balance.payable) }} · {{ t("待追回", "Recovery due") }}
        {{ cash(account.balance.recoveryDue) }}
      </p>
      <p v-if="modal.action === 'approve'" class="hint">
        {{
          t(
            "核实货主已同意当前底价、分成、等待期及到期日，再填写约定依据。",
            "Verify the owner accepted the floor price, share, waiting period and expiry, then record the evidence.",
          )
        }}
      </p>
      <div v-if="modal.schema === 'import'" class="import-panel">
        <button type="button" @click="template">
          {{ t("下载当前在售商品模板", "Download available-item template") }}
        </button>
        <p class="hint">itemReference, price, externalReference, revision</p>
        <label
          >{{ t("选择CSV（最多300行 / 512KiB）", "CSV (max 300 rows / 512KiB)")
          }}<input type="file" accept=".csv,text/csv" @change="importFile"
        /></label>
        <p>
          {{ importRows.length
          }}{{
            t(
              "行待导入；任一失败整批回滚",
              " rows ready; any failure rolls back the batch",
            )
          }}
        </p>
        <div v-if="importRows.length" class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>{{ t("商品", "Item") }}</th>
                <th>{{ t("金额", "Amount") }}</th>
                <th>{{ t("凭据", "Reference") }}</th>
                <th>{{ t("版本", "Version") }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="r in importRows.slice(0, 20)" :key="r.itemReference">
                <td>{{ r.itemReference }}</td>
                <td>{{ r.price }}</td>
                <td>{{ r.externalReference }}</td>
                <td>{{ r.revision }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
      <p
        v-if="modal.schema === 'items' && setting('consignmentNotice')"
        class="hint"
      >
        {{ setting("consignmentNotice") }}
      </p>
      <div class="form-fields">
        <template v-for="f in fields" :key="f.key"
          ><fieldset v-if="f.type === 'permissions'">
            <legend>{{ t(f.zh, f.en) }}</legend>
            <label
              v-for="p in adminData.permissions"
              :key="p.id"
              class="checkbox"
              ><input
                v-model="form.permissions"
                type="checkbox"
                :value="p.code"
              />{{ p.name }}</label
            >
          </fieldset>
          <label v-else-if="f.type === 'checkbox'" class="checkbox"
            ><input v-model="form[f.key]" type="checkbox" />{{
              t(f.zh, f.en)
            }}</label
          ><label v-else
            >{{ t(f.zh, f.en)
            }}<select
              v-if="f.type === 'select'"
              v-model="form[f.key]"
              :disabled="fieldLimits(modal, f).disabled"
              :required="
                !f.optional ||
                (modal.schema === 'users' && f.key === 'password' && !modal.row)
              "
            >
              <option :value="null">
                {{
                  t(
                    f.optional ? "无" : "请选择",
                    f.optional ? "None" : "Select",
                  )
                }}
              </option>
              <option v-for="o in options(f)" :key="o.value" :value="o.value">
                {{ o.label }}
              </option></select
            ><textarea
              v-else-if="f.type === 'textarea'"
              v-model="form[f.key]"
              :required="
                !f.optional ||
                (modal.schema === 'users' && f.key === 'password' && !modal.row)
              "
              rows="3"
              :maxlength="fieldLimits(modal, f).maxLength"
            ></textarea
            ><input
              v-else
              v-model="form[f.key]"
              :type="f.type"
              :required="
                !f.optional ||
                (modal.schema === 'users' && f.key === 'password' && !modal.row)
              "
              :step="f.type === 'number' ? 1 : undefined"
              :autocomplete="f.type === 'password' ? 'new-password' : 'off'"
              :maxlength="fieldLimits(modal, f).maxLength"
              :min="fieldLimits(modal, f).min"
              :max="fieldLimits(modal, f).max"
              :pattern="fieldLimits(modal, f).pattern"
              :disabled="fieldLimits(modal, f).disabled"
              :inputmode="
                [
                  'askingPrice',
                  'minimumPrice',
                  'ownerPercent',
                  'amount',
                  'price',
                ].includes(f.key)
                  ? 'decimal'
                  : undefined
              " /></label
        ></template>
      </div>
      <p v-if="error" role="alert" class="error">{{ error }}</p>
      <div class="dialog-actions">
        <button type="button" :disabled="busy" @click="close">
          {{
            modal.schema === "about" ? t("关闭", "Close") : t("取消", "Cancel")
          }}</button
        ><button
          v-if="modal.schema !== 'about'"
          class="primary"
          :disabled="
            busy ||
            uncertain ||
            (modal.schema === 'import' && !importRows.length)
          "
        >
          {{ busy ? t("保存中…", "Saving…") : t("确认保存", "Confirm") }}
        </button>
      </div>
    </form>
  </div>
</template>
