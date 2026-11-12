package com.procureai.agent.model;

import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

    private final AgentProperties properties;
    private volatile Client client;

    public GeminiSdkModelGateway(AgentProperties properties) {
        this.properties = properties;
    }

    @Override
    public ModelTurn generate(String systemInstruction, List<ModelMessage> conversation,
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
                    properties.getModel(), toSdkContents(conversation), config);
            Content candidateContent = response.candidates().orElseThrow(() ->
                    new IllegalStateException("Gemini returned no candidate content."))
                    .stream().findFirst().flatMap(candidate -> candidate.content())
                    .orElseThrow(() -> new IllegalStateException("Gemini returned no candidate content."));

            List<ModelPart> parts = new ArrayList<>();
            List<ModelToolCall> calls = new ArrayList<>();
            StringBuilder text = new StringBuilder();
            for (Part part : candidateContent.parts().orElse(List.of())) {
                part.text().ifPresent(value -> {
                    parts.add(ModelPart.text(value));
                    text.append(value);
                });
                part.functionCall().ifPresent(functionCall -> {
                    ModelToolCall call = toToolCall(functionCall);
                    calls.add(call);
                    parts.add(ModelPart.call(call));
                });
            }
            if (parts.isEmpty()) {
                throw new IllegalStateException("Gemini returned an empty candidate response.");
            }
            ModelMessage message = new ModelMessage("model", parts);
            return new ModelTurn(message, text.toString(), calls);
        } catch (ApiException exception) {
            int code = exception.code();
            Kind kind = code == 401 || code == 403 ? Kind.AUTHENTICATION
                    : code == 429 ? Kind.RATE_LIMIT
                    : code == 408 || code == 504 ? Kind.TIMEOUT : Kind.API_FAILURE;
            String message = switch (kind) {
                case AUTHENTICATION -> "Gemini authentication failed. Check GEMINI_API_KEY.";
                case RATE_LIMIT -> "Gemini rate limit was reached. Try again later.";
                case TIMEOUT -> "Gemini did not respond before the configured request timeout.";
                case API_FAILURE -> "Gemini returned an API error; no recommendation was accepted.";
            };
            throw new ModelGatewayException(kind, message, exception);
        } catch (RuntimeException exception) {
            if (hasTimeoutCause(exception)) {
                throw new ModelGatewayException(Kind.TIMEOUT,
                        "Gemini did not respond before the configured request timeout.", exception);
            }
            if (exception instanceof ModelGatewayException gatewayException) {
                throw gatewayException;
            }
            throw new ModelGatewayException(Kind.API_FAILURE,
                    "Gemini returned an invalid response or the request could not be completed.", exception);
        }
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
                                .build())
                        .build();
            }
        }
        return client;
    }

    private List<Content> toSdkContents(List<ModelMessage> conversation) {
        return conversation.stream().map(message -> {
            List<Part> parts = message.parts().stream().map(this::toSdkPart).toList();
            return Content.builder().role(message.role()).parts(parts).build();
        }).toList();
    }

    private Part toSdkPart(ModelPart part) {
        if (part.text() != null) return Part.fromText(part.text());
        if (part.toolCall() != null) {
            ModelToolCall call = part.toolCall();
            return Part.builder().functionCall(FunctionCall.builder()
                    .id(call.id()).name(call.name()).args(call.arguments()).build()).build();
        }
        if (part.toolResult() != null) {
            ModelToolResult result = part.toolResult();
            return Part.builder().functionResponse(FunctionResponse.builder()
                    .id(result.id()).name(result.name()).response(result.result()).build()).build();
        }
        throw new IllegalArgumentException("Conversation contains an empty model part.");
    }

    private ModelToolCall toToolCall(FunctionCall functionCall) {
        String name = functionCall.name().orElse("");
        String id = functionCall.id().filter(StringUtils::hasText).orElseGet(() -> UUID.randomUUID().toString());
        Map<String, Object> arguments = functionCall.args().orElse(Map.of());
        return new ModelToolCall(id, name, arguments);
    }

    private boolean hasTimeoutCause(Throwable throwable) {
        Throwable cause = throwable;
        while (cause != null) {
            if (cause instanceof SocketTimeoutException
                    || cause.getClass().getSimpleName().contains("Timeout")) return true;
            cause = cause.getCause();
        }
        return false;
    }
}
