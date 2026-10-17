package nz.ac.auckland.se310.fairshare.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * #16: a member's own spending (their share of each expense), converted into their home currency.
 * {@code total} and the three breakdowns cover {@code month} only; {@code months} is the
 * month-by-month history ending at it (AC2).
 */
public record SpendingSummaryResponse(
        String month,
        String currency,
        BigDecimal total,
        List<SpendingBreakdownLine> byCategory,
        List<SpendingBreakdownLine> byGroup,
        List<SpendingBreakdownLine> byPerson,
        List<MonthlySpending> months) {

    /** One slice of the selected month's total, e.g. a category, a group or the person who paid. */
    public record SpendingBreakdownLine(String key, String label, BigDecimal amount) {}

    /** AC2: {@code changeFromPreviousMonth} is null for the first month shown, which has nothing to compare to. */
    public record MonthlySpending(String month, BigDecimal total, BigDecimal changeFromPreviousMonth) {}
}
