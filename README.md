# ProcureAI — AI Purchasing Agent

ProcureAI is a full-stack purchasing agent for a retail / quick-commerce buyer. Given a purchasing situation
(for example "the system recommends buying 800 units"), a Gemini model **chooses which evidence to gather**
through read-only tools. Java executes those tools and returns the results, then **independently validates**
the model's proposed decision (ACCEPT / MODIFY / REJECT / INVESTIGATE). A human approves it, and only then can a
separate execution step **revalidate current evidence, create one internal PO idempotently and read it back**.

- Architecture and feedback loop: [`docs/architecture.md`](docs/architecture.md)
- Scenarios and evaluation approach: [`docs/evaluation.md`](docs/evaluation.md)
- Data model: [`docs/database-design.md`](docs/database-design.md)

## Stack

| Layer | Technology |
| --- | --- |
| Backend | Java 17, Spring Boot 3.4, Spring Data JPA / Hibernate, Bean Validation, H2 (PostgreSQL mode) |
| AI | Google GenAI Java SDK 2.1, manual function calling, `FunctionCallingConfig` mode `AUTO` |
| Frontend | React, Vite, TypeScript, Tailwind CSS, Lucide icons, central API client |
| Tests | JUnit 5, Mockito, Spring Boot Test, Vitest |

It is a modular monolith: one Spring Boot API and one React SPA. Vite proxies `/api` to port 8080.

## Run locally

Requirements: Java 17, Maven 3.9+, Node.js 22+.

1. **Gemini key** (needed only for live investigations). Choose one option:
   - copy `.env.example` to `.env` in the repo root and set `GEMINI_API_KEY` (the file is gitignored and loaded automatically), or
   - set the `GEMINI_API_KEY` environment variable in the shell that starts the backend.

   Without a key, the app still runs and reports a clear configuration error when you start an investigation.
2. **Backend** (from `backend/`):
   ```powershell
   mvn "-Dmaven.repo.local=.m2repo" spring-boot:run
   ```
   This seeds the demo data on startup (in-memory H2, so it is reset on every restart). Health check: `GET http://localhost:8080/api/health`.
3. **Frontend** (from `frontend/`):
   ```powershell
   npm install
   npm run dev
   ```
   Open http://localhost:5173.

Optional local profile (`-Dspring-boot.run.profiles=local`): enables the H2 console and detailed actuator health.

## Demo walkthrough

1. **AI Purchasing Agent** page → choose an *Assignment scenario preset* (1–4) → **Start investigation**.
   A preset only fills in the form; Gemini still decides which tools to call.
2. Inspect the recommendation and the **investigation timeline**. It shows each tool Gemini selected, its arguments,
   the Java result, the evidence references and the final validated decision.
3. **Approvals** → review an ACCEPT/MODIFY run → choose quantity and supplier offer → **Approve plan**.
   Approving does not create a PO.
4. **Execute purchase** → Java revalidates the evidence fingerprint and hard constraints, creates one internal
   `APPROVED` PO with an approval-derived idempotency key, then reads it back and verifies it. Clicking again returns
   the same PO and never creates a duplicate.
5. **Purchase Orders** and **Activity** show the persisted PO and the full audit trail.

## Seeded scenarios (mock data)

| # | Situation | Product · warehouse | Key evidence |
| --- | --- | --- | --- |
| 1 | Review an 800-unit recommendation | WM-001 · DEL-01 | 260 on hand / 45 reserved; ~20/day forecast; open PO 300 ordered / 250 confirmed / 50 received; unconfirmed draft PO; 2 suppliers (MOQ, pack, lead time, price); ₹5L budget; 120 m³ storage |
| 2 | Supplier can only deliver 250 of a 500-unit PO | KB-002 · BLR-02 | Apex PO 500 ordered / 250 confirmed; 3 supplier offers incl. faster MetroTech; ₹4.5L budget |
| 3 | Demand rose above the normal plan | HS-003 · MUM-03 | Older run ~12/day, superseded by today's demand-sensing run ~30/day; existing 200-unit confirmed PO |
| 4 | Constraints block the recommended purchase | MN-004 · HYD-04 | 300 monitors exceed budget (~130 affordable) and free storage (~150 fit) |

The data describes situations only. It never encodes the expected AI decision.

## Safety model (what Gemini can and cannot do)

- Gemini receives **only read-only tools**: inventory, forecast, open POs, supplier offers, budget, storage,
  requirement calculation and constraint evaluation. There is no write tool.
- Gemini is not forced to call every tool, or to call them in a fixed order. Java limits rounds, total tool executions and repeated identical calls.
- Java rejects ACCEPT/MODIFY proposals that cite evidence not returned in the **current run**, reference supplier offers it never
  observed, or lack a **passing constraint evaluation from this run for exactly the proposed offer and quantity**
  (MOQ, pack size, budget, storage, supplier status, freshness). Rejected proposals are kept in the audit trail for review.
- Execution requires a recorded human approval and passes a fresh evidence check. It is idempotent and verified by read-back.
- Audit trail: every model request, tool selection, execution, result and decision is persisted as an `AgentEvent`.
  Hidden model reasoning is never requested or stored. Failure events store only sanitized diagnostics (no keys, headers, payloads or stack traces).

**Known limitations:** approver identity is client-supplied (no authentication), POs are internal records and are not
sent to suppliers, and the database is in-memory. This is a local demo, not a production purchasing system.

## Deploy (single Docker image, Render free tier)

The root `Dockerfile` builds the React app, bundles it into the Spring Boot jar and serves both on one port
(`PORT`, default 8080). `render.yaml` is a ready-made Render Blueprint.

1. On https://render.com choose **New → Blueprint**, connect this GitHub repository and pick `main`.
2. When prompted, enter `GEMINI_API_KEY`. It is stored as a Render secret and never committed.
3. Wait for the build to finish (about 5–8 minutes the first time), then open the service URL. The health check is `/api/health`.

Notes:
- The free instance sleeps when idle, and the first request after that takes about a minute. The H2 database is in memory, so every
  restart reseeds clean demo data. That is convenient for demos, but nothing persists.
- The app has **no authentication**. Anyone with the URL can run investigations (which use your Gemini quota) and
  approve internal demo POs. Share the URL only with evaluators.
- Free Gemini keys allow 20 requests per model per day (about 6 investigations). For a live demo, enable billing on the key or set
  `GEMINI_MODEL` to another model that still has quota.

Live AI results and evaluation: see [`docs/evaluation.md`](docs/evaluation.md).

## Tests

```powershell
cd backend;  mvn "-Dmaven.repo.local=.m2repo" test
cd frontend; npm test; npm run build
```

Latest local run (2026-11-12): backend **140 tests, 0 failures, 0 errors, 1 skipped**. The skipped test is the opt-in
live Gemini test, enabled by setting the key. Frontend: 10 tests passed and the production build succeeded.
On some Windows setups, Surefire's forked JVM fails with "Boot Manifest-JAR contains absolute paths"; if so, add `-DforkCount=0`.
