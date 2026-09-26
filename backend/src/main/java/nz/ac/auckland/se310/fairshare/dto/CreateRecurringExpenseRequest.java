package nz.ac.auckland.se310.fairshare.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import nz.ac.auckland.se310.fairshare.model.RecurringExpense;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CreateRecurringExpenseRequest(
        @NotNull(message = "Amount is required")
        @Positive(message = "Amount must be a positive number")
        // Amounts are stored to the cent, so anything under one cent would round away to 0.00.
        @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
        // Generation splits into whole cents; a fractional cent (e.g. 10.005) can't be split exactly.
        @Digits(integer = 8, fraction = 2, message = "Amounts should only have up to 2 decimal places.")
        BigDecimal amount,

        @NotBlank(message = "Description is required")
        @Size(max = 255, message = "Description must be at most 255 characters")
        String description,

        @NotNull(message = "Payer is required")
        Long paidByUserId,

        @NotEmpty(message = "At least one participant is required")
        List<Long> participantUserIds,

        @NotNull(message = "Frequency is required")
        RecurringExpense.Frequency frequency,

        @NotNull(message = "Start date is required")
        LocalDate startDate,

        LocalDate endDate) {}
