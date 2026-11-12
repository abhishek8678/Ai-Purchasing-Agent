package com.procureai.service.dto;

import java.time.LocalDate;

public record DailyForecastEvidence(LocalDate forecastDate, long expectedUnits) {
}
