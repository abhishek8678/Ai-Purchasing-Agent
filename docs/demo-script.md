# ProcureAI — 3–4 minute demo script

Before recording: open the deployed URL once so the free Render instance has woken up (the first request can take about a minute).
Have these tabs ready: **Overview**, **GitHub repo**, `docs/architecture.md`.

---

## 0:00–0:30 · What it is (Overview page)

> "This is ProcureAI, an AI purchasing agent for a retail / quick-commerce buyer. A replenishment system recommends a
> purchase. The agent investigates whether that recommendation is right: inventory, demand forecast, open POs, supplier
> MOQ, pack size, lead time, budget and storage. Then it recommends accept, modify, reject or investigate.
> A human approves, and only then does the system create and verify the purchase order."

Point at the **Review guide**: "The workflow has four steps: investigate, approve, execute and verify, audit."

## 0:30–1:45 · Scenario 1 live (click "Scenario 1" card → Start investigation)

> "The system recommends buying 800 wireless mice. I start the investigation. Gemini is given eight read-only
> tools, and **it decides which ones to call**. Java only executes them."

While it runs (~15 s), then on the **timeline**:
> "In round one Gemini requested seven evidence tools. In round two it checked purchasing constraints for several
> alternative quantities on its own. Then it proposed **MODIFY to 300**. The reasoning: net requirement 275, rounded
> up to the supplier's 50-unit pack size, from the cheaper supplier. It also flags the 50 unconfirmed units on the open PO as a risk."

> "Java doesn't trust that answer. It independently validates that every evidence reference came from this run and
> that there is a passing constraint check for exactly this supplier and quantity. Otherwise the run is
> sent back for review."

## 1:45–2:30 · Human approval + execution (Approvals page)

> "Nothing is purchased automatically. Here a buyer reviews and approves." → **Approve plan**
> "Approving only records a decision. Execution is a separate action." → **Execute purchase**
> "On execution, Java re-collects the evidence. If anything changed since approval, execution is blocked. Then it creates
> one PO with an idempotency key and reads it back from the database to verify it."
> Click **Execute** again if visible, or show **Purchase Orders**: "Retrying returns the same PO, never a duplicate."

## 2:30–3:15 · Other scenarios + audit (Overview → Scenario 4, Activity page)

> "The same agent handles the other assignment scenarios: a supplier shortfall, a demand spike, and a constraint
> block. For example, in scenario 4, 300 monitors exceed both budget and storage, and the agent **rejects** instead of blindly executing."
> On **Activity**: "Every model round, tool call, result and validation is persisted. That includes which Gemini model
> answered. If the free-tier quota runs out, the run automatically continues on a fallback model."

## 3:15–3:45 · Architecture + close (architecture.md / GitHub)

> "Spring Boot and Java 17 backend, React frontend, Gemini with manual function calling in AUTO mode, H2 mock data.
> The key design decision: **the model decides, Java enforces**. Gemini chooses tools and proposes a decision. Java owns
> validation, hard constraints, human approval, idempotent execution and read-back. 141 backend tests cover the
> deterministic safeguards. The evaluation doc records the live runs honestly, including where the model's judgement was debatable."

**Limitations to mention if asked:** no authentication (the approver name is entered by hand), POs are internal and not sent to
suppliers, in-memory database, free-tier Gemini quota.
