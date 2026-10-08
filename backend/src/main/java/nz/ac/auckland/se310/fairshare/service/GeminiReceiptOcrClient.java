package nz.ac.auckland.se310.fairshare.service;

import nz.ac.auckland.se310.fairshare.dto.ReceiptExtractionResponse;
import nz.ac.auckland.se310.fairshare.dto.ReceiptItemResponse;
import nz.ac.auckland.se310.fairshare.exception.ReceiptExtractionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Extracts structured receipt data using Gemini's multimodal API. */
@Component
public class GeminiReceiptOcrClient implements ReceiptOcrClient {

    private static final String EXTRACTION_FAILURE =
            "Gemini could not extract this receipt.";
    private static final Pattern SAFE_MODEL_NAME = Pattern.compile("[A-Za-z0-9._-]+");
    private static final int MAX_RECEIPT_LINES = 100;
    private static final int MAX_DESCRIPTION_LENGTH = 160;
    private static final String PROMPT = """
            Read this receipt image and extract its purchase details.

            Rules:
            - items must contain only actual purchased positive-price line items.
            - Do not include headings, dates, order numbers, tax, GST, subtotal, total,
              payment methods, tendered amounts, cash, change, or loyalty metadata as items.
            - Ignore discount, coupon, deal reduction, or negative-price lines.
            - total must be the final amount paid.
            - Preserve quantities in item descriptions when they are printed.
            - Do not invent missing products or prices.
            - If the receipt is not legible, return an empty items array and total 0.
            """;

    private final String apiKey;
    private final String model;
    private final Duration timeout;
    private final GeminiGateway gateway;
    private final ObjectMapper objectMapper;

    @Autowired
    public GeminiReceiptOcrClient(
            @Value("${gemini.api-key:}") String apiKey,
            @Value("${gemini.model:gemini-3.5-flash-lite}") String model,
            @Value("${gemini.timeout-seconds:35}") long timeoutSeconds) {
        this(apiKey, model, Duration.ofSeconds(timeoutSeconds), GeminiReceiptOcrClient::send);
    }

    GeminiReceiptOcrClient(
            String apiKey,
            String model,
            Duration timeout,
            GeminiGateway gateway) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model == null ? "" : model.trim();
        this.timeout = timeout;
        this.gateway = gateway;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public ReceiptExtractionResponse extract(byte[] image, String mediaType) {
        if (apiKey.isBlank()) {
            throw new ReceiptExtractionException("Gemini is not configured.");
        }
        if (!SAFE_MODEL_NAME.matcher(model).matches()) {
            throw new ReceiptExtractionException("The configured Gemini model is invalid.");
        }

        try {
            String requestBody = createRequest(image, mediaType);
            GeminiHttpResponse response = gateway.generate(apiKey, model, requestBody, timeout);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ReceiptExtractionException(
                        "Gemini returned HTTP " + response.statusCode() + ".");
            }
            return parseResponse(response.body());
        } catch (ReceiptExtractionException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ReceiptExtractionException(EXTRACTION_FAILURE, ex);
        } catch (IOException | RuntimeException ex) {
            throw new ReceiptExtractionException(EXTRACTION_FAILURE, ex);
        }
    }

    private String createRequest(byte[] image, String mediaType) throws JacksonException {
        Map<String, Object> itemSchema = Map.of(
                "type", "OBJECT",
                "properties", Map.of(
                        "description", Map.of("type", "STRING"),
                        "price", Map.of("type", "NUMBER")),
                "required", List.of("description", "price"));
        Map<String, Object> receiptSchema = Map.of(
                "type", "OBJECT",
                "properties", Map.of(
                        "items", Map.of("type", "ARRAY", "items", itemSchema),
                        "total", Map.of("type", "NUMBER")),
                "required", List.of("items", "total"));

        Map<String, Object> inlineData = Map.of(
                "mimeType", mediaType,
                "data", Base64.getEncoder().encodeToString(image));
        List<Map<String, Object>> parts = List.of(
                Map.of("text", PROMPT),
                Map.of("inlineData", inlineData));

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("contents", List.of(Map.of("role", "user", "parts", parts)));
        request.put("generationConfig", Map.of(
                "temperature", 0,
                "responseMimeType", "application/json",
                "responseSchema", receiptSchema));
        return objectMapper.writeValueAsString(request);
    }

    private ReceiptExtractionResponse parseResponse(String responseBody) throws JacksonException {
        JsonNode response = objectMapper.readTree(responseBody);
        String generatedJson = response.path("candidates")
                .path(0)
                .path("content")
                .path("parts")
                .path(0)
                .path("text")
                .asText("")
                .trim();
        if (generatedJson.isBlank()) {
            throw new ReceiptExtractionException(EXTRACTION_FAILURE);
        }

        JsonNode receipt = objectMapper.readTree(stripCodeFence(generatedJson));
        List<ReceiptItemResponse> items = new ArrayList<>();
        readLines(receipt.path("items"), items);

        BigDecimal total = readAmount(receipt.path("total"));
        if (items.isEmpty() || total == null || total.signum() <= 0) {
            throw new ReceiptExtractionException(EXTRACTION_FAILURE);
        }

        return new ReceiptExtractionResponse(
                List.copyOf(items),
                total.setScale(2, RoundingMode.HALF_UP));
    }

    private void readLines(JsonNode lines, List<ReceiptItemResponse> items) {
        if (!lines.isArray()) {
            return;
        }

        int count = Math.min(lines.size(), MAX_RECEIPT_LINES);
        for (int index = 0; index < count; index++) {
            JsonNode line = lines.path(index);
            String description = cleanDescription(line.path("description").asText(""));
            BigDecimal amount = readAmount(line.path("price"));
            if (description.isBlank() || amount == null || amount.signum() == 0) {
                continue;
            }

            if (amount.signum() > 0) {
                items.add(new ReceiptItemResponse(
                        description, amount.setScale(2, RoundingMode.HALF_UP)));
            }
        }
    }

    private BigDecimal readAmount(JsonNode node) {
        try {
            if (node.isNumber()) {
                return node.decimalValue();
            }
            if (node.isTextual()) {
                return new BigDecimal(node.asText().replaceAll("[^0-9.,-]", "")
                        .replace(',', '.'));
            }
        } catch (NumberFormatException ignored) {
            // Invalid model output is ignored and will cause fallback if required fields are absent.
        }
        return null;
    }

    private String cleanDescription(String description) {
        String cleaned = description.replaceAll("\\s+", " ").trim();
        return cleaned.length() <= MAX_DESCRIPTION_LENGTH
                ? cleaned
                : cleaned.substring(0, MAX_DESCRIPTION_LENGTH).trim();
    }

    private String stripCodeFence(String value) {
        return value.replaceFirst("^```(?:json)?\\s*", "")
                .replaceFirst("\\s*```$", "")
                .trim();
    }

    private static GeminiHttpResponse send(
            String apiKey,
            String model,
            String requestBody,
            Duration timeout) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models/"
                        + model + ":generateContent"))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = client.send(
                request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return new GeminiHttpResponse(response.statusCode(), response.body());
    }

    @FunctionalInterface
    interface GeminiGateway {
        GeminiHttpResponse generate(
                String apiKey,
                String model,
                String requestBody,
                Duration timeout) throws IOException, InterruptedException;
    }

    record GeminiHttpResponse(int statusCode, String body) {}
}
