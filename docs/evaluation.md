# Evaluation approach

The goal is to check that the *system* makes, executes and validates purchasing decisions safely, whatever the model says.
We therefore evaluate two layers separately:

1. **Deterministic layer (automated, repeatable).** Evidence calculations, constraint checks, recommendation validation,
   approval, revalidation, idempotent execution and read-back are covered by JUnit tests with a scripted model gateway.
2. **Model layer (live, observed).** For each scenario a live Gemini run is judged against the checklist below, using
   the persisted `AgentEvent` trail as the source of truth. We do not assert that the model always picks one exact quantity.

## Checklist used for every scenario

| Question (from the assignment) | How it is checked |
| --- | --- |
| Was the decision appropriate? | The decision and quantity fall inside the feasible range implied by the evidence: requirement vs. MOQ, pack, budget and storage. |
| Did the agent obtain the necessary information? | `TOOL_SELECTED` / `TOOL_EXECUTION_COMPLETED` events: which tools ran, in what order, and how many rounds. |
| Did it respect constraints? | `evaluate_purchase_constraints` results, plus Java's validator (`AgentRecommendationValidator`). |
| Did it take the appropriate action? | No PO without human approval; INVESTIGATE / REJECT cannot be approved for execution. |
| Did it validate the result? | Execution re-reads the persisted PO (`PurchaseOrderReadBackVerifier`) and compares header, line, quantity, price and supplier. |
| What if the action does not work? | Changed evidence → execution `BLOCKED`, so a new run is required. A retry → the same PO is returned through the idempotency key. Model / transport failure → `RUN_FAILED` with sanitized cause chain. |

## Scenarios

| Scenario | Data (seeded) | What a good agent should notice | Automated coverage | Live Gemini result |
| --- | --- | --- | --- | --- |
| 1. 800-unit recommendation | WM-001 · DEL-01 | Net requirement (~275) is far below 800; only confirmed PO units count; MOQ / pack rounding; budget and storage are fine → MODIFY expected to be reasonable | Evidence integration, agent loop, validator, approval, execution, idempotency, read-back | MODIFY → 300 (see below) |
| 2. Supplier shortfall (500 → 250) | KB-002 · BLR-02 | Only 250 confirmed units count as supply; check inventory and other suppliers' lead times; source the gap or escalate | `ScenarioDataSeederTest` (confirmed vs. unconfirmed supply, 3 offers) | ACCEPT 250 (see below) |
| 3. Demand increase | HS-003 · MUM-03 | The newest forecast run (~30/day) supersedes the normal plan (~12/day); stock plus the existing PO no longer covers demand → increase the plan | `ScenarioDataSeederTest` (newer run selected, 900 vs 360 units), revalidation tests | MODIFY → 650 (see below) |
| 4. Constraint block | MN-004 · HYD-04 | 300 units fail budget and storage; propose a feasible smaller quantity or escalate instead of executing | `ScenarioDataSeederTest` (300 fails budget + storage, 120 passes), constraint and execution-block tests | REJECT (see below) |

## Recorded live Gemini runs (2026-11-12, `gemini-3.5-flash-lite`, local)

All tool choices below were made by the model. Java only executed and validated them.

| Scenario | Rounds | Tools chosen by Gemini | Decision | Java validation | Assessment |
| --- | --- | --- | --- | --- | --- |
| 1. WM-001 800 units | 3 | R1: 7 evidence tools in parallel. R2: `evaluate_purchase_constraints` for 800, 275 and 300 units | **MODIFY → 300** (Northstar) | Passed | Good: net requirement 275 rounded up to the 50-unit pack, cheapest supplier, and the 50 unconfirmed PO units flagged as a risk |
| 2. KB-002 shortfall | 3 | R1: evidence tools. R2: constraints for 250 units at Apex | **ACCEPT 250** (Apex) | Passed | Acceptable but debatable: it re-orders from the supplier that fell short, and itself flags that this is 23 units below the 273-unit requirement. A buyer might prefer MetroTech's 4-day lead time |
| 3. HS-003 demand rise | 3 | R1: evidence tools. R2: constraints for 650 units | **MODIFY 150 → 650** (Apex) | Passed | Good: uses the new 30/day forecast (900 units) instead of the superseded run, and rounds 640 up to a pack multiple |
| 4. MN-004 constraints | 3 | R1: evidence tools. R2: constraints for 300 units at both suppliers | **REJECT** | Passed | Safe: cites both the budget (₹34.5L vs ₹15L) and storage (18 m³ vs 9 m³) failures and asks whether to escalate. A stronger answer would also propose a feasible ~120-unit MODIFY |

**What the validator caught live.**
- An earlier Scenario 2 run returned ACCEPT without evaluating constraints. This led to a new rule: ACCEPT/MODIFY must cite a passing
  constraint evaluation from this run for exactly the selected offer and quantity.
- An earlier Scenario 1 run cited a supplier offer the model had never been shown a reference for. That was a tool-output bug, now fixed.

**Reliability findings (live).**
- `gemini-3.8-flash` returned 503 "high demand" or hung past 60 s, so the default model is now `gemini-3.5-flash`.
- The SDK's hidden retries (up to 5) all shared one 60 s call timeout. These retries are disabled, and the app now retries timeouts and 5xx up to 3 times, each attempt with its own 45 s limit.
- Gemini 3 models require the opaque `thoughtSignature` on function calls to be echoed back. The app now passes it through in memory without storing it.
- The free tier allows only **20 requests per model per day**, and one investigation uses about 3 requests. 429 errors are not retried.

## Honest gaps

- Only one live run per scenario has been recorded, and model decisions can vary between runs. Java's safeguards are what is guaranteed.
- Scenario 2 is an investigation and recommendation flow. There is no endpoint for suppliers to report shortfalls; the shortfall is
  seeded as a PO with `confirmedUnits < orderedUnits`, and the situation is passed to Gemini as the run instruction.
- Scenario 3 uses forecast runs. There is no separate actual-sales table.
- Approvals are not authenticated, and POs are internal records that are not sent to suppliers.
