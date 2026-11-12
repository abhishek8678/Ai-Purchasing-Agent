package com.procureai.agent.model;

import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.google.genai.Client;
import com.google.genai.errors.ApiException;
import com.google.genai.types.AutomaticFunctionCallingConfig;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.FunctionCallingConfig;
import com.google.genai.types.FunctionCallingConfigMode;
import com.google.genai.types.FunctionDeclaration;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import com.google.genai.types.Part;
import com.google.genai.types.Tool;
import com.google.genai.types.ToolConfig;
import com.procureai.agent.config.AgentProperties;
import com.procureai.agent.exception.AgentConfigurationException;
import com.procureai.agent.tool.AgentToolDefinition;
import com.procureai.agent.model.ModelGatewayException.Kind;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class GeminiSdkModelGateway implements GeminiModelGateway {

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(GeminiSdkModelGateway.class);

    private final AgentProperties properties;
    private volatile Client client;

    public GeminiSdkModelGateway(AgentProperties properties) {
        this.properties = properties;
    }

    /**
     * Google's documented placeholder for function-call history that was produced by a different model. Real
     * signatures are only valid for the model that issued them.
     */
    static final byte[] FOREIGN_SIGNATURE_PLACEHOLDER =
            "skip_thought_signature_validator".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

    /**
     * Calls Gemini through an ordered model chain. The run sticks to the model that has already answered; only
     * when that model is out of quota (429) or still failing after bounded retries (timeout / 5xx) does the run
     * continue on the next configured fallback model. Authentication and bad-request errors are never retried.
     */
    @Override
    public ModelTurn generate(String systemInstruction, List<ModelMessage> conversation,
                              List<AgentToolDefinition> toolDefinitions) {
        List<String> chain = modelChain(properties.getModel(), properties.getFallbackModels(), lastModel(conversation));
        List<String> tried = new ArrayList<>();
        ModelGatewayException last = null;
        for (String model : chain) {
            tried.add(model);
            try {
                ModelTurn turn = generateWithRetries(model, systemInstruction, conversation, toolDefinitions);
                if (tried.size() > 1) {
                    LOGGER.warn("Gemini model {} unavailable; run continued on fallback model {}.", tried.get(0), model);
                }
                return turn;
            } catch (ModelGatewayException exception) {
                last = exception;
                if (!canFallBack(exception)) break;
                LOGGER.warn("Gemini model {} failed ({}); trying next fallback model.", model, exception.getKind());
            }
        }
        Map<String, Object> diagnostic = new LinkedHashMap<>(last.getDiagnostic());
        diagnostic.put("modelsTried", List.copyOf(tried));
        throw new ModelGatewayException(last.getKind(), last.getMessage(), last.getCause(), diagnostic);
    }

    /** Sticky model first (if any), then the primary model and fallbacks, without duplicates. */
    static List<String> modelChain(String primary, List<String> fallbacks, String sticky) {
        java.util.LinkedHashSet<String> chain = new java.util.LinkedHashSet<>();
        if (StringUtils.hasText(sticky)) chain.add(sticky.trim());
        if (StringUtils.hasText(primary)) chain.add(primary.trim());
        if (fallbacks != null) fallbacks.stream().filter(StringUtils::hasText).map(String::trim).forEach(chain::add);
        return List.copyOf(chain);
    }

    /** The model that issued the most recent thought signature in this conversation, if any. */
    static String lastModel(List<ModelMessage> conversation) {
        for (int i = conversation.size() - 1; i >= 0; i--) {
            for (ModelPart part : conversation.get(i).parts()) {
                if (part.signatureModel() != null) return part.signatureModel();
            }
        }
        return null;
    }

    static boolean canFallBack(ModelGatewayException exception) {
        return exception.getKind() == Kind.RATE_LIMIT || isTransient(exception);
    }

    private ModelTurn generateWithRetries(String model, String systemInstruction, List<ModelMessage> conversation,
                                          List<AgentToolDefinition> toolDefinitions) {
        int maxAttempts = Math.max(1, properties.getMaxModelAttempts());
        for (int attempt = 1; ; attempt++) {
            try {
                return generateOnce(model, systemInstruction, conversation, toolDefinitions);
            } catch (ModelGatewayException exception) {
                if (attempt >= maxAttempts || !isTransient(exception)) {
                    throw withAttempts(exception, attempt);
                }
                LOGGER.warn("Gemini {} attempt {}/{} failed transiently ({}); retrying.", model, attempt, maxAttempts,
                        exception.getKind());
                sleep(properties.getModelRetryBackoff().multipliedBy(attempt));
            }
        }
    }

    static boolean isTransient(ModelGatewayException exception) {
        // 429 is not retried: free-tier quotas are per day, so an immediate retry only wastes requests.
        if (exception.getKind() == Kind.TIMEOUT) return true;
        Object status = exception.getDiagnostic().get("httpStatus");
        return status instanceof Integer code && (code == 500 || code == 502 || code == 503 || code == 504);
    }

    private static ModelGatewayException withAttempts(ModelGatewayException exception, int attempts) {
        Map<String, Object> diagnostic = new LinkedHashMap<>(exception.getDiagnostic());
        diagnostic.put("attempts", attempts);
        return new ModelGatewayException(exception.getKind(), exception.getMessage(), exception.getCause(), diagnostic);
    }

    private static void sleep(java.time.Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private ModelTurn generateOnce(String model, String systemInstruction, List<ModelMessage> conversation,
                                   List<AgentToolDefinition> toolDefinitions) {
        try {
            List<FunctionDeclaration> declarations = toolDefinitions.stream()
                    .map(definition -> FunctionDeclaration.builder()
                            .name(definition.name())
                            .description(definition.description())
                            .parametersJsonSchema(definition.parametersJsonSchema())
                            .build())
                    .toList();
            Tool functionTool = Tool.builder().functionDeclarations(declarations).build();
            GenerateContentConfig config = GenerateContentConfig.builder()
                    .systemInstruction(Content.fromParts(Part.fromText(systemInstruction)))
                    .tools(functionTool)
                    .toolConfig(ToolConfig.builder().functionCallingConfig(FunctionCallingConfig.builder()
                            .mode(FunctionCallingConfigMode.Known.AUTO).build()).build())
                    .automaticFunctionCalling(AutomaticFunctionCallingConfig.builder().disable(true).build())
                    .maxOutputTokens(8192)
                    .build();

            GenerateContentResponse response = client().models.generateContent(
                    model, toSdkContents(conversation, model), config);
            Content candidateContent = response.candidates().orElseThrow(() ->
                    new IllegalStateException("Gemini returned no candidate content."))
                    .stream().findFirst().flatMap(candidate -> candidate.content())
                    .orElseThrow(() -> new IllegalStateException("Gemini returned no candidate content."));

            List<ModelPart> parts = new ArrayList<>();
            List<ModelToolCall> calls = new ArrayList<>();
            StringBuilder text = new StringBuilder();
            parts.addAll(toModelParts(candidateContent.parts().orElse(List.of()), calls, text, model));
            if (parts.isEmpty()) {
                throw new IllegalStateException("Gemini returned an empty candidate response.");
            }
            ModelMessage message = new ModelMessage("model", parts);
            return new ModelTurn(message, text.toString(), calls, model);
        } catch (ApiException exception) {
            int code = exception.code();
            Kind kind = code == 401 || code == 403 ? Kind.AUTHENTICATION
                    : code == 429 ? Kind.RATE_LIMIT
                    : code == 408 || code == 504 ? Kind.TIMEOUT : Kind.API_FAILURE;
            String message = switch (kind) {
                case AUTHENTICATION -> "Gemini authentication failed. Check GEMINI_API_KEY.";
                case RATE_LIMIT -> "Gemini API quota is exhausted for the configured models (the free tier allows "
                        + "about 20 requests per model per day). Try again later or use a billing-enabled API key.";
                case TIMEOUT -> "Gemini did not respond before the configured request timeout.";
                case API_FAILURE -> "Gemini returned an API error; no recommendation was accepted.";
            };
            throw gatewayException(kind, message, exception, exception.code(), exception.status(), exception.message());
        } catch (RuntimeException exception) {
            if (hasTimeoutCause(exception)) {
                throw gatewayException(Kind.TIMEOUT,
                        "Gemini did not respond before the configured request timeout.", exception,
                        null, null, exception.getMessage());
            }
            if (exception instanceof ModelGatewayException gatewayException) {
                throw gatewayException;
            }
            throw gatewayException(Kind.API_FAILURE,
                    "Gemini returned an invalid response or the request could not be completed.", exception,
                    null, null, exception.getMessage());
        }
    }

    static ModelGatewayException gatewayException(Kind kind, String userMessage, Throwable cause,
                                                  Integer httpStatus, String sdkStatus, String sdkMessage) {
        Map<String, Object> diagnostic = new LinkedHashMap<>();
        diagnostic.put("requestPhase", "GENERATE_CONTENT");
        diagnostic.put("exceptionClass", cause.getClass().getName());
        diagnostic.put("classification", kind.name());
        if (httpStatus != null) diagnostic.put("httpStatus", httpStatus);
        String category = sanitize(sdkStatus);
        if (category != null) diagnostic.put("sdkStatus", category);
        String message = sanitize(sdkMessage);
        if (message != null) diagnostic.put("sdkMessage", message);
        List<Map<String, Object>> causeChain = sanitizedCauseChain(cause);
        if (!causeChain.isEmpty()) diagnostic.put("causeChain", causeChain);
        return new ModelGatewayException(kind, userMessage, cause, diagnostic);
    }

    static String sanitize(String value) {
        return sanitize(value, 300);
    }

    private static String sanitize(String value, int maximumLength) {
        if (!StringUtils.hasText(value)) return null;
        String sanitized = value.replaceAll("(?i)(authorization\\s*[\\\"']?\\s*[:=]\\s*[\\\"']?)(bearer\\s+)?[^\\s,;\\\"']+", "$1[REDACTED]")
                .replaceAll("(?i)(api[_-]?key\\s*[\\\"']?\\s*[:=]\\s*[\\\"']?)[^\\s,;\\\"']+", "$1[REDACTED]")
                .replaceAll("(?i)(\\b(?:bearer)\\s+)[^\\s,;\\\"']+", "$1[REDACTED]")
                .replaceAll("(?i)([?&](?:key|api_key|access_token|token|authorization)=)[^&\\s]+", "$1[REDACTED]")
                .replaceAll("(?i)(https?://)[^\\s/@:]+:[^\\s/@]+@", "$1[REDACTED]@")
                .replaceAll("(?i)((?:password|client_secret|access_token|token)\\s*[\\\"']?\\s*[:=]\\s*[\\\"']?)[^\\s,;\\\"']+", "$1[REDACTED]");
        return sanitized.length() > maximumLength ? sanitized.substring(0, maximumLength) : sanitized;
    }

    static List<Map<String, Object>> sanitizedCauseChain(Throwable throwable) {
        List<Map<String, Object>> causes = new ArrayList<>();
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable current = throwable;
        for (int depth = 0; current != null && depth < 4 && visited.add(current); depth++) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("depth", depth);
            entry.put("exceptionClass", current.getClass().getName());
            String message = sanitize(current.getMessage(), 200);
            if (message != null) entry.put("message", message);
            causes.add(Map.copyOf(entry));
            current = current.getCause();
        }
        return List.copyOf(causes);
    }

    private Client client() {
        Client current = client;
        if (current != null) return current;
        synchronized (this) {
            if (client == null) {
                if (!StringUtils.hasText(properties.getGeminiApiKey())) {
                    throw new AgentConfigurationException(
                            "GEMINI_API_KEY is not configured. Set it before starting an agent run.");
                }
                properties.validateLimits();
                client = Client.builder()
                        .apiKey(properties.getGeminiApiKey())
                        .httpOptions(HttpOptions.builder()
                                .timeout(Math.toIntExact(properties.getRequestTimeout().toMillis()))
                                // Disable hidden SDK retries; generate() retries with a per-attempt timeout.
                                .retryOptions(HttpRetryOptions.builder().attempts(1).build())
                                .build())
                        .build();
            }
        }
        return client;
    }

    /**
     * Converts model response parts, keeping each part's opaque thought signature so it can be echoed back.
     * Parts flagged as thoughts (reasoning summaries) are dropped: hidden reasoning is never kept or shown.
     */
    static List<ModelPart> toModelParts(List<Part> sdkParts, List<ModelToolCall> calls, StringBuilder text,
                                        String model) {
        List<ModelPart> parts = new ArrayList<>();
        for (Part part : sdkParts) {
            if (part.thought().orElse(false)) continue;
            byte[] signature = part.thoughtSignature().orElse(null);
            String signatureModel = signature == null ? null : model;
            part.text().ifPresent(value -> {
                parts.add(ModelPart.text(value).withThoughtSignature(signature, signatureModel));
                text.append(value);
            });
            part.functionCall().ifPresent(functionCall -> {
                ModelToolCall call = toToolCall(functionCall);
                calls.add(call);
                parts.add(ModelPart.call(call).withThoughtSignature(signature, signatureModel));
            });
        }
        return parts;
    }

    private List<Content> toSdkContents(List<ModelMessage> conversation, String targetModel) {
        return conversation.stream().map(message -> {
            List<Part> parts = message.parts().stream().map(part -> toSdkPart(part, targetModel)).toList();
            return Content.builder().role(message.role()).parts(parts).build();
        }).toList();
    }

    /** Real signature for the model that issued it; the documented placeholder when the run switched models. */
    static byte[] signatureFor(ModelPart part, String targetModel) {
        if (part.thoughtSignature() == null) return null;
        return targetModel != null && targetModel.equals(part.signatureModel())
                ? part.thoughtSignature() : FOREIGN_SIGNATURE_PLACEHOLDER;
    }

    static Part toSdkPart(ModelPart part, String targetModel) {
        byte[] signature = signatureFor(part, targetModel);
        if (part.text() != null) {
            Part.Builder builder = Part.builder().text(part.text());
            if (signature != null) builder.thoughtSignature(signature);
            return builder.build();
        }
        if (part.toolCall() != null) {
            ModelToolCall call = part.toolCall();
            Part.Builder builder = Part.builder().functionCall(FunctionCall.builder()
                    .id(call.id()).name(call.name()).args(call.arguments()).build());
            if (signature != null) builder.thoughtSignature(signature);
            return builder.build();
        }
        if (part.toolResult() != null) {
            ModelToolResult result = part.toolResult();
            return Part.builder().functionResponse(FunctionResponse.builder()
                    .id(result.id()).name(result.name()).response(result.result()).build()).build();
        }
        throw new IllegalArgumentException("Conversation contains an empty model part.");
    }

    private static ModelToolCall toToolCall(FunctionCall functionCall) {
        String name = functionCall.name().orElse("");
        String id = functionCall.id().filter(StringUtils::hasText).orElseGet(() -> UUID.randomUUID().toString());
        Map<String, Object> arguments = functionCall.args().orElse(Map.of());
        return new ModelToolCall(id, name, arguments);
    }

    /**
     * The SDK applies HttpOptions.timeout as OkHttp's whole-call timeout. When it expires OkHttp throws
     * InterruptedIOException("timeout") (not SocketTimeoutException), which the SDK wraps in GenAiIOException.
     */
    static boolean hasTimeoutCause(Throwable throwable) {
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable cause = throwable;
        while (cause != null && visited.add(cause)) {
            if (cause instanceof SocketTimeoutException
                    || cause.getClass().getSimpleName().contains("Timeout")) return true;
            if (cause instanceof InterruptedIOException && cause.getMessage() != null
                    && cause.getMessage().toLowerCase(java.util.Locale.ROOT).contains("timeout")) return true;
            cause = cause.getCause();
        }
        return false;
    }
}
