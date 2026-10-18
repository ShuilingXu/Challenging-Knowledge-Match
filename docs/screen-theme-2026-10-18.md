# 大屏展示配色统一 + 常驻加入二维码（2026-10-18）

## 目标

1. 大屏（受控屏）的展示界面与后台工作区使用同一套配色与卡片语言，不再出现「后台是象牙纸、大屏是深蓝/墨绿海报」的割裂。
2. 答题、抢答、海龟汤三类现场页面在右上角**常驻**加入二维码：迟到的观众随时可以扫码入场，翻页、滚动、公布答案都不会把二维码带走。

## 配色与设计：向后台 SOYO 工作区看齐

沿用 `theme-soyo.css` 里后台已经在用的变量（`--soyo-ink / --soyo-text / --soyo-cocoa / --soyo-line / --soyo-gold / --soyo-sky / --soyo-blush`），只替换大屏自己的视觉语言：

| 位置 | 旧版 | 现在 |
|---|---|---|
| 页面与画布 | 深蓝底 + 巨大斜切青/金色块；海龟汤是墨绿剧场 | 象牙纸底 + 细网点纹理 + 角落柔粉/淡金圆环，与后台工作区同一套装饰 |
| 顶部设备条 | 透明、无边框、浅蓝灰文字 | 后台顶栏同款半透明纸张条：细描边、圆角、极轻阴影；`设备已受控` 改为后台 live 绿胶囊 |
| 答题 / 抢答 | 白字压在撞色背景上，选项是半透明深底块 | 可可棕题面、紫罗兰题型胶囊、绿色提交胶囊；选项为纸张卡片，正确项用柔金渐变 + 金色字母块 |
| 积分榜 / 获奖名单 | 深底半透明行 | 纸张行卡片 + 金色名次 + 可可棕分数 |
| 大屏模板 | 模板背景色直接铺满整屏，文字块偏白 | 背景色保留（模板主色继续由管理员设置），叠加纸张网点与角落光晕；文字/二维码/图片/文件都收进统一圆角卡片；只有一个可视组件时独占一行居中 |
| 配屏提示 | 深色背景 + 上下两条金线 | 后台登录卡同款纸张卡片（26px 圆角 + 深阴影） |
| 海龟汤 | 墨绿底、白色正文、金色编号 | 同一张象牙纸：可可棕标题、纸张分区卡片、柔金线索编号与进度条、金色汤底分区 |

尺寸不再写死 px / vw：题面、选项、列表、模板文字全部改用画布容器单位（`cqw / cqh`）配合 `clamp()`，`--screen-font-scale`（50%–200%）继续生效，1280×720、1920×1080、手机竖屏都按画布等比缩放。没有配图时「题目 + 选项」与页脚平分留白，避免 16:9 画布顶部堆叠、下半屏空一片。

## 常驻加入二维码

- 触发条件集中在 `src/activity-flow.js` 的 `screenJoinPrompt(mode)`：`QUESTION`、`RESULT`（答题与抢答共用这两个模式）与 `TURTLE_SOUP` 返回文案，其余模式（候场、模板、积分榜、获奖名单等）返回 `null`。
- 二维码渲染在画布右上角（`position: absolute; z-index: 4`），与内容层分开，所以内容滚动、翻页、公布答案都不会移除它；`ScreenJoinQr` 单独导出一份，便于静态渲染测试。
- 内容区通过 `.public-canvas--with-join` 预留右侧安全边距（`--join-qr-reserve`），题面、选项、线索板都不会被二维码压住；窄屏二维码自动缩到 56–84px、隐藏副标题。
- 扫码落地页与模板里的「活动二维码」一致：`{origin}/join/{activityId}`（`screenJoinUrl`），即本会场的报名/参与入口。

## 预置模板配色

- 运行期种子 `ScreenService.ensurePresetTemplates` 的四个背景色改为纸张色：`#f6e7e3`（入场指引）、`#e8f0f6`（传送门二维码）、`#f6ece1`（答题直播）、`#efeaf2`（积分榜）。
- 新增迁移 `V27__screen_template_paper_palette.sql`：把**已有**预置模板里的四个旧色值替换成新色值；`preset = false` 的自定义模板一律不动（管理员自己挑的颜色继续生效）。

