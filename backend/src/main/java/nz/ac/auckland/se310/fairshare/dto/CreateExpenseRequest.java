package nz.ac.auckland.se310.fairshare.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateExpenseRequest(
        @NotNull(message = "Amount is required")
        @Positive(message = "Amount must be a positive number")
        // Amounts are stored to the cent, so anything under one cent would round away to 0.00.
        @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
        // expense.original_amount is DECIMAL(15,2); anything larger fails to save with a 500.
        @DecimalMax(value = "9999999999999.99", message = "Amount must be at most 9,999,999,999,999.99")
        BigDecimal amount,

        @NotBlank(message = "Description is required")
        @Size(max = 255, message = "Description must be at most 255 characters")
        String description,

        @NotNull(message = "Payer is required")
        Long paidByUserId,

        @NotEmpty(message = "At least one participant is required")
        List<Long> participantUserIds,

        @PastOrPresent(message = "Expense date cannot be in the future")
        LocalDate expenseDate,

        // #14 AC1: ISO 4217 code the amount was entered in. Left out, it defaults to the group's
        // base currency. Checked against the supported list by CurrencyService (AC3).
        String currency) {

    public CreateExpenseRequest(BigDecimal amount, String description, Long paidByUserId,
                                List<Long> participantUserIds, LocalDate expenseDate) {
        this(amount, description, paidByUserId, participantUserIds, expenseDate, null);
    }
}
