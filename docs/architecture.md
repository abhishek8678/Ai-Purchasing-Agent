# ProcureAI architecture

```mermaid
flowchart LR
  UI[React frontend] --> API[Spring Boot REST API]
  API --> Agent[Agent loop - AgentExecutionService]
  Agent <--> Gemini[(Gemini - AUTO function calling)]
  Agent --> Tools[Read-only tool registry]
  Tools --> Services[Investigation services]
  Services --> Repos[JPA repositories]
  Repos --> DB[(H2 demo database)]
  Agent --> Validator[Recommendation validator]
  Agent --> Audit[AgentRun + AgentEvent audit]
  UI --> Approval[Human approval]
  Approval --> Execute[Explicit execute request]
  Execute --> Revalidate[Fresh evidence + constraint revalidation]
  Revalidate --> PO[Idempotent internal PO creation]
  PO --> ReadBack[Persisted PO read-back verification]
  PO --> DB
```

## Agent loop: who decides what

```mermaid
sequenceDiagram
  participant J as Java (AgentExecutionService)
  participant G as Gemini
  participant T as Tool registry
  J->>G: situation + 8 tool declarations (mode AUTO)
  loop until Gemini returns final JSON (max rounds / tool limits)
    G-->>J: functionCall(s) chosen by the model
    J->>T: execute requested read-only tool
    T-->>J: result + evidence references
    J->>G: functionResponse (same call id)
  end
  G-->>J: decision JSON (ACCEPT / MODIFY / REJECT / INVESTIGATE)
  J->>J: validate references, offers, hard constraints
  J-->>J: persist DECISION_PROPOSED or NEEDS_INPUT
```

- **Gemini decides** which tools to call, in what order, whether to ask follow-up questions, and the proposed decision.
- **Java decides** what is allowed: tool execution limits, validation of every evidence reference against the current run,
  hard business constraints, human approval, and execution safety. Java does not compute a decision before asking the model.

## Feedback loop after a decision

1. **Approval** binds the run's evidence snapshot fingerprint, quantity and supplier offer.
2. **Execution** recomputes the evidence. If anything material changed, or a constraint now fails, execution is `BLOCKED`
   and a new investigation is required.
3. **PO creation** uses an idempotency key derived from the approval, so retries return the existing PO.
4. **Read-back** reloads the PO from the database and verifies supplier, warehouse, status, line quantity, price and offer.
   A mismatch marks the execution as failed instead of reporting success.

## Failure diagnostics

Gemini transport and API errors are classified as AUTHENTICATION, RATE_LIMIT, TIMEOUT or API_FAILURE. They are stored in the
`RUN_FAILED` event with a sanitized cause chain: up to 4 levels of class name and redacted message, with no stack traces,
headers, payloads or keys. The SDK applies its timeout as OkHttp's whole-call timeout; when it expires OkHttp throws
`InterruptedIOException("timeout")`, which is classified as TIMEOUT.
