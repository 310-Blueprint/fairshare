package nz.ac.auckland.se310.fairshare.dto;

import nz.ac.auckland.se310.fairshare.model.RecurringExpense;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record RecurringExpenseResponse(
        Long id, Long groupId, Long paidByUserId, String paidByUsername,
        BigDecimal amount, String description, RecurringExpense.Frequency frequency,
        LocalDate startDate, LocalDate endDate, LocalDate nextDueDate, boolean active,
        List<Long> participantUserIds) {}
