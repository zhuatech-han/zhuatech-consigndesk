// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
/** 货币只用于显示，计算在服务端使用十进制。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function money(value, currency = "CNY", lang = "zh") {
  return new Intl.NumberFormat(lang === "en" ? "en-GB" : "zh-CN", {
    style: "currency",
    currency,
  }).format(Number(value || 0));
}
/** 按筛选和排序取有界页面，不修改原列表。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function pageRows(rows, search, page, size, descending = true) {
  return rows
    .filter((x) => searchText(x).includes(search.toLowerCase()))
    .toSorted((a, b) =>
      descending ? Number(b.id) - Number(a.id) : Number(a.id) - Number(b.id),
    )
    .slice((page - 1) * size, page * size);
}

/** 搜索只匹配业务值，避免字段名导致误匹配。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function searchText(value) {
  if (value === null || value === undefined) return "";
  if (typeof value === "object")
    return Object.values(value).map(searchText).join(" ");
  return String(value).toLowerCase();
}
