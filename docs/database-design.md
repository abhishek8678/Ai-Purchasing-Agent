# ProcureAI Database Design — Step 2.1

## 1. Business domain overview

ProcureAI reviews a proposed purchase against operational evidence for a particular product and fulfillment warehouse. Its first scenario is the review of an 800-unit recommendation for a Wireless Mouse at the Delhi Fulfillment Center. The recommendation is an input to investigate, not a fact the system should accept as correct.

The database therefore needs to represent four kinds of information:

1. **Master data:** products, warehouses, suppliers, and the supplier offers available for a product at a destination.
2. **Operational evidence:** inventory, time-bucketed demand forecasts, inbound purchase order lines, warehouse capacity, and replenishment policy.
3. **Purchasing controls:** budget periods and purchase order state, including supplier confirmation and buyer approval.
4. **Agent audit:** each investigation and the actual tool calls, observations, and final decision it produced.

The design is a small relational schema for a modular monolith. It uses normal foreign keys and portable scalar types, so H2 can be used locally and PostgreSQL can be adopted later without changing the business model.

### Candidate entity review

| Candidate | Recommendation | Purpose |
| --- | --- | --- |
| Product | Keep | Shared product identity and physical unit attributes used for storage estimates. |
| Warehouse | Keep | Fulfillment node against which stock, constraints, and orders are evaluated. |
| Supplier | Keep | Supplier identity and overall operating status. |
| Inventory | Keep | Current on-hand and reserved quantities for one product at one warehouse. |
| DemandForecast | Keep | Forecast quantities for future dates, preserving generated versions. |
| SupplierProduct | Keep, with warehouse scope | An offer for one supplier, product, and destination, including price and ordering terms. |
| PurchaseOrder | Keep | Supplier order header, status, approval, and idempotency identity. |
| PurchaseOrderItem | Keep | Quantities, confirmation, receipt, delivery date, and price snapshot for an ordered offer. |
| PurchasingBudget | Keep | Authorized amount and currency for a warehouse and date range. |
| StorageCapacity | Keep | Warehouse capacity and the committed volume snapshot used to determine remaining space. |
| ReplenishmentPolicy | Add | Holds safety stock by product and warehouse; this is a policy value, not a current stock count. |
| AgentRun / AgentEvent | Add | Makes investigations, real tool activity, evidence, and decisions auditable. |
| PurchasingApproval | Add | Stores one explicit human decision, the approved plan and evidence fingerprint, execution outcome, and PO link for an AgentRun. |

No separate `SupplierWarehouse` is proposed for the initial scope: the destination-specific terms live on `SupplierProduct`. This avoids splitting one offer across two joins while still allowing the same supplier and product to have different terms at different fulfillment nodes.

## 2. Recommended entities and attributes

Types below are Java/JPA types. `Long` primary keys use generated identity values for ordinary relational entities. Monetary values use `BigDecimal` and `DECIMAL(19,4)`. Quantities use `Long` and non-negative whole units. Dates that describe business days use `LocalDate`; event times use `LocalDateTime` stored consistently in UTC. All enums should be persisted as strings (`EnumType.STRING`), never ordinal numbers.

### Product

**Purpose:** A catalog item that may be stocked and purchased at multiple warehouses.

| Field | Type | Required | Notes / validation |
| --- | --- | --- | --- |
| id | Long | Yes | Primary key. |
| sku | String (80) | Yes | Stable internal SKU; trim and reject blank. |
| name | String (200) | Yes | Human-readable name; reject blank. |
| unitOfMeasure | String (12) | Yes | Unit in which quantities of this product are purchased and stocked (for example `EACH` for the Wireless Mouse). Store a normalized code and validate against supported units. |
| description | String (500), nullable | No | Optional catalog details. |
| unitVolumeM3 | BigDecimal, `DECIMAL(14,6)`, nullable | No | Cubic metres occupied by one sellable unit; if absent, storage feasibility is unknown and should be investigated. If present, must be greater than zero. |
| active | Boolean | Yes | Whether the product can be considered for new purchasing. |
| createdAt / updatedAt | LocalDateTime | Yes | Audit timestamps, UTC. |

**Constraints:** unique `sku`; index `active` only if active catalog queries become material. The nullable volume allows incomplete upstream catalog data to be represented and flagged instead of fabricated. Scenario 1 quantities are whole `EACH` units; adding weight-based or fractional units later may require decimal quantities and unit conversion rules.

### Warehouse

**Purpose:** A fulfillment node where stock is held and purchases are budgeted.

| Field | Type | Required | Notes / validation |
| --- | --- | --- | --- |
| id | Long | Yes | Primary key. |
| code | String (40) | Yes | Stable warehouse code. |
| name | String (160) | Yes | Display name, such as Delhi Fulfillment Center. |
| city | String (100) | Yes | Location used for buyer-facing context. |
| countryCode | String (2) | Yes | ISO 3166-1 alpha-2; use `IN` for the example. |
| active | Boolean | Yes | Inactive nodes cannot receive new orders. |
| createdAt / updatedAt | LocalDateTime | Yes | Audit timestamps, UTC. |

**Constraints:** unique `code`; index `(active, city)` only if needed for selection. A separate address entity is not justified by the assignment.

