package nz.ac.auckland.se310.fairshare.dto;

import java.math.BigDecimal;
import java.util.List;

public record ReceiptExtractionResponse(
        List<ReceiptItemResponse> items,
        BigDecimal total) {}
