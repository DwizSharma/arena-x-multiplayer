'use client';
import { motion } from 'framer-motion';
import { useParams } from 'next/navigation';
import { useEffect, useState } from 'react';
import { useArenaSocket } from '@/lib/socket/useArenaSocket';
export default function Results() {
  const { code } = useParams<{ code: string }>();
  const arena = useArenaSocket(code);
  const [persistedPlayers, setPersistedPlayers] = useState<any[]>([]);
  useEffect(() => {
    const api = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8081';
    void fetch(`${api}/api/matches/${encodeURIComponent(code)}/results`, { cache: 'no-store' })
      .then(response => response.ok ? response.json() : [])
      .then(items => { if (Array.isArray(items)) setPersistedPlayers(items); })
      .catch(() => undefined);
  }, [code]);
  const players = persistedPlayers.length ? persistedPlayers : arena.result?.length ? arena.result : arena.snapshot?.players || arena.leaderboard;
  const podium = [players[1], players[0], players[2]].filter(Boolean);
  const selfId = typeof window === 'undefined' ? '' : sessionStorage.getItem('playerId');
  const self = players.find((p: any) => p.playerId === selfId);
  return <main className="app-shell">
    <div className="topbar"><a className="page-link" href={`/play/${code}`}>← Back to arena</a><span className="eyebrow">FINAL RESULTS</span></div>
    <section className="hero"><span className="eyebrow">ROOM {code.toUpperCase()}</span><h1>That’s a wrap.</h1><p className="subhead">Here are the final standings.</p></section>
    <section className="podium">{podium.map((p: any, i: number) => <motion.div key={p.playerId} initial={{ opacity: 0, y: 35 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: i * .15 }} className={`podium-card ${p.rank === 1 ? 'first' : ''}`}><div className="podium-number">#{p.rank}</div><strong>{p.name}</strong><p className="muted">{p.college}</p><b>{p.score.toLocaleString()} pts</b></motion.div>)}</section>
    {self && <section className="panel"><span className="eyebrow">YOUR PERFORMANCE</span><h2>{self.accuracyPercent}% accuracy <span className="muted">· rank #{self.rank}</span></h2><p className="muted">Average response: {(self.averageSpeedMs / 1000).toFixed(2)} seconds</p><div className="topic-breakdown">{Object.entries(self.topics || {}).map(([topic, value]: [string, any]) => <div className="topic-row" key={topic}><span>{topic}</span><div className="topic-track"><div style={{ width: `${value.accuracyPercent}%` }} /></div><strong>{Math.round(value.accuracyPercent)}%</strong><small>{value.averageSpeedMs ? `${(value.averageSpeedMs / 1000).toFixed(1)}s avg` : '—'}</small></div>)}</div></section>}
    <div style={{ marginTop: 20 }}><a className="page-link" href={`/projector/${code}`}>Open projector view →</a></div>
  </main>;
}