### Supplier

**Purpose:** A vendor that can provide products under one or more destination-specific offers.

| Field | Type | Required | Notes / validation |
| --- | --- | --- | --- |
| id | Long | Yes | Primary key. |
| code | String (40) | Yes | Stable internal supplier code. |
| name | String (180) | Yes | Display/legal trading name. |
| status | SupplierStatus enum | Yes | `ACTIVE`, `INACTIVE`. Offer availability remains on the offer because a supplier may be unavailable for only one product or node. |
| createdAt / updatedAt | LocalDateTime | Yes | Audit timestamps, UTC. |

**Constraints:** unique `code`.

### SupplierProduct

**Purpose:** The current purchasable offer for a product from a supplier to a particular warehouse. Its destination scope makes lead time and price meaningful when a supplier serves multiple nodes.

| Field | Type | Required | Notes / validation |
| --- | --- | --- | --- |
| id | Long | Yes | Primary key. |
| supplierId | Supplier (ManyToOne) | Yes | Supplier providing the offer. |
| productId | Product (ManyToOne) | Yes | Product being offered. |
| warehouseId | Warehouse (ManyToOne) | Yes | Destination covered by these terms. |
| supplierSku | String (100), nullable | No | Vendor's catalog identifier. |
| minimumOrderQuantity | Long | Yes | Positive whole units. |
| packSize | Long | Yes | Positive whole units per order multiple. |
| leadTimeDays | Integer | Yes | Non-negative calendar days from order placement to expected delivery. |
| unitPrice | BigDecimal, `DECIMAL(19,4)` | Yes | Non-negative price per sellable unit. |
| currencyCode | String (3) | Yes | ISO 4217 code; for the initial scenario use INR. |
| status | SupplierOfferStatus enum | Yes | `AVAILABLE`, `TEMPORARILY_UNAVAILABLE`, `INACTIVE`. |
| createdAt / updatedAt | LocalDateTime | Yes | Audit timestamps, UTC. |

**Constraints:** unique `(supplierId, productId, warehouseId)` for one current offer; indexes on `(productId, warehouseId, status)` for alternatives and `(supplierId, status)` for supplier lookups. Do not delete an offer referenced by a purchase order; mark it inactive. Service validation checks that a proposed quantity meets MOQ and is a multiple of pack size. Keeping only current terms is adequate for this short assignment; each PO line snapshots the agreed price so later offer changes do not rewrite history.

### Inventory

**Purpose:** The latest observed stock position for one product at one warehouse.

| Field | Type | Required | Notes / validation |
| --- | --- | --- | --- |
| id | Long | Yes | Primary key. |
| productId | Product (ManyToOne) | Yes | Stocked product. |
| warehouseId | Warehouse (ManyToOne) | Yes | Stock location. |
| onHandUnits | Long | Yes | Non-negative physical units on the latest count. |
| reservedUnits | Long | Yes | Non-negative units allocated to orders or other demand. |
| lastCountedAt | LocalDateTime | Yes | Timestamp for the source stock snapshot, UTC. |
| version | Long | Yes | JPA optimistic-lock version to prevent silent concurrent overwrites. |
| createdAt / updatedAt | LocalDateTime | Yes | Audit timestamps, UTC. |

**Constraints:** unique `(productId, warehouseId)`; index `(warehouseId, productId)`. Available units are derived as `onHandUnits - reservedUnits`; do not persist a second copy. If reserved exceeds on-hand, the data should be surfaced as inconsistent for investigation, not silently clamped.

### ReplenishmentPolicy (added)

**Purpose:** Stores the buyer-approved replenishment settings for one product at one warehouse. Safety stock belongs here rather than being mixed into physical inventory.

| Field | Type | Required | Notes / validation |
| --- | --- | --- | --- |
| id | Long | Yes | Primary key. |
| productId | Product (ManyToOne) | Yes | Product policy applies to. |
| warehouseId | Warehouse (ManyToOne) | Yes | Node policy applies to. |
| safetyStockUnits | Long | Yes | Non-negative buffer quantity. |
| reviewPeriodDays | Integer | Yes | Positive planning/review horizon used with supplier lead time. |
| createdAt / updatedAt | LocalDateTime | Yes | Audit timestamps, UTC. |

**Constraints:** unique `(productId, warehouseId)`; index `(warehouseId, productId)`. Keeping a modest review period makes the demand coverage calculation explicit rather than silently assuming the supplier lead time is the whole planning horizon.

### DemandForecast

**Purpose:** Stores forecast demand in daily buckets for a product and warehouse, retaining distinct forecast generations so a changed forecast can be compared with the earlier evidence.

| Field | Type | Required | Notes / validation |
| --- | --- | --- | --- |
| id | Long | Yes | Primary key. |
| productId | Product (ManyToOne) | Yes | Forecasted product. |
| warehouseId | Warehouse (ManyToOne) | Yes | Demand location. |
| forecastDate | LocalDate | Yes | Business date the quantity covers. |
| expectedUnits | Long | Yes | Non-negative expected demand for that date. |
| forecastRunId | UUID | Yes | Identifies rows emitted together by one forecast generation. |
| generatedAt | LocalDateTime | Yes | When the forecast was produced, UTC. |
| modelVersion | String (80), nullable | No | Optional source/version label for reproducibility. |

