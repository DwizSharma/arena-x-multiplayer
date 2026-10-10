'use client';

import { FormEvent, useState } from 'react';

const modes = [
  { id: 'CLASSIC', icon: '🏁', name: 'Classic arena', meta: '3 rounds · mixed topics', note: 'The all-round placement warm-up.' },
  { id: 'MEME_RUSH', icon: '💀', name: 'Meme rush', meta: '5 rounds · 20 sec each', note: 'Aptitude questions with group-chat energy.' },
  { id: 'QUANT_SPRINT', icon: '🧮', name: 'Quant sprint', meta: '5 rounds · number skills', note: 'Percentages, ratios and speed maths.' },
  { id: 'LOGIC_LAB', icon: '🧠', name: 'Logic lab', meta: '5 rounds · brain teasers', note: 'Patterns, deductions and tiny plot twists.' },
] as const;

function freshCode() {
  const alphabet = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  return `AX${Array.from({ length: 4 }, () => alphabet[Math.floor(Math.random() * alphabet.length)]).join('')}`;
}

export default function Home() {
  const [mode, setMode] = useState<string>('CLASSIC');
  const [room, setRoom] = useState('APT892');
  const [error, setError] = useState('');

  function enterRoom(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const code = room.trim().toUpperCase();
    if (!/^[A-Z0-9]{3,10}$/.test(code)) {
      setError('Use 3–10 letters or numbers for the room code.');
      return;
    }
    window.location.href = `/play/${encodeURIComponent(code)}?mode=${encodeURIComponent(mode)}`;
  }

  function createRoom() {
    const code = freshCode();
    setRoom(code);
    window.location.href = `/play/${code}?mode=${encodeURIComponent(mode)}`;
  }

  return <main className="app-shell home-shell">
    <header className="topbar home-topbar">
      <a className="brand" href="/" aria-label="ArenaX home">ARENA<i>X</i><span className="brand-dot">●</span></a>
      <div className="home-nav"><span className="live-pill"><i /> LIVE PLAY</span><a href="#modes">Game modes</a><a href="/leagues">Leagues</a></div>
    </header>

    <section className="home-hero">
      <div className="hero-copy">
        <span className="eyebrow">APTITUDE, WITH A CROWD</span>
        <h1>Brains on.<br /><em>Pressure up.</em></h1>
        <p>Race your friends through placement-style questions. Every millisecond matters. Your group chat will hear about it.</p>
        <div className="hero-stats"><span><b>50</b> players per room</span><span><b>0</b> pay-to-win powerups</span><span><b>100%</b> server-scored</span></div>
      </div>
      <div className="hero-art" aria-label="A live quiz scoreboard illustration" role="img">
        <div className="orbit orbit-one" /><div className="orbit orbit-two" />
        <div className="art-sticker sticker-zap">⚡</div><div className="art-sticker sticker-brain">🧠</div>
        <div className="score-card"><div className="score-top"><span>ROUND 03 / 05</span><span className="score-live"><i /> LIVE</span></div><div className="score-question">A little pressure<br />never hurt.</div><div className="score-bars"><i /><i /><i /><i /></div><div className="score-bottom"><span>YOUR RANK</span><b>#02 <small>▲ 3</small></b></div></div>
        <div className="meme-note">POV: you actually<br />practised 😎</div>
      </div>
    </section>

    <section className="mode-section" id="modes">
      <div className="section-heading"><div><span className="eyebrow">PICK YOUR VIBE</span><h2>How are we playing?</h2></div><span className="section-aside">Same fair scoring. Different energy.</span></div>
      <div className="mode-grid" role="group" aria-label="Choose a game mode">
        {modes.map((item) => <button key={item.id} type="button" aria-pressed={mode === item.id} onClick={() => setMode(item.id)} className={`mode-card ${mode === item.id ? 'selected' : ''}`}>
          <span className="mode-icon">{item.icon}</span><span className="mode-meta">{item.meta}</span><strong>{item.name}</strong><span className="mode-note">{item.note}</span><span className="mode-check">{mode === item.id ? '✓ SELECTED' : 'CHOOSE MODE'}</span>
        </button>)}
      </div>
    </section>

    <section className="launch-panel">
      <div className="launch-intro"><span className="eyebrow">YOUR NEXT REMATCH STARTS HERE</span><h2>Get in the game.</h2><p>Create a room for your crew or enter a code from your host.</p></div>
      <div className="launch-actions">
        <button type="button" className="primary create-room" onClick={createRoom}>Create a room <span>↗</span></button>
        <form onSubmit={enterRoom} className="join-room-form"><label htmlFor="room">Have a room code?</label><div className="join-room-row"><input id="room" value={room} onChange={event => { setRoom(event.target.value); setError(''); }} maxLength={10} autoCapitalize="characters" placeholder="e.g. APT892" aria-describedby={error ? 'room-error' : undefined} /><button type="submit" className="secondary">Join room →</button></div>{error && <p id="room-error" className="error" role="alert">{error}</p>}</form>
      </div>
    </section>

    <footer className="home-footer"><span>Made for the moments between “easy question” and “wait, what?”</span><span>ARENA<i>X</i> · PLAY FAIR. PLAY AGAIN.</span></footer>
  </main>;
}
