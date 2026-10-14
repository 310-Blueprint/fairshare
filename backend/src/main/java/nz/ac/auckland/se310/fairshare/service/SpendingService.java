package nz.ac.auckland.se310.fairshare.service;

import nz.ac.auckland.se310.fairshare.dto.SpendingSummaryResponse;
import nz.ac.auckland.se310.fairshare.dto.SpendingSummaryResponse.MonthlySpending;
import nz.ac.auckland.se310.fairshare.dto.SpendingSummaryResponse.SpendingBreakdownLine;
import nz.ac.auckland.se310.fairshare.exception.ExchangeRateUnavailableException;
import nz.ac.auckland.se310.fairshare.exception.SpendingAccessDeniedException;
import nz.ac.auckland.se310.fairshare.model.Expense;
import nz.ac.auckland.se310.fairshare.model.ExpenseShare;
import nz.ac.auckland.se310.fairshare.model.User;
import nz.ac.auckland.se310.fairshare.repository.ExpenseShareRepository;
import nz.ac.auckland.se310.fairshare.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SpendingService {

    // AC2: how many months, ending at the selected one, the month-by-month history covers.
    private static final int HISTORY_MONTHS = 6;
    private static final String UNCATEGORISED_KEY = "UNCATEGORISED";
    private static final String UNCATEGORISED_LABEL = "Uncategorised";
    private static final int MONEY_SCALE = 2;
    private static final int RATE_SCALE = 8; // matches ExpenseService
    // users.currency is nullable, so an account without a home currency still gets one (as in #2).
    private static final User.Currency FALLBACK_CURRENCY = User.Currency.NZD;

    private final ExpenseShareRepository expenseShareRepository;
    private final UserRepository userRepository;
    private final ExchangeRateProvider exchangeRateProvider;
    private final Clock clock;

    public SpendingService(ExpenseShareRepository expenseShareRepository, UserRepository userRepository,
                           ExchangeRateProvider exchangeRateProvider, Clock clock) {
        this.expenseShareRepository = expenseShareRepository;
        this.userRepository = userRepository;
        this.exchangeRateProvider = exchangeRateProvider;
        this.clock = clock;
    }

    /**
     * #16: the user's share of each expense, in groups they belong to, for {@code month} (the
     * current month when null), plus the months leading up to it. Amounts are converted from each
     * group's currency into the user's home currency.
     */
    @Transactional(readOnly = true)
    public SpendingSummaryResponse getSummary(Long requestedUserId, Long currentUserId, YearMonth month) {
        if (!currentUserId.equals(requestedUserId)) {
            throw new SpendingAccessDeniedException(); // AC4
        }
        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + currentUserId));

        LocalDate today = LocalDate.now(clock);
        YearMonth selected = month == null ? YearMonth.from(today) : month; // AC1: current month by default
        YearMonth firstMonth = selected.minusMonths(HISTORY_MONTHS - 1L);
        MonthlyConverter converter = new MonthlyConverter(homeCurrency(user), today);

        Map<YearMonth, BigDecimal> monthlyTotals = new LinkedHashMap<>();
        for (YearMonth m = firstMonth; !m.isAfter(selected); m = m.plusMonths(1)) {
            monthlyTotals.put(m, BigDecimal.ZERO);
        }
        Breakdown byCategory = new Breakdown();
        Breakdown byGroup = new Breakdown();
        Breakdown byPerson = new Breakdown();

        List<ExpenseShare> shares = expenseShareRepository.findSpendingShares(
                currentUserId, firstMonth.atDay(1), selected.atEndOfMonth());
        for (ExpenseShare share : shares) {
            Expense expense = share.getExpense();
            YearMonth expenseMonth = YearMonth.from(expense.getExpenseDate());
            BigDecimal amount = converter.convert(
                    share.getShareAmount(), expense.getGroup().getBaseCurrency().name(), expenseMonth);
            monthlyTotals.merge(expenseMonth, amount, BigDecimal::add);

            if (expenseMonth.equals(selected)) {
                byCategory.add(categoryKey(expense), categoryLabel(expense), amount);
                byGroup.add(String.valueOf(expense.getGroup().getId()), expense.getGroup().getGroupName(), amount);
                User payer = expense.getPaidBy();
                byPerson.add(String.valueOf(payer.getId()),
                        payer.getId().equals(currentUserId) ? "You" : payer.getUsername(), amount);
            }
        }

        return new SpendingSummaryResponse(
                selected.toString(),
                converter.displayCurrency(),
                money(monthlyTotals.getOrDefault(selected, BigDecimal.ZERO)),
                byCategory.lines(),
                byGroup.lines(),
                byPerson.lines(),
                monthlyHistory(monthlyTotals));
    }

    // AC2: each month's total and how it changed from the month before.
    private static List<MonthlySpending> monthlyHistory(Map<YearMonth, BigDecimal> monthlyTotals) {
        List<MonthlySpending> history = new ArrayList<>();
        BigDecimal previous = null;
        for (Map.Entry<YearMonth, BigDecimal> entry : monthlyTotals.entrySet()) {
            BigDecimal total = money(entry.getValue());
            history.add(new MonthlySpending(entry.getKey().toString(), total,
                    previous == null ? null : total.subtract(previous)));
            previous = total;
        }
        return history;
    }

    private static String categoryKey(Expense expense) {
        return expense.getCategory() == null ? UNCATEGORISED_KEY : expense.getCategory().name();
    }

    private static String categoryLabel(Expense expense) {
        return expense.getCategory() == null ? UNCATEGORISED_LABEL : expense.getCategory().getLabel();
    }

    private static String homeCurrency(User user) {
        return (user.getCurrency() == null ? FALLBACK_CURRENCY : user.getCurrency()).name();
    }

    private static BigDecimal money(BigDecimal amount) {
        return amount.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    /** Totals per key, largest first; amounts are only rounded once everything has been added up. */
    private static final class Breakdown {
        private final Map<String, String> labels = new HashMap<>();
        private final Map<String, BigDecimal> totals = new HashMap<>();

        void add(String key, String label, BigDecimal amount) {
            labels.putIfAbsent(key, label);
            totals.merge(key, amount, BigDecimal::add);
        }

        List<SpendingBreakdownLine> lines() {
            return totals.entrySet().stream()
                    .map(entry -> new SpendingBreakdownLine(entry.getKey(), labels.get(entry.getKey()),
                            money(entry.getValue())))
                    .sorted(Comparator.comparing(SpendingBreakdownLine::amount).reversed()
                            .thenComparing(SpendingBreakdownLine::label))
                    .toList();
        }
    }

    /**
     * Converts into the home currency with one rate per currency per month: the rate on the
     * month's last day, or today's for a month that has not ended. One lookup per month keeps the
     * overview quick, rather than one call to the rate service for every expense date.
     */
    private final class MonthlyConverter {
        private final String displayCurrency;
        private final LocalDate today;
        private final Map<String, BigDecimal> rates = new HashMap<>();

        MonthlyConverter(String displayCurrency, LocalDate today) {
            this.displayCurrency = displayCurrency;
            this.today = today;
        }

        String displayCurrency() {
            return displayCurrency;
        }

        BigDecimal convert(BigDecimal amount, String currency, YearMonth month) {
            if (currency.equals(displayCurrency)) {
                return amount;
            }
            BigDecimal rate = rates.computeIfAbsent(currency + "@" + month, key -> lookUpRate(currency, month));
            return amount.multiply(rate);
        }

        private BigDecimal lookUpRate(String currency, YearMonth month) {
            LocalDate monthEnd = month.atEndOfMonth();
            LocalDate rateDate = monthEnd.isAfter(today) ? today : monthEnd;
            try {
                return exchangeRateProvider.getRate(currency, displayCurrency, rateDate)
                        .setScale(RATE_SCALE, RoundingMode.HALF_UP);
            } catch (ExchangeRateUnavailableException e) {
                throw ExchangeRateUnavailableException.forSpending(currency, displayCurrency, e);
            }
        }
    }
}