**Constraints:** unique `(forecastRunId, productId, warehouseId, forecastDate)`; index `(productId, warehouseId, forecastDate, generatedAt)`. The agent can sum the relevant daily forecasts across lead time plus review horizon. It should compare runs consistently (for example, latest complete run) and treat a missing horizon as missing evidence.

### PurchasingBudget

**Purpose:** Records the authorized spend for a warehouse over a defined business period.

| Field | Type | Required | Notes / validation |
| --- | --- | --- | --- |
| id | Long | Yes | Primary key. |
| warehouseId | Warehouse (ManyToOne) | Yes | Budget owner. |
| periodStart / periodEnd | LocalDate | Yes | Inclusive budget dates; start must not be after end. |
| budgetAmount | BigDecimal, `DECIMAL(19,4)` | Yes | Allocated/authorized purchasing amount; non-negative. |
| currencyCode | String (3) | Yes | ISO 4217 code. |
| createdAt / updatedAt | LocalDateTime | Yes | Audit timestamps, UTC. |

**Constraints:** unique `(warehouseId, periodStart, periodEnd)`; index `(warehouseId, periodStart, periodEnd)`. Overlapping periods for one warehouse should be rejected by service validation because a simple portable unique constraint cannot express arbitrary date-range overlap. `budgetAmount` is the allocated amount; do not store mutable duplicate spent, committed, or available amounts. Derive those using the demo policy in section 5. A multi-currency calculation needs exchange-rate data and is outside this initial scope.

### StorageCapacity

**Purpose:** Stores the warehouse's latest capacity and the amount already occupied or reserved, based on the warehouse management system snapshot.

| Field | Type | Required | Notes / validation |
| --- | --- | --- | --- |
| id | Long | Yes | Primary key. |
| warehouseId | Warehouse (OneToOne) | Yes | One current capacity snapshot per warehouse in this simple version. |
| totalVolumeM3 | BigDecimal, `DECIMAL(14,6)` | Yes | Positive total cubic-metre capacity. |
| allocatedVolumeM3 | BigDecimal, `DECIMAL(14,6)` | Yes | Physical occupancy plus capacity already reserved for inbound goods, as supplied by the WMS; non-negative. |
| measuredAt | LocalDateTime | Yes | Snapshot timestamp, UTC. |
| version | Long | Yes | Optimistic-lock version. |
| createdAt / updatedAt | LocalDateTime | Yes | Audit timestamps, UTC. |

**Constraints:** unique `warehouseId`; index on `warehouseId`. Available volume is derived as `totalVolumeM3 - allocatedVolumeM3`. The WMS definition must include already booked inbound orders to avoid counting inbound stock twice. The proposed order's estimated volume is `quantity × Product.unitVolumeM3`. If the unit volume or WMS snapshot is missing/stale, the agent should request more information.

### PurchaseOrder

**Purpose:** Represents a supplier order header and its approval/execution state. A PO has one supplier and one destination warehouse; separate documents are used for different suppliers or delivery nodes.

| Field | Type | Required | Notes / validation |
| --- | --- | --- | --- |
| id | Long | Yes | Primary key. |
| poNumber | String (40) | Yes | Human-readable unique order number. |
| supplierId | Supplier (ManyToOne) | Yes | Supplier receiving the order. |
| warehouseId | Warehouse (ManyToOne) | Yes | Delivery destination. |
| orderDate | LocalDate | Yes | Date the order was created/approved for business purposes. |
| status | PurchaseOrderStatus enum | Yes | `DRAFT`, `PENDING_APPROVAL`, `APPROVED`, `SUBMITTED`, `ACKNOWLEDGED`, `PARTIALLY_RECEIVED`, `RECEIVED`, `CANCELLED`, `FAILED`. The basic path is `DRAFT → PENDING_APPROVAL → APPROVED → SUBMITTED → ACKNOWLEDGED → PARTIALLY_RECEIVED → RECEIVED`; real orders may skip states that do not apply (for example, a fully received order need not pass through `PARTIALLY_RECEIVED`). `CANCELLED` is an intentional stop; `FAILED` records an unsuccessful execution or processing outcome. |
| currencyCode | String (3) | Yes | Currency shared by this order's item price snapshots. |
| idempotencyKey | String (100), nullable until execution | No | Unique key supplied for a create/modify request; prevents duplicate execution. |
| approvedBy | String (120), nullable | No | Buyer identity for the recorded approval; not authentication by itself. |
| approvedAt | LocalDateTime, nullable | No | Approval timestamp, UTC. |
| createdAt / updatedAt | LocalDateTime | Yes | Audit timestamps, UTC. |

**Constraints:** unique `poNumber`; unique `idempotencyKey` when present; indexes `(warehouseId, status, orderDate)` and `(supplierId, status)`. The lifecycle above is a guide, not a requirement that every order pass through each state. State transitions and rules requiring approval belong in the service/policy layer, not in the enum alone.

### PurchaseOrderItem

**Purpose:** Represents one product offer and its quantities on a PO. Quantities let the agent distinguish ordered, supplier-confirmed, and actually received stock.

