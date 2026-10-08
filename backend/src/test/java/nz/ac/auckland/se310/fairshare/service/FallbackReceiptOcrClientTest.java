package nz.ac.auckland.se310.fairshare.service;

import nz.ac.auckland.se310.fairshare.dto.ReceiptExtractionResponse;
import nz.ac.auckland.se310.fairshare.dto.ReceiptItemResponse;
import nz.ac.auckland.se310.fairshare.exception.ReceiptExtractionException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class FallbackReceiptOcrClientTest {

    @Test
    void returnsGeminiResultWithoutCallingTesseract() {
        ReceiptExtractionResponse geminiResult = receipt("Gemini item");
        AtomicBoolean fallbackCalled = new AtomicBoolean(false);
        FallbackReceiptOcrClient client = new FallbackReceiptOcrClient(
                (image, mediaType) -> geminiResult,
                (image, mediaType) -> {
                    fallbackCalled.set(true);
                    return receipt("Fallback item");
                });

        ReceiptExtractionResponse result = client.extract(new byte[] {1}, "image/png");

        assertThat(result).isEqualTo(geminiResult);
        assertThat(fallbackCalled).isFalse();
    }

    @Test
    void usesTesseractWhenGeminiFails() {
        ReceiptExtractionResponse fallbackResult = receipt("Fallback item");
        FallbackReceiptOcrClient client = new FallbackReceiptOcrClient(
                (image, mediaType) -> {
                    throw new ReceiptExtractionException("Gemini unavailable");
                },
                (image, mediaType) -> fallbackResult);

        ReceiptExtractionResponse result = client.extract(new byte[] {1}, "image/jpeg");

        assertThat(result).isEqualTo(fallbackResult);
    }

    private ReceiptExtractionResponse receipt(String description) {
        return new ReceiptExtractionResponse(
                List.of(new ReceiptItemResponse(description, new BigDecimal("4.50"))),
                new BigDecimal("4.50"));
    }
}
