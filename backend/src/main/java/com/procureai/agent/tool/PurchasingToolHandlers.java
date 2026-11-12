package com.procureai.agent.tool;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.procureai.agent.dto.AgentRunRequest;
import com.procureai.domain.entity.Product;
import com.procureai.domain.entity.Warehouse;
import com.procureai.service.BudgetInvestigationService;
import com.procureai.service.ConstraintValidationService;
import com.procureai.service.DemandForecastInvestigationService;
import com.procureai.service.EvidenceLookupService;
import com.procureai.service.InventoryInvestigationService;
import com.procureai.service.OpenPurchaseOrderInvestigationService;
import com.procureai.service.PurchaseRequirementService;
import com.procureai.service.PurchasingEvidenceSnapshotService;
import com.procureai.service.StorageInvestigationService;
import com.procureai.service.SupplierInvestigationService;
import com.procureai.service.dto.InvestigationRequest;
import com.procureai.service.dto.PlanningHorizon;
import com.procureai.service.dto.PurchasingEvidenceSnapshot;
import org.springframework.stereotype.Component;

@Component
public class PurchasingToolHandlers {

    private final EvidenceLookupService lookupService;
    private final InventoryInvestigationService inventoryService;
    private final DemandForecastInvestigationService forecastService;
    private final OpenPurchaseOrderInvestigationService purchaseOrderService;
    private final SupplierInvestigationService supplierService;
    private final BudgetInvestigationService budgetService;
    private final StorageInvestigationService storageService;
    private final PurchasingEvidenceSnapshotService snapshotService;
    private final PurchaseRequirementService requirementService;
    private final ConstraintValidationService constraintService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public PurchasingToolHandlers(
            EvidenceLookupService lookupService,
            InventoryInvestigationService inventoryService,
            DemandForecastInvestigationService forecastService,
            OpenPurchaseOrderInvestigationService purchaseOrderService,
            SupplierInvestigationService supplierService,
            BudgetInvestigationService budgetService,
            StorageInvestigationService storageService,
            PurchasingEvidenceSnapshotService snapshotService,
            PurchaseRequirementService requirementService,
            ConstraintValidationService constraintService,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.lookupService = lookupService;
        this.inventoryService = inventoryService;
        this.forecastService = forecastService;
        this.purchaseOrderService = purchaseOrderService;
        this.supplierService = supplierService;
        this.budgetService = budgetService;
        this.storageService = storageService;
        this.snapshotService = snapshotService;
        this.requirementService = requirementService;
        this.constraintService = constraintService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public List<AgentTool> tools() {
        List<AgentTool> tools = new ArrayList<>();
        tools.add(tool("get_inventory", "Get current on-hand, reserved, available, timestamp, and freshness evidence for this run's product and warehouse.", emptySchema(),
                (args, context) -> {
                    Product product = product(context.request());
                    Warehouse warehouse = warehouse(context.request());
                    var evidence = inventoryService.investigate(product, warehouse);
                    String ref = inventoryReference(evidence.productSku(), evidence.warehouseCode(),
                            evidence.lastCountedAt());
                    return output(Map.of("evidenceReference", ref, "inventory", json(evidence)), Set.of(ref), Map.of());
                }));
        tools.add(tool("get_demand_forecast", "Get daily demand forecast evidence for the run's inclusive planning horizon; reports gaps, run identity, freshness, and selected-run policy.", emptySchema(),
                (args, context) -> {
                    Product product = product(context.request());
                    Warehouse warehouse = warehouse(context.request());
                    var evidence = forecastService.investigate(product, warehouse, horizon(context.request()));
                    String ref = forecastReference(evidence.forecastRunId().toString());
                    return output(Map.of("evidenceReference", ref, "forecast", json(evidence)), Set.of(ref), Map.of());
                }));
        tools.add(tool("get_open_purchase_orders", "Get PO line evidence in the planning horizon, separating confirmed outstanding units from uncertain potential supply.", emptySchema(),
                (args, context) -> {
                    Product product = product(context.request());
                    Warehouse warehouse = warehouse(context.request());
                    var evidence = purchaseOrderService.investigate(product, warehouse, horizon(context.request()));
                    String ref = purchaseOrderReference(evidence.productSku(), evidence.warehouseCode(), evidence.planningHorizon());
                    return output(Map.of("evidenceReference", ref, "purchaseOrders", json(evidence)), Set.of(ref), Map.of());
                }));
        tools.add(tool("get_supplier_offers", "Get available and unavailable supplier offers, commercial terms, MOQ/pack rounding for the original recommendation, and real offer identifiers. No offer is selected automatically.", emptySchema(),
                (args, context) -> {
                    Product product = product(context.request());
                    Warehouse warehouse = warehouse(context.request());
                    var evidence = supplierService.investigate(product, warehouse,
                            context.request().recommendedPurchaseUnits());
                    String ref = supplierReference(evidence.productSku(), evidence.warehouseCode());
                    Set<String> refs = new LinkedHashSet<>();
                    refs.add(ref);
                    Map<Long, Long> offerSuppliers = new LinkedHashMap<>();
                    evidence.offers().forEach(offer -> {
                        if (offer.supplierProductId() != null) {
                            refs.add(offerReference(offer.supplierProductId()));
                            if (offer.supplierId() != null) {
                                offerSuppliers.put(offer.supplierProductId(), offer.supplierId());
                            }
                        }
                    });
                    // Show the model the exact per-offer references the validator requires it to cite.
                    Map<String, String> offerReferences = new LinkedHashMap<>();
                    offerSuppliers.keySet().forEach(id -> offerReferences.put(String.valueOf(id), offerReference(id)));
                    return output(Map.of("evidenceReference", ref, "supplierOffers", json(evidence),
                            "offerEvidenceReferencesBySupplierProductId", offerReferences), refs, offerSuppliers);
                }));
        tools.add(tool("get_purchasing_budget", "Get allocated, spent, committed, and available purchasing budget for the run's warehouse and current budget date.", emptySchema(),
                (args, context) -> {
                    Warehouse warehouse = warehouse(context.request());
                    var evidence = budgetService.investigate(warehouse, LocalDate.now(clock));
                    String ref = "budget:" + evidence.warehouseCode() + ":" + evidence.periodStart() + ":" + evidence.periodEnd();
                    return output(Map.of("evidenceReference", ref, "budget", json(evidence)), Set.of(ref), Map.of());
                }));
        tools.add(tool("get_storage_capacity", "Get the WMS total, allocated, available volume, measurement time, consistency, and freshness for the run's warehouse.", emptySchema(),
                (args, context) -> {
                    Warehouse warehouse = warehouse(context.request());
                    var evidence = storageService.investigate(warehouse);
                    String ref = "storage:" + evidence.warehouseCode() + ":" + evidence.measuredAt();
                    return output(Map.of("evidenceReference", ref, "storage", json(evidence)), Set.of(ref), Map.of());
                }));
        tools.add(tool("calculate_purchase_requirement", "Calculate the transparent baseline requirement from complete current evidence. Uncertain supply is reported separately and is not subtracted.", emptySchema(),
                (args, context) -> {
                    PurchasingEvidenceSnapshot snapshot = snapshot(context.request());
                    var baseline = requirementService.calculate(snapshot.inventory(), snapshot.forecast(),
                            snapshot.safetyStockUnits(), snapshot.purchaseOrders());
                    Set<String> refs = evidenceReferences(snapshot);
                    String baselineRef = "baseline:" + context.runId() + ":" + context.executionNumber();
                    refs.add(baselineRef);
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("evidenceReference", baselineRef);
                    result.put("baselineRequirement", json(baseline));
                    result.put("supportingEvidenceReferences", refs.stream().filter(ref -> !ref.equals(baselineRef)).toList());
                    result.put("evidenceIssues", json(snapshot.issues()));
                    return AgentToolOutput.success(result, refs, offerSuppliers(snapshot));
                }));
        tools.add(tool("evaluate_purchase_constraints", "Evaluate explicit hard constraints for a proposed positive whole-unit quantity and a real supplier offer identifier from this run. This is not a final purchase decision.", constraintSchema(),
                (args, context) -> {
                    long supplierProductId = ToolArgumentValidator.requirePositiveLong(args, "supplierProductId");
                    long proposedUnits = ToolArgumentValidator.requirePositiveLong(args, "proposedUnits");
                    PurchasingEvidenceSnapshot snapshot = snapshot(context.request());
                    Map<Long, Long> offers = offerSuppliers(snapshot);
                    if (!offers.containsKey(supplierProductId)) {
                        throw new IllegalArgumentException("supplierProductId does not identify an offer for this product and warehouse.");
                    }
                    var evaluation = constraintService.evaluate(snapshot, supplierProductId, proposedUnits);
                    Set<String> refs = evidenceReferences(snapshot);
                    String constraintRef = "constraints:" + context.runId() + ":" + context.executionNumber();
                    refs.add(constraintRef);
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("evidenceReference", constraintRef);
                    result.put("constraintEvaluation", json(evaluation));
                    result.put("evaluatedSupplierProductId", supplierProductId);
                    result.put("evaluatedUnits", proposedUnits);
                    result.put("allConstraintsPassed", evaluation.results().stream()
                            .allMatch(item -> item.status() == com.procureai.service.dto.ConstraintStatus.PASS));
                    result.put("supportingEvidenceReferences", refs.stream().filter(ref -> !ref.equals(constraintRef)).toList());
                    return AgentToolOutput.success(result, refs, offers);
                }));
        return List.copyOf(tools);
    }

    private Product product(AgentRunRequest request) {
        return lookupService.requireProduct(request.productSku());
    }

    private Warehouse warehouse(AgentRunRequest request) {
        return lookupService.requireWarehouse(request.warehouseCode());
    }

    private PlanningHorizon horizon(AgentRunRequest request) {
        return new PlanningHorizon(request.planningStartDate(), request.planningEndDate());
    }

    private InvestigationRequest investigationRequest(AgentRunRequest request) {
        return new InvestigationRequest(request.productSku(), request.warehouseCode(), horizon(request),
                LocalDate.now(clock), request.recommendedPurchaseUnits());
    }

    private PurchasingEvidenceSnapshot snapshot(AgentRunRequest request) {
        return snapshotService.investigate(investigationRequest(request));
    }

    private Map<Long, Long> offerSuppliers(PurchasingEvidenceSnapshot snapshot) {
        Map<Long, Long> offers = new LinkedHashMap<>();
        if (snapshot.suppliers() != null) {
            snapshot.suppliers().offers().forEach(offer -> {
                if (offer.supplierProductId() != null && offer.supplierId() != null) {
                    offers.put(offer.supplierProductId(), offer.supplierId());
                }
            });
        }
        return offers;
    }

    private Set<String> evidenceReferences(PurchasingEvidenceSnapshot snapshot) {
        Set<String> refs = new LinkedHashSet<>();
        if (snapshot.inventory() != null) {
            refs.add(inventoryReference(snapshot.inventory().productSku(), snapshot.inventory().warehouseCode(),
                    snapshot.inventory().lastCountedAt()));
        }
        if (snapshot.forecast() != null) refs.add(forecastReference(snapshot.forecast().forecastRunId().toString()));
        if (snapshot.purchaseOrders() != null) {
            refs.add(purchaseOrderReference(snapshot.purchaseOrders().productSku(),
                    snapshot.purchaseOrders().warehouseCode(), snapshot.purchaseOrders().planningHorizon()));
        }
        if (snapshot.suppliers() != null) {
            refs.add(supplierReference(snapshot.suppliers().productSku(), snapshot.suppliers().warehouseCode()));
            snapshot.suppliers().offers().stream().filter(offer -> offer.supplierProductId() != null)
                    .forEach(offer -> refs.add(offerReference(offer.supplierProductId())));
        }
        if (snapshot.budget() != null) {
            refs.add("budget:" + snapshot.budget().warehouseCode() + ":" + snapshot.budget().periodStart()
                    + ":" + snapshot.budget().periodEnd());
        }
        if (snapshot.storage() != null) refs.add("storage:" + snapshot.storage().warehouseCode() + ":" + snapshot.storage().measuredAt());
        return refs;
    }

    private String inventoryReference(String sku, String warehouse, Object timestamp) {
        return "inventory:" + sku + ":" + warehouse + ":" + timestamp;
    }

    private String forecastReference(String runId) { return "forecast:" + runId; }
    private String supplierReference(String sku, String warehouse) { return "supplier-offers:" + sku + ":" + warehouse; }
    private String offerReference(Long offerId) { return "supplier-offer:" + offerId; }
    private String purchaseOrderReference(String sku, String warehouse, PlanningHorizon horizon) {
        return "purchase-orders:" + sku + ":" + warehouse + ":" + horizon.startDate() + ":" + horizon.endDate();
    }

    private AgentToolOutput output(Map<String, Object> payload, Set<String> refs, Map<Long, Long> offers) {
        return AgentToolOutput.success(payload, refs, offers);
    }

    private Object json(Object value) {
        return objectMapper.convertValue(value, Object.class);
    }

    private AgentTool tool(String name, String description, AgentToolDefinition definition,
                           BiFunction<Map<String, Object>, AgentToolContext, AgentToolOutput> executor) {
        return new AgentTool() {
            private final AgentToolDefinition toolDefinition = new AgentToolDefinition(name, description,
                    definition.parametersJsonSchema(), definition.allowedArguments(), definition.requiredArguments());
            @Override public AgentToolDefinition definition() { return toolDefinition; }
            @Override public AgentToolOutput execute(Map<String, Object> arguments, AgentToolContext context) {
                return executor.apply(arguments, context);
            }
        };
    }

    private static AgentToolDefinition emptySchema() {
        return new AgentToolDefinition("", "", Map.of("type", "object", "properties", Map.of(),
                "additionalProperties", false), Set.of(), Set.of());
    }

    private static AgentToolDefinition constraintSchema() {
        Map<String, Object> properties = Map.of(
                "supplierProductId", Map.of("type", "integer", "minimum", 1,
                        "description", "SupplierProduct offer ID returned by get_supplier_offers."),
                "proposedUnits", Map.of("type", "integer", "minimum", 1,
                        "description", "Proposed whole-unit order quantity to evaluate."));
        return new AgentToolDefinition("", "", Map.of("type", "object", "properties", properties,
                "required", List.of("supplierProductId", "proposedUnits"), "additionalProperties", false),
                Set.of("supplierProductId", "proposedUnits"), Set.of("supplierProductId", "proposedUnits"));
    }
}
