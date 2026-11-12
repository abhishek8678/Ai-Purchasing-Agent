package com.procureai.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.procureai.domain.entity.Product;
import com.procureai.domain.entity.ReplenishmentPolicy;
import com.procureai.domain.entity.SupplierProduct;
import com.procureai.domain.entity.Warehouse;
import com.procureai.repository.ReplenishmentPolicyRepository;
import com.procureai.repository.SupplierProductRepository;
import com.procureai.service.dto.BaselineRequirementEvidence;
import com.procureai.service.dto.BudgetEvidence;
import com.procureai.service.dto.EvidenceIssue;
import com.procureai.service.dto.ForecastEvidence;
import com.procureai.service.dto.InventoryEvidence;
import com.procureai.service.dto.InvestigationRequest;
import com.procureai.service.dto.ProductEvidence;
import com.procureai.service.dto.PurchaseOrderEvidence;
import com.procureai.service.dto.PurchasingEvidenceSnapshot;
import com.procureai.service.dto.StorageEvidence;
import com.procureai.service.dto.SupplierEvidence;
import com.procureai.service.dto.WarehouseEvidence;
import com.procureai.service.exception.InvestigationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Assembles an evidence snapshot without prescribing a mandatory future-agent tool sequence. */
@Service
public class PurchasingEvidenceSnapshotService {

    private final EvidenceLookupService lookupService;
    private final SupplierProductRepository supplierProductRepository;
    private final ReplenishmentPolicyRepository policyRepository;
    private final InventoryInvestigationService inventoryService;
    private final DemandForecastInvestigationService forecastService;
    private final OpenPurchaseOrderInvestigationService purchaseOrderService;
    private final SupplierInvestigationService supplierService;
    private final BudgetInvestigationService budgetService;
    private final StorageInvestigationService storageService;
    private final PurchaseRequirementService requirementService;
    private final Clock clock;

    public PurchasingEvidenceSnapshotService(
            EvidenceLookupService lookupService,
            SupplierProductRepository supplierProductRepository,
            ReplenishmentPolicyRepository policyRepository,
            InventoryInvestigationService inventoryService,
            DemandForecastInvestigationService forecastService,
            OpenPurchaseOrderInvestigationService purchaseOrderService,
            SupplierInvestigationService supplierService,
            BudgetInvestigationService budgetService,
            StorageInvestigationService storageService,
            PurchaseRequirementService requirementService,
            Clock clock
    ) {
        this.lookupService = lookupService;
        this.supplierProductRepository = supplierProductRepository;
        this.policyRepository = policyRepository;
        this.inventoryService = inventoryService;
        this.forecastService = forecastService;
        this.purchaseOrderService = purchaseOrderService;
        this.supplierService = supplierService;
        this.budgetService = budgetService;
        this.storageService = storageService;
        this.requirementService = requirementService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PurchasingEvidenceSnapshot investigate(InvestigationRequest request) {
        Product product = lookupService.requireProduct(request.productSku());
        Warehouse warehouse = lookupService.requireWarehouse(request.warehouseCode());
        ProductEvidence productEvidence = new ProductEvidence(product.getId(), product.getSku(), product.getName(),
                product.getUnitOfMeasure(), product.getUnitVolumeM3(), Boolean.TRUE.equals(product.getActive()));
        WarehouseEvidence warehouseEvidence = new WarehouseEvidence(warehouse.getId(), warehouse.getCode(),
                warehouse.getName(), warehouse.getCity(), warehouse.getCountryCode(),
                Boolean.TRUE.equals(warehouse.getActive()));

        List<EvidenceIssue> issues = new ArrayList<>();
        List<SupplierProduct> offers = supplierProductRepository.findByProductAndWarehouse(product, warehouse);
        SupplierEvidence supplierEvidence = supplierService.investigate(
                product, warehouse, request.recommendedPurchaseUnits(), offers);
        PurchaseOrderEvidence purchaseOrderEvidence = purchaseOrderService.investigate(
                product, warehouse, request.planningHorizon(), offers);
        InventoryEvidence inventoryEvidence = capture("MISSING_OR_INVALID_INVENTORY", issues,
                () -> inventoryService.investigate(product, warehouse));
        ForecastEvidence forecastEvidence = capture("MISSING_OR_INVALID_FORECAST", issues,
                () -> forecastService.investigate(product, warehouse, request.planningHorizon()));
        BudgetEvidence budgetEvidence = capture("MISSING_OR_INVALID_BUDGET", issues,
                () -> budgetService.investigate(warehouse, request.budgetDate()));
        StorageEvidence storageEvidence = capture("MISSING_OR_INVALID_STORAGE", issues,
                () -> storageService.investigate(warehouse));

        ReplenishmentPolicy replenishmentPolicy = policyRepository.findByProductAndWarehouse(product, warehouse)
                .orElse(null);
        Long safetyStock = replenishmentPolicy == null ? null : replenishmentPolicy.getSafetyStockUnits();
        Integer reviewPeriod = replenishmentPolicy == null ? null : replenishmentPolicy.getReviewPeriodDays();
        if (replenishmentPolicy == null) {
            issues.add(new EvidenceIssue("MISSING_REPLENISHMENT_POLICY",
                    "No replenishment policy exists for " + product.getSku() + " at " + warehouse.getCode()));
        }

        BaselineRequirementEvidence baseline = requirementService.calculate(
                inventoryEvidence, forecastEvidence, safetyStock, purchaseOrderEvidence);
        return new PurchasingEvidenceSnapshot(productEvidence, warehouseEvidence, request.planningHorizon(),
                LocalDateTime.now(clock), inventoryEvidence, forecastEvidence, purchaseOrderEvidence,
                supplierEvidence, budgetEvidence, storageEvidence, safetyStock, reviewPeriod, baseline,
                List.copyOf(issues));
    }

    private <T> T capture(String fallbackCode, List<EvidenceIssue> issues, EvidenceSupplier<T> supplier) {
        try {
            return supplier.get();
        } catch (InvestigationException exception) {
            issues.add(new EvidenceIssue(exception.getCode() == null ? fallbackCode : exception.getCode(),
                    exception.getMessage()));
            return null;
        }
    }

    @FunctionalInterface
    private interface EvidenceSupplier<T> {
        T get();
    }
}
