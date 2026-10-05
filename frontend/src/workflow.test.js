// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
import test from "node:test";
import assert from "node:assert/strict";
import { commitChange, cashDraft, removableDraft } from "./workflow.js";
import { api, resetCsrf } from "./api.js";
import { fieldLimits } from "./schema.js";

test("payment committed but refresh unavailable: close input, report saved, never replay money", async () => {
  const original = globalThis.fetch;
  let writes = 0;
  const view = {
    dialog: true,
    draft: { amount: "40.00", externalReference: "TEST-payment" },
    saved: false,
  };
  globalThis.fetch = async (url) => {
    if (url === "/api/auth/csrf")
      return new Response('{"header":"X-CSRF-TOKEN","token":"TEST"}');
    if (url === "/api/cash") {
      writes++;
      return new Response('{"id":1}');
    }
    throw new Error("Connection lost while reloading");
  };
  try {
    resetCsrf();
    const refreshError = await commitChange(
      () => api("/cash", "POST", view.draft),
      () => {
        view.dialog = false;
        view.draft = null;
        view.saved = true;
      },
      () => api("/accounts/1"),
    );
    assert.equal(writes, 1);
    assert.equal(view.saved, true);
    assert.equal(view.dialog, false);
    assert.equal(refreshError.message, "NETWORK_ERROR");
  } finally {
    globalThis.fetch = original;
    resetCsrf();
  }
});

test("rejected payment keeps its reference and amount and does not show success or reload", async () => {
  const draft = { amount: "40.00", externalReference: "TEST-payment" };
  let committed = false,
    refreshed = false;
  await assert.rejects(
    () =>
      commitChange(
        async () => {
          throw new Error("INSUFFICIENT_PAYABLE");
        },
        () => {
          committed = true;
        },
        async () => {
          refreshed = true;
        },
      ),
    { message: "INSUFFICIENT_PAYABLE" },
  );
  assert.equal(committed, false);
  assert.equal(refreshed, false);
  assert.deepEqual(draft, {
    amount: "40.00",
    externalReference: "TEST-payment",
  });
});

test("paid sale then returned item selects recovery rather than a zero payout", () => {
  assert.deepEqual(cashDraft({ payable: "0.00", recoveryDue: "60.01" }), {
    kind: "RECOVERY",
    amount: "60.01",
  });
  assert.deepEqual(cashDraft({ payable: "40.00", recoveryDue: "0.00" }), {
    kind: "PAYOUT",
    amount: "40.00",
  });
  assert.equal(
    cashDraft({ payable: "0.00", recoveryDue: "0.00", held: "100.00" }),
    null,
  );
});

test("withdrawn or returned applications keep history even though they become editable drafts", () => {
  const me = { id: 1, consignorId: null, permissions: ["read", "process"] };
  const detail = {
    item: { status: "DRAFT", creatorId: 1 },
    events: [{ action: "DRAFT_SAVE" }],
  };
  assert.equal(removableDraft(detail, me), true);
  for (const action of ["SUBMIT", "WITHDRAW", "REJECT"]) {
    assert.equal(
      removableDraft({ ...detail, events: [...detail.events, { action }] }, me),
      false,
    );
  }
  assert.equal(removableDraft(detail, { ...me, permissions: ["read"] }), false);
  assert.equal(removableDraft(detail, { ...me, id: 2 }), false);
});

test("edit form respects immutable account binding and review evidence length", () => {
  assert.equal(
    fieldLimits(
      { schema: "users", row: { id: 1 } },
      { key: "consignorId", type: "select" },
    ).disabled,
    true,
  );
  assert.equal(
    fieldLimits(
      { schema: "users", row: null },
      { key: "consignorId", type: "select" },
    ).disabled,
    false,
  );
  assert.equal(
    fieldLimits(
      { schema: "action", action: "approve" },
      { key: "note", type: "textarea" },
    ).maxLength,
    500,
  );
  assert.equal(
    fieldLimits(
      { schema: "consignors" },
      { key: "contactNote", type: "textarea" },
    ).maxLength,
    500,
  );
});

test("browser validation patterns accept usable codes and reject unexpected characters under Unicode v mode", () => {
  const account = new RegExp(
    "^(?:" +
      fieldLimits({ schema: "users" }, { key: "username" }).pattern +
      ")$",
    "v",
  );
  assert.ok(account.test("staff-01"));
  assert.ok(account.test("staff_01"));
  assert.equal(account.test("staff 01"), false);
  assert.equal(account.test("ab"), false);
  const currency = new RegExp(
    "^(?:" +
      fieldLimits(
        { schema: "settings", row: { code: "currency" } },
        { key: "value" },
      ).pattern +
      ")$",
    "v",
  );
  assert.ok(currency.test("CNY"));
  assert.equal(currency.test("cny"), false);
});
