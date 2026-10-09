package nz.ac.auckland.se310.fairshare.dto;

import java.math.BigDecimal;
import java.util.List;

// #2 AC2/AC3/AC5/AC6: one combined, explicitly-directed net figure plus the individual entries behind it.
// amount is in currency, the viewer's home currency; each entry keeps the currency it was recorded in.
public record NetBalanceResponse(
        Long otherUserId,
        String otherUsername,
        Long fromUserId,
        Long toUserId,
        BigDecimal amount,
        String currency,
        boolean settled,
        List<IndividualDebtResponse> entries) {
}
