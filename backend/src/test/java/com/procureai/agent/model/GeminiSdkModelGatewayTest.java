package com.procureai.agent.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Map;

import com.google.genai.errors.ApiException;
import com.procureai.agent.model.ModelGatewayException.Kind;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;

class GeminiSdkModelGatewayTest {

    @Test
    void retainsSanitizedApiDiagnosticsAndOriginalCause() {
        ApiException apiException = new ApiException(400, "INVALID_ARGUMENT",
                "Authorization: Bearer secret-token api_key=another-secret");

        ModelGatewayException exception = GeminiSdkModelGateway.gatewayException(Kind.API_FAILURE,
                "Gemini returned an API error; no recommendation was accepted.", apiException,
                apiException.code(), apiException.status(), apiException.message());

        assertThat(exception.getKind()).isEqualTo(Kind.API_FAILURE);
        assertThat(exception.getCause()).isSameAs(apiException);
        assertThat(exception.getDiagnostic()).containsEntry("httpStatus", 400)
                .containsEntry("sdkStatus", "INVALID_ARGUMENT")
                .containsEntry("requestPhase", "GENERATE_CONTENT")
                .containsEntry("exceptionClass", ApiException.class.getName())
                .containsEntry("classification", "API_FAILURE");
        assertThat((String) exception.getDiagnostic().get("sdkMessage"))
                .contains("[REDACTED]")
                .doesNotContain("secret-token", "another-secret");
        assertThat((List<Map<String, Object>>) exception.getDiagnostic().get("causeChain"))
                .first(InstanceOfAssertFactories.MAP).containsEntry("depth", 0).containsEntry("exceptionClass", ApiException.class.getName());
    }

    @Test
    void preservesClassificationForGenericRuntimeFailureWithoutHttpDetails() {
        IllegalStateException cause = new IllegalStateException("unexpected gateway state");

        ModelGatewayException exception = GeminiSdkModelGateway.gatewayException(Kind.TIMEOUT,
                "Gemini did not respond before the configured request timeout.", cause,
                null, null, cause.getMessage());

        assertThat(exception.getKind()).isEqualTo(Kind.TIMEOUT);
        assertThat(exception.getCause()).isSameAs(cause);
        assertThat(exception.getDiagnostic()).containsEntry("classification", "TIMEOUT")
                .containsEntry("exceptionClass", IllegalStateException.class.getName())
                .doesNotContainKeys("httpStatus", "sdkStatus");
    }

    @Test
    void omitsUnavailableStatusAndMessageAndSanitizesJsonAndQueryCredentials() {
        RuntimeException cause = new RuntimeException();

        ModelGatewayException exception = GeminiSdkModelGateway.gatewayException(Kind.API_FAILURE,
                "Gemini request failed.", cause, null, null, null);

        assertThat(exception.getDiagnostic()).doesNotContainKeys("httpStatus", "sdkStatus", "sdkMessage");
        assertThat(GeminiSdkModelGateway.sanitize("{\"authorization\":\"Bearer hidden\",\"apiKey\":\"secret\"} ?key=query-secret"))
                .contains("[REDACTED]")
                .doesNotContain("hidden", "secret", "query-secret");
    }

    @Test
    void capturesNestedSocketTimeoutAndConnectExceptions() {
        SocketTimeoutException timeout = new SocketTimeoutException("read timed out");
        ConnectException connect = new ConnectException("connection refused");
        timeout.initCause(connect);
        RuntimeException wrapper = new RuntimeException("request failed", timeout);

        List<Map<String, Object>> causes = GeminiSdkModelGateway.sanitizedCauseChain(wrapper);

        assertThat(causes).hasSize(3);
        assertThat(causes.get(1)).containsEntry("depth", 1)
                .containsEntry("exceptionClass", SocketTimeoutException.class.getName())
                .containsEntry("message", "read timed out");
        assertThat(causes.get(2)).containsEntry("depth", 2)
                .containsEntry("exceptionClass", ConnectException.class.getName())
                .containsEntry("message", "connection refused");
    }

    @Test
    void omitsMissingMessagesLimitsDepthAndRedactsEveryCause() {
        RuntimeException fifth = new RuntimeException("https://user:password@example.test?access_token=token-five");
        RuntimeException fourth = new RuntimeException("Authorization: Bearer token-four", fifth);
        RuntimeException third = new RuntimeException("apiKey=token-three", fourth);
        RuntimeException second = new RuntimeException("", third);
        RuntimeException first = new RuntimeException("token=token-one", second);

        List<Map<String, Object>> causes = GeminiSdkModelGateway.sanitizedCauseChain(first);

        assertThat(causes).hasSize(4);
        assertThat(causes.get(1)).containsEntry("exceptionClass", RuntimeException.class.getName())
                .doesNotContainKey("message");
        assertThat(causes.toString()).contains("[REDACTED]")
                .doesNotContain("token-one", "token-three", "token-four", "password", "token-five");
    }

