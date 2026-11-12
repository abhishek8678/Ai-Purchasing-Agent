package com.procureai.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.procureai.config.InvestigationPolicyProperties;
import com.procureai.domain.entity.DemandForecast;
import com.procureai.domain.entity.Product;
import com.procureai.domain.entity.Warehouse;
import com.procureai.repository.DemandForecastRepository;
import com.procureai.service.dto.DailyForecastEvidence;
import com.procureai.service.dto.ForecastEvidence;
import com.procureai.service.dto.PlanningHorizon;
import com.procureai.service.exception.InvestigationException;
import org.springframework.stereotype.Service;

@Service
public class DemandForecastInvestigationService {

    private final DemandForecastRepository forecastRepository;
    private final InvestigationPolicyProperties policy;
    private final Clock clock;

    public DemandForecastInvestigationService(
            DemandForecastRepository forecastRepository,
            InvestigationPolicyProperties policy,
            Clock clock
    ) {
        this.forecastRepository = forecastRepository;
        this.policy = policy;
        this.clock = clock;
    }

    public ForecastEvidence investigate(Product product, Warehouse warehouse, PlanningHorizon horizon) {
        List<DemandForecast> allForecasts = forecastRepository.findByProductAndWarehouse(product, warehouse);
        if (allForecasts.isEmpty()) {
            throw new InvestigationException("MISSING_FORECAST",
                    "No daily forecasts exist for " + product.getSku() + " at " + warehouse.getCode());
        }

        Map<UUID, List<DemandForecast>> byRun = new HashMap<>();
        allForecasts.forEach(forecast -> byRun.computeIfAbsent(forecast.getForecastRunId(), key -> new ArrayList<>())
                .add(forecast));

        List<ForecastRun> runs = byRun.values().stream().map(rows -> toRun(rows, horizon))
                .sorted(Comparator.comparing(ForecastRun::generatedAt,
                        Comparator.nullsFirst(Comparator.naturalOrder())).reversed())
                .toList();
        ForecastRun selected = runs.stream().filter(ForecastRun::complete).findFirst().orElse(runs.get(0));
        boolean usedIncompleteFallback = !selected.complete();
        LocalDateTime now = LocalDateTime.now(clock);
        Duration threshold = policy.getForecastFreshnessThreshold();
        boolean fresh = selected.generatedAt() != null
                && !selected.generatedAt().isAfter(now)
                && !selected.generatedAt().isBefore(now.minus(threshold));

        List<String> warnings = new ArrayList<>();
        if (usedIncompleteFallback) {
            warnings.add("No complete forecast generation covers the entire planning horizon; only one latest run is reported.");
        }
        if (selected.generatedAt() == null) {
            warnings.add("Forecast generation timestamp is missing.");
        } else if (selected.generatedAt().isAfter(now)) {
            warnings.add("Forecast generation timestamp is in the future.");
        } else if (!fresh) {
            warnings.add("Forecast generation is older than the configured freshness threshold.");
        }
        if (!selected.missingDates().isEmpty()) {
            warnings.add("Forecast is missing " + selected.missingDates().size() + " date(s) in the requested horizon.");
        }
        if (selected.rows().stream().anyMatch(row -> row.getExpectedUnits() == null || row.getExpectedUnits() < 0)) {
            throw new InvestigationException("INVALID_FORECAST", "Forecast units must be present and non-negative.");
        }

        List<DailyForecastEvidence> daily = selected.rows().stream()
                .filter(row -> horizon.contains(row.getForecastDate()))
                .sorted(Comparator.comparing(DemandForecast::getForecastDate))
                .map(row -> new DailyForecastEvidence(row.getForecastDate(), row.getExpectedUnits()))
                .toList();
        long observedDemand = daily.stream().mapToLong(DailyForecastEvidence::expectedUnits)
                .reduce(0L, Math::addExact);

        return new ForecastEvidence(product.getSku(), warehouse.getCode(), selected.runId(),
                selected.modelVersion(), selected.generatedAt(), horizon, daily, selected.missingDates(),
                observedDemand, selected.complete(), fresh, threshold, policy.getForecastSelectionPolicy(),
                List.copyOf(warnings));
    }

    private ForecastRun toRun(List<DemandForecast> rows, PlanningHorizon horizon) {
        Map<LocalDate, DemandForecast> byDate = new HashMap<>();
        rows.forEach(row -> byDate.put(row.getForecastDate(), row));
        List<LocalDate> missingDates = horizon.dates().stream().filter(date -> !byDate.containsKey(date)).toList();
        DemandForecast first = rows.stream().min(Comparator.comparing(
                DemandForecast::getGeneratedAt, Comparator.nullsLast(Comparator.naturalOrder()))).orElseThrow();
        List<DemandForecast> orderedRows = rows.stream()
                .sorted(Comparator.comparing(DemandForecast::getForecastDate)).toList();
        return new ForecastRun(first.getForecastRunId(), first.getModelVersion(), first.getGeneratedAt(),
                orderedRows, missingDates, missingDates.isEmpty());
    }

    private record ForecastRun(
            UUID runId,
            String modelVersion,
            LocalDateTime generatedAt,
            List<DemandForecast> rows,
            List<LocalDate> missingDates,
            boolean complete
    ) {
    }
}
