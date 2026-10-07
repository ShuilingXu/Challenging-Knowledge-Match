import { BadgeCheck } from 'lucide-react';

export function ParticipantAnswerReveal({ question, state, children }) {
  if (!question || state?.stage !== 'ANSWER_REVEALED' || state.questionId !== question.id) return null;
  const answers = question.answers || [];
  return <section className="participant-answer-reveal" aria-label="本题公布答案">
    <div className="answer-feedback is-revealed" role="status">
      <BadgeCheck size={20} />
      <div>
        <strong>本题答案已公布</strong>
        {answers.length > 0 ? <>
          <strong>{question.type === 'TEXT' ? '参考答案' : '正确答案'}</strong>
          <ul>{answers.map((answer, index) => {
            const optionIndex = question.type === 'TEXT' ? -1 : (question.options || []).indexOf(answer);
            return <li key={index}>{optionIndex >= 0 ? `${String.fromCharCode(65 + optionIndex)}. ${answer}` : answer}</li>;
          })}</ul>
        </> : <span>本题未配置文字答案，请查看答案材料或听主持人讲解。</span>}
        <span>请等待主持人开启下一题，答题结果和积分会实时更新。</span>
      </div>
    </div>
    {children}
  </section>;
}
