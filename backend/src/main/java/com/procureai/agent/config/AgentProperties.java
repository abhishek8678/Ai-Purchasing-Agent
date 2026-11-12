package com.procureai.agent.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@ConfigurationProperties(prefix = "procureai.agent")
public class AgentProperties {

    private String geminiApiKey = "";
    private String model = "gemini-3.5-flash";
    private Duration requestTimeout = Duration.ofSeconds(60);
    private int maxInteractionRounds = 8;
    private int maxToolExecutions = 16;
    private int maxRepeatedIdenticalToolCalls = 2;
    /** Attempts per model round for transient failures (timeout, 5xx). Each attempt has its own timeout. */
    private int maxModelAttempts = 3;
    private Duration modelRetryBackoff = Duration.ofSeconds(2);

    public void validateLimits() {
        if (!StringUtils.hasText(model) || requestTimeout == null || requestTimeout.isNegative()
                || requestTimeout.isZero() || requestTimeout.toMillis() > Integer.MAX_VALUE
                || maxInteractionRounds < 1 || maxToolExecutions < 1
                || maxRepeatedIdenticalToolCalls < 1 || maxModelAttempts < 1 || maxModelAttempts > 5
                || modelRetryBackoff == null || modelRetryBackoff.isNegative()) {
            throw new IllegalStateException("Invalid procureai.agent execution configuration");
        }
    }

    public String getGeminiApiKey() { return geminiApiKey; }
    public void setGeminiApiKey(String geminiApiKey) { this.geminiApiKey = geminiApiKey; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public Duration getRequestTimeout() { return requestTimeout; }
    public void setRequestTimeout(Duration requestTimeout) { this.requestTimeout = requestTimeout; }
    public int getMaxInteractionRounds() { return maxInteractionRounds; }
    public void setMaxInteractionRounds(int maxInteractionRounds) { this.maxInteractionRounds = maxInteractionRounds; }
    public int getMaxToolExecutions() { return maxToolExecutions; }
    public void setMaxToolExecutions(int maxToolExecutions) { this.maxToolExecutions = maxToolExecutions; }
    public int getMaxModelAttempts() { return maxModelAttempts; }
    public void setMaxModelAttempts(int maxModelAttempts) { this.maxModelAttempts = maxModelAttempts; }
    public Duration getModelRetryBackoff() { return modelRetryBackoff; }
    public void setModelRetryBackoff(Duration modelRetryBackoff) { this.modelRetryBackoff = modelRetryBackoff; }
    public int getMaxRepeatedIdenticalToolCalls() { return maxRepeatedIdenticalToolCalls; }
    public void setMaxRepeatedIdenticalToolCalls(int maxRepeatedIdenticalToolCalls) {
        this.maxRepeatedIdenticalToolCalls = maxRepeatedIdenticalToolCalls;
    }
}
