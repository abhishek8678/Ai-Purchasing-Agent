package com.procureai.service.dto;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.stream.LongStream;

/** Inclusive calendar-date planning interval. */
public record PlanningHorizon(LocalDate startDate, LocalDate endDate) {

    public PlanningHorizon {
        Objects.requireNonNull(startDate, "startDate is required");
        Objects.requireNonNull(endDate, "endDate is required");
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("Planning horizon end date must not be before start date");
        }
    }

    public boolean contains(LocalDate date) {
        return date != null && !date.isBefore(startDate) && !date.isAfter(endDate);
    }

    public List<LocalDate> dates() {
        long inclusiveDays = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        if (inclusiveDays > 3660) {
            throw new IllegalArgumentException("Planning horizon cannot exceed 10 years");
        }
        return LongStream.range(0, inclusiveDays).mapToObj(startDate::plusDays).toList();
    }
}
