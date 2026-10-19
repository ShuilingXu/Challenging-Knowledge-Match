// Minimal Chrome DevTools Protocol driver (Edge/Chrome headless).
// Uses Node's built-in WebSocket (Node >= 22) and fetch. No dependencies.
import fs from "node:fs";

const PORT = process.env.CDP_PORT || 9222;
let ws = null;
let nextId = 1;
const pending = new Map();
const listeners = new Set();

async function ensure() {
  if (ws && ws.readyState === 1) return;
  const list = await (await fetch(`http://127.0.0.1:${PORT}/json/list`)).json();
  const page = list.find((t) => t.type === "page" && !t.url.startsWith("devtools"));
  if (!page) throw new Error("no page target found");
  ws = new WebSocket(page.webSocketDebuggerUrl);
  await new Promise((resolve, reject) => {
    ws.onopen = resolve;
    ws.onerror = () => reject(new Error("websocket failed to open"));
  });
  ws.onmessage = (ev) => {
    const msg = JSON.parse(ev.data);
    if (msg.id && pending.has(msg.id)) {
      const { resolve, reject } = pending.get(msg.id);
      pending.delete(msg.id);
      if (msg.error) reject(new Error(msg.error.message));
      else resolve(msg.result);
    } else if (msg.method) {
      for (const fn of listeners) fn(msg);
    }
  };
  await send("Page.enable");
  await send("Runtime.enable");
}

export async function send(method, params = {}) {
  await ensure();
  const id = nextId++;
  const p = new Promise((resolve, reject) => pending.set(id, { resolve, reject }));
  ws.send(JSON.stringify({ id, method, params }));
  return p;
}

export async function evalJs(expression, awaitPromise = true) {
  const res = await send("Runtime.evaluate", {
    expression,
    awaitPromise,
    returnByValue: true,
    userGesture: true,
  });
  if (res.exceptionDetails) {
    throw new Error("JS error: " + (res.exceptionDetails.exception?.description || res.exceptionDetails.text));
  }
  return res.result.value;
}

export const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

export async function goto(url, { wait = 1200 } = {}) {
  await send("Page.navigate", { url });
  await sleep(wait);
}

export async function waitFor(fn, { timeout = 20000, interval = 250 } = {}) {
  const start = Date.now();
  while (Date.now() - start < timeout) {
    try {
      const v = await evalJs(`Boolean((${fn.toString()})())`);
      if (v) return v;
    } catch {
      /* page may still be loading */
    }
    await sleep(interval);
  }
  throw new Error("waitFor timeout: " + fn.toString().slice(0, 120));
}

export async function viewport(width, height, deviceScaleFactor = 2) {
  await send("Emulation.setDeviceMetricsOverride", {
    width,
    height,
    deviceScaleFactor,
    mobile: false,
  });
}

export async function contentSize() {
  return evalJs(`(() => ({
    w: Math.max(document.documentElement.scrollWidth, document.body.scrollWidth),
    h: Math.max(document.documentElement.scrollHeight, document.body.scrollHeight)
  }))()`);
}

export async function shot(path, { fullPage = false, width = 1440, height = 900, clip = null, scale = 2 } = {}) {
  if (fullPage) {
    const { w, h } = await contentSize();
    await viewport(width, Math.min(h, 6000), scale);
    await sleep(600);
  } else {
    await viewport(width, height, scale);
    await sleep(300);
  }
  const params = { format: "png", fromSurface: true };
  if (clip) params.clip = { ...clip, scale: 1 };
  const { data } = await send("Page.captureScreenshot", params);
  fs.writeFileSync(path, Buffer.from(data, "base64"));
  return path;
}

export async function clickText(text, selector = "button, a, summary, label") {
  return evalJs(`(() => {
    const els = [...document.querySelectorAll(${JSON.stringify(selector)})];
    const el = els.find(e => (e.innerText || e.textContent || '').trim().includes(${JSON.stringify(text)}));
    if (!el) return false;
    el.scrollIntoView({block:'center'});
    el.click();
    return true;
  })()`);
}

export async function elementRect(selector, text = null) {
  return evalJs(`(() => {
    const els = [...document.querySelectorAll(${JSON.stringify(selector)})];
    const el = ${text ? `els.find(e => (e.innerText || '').trim().includes(${JSON.stringify(text)}))` : "els[0]"};
    if (!el) return null;
    const r = el.getBoundingClientRect();
    return { x: r.x + scrollX, y: r.y + scrollY, w: r.width, h: r.height };
  })()`);
}

export async function close() {
  try { ws?.close(); } catch {}
}
