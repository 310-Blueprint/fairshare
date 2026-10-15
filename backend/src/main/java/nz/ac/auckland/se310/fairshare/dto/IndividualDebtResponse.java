package nz.ac.auckland.se310.fairshare.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

// #2 AC5: payer/debtor are named explicitly rather than relying on a signed amount.
public record IndividualDebtResponse(
        Long id,
        Long payerUserId,
        String payerUsername,
        Long debtorUserId,
        String debtorUsername,
        BigDecimal amount,
        String currency,
        String description,
        LocalDate date,
        boolean canEdit) {
}
