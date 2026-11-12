package com.procureai.service.dto;

import java.util.List;

public record BaselineRequirementEvidence(
        Long grossDemandUnits,
        Long safetyStockUnits,
        Long availableStockUnits,
        Long confirmedIncomingUnits,
        Long uncertainUnconfirmedSupplyUnits,
        Long baselineNetRequirementUnits,
        boolean calculable,
        List<String> warnings
) {
}
