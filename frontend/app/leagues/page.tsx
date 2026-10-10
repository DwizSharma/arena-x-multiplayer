'use client';

import { useEffect, useState } from 'react';
import { API_BASE_URL } from '@/lib/api';
import styles from './page.module.css';

type Period = 'WEEKLY' | 'MONTHLY' | 'ALL_TIME';
type PlayerStanding = { rank: number; playerId: string; name: string; college: string; points: number; matches: number; wins: number; accuracyPercent: number };
type CollegeStanding = { rank: number; college: string; points: number; matches: number; players: number };
type League = { period: Period; players: PlayerStanding[]; colleges: CollegeStanding[] };

const periods: { id: Period; title: string }[] = [
  { id: 'WEEKLY', title: 'This week' },
  { id: 'MONTHLY', title: 'This month' },
  { id: 'ALL_TIME', title: 'All time' },
];

export default function LeaguesPage() {
  const [period, setPeriod] = useState<Period>('ALL_TIME');
  const [league, setLeague] = useState<League | null>(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError('');
    void fetch(`${API_BASE_URL}/api/leagues?period=${period}`, { cache: 'no-store', signal: controller.signal })
      .then(async response => {
        const body = await response.json().catch(() => ({}));
        if (!response.ok) throw new Error(body.error || `Could not load standings (${response.status})`);
        return body as League;
      })
      .then(setLeague)
      .catch(reason => {
        if (!controller.signal.aborted) setError(reason instanceof Error ? reason.message : 'Could not load standings.');
      })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [period]);

  return <main className={`app-shell league-shell ${styles.leagueShell}`}>
    <header className="topbar"><a className="brand" href="/">ARENA<i>X</i></a><a className="page-link" href="/">← Back to arena</a></header>
    <section className="hero"><span className="eyebrow">THE CROWD KEEP SCORE</span><h1>League standings.</h1><p className="subhead">Every match counts. See who’s climbing and which colleges are taking the lead.</p></section>
    <div className="league-tabs" role="group" aria-label="Standings time period">
      {periods.map(item => <button key={item.id} type="button" aria-pressed={period === item.id} className={period === item.id ? 'active' : ''} onClick={() => setPeriod(item.id)}>{item.title}</button>)}
    </div>
    {error && <p className="error" role="alert">{error}</p>}
    {loading && <p className="muted" role="status">Loading standings…</p>}
    {!loading && !error && league && <div className="league-grid">
      <section className="panel league-table-panel"><header className="league-heading"><div><span className="eyebrow">TOP PLAYERS</span><h2>Individual standings</h2></div><span className="player-count">{league.players.length} players</span></header>
        {league.players.length ? <div className="league-table-scroll"><table className="league-table"><thead><tr><th>Rank</th><th>Player</th><th>College</th><th>Points</th><th>Wins</th><th>Accuracy</th></tr></thead><tbody>{league.players.map(player => <tr key={player.playerId}><td className="league-rank">#{player.rank}</td><td><strong>{player.name}</strong><small>{player.matches} matches</small></td><td>{player.college}</td><td>{player.points.toLocaleString()}</td><td>{player.wins}</td><td>{player.accuracyPercent}%</td></tr>)}</tbody></table></div> : <p className="league-empty">No completed matches in this period yet. Start a room and claim the top spot.</p>}
      </section>
      <section className="panel league-table-panel"><header className="league-heading"><div><span className="eyebrow">CAMPUS RANKINGS</span><h2>College standings</h2></div><span className="player-count">{league.colleges.length} colleges</span></header>
        {league.colleges.length ? <div className="league-table-scroll"><table className="league-table college-table"><thead><tr><th>Rank</th><th>College</th><th>Points</th><th>Matches</th><th>Players</th></tr></thead><tbody>{league.colleges.map(college => <tr key={college.college}><td className="league-rank">#{college.rank}</td><td><strong>{college.college}</strong></td><td>{college.points.toLocaleString()}</td><td>{college.matches}</td><td>{college.players}</td></tr>)}</tbody></table></div> : <p className="league-empty">College standings appear here after the first match.</p>}
      </section>
    </div>}
  </main>;
}
