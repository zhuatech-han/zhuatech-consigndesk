// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
let csrf;
/** 超时覆盖响应头及正文；写入响应丢失不代表事务未提交。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
async function request(url, options = {}, raw = false) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 20000);
  try {
    const response = await fetch(url, {
      ...options,
      signal: controller.signal,
    });
    const value = await (
      raw && response.ok ? response.text() : response.json()
    ).catch(() => {
      const known = {
        401: "UNAUTHENTICATED",
        403: "FORBIDDEN",
        413: "UPLOAD_TOO_LARGE",
        429: "LOGIN_THROTTLED",
      };
      if (known[response.status]) return { code: known[response.status] };
      throw new Error("NETWORK_ERROR");
    });
    return { response, value };
  } catch {
    throw new Error("NETWORK_ERROR");
  } finally {
    clearTimeout(timeout);
  }
}
/** 同源请求；CSRF 令牌保存在内存，会话由 HttpOnly Cookie 管理。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export async function api(path, method = "GET", body, raw = false) {
  if (!csrf || path === "/auth/csrf") {
    const { response, value } = await request("/api/auth/csrf");
    if (!response.ok || !value?.header || !value?.token)
      throw new Error("NETWORK_ERROR");
    csrf = value;
    if (path === "/auth/csrf") return csrf;
  }
  let result;
  try {
    result = await request(
      "/api" + path,
      {
        method,
        headers: {
          "Content-Type": "application/json",
          ...(method === "GET" ? {} : { [csrf.header]: csrf.token }),
        },
        ...(body === undefined || method === "GET"
          ? {}
          : { body: JSON.stringify(body) }),
      },
      raw,
    );
  } catch {
    throw new Error(method === "GET" ? "NETWORK_ERROR" : "RESULT_UNKNOWN");
  }
  const { response, value } = result;
  if (!response.ok) {
    if (response.status === 403 && !path.startsWith("/public/")) {
      const session = await request("/api/auth/me").catch(() => null);
      if (session?.response.status === 401) {
        csrf = null;
        throw new Error("UNAUTHENTICATED");
      }
    }
    if (response.status === 401) csrf = null;
    throw new Error(
      value.code ||
        (response.status === 413 ? "UPLOAD_TOO_LARGE" : "NETWORK_ERROR"),
    );
  }
  return value;
}
/** 登录使用 POST JSON 和当前 CSRF 令牌；不缓存密码。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function signIn(credentials) {
  return api("/auth/login", "POST", credentials);
}
/** 会话结束后重新生成 CSRF 引导。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function resetCsrf() {
  csrf = null;
}
