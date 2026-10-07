import { createElement } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';
import { AnswerCard, BuzzCard } from './App';

const question = {
  id: 'question-1', type: 'SINGLE', title: '选择城市',
  options: ['北京', '上海'], answers: ['上海'],
  mediaUrls: ['/question.png'], answerMediaUrls: ['/answer.png'],
};
const render = (Card, q, stage = 'ANSWER_REVEALED', questionId = q.id) => renderToStaticMarkup(createElement(Card, {
  activityId: 'quiz', participant: { id: 'player' }, question: q,
  state: { stage, questionId, seconds: 0 }, activityStatus: 'LIVE',
}));

describe.each([['标准模式', AnswerCard], ['抢答模式', BuzzCard]])('%s公布答案', (_, Card) => {
  it('shows the correct option and answer media even without a submission or buzz', () => {
    const html = render(Card, question);
    expect(html).toContain('正确答案');
    expect(html).toContain('B. 上海');
    expect(html).toContain('/answer.png');
    expect(html).toContain('/question.png');
    expect(html).not.toContain('确认提交');
    expect(html).not.toContain('buzzer-button');
  });
  it('shows all correct choices and text reference answers', () => {
    const multi = render(Card, { ...question, type: 'MULTIPLE', answers: ['北京', '上海'] });
    expect(multi).toContain('A. 北京');
    expect(multi).toContain('B. 上海');
    const text = render(Card, { ...question, type: 'TEXT', options: [], answers: ['参考内容', '另一种回答'] });
    expect(text).toContain('参考答案');
    expect(text).toContain('参考内容');
    expect(text).toContain('另一种回答');
  });
  it('hides answer content before reveal and for stale question data', () => {
    for (const html of [render(Card, question, 'QUESTION_OPEN'), render(Card, question, 'LOBBY'), render(Card, question, 'ANSWER_REVEALED', 'question-2')]) {
      expect(html).not.toContain('正确答案');
      expect(html).not.toContain('B. 上海');
      expect(html).not.toContain('/answer.png');
    }
  });
  it('provides a fallback for manually graded questions without text answers', () => {
    expect(render(Card, { ...question, type: 'TEXT', answers: [] })).toContain('本题未配置文字答案');
  });
});