| Field | Type | Required | Notes / validation |
| --- | --- | --- | --- |
| id | Long | Yes | Primary key. |
| purchaseOrderId | PurchaseOrder (ManyToOne) | Yes | Owning order. |
| supplierProductId | SupplierProduct (ManyToOne) | Yes | Offer selected for this product, supplier, and destination. |
| orderedUnits | Long | Yes | Positive quantity requested on the PO. |
| confirmedUnits | Long, nullable | No | Total quantity the supplier has confirmed for this line; null means no response yet. When present, must be between zero and `orderedUnits`. |
| receivedUnits | Long | Yes | Cumulative physical quantity received/read back; starts at zero and cannot be negative or exceed `orderedUnits`. It is never future incoming stock. |
| unitPriceAtOrder | BigDecimal, `DECIMAL(19,4)` | Yes | Price snapshot at order time; non-negative and in PO currency. |
| expectedDeliveryDate | LocalDate, nullable | No | Expected date for this line, especially useful when a supplier confirms a partial or delayed delivery. |
| createdAt / updatedAt | LocalDateTime | Yes | Audit timestamps, UTC. |

**Constraints:** unique `(purchaseOrderId, supplierProductId)`; index `(supplierProductId, expectedDeliveryDate)`. Validate that the offer's supplier and warehouse match the PO header at write time; this cross-row rule cannot be expressed by a basic foreign key alone. Do not store redundant outstanding quantities or line totals.

**Incoming-supply evidence policy:** Keep the three quantities distinct: `orderedUnits` is the request, `confirmedUnits` is the supplier's total accepted quantity, and `receivedUnits` is physical receipt to date. For an eligible PO line whose `expectedDeliveryDate` falls within the demand horizon, reliable confirmed outstanding supply is `max(confirmedUnits - receivedUnits, 0)` when `confirmedUnits` is present. Count it only for `ACKNOWLEDGED` or `PARTIALLY_RECEIVED` orders. Report unconfirmed potential separately as `max(orderedUnits - max(confirmedUnits or 0, receivedUnits), 0)` on `SUBMITTED`, `ACKNOWLEDGED`, or `PARTIALLY_RECEIVED` lines due within the horizon; do not treat it as reliable incoming stock. `DRAFT` and `PENDING_APPROVAL` orders are proposals, not supplier supply. `RECEIVED` lines add no future supply; if that status conflicts with outstanding quantities, flag the inconsistency for investigation. `CANCELLED` and `FAILED` orders contribute neither reliable incoming nor potential supply. Thus, received units are never added to future incoming, and the confirmed and unconfirmed portions of an outstanding line are not double-counted.

### AgentRun and AgentEvent (added)

**Purpose:** Preserve enough evidence to explain and evaluate an investigation without storing private model chain-of-thought.

`AgentRun` fields: `id UUID` primary key; `productId` and `warehouseId` foreign keys; `inputRecommendationUnits Long`; `status AgentRunStatus` (`RUNNING`, `COMPLETED`, `FAILED`, `NEEDS_INPUT`); nullable `decision PurchaseDecision` (`ACCEPT`, `MODIFY`, `REJECT`, `INVESTIGATE`); nullable `proposedUnits Long`; concise `decisionSummary String/TEXT`; `startedAt LocalDateTime`; nullable `completedAt LocalDateTime`; and created/updated timestamps. A run may end without a proposed quantity when evidence is insufficient.

`AgentEvent` fields: `id Long` primary key; `agentRunId` foreign key; `sequenceNumber Integer`; `eventType AgentEventType` (for example `TOOL_REQUESTED`, `TOOL_RESULT`, `DECISION_VALIDATED`, `APPROVAL_RECORDED`, `EXECUTION_RESULT`, `READBACK_VALIDATION`); nullable `toolName String(100)`; nullable `argumentsJson TEXT`; nullable `resultJson TEXT`; concise `summary TEXT`; `occurredAt LocalDateTime`. JSON-as-text is portable between H2 and PostgreSQL for this small project; validate JSON at the application boundary and redact secrets before persistence.

**Constraints:** unique `AgentRun.id` as UUID; unique `(agentRunId, sequenceNumber)`; indexes `(productId, warehouseId, startedAt)` on runs and `(agentRunId, occurredAt)` on events. Tool event rows are append-only from the application perspective. Store observations and structured decisions, not hidden chain-of-thought. Purchase order changes and final read-back checks should also be recorded as events.

### PurchasingApproval (Milestone 5)

**Purpose:** Preserve the human decision separately from the original AI recommendation and make execution repeatable and auditable. One approval record is allowed per `AgentRun`; a new investigation is required if the decision or execution plan needs to change.

