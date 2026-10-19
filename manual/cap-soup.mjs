import { goto, evalJs, sleep, viewport, shot, contentSize } from "./cdp.mjs";

const LIVE = "00254159-2865-4a0c-ab8a-276710ee6d43";
await viewport(1440, 900, 1);
await goto("https://match.soyorin.love/admin/overview", { wait: 1500 });
await evalJs(`localStorage.setItem('matrix.activity-id', ${JSON.stringify(LIVE)})`);
await goto("https://match.soyorin.love/admin/soup-control", { wait: 6000 });
await sleep(2000);

const chip = () => evalJs(`document.querySelector('.soup-controls .type-chip')?.innerText.trim()`);
const click = (t) => evalJs(`(() => {
  const b = [...document.querySelectorAll('button')].find(x => x.innerText.trim() === ${JSON.stringify(t)} && !x.disabled);
  if (!b) return false; b.click(); return true;
})()`);
const clue = (n, reveal) => evalJs(`(() => {
  const b = document.querySelector('button[aria-label="${reveal ? "公开" : "撤回"}线索 ${n}"]');
  if (!b || b.disabled) return false; b.scrollIntoView({block:'center'}); b.click(); return true;
})()`);

console.log("chip before:", await chip());
console.log("size:", JSON.stringify(await contentSize()));
await shot("manual/raw/soup-idle.png", { fullPage: true, width: 1440, scale: 2 });

console.log("show surface:", await click("展示汤面"));
for (let i = 0; i < 30; i++) {
  await sleep(300);
  if ((await chip()) === "推理进行中") break;
}
console.log("chip after surface:", await chip());
await sleep(1500);
await shot("manual/raw/soup-surface.png", { fullPage: true, width: 1440, scale: 2 });

console.log("clue1:", await clue(1, true));
await sleep(1200);
console.log("clue2:", await clue(2, true));
await sleep(1500);
console.log("chip after clues:", await chip(), await evalJs(`document.querySelector('.soup-controls p')?.innerText`));
await shot("manual/raw/soup-clues.png", { fullPage: true, width: 1440, scale: 2 });
console.log("done");
process.exit(0);
