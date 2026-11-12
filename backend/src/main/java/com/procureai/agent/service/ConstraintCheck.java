package com.procureai.agent.service;

/** What one evaluate_purchase_constraints call in the current run actually evaluated, and whether it all passed. */
public record ConstraintCheck(Long supplierProductId, Long proposedUnits, boolean allPassed) {
}