Fields: `id UUID` primary key; unique one-to-one `agentRunId`; `approvalStatus ApprovalStatus` (`APPROVED`, `REJECTED`); `executionStatus ExecutionStatus` (`NOT_EXECUTED`, `IN_PROGRESS`, `BLOCKED`, `SUCCEEDED`, `VERIFICATION_FAILED`, `FAILED`); nullable `approvedQuantity Long` and `supplierProductId` for rejected decisions; `approverIdentity String(120)`; `decisionAt LocalDateTime`; nullable `rejectionReason String(1000)`; nullable `unitPriceAtApproval BigDecimal(19,4)` and `currencyCode String(3)`; unique nullable `idempotencyKey String(100)`; nullable unique one-to-one `purchaseOrderId`; `evidenceFingerprint String(64)` and `approvalEvidenceJson TEXT` for the exact evidence snapshot reviewed at approval; concise `executionSummary TEXT`, nullable `executionAt`, and created/updated timestamps.

Only a `COMPLETED` AgentRun with a persisted, valid `ACCEPT` or `MODIFY` recommendation can be approved. The explicitly approved quantity must equal the validated recommendation quantity. The approver explicitly names an offer; if the AI recommendation named one, it must match. `REJECT` and `INVESTIGATE` recommendations cannot be executed. A rejection requires a reason and approver identity, and has no purchasing plan or execution key.

The approver identity in this demo is supplied in the request and is only an audit label. It is not authentication and does not establish that the caller is authorized. A real identity provider and role-based authorization remain out of scope.

At approval time the service captures current evidence. Immediately before creating a PO, it reads a new snapshot and compares its canonical fingerprint with the approved evidence. Product/warehouse status, inventory, forecast, incoming POs, offers and their terms, budget, capacity, and baseline evidence are all included. Any changed section or non-passing hard constraint blocks execution and requires a new investigation and human approval; the approved quantity is never silently changed. Execution also uses a deterministic idempotency key derived from the approval UUID, a database uniqueness constraint, a pessimistic approval-row lock, and a serializable transaction.

PO header and item are written atomically with initial status `APPROVED`; supplier submission and acknowledgement are not claimed. The service reads the persisted header and line back by database ID and verifies PO number, supplier, warehouse, status, offer/product, quantity, price, currency, and idempotency key. A mismatch is stored as `VERIFICATION_FAILED` with any persisted PO link retained; retry checks that key and never creates a compensating PO.

**Constraints:** unique `agentRunId` and `idempotencyKey`; one approval decision per run; at most one approval refers to a PO. The event stream on the AgentRun records approval requested/granted/rejected, revalidation, execution, PO creation, and read-back results. `APPROVED` approval status alone does not execute a PO; execution is a separate explicit request.

## 3. Relationships and JPA direction

- `Inventory`, `DemandForecast`, `ReplenishmentPolicy`, `SupplierProduct`, `PurchasingBudget`, `StorageCapacity`, and `PurchaseOrder` each hold the foreign key to their parent rows using `@ManyToOne` or `@OneToOne`.
- `Product` and `Warehouse` each have many inventory rows across their combinations. The `(product, warehouse)` pair is unique in the inventory table.
- Product and warehouse have many forecast rows. Each forecast row belongs to exactly one product and one warehouse.
- Product, supplier, and warehouse connect through `SupplierProduct`. This is a real association entity because the relationship has its own terms and status, not just a bare many-to-many link.
- A warehouse has one current `StorageCapacity` row and many budget periods. A product/warehouse pair has one current replenishment policy.
- A purchase order belongs to one supplier and one warehouse, and has multiple order items. Each item points to the chosen supplier offer; that offer determines its product. The PO item snapshots price and quantities so later offer changes do not alter historical orders.
- An agent run points to the product and warehouse under review and has many ordered events. Events are owned by the run.
- A purchasing approval has a unique one-to-one relationship to its AgentRun and an optional one-to-one link to the single PO created for it. PO creation has no model/tool entry point; it is initiated only by the approval execution endpoint.

Prefer unidirectional relationships from child to parent in the JPA model. This keeps the object graph small and prevents recursive JSON serialization. If a parent needs its children, query a repository/service rather than eagerly loading a collection. Use `FetchType.LAZY` for `ManyToOne`; use lazy collections only if bidirectional collections are later justified. API responses should use DTOs rather than serializing entities.

### Mermaid ER diagram

