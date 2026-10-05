// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
const f = (key, zh, en, type = "text", options = null, optional = false) => ({
  key,
  zh,
  en,
  type,
  options,
  optional,
});
/** 货主、商品、价表与账号表单，字段对应真实接口。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export const forms = {
  users: [
    f("username", "登录账号", "Username"),
    f("displayName", "显示姓名", "Display name"),
    f(
      "password",
      "密码（新建必填，修改可留空）",
      "Password (required for new accounts)",
      "password",
      null,
      true,
    ),
    f("roleId", "角色", "Role", "select", "roles"),
    f("departmentId", "部门", "Department", "select", "departments"),
    f(
      "consignorId",
      "绑定货主（员工留空）",
      "Consignor (blank for staff)",
      "select",
      "consignors",
      true,
    ),
    f("enabled", "启用", "Enabled", "checkbox"),
  ],
  roles: [
    f("name", "角色名称", "Role name"),
    f("scope", "数据范围", "Data scope", "select", [
      ["ALL", "全部", "All"],
      ["DEPARTMENT", "本部门", "Department"],
      ["CONSIGNOR", "绑定货主", "Bound consignor"],
    ]),
    f("permissions", "权限", "Permissions", "permissions"),
  ],
  departments: [f("name", "部门名称", "Department name")],
  permissions: [f("name", "权限显示名称", "Permission display name")],
  menus: [
    f("name", "菜单名称", "Menu name"),
    f("nameEn", "英文名称", "English name"),
    f(
      "permissionCode",
      "所需权限",
      "Required permission",
      "select",
      "permissions",
    ),
    f("position", "顺序", "Position", "number"),
    f("enabled", "启用", "Enabled", "checkbox"),
  ],
  dictionaries: [
    f("type", "字典类型", "Dictionary type"),
    f("code", "代码", "Code"),
    f("name", "名称", "Name"),
    f("nameEn", "英文名称", "English name"),
    f("enabled", "启用", "Enabled", "checkbox"),
  ],
  settings: [f("value", "设置值", "Value", "textarea")],

  password: [
    f("oldPassword", "当前密码", "Current password", "password"),
    f("newPassword", "新密码", "New password", "password"),
  ],
};

/** 货主及寄售实物输入；金额使用文本避免二进制浮点写入。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
Object.assign(forms, {
  consignors: [
    f("code", "货主代码", "Consignor code"),
    f("name", "货主名称", "Consignor name"),
    f("departmentId", "所属部门", "Department", "select", "departments"),
    f("contactNote", "联系备注", "Contact note", "textarea", null, true),
    f("enabled", "启用", "Enabled", "checkbox"),
  ],
  items: [
    f("consignorId", "货主", "Consignor", "select", "consignors"),
    f("name", "实物名称", "Item name"),
    f("category", "分类", "Category", "select", "categories"),
    f("conditionNote", "实物与成色说明", "Item and condition", "textarea"),
    f("askingPrice", "标价", "Asking price"),
    f("minimumPrice", "最低售价", "Minimum sale price"),
    f("ownerPercent", "货主分成 %", "Consignor share %"),
    f("holdDays", "销售后结算等待天数", "Payout waiting days", "number"),
    f("expiresOn", "寄售到期日（UTC）", "Expiry date (UTC)", "date"),
  ],
  sale: [
    f("price", "实际销售金额", "Actual sale amount"),
    f("externalReference", "外部销售凭据", "External sale reference"),
  ],
  cash: [
    f("kind", "记录类型", "Record kind", "select", [
      ["PAYOUT", "已付款登记", "Payout record"],
      ["RECOVERY", "已追回登记", "Recovery record"],
    ]),
    f("amount", "金额", "Amount"),
    f("externalReference", "外部资金凭据", "External payment reference"),
    f("note", "实际发生说明", "Evidence note", "textarea"),
  ],
  reversal: [
    f("externalReference", "冲正凭据", "Reversal reference"),
    f("note", "实际冲正说明", "Reversal evidence", "textarea"),
  ],
  refund: [
    f("externalReference", "退款凭据", "Refund reference"),
    f(
      "note",
      "全额退款与实物归还说明",
      "Full refund and returned item",
      "textarea",
    ),
  ],
  action: [f("note", "依据或原因", "Evidence or reason", "textarea")],
  receive: [
    f("externalReference", "收货凭据", "Intake reference"),
    f(
      "note",
      "实物核验与收货说明",
      "Inspection and receipt evidence",
      "textarea",
    ),
  ],
  collect: [
    f("externalReference", "取回凭据", "Collection reference"),
    f("note", "实物交接说明", "Handover evidence", "textarea"),
  ],
  renew: [
    f("expiresOn", "新到期日", "New expiry date", "date"),
    f(
      "note",
      "货主同意续期的外部依据",
      "External renewal agreement",
      "textarea",
    ),
  ],
});

/** 输入边界对应接口约束，字典键及已有商品的货主绑定不可改写。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function fieldLimits(modal, field) {
  const { schema, row, action, item } = modal;
  const key = field.key;
  const limits = { maxLength: 120 };
  if (key === "username")
    Object.assign(limits, { maxLength: 60, pattern: "[a-zA-Z0-9_.\\-]{3,60}" });
  if (key === "code" || key === "type") limits.maxLength = 60;
  if (schema === "consignors" && key === "code")
    limits.pattern = "[A-Za-z0-9_.\\-]{1,60}";
  if (key === "name" && ["items", "consignors"].includes(schema))
    limits.maxLength = 160;
  if (field.type === "password") limits.maxLength = 72;
  if (field.type === "textarea")
    limits.maxLength =
      key === "contactNote" || ["approve", "reject"].includes(action)
        ? 500
        : 1000;
  if (schema === "settings")
    limits.maxLength =
      row.code === "companyName" ? 160 : row.code === "currency" ? 3 : 1000;
  if (schema === "settings" && row.code === "currency")
    limits.pattern = "[A-Z]{3}";
  if (key === "externalReference") limits.maxLength = 100;
  if (
    ["askingPrice", "minimumPrice", "ownerPercent", "amount", "price"].includes(
      key,
    )
  ) {
    limits.maxLength = 12;
    limits.pattern = "[0-9]+([.][0-9]{1,2})?";
  }
  if (key === "holdDays") Object.assign(limits, { min: 0, max: 90 });
  if (field.type === "date") {
    const today = Date.now();
    limits.min = new Date(today).toISOString().slice(0, 10);
    limits.max = new Date(today + 730 * 86400000).toISOString().slice(0, 10);
    if (schema === "renew")
      limits.min = [
        limits.min,
        new Date(Date.parse(item.expiresOn + "T00:00:00Z") + 86400000)
          .toISOString()
          .slice(0, 10),
      ]
        .sort()
        .at(-1);
  }
  limits.disabled = Boolean(
    row &&
    ((schema === "dictionaries" && ["type", "code"].includes(key)) ||
      (schema === "items" && key === "consignorId") ||
      (schema === "users" && key === "consignorId")),
  );
  return limits;
}
