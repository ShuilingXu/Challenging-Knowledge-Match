import fs from "node:fs";
import { goto, evalJs, sleep, viewport, shot, contentSize } from "./cdp.mjs";

const LIVE = "00254159-2865-4a0c-ab8a-276710ee6d43";
await viewport(1440, 900, 1);
await goto("https://match.soyorin.love/admin/overview", { wait: 1500 });
await evalJs(`localStorage.setItem('matrix.activity-id', ${JSON.stringify(LIVE)})`);

const rectOf = (js) => evalJs(`(() => { const el = ${js}; if (!el) return null; const r = el.getBoundingClientRect(); return { x: r.x + scrollX, y: r.y + scrollY, w: r.width, h: r.height }; })()`);
const byText = (t) => `[...document.querySelectorAll('button')].find(b => b.innerText.trim() === ${JSON.stringify(t)})`;
const stage = () => evalJs(`document.querySelector('.control-connection b')?.innerText.trim()`);
const click = (t) => evalJs(`(() => { const b = [...document.querySelectorAll('button')].find(x => x.innerText.trim() === ${JSON.stringify(t)} && !x.disabled); if (!b) return false; b.click(); return true; })()`);

// ---------- quiz ----------
await goto("https://match.soyorin.love/admin/control", { wait: 5000 });
await sleep(1500);
if ((await stage()) !== "答题中") {
  console.log("open next:", await click("下一题并开始"));
  for (let i = 0; i < 25; i++) { await sleep(300); if ((await stage()) === "答题中") break; }
}
await sleep(700);
console.log("quiz stage:", await stage());
const quizRects = {
  stage: await rectOf(`document.querySelector('.control-connection')`),
  picker: await rectOf(`document.querySelector('select[aria-label="选择控场题目"]')`),
  open: await rectOf(byText("开题并计时")),
  buzz: await rectOf(`document.querySelector('.control-response-overview')`),
  reveal: await rectOf(byText("公布答案")),
  next: await rectOf(byText("下一题并开始")),
  timer: await rectOf(`document.querySelector('.control-aside')`),
};
fs.writeFileSync("manual/raw/quiz-rects.json", JSON.stringify(quizRects, null, 1));
await shot("manual/raw/quiz-long.png", { fullPage: true, width: 1440, scale: 2 });
console.log("quiz size:", JSON.stringify(await contentSize()));

// ---------- soup ----------
await goto("https://match.soyorin.love/admin/soup-control", { wait: 6000 });
await sleep(2000);
console.log("soup chip:", await evalJs(`document.querySelector('.soup-controls .type-chip')?.innerText.trim()`));
const soupRects = {
  show: await rectOf(byText("展示汤面")),
  header: await rectOf(`document.querySelector('.soup-controls')`),
  board: await rectOf(`document.querySelector('.soup-clue-board')`),
  clue1: await rectOf(`document.querySelector('button[aria-label^="公开线索 3"]')`),
  toggle: await rectOf(`document.querySelector('.soup-answer-switch')`),
  preview: await rectOf(`document.querySelector('.soup-preview')`),
  manual: await rectOf(`document.querySelector('.soup-manual')`),
  playlist: await rectOf(`document.querySelector('.soup-playlist')`),
};
fs.writeFileSync("manual/raw/soup-rects.json", JSON.stringify(soupRects, null, 1));
await shot("manual/raw/soup-long.png", { fullPage: true, width: 1440, scale: 2 });
console.log("soup size:", JSON.stringify(await contentSize()));
console.log("done");
process.exit(0);