```mermaid
erDiagram
    PRODUCT ||--o{ INVENTORY : stocked_as
    WAREHOUSE ||--o{ INVENTORY : stores
    PRODUCT ||--o{ DEMAND_FORECAST : forecast_for
    WAREHOUSE ||--o{ DEMAND_FORECAST : forecast_at
    PRODUCT ||--o{ REPLENISHMENT_POLICY : has_policy
    WAREHOUSE ||--o{ REPLENISHMENT_POLICY : policy_at
    SUPPLIER ||--o{ SUPPLIER_PRODUCT : offers
    PRODUCT ||--o{ SUPPLIER_PRODUCT : offered_product
    WAREHOUSE ||--o{ SUPPLIER_PRODUCT : delivered_to
    WAREHOUSE ||--o{ PURCHASING_BUDGET : budgeted_by
    WAREHOUSE ||--|| STORAGE_CAPACITY : capacity_snapshot
    SUPPLIER ||--o{ PURCHASE_ORDER : receives
    WAREHOUSE ||--o{ PURCHASE_ORDER : destination
    PURCHASE_ORDER ||--|{ PURCHASE_ORDER_ITEM : contains
    SUPPLIER_PRODUCT ||--o{ PURCHASE_ORDER_ITEM : selected_offer
    PRODUCT ||--o{ AGENT_RUN : investigated
    WAREHOUSE ||--o{ AGENT_RUN : investigated_at
    AGENT_RUN ||--o{ AGENT_EVENT : records
    AGENT_RUN ||--o| PURCHASING_APPROVAL : decided_by
    SUPPLIER_PRODUCT ||--o{ PURCHASING_APPROVAL : approved_offer
    PURCHASE_ORDER ||--o| PURCHASING_APPROVAL : execution_result

    PRODUCT {
        long id PK
        string sku UK
        string name
        string unitOfMeasure
        decimal unitVolumeM3
    }
    WAREHOUSE {
        long id PK
        string code UK
        string name
        string city
    }
    SUPPLIER {
        long id PK
        string code UK
        string name
        string status
    }
    INVENTORY {
        long id PK
        long productId FK
        long warehouseId FK
        long onHandUnits
        long reservedUnits
    }
    DEMAND_FORECAST {
        long id PK
        long productId FK
        long warehouseId FK
        date forecastDate
        long expectedUnits
        uuid forecastRunId
    }
    REPLENISHMENT_POLICY {
        long id PK
        long productId FK
        long warehouseId FK
        long safetyStockUnits
        int reviewPeriodDays
    }
    SUPPLIER_PRODUCT {
        long id PK
        long supplierId FK
        long productId FK
        long warehouseId FK
        long minimumOrderQuantity
        long packSize
        int leadTimeDays
        decimal unitPrice
        string currencyCode
    }
    PURCHASING_BUDGET {
        long id PK
        long warehouseId FK
        date periodStart
        date periodEnd
        decimal budgetAmount
        string currencyCode
    }
    STORAGE_CAPACITY {
        long id PK
        long warehouseId FK
        decimal totalVolumeM3
        decimal allocatedVolumeM3
        datetime measuredAt
    }
    PURCHASE_ORDER {
        long id PK
        string poNumber UK
        long supplierId FK
        long warehouseId FK
        date orderDate
        string status
        string idempotencyKey UK
    }
    PURCHASE_ORDER_ITEM {
        long id PK
        long purchaseOrderId FK
        long supplierProductId FK
        long orderedUnits
        long confirmedUnits
        long receivedUnits
        decimal unitPriceAtOrder
    }
    AGENT_RUN {
        uuid id PK
        long productId FK
        long warehouseId FK
        long inputRecommendationUnits
        string status
        string decision
    }
    AGENT_EVENT {
        long id PK
        uuid agentRunId FK
        int sequenceNumber
        string eventType
        string toolName
        text resultJson
    }
    PURCHASING_APPROVAL {
        uuid id PK
        uuid agentRunId FK UK
        string approvalStatus
        string executionStatus
        long approvedQuantity
        long supplierProductId FK
        string approverIdentity
        datetime decisionAt
        decimal unitPriceAtApproval
        string currencyCode
        string idempotencyKey UK
        long purchaseOrderId FK UK
        string evidenceFingerprint
        text approvalEvidenceJson
        text executionSummary
    }
```

## 4. Scenario 1 data requirements — Wireless Mouse at Delhi Fulfillment Center

The scenario should be representable with rows for a product, warehouse, supplier(s), one inventory snapshot, daily forecast rows, supplier offers, current policy, budget, capacity snapshot, and any open PO headers/items. The initial recommendation quantity (800) belongs to the investigation input (`AgentRun`), not to product or inventory master data.

Before judging that quantity, the agent needs:

- **Product and node identity:** Wireless Mouse SKU, active status, Delhi warehouse code, and the target product/warehouse pair.
- **Current inventory:** `onHandUnits`, `reservedUnits`, and `lastCountedAt`; available stock is derived as on-hand less reserved. The snapshot must include receipts recorded through its timestamp. If the snapshot predates a receipt, flag and reconcile the stale evidence rather than adding that PO receipt to on-hand a second time.
- **Demand:** a complete daily demand forecast for the period covering supplier lead time plus review period; `expectedUnits`, forecast run identity and generation time let the agent detect missing or stale forecasts.
- **Safety stock:** `safetyStockUnits` and `reviewPeriodDays` for that product at that node.
- **Incoming stock:** submitted, acknowledged, and partially received PO lines, with `orderedUnits`, `confirmedUnits`, `receivedUnits`, status, and expected delivery date. Only confirmed outstanding quantity on an acknowledged/partially received line due within the horizon counts as reliable incoming stock. Unconfirmed outstanding quantity is reported separately as uncertain potential supply; draft and pending-approval POs are proposals, while cancelled/failed orders contribute no supply. Received units are historical physical stock and never future incoming. The inventory snapshot incorporates receipts through its timestamp, so the same received or inbound quantity is not added twice.
- **Purchase unit:** `unitOfMeasure`; use `EACH` for the Wireless Mouse so the 800-unit recommendation and all stock/order quantities refer to the same purchasable unit.
- **Supplier terms:** available supplier offer(s) for the product and Delhi destination, including status, MOQ, pack size, lead time, unit price, and currency.
- **Budget:** applicable date period, allocated amount, currency, PO line prices and quantities within that period, plus PO status and receipts. The service reports spent, committed, and available amounts separately using the demo accounting policy in section 5; an amount is not simultaneously spent and committed.
- **Storage:** warehouse total and allocated/reserved volume from the WMS snapshot, snapshot time, and product volume per unit. Remaining capacity is derived.
- **Information quality:** whether each source is present, current enough, in a consistent currency/unit, and applicable to the selected product and warehouse.
- **Human decision:** a completed actionable AgentRun, the exact approved quantity and supplier offer, a human approver audit identity, and a separate explicit execution request. The approval endpoint itself creates no PO.

