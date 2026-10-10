'use client';

import { FormEvent, useEffect, useState } from 'react';

const apiBase = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8081';
const modes = [
  { id: 'CLASSIC', name: 'Classic arena', emoji: '🏁' },
  { id: 'MEME_RUSH', name: 'Meme rush', emoji: '💀' },
  { id: 'QUANT_SPRINT', name: 'Quant sprint', emoji: '🧮' },
  { id: 'LOGIC_LAB', name: 'Logic lab', emoji: '🧠' },
];

type HostRoom = { code: string; mode: string; phase: string; playerCount: number; capacity: number; joinPath: string };
type SetOption = { id: string; title: string; archived: boolean };

async function readResponse(response: Response) {
  const body = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(body.message || body.error || `Request failed (${response.status})`);
  return body;
}

export default function AdminPage() {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [token, setToken] = useState('');
  const [room, setRoom] = useState<HostRoom | null>(null);
  const [mode, setMode] = useState('CLASSIC');
  const [questionSetId, setQuestionSetId] = useState('');
  const [questionSets, setQuestionSets] = useState<SetOption[]>([]);
  const [busy, setBusy] = useState(false);
  const [copied, setCopied] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');

  useEffect(() => {
    setToken(sessionStorage.getItem('arenaAdminToken') || '');
    const savedRoom = sessionStorage.getItem('arenaAdminRoom');
    if (savedRoom) {
      try { setRoom(JSON.parse(savedRoom) as HostRoom); } catch { sessionStorage.removeItem('arenaAdminRoom'); }
    }
  }, []);

  useEffect(() => {
    if (!token) return;
    void fetch(`${apiBase}/api/admin/question-sets`, { headers: { Authorization: `Bearer ${token}` }, cache: 'no-store' })
      .then(readResponse).then((items: SetOption[]) => setQuestionSets(items.filter(item => !item.archived)))
      .catch(() => undefined);
  }, [token]);

  useEffect(() => {
    if (!token || !room?.code) return;
    let alive = true;
    const refresh = async () => {
      try {
        const response = await fetch(`${apiBase}/api/admin/rooms/${encodeURIComponent(room.code)}`, { headers: { Authorization: `Bearer ${token}` }, cache: 'no-store' });
        const current = await readResponse(response) as HostRoom;
        if (alive) { setRoom(current); sessionStorage.setItem('arenaAdminRoom', JSON.stringify(current)); }
      } catch (reason) {
        if (alive && reason instanceof Error && reason.message.includes('expired')) setError('Your admin session expired. Sign in again to continue.');
      }
    };
    void refresh();
    const interval = window.setInterval(() => void refresh(), 2500);
    return () => { alive = false; window.clearInterval(interval); };
  }, [token, room?.code]);

  async function signIn(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setError(''); setBusy(true);
    try {
      const response = await fetch(`${apiBase}/api/admin/login`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username, password }) });
      const session = await readResponse(response);
      sessionStorage.setItem('arenaAdminToken', session.token);
      setToken(session.token); setPassword(''); setNotice('Signed in. Your admin session lasts up to eight hours.');
    } catch (reason) { setError(reason instanceof Error ? reason.message : 'Could not sign in.'); }
    finally { setBusy(false); }
  }

  async function createRoom() {
    setError(''); setNotice(''); setBusy(true);
    try {
      const response = await fetch(`${apiBase}/api/admin/rooms`, { method: 'POST', headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' }, body: JSON.stringify({ mode, questionSetId: questionSetId.trim() || null }) });
      const created = await readResponse(response) as HostRoom;
      setRoom(created); sessionStorage.setItem('arenaAdminRoom', JSON.stringify(created));
      setNotice('Private room created. Share its invite link or room code with your students.');
    } catch (reason) { setError(reason instanceof Error ? reason.message : 'Could not create room.'); }
    finally { setBusy(false); }
  }

  async function startRoom() {
    if (!room) return;
    setError(''); setBusy(true);
    try {
      const response = await fetch(`${apiBase}/api/admin/rooms/${encodeURIComponent(room.code)}/start`, { method: 'POST', headers: { Authorization: `Bearer ${token}` } });
      const started = await readResponse(response) as HostRoom;
      setRoom(started); sessionStorage.setItem('arenaAdminRoom', JSON.stringify(started)); setNotice('Match started. Students are now racing the clock.');
    } catch (reason) { setError(reason instanceof Error ? reason.message : 'Could not start the match.'); }
    finally { setBusy(false); }
  }

  async function launchMoment(kind: string) {
    if (!room) return;
    setError(''); setNotice(''); setBusy(true);
    try {
      await readResponse(await fetch(`${apiBase}/api/admin/rooms/${encodeURIComponent(room.code)}/moments`, { method: 'POST', headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' }, body: JSON.stringify({ kind }) }));
      setNotice('Fun moment is live for everyone in the room.');
    } catch (reason) { setError(reason instanceof Error ? reason.message : 'Could not launch that moment.'); }
    finally { setBusy(false); }
  }

  async function copyInvite() {
    if (!room) return;
    try { await navigator.clipboard.writeText(`${location.origin}${room.joinPath}`); setCopied(true); window.setTimeout(() => setCopied(false), 2200); }
    catch { setError('Clipboard access is unavailable. Share the room code: ' + room.code); }
  }

  async function signOut() {
    if (token) await fetch(`${apiBase}/api/admin/logout`, { method: 'POST', headers: { Authorization: `Bearer ${token}` } }).catch(() => undefined);
    sessionStorage.removeItem('arenaAdminToken'); sessionStorage.removeItem('arenaAdminRoom');
    setToken(''); setRoom(null); setNotice('Signed out.');
  }

  const selectedMode = modes.find(item => item.id === (room?.mode || mode)) || modes[0];
  const matchRunning = Boolean(room && room.phase !== 'LOBBY' && room.phase !== 'FINAL_PODIUM');
  const canLaunchMoment = Boolean(room && room.phase !== 'LOBBY' && room.phase !== 'FINAL_PODIUM');

  return <main className="app-shell admin-shell">
    <header className="topbar admin-topbar"><a className="brand" href="/">ARENA<i>X</i></a><span className="eyebrow">FACULTY HOST CONSOLE</span></header>
    {!token ? <form className="panel admin-login" onSubmit={signIn}>
      <span className="eyebrow">PRIVATE HOST ACCESS</span><h1>Run the room.</h1><p className="subhead">Sign in with the admin credentials configured by your ArenaX operator.</p>
      <div className="field"><label htmlFor="admin-user">Admin username</label><input id="admin-user" autoComplete="username" required maxLength={120} value={username} onChange={event => setUsername(event.target.value)} /></div>
      <div className="field"><label htmlFor="admin-password">Password</label><input id="admin-password" type="password" autoComplete="current-password" required maxLength={256} value={password} onChange={event => setPassword(event.target.value)} /></div>
      {error && <p className="error" role="alert">{error}</p>}{notice && <p className="admin-notice" role="status">{notice}</p>}
      <button type="submit" className="primary admin-submit" disabled={busy}>{busy ? 'Signing in…' : 'Sign in as host →'}</button>
      <p className="admin-footnote">Student accounts are created when they join a room. Admin credentials are configured in the server environment.</p>
    </form> : <>
      <section className="admin-heading"><div><span className="eyebrow">YOUR CLASSROOM, YOUR RULES</span><h1>Make it a match.</h1><p className="subhead">Create a private room, send the invite, and start when your crew is ready.</p></div><button type="button" className="admin-logout" onClick={signOut}>Sign out ↗</button></section>
      <div className="admin-grid">
        <section className="panel admin-create-card"><span className="eyebrow">ROOM SETUP</span><h2>Build your next session</h2><p className="muted">Only people with your generated room code can find this room.</p>
          <label className="admin-label" htmlFor="admin-mode">Game mode</label><div id="admin-mode" className="admin-mode-grid" role="group" aria-label="Choose a game mode">{modes.map(item => <button key={item.id} type="button" aria-pressed={mode === item.id} className={`admin-mode ${mode === item.id ? 'selected' : ''}`} onClick={() => setMode(item.id)}><span>{item.emoji}</span>{item.name}</button>)}</div>
          <div className="field"><label htmlFor="set-id">Question set</label><select id="set-id" value={questionSetId} onChange={event => setQuestionSetId(event.target.value)}><option value="">Use the selected mode’s built-in questions</option>{questionSets.map(set => <option key={set.id} value={set.id}>{set.title}</option>)}</select><a className="page-link" href="/admin/questions">Manage question sets →</a></div>
          <button className="primary admin-submit" type="button" onClick={createRoom} disabled={busy}>{busy ? 'Working…' : room ? 'Create another private room →' : 'Create private room →'}</button>
        </section>

        <section className="panel admin-live-card"><div className="admin-live-heading"><div><span className="eyebrow">LIVE ROOM CONTROL</span><h2>{room ? `Room ${room.code}` : 'Your room will appear here'}</h2></div>{room && <span className="host-phase">{room.phase.replaceAll('_', ' ')}</span>}</div>
          {room ? <><div className="private-code"><span>STUDENT JOIN CODE</span><strong>{room.code}</strong></div><div className="room-stats"><span><b>{room.playerCount}</b>/{room.capacity} students in lobby</span><span>{selectedMode.emoji} {selectedMode.name}</span></div><div className="admin-room-actions"><button type="button" className="secondary" onClick={copyInvite}>{copied ? 'Invite copied ✓' : 'Copy student invite ↗'}</button><button type="button" className="primary" onClick={startRoom} disabled={busy || room.playerCount < 1 || room.phase !== 'LOBBY'}>{matchRunning ? 'Match is live ✓' : room.phase === 'FINAL_PODIUM' ? 'Match complete' : room.playerCount ? 'Start match →' : 'Waiting for students…'}</button></div><p className="invite-url">Invite link: <code>{room.joinPath}</code></p><div className="fun-control"><span className="eyebrow">BETWEEN-ROUND ENERGY</span><p>Launch a quick, score-free room vote. It never changes standings.</p><div className="fun-buttons"><button type="button" className="secondary" disabled={busy || !canLaunchMoment} onClick={() => launchMoment('HYPE_CHECK')}>🔥 Hype check</button><button type="button" className="secondary" disabled={busy || !canLaunchMoment} onClick={() => launchMoment('MEME_BREAK')}>💀 Meme break</button><button type="button" className="secondary" disabled={busy || !canLaunchMoment} onClick={() => launchMoment('BRAIN_BREAK')}>🍕 Brain break</button></div></div></> : <div className="admin-empty"><div className="empty-icon">🎟️</div><p>Create a room to get a unique join code and live controls.</p></div>}
          {error && <p className="error" role="alert">{error}</p>}{notice && <p className="admin-notice" role="status">{notice}</p>}
        </section>
      </div>
      <p className="admin-footnote">Room codes are randomly generated. Active rooms are held in server memory and are cleared if the backend restarts.</p>
    </>}
  </main>;
}
