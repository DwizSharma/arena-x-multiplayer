'use client';
import ReactMarkdown from 'react-markdown';
import { useEffect, useRef, useState } from 'react';

export function QuestionCard({ question, onAnswer, locked }: { question: any; onAnswer: (questionId: string, optionId: string) => void; locked: boolean }) {
  const [now, setNow] = useState(Date.now());
  const receivedAt = useRef(performance.now());
  useEffect(() => { receivedAt.current = performance.now(); }, [question?.promptId]);
  useEffect(() => { const timer = window.setInterval(() => setNow(Date.now()), 100); return () => clearInterval(timer); }, []);
  if (!question) return null;
  const total = Math.max(1, question.durationMs || 1); const left = Math.max(0, (question.remainingMs ?? total) - (performance.now() - receivedAt.current)); const pct = Math.max(0, Math.min(100, left / total * 100));
  return <section className="panel question-card" aria-live="polite">
    <div className="timer-track" aria-label={`${Math.ceil(left / 1000)} seconds remaining`}><div style={{ width: `${pct}%` }} /></div>
    <div className="question-meta"><span>QUESTION {question.index + 1} <i className="topic-chip">{question.topic || 'APTITUDE'}</i></span><span>{Math.ceil(left / 1000)}s</span></div>
    <div className="prompt"><ReactMarkdown>{question.prompt}</ReactMarkdown></div>
    {question.imageUrl && <img className="question-image" src={question.imageUrl} alt="Question illustration" />}
    {question.tableData?.length > 0 && <div className="table-wrap"><table><tbody>{question.tableData.map((row: string[], i: number) => <tr key={i}>{row.map((cell, j) => <td key={j}>{cell}</td>)}</tr>)}</tbody></table></div>}
    <div className="options">{question.options.map((option: { id: string; text: string }, i: number) => <button key={option.id} disabled={locked || left <= 0} onClick={() => onAnswer(question.promptId, option.id)} className={`option option-${i}`}><span className="option-letter">{option.id}</span>{option.text}</button>)}</div>
    {locked && <p className="locked">Answer locked in ✓</p>}
    <p className="scoring-note">Fast + correct = up to 1,000 points · wrong answer = 0 · no pay-to-win powerups</p>
  </section>;
}
