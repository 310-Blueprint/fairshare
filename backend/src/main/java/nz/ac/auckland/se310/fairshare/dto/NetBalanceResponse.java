package nz.ac.auckland.se310.fairshare.dto;

import java.math.BigDecimal;
import java.util.List;

// #3 AC2/AC3/AC5/AC6: one combined, explicitly-directed net figure plus the individual entries behind it.
public record NetBalanceResponse(
        Long otherUserId,
        String otherUsername,
        Long fromUserId,
        Long toUserId,
        BigDecimal amount,
        boolean settled,
        List<IndividualDebtResponse> entries) {
}
