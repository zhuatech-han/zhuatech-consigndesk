// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { parse, compileScript } from "@vue/compiler-sfc";
import { createSSRApp, h, nextTick } from "vue";
import { renderToString } from "@vue/server-renderer";
import { resetCsrf } from "./api.js";

// Execute the actual Vue setup in SSR; this checks state transitions, not browser clicks or layout.
const { descriptor } = parse(
  readFileSync(new URL("./App.vue", import.meta.url), "utf8"),
);
const compiled = compileScript(descriptor, {
  id: "consigndesk-flow-test",
}).content.replace(
  /from ["']([^"']+)["']/g,
  (_, path) => `from ${JSON.stringify(import.meta.resolve(path))}`,
);
const component = (
  await import(
    "data:text/javascript;base64," + Buffer.from(compiled).toString("base64")
  )
).default;
async function setup() {
  const storage = globalThis.localStorage;
  globalThis.localStorage = { getItem: () => "zh", setItem: () => {} };
  let state;
  try {
    await renderToString(
      createSSRApp({
        setup(props, context) {
          state = component.setup(props, context);
          return () => h("span");
        },
      }),
    );
    return state;
  } finally {
    globalThis.localStorage = storage;
  }
}
const profile = {
  id: 1,
  departmentId: 1,
  consignorId: null,
  scope: "ALL",
  permissions: ["read", "process", "finance", "admin"],
  menus: [{ code: "admin" }],
};

test("actual component expires an open private form without blocking the login page", async () => {
  const state = await setup();
  state.me.value = profile;
  state.modal.value = { schema: "users" };
  state.form.value = { password: "TEST-private-input" };
  state.items.value = [{ id: 1 }];
  state.adminData.value = { users: [{ id: 1 }] };
  state.detail.value = { item: { id: 1 } };
  state.account.value = { consignor: { id: 1 } };
  state.login.value.password = "TEST-private-login";
  state.fail(new Error("UNAUTHENTICATED"));
  await nextTick();
  assert.equal(state.me.value, null);
  assert.equal(state.modal.value, null);
  assert.deepEqual(state.form.value, {});
  assert.deepEqual(state.items.value, []);
  assert.deepEqual(state.adminData.value, {});
  assert.equal(state.detail.value, null);
  assert.equal(state.account.value, null);
  assert.equal(state.login.value.password, "");
  assert.match(state.error.value, /重新登录/);
});

test("actual save keeps denied input, clears stale success, and separates committed writes from refresh failures", async () => {
  const original = globalThis.fetch;
  try {
    for (const denied of [true, false]) {
      resetCsrf();
      const state = await setup();
      state.me.value = profile;
      state.nav.value = "admin";
      state.modal.value = { schema: "users" };
      state.form.value = {
        username: "TEST-new",
        displayName: "TEST",
        password: "TEST-private-input",
        roleId: 1,
        departmentId: 1,
      };
      state.notice.value = "Previous operation saved";
      let writes = 0;
      globalThis.fetch = async (url) => {
        if (url === "/api/auth/csrf")
          return new Response('{"header":"X-CSRF-TOKEN","token":"TEST"}');
        if (url === "/api/admin/users") {
          writes++;
          return denied
            ? new Response('{"code":"INVALID_USERNAME"}', { status: 400 })
            : new Response('{"id":2}');
        }
        throw new Error("Refresh unavailable");
      };
      await state.save();
      assert.equal(writes, 1);
      assert.equal(state.busy.value, false);
      if (denied) {
        assert.ok(state.modal.value);
        assert.equal(state.form.value.password, "TEST-private-input");
        assert.equal(state.notice.value, "");
        assert.match(state.error.value, /账号需3至60/);
      } else {
        assert.equal(state.modal.value, null);
        assert.deepEqual(state.form.value, {});
        assert.equal(state.notice.value, "已保存");
        assert.match(state.error.value, /记录已保存.*勿重复登记/);
      }
    }
  } finally {
    globalThis.fetch = original;
    resetCsrf();
  }
});

test("actual refresh applies revoked permissions and clears data from the former scope even if reloading fails", async () => {
  const original = globalThis.fetch;
  const state = await setup();
  state.me.value = profile;
  state.nav.value = "admin";
  state.items.value = [{ id: 1, name: "TEST former department" }];
  state.adminData.value = { users: [{ id: 1 }] };
  globalThis.fetch = async (url) => {
    if (url === "/api/auth/csrf")
      return new Response('{"header":"X-CSRF-TOKEN","token":"TEST"}');
    if (url === "/api/auth/me")
      return new Response(
        JSON.stringify({
          ...profile,
          departmentId: 2,
          scope: "DEPARTMENT",
          permissions: ["read"],
          menus: [{ code: "items" }],
        }),
      );
    throw new Error("Reload unavailable");
  };
  try {
    resetCsrf();
    await state.run(state.refreshPage);
    assert.equal(state.me.value.departmentId, 2);
    assert.equal(state.nav.value, "items");
    assert.deepEqual(state.items.value, []);
    assert.deepEqual(state.adminData.value, {});
    assert.match(state.error.value, /连接/);
  } finally {
    globalThis.fetch = original;
    resetCsrf();
  }
});

test("actual component prevents resubmitting a payment whose response was lost", async () => {
  const original = globalThis.fetch;
  const state = await setup();
  state.me.value = profile;
  state.modal.value = { schema: "cash" };
  state.account.value = {
    consignor: { id: 1 },
    balance: { payable: "40.00", recoveryDue: "0.00" },
  };
  state.form.value = {
    kind: "PAYOUT",
    amount: "40.00",
    externalReference: "TEST-lost",
    note: "TEST",
  };
  let writes = 0;
  globalThis.fetch = async (url) => {
    if (url === "/api/auth/csrf")
      return new Response('{"header":"X-CSRF-TOKEN","token":"TEST"}');
    writes++;
    throw new Error("Response lost after write");
  };
  try {
    resetCsrf();
    await state.save();
    await state.save();
    assert.equal(writes, 1);
    assert.equal(state.uncertain.value, true);
    assert.equal(state.form.value.externalReference, "TEST-lost");
    assert.match(state.error.value, /可能已完成/);
    assert.equal(state.notice.value, "");
  } finally {
    globalThis.fetch = original;
    resetCsrf();
  }
});

test("empty business setup explains what must be created before item entry", async () => {
  const state = await setup();
  state.me.value = profile;
  await state.open("items");
  assert.equal(state.modal.value, null);
  assert.match(state.error.value, /货主档案.*启用货主/);
  state.consignors.value = [{ id: 1, enabled: true }];
  await state.open("items");
  assert.equal(state.modal.value, null);
  assert.match(state.error.value, /分类字典.*启用/);
});
