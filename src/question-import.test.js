import { describe, expect, it } from "vitest";
import { parseQuestionImport, questionImportTemplate } from "./question-import";

describe("question import", () => {
  it("parses the generated template and maps answer letters", () => {
    const rows = parseQuestionImport(questionImportTemplate());
    expect(rows).toHaveLength(3);
    expect(rows[0].answers).toEqual(["东"]);
    expect(rows[1].answers).toEqual(["苹果", "香蕉"]);
    expect(rows[2].textAcceptedAnswers).toContain("北京");
  });
  it("supports quoted commas and reports invalid rows", () => {
    const csv = 'type,title,options,answers\nSINGLE,"题干，含逗号","甲||乙",B';
    expect(parseQuestionImport(csv)[0].title).toBe("题干，含逗号");
    expect(() => parseQuestionImport("type,title\nSINGLE,缺选项")).toThrow("至少需要两个选项");
  });
});
