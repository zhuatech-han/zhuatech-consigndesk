// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
import { test } from "node:test";
import { strict as assert } from "node:assert";
import { money, pageRows } from "./format.js";
test("currency supports both markets", () => {
  assert.match(money("1234.50", "USD", "en"), /1,234.50/);
  assert.match(money("-20.01"), /-/);
});
test("search then sort then paginate without mutation", () => {
  const rows = [
    { id: 1, name: "A" },
    { id: 3, name: "B" },
    { id: 2, name: "A" },
  ];
  assert.deepEqual(
    pageRows(rows, "A", 1, 1).map((x) => x.id),
    [2],
  );
  assert.deepEqual(
    pageRows(rows, "A", 2, 1).map((x) => x.id),
    [1],
  );
  assert.equal(rows[0].id, 1);
});
