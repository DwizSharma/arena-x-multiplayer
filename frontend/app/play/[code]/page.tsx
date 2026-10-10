'use client';

import { FormEvent, useEffect, useState, type CSSProperties } from 'react';
import { useParams } from 'next/navigation';
import { API_BASE_URL } from '@/lib/api';
import { useArenaSocket } from '@/lib/socket/useArenaSocket';
import { QuestionCard } from '@/components/QuestionCard';
import { LiveLeaderboard } from '@/components/LiveLeaderboard';

const modeOptions = [
  { id: 'CLASSIC', title: 'Classic arena', emoji: '🏁' },
  { id: 'MEME_RUSH', title: 'Meme rush', emoji: '💀' },
  { id: 'QUANT_SPRINT', title: 'Quant sprint', emoji: '🧮' },
  { id: 'LOGIC_LAB', title: 'Logic lab', emoji: '🧠' },
];

export default function PlayRoom() {
  const params = useParams<{ code: string }>();
  const code = params.code.toUpperCase();
  const [name, setName] = useState('');
  const [college, setCollege] = useState('');
  const [ready, setReady] = useState(false);
  const [locked, setLocked] = useState(false);
  const [error, setError] = useState('');
  const [questionSetId, setQuestionSetId] = useState('');
  const [selectedMode, setSelectedMode] = useState('CLASSIC');
  const [answerFeedback, setAnswerFeedback] = useState('');
  const [copied, setCopied] = useState(false);
  const [votedMomentId, setVotedMomentId] = useState('');
  const [votedMomentChoice, setVotedMomentChoice] = useState('');
  const arena = useArenaSocket(code);

  useEffect(() => {
    if (sessionStorage.getItem('playerId') && sessionStorage.getItem('sessionToken')) setReady(true);
    const requestedMode = new URLSearchParams(window.location.search).get('mode');
    if (requestedMode && modeOptions.some(item => item.id === requestedMode)) setSelectedMode(requestedMode);
  }, []);

  useEffect(() => { setLocked(Boolean(arena.snapshot?.submitted)); }, [arena.snapshot?.submitted, arena.question]);

  useEffect(() => {
    if (!arena.answerResult) return;
    const message = arena.answerResult.status === 'ACCEPTED'
      ? (arena.answerResult.score > 0 ? `Big brain energy · +${arena.answerResult.score} pts` : 'Locked in. The reveal is coming.')
      : `Answer status: ${String(arena.answerResult.status).toLowerCase().replaceAll('_', ' ')}`;
    setAnswerFeedback(message);
    const timer = window.setTimeout(() => setAnswerFeedback(''), 4200);
    return () => window.clearTimeout(timer);
  }, [arena.answerResult]);

  useEffect(() => {
    if (arena.moment?.momentId && arena.moment.momentId !== votedMomentId) {
      setVotedMomentId('');
      setVotedMomentChoice('');
    }
  }, [arena.moment?.momentId, votedMomentId]);

  async function join(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setError('');
    try {
      const response = await fetch(`${API_BASE_URL}/api/session`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ name, college }) });
      if (!response.ok) throw new Error('Could not create player session. Check that the backend is running.');
      const player = await response.json();
      sessionStorage.setItem('playerId', player.playerId); sessionStorage.setItem('sessionToken', player.token); sessionStorage.setItem('roomCode', code);
      location.reload();
    } catch (reason) { setError(reason instanceof Error ? reason.message : 'Could not join'); }
  }

  async function shareRoom() {
    try {
      await navigator.clipboard.writeText(`${location.origin}/play/${code}?mode=${selectedMode}`);
      setCopied(true); window.setTimeout(() => setCopied(false), 2200);
    } catch { setCopied(false); }
  }

  if (!ready) return <main className="app-shell"><div className="topbar"><a className="page-link" href="/">← ArenaX</a><span className="eyebrow">ROOM {code}</span></div><form className="panel join-card" onSubmit={join}><span className="eyebrow">ROOM {code}</span><h1>Join the arena.</h1><p className="subhead">Your name will appear on the live leaderboard.</p><div className="field"><label htmlFor="name">Player name</label><input id="name" required maxLength={32} value={name} onChange={event => setName(event.target.value)} /></div><div className="field"><label htmlFor="college">College name</label><input id="college" required maxLength={80} value={college} onChange={event => setCollege(event.target.value)} /></div>{error && <p className="error" role="alert">{error}</p>}<button className="primary" type="submit">Join room →</button></form></main>;

  const hostManaged = Boolean(arena.snapshot?.hostManaged);
  const activeMode = hostManaged ? arena.snapshot?.mode || selectedMode : arena.phase === 'LOBBY' ? selectedMode : arena.snapshot?.mode || selectedMode;
  const modeTitle = modeOptions.find(item => item.id === activeMode)?.title || 'Classic arena';
  const myStanding = (arena.snapshot?.players || arena.roster).find((player: any) => player.playerId === sessionStorage.getItem('playerId'));

  return <main className="app-shell play-shell">
    <div className="topbar"><a className="brand" href="/">ARENA<i>X</i></a><span className={`connection ${arena.connected ? 'on' : ''}`}>{arena.connected ? 'Connected' : 'Reconnecting…'}</span></div>
    <div className="hero play-hero">
      <span className="eyebrow">ROOM {code} · {arena.phase.replaceAll('_', ' ')}</span>
      <h1>{arena.phase === 'FINAL_PODIUM' ? 'You made the board.' : arena.phase === 'LOBBY' ? 'Crew, assemble.' : 'Play to win.'}</h1>
      <p className="subhead">{arena.phase === 'LOBBY' ? (hostManaged ? `Your host picked ${modeTitle}. Invite classmates, then wait for the start.` : 'Pick a mode, share the room code, then let the friendly chaos begin.') : `${modeTitle} · Server-scored, speed rewards accuracy. No client clock tricks.`}</p>
      {arena.phase === 'LOBBY' && hostManaged && <div className="host-waiting">🔒 Private host room · the host will start the match</div>}
      {arena.phase === 'LOBBY' && !hostManaged && <div className="lobby-controls">
        <div className="lobby-modes" role="group" aria-label="Choose match mode">{modeOptions.map(item => <button type="button" key={item.id} aria-pressed={selectedMode === item.id} className={`lobby-mode ${selectedMode === item.id ? 'chosen' : ''}`} onClick={() => setSelectedMode(item.id)}><span>{item.emoji}</span>{item.title}</button>)}</div>
        <div className="start-controls"><input aria-label="Optional custom question set ID" placeholder="Optional custom question set ID" value={questionSetId} onChange={event => setQuestionSetId(event.target.value)} /><button className="primary" onClick={() => arena.start(questionSetId.trim() || undefined, selectedMode)}>Start {modeTitle} →</button></div>
      </div>}
    </div>

    <div className="arena-grid"><div>
      {arena.phase === 'LOBBY' ? <section className="panel lobby-panel">
        <div className="lobby-heading"><div><span className="eyebrow">MATCH LOBBY</span><h2>Players are joining</h2></div>{!hostManaged && <button type="button" className="share-button" onClick={shareRoom}>{copied ? 'Link copied ✓' : 'Invite your crew ↗'}</button>}</div>
        <p className="muted"><strong className="roster-count">{arena.roster.length}</strong> / 50 players · room code <b className="room-code">{code}</b></p>
        {arena.roster.length ? <div className="lobby-roster">{arena.roster.slice(0, 12).map((player: any, index: number) => <span title={player.name} key={player.playerId} className="roster-avatar" style={{ '--avatar-hue': `${(index * 43 + 195) % 360}` } as CSSProperties}>{player.name?.slice(0, 1).toUpperCase()}</span>)}{arena.roster.length > 12 && <span className="roster-more">+{arena.roster.length - 12}</span>}</div> : <p className="lobby-empty">It’s quiet… too quiet. Send the invite. 👀</p>}
        <div className="lobby-rules"><span>⚖️ Same question for everyone</span><span>⏱️ Server keeps the time</span><span>🎯 Correct + quick earns more</span></div>
      </section> : arena.question ? <><QuestionCard question={arena.question} locked={locked} onAnswer={(questionId, optionId) => { if (locked) return; setLocked(true); arena.answer(questionId, optionId); }} />{answerFeedback && <div className="answer-toast" role="status">{activeMode === 'MEME_RUSH' ? '💀 ' : '✨ '}{answerFeedback}</div>}</> : <section className="panel phase-panel">
        <span className="eyebrow">{arena.phase.replaceAll('_', ' ')}</span><h2>{arena.phase === 'FINAL_PODIUM' ? 'Match complete. Main-character moment.' : arena.phase === 'QUESTION_REVEAL' ? 'The receipts are in.' : 'Breathe. Next round loading…'}</h2>
        <p className="muted">{arena.phase === 'ROUND_LEADERBOARD' ? 'Rankings just moved. Check the board.' : 'Stay in the room. Your score and rank carry over.'}</p>
        {arena.phase === 'ROUND_LEADERBOARD' && <div className="meme-reaction">{myStanding?.rank === 1 ? '👑 Unreasonably iconic.' : '🫡 We go again.'}</div>}
      </section>}
      {arena.reveal && <section className="panel reveal-panel" style={{ marginTop: 16 }}><span className="eyebrow">ANSWER REVEAL</span><h2>Correct answer: {arena.reveal.correctText}</h2><p className="muted">Answers received: {Object.values(arena.reveal.distribution || {}).reduce((total: number, count: any) => total + count, 0)} · wrong answers score 0, so take the smart guess.</p><p className="meme-reaction">{activeMode === 'MEME_RUSH' ? 'The answer reveal understood the assignment.' : 'Lock in the lesson. Run it back.'}</p></section>}
      {arena.moment && <section className={`panel room-moment ${arena.moment.active ? 'moment-active' : ''}`} aria-live="polite"><div><span className="eyebrow">{arena.moment.kind.replaceAll('_', ' ')} · SCORE-FREE</span><h2>{arena.moment.prompt}</h2></div><div className="moment-options">{arena.moment.options.map((option: string) => <button type="button" key={option} disabled={!arena.moment.active || votedMomentId === arena.moment.momentId} aria-pressed={votedMomentChoice === option} className={`moment-option ${votedMomentChoice === option ? 'moment-voted' : ''}`} onClick={() => { setVotedMomentId(arena.moment.momentId); setVotedMomentChoice(option); arena.voteMoment(arena.moment.momentId, option); }}><span>{option}</span><b>{arena.moment.counts[option] || 0}</b></button>)}</div><p className="muted">{arena.moment.active ? votedMomentId === arena.moment.momentId ? 'Vote locked in. Your score stays untouched.' : 'One vote each · standings stay untouched' : 'Poll closed · thanks for the room energy'}</p></section>}
      {arena.phase === 'FINAL_PODIUM' && <a className="page-link results-link" href={`/play/${code}/results`}>See your stats & the podium →</a>}
    </div><LiveLeaderboard players={arena.roster.length ? arena.roster : arena.snapshot?.players || []} /></div>
  </main>;
}
