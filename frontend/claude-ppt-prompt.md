# Claude prompt: ArenaX project presentation

Create a polished, presentation-ready PowerPoint deck about the ArenaX Multiplayer project in this repository. Use the repository as the factual source; the audience is hackathon judges, faculty, and students. The deck should explain the problem, the live quiz experience, what we built, how fairness and scaling work, and what remains to do. Make it clear and persuasive without claiming features or results the code does not support.

## Before creating the slides

Read the project README and inspect the frontend, backend, Docker Compose configuration, and `simulation/simulate_50_players.js`. The README describes the implemented project. `AptiQuiz_PRD.docx` is a requirements/design document: distinguish its proposed or aspirational requirements from implemented features. If you cannot verify a claim in the code or README, omit it or mark it as a proposal. Do not invent benchmark results, adoption, user quotes, deployment URLs, team names, or competition results.

## Presentation requirements

- Create a 10-slide deck, 16:9 widescreen, suitable for a 5–7 minute presentation.
- Use the name **ArenaX Multiplayer** and describe it as a live, server-authoritative aptitude and logic quiz arena for **Problem Statement 3: AptiQuiz**.
- Keep slide text concise: one clear takeaway per slide, short bullets, readable type, and useful visual hierarchy. Add brief speaker notes with a natural narration for each slide.
- Use a modern, energetic collegiate esports visual style consistent with the app: deep navy/purple backgrounds, bright lime accents, clean scoreboard motifs, restrained neon, and high contrast. Avoid visual clutter and generic stock-photo collages.
- Prefer diagrams, product-flow illustrations, and editable charts/shapes over decorative imagery. Use genuine repository screenshots if available; otherwise create clean mockups clearly representing the actual UI. Do not portray mockups as screenshots.
- Use only details supported by the repository. Keep the distinction between completed and planned work visible.

## Suggested slide sequence

1. **Title** — ArenaX Multiplayer; “Aptitude, with a crowd”; identify AptiQuiz / Problem Statement 3. Leave team name and statement number as editable placeholders; README has no team statement number.
2. **The problem** — Solo aptitude practice can feel dull and lacks the pressure and shared energy of a live contest. Frame this as the product motivation, not as a measured research finding.
3. **The experience** — Show the actual journey: host creates a private room and shares a code; students join on their phones or laptops; timed questions, reveal, live leaderboard, final stats; projector view for the room.
4. **What players can do** — Four implemented modes (Classic, Meme Rush, Quant Sprint, Logic Lab), lobby, timed multiple-choice questions, live standings, answer reveals, score-free inter-round polls, personal results and topic analytics. Do not imply every mode has a custom-built question bank unless verified.
5. **What hosts can do** — Authenticated host console, private room creation and start control, reusable question-set library (create/edit/reorder/duplicate/archive/restore), match analytics and CSV export.
6. **Fair play by design** — Server-owned clock and scoring; first accepted answer per player/question; correct answer withheld until reveal; per-player option shuffle; reconnect snapshots. Explain that these are software safeguards, not a claim of comprehensive anti-cheat.
7. **How it works** — Architecture diagram: Next.js/React frontend ↔ Spring Boot/Java WebSocket (STOMP) game service; PostgreSQL for durable question sets/results/college points; Redis for shared room/session snapshots and coordination; RabbitMQ to relay STOMP events across backend replicas; HAProxy gateway; Docker Compose deployment. Label the server as authority for game state and scoring.
8. **Results and community** — Per-player match results and topic analytics, player and college leagues with weekly/monthly/all-time periods, projector leaderboard and answer distribution. Note player standings use generated session IDs, not permanent student accounts.
9. **Scale and readiness** — The repository includes a 50-player WebSocket simulation script and Docker health checks; multiple backend replicas can be run behind HAProxy. State that the script exists, but do not claim a passing capacity benchmark, latency, or production readiness unless verified results are present.
10. **Status and next step** — Summarize the working product and identify public deployment as the outstanding README item. End with a demo invitation or “Play fair. Play again.” Do not add a made-up live URL.

## Accuracy checklist

- This is a responsive web app, not native mobile apps.
- Players do not interact with AI; questions and scoring are deterministic. AI coding assistance is not an AI feature of the product.
- The README says wrong answers score zero and faster correct answers can earn more; avoid describing unimplemented scoring formulas as verified.
- Do not claim actual measured 50-player performance, user growth, institution adoption, or a public deployment.
- Separate implemented work from future work and label all placeholders.

Deliver the finished editable `.pptx` file. If you cannot create a PowerPoint file directly, provide the complete slide-by-slide content, visual direction, and speaker notes in a format that can be pasted into PowerPoint, and say clearly that it is not a `.pptx` file.
