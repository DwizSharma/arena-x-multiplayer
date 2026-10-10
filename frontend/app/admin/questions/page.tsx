'use client';

import { FormEvent, useEffect, useState } from 'react';

const api = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8081';
type Question = { prompt: string; options: { id: string; text: string }[]; correctOptionId: string; topic?: string; difficulty?: string; imageUrl?: string; tableData?: string[][] };
type QuestionSet = { id: string; title: string; archived: boolean; questions: Question[] };
const example = JSON.stringify([{ prompt: 'What is 15% of 200?', options: [{ id: 'A', text: '20' }, { id: 'B', text: '30' }, { id: 'C', text: '40' }, { id: 'D', text: '50' }], correctOptionId: 'B', topic: 'Quant', difficulty: 'Easy' }], null, 2);

async function responseJson(response: Response) {
  const value = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(value.message || value.error || `Request failed (${response.status})`);
  return value;
}

export default function QuestionSetsPage() {
  const [token, setToken] = useState('');
  const [sets, setSets] = useState<QuestionSet[]>([]);
  const [selected, setSelected] = useState('');
  const [title, setTitle] = useState('');
  const [questions, setQuestions] = useState(example);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => { setToken(sessionStorage.getItem('arenaAdminToken') || ''); }, []);
  async function loadSets(activeToken = token): Promise<QuestionSet[]> {
    if (!activeToken) return [];
    const list = await responseJson(await fetch(`${api}/api/admin/question-sets`, { headers: { Authorization: `Bearer ${activeToken}` }, cache: 'no-store' })) as QuestionSet[];
    setSets(list); return list;
  }
  useEffect(() => { void loadSets().catch(reason => setError(reason instanceof Error ? reason.message : 'Could not load question sets.')); }, [token]);

  function choose(set: QuestionSet) {
    setSelected(set.id); setTitle(set.title); setQuestions(JSON.stringify(set.questions, null, 2)); setError(''); setNotice('');
  }
  function newSet() { setSelected(''); setTitle(''); setQuestions(example); setError(''); setNotice(''); }
  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setError(''); setNotice(''); setBusy(true);
    try {
      const parsed = JSON.parse(questions) as Question[];
      if (!Array.isArray(parsed)) throw new Error('Questions must be a JSON array.');
      const response = await fetch(`${api}/api/admin/question-sets${selected ? `/${encodeURIComponent(selected)}` : ''}`, {
        method: selected ? 'PUT' : 'POST', headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
        body: JSON.stringify({ title, questions: parsed }),
      });
      const saved = await responseJson(response);
      await loadSets();
      if (saved.id) setSelected(saved.id);
      setNotice('Question set saved. Order in the JSON list is the play order.');
    } catch (reason) { setError(reason instanceof Error ? reason.message : 'Could not save question set.'); }
    finally { setBusy(false); }
  }
  async function duplicate() {
    if (!selected) return;
    setBusy(true); setError('');
    try { const copy = await responseJson(await fetch(`${api}/api/admin/question-sets/${encodeURIComponent(selected)}/duplicate`, { method: 'POST', headers: { Authorization: `Bearer ${token}` } })); const updated = await loadSets(); const newCopy = updated.find(item => item.id === copy.id); if (newCopy) choose(newCopy); setNotice('Duplicate created.'); }
    catch (reason) { setError(reason instanceof Error ? reason.message : 'Could not duplicate set.'); }
    finally { setBusy(false); }
  }
  async function toggleArchive() {
    const set = sets.find(item => item.id === selected); if (!set) return;
    setBusy(true); setError('');
    try { await responseJson(await fetch(`${api}/api/admin/question-sets/${encodeURIComponent(selected)}/archive`, { method: 'PATCH', headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' }, body: JSON.stringify({ archived: !set.archived }) })); await loadSets(); setNotice(set.archived ? 'Question set restored.' : 'Question set archived.'); }
    catch (reason) { setError(reason instanceof Error ? reason.message : 'Could not update set.'); }
    finally { setBusy(false); }
  }
  if (!token) return <main className="app-shell"><section className="panel"><a className="page-link" href="/admin">← Sign in to host console</a><h1>Host sign-in required</h1></section></main>;

  return <main className="app-shell admin-shell">
    <header className="topbar"><a className="page-link" href="/admin">← Host console</a><span className="eyebrow">QUESTION SETS</span></header>
    <section className="admin-heading"><div><span className="eyebrow">AUTHORING</span><h1>Your quiz library.</h1><p className="subhead">Create reusable sets, edit questions, and change play order.</p></div><button className="secondary" type="button" onClick={newSet}>New question set</button></section>
    <div className="admin-grid">
      <section className="panel"><span className="eyebrow">SAVED SETS</span>{sets.length ? sets.map(set => <button key={set.id} type="button" className={`admin-mode ${selected === set.id ? 'selected' : ''}`} style={{ width: '100%', marginTop: 8, justifyContent: 'space-between' }} onClick={() => choose(set)}><span>{set.title}</span><small>{set.archived ? 'Archived' : `${set.questions.length} questions`}</small></button>) : <p className="muted">No question sets yet.</p>}</section>
      <form className="panel" onSubmit={save}><span className="eyebrow">{selected ? 'EDIT SET' : 'NEW SET'}</span><div className="field"><label htmlFor="set-title">Set title</label><input id="set-title" required maxLength={200} value={title} onChange={event => setTitle(event.target.value)} /></div><div className="field"><label htmlFor="set-questions">Questions (JSON, in play order)</label><textarea id="set-questions" required rows={20} value={questions} onChange={event => setQuestions(event.target.value)} spellCheck={false} style={{ width: '100%', fontFamily: 'monospace', fontSize: 12 }} /></div><p className="muted">Each item needs prompt, options (2–6 with unique id/text), and correctOptionId. Optional fields: topic, difficulty, imageUrl, tableData.</p>{error && <p className="error" role="alert">{error}</p>}{notice && <p className="admin-notice" role="status">{notice}</p>}<div className="admin-room-actions"><button className="primary" disabled={busy}>{busy ? 'Saving…' : 'Save question set'}</button>{selected && <button className="secondary" type="button" disabled={busy} onClick={() => void duplicate()}>Duplicate</button>}{selected && <button className="secondary" type="button" disabled={busy} onClick={() => void toggleArchive()}>{sets.find(set => set.id === selected)?.archived ? 'Restore' : 'Archive'}</button>}</div></form>
    </div>
  </main>;
}
