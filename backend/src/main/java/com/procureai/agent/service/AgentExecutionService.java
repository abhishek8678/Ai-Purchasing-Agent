package com.procureai.agent.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.procureai.agent.audit.AgentAuditService;
import com.procureai.agent.config.AgentProperties;
import com.procureai.agent.dto.AgentRunRequest;
import com.procureai.agent.dto.AgentRunResponse;
import com.procureai.agent.dto.PurchasingRecommendation;
import com.procureai.agent.exception.AgentConfigurationException;
import com.procureai.agent.model.GeminiModelGateway;
import com.procureai.agent.model.ModelGatewayException;
import com.procureai.agent.model.ModelMessage;
import com.procureai.agent.model.ModelPart;
import com.procureai.agent.model.ModelToolCall;
import com.procureai.agent.model.ModelToolResult;
import com.procureai.agent.model.ModelTurn;
import com.procureai.agent.tool.AgentToolContext;
import com.procureai.agent.tool.AgentToolOutput;
import com.procureai.agent.tool.AgentToolRegistry;
import com.procureai.domain.entity.AgentRun;
import com.procureai.domain.entity.Product;
import com.procureai.domain.entity.Warehouse;
import com.procureai.domain.enums.AgentEventType;
import com.procureai.domain.enums.AgentRunStatus;
import com.procureai.domain.enums.PurchaseDecision;
import com.procureai.repository.AgentRunRepository;
import com.procureai.service.EvidenceLookupService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AgentExecutionService {

    private static final String SYSTEM_INSTRUCTION = """
            You are ProcureAI, a purchasing analyst. Investigate the requested purchase using only evidence returned by the available tools and the run request. Decide which tools are useful; you may call tools in any order, call only a subset, request additional evidence after reviewing results, or finish without using every tool. Tool calls are executed by the application, not by you.

            Compare supplier availability, unit price, lead time, MOQ and pack size when the evidence supports it. Consider inventory, forecasts, confirmed and uncertain incoming supply, budget, storage, and explicit constraints as relevant. Explain uncertainty, stale or missing evidence, risks, and unresolved questions. Never invent facts or identifiers. Do not treat a single constraint failure as an automatic final decision if more evidence or an alternative could resolve it. Do not create or submit purchase orders, change inventory, seek approval, or claim execution. Do not provide or request hidden chain-of-thought. Provide only a concise rationale supported by the evidence.

            When ready, return exactly one JSON object, with no Markdown fences or text outside it, matching these fields: decision (ACCEPT, MODIFY, REJECT, or INVESTIGATE), originalRecommendedQuantity (integer from the run request), proposedQuantity (positive integer for ACCEPT or MODIFY; null for REJECT or INVESTIGATE), selectedSupplierId (integer or null), selectedSupplierProductId (integer or null), recommendationSummary (concise string), evidenceReferences (array of exact evidenceReference values returned by tools; when you select a supplier offer, include that offer's exact supplier-offer reference from offerEvidenceReferencesBySupplierProductId), keySupportingFacts (array of concise factual strings), risks (array of strings), unresolvedQuestions (array of strings), constraintEvaluationReferences (array of exact constraint evidence references returned by evaluate_purchase_constraints), confidence (number from 0 to 1 or null). Before returning ACCEPT or MODIFY you must call evaluate_purchase_constraints for exactly the selected supplierProductId and proposed quantity, every constraint must pass, and that evaluation's reference must be in constraintEvaluationReferences; otherwise choose MODIFY with a feasible quantity, REJECT, or INVESTIGATE. ACCEPT keeps the original quantity. MODIFY proposes a different positive quantity. REJECT and INVESTIGATE do not claim a proposed purchase quantity. Use INVESTIGATE when evidence is insufficient, inconsistent, stale, or needs clarification.
            """;

    private final EvidenceLookupService lookupService;
    private final AgentProperties properties;
    private final GeminiModelGateway modelGateway;
    private final AgentToolRegistry toolRegistry;
    private final AgentAuditService auditService;
    private final AgentRecommendationValidator recommendationValidator;
    private final AgentRunQueryService queryService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AgentExecutionService(EvidenceLookupService lookupService, AgentProperties properties,
                                 GeminiModelGateway modelGateway, AgentToolRegistry toolRegistry,
                                 AgentAuditService auditService,
                                 AgentRecommendationValidator recommendationValidator,
                                 AgentRunQueryService queryService, ObjectMapper objectMapper, Clock clock) {
        this.lookupService = lookupService;
        this.properties = properties;
        this.modelGateway = modelGateway;
        this.toolRegistry = toolRegistry;
        this.auditService = auditService;
        this.recommendationValidator = recommendationValidator;
        this.queryService = queryService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public AgentRunResponse start(AgentRunRequest request) {
        properties.validateLimits();
        if (!StringUtils.hasText(properties.getGeminiApiKey())) {
            throw new AgentConfigurationException(
                    "GEMINI_API_KEY is not configured. Set it before starting an agent run.");
        }
        Product product = lookupService.requireProduct(request.productSku());
        Warehouse warehouse = lookupService.requireWarehouse(request.warehouseCode());
        AgentRun run = auditService.start(product, warehouse, request);
        int[] sequence = {1};
        try {
            return execute(run, request, sequence);
        } catch (RuntimeException exception) {
            // Persistence failures are not represented as successful agent outcomes.
            try {
                auditService.finish(run.getId(), ++sequence[0], AgentRunStatus.FAILED, null, null,
                        "The investigation stopped because an internal processing or persistence error occurred.",
                        AgentEventType.RUN_FAILED, null,
                        Map.of("errorCode", "INTERNAL_AGENT_FAILURE"));
            } catch (RuntimeException ignored) {
                // If persistence is unavailable, there is no reliable audit write to claim.
            }
            throw exception;
        }
    }

    private AgentRunResponse execute(AgentRun run, AgentRunRequest request, int[] sequence) {
        UUID runId = run.getId();
        List<ModelMessage> conversation = new ArrayList<>();
        Set<String> evidenceReferences = new HashSet<>();
        Set<String> constraintReferences = new HashSet<>();
        Map<Long, Long> supplierOffers = new LinkedHashMap<>();
        Map<String, ConstraintCheck> constraintChecks = new HashMap<>();
        Map<String, Integer> identicalCalls = new HashMap<>();
        int toolExecutions = 0;
        conversation.add(new ModelMessage("user", List.of(ModelPart.text(initialPrompt(request)))));

        for (int round = 1; round <= properties.getMaxInteractionRounds(); round++) {
            auditService.append(runId, ++sequence[0], AgentEventType.MODEL_REQUESTED, null,
                    Map.of("model", properties.getModel(), "round", round), null,
                    "Sent the current evidence conversation to Gemini.");
            ModelTurn turn;
            try {
                turn = modelGateway.generate(SYSTEM_INSTRUCTION, List.copyOf(conversation), toolRegistry.definitions());
            } catch (ModelGatewayException exception) {
                return failRun(runId, sequence, "Gemini request failed: " + safeModelMessage(exception),
                        exception.getKind().name(), exception.getDiagnostic());
            } catch (AgentConfigurationException exception) {
                return failRun(runId, sequence, exception.getMessage(), "GEMINI_CONFIGURATION_ERROR");
            }

            if (turn == null || turn.modelMessage() == null) {
                return needsInput(runId, sequence, "Gemini returned no usable model response.",
                        Map.of("errorCode", "EMPTY_MODEL_RESPONSE"));
            }
            if (turn.toolCalls().isEmpty()) {
                return finishRecommendation(runId, request, sequence, turn.text(), evidenceReferences,
                        constraintReferences, supplierOffers, constraintChecks);
            }

            conversation.add(turn.modelMessage());
            List<ModelPart> toolResponseParts = new ArrayList<>();
            for (ModelToolCall call : turn.toolCalls()) {
                Map<String, Object> requestAudit = Map.of("callId", call.id(), "arguments", call.arguments());
                auditService.append(runId, ++sequence[0], AgentEventType.TOOL_SELECTED, call.name(),
                        requestAudit, null, "Gemini selected an evidence tool.");

                String callFingerprint = call.name() + ":" + canonicalArguments(call.arguments());
                int repeatedCount = identicalCalls.merge(callFingerprint, 1, Integer::sum);
                if (toolExecutions >= properties.getMaxToolExecutions()) {
                    return limitReached(runId, sequence, call, requestAudit,
                            "The configured maximum number of tool executions was reached.", "MAX_TOOL_EXECUTIONS");
                }
                if (repeatedCount > properties.getMaxRepeatedIdenticalToolCalls()) {
                    return limitReached(runId, sequence, call, requestAudit,
                            "The same tool call was repeated more times than the configured limit.",
                            "REPEATED_IDENTICAL_TOOL_CALL");
                }

                toolExecutions++;
                auditService.append(runId, ++sequence[0], AgentEventType.TOOL_EXECUTION_STARTED, call.name(),
                        requestAudit, null, "Java started the requested tool execution.");
                AgentToolOutput output = toolRegistry.execute(call.name(), call.arguments(),
                        new AgentToolContext(runId, request, toolExecutions));
                Map<String, Object> resultAudit = new LinkedHashMap<>();
                resultAudit.put("callId", call.id());
                resultAudit.putAll(output.asModelResult());
                auditService.append(runId, ++sequence[0], output.success()
                                ? AgentEventType.TOOL_EXECUTION_COMPLETED : AgentEventType.TOOL_EXECUTION_FAILED,
                        call.name(), requestAudit, resultAudit,
                        output.success() ? "Java completed the requested tool." : "Java tool execution failed.");
                if (output.success()) {
                    evidenceReferences.addAll(output.evidenceReferences());
                    supplierOffers.putAll(output.supplierOffers());
                    output.evidenceReferences().forEach(reference -> {
                        if (reference.startsWith("constraints:")) constraintReferences.add(reference);
                    });
                    recordConstraintCheck(output, constraintChecks);
                    auditService.append(runId, ++sequence[0], AgentEventType.EVIDENCE_OBSERVED, call.name(),
                            Map.of("callId", call.id()), Map.of("evidenceReferences", output.evidenceReferences()),
                            "Tool returned evidence references.");
                }
                toolResponseParts.add(ModelPart.result(new ModelToolResult(call.id(), call.name(), output.asModelResult())));
            }
            conversation.add(new ModelMessage("user", toolResponseParts));
        }
        return needsInput(runId, sequence, "The configured maximum number of model interaction rounds was reached.",
                Map.of("errorCode", "MAX_INTERACTION_ROUNDS"));
    }

    private AgentRunResponse finishRecommendation(UUID runId, AgentRunRequest request, int[] sequence,
                                                   String modelText, Set<String> evidenceReferences,
                                                   Set<String> constraintReferences,
                                                   Map<Long, Long> supplierOffers,
                                                   Map<String, ConstraintCheck> constraintChecks) {
        PurchasingRecommendation recommendation;
        try {
            if (!StringUtils.hasText(modelText)) throw new IllegalArgumentException("Final response is empty.");
            recommendation = objectMapper.readValue(extractJsonObject(modelText), PurchasingRecommendation.class);
        } catch (Exception exception) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("errorCode", "INVALID_STRUCTURED_RECOMMENDATION");
            if (StringUtils.hasText(modelText)) {
                // The final answer text (not reasoning), truncated, so a parse failure can be diagnosed.
                details.put("modelTextPreview", modelText.length() > 400 ? modelText.substring(0, 400) : modelText);
            }
            return needsInput(runId, sequence, "Gemini did not return a valid structured recommendation.", details);
        }
        List<String> validationErrors = new ArrayList<>(recommendationValidator.validate(recommendation, request,
                evidenceReferences, constraintReferences, supplierOffers));
        validationErrors.addAll(recommendationValidator.validateConstraintCoverage(recommendation, constraintChecks));
        if (!validationErrors.isEmpty()) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("errorCode", "RECOMMENDATION_VALIDATION_FAILED");
            details.put("validationErrors", validationErrors);
            // Keep the rejected proposal so reviewers can see what the model suggested and why Java refused it.
            details.put("rejectedProposal", recommendation);
            return needsInput(runId, sequence, "Gemini's structured recommendation failed validation.", details);
        }

        auditService.append(runId, ++sequence[0], AgentEventType.DECISION_PROPOSED, null, null,
                recommendation, "Gemini returned a structured purchasing recommendation.");
        AgentRunStatus status = recommendation.decision() == PurchaseDecision.INVESTIGATE
                ? AgentRunStatus.NEEDS_INPUT : AgentRunStatus.COMPLETED;
        AgentEventType finalEvent = status == AgentRunStatus.NEEDS_INPUT
                ? AgentEventType.NEEDS_INPUT : AgentEventType.RUN_COMPLETED;
        auditService.finish(runId, ++sequence[0], status, recommendation.decision(),
                recommendation.proposedQuantity(), recommendation.recommendationSummary(), finalEvent,
                null, null);
        return queryService.getRun(runId);
    }

    private AgentRunResponse failRun(UUID runId, int[] sequence, String summary, String errorCode) {
        return failRun(runId, sequence, summary, errorCode, Map.of());
    }

    private AgentRunResponse failRun(UUID runId, int[] sequence, String summary, String errorCode,
                                     Map<String, Object> diagnostic) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("errorCode", errorCode);
        details.putAll(diagnostic);
        auditService.finish(runId, ++sequence[0], AgentRunStatus.FAILED, null, null, summary,
                AgentEventType.RUN_FAILED, null, details);
        return queryService.getRun(runId);
    }

    private AgentRunResponse needsInput(UUID runId, int[] sequence, String summary, Object details) {
        auditService.finish(runId, ++sequence[0], AgentRunStatus.NEEDS_INPUT, null, null, summary,
                AgentEventType.NEEDS_INPUT, null, details);
        return queryService.getRun(runId);
    }

    private AgentRunResponse limitReached(UUID runId, int[] sequence, ModelToolCall call,
                                          Map<String, Object> requestAudit, String message, String code) {
        auditService.append(runId, ++sequence[0], AgentEventType.TOOL_EXECUTION_FAILED, call.name(),
                requestAudit, Map.of("callId", call.id(), "ok", false,
                        "error", Map.of("code", code, "message", message)),
                "Tool request was not executed because a safety limit was reached.");
        return needsInput(runId, sequence, message, Map.of("errorCode", code));
    }

    private String initialPrompt(AgentRunRequest request) {
        StringBuilder prompt = new StringBuilder("Investigate this purchasing proposal using the available evidence tools.\n")
                .append("Product SKU: ").append(request.productSku()).append('\n')
                .append("Warehouse code: ").append(request.warehouseCode()).append('\n')
                .append("Original recommended quantity: ").append(request.recommendedPurchaseUnits()).append(" EACH\n")
                .append("Inclusive planning horizon: ").append(request.planningStartDate()).append(" through ")
                .append(request.planningEndDate()).append("\nBudget date: ").append(LocalDate.now(clock)).append('\n');
        if (StringUtils.hasText(request.userInstruction())) {
            prompt.append("Additional user instruction: ").append(request.userInstruction());
        }
        return prompt.toString();
    }

    @SuppressWarnings("unchecked")
    private static void recordConstraintCheck(AgentToolOutput output, Map<String, ConstraintCheck> checks) {
        if (!(output.asModelResult().get("result") instanceof Map<?, ?> raw)) return;
        Map<String, Object> result = (Map<String, Object>) raw;
        if (result.get("evidenceReference") instanceof String reference && reference.startsWith("constraints:")
                && result.get("evaluatedSupplierProductId") instanceof Number offer
                && result.get("evaluatedUnits") instanceof Number units
                && result.get("allConstraintsPassed") instanceof Boolean passed) {
            checks.put(reference, new ConstraintCheck(offer.longValue(), units.longValue(), passed));
        }
    }

    /**
     * Models sometimes wrap the requested JSON in Markdown fences or add a sentence around it. Extract the outermost
     * JSON object; the result is still parsed strictly and then validated like any other recommendation.
     */
    static String extractJsonObject(String modelText) {
        String text = modelText.trim();
        if (text.startsWith("```")) {
            text = text.replaceFirst("^```[a-zA-Z]*\\s*", "").replaceFirst("\\s*```\\s*$", "");
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        return start >= 0 && end > start ? text.substring(start, end + 1) : text;
    }

    private String canonicalArguments(Map<String, Object> arguments) {
        try {
            return objectMapper.writeValueAsString(new TreeMap<>(arguments));
        } catch (Exception exception) {
            return String.valueOf(arguments);
        }
    }

    private String safeModelMessage(ModelGatewayException exception) {
        return exception.getMessage() == null ? "the model request could not be completed." : exception.getMessage();
    }
}
