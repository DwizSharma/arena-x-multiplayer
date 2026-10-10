# ArenaX Multiplayer

Repository: https://github.com/DwizSharma/arena-x-multiplayer

## What it does
ArenaX is a live, server-authoritative quiz arena. Players join a room from their phones, answer timed multiple-choice questions, and see a live leaderboard, while a projector view shows standings and answer distribution on a big screen. Problem statement: **YOUR_PROBLEM_STATEMENT_NUMBER**. Statement number: **YOUR_STATEMENT_NUMBER**.

## Done / Left / Plan
**Done**
- Join flow (name + college), lobby, timed questions, reveal, round leaderboard, final podium
- Server-side clock, first-answer-wins scoring, reconnect snapshots
- Projector view and custom question sets stored in PostgreSQL
- Match summaries and college points saved to PostgreSQL
- 50-player WebSocket simulation script
- Docker Compose deployment with health checks

**Left**
- Restore active rooms after a backend restart (rooms and sessions are in memory today)
- Support more than one backend replica (in-process scheduler and room map)
- Persist detailed per-player answer analytics
- Deploy to a public URL

**Plan**
- Use Redis snapshots to rebuild rooms on startup
- Move room state and scheduling out of process so the backend can scale horizontally
- Write answer analytics to PostgreSQL at the end of each match
- Deploy the Compose stack to a cloud host and add the live URL below

## Architecture and why
- **Backend:** Java 21 + Spring Boot. STOMP over WebSocket gives low-latency two-way messaging; the simple broker broadcasts room state, roster, reveals and standings, and user queues carry each player's shuffled question and answer acknowledgement.
- **Server-authoritative game engine:** the server owns the clock and ignores client timestamps. Answers go through an atomic `putIfAbsent`, so only the first answer per player per question counts. A 250 ms grace period absorbs network delay without extending the scored window. This keeps scoring fair.
- **Per-player option shuffle:** each player sees options in a deterministic player-specific order, mapped back to canonical IDs on the server. Public messages never contain the correct answer.
- **Session tokens:** `POST /api/session` issues a token that is validated at WebSocket handshake and bound to the connection.
- **PostgreSQL:** question sets, colleges, match history and college points (durable data).
- **Redis:** expiring JSON snapshots of room state; the in-memory room stays authoritative.
- **Frontend:** Next.js (App Router) + Tailwind, with a STOMP client hook that reconnects and requests a snapshot.
- **Room flow:** `LOBBY -> QUESTION_INTRO (3 s) -> QUESTION_ACTIVE (20-45 s) -> QUESTION_REVEAL (5 s) -> ROUND_LEADERBOARD (5 s) -> podium`. The question ends early if every player has answered.

## What we added
- Projector route (`/projector/[code]`) for auditorium display
- Reconnect snapshots (remaining time, shuffled options, score, rank)
- Custom question sets via `POST /api/question-sets`
- 50-player load simulation (`simulation/`)
- Docker Compose deployment with health checks

## How to run it
Requires Docker with the Compose plugin. No login is needed: open the app, enter a name and college, and join room `APT892`.

```bash
git clone https://github.com/DwizSharma/arena-x-multiplayer.git
cd arena-x-multiplayer
cp .env.example .env        # then set DATABASE_PASSWORD in .env
docker compose up --build
```

Open http://localhost:3000, join room `APT892`, and press **Start match** from a connected player. Open extra browser tabs or incognito windows to act as more players, and `/projector/APT892` for the big-screen view.

Run services locally instead (Java 21, Maven, Node 20+):

```bash
docker compose up -d postgres redis
set -a; source .env; set +a
cd backend && mvn spring-boot:run          # terminal 1
cd frontend && npm install && npm run dev  # terminal 2
```

50-player simulation (backend running, room not yet started):

```bash
cd simulation && npm install && node simulate_50_players.js
```

Custom question set: `POST /api/question-sets` with a title, host ID and questions (each needs at least two options and a matching `correctOptionId`). Start a match by sending `{"questionSetId":"<id>"}` to `/app/room/{code}/start`.

Run a single backend instance only; active matches are cleared on restart.

Live URL: TODO (add once deployed)

### Troubleshooting
- **`set DATABASE_PASSWORD in .env`:** create the env file with `cp .env.example .env` and set a password.
- **Changed the password after the first run:** Postgres keeps the old one in its volume. Reset with `docker compose down -v`.
- **`failed to resolve reference` / DNS timeouts when pulling images:** set a reliable DNS server (for example 8.8.8.8 and 1.1.1.1) for the host or in `/etc/docker/daemon.json`, restart Docker, and re-run.
- **`buildx isn't installed` warning:** harmless. Install `docker-buildx` or run with `COMPOSE_BAKE=false`.
- **Port already in use (3000, 8080, 5432, 6379):** stop the other service using it.

## Tools and AI used
- Java 21, Spring Boot, Spring Data JPA, STOMP/WebSocket, PostgreSQL 16, Redis 7
- Next.js, React, Tailwind CSS, framer-motion, @stomp/stompjs
- Docker / Docker Compose
- AI coding tools: OpenAI Codex was used as a coding assistant during implementation and documentation. Players do not interact with AI; questions and scoring are deterministic.
- The app has no AI features, and players do not talk to an AI. Questions and scoring are deterministic.

## Who it is for
College quiz hosts and participants running live events, from classrooms to auditorium contests. Players come back for the college leaderboard and points across matches, and hosts for fast set-up with custom question sets.