No sample values beyond the assignment's 800-unit recommendation are specified here. The database design does not decide whether 800 should be accepted.

## 5. Database constraints and mapping guidance

### Numeric precision and checks

- Store currency amounts as `DECIMAL(19,4)` / `BigDecimal`; do not use `float` or `double` for money.
- Store unit counts as `BIGINT` / `Long` and validate whole, non-negative quantities. The assignment's quantities are small, but `Long` leaves room for aggregated forecasts and multi-node totals.
- Store product volume and capacity in a single canonical unit (cubic metres) as `DECIMAL(14,6)`. Convert/validate at ingestion; do not mix cubic centimetres and metres in comparisons.
- Validate positive MOQ/pack size, non-negative lead time, price/budget/volume bounds, valid date ranges, receipt not exceeding order quantity, and valid enum values.
- Enforce natural keys and association keys with unique constraints listed above. Add indexes for the common filters (warehouse/product, product/warehouse/date, status/destination, forecast horizon, run/event history).
- Enforce supplier/warehouse consistency between PO headers and offer lines in the service layer, because a simple JPA relation cannot check matching values across different referenced rows.

### Initial demo purchasing-budget accounting policy

For a budget period, include purchase orders whose `orderDate` falls inside that period and whose currency matches the budget. Use `unitPriceAtOrder` for each line. This is a purchasing-control estimate, not an invoice ledger.

- **Allocated:** `PurchasingBudget.budgetAmount`, the authorized maximum.
- **Spent:** sum `receivedUnits × unitPriceAtOrder` for included PO lines, regardless of the order's current status. Receipt is the demo's proxy for purchasing spend; a cancelled or failed PO with some units already received still contributes that received portion.
- **Committed:** sum `(orderedUnits - receivedUnits) × unitPriceAtOrder` for included orders in `APPROVED`, `SUBMITTED`, `ACKNOWLEDGED`, or `PARTIALLY_RECEIVED`. This holds budget for the unreceived portion, including quantities the supplier has not yet confirmed, until the PO is changed or cancelled. `DRAFT` and `PENDING_APPROVAL` are not approved commitments. `RECEIVED`, `CANCELLED`, and `FAILED` contribute no remaining commitment.
- **Available:** `allocated - spent - committed`. Do not clamp a negative result; it signals an overrun that the agent must surface. Spent uses received units while committed uses only unreceived units, so the same PO quantity cannot appear in both totals. If a PO marked `RECEIVED` has unreceived quantity, flag inconsistent data rather than silently treating its remainder as committed.

Invoice matching, payment timing, taxes, freight, and accounting-ledger recognition are explicitly out of scope. The receipt-based spend policy is sufficient for the recommendation demo and must not be described as cash paid.

### Fetching and cascading

- Mark `@ManyToOne` references LAZY to avoid loading full catalog or PO graphs for every read.
- Avoid cascade remove on product, warehouse, supplier, offer, inventory, budget, and PO relationships. Historical purchase and audit evidence must survive master-data deactivation.
- A PO may cascade `PERSIST` to newly created item rows inside one transaction; do not cascade `REMOVE` from parent business masters into orders or events.
- Deactivate products, suppliers, warehouses, or offers instead of deleting referenced rows.
- Use a transactional service to revalidate budget, capacity, supplier validity, and approval immediately before PO execution. Database uniqueness plus a unique idempotency key prevents duplicate execution requests.

### Audit fields

Use `createdAt` and `updatedAt` on mutable business records. For immutable event rows, store only `occurredAt` plus the run and sequence. Set timestamps consistently in UTC. `@Version` on frequently updated inventory and capacity snapshots helps detect concurrent writes. Agent events and PO state transitions should be append-only or preserved in event history.

## 6. Important design decisions

1. **The 800 units are not a business truth.** They are the quantity under review in an agent run, so the evidence remains independent of the recommendation.
2. **Available values are derived.** Do not persist available inventory, reliable/uncertain outstanding supply, line totals, remaining budget, or free storage as duplicate writable values. Derive them from their source values in a service.
3. **Supplier offer is destination-specific.** A supplier's lead time and commercial terms can change by fulfillment node, so `SupplierProduct` includes the target warehouse.
4. **PO values are snapshots.** Ordered/confirmed/received quantities and the unit price at order time preserve evidence even if an offer changes later.
5. **Forecasts retain generations.** A new forecast run does not overwrite old evidence, allowing changed demand to be explained and evaluated.
6. **Safety stock is a policy.** It is separate from on-hand stock and can vary by product and node.
7. **Storage has a defined source.** The WMS allocation snapshot includes physical use and capacity reserved for inbound goods. The agent subtracts it once and does not separately subtract those same POs again.
8. **Audit events are observations, not chain-of-thought.** Persist real tool calls, returned business evidence, concise decision summaries, approvals, execution, and read-back validation. Do not persist hidden model reasoning.
9. **The database does not choose the decision.** It supplies evidence and enforces data integrity. The model may recommend ACCEPT, MODIFY, REJECT, or INVESTIGATE; deterministic policy validates hard constraints and safe execution.