    @Test
    void stopsAtCircularCauseWithoutDuplicateEntries() {
        CircularException circular = new CircularException("Bearer circular-secret");

        List<Map<String, Object>> causes = GeminiSdkModelGateway.sanitizedCauseChain(circular);

        assertThat(causes).singleElement(InstanceOfAssertFactories.MAP).containsEntry("depth", 0)
                .containsEntry("exceptionClass", CircularException.class.getName());
        assertThat(causes.toString()).doesNotContain("circular-secret");
    }

    @Test
    void classifiesOkHttpCallTimeoutWrappedBySdkAsTimeout() {
        // Mirrors the SDK: GenAiIOException("Failed to execute HTTP request.") wrapping OkHttp's call timeout.
        RuntimeException sdkWrapper = new RuntimeException("Failed to execute HTTP request.",
                new java.io.InterruptedIOException("timeout"));

        assertThat(GeminiSdkModelGateway.hasTimeoutCause(sdkWrapper)).isTrue();
        assertThat(GeminiSdkModelGateway.hasTimeoutCause(new RuntimeException("x", new ConnectException("refused"))))
                .isFalse();
        assertThat(GeminiSdkModelGateway.hasTimeoutCause(new CircularException("loop"))).isFalse();
    }

    @Test
    void roundTripsThoughtSignatureOnFunctionCallsAndDropsThoughtParts() {
        byte[] signature = {1, 2, 3};
        com.google.genai.types.Part callPart = com.google.genai.types.Part.builder()
                .functionCall(com.google.genai.types.FunctionCall.builder().id("c1").name("get_inventory")
                        .args(Map.of()).build())
                .thoughtSignature(signature).build();
        com.google.genai.types.Part thoughtPart = com.google.genai.types.Part.builder()
                .text("hidden reasoning summary").thought(true).build();
        List<ModelToolCall> calls = new java.util.ArrayList<>();
        StringBuilder text = new StringBuilder();

        List<ModelPart> parts = GeminiSdkModelGateway.toModelParts(List.of(thoughtPart, callPart), calls, text);

        assertThat(parts).singleElement().satisfies(part -> assertThat(part.thoughtSignature()).containsExactly(1, 2, 3));
        assertThat(text.toString()).doesNotContain("hidden reasoning");
        assertThat(calls).extracting(ModelToolCall::name).containsExactly("get_inventory");
        assertThat(GeminiSdkModelGateway.toSdkPart(parts.get(0)).thoughtSignature()).hasValueSatisfying(
                value -> assertThat(value).containsExactly(1, 2, 3));
    }

    @Test
    void retriesOnlyTransientFailures() {
        ModelGatewayException timeout = GeminiSdkModelGateway.gatewayException(Kind.TIMEOUT, "t",
                new RuntimeException(), null, null, null);
        ModelGatewayException overloaded = GeminiSdkModelGateway.gatewayException(Kind.API_FAILURE, "o",
                new RuntimeException(), 503, "UNAVAILABLE", "high demand");
        ModelGatewayException badRequest = GeminiSdkModelGateway.gatewayException(Kind.API_FAILURE, "b",
                new RuntimeException(), 400, "INVALID_ARGUMENT", "bad");
        ModelGatewayException auth = GeminiSdkModelGateway.gatewayException(Kind.AUTHENTICATION, "a",
                new RuntimeException(), 403, "PERMISSION_DENIED", "denied");

        assertThat(GeminiSdkModelGateway.isTransient(timeout)).isTrue();
        assertThat(GeminiSdkModelGateway.isTransient(overloaded)).isTrue();
        assertThat(GeminiSdkModelGateway.isTransient(badRequest)).isFalse();
        assertThat(GeminiSdkModelGateway.isTransient(auth)).isFalse();
        ModelGatewayException quota = GeminiSdkModelGateway.gatewayException(Kind.RATE_LIMIT, "q",
                new RuntimeException(), 429, "RESOURCE_EXHAUSTED", "quota");
        assertThat(GeminiSdkModelGateway.isTransient(quota)).isFalse();
    }

    private static final class CircularException extends RuntimeException {
        private CircularException(String message) {
            super(message);
        }

        @Override
        public synchronized Throwable getCause() {
            return this;
        }
    }
}