## 验证

- `npm test`：11 个测试文件、70 项全部通过（新增 `screen-join-qr.test.js` 2 项、`activity-flow.test.js` 3 项）。
- `npm run build`：生产构建成功。
- `V27` 迁移在 H2（`MODE=PostgreSQL`）上跑过：预置模板颜色被替换，自定义模板保持原色。
- 静态预览（真实组件结构 + 真实样式表）截图核对了 1280×720、1920×1080、430×932 三种画布：题目、公布结果、带配图题目、积分榜、模板、候场、配屏、海龟汤（汤面 / 揭晓 / 候场）。
- 未改后端接口、未改数据库结构、未改任何线上配置。

## 生产部署（2026-10-18）

已发布到 `https://match.soyorin.love`（主机 `sh-1.soyorin.love`，root + `~/.ssh/prod-env.pem`）：

1. 前端 `npm run build` → `dist/`（含 `public/knowledge-match-user-manual.pdf`），打包上传到 `/opt/challenging-knowledge-match/staging-dist/`。
2. 后端 `mvn -B -f server/pom.xml package`（25 个测试类、125 项测试全部通过）→ `knowledge-match-api-0.1.0.jar`，上传为 `.jar.new`。
3. 升级前用 `pg_dump` 备份数据库：`/opt/challenging-knowledge-match/backups/pretemplate-20261018-202517.sql`（372 KB）。
4. `deploy/bootstrap-sh-1.soyorin.love.sh` 安装新 jar 与新前端、刷新 OpenResty 配置；随后 `systemctl restart knowledge-match.service` 让新 jar 生效（bootstrap 里的 `enable --now` 不会重启已在运行的服务）。
5. 生产核对：
   - `/api/health` = UP（本机 8082 与公网 HTTPS 均通过），服务 12 秒启动完成。
   - Flyway 在 PostgreSQL 上应用 `27 - screen template paper palette`，`flyway_schema_history` 最新版本 = 27。
   - 预置模板背景色已换成纸张色：13 个预置模板中 12 个带背景色的全部换色，旧色值 0 条残留；`preset = false` 的过滤条件保证以后管理员自建模板不受影响（目前自定义模板 0 条）。
   - 公网 `index-SZJKapi0.js` / `index-Doi2M-R_.css` 与本机构建 SHA256 完全一致；CSS 内含 `screen-join-qr` 与新版纸张画布，旧版斜切色块已消失。
   - HTTP→HTTPS 301、证书 `CN=match.soyorin.love` 校验通过、Redis `PONG`、PostgreSQL 查询正常、媒体上传/下载 smoke test 通过。
   - 实机截图确认：登录页与 `https://match.soyorin.love/screen/<id>`（未配对的提示页）都已渲染新版纸张配色。

## 文件

- `src/App.jsx`：`PublicScreen` 常驻二维码 + `ScreenJoinQr`；`TemplateScreen` 背景色改走 `--template-tone`，新增 `template-screen--single`。
- `src/activity-flow.js`：`screenJoinPrompt`、`screenJoinUrl`。
- `src/theme-soyo.css`：「大屏展示」整段重写（页面 / 设备条 / 画布 / 题目 / 积分榜 / 模板 / 二维码 / 配屏 / 窄屏）。
- `src/turtle-soup.css`：大屏海龟汤改为纸张配色（后台控场页的预览同步）。
- `server/.../ScreenService.java`、`server/.../V27__screen_template_paper_palette.sql`：预置模板配色。
- `src/screen-join-qr.test.js`、`src/activity-flow.test.js`：覆盖二维码触发条件、落地链接与渲染。

## 截图

改版前（1600×900）：

![改版前的答题大屏](before-question.png)

答题（1920×1080，右上角常驻二维码）：

![答题大屏](after-question-1920.png)

公布答案：

![公布答案](after-result-1920.png)

海龟汤（汤面 + 已公开线索）：

![海龟汤大屏](after-soup-1920.png)

大屏模板（单一二维码组件独占一行居中）：

![大屏模板](after-template-1920.png)

配屏提示：

![配屏提示](after-pairing-1280.png)

手机竖屏（430×932）下的海龟汤：

![手机竖屏海龟汤](after-soup-mobile.png)
