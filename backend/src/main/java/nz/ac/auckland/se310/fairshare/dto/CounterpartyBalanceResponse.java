package nz.ac.auckland.se310.fairshare.dto;

import java.math.BigDecimal;

// #3: one row of the individual-debts overview page.
public record CounterpartyBalanceResponse(
        Long counterpartyUserId,
        String counterpartyUsername,
        Long fromUserId,
        Long toUserId,
        BigDecimal amount,
        boolean settled) {
}
