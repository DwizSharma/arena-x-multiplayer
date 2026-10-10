# ArenaX Multiplayer

ArenaX is a live, server-authoritative quiz arena built with Java 21, Spring Boot, STOMP over WebSocket, PostgreSQL, Redis, and Next.js. The repository includes a working demonstration match, browser UI, projector route, Docker deployment, and 50-client simulation harness.

## Architecture

```text
 Player browser                         ArenaX backend
 ┌─────────────────┐     POST /api/session      ┌──────────────────────┐
 │ Join form        │ ─────────────────────────> │ SessionRegistry      │
 │                 │ <── playerId + token ────── │                      │
 │ STOMP client     │ ═══ /ws-arena?token ═════> │ Handshake validation │
 │                 │ ─── /app/room/{code}/join > │ RoomEngine (memory)  │
 │ Question + timer │ <── /user/queue/question ─ │ server clock + FSM   │
 │                 │ ─── /app/room/{code}/answer>│ first answer wins    │
 │ Leaderboard      │ <── /topic/room/{code}/... │ score + standings    │
 └─────────────────┘                            └──────────┬───────────┘
                                                         │ JPA schema
                                                   ┌─────┴─────┐
                                                   │ Postgres  │  Redis
                                                   └───────────┘
```

The simple broker broadcasts room state, roster, questions, reveals, and standings. Personalized shuffled questions and answer acknowledgements use user queues. The projector subscribes to public room topics.

## Server clock, scoring, and answer integrity

The server records the start instant and receives each answer itself. It ignores client timestamps, calculates elapsed time from its own clock, and rejects an answer received after the question deadline plus a 250 ms network grace period. The grace period absorbs small network scheduling delays; it does not extend the scored time window. The speed bonus is capped at zero after the allowed time, and wrong answers score zero.

The engine accepts only the first answer for each player and question using an atomic `putIfAbsent`. Later packets are rejected as duplicates. Public question messages omit the correct option. Each player receives the same options in a deterministic player-specific shuffle; the answer is mapped back to its canonical option ID on the server. The server validates that the player's session token was issued by ArenaX and binds that identity to the WebSocket session.

Rooms progress through `LOBBY → QUESTION_INTRO (3 s) → QUESTION_ACTIVE (20–45 s) → QUESTION_REVEAL (5 s) → ROUND_LEADERBOARD (5 s)`. If every joined player submits during the active phase, the engine cancels the pending timeout and reveals immediately. After the final question it broadcasts the podium state. Reconnecting clients request a snapshot with remaining time, their shuffled options, submission status, score, and rank.

## Start with Docker

Install Docker with the Compose plugin, then run from the repository root:

```bash
docker compose up --build
```

Open [http://localhost:3000](http://localhost:3000), join room `APT892`, then press **Start match** from a connected player. PostgreSQL is available on port 5432 (`arena` / `arena` / `arena_db`) and Redis on port 6379. Set `DATABASE_PASSWORD` before startup to override the development database password. The compose health checks wait for PostgreSQL and Redis before starting the backend, and for the backend before starting Next.js.

## Run services locally

Start only the data services:

```bash
docker compose up -d postgres redis
```

In a terminal, run Spring Boot (Java 21 and Maven required):

```bash
cd backend
mvn spring-boot:run
```

In another terminal, run the Next.js app (Node 20+ required):

```bash
cd frontend
npm install
npm run dev
```

Visit [http://localhost:3000](http://localhost:3000). By default, the browser calls the backend at `http://localhost:8080` and connects to `ws://localhost:8080/ws-arena`. Override these with `NEXT_PUBLIC_API_URL` and `NEXT_PUBLIC_WS_URL` when running behind a proxy or on another host.

## 50-player load simulation

Start the backend and make sure room `APT892` has not already been started. Then run:

```bash
cd simulation
npm install
node simulate_50_players.js
```

The script creates 50 sessions, connects concurrent STOMP clients, joins them as `Student_1` through `Student_50`, starts the match, and chooses randomized answers after 1.2–7.5 seconds. It prints accepted/rejected counts and server-measured answer elapsed times. Configure `API_URL`, `WS_URL`, and `ROOM_CODE` to target another local deployment or room.

## Routes and packages

- `/play/[code]` — join form, lobby, question flow, reveal, and live standings.
- `/play/[code]/results` — final podium view.
- `/projector/[code]` — auditorium standings and answer distribution.
- `backend/` — Spring Boot service and JPA entities for colleges, question sets, questions, and match history.
- `frontend/` — Next.js App Router client, STOMP reconnect hook, and responsive views.
- `simulation/` — 50-player WebSocket simulation.

## Current deployment scope

The engine runs a bundled three-question demonstration set. Active rooms, issued sessions, option maps, and detailed player answer analytics stay in backend memory; completed match summaries and college points are written to PostgreSQL. Question set and question entities are defined, but the demo engine does not yet load custom question sets. Restarting the backend clears active rooms and player tokens, and the detailed per-player analytics are not durable. Redis is provisioned for future shared room/session state; this version uses an in-process scheduler and room map, so run a single backend replica.

## Public GitHub remote

The local repository is on branch `main`. GitHub CLI authentication could not be completed in this environment because its saved token is invalid. Authenticate, then create and push the requested **public** repository:

```bash
gh auth login -h github.com
gh repo create arena-x-multiplayer --source=. --remote=origin --push --public
```

If creating the public repository in the GitHub website instead, add its URL and push:

```bash
git remote add origin <USER_GITHUB_REPO_URL>
git push -u origin main
```

Push subsequent milestone commits with `git push`.
