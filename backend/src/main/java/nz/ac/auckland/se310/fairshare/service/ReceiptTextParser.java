package nz.ac.auckland.se310.fairshare.service;

import nz.ac.auckland.se310.fairshare.dto.ReceiptExtractionResponse;
import nz.ac.auckland.se310.fairshare.dto.ReceiptItemResponse;
import nz.ac.auckland.se310.fairshare.exception.ReceiptExtractionException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ReceiptTextParser {

    private static final String EXTRACTION_FAILURE =
            "We couldn't read this receipt. Try a clearer image or enter the expense manually.";
    private static final Pattern PRICE_AT_END = Pattern.compile(
            "^(?<description>.*?)(?:\\s+)(?<price>-?\\(?[$€£]?\\s*\\d{1,6}(?:[.,]\\d{2})\\)?)\\s*$");
    private static final Pattern TOTAL_LABEL = Pattern.compile(
            "^(?:.*\\btotal\\b.*|p?total\\b.*|amount\\s+due.*|balance\\s+due.*)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern NON_ITEM_LABEL = Pattern.compile(
            "^(?:sub\\s*total|p?total|cash|chang.*|card|eftpos|visa|mastercard|payment|tendered|"
                    + "paid|discount|coupon|savings?)\\b.*",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DISCOUNT_LABEL = Pattern.compile(
            ".*\\b(?:discount|coupon|savings?)\\b.*", Pattern.CASE_INSENSITIVE);
    public ReceiptExtractionResponse parse(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            throw new ReceiptExtractionException(EXTRACTION_FAILURE);
        }

        List<String> lines = rawText.lines()
                .map(this::normaliseLine)
                .filter(line -> !line.isBlank())
                .toList();
        List<ReceiptItemResponse> items = new ArrayList<>();
        BigDecimal explicitTotal = null;

        for (String line : lines) {
            Matcher matcher = PRICE_AT_END.matcher(line);
            if (!matcher.matches()) {
                continue;
            }

            String description = cleanDescription(matcher.group("description"));
            BigDecimal price = parsePrice(matcher.group("price"));
            if (description.isBlank() || price == null) {
                continue;
            }

            if (TOTAL_LABEL.matcher(description).matches()) {
                explicitTotal = price.abs();
            } else if (!NON_ITEM_LABEL.matcher(description).matches()
                    && !DISCOUNT_LABEL.matcher(description).matches()
                    && price.signum() > 0) {
                items.add(new ReceiptItemResponse(description, price));
            }
        }

        if (items.isEmpty()) {
            throw new ReceiptExtractionException(EXTRACTION_FAILURE);
        }

        BigDecimal itemTotal = items.stream()
                .map(ReceiptItemResponse::price)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal total = explicitTotal != null ? explicitTotal : itemTotal;
        total = total.setScale(2, RoundingMode.HALF_UP);
        if (total.signum() <= 0) {
            throw new ReceiptExtractionException(EXTRACTION_FAILURE);
        }

        return new ReceiptExtractionResponse(List.copyOf(items), total);
    }

    private String normaliseLine(String line) {
        return line.replace('\u00a0', ' ')
                .replaceAll("[|]", "I")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String cleanDescription(String description) {
        return description.replaceAll("^[*#:_-]+", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private BigDecimal parsePrice(String text) {
        boolean parenthesised = text.contains("(") && text.contains(")");
        String cleaned = text.replaceAll("[$€£()\\s]", "");
        if (cleaned.contains(",") && cleaned.contains(".")) {
            cleaned = cleaned.replace(",", "");
        } else {
            cleaned = cleaned.replace(',', '.');
        }

        try {
            BigDecimal value = new BigDecimal(cleaned).setScale(2, RoundingMode.HALF_UP);
            return parenthesised ? value.abs().negate() : value;
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
