// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
import { toRaw } from "vue";
/** 拷贝响应式表单对象，编辑不提前改变列表，兼容 Vue Proxy。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function cloneForm(row) {
  return structuredClone(row ? toRaw(row) : {});
}
/** 有限CSV解析：引号转义、CRLF、BOM及多行字段；结构异常整批拒绝。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function parseSales(text) {
  if (new TextEncoder().encode(text).length > 524288)
    throw new Error("IMPORT_TOO_LARGE");
  text = text.replace(/^\uFEFF/, "");
  const records = [];
  let row = [],
    value = "",
    quoted = false,
    closed = false;
  const field = () => {
    row.push(value);
    value = "";
    closed = false;
  };
  const end = () => {
    field();
    if (row.some((x) => x !== "")) records.push(row);
    row = [];
  };
  for (let i = 0; i < text.length; i++) {
    const c = text[i];
    if (quoted) {
      if (c === '"') {
        if (text[i + 1] === '"') {
          value += '"';
          i++;
        } else {
          quoted = false;
          closed = true;
        }
      } else value += c;
      continue;
    }
    if (c === '"') {
      if (value || closed) throw new Error("INVALID_IMPORT");
      quoted = true;
    } else if (c === ",") field();
    else if (c === "\r" || c === "\n") {
      if (c === "\r" && text[i + 1] === "\n") i++;
      end();
    } else {
      if (closed) throw new Error("INVALID_IMPORT");
      value += c;
    }
  }
  if (quoted) throw new Error("INVALID_IMPORT");
  if (value || row.length || closed) end();
  const headers = ["itemReference", "price", "externalReference", "revision"];
  if (
    !records.length ||
    records[0].join(",") !== headers.join(",") ||
    records.length < 2 ||
    records.length > 301
  )
    throw new Error("INVALID_IMPORT");
  const items = new Set(),
    receipts = new Set();
  return records.slice(1).map((values, index) => {
    const r = values.map((v) => v.trim());
    const reject = (reason) => {
      throw Object.assign(new Error("INVALID_IMPORT"), {
        recordNumber: index + 1,
        reason,
      });
    };
    if (r.length !== 4) reject("IMPORT_FIELDS");
    if (!r[0] || !r[2] || r[0].length > 60 || r[2].length > 100)
      reject("IMPORT_REFERENCE");
    if (
      !/^\d+(\.\d{1,2})?$/.test(r[1]) ||
      Number(r[1]) <= 0 ||
      Number(r[1]) > 999999.99
    )
      reject("IMPORT_PRICE");
    if (!/^\d+$/.test(r[3]) || !Number.isSafeInteger(Number(r[3])))
      reject("IMPORT_REVISION");
    if (items.has(r[0]) || receipts.has(r[2])) reject("IMPORT_DUPLICATE");
    items.add(r[0]);
    receipts.add(r[2]);
    return {
      itemReference: r[0],
      price: r[1],
      externalReference: r[2],
      revision: Number(r[3]),
    };
  });
}

/** CSV数据记录编号不冒充物理行号，明确指出可修改的字段。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export const importHints = {
  IMPORT_FIELDS: [
    "须包含4列，请使用下载的模板",
    "Expected four columns; use the downloaded template",
  ],
  IMPORT_REFERENCE: [
    "商品编号和销售凭据不能为空，长度分别最多60和100个字符",
    "Item and sale references are required, max 60 and 100 characters",
  ],
  IMPORT_PRICE: [
    "金额须为正数、最多两位小数，最高999999.99",
    "Use a positive amount with at most two decimals, max 999999.99",
  ],
  IMPORT_REVISION: [
    "版本号须为整数，请保留模板中的版本",
    "Use the integer revision from the template",
  ],
  IMPORT_DUPLICATE: [
    "同一文件内商品编号或销售凭据重复",
    "Duplicate item or sale reference in this file",
  ],
  IMPORT_UNAVAILABLE: [
    "商品不存在或不在售，请关闭窗口刷新后重新下载模板",
    "Item missing or unavailable; close, refresh and download a new template",
  ],
  IMPORT_VERSION: [
    "商品版本已变化，请关闭窗口刷新后重新下载模板",
    "Item revision changed; close, refresh and download a new template",
  ],
};
/** 寄售状态的中英文显示。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export const states = {
  DRAFT: ["草稿", "Draft"],
  SUBMITTED: ["待审核", "Submitted"],
  APPROVED: ["待收货", "Approved"],
  AVAILABLE: ["在售", "Available"],
  SOLD: ["已售", "Sold"],
  REFUNDED: ["已退款", "Refunded"],
  COLLECTED: ["已取回", "Collected"],
  CANCELLED: ["已取消", "Cancelled"],
  PAYOUT: ["已付款登记", "Payout"],
  RECOVERY: ["已追回登记", "Recovery"],
  REVERSAL: ["资金冲正", "Reversal"],
  SALE: ["销售分成", "Sale share"],
  REFUND: ["退款冲减", "Refund debit"],
  DRAFT_SAVE: ["保存草稿", "Draft saved"],
  SUBMIT: ["提交审核", "Submitted for review"],
  WITHDRAW: ["撤回申请", "Submission withdrawn"],
  APPROVE: ["审核通过", "Terms approved"],
  REJECT: ["退回修改", "Returned for revision"],
  RECEIVE: ["收货上架", "Item received"],
  RENEW: ["登记续期", "Renewal recorded"],
  COLLECT: ["登记取回", "Collection recorded"],
  CANCEL: ["取消寄售", "Consignment cancelled"],
};
/** 显示服务端拒绝原因，不把错误伪装为成功。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export const errors = {
  UNAUTHENTICATED: ["登录已失效，请重新登录", "Session expired; sign in again"],
  LOGIN_FAILED: ["账号或密码错误", "Incorrect username or password"],
  LOGIN_THROTTLED: ["尝试过多，请稍后重试", "Too many attempts; try later"],
  FORBIDDEN: ["没有此操作权限", "Not permitted"],
  OUT_OF_SCOPE: ["记录不属于当前账号", "Outside your account scope"],
  INVALID_INPUT: [
    "请检查必填字段、格式及长度",
    "Check required fields, formats and lengths",
  ],
  INVALID_STATE: [
    "当前状态不能进行此操作",
    "Action not allowed in the current state",
  ],
  VERSION_CONFLICT: [
    "记录已被修改，请关闭窗口后刷新并核对",
    "Record changed; close the dialog, refresh and review",
  ],
  INVALID_MONEY: [
    "金额须为正数、最多两位小数，最高999999.99",
    "Positive amount, max two decimals and 999999.99",
  ],
  INVALID_PERCENT: [
    "分成须大于0且不超过100，最多两位小数",
    "Share must be >0 and <=100, max two decimals",
  ],
  INVALID_DATE: ["日期不符合范围，请核对", "Review the date range"],
  INVALID_HOLD_DAYS: [
    "结算等待期须为0至90天的整数",
    "Waiting period must be 0–90 whole days",
  ],
  NO_ACTIVE_CONSIGNOR: [
    "请先在货主档案中新建或启用货主；无维护权限请联系管理员",
    "Create or enable a consignor first; ask an administrator if you cannot maintain consignors",
  ],
  NO_ACTIVE_CATEGORY: [
    "请管理员在账号与设置的分类字典中启用商品分类",
    "Ask an administrator to enable an item category in Accounts & settings",
  ],
  INVALID_CATEGORY: ["请选择启用的商品分类", "Select an active category"],
  INDEPENDENT_REVIEW_REQUIRED: [
    "须由其他工作人员审核",
    "Another staff member must review",
  ],
  EXPIRED: [
    "寄售已到期，须核实续期后才能销售",
    "Consignment expired; verify a renewal first",
  ],
  BELOW_MINIMUM: [
    "标价或实际售价不能低于最低售价",
    "Asking or actual sale price cannot be below the minimum price",
  ],
  INSUFFICIENT_PAYABLE: [
    "金额超过到期可支付余额",
    "Amount exceeds matured payable balance",
  ],
  EXCESS_RECOVERY: ["金额超过待追回余额", "Amount exceeds the recovery due"],
  ALREADY_REFUNDED: ["此销售已退款", "Sale already refunded"],
  ALREADY_REVERSED: ["此资金记录已冲正", "Cash record already reversed"],
  CONFLICT: [
    "凭据重复或记录正在被引用",
    "Duplicate reference or record is in use",
  ],
  CONSIGNOR_INACTIVE: [
    "货主已停用或账号部门不一致",
    "Consignor inactive or department mismatch",
  ],
  CONSIGNOR_ROLE_REQUIRED: [
    "货主账号只能使用本人寄售角色",
    "A consignor account requires a portal-only role",
  ],
  CONSIGNOR_BINDING_REQUIRED: [
    "请绑定货主并设置本人数据范围",
    "Bind a consignor with consignor scope",
  ],
  CONSIGNOR_BINDING_IMMUTABLE: [
    "现有账号不能更换货主绑定",
    "Existing account cannot change consignor",
  ],
  NOT_CREATOR: [
    "只能编辑本人建立的草稿",
    "Only the creator may edit this draft",
  ],
  HISTORY_RETAINED: [
    "已提交过的记录必须保留历史",
    "Previously submitted records retain history",
  ],
  LAST_ADMIN: [
    "必须保留一个可用管理员",
    "An enabled administrator must remain",
  ],
  DEPARTMENT_LOCKED: [
    "已有商品或账号，不能移动货主部门",
    "Items or accounts prevent a department move",
  ],
  CURRENCY_LOCKED: [
    "已有业务记录，不能更换币种",
    "Currency is locked after the first item",
  ],
  WEAK_PASSWORD: [
    "密码须为12至72个字符，UTF-8不超过72字节，并含大小写字母及数字",
    "Password: 12–72 characters, max 72 UTF-8 bytes, with upper/lowercase and digits",
  ],
  INVALID_IMPORT: [
    "CSV格式、数量、版本或重复行错误",
    "Invalid CSV rows, versions or duplicates",
  ],
  DUPLICATE_IMPORT: [
    "导入有重复商品或凭据",
    "Duplicate item or reference in import",
  ],
  IMPORT_TOO_LARGE: ["CSV不能超过512KiB", "CSV must not exceed 512KiB"],
  UPLOAD_TOO_LARGE: [
    "请求内容过大，请缩小文件或减少行数",
    "Request is too large; reduce file size or row count",
  ],
  NETWORK_ERROR: [
    "请求未完成，请检查连接",
    "Request failed; check the connection",
  ],
  RESULT_UNKNOWN: [
    "未收到操作结果，操作可能已完成。请关闭此窗口并刷新核对记录或凭据，勿直接重复提交。",
    "No result received; the action may be complete. Close this dialog and refresh to check the record or reference before submitting again.",
  ],
  NOT_FOUND: [
    "记录已不存在，请刷新列表",
    "Record no longer exists; refresh the list",
  ],
  OLD_PASSWORD_INVALID: [
    "当前密码不正确，请重新输入",
    "Current password is incorrect; enter it again",
  ],
  INVALID_USERNAME: [
    "账号需3至60个字母、数字、下划线、点或连字符",
    "Username: 3–60 letters, digits, underscores, dots or hyphens",
  ],
  INVALID_CODE: [
    "货主代码只能使用字母、数字、下划线、点或连字符，最多60个字符",
    "Consignor code: letters, digits, underscores, dots or hyphens, max 60 characters",
  ],
  INVALID_SCOPE: [
    "请核对数据范围，管理权限必须使用全部范围",
    "Review the data scope; administration requires All scope",
  ],
  INVALID_PERMISSION: [
    "权限不存在，请刷新后重新选择",
    "Permission no longer exists; refresh and select again",
  ],
  INVALID_CURRENCY: [
    "请输入支持两位小数的币种代码，如CNY、USD或EUR",
    "Use a currency code with two decimal places, such as CNY, USD or EUR",
  ],
  DICTIONARY_KEY_IMMUTABLE: [
    "已有字典的类型和代码不能修改，请新建分类",
    "Dictionary type and code cannot change; create a new category",
  ],
  REGISTERED_PERMISSIONS_ONLY: [
    "仅能修改系统已有权限名称",
    "Only existing permission names can be edited",
  ],
  REGISTERED_MENUS_ONLY: [
    "仅能维护系统已有菜单",
    "Only existing menus can be maintained",
  ],
  REGISTERED_SETTINGS_ONLY: [
    "仅能维护系统已有参数",
    "Only existing settings can be maintained",
  ],
  INVALID_SETTING: [
    "系统不支持此参数，请刷新后重选",
    "Setting is unsupported; refresh and select again",
  ],
  BUILTIN_RESOURCE: [
    "系统基础记录不能删除，可修改允许维护的字段",
    "System records cannot be deleted; edit supported fields",
  ],
  INVALID_REVERSAL: [
    "请选择当前货主的原资金记录，不能冲正冲正记录",
    "Select an original payment for this consignor; reversals cannot be reversed",
  ],
  INVALID_CASH_KIND: [
    "请选择付款或追回，冲正请从原凭据处操作",
    "Choose payout or recovery; reverse from the original payment record",
  ],
  RESOURCE_LIMIT: [
    "记录超过当前接口一万条上限，请联系部署人员处理",
    "The 10,000-record API limit was reached; contact the deployment administrator",
  ],
  INTERNAL_ERROR: [
    "服务暂时异常，请稍后刷新核对记录，再联系部署人员",
    "Service error; refresh and verify records, then contact the deployment administrator",
  ],
  CSRF_EXPIRED: [
    "会话已变化，请重新登录后核对",
    "Session changed; sign in and review",
  ],
};
