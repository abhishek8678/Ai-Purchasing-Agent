package com.procureai.service.dto;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ForecastEvidence(
        String productSku,
        String warehouseCode,
        UUID forecastRunId,
        String modelVersion,
        LocalDateTime generatedAt,
        PlanningHorizon planningHorizon,
        List<DailyForecastEvidence> dailyForecasts,
        List<LocalDate> missingDates,
        long observedDemandUnits,
        boolean complete,
        boolean fresh,
        Duration freshnessThreshold,
        String selectionPolicy,
        List<String> warnings
) {
}
