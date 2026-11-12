package com.procureai.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Explicit investigation defaults. Forecast selection uses the newest complete run;
 * if no complete run exists, the newest available run is reported for gap analysis only.
 */
@Component
@ConfigurationProperties(prefix = "procureai.investigation")
public class InvestigationPolicyProperties {

    private Duration inventoryFreshnessThreshold = Duration.ofHours(24);
    private Duration forecastFreshnessThreshold = Duration.ofDays(3);
    private Duration storageFreshnessThreshold = Duration.ofDays(7);
    private String forecastSelectionPolicy =
            "LATEST_COMPLETE_RUN_FALLBACK_TO_LATEST_AVAILABLE_FOR_GAP_REPORTING";

    public Duration getInventoryFreshnessThreshold() {
        return inventoryFreshnessThreshold;
    }

    public void setInventoryFreshnessThreshold(Duration inventoryFreshnessThreshold) {
        this.inventoryFreshnessThreshold = inventoryFreshnessThreshold;
    }

    public Duration getForecastFreshnessThreshold() {
        return forecastFreshnessThreshold;
    }

    public void setForecastFreshnessThreshold(Duration forecastFreshnessThreshold) {
        this.forecastFreshnessThreshold = forecastFreshnessThreshold;
    }

    public Duration getStorageFreshnessThreshold() {
        return storageFreshnessThreshold;
    }

    public void setStorageFreshnessThreshold(Duration storageFreshnessThreshold) {
        this.storageFreshnessThreshold = storageFreshnessThreshold;
    }

    public String getForecastSelectionPolicy() {
        return forecastSelectionPolicy;
    }

    public void setForecastSelectionPolicy(String forecastSelectionPolicy) {
        this.forecastSelectionPolicy = forecastSelectionPolicy;
    }
}
