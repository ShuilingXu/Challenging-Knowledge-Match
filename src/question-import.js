const columns = ["type", "title", "options", "answers", "fullScore", "partialCreditPercent", "textAcceptedAnswers", "textMatchMode", "enabled"];
const aliases = { 题型: "type", 题干: "title", 选项: "options", 答案: "answers", 分值: "fullScore", 部分得分: "partialCreditPercent", 标准答案: "textAcceptedAnswers", 匹配模式: "textMatchMode", 启用: "enabled" };
const typeAliases = { 单选: "SINGLE", 单选题: "SINGLE", 多选: "MULTIPLE", 多选题: "MULTIPLE", 文本: "TEXT", 文本题: "TEXT" };
const splitValues = (value) => String(value || "").split(/\|\||\r?\n|；|;/).map((item) => item.trim()).filter(Boolean);

export function questionImportTemplate() {
  const rows = [
    columns,
    ["SINGLE", "太阳从哪边升起？", "东||西||南||北", "A", "100", "40", "", "", "true"],
    ["MULTIPLE", "以下哪些属于水果？", "苹果||土豆||香蕉", "A,C", "100", "40", "", "", "true"],
    ["TEXT", "我国的首都是哪里？", "", "", "100", "0", "北京||北京市", "FUZZY", "true"],
  ];
  return "\uFEFF" + rows.map((row) => row.map((value) => "\"" + String(value).replaceAll('"', '""') + "\"").join(",")).join("\r\n") + "\r\n";
}

function parseCsv(text) {
  const rows = [];
  let row = [], value = "", quoted = false;
  for (let index = 0; index < text.length; index += 1) {
    const char = text[index];
    if (char === '"') {
      if (quoted && text[index + 1] === '"') { value += '"'; index += 1; }
      else quoted = !quoted;
    } else if (char === "," && !quoted) { row.push(value); value = ""; }
    else if ((char === "\n" || char === "\r") && !quoted) {
      if (char === "\r" && text[index + 1] === "\n") index += 1;
      row.push(value); value = "";
      if (row.some((cell) => cell.trim())) rows.push(row);
      row = [];
    } else value += char;
  }
  if (quoted) throw new Error("CSV 中有未闭合的引号，请检查文件格式");
  if (value || row.length) { row.push(value); if (row.some((cell) => cell.trim())) rows.push(row); }
  return rows;
}

export function parseQuestionImport(text) {
  const rows = parseCsv(text);
  if (rows.length < 2) throw new Error("导入文件没有可用数据，请先下载并填写模板");
  if (rows.length > 501) throw new Error("每次最多导入 500 道题目，请拆分文件后再导入");
  const headers = rows[0].map((cell) => aliases[cell.trim()] || cell.trim());
  if (!headers.includes("title")) throw new Error("缺少 title（题干）列，请使用下载的导入模板");
  if (new Set(headers).size !== headers.length) throw new Error("CSV 表头不能重复");
  return rows.slice(1).map((cells, index) => {
    const fail = (message) => { throw new Error("第 " + (index + 2) + " 行：" + message); };
    if (cells.length > headers.length) fail("列数超过表头，请检查逗号或引号");
    const row = Object.fromEntries(headers.map((key, offset) => [key, (cells[offset] || "").trim()]));
    const rawType = (row.type || "SINGLE").toUpperCase();
    const type = typeAliases[rawType] || rawType;
    if (!["SINGLE", "MULTIPLE", "TEXT"].includes(type)) fail("题型须为 SINGLE、MULTIPLE 或 TEXT");
    const title = row.title;
    if (!title || title.length > 8000) fail("题干不能为空，且最多为 8000 个字符");
    const fullScore = Number(row.fullScore || 100);
    const partialCreditPercent = Number(row.partialCreditPercent === "" || row.partialCreditPercent == null ? 40 : row.partialCreditPercent);
    if (!Number.isInteger(fullScore) || fullScore < 1 || fullScore > 100000) fail("分值须为 1 至 100000 的整数");
    if (!Number.isInteger(partialCreditPercent) || partialCreditPercent < 0 || partialCreditPercent > 100) fail("部分得分须为 0 至 100 的整数");
    const options = type === "TEXT" ? [] : splitValues(row.options);
    const answers = type === "TEXT" ? [] : [...new Set(String(row.answers || "").split(/[,，、]/).map((item) => item.trim()).filter(Boolean).map((answer) => {
      const offset = /^[A-Z]$/i.test(answer) ? answer.toUpperCase().charCodeAt(0) - 65 : -1;
      return offset >= 0 && offset < options.length ? options[offset] : answer;
    }))];
    if (type !== "TEXT") {
      if (options.length < 2) fail("选择题至少需要两个选项，用 || 分隔");
      if (new Set(options).size !== options.length) fail("选项不能重复");
      if (options.some((option) => /[,|]/.test(option))) fail("选项内容不能包含英文逗号或竖线");
      if (!answers.length || answers.some((answer) => !options.includes(answer))) fail("答案须为有效选项字母或选项内容");
      if (type === "SINGLE" && answers.length !== 1) fail("单选题只能有一个正确答案");
    }
    const textAcceptedAnswers = type === "TEXT" ? splitValues(row.textAcceptedAnswers) : [];
    const textMatchMode = type === "TEXT" ? (row.textMatchMode || "MANUAL").toUpperCase() : null;
    if (type === "TEXT") {
      if (!["FUZZY", "REGEX", "MANUAL"].includes(textMatchMode)) fail("匹配模式须为 FUZZY、REGEX 或 MANUAL");
      if (textMatchMode !== "MANUAL" && !textAcceptedAnswers.length) fail("自动匹配文本题需要填写标准答案");
      if (textAcceptedAnswers.length > 50 || textAcceptedAnswers.some((answer) => answer.length > 2000)) fail("标准答案最多 50 项，每项最多 2000 个字符");
    }
    const enabledValue = (row.enabled || "true").toLowerCase();
    if (!["true", "false", "是", "否", "1", "0"].includes(enabledValue)) fail("启用须为 true 或 false");
    return { type, title, options, answers, fullScore, partialCreditPercent, textAcceptedAnswers, textMatchMode, enabled: !["false", "否", "0"].includes(enabledValue) };
  });
}
