package nz.ac.auckland.se310.fairshare.service;

import nz.ac.auckland.se310.fairshare.dto.ReceiptExtractionResponse;
import nz.ac.auckland.se310.fairshare.exception.ReceiptExtractionException;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeminiReceiptOcrClientTest {

    @Test
    void extractsStructuredItemsAndTotal() {
        GeminiReceiptOcrClient client = clientReturning(200, """
                {"candidates":[{"content":{"parts":[{"text":"{\\\"items\\\":[{\\\"description\\\":\\\"Big Mac\\\",\\\"price\\\":9.20},{\\\"description\\\":\\\"Fries\\\",\\\"price\\\":4.50}],\\\"total\\\":13.70}"}]}}]}
                """);

        ReceiptExtractionResponse result = client.extract(new byte[] {1, 2, 3}, "image/jpeg");

        assertThat(result.items()).extracting(item -> item.description())
                .containsExactly("Big Mac", "Fries");
        assertThat(result.items()).extracting(item -> item.price().toPlainString())
                .containsExactly("9.20", "4.50");
        assertThat(result.total().toPlainString()).isEqualTo("13.70");
    }

    @Test
    void rejectsMissingConfigurationOrUnusableModelOutput() {
        GeminiReceiptOcrClient missingKey = new GeminiReceiptOcrClient(
                "", "gemini-3.5-flash-lite", Duration.ofSeconds(1),
                (apiKey, model, request, timeout) -> {
                    throw new AssertionError("Gateway should not be called");
                });
        GeminiReceiptOcrClient emptyReceipt = clientReturning(200, """
                {"candidates":[{"content":{"parts":[{"text":"{\\\"items\\\":[],\\\"total\\\":0}"}]}}]}
                """);

        assertThatThrownBy(() -> missingKey.extract(new byte[] {1}, "image/png"))
                .isInstanceOf(ReceiptExtractionException.class)
                .hasMessageContaining("not configured");
        assertThatThrownBy(() -> emptyReceipt.extract(new byte[] {1}, "image/png"))
                .isInstanceOf(ReceiptExtractionException.class);
    }

    @Test
    void rejectsApiErrorsSoTheCompositeCanFallback() {
        GeminiReceiptOcrClient client = clientReturning(429, "quota exceeded");

        assertThatThrownBy(() -> client.extract(new byte[] {1}, "image/png"))
                .isInstanceOf(ReceiptExtractionException.class)
                .hasMessageContaining("429");
    }

    private GeminiReceiptOcrClient clientReturning(int status, String body) {
        return new GeminiReceiptOcrClient(
                "test-key",
                "gemini-3.5-flash-lite",
                Duration.ofSeconds(1),
                (apiKey, model, request, timeout) -> {
                    assertThat(apiKey).isEqualTo("test-key");
                    assertThat(request).contains("inlineData", "responseSchema");
                    return new GeminiReceiptOcrClient.GeminiHttpResponse(status, body);
                });
    }
}
