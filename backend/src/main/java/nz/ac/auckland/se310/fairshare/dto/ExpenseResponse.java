package nz.ac.auckland.se310.fairshare.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * {@code amount} is in the group's base currency. {@code originalAmount} and
 * {@code originalCurrency} are what the member entered (#14 AC1).
 */
public record ExpenseResponse(
        Long id, Long groupId, Long paidByUserId, String paidByUsername,
        BigDecimal amount, String description, LocalDate expenseDate, Instant createdAt,
        List<Long> participantUserIds, Long recurringExpenseId,
        BigDecimal originalAmount, String originalCurrency, BigDecimal exchangeRate) {

    // AC3: recurringExpenseId is null for a manually recorded expense.
    public ExpenseResponse(Long id, Long groupId, Long paidByUserId, String paidByUsername,
                           BigDecimal amount, String description, LocalDate expenseDate, Instant createdAt,
                           List<Long> participantUserIds) {
        this(id, groupId, paidByUserId, paidByUsername, amount, description, expenseDate, createdAt,
                participantUserIds, null, amount, null, BigDecimal.ONE);
    }

    public ExpenseResponse(Long id, Long groupId, Long paidByUserId, String paidByUsername,
                           BigDecimal amount, String description, LocalDate expenseDate, Instant createdAt) {
        this(id, groupId, paidByUserId, paidByUsername, amount, description, expenseDate, createdAt, List.of());
    }
}