## 7. Risks and assumptions

- **Currency:** the initial scenario uses one currency (recommended INR). Cross-currency budgets need explicit FX rates and conversion-time policy; none are modeled here.
- **Storage source semantics:** the WMS must define `allocatedVolumeM3` as physical occupancy plus already reserved inbound volume. If it does not, the calculation needs a separate inbound-reservation source to avoid under- or double-counting.
- **Inventory freshness:** `lastCountedAt` is essential. The acceptable age is a business rule to be chosen in service configuration; no arbitrary staleness threshold is embedded in the schema.
- **Forecast coverage:** forecasts must cover lead time plus review period. Missing days or a stale generation are investigation gaps, not zero demand.
- **Budget accounting:** the demo uses PO `orderDate` to select the budget period, counts received quantity as spent, and counts unreceived quantity as committed only for approved/submitted/acknowledged/partially received orders. Spent and committed quantities do not overlap. This period attribution is a purchasing-control convention; invoice, payment, tax, and freight accounting remain out of scope.
- **Incoming-supply confidence:** only due-in-horizon confirmed outstanding quantity on acknowledged/partially received orders counts as reliable incoming. Unconfirmed amounts remain a separate potential-supply figure; draft/pending-approval proposals and cancelled/failed orders are not incoming stock. Current inventory snapshots must include receipts through their timestamp.
- **Supplier terms history:** only the current offer is stored. PO snapshots preserve historical price/quantity, but reconstructing all historical MOQ or lead-time changes would need an offer-history table, unnecessary for the first scenario.
- **Approval identity:** `approvedBy` is an audit label, not an authenticated identity. A real identity provider/security model is out of scope for this mock assignment.
- **Approval identity security:** the demo accepts a client-supplied approver label; it is not secure authentication or authorization. Production use requires authenticated identity and role checks.
- **Concurrent execution:** approval UUID-derived PO idempotency keys, database uniqueness, a pessimistic approval lock, and a serializable transaction protect the internal demo write. Revalidation still occurs immediately before PO creation.
- **Revalidation:** an approval binds a canonical evidence snapshot and fingerprint. Any changed evidence or failed hard constraint blocks PO creation; a materially changed plan needs a new AgentRun and explicit approval.
- **Recovery:** if a transaction fails before commit, the system records failure after rollback and allows a safe retry with the same key when no PO exists. If a PO already exists for the key, it is read back and never duplicated; mismatches remain linked for investigation.
- **H2/PostgreSQL:** avoid H2-only data types and database-specific SQL. Use portable JPA mappings and review generated DDL against PostgreSQL when that database is introduced.

## 8. Proposed implementation order for Step 2.2 onward

1. Confirm the business assumptions below before encoding them.
2. Add portable enums and entity classes for `Product`, `Warehouse`, `Supplier`, and `SupplierProduct`.
3. Add `Inventory`, `DemandForecast`, and `ReplenishmentPolicy` with their composite uniqueness and useful indexes.
4. Add `PurchasingBudget` and `StorageCapacity`; document the source semantics for budget commitments and allocated storage.
5. Add `PurchaseOrder` and `PurchaseOrderItem`, including order/confirmation/receipt status and idempotency constraints.
6. Add `AgentRun` and append-only `AgentEvent` to capture actual investigations and outcomes.
7. Add repositories and a small set of mock rows only after the schema is reviewed; test uniqueness, quantity checks, and core relationships with H2.
8. Keep database migration tooling out of this short local milestone unless schema evolution becomes necessary; if introduced, ensure the same migrations run against H2 and PostgreSQL.

## 9. Remaining demo assumptions

The current review defines `EACH` for the Wireless Mouse, the PO status lifecycle, the incoming-supply confidence policy, and the receipt-based budget accounting policy. Those items are no longer open design questions for the initial demo.

The following are remaining assumptions to confirm or refine when implementation begins:

1. Use INR as the sole demo currency; defer exchange-rate conversion.
2. Store storage measures only in cubic metres and define WMS allocated volume as including inbound reservations.
3. Keep one current supplier offer per supplier/product/warehouse; preserve historical prices on PO lines.
4. Use daily forecasts covering lead time plus a configurable review period, retain separate forecast runs, and agree how the service selects the latest complete run.
5. Configure acceptable inventory/forecast/capacity snapshot freshness thresholds; until then, missing or stale timestamps are evidence gaps for `INVESTIGATE`.
6. Treat reserved stock greater than on-hand, missing forecast days, and unknown product volume as evidence gaps for `INVESTIGATE`, rather than silently correcting or assuming values.

The approval model and the execution/revalidation rules above are implemented for the initial demo. These remaining items concern later operational scope; none changes the current approval safety gate.
