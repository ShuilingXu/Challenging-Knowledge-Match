import { goto, evalJs, sleep, viewport, shot, contentSize } from "./cdp.mjs";

const LIVE = "00254159-2865-4a0c-ab8a-276710ee6d43";
await viewport(1440, 900, 1);
await goto("https://match.soyorin.love/admin/overview", { wait: 1500 });
await evalJs(`localStorage.setItem('matrix.activity-id', ${JSON.stringify(LIVE)})`);
await goto("https://match.soyorin.love/admin/control", { wait: 5000 });
await sleep(1500);

const stage = () => evalJs(`document.querySelector('.control-connection b')?.innerText.trim()`);
const click = (t) => evalJs(`(() => {
  const b = [...document.querySelectorAll('button')].find(x => x.innerText.trim() === ${JSON.stringify(t)} && !x.disabled);
  if (!b) return false; b.click(); return true;
})()`);

console.log("stage before:", await stage());

if ((await stage()) !== "答题中") {
  console.log("click 开题并计时:", await click("开题并计时"));
  for (let i = 0; i < 20; i++) {
    await sleep(300);
    if ((await stage()) === "答题中") break;
  }
  console.log("stage after open:", await stage());
}
await sleep(800);
console.log("timer:", await evalJs(`document.querySelector('.control-question-footer')?.innerText.replace(/\\n/g,' | ')`));
console.log("SIZE:", JSON.stringify(await contentSize()));
await shot("manual/raw/quiz-open.png", { fullPage: true, width: 1440, scale: 2 });

console.log("click 公布答案:", await click("公布答案"));
for (let i = 0; i < 20; i++) {
  await sleep(300);
  const s = await stage();
  if (s && s.includes("答案")) break;
}
console.log("stage after reveal:", await stage());
await sleep(1000);
await shot("manual/raw/quiz-revealed.png", { fullPage: true, width: 1440, scale: 2 });
console.log("done");
process.exit(0);
