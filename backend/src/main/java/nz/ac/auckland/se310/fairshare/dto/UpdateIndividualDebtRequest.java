package nz.ac.auckland.se310.fairshare.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

// #2 AC8: the creator may swap which side of the entry each person is on, not just amount/description/date.
// Users are named by ID rather than username, since usernames are not unique.
public record UpdateIndividualDebtRequest(
        @NotNull(message = "Payer is required")
        Long payerUserId,

        @NotNull(message = "Debtor is required")
        Long debtorUserId,

        @NotNull(message = "Amount is required")
        @Positive(message = "Amount must be a positive number")
        @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
        @DecimalMax(value = "9999999999999999.99", message = "Amount is too large")
        BigDecimal amount,

        @NotBlank(message = "Description is required")
        @Size(max = 255, message = "Description must be at most 255 characters")
        String description,

        @NotNull(message = "Date is required")
        @PastOrPresent(message = "Date cannot be in the future")
        LocalDate date,

        // ISO 4217 code; null keeps the entry's current currency.
        String currency) {
}
