# ProcureAI

ProcureAI is a full-stack purchasing decision and execution system for retail and quick-commerce purchasing teams. It is being built milestone by milestone against the supplied AI Buyer Agent assignment.

## Assignment requirements checklist

| Assignment requirement | Planned component / milestone |
| --- | --- |
| Investigate inventory, demand, open POs, supplier terms, budget, and storage for the 800-unit recommendation | Scenario 1 business tools, data, and agent (Milestones 2–5) |
| Accept, modify, reject, or investigate further, with evidence | Dynamic agent decision and evidence validation (Milestones 4–5) |
| Approval before PO actions, safety, and duplicate protection | Human approval and execution services (Milestone 5) |
| Read back actual PO, validate outcomes, and recover from mismatch/failure | PO verification and execution recovery (Milestone 5) |
| Full-stack buyer UI with actual investigation activity | React dashboard (Milestone 8) |
| Evaluation scenarios covering decision quality, constraints, actions, validation, and failures | Tests and evaluation (Milestone 9) |
| Source, setup docs, architecture, approach, test/evaluation details, mock data, feedback loop, demo, and `.env.example` | Documentation and submission (Milestone 9) |
| Public GitHub repository and intact commit history | Repository and delivery (Milestone 10; this workspace currently has no `.git`) |
| Optional extension to supplier shortfall, demand change, or other buyer problems | Consider after Scenario 1 is complete |

The assignment permits mock APIs, databases, and datasets, and requires at least one scenario to work end-to-end. Scenario 1 is the priority. The AI should choose relevant investigation tools dynamically; deterministic code will enforce calculations, hard constraints, authorization, execution, and validation without preselecting the model's recommendation.

## Milestone 1: foundations

### Prerequisites

- Java 17+
- Maven 3.9+
- Node.js 22+ and npm
- PostgreSQL is planned for later milestones. H2 is used for local foundation work.

### Run

Backend (from `backend/`):

```powershell
mvn -Dmaven.repo.local=.m2repo spring-boot:run
```

Frontend (from `frontend/`):

```powershell
npm install
npm run dev
```

Run the backend health test from `backend/` with `mvn -Dmaven.repo.local=.m2repo test`.

Open the Vite URL shown in the terminal. The page calls `GET /api/health` on the Vite origin, and the Vite proxy forwards that request to `http://localhost:8080`. The page then displays the backend response. The API also exposes `GET /actuator/health`.

## Implemented milestones

1. Project setup and frontend/backend foundations
2. Database configuration, entities, and mock business data
3. Business services and independent tools
4. Gemini integration and dynamic tool-calling loop
5. Human approval, safe internal PO execution, idempotency, and persisted PO verification

Each milestone stops at its verified scope and waits for review before additional work.

## Milestone 5: approval and safe execution

The AI recommendation remains immutable. A human must explicitly approve its actionable quantity and a supplier offer, then separately request execution. The demo accepts a client-supplied `approverIdentity` as an audit label; it is **not authentication or authorization**. Production use requires authenticated identity and permission checks.

- `POST /api/agent-runs/{runId}/approve` records an approval only; it does not create a PO.
- `POST /api/agent-runs/{runId}/reject` records a reasoned rejection.
- `GET /api/approvals/{approvalId}` reads the approval state and has no execution side effect.
- `POST /api/approvals/{approvalId}/execute` performs current-evidence revalidation, creates an internal `APPROVED` PO, and verifies the persisted header and line. It does not submit the PO to a supplier.

An approval binds an evidence snapshot and fingerprint. If evidence or hard constraints change before execution, the attempt is blocked and requires a new investigation and approval. The PO idempotency key is derived from the approval ID; retries read the existing record before any creation attempt.
