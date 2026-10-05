// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
import test from "node:test";
import assert from "node:assert/strict";
import { parseSales, cloneForm } from "./consign.js";
const head = "itemReference,price,externalReference,revision";
test("actual sale imports retain exact price text, quoted references, BOM and CRLF", () => {
  const r = parseSales("\uFEFF" + head + '\r\nCI-1,100.01,"POS,1",3\r\n');
  assert.equal(r[0].price, "100.01");
  assert.equal(r[0].externalReference, "POS,1");
  assert.equal(r[0].revision, 3);
});
test("malformed, fractional revisions and silently rounded prices are rejected", () => {
  for (const row of [
    "CI-1,1.001,P,1",
    "CI-1,0,P,1",
    "CI-1,1,P,1.5",
    "CI-1,1,P,9007199254740993",
    "CI-1,1,P,",
    '"CI-1,1,P,1',
  ])
    assert.throws(() => parseSales(head + "\n" + row));
});
test("duplicate items and receipts would otherwise cause partial imports", () => {
  assert.throws(() => parseSales(head + "\nCI-1,1,P1,1\nCI-1,2,P2,1"));
  assert.throws(() => parseSales(head + "\nCI-1,1,P,1\nCI-2,2,P,1"));
});
test("bounded file bytes and row count before any request", () => {
  assert.throws(() => parseSales("x".repeat(524289)));
  assert.throws(() =>
    parseSales(
      head +
        "\n" +
        Array.from({ length: 301 }, (_, i) => `CI-${i},1,P-${i},1`).join("\n"),
    ),
  );
});

import { reactive } from "vue";
test("editing a Vue reactive record clones independently without Proxy cloning failure", () => {
  const row = reactive({ id: 1, name: "TEST item", permissions: ["read"] });
  const draft = cloneForm(row);
  draft.name = "Changed";
  draft.permissions.push("process");
  assert.equal(row.name, "TEST item");
  assert.deepEqual([...row.permissions], ["read"]);
});

test("CSV diagnostics identify the invalid data record and distinguish price from duplicate references", () => {
  assert.throws(() => parseSales(head + "\nCI-1,10.01,P1,1\nCI-2,1.001,P2,1"), {
    message: "INVALID_IMPORT",
    recordNumber: 2,
    reason: "IMPORT_PRICE",
  });
  assert.throws(
    () => parseSales(head + "\nCI-1,10.01,P1,1\n CI-1 ,10.01,P2,1"),
    { message: "INVALID_IMPORT", recordNumber: 2, reason: "IMPORT_DUPLICATE" },
  );
  assert.equal(
    parseSales(head + "\n CI-1 , 10.01 , P1 , 1 ")[0].externalReference,
    "P1",
  );
});
