// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
/** 写入失败保留输入，写入成功后只重载读取；禁止因重载失败重发写入。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export async function commitChange(write, committed, refresh) {
  await write();
  committed();
  try {
    await refresh();
    return null;
  } catch (error) {
    return error;
  }
}

/** 余额决定实际可登记的交接类型，零余额不产生零金额付款。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function cashDraft(balance) {
  if (Number(balance.recoveryDue) > 0)
    return { kind: "RECOVERY", amount: balance.recoveryDue };
  if (Number(balance.payable) > 0)
    return { kind: "PAYOUT", amount: balance.payable };
  return null;
}

/** 删除入口仅用于尚未提交的本人草稿；退回及撤回的历史须保留。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function removableDraft(detail, me) {
  return Boolean(
    detail &&
    detail.item.status === "DRAFT" &&
    detail.item.creatorId === me?.id &&
    (me.consignorId != null || me.permissions.includes("process")) &&
    detail.events.every((event) => event.action === "DRAFT_SAVE"),
  );
}
