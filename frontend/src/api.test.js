// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
import test from "node:test";
import assert from "node:assert/strict";
import { api, resetCsrf, signIn } from "./api.js";
test("page sign-in sends credentials as POST JSON after CSRF bootstrap", async () => {
  const original = globalThis.fetch;
  const calls = [];
  globalThis.fetch = async (url, opts) => {
    calls.push([url, opts]);
    if (url === "/api/auth/csrf")
      return new Response(
        JSON.stringify({ header: "X-CSRF-TOKEN", token: "TEST-token" }),
      );
    assert.equal(url, "/api/auth/login");
    assert.equal(opts.method, "POST");
    assert.equal(opts.headers["X-CSRF-TOKEN"], "TEST-token");
    assert.deepEqual(JSON.parse(opts.body), {
      username: "TEST-buyer",
      password: "TEST-password",
    });
    return new Response(
      JSON.stringify({ username: "TEST-buyer", consignorId: 1 }),
    );
  };
  try {
    resetCsrf();
    assert.equal(
      (await signIn({ username: "TEST-buyer", password: "TEST-password" }))
        .consignorId,
      1,
    );
    assert.equal(calls.length, 2);
  } finally {
    globalThis.fetch = original;
    resetCsrf();
  }
});
test("expired session rejected by CSRF requests sign-in without replaying a write", async () => {
  const original = globalThis.fetch;
  const calls = [];
  globalThis.fetch = async (url, opts) => {
    calls.push([url, opts?.method]);
    if (url === "/api/auth/csrf")
      return new Response(
        JSON.stringify({ header: "X-CSRF-TOKEN", token: "TEST-token" }),
      );
    if (url === "/api/auth/me")
      return new Response('{"code":"UNAUTHENTICATED"}', { status: 401 });
    return new Response('{"code":"FORBIDDEN"}', { status: 403 });
  };
  try {
    resetCsrf();
    await assert.rejects(() => api("/items", "POST", { note: "TEST" }), {
      message: "UNAUTHENTICATED",
    });
    assert.equal(
      calls.filter(([url, method]) => url === "/api/items" && method === "POST")
        .length,
      1,
    );
    assert.equal(calls.at(-1)[0], "/api/auth/me");
  } finally {
    globalThis.fetch = original;
    resetCsrf();
  }
});

test("CSV download uses GET without a body and returns CSV instead of a JSON error", async () => {
  const original = globalThis.fetch;
  globalThis.fetch = async (url, opts) => {
    if (url === "/api/auth/csrf")
      return new Response(
        JSON.stringify({ header: "X-CSRF-TOKEN", token: "TEST-token" }),
      );
    assert.equal(url, "/api/export/sales");
    assert.equal(opts.method, "GET");
    assert.equal(opts.body, undefined);
    return new Response("reference,price\r\nTEST,100.01\r\n", {
      headers: { "Content-Type": "text/csv" },
    });
  };
  try {
    resetCsrf();
    assert.equal(
      await api("/export/sales", "GET", null, true),
      "reference,price\r\nTEST,100.01\r\n",
    );
  } finally {
    globalThis.fetch = original;
    resetCsrf();
  }
});

test("lost write response and unreadable success body mean unknown outcome, with no automatic retry", async () => {
  const original = globalThis.fetch;
  try {
    for (const mode of ["headers", "body"]) {
      let writes = 0;
      globalThis.fetch = async (url) => {
        if (url === "/api/auth/csrf")
          return new Response('{"header":"X-CSRF-TOKEN","token":"TEST"}');
        writes++;
        if (mode === "headers") throw new Error("lost response");
        return {
          ok: true,
          status: 200,
          json: async () => {
            throw new Error("lost body");
          },
        };
      };
      resetCsrf();
      await assert.rejects(
        () => api("/cash", "POST", { externalReference: "TEST" }),
        { message: "RESULT_UNKNOWN" },
      );
      assert.equal(writes, 1);
    }
  } finally {
    globalThis.fetch = original;
    resetCsrf();
  }
});

test("permission rejection stays visible when the extra session check loses connection", async () => {
  const original = globalThis.fetch;
  globalThis.fetch = async (url) => {
    if (url === "/api/auth/csrf")
      return new Response('{"header":"X-CSRF-TOKEN","token":"TEST"}');
    if (url === "/api/auth/me") throw new Error("lost session check");
    return new Response('{"code":"FORBIDDEN"}', { status: 403 });
  };
  try {
    resetCsrf();
    await assert.rejects(() => api("/cash", "POST", {}), {
      message: "FORBIDDEN",
    });
  } finally {
    globalThis.fetch = original;
    resetCsrf();
  }
});

test("an empty expired-session response still returns to sign-in", async () => {
  const original = globalThis.fetch;
  globalThis.fetch = async (url) =>
    url === "/api/auth/csrf"
      ? new Response('{"header":"X-CSRF-TOKEN","token":"TEST"}')
      : new Response("", { status: 401 });
  try {
    resetCsrf();
    await assert.rejects(() => api("/items"), { message: "UNAUTHENTICATED" });
  } finally {
    globalThis.fetch = original;
    resetCsrf();
  }
});

test("response headers arriving do not disable timeout while the body is stuck", async () => {
  const originalFetch = globalThis.fetch,
    originalTimeout = globalThis.setTimeout;
  let aborted = false;
  globalThis.setTimeout = (callback) => originalTimeout(callback, 10);
  globalThis.fetch = async (url, options) => {
    if (url === "/api/auth/csrf")
      return new Response('{"header":"X-CSRF-TOKEN","token":"TEST"}');
    return {
      ok: true,
      status: 200,
      json: () =>
        new Promise((resolve, reject) => {
          options.signal.addEventListener(
            "abort",
            () => {
              aborted = true;
              reject(new Error("Body interrupted"));
            },
            { once: true },
          );
        }),
    };
  };
  try {
    resetCsrf();
    await assert.rejects(() => api("/items"), { message: "NETWORK_ERROR" });
    assert.equal(aborted, true);
  } finally {
    globalThis.fetch = originalFetch;
    globalThis.setTimeout = originalTimeout;
    resetCsrf();
  }
});
