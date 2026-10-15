package nz.ac.auckland.se310.fairshare.service;

import nz.ac.auckland.se310.fairshare.dto.ReceiptExtractionResponse;
import nz.ac.auckland.se310.fairshare.exception.ReceiptExtractionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/** Uses Gemini first and falls back to local Tesseract OCR if Gemini cannot extract a receipt. */
@Component
@Primary
public class FallbackReceiptOcrClient implements ReceiptOcrClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(FallbackReceiptOcrClient.class);

    private final ReceiptOcrClient primary;
    private final ReceiptOcrClient fallback;

    @Autowired
    public FallbackReceiptOcrClient(
            GeminiReceiptOcrClient primary,
            TesseractReceiptOcrClient fallback) {
        this((ReceiptOcrClient) primary, fallback);
    }

    FallbackReceiptOcrClient(ReceiptOcrClient primary, ReceiptOcrClient fallback) {
        this.primary = primary;
        this.fallback = fallback;
    }

    @Override
    public ReceiptExtractionResponse extract(byte[] image, String mediaType) {
        try {
            return primary.extract(image, mediaType);
        } catch (ReceiptExtractionException ex) {
            LOGGER.warn(
                    "Gemini receipt extraction failed; using Tesseract fallback ({})",
                    ex.getMessage());
            return fallback.extract(image, mediaType);
        }
    }
}
