package com.procureai.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.procureai.domain.entity.PurchaseOrder;
import com.procureai.domain.entity.PurchaseOrderItem;
import com.procureai.domain.entity.PurchasingBudget;
import com.procureai.domain.entity.Warehouse;
import com.procureai.domain.enums.PurchaseOrderStatus;
import com.procureai.repository.PurchaseOrderItemRepository;
import com.procureai.repository.PurchaseOrderRepository;
import com.procureai.repository.PurchasingBudgetRepository;
import com.procureai.service.dto.BudgetEvidence;
import com.procureai.service.exception.InvestigationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BudgetInvestigationService {

    private static final Set<PurchaseOrderStatus> COMMITTED_STATUSES = Set.of(
            PurchaseOrderStatus.APPROVED,
            PurchaseOrderStatus.SUBMITTED,
            PurchaseOrderStatus.ACKNOWLEDGED,
            PurchaseOrderStatus.PARTIALLY_RECEIVED
    );

    private final PurchasingBudgetRepository budgetRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderItemRepository itemRepository;

    public BudgetInvestigationService(
            PurchasingBudgetRepository budgetRepository,
            PurchaseOrderRepository purchaseOrderRepository,
            PurchaseOrderItemRepository itemRepository
    ) {
        this.budgetRepository = budgetRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.itemRepository = itemRepository;
    }

    @Transactional(readOnly = true)
    public BudgetEvidence investigate(Warehouse warehouse, LocalDate budgetDate) {
        List<PurchasingBudget> matchingBudgets = budgetRepository.findByWarehouse(warehouse).stream()
                .filter(budget -> !budgetDate.isBefore(budget.getPeriodStart())
                        && !budgetDate.isAfter(budget.getPeriodEnd()))
                .toList();
        if (matchingBudgets.isEmpty()) {
            throw new InvestigationException("MISSING_BUDGET",
                    "No purchasing budget covers " + budgetDate + " for warehouse " + warehouse.getCode());
        }
        if (matchingBudgets.size() > 1) {
            throw new InvestigationException("OVERLAPPING_BUDGETS",
                    "More than one budget covers " + budgetDate + " for warehouse " + warehouse.getCode());
        }

        PurchasingBudget budget = matchingBudgets.get(0);
        if (budget.getBudgetAmount() == null || budget.getBudgetAmount().signum() < 0
                || budget.getCurrencyCode() == null) {
            throw new InvestigationException("INVALID_BUDGET",
                    "Budget amount and currency must be present and valid for " + warehouse.getCode());
        }
        List<PurchaseOrder> orders = purchaseOrderRepository.findByWarehouseAndOrderDateBetween(
                warehouse, budget.getPeriodStart(), budget.getPeriodEnd());
        List<PurchaseOrderItem> items = orders.isEmpty()
                ? List.of()
                : itemRepository.findByPurchaseOrderIn(orders);

        BigDecimal spent = BigDecimal.ZERO;
        BigDecimal committed = BigDecimal.ZERO;
        List<String> mismatches = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        boolean complete = true;
        for (PurchaseOrder order : orders) {
            if (order.getCurrencyCode() == null || !budget.getCurrencyCode().equals(order.getCurrencyCode())) {
                mismatches.add(order.getPoNumber() + ": " + order.getCurrencyCode());
                complete = false;
                continue;
            }
            for (PurchaseOrderItem item : items) {
                if (!Objects.equals(item.getPurchaseOrder().getId(), order.getId())) {
                    continue;
                }
                String invalid = validateLine(item, order);
                if (invalid != null) {
                    warnings.add(order.getPoNumber() + ": " + invalid);
                    complete = false;
                    continue;
                }
                BigDecimal lineSpent = item.getUnitPriceAtOrder()
                        .multiply(BigDecimal.valueOf(item.getReceivedUnits()));
                spent = spent.add(lineSpent);
                if (order.getStatus() == PurchaseOrderStatus.RECEIVED
                        && item.getReceivedUnits() < item.getOrderedUnits()) {
                    warnings.add(order.getPoNumber() + ": RECEIVED status has unreceived units.");
                    complete = false;
                }
                if (order.getStatus() != null && COMMITTED_STATUSES.contains(order.getStatus())) {
                    long unreceived = Math.subtractExact(item.getOrderedUnits(), item.getReceivedUnits());
                    BigDecimal lineCommitment = item.getUnitPriceAtOrder().multiply(BigDecimal.valueOf(unreceived));
                    committed = committed.add(lineCommitment);
                }
            }
        }

        BigDecimal available = budget.getBudgetAmount().subtract(spent).subtract(committed);
        if (!mismatches.isEmpty()) {
            warnings.add("POs in a different currency were excluded; no currency conversion was applied.");
        }
        return new BudgetEvidence(warehouse.getCode(), budget.getPeriodStart(), budget.getPeriodEnd(),
                budget.getCurrencyCode(), budget.getBudgetAmount(), spent, committed, available,
                complete, List.copyOf(mismatches), List.copyOf(warnings));
    }

    private String validateLine(PurchaseOrderItem item, PurchaseOrder order) {
        if (order.getStatus() == null || item.getOrderedUnits() == null || item.getOrderedUnits() <= 0
                || item.getReceivedUnits() == null || item.getReceivedUnits() < 0
                || item.getReceivedUnits() > item.getOrderedUnits()
                || item.getUnitPriceAtOrder() == null || item.getUnitPriceAtOrder().signum() < 0) {
            return "PO line quantities, status, or price are missing or inconsistent.";
        }
        return null;
    }
}
