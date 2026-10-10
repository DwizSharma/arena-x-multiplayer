'use client';
import { AnimatePresence, motion } from 'framer-motion';
import { useEffect, useRef } from 'react';
export function LiveLeaderboard({ players }: { players: any[] }) {
  const previous = useRef(new Map<string, number>());
  useEffect(() => { players.forEach(p => previous.current.set(p.playerId, p.rank)); }, [players]);
  return <section className="panel leaderboard"><header><div><span className="eyebrow">LIVE STANDINGS</span><h2>Leaderboard</h2></div><span className="player-count">{players.length} players</span></header>
    <div className="leader-list"><AnimatePresence>{players.map((p, i) => { const before = previous.current.get(p.playerId); const delta = before == null ? 0 : before - p.rank; return <motion.div layout key={p.playerId} initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0 }} transition={{ duration: .28 }} className={`leader-row ${i < 3 ? 'top-player' : ''}`}><span className="rank">{String(p.rank).padStart(2, '0')}</span><span className="avatar">{p.name?.slice(0, 1).toUpperCase()}</span><div className="leader-person"><strong>{p.name}</strong><small>{p.college}</small></div><span className={`delta ${delta > 0 ? 'up' : delta < 0 ? 'down' : ''}`}>{delta > 0 ? `+${delta}` : delta < 0 ? delta : '—'}</span><strong className="points">{p.score.toLocaleString()}</strong></motion.div>; })}</AnimatePresence>
      {!players.length && <p className="muted">Waiting for players to join…</p>}</div>
  </section>;
}
