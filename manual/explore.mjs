import { goto, evalJs, waitFor, shot, contentSize, sleep, viewport } from "./cdp.mjs";

await viewport(1440, 900, 1);
const pages = [
  ["overview", "https://match.soyorin.love/admin/overview"],
  ["control", "https://match.soyorin.love/admin/control"],
  ["questions", "https://match.soyorin.love/admin/questions"],
  ["soup-control", "https://match.soyorin.love/admin/soup-control"],
];
for (const [name, url] of pages) {
  await goto(url, { wait: 3500 });
  await sleep(1200);
  const size = await contentSize();
  const text = await evalJs("document.body.innerText.replace(/\\n{2,}/g,'\\n').slice(0, 2600)");
  console.log(`\n===== ${name} ${url} size=${JSON.stringify(size)} =====`);
  console.log(text);
  await shot(`manual/raw/${name}.png`, { fullPage: false, width: 1440, height: 900, scale: 1 });
}
process.exit(0);
