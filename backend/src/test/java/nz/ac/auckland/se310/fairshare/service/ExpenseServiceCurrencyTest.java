package nz.ac.auckland.se310.fairshare.service;

import nz.ac.auckland.se310.fairshare.dto.CreateExpenseRequest;
import nz.ac.auckland.se310.fairshare.dto.ExpenseResponse;
import nz.ac.auckland.se310.fairshare.exception.InvalidExpenseAmountException;
import nz.ac.auckland.se310.fairshare.exception.UnsupportedCurrencyException;
import nz.ac.auckland.se310.fairshare.model.Expense;
import nz.ac.auckland.se310.fairshare.model.ExpenseGroup;
import nz.ac.auckland.se310.fairshare.model.User;
import nz.ac.auckland.se310.fairshare.repository.ExpenseGroupRepository;
import nz.ac.auckland.se310.fairshare.repository.ExpenseRepository;
import nz.ac.auckland.se310.fairshare.repository.ExpenseShareRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * #14 AC1 and AC3: how ExpenseService converts an entered amount into the group's base currency.
 * Runs without a database, using an NZD group and a stub rate of 1 USD = 1.7056 NZD.
 */
class ExpenseServiceCurrencyTest {

    private static final long GROUP_ID = 1L;
    private static final long ALICE = 10L;
    private static final long BOB = 20L;
    private static final LocalDate EXPENSE_DATE = LocalDate.of(2026, Month.AUGUST, 1);
    private static final BigDecimal USD_TO_NZD = new BigDecimal("1.7056");

    private final ExpenseRepository expenseRepository = mock(ExpenseRepository.class);
    private final ExpenseGroupRepository groupRepository = mock(ExpenseGroupRepository.class);
    private final ExpenseShareRepository expenseShareRepository = mock(ExpenseShareRepository.class);

    /** Every rate lookup made, as "FROM->TO@date", so tests can check what was asked for. */
    private final List<String> rateLookups = new ArrayList<>();

    private ExpenseGroup group;
    private ExpenseService expenseService;

    @BeforeEach
    void setUp() {
        User alice = user(ALICE, "alice");
        group = new ExpenseGroup("Road trip", null, User.Currency.NZD, alice);
        group.addMember(user(BOB, "bob"));

        when(groupRepository.findByIdAndMembersUserId(GROUP_ID, ALICE)).thenReturn(Optional.of(group));
        when(expenseRepository.save(any(Expense.class))).then(returnsFirstArg());

        ExchangeRateProvider stubRates = (from, to, date) -> {
            rateLookups.add(from + "->" + to + "@" + date);
            return from.equals(to) ? BigDecimal.ONE : USD_TO_NZD;
        };
        expenseService = new ExpenseService(expenseRepository, groupRepository, expenseShareRepository,
                new CurrencyService(), stubRates);
    }

    @Test
    void ac1_foreignExpenseKeepsTheOriginalAmountAndCurrency() {
        ExpenseResponse created = expenseService.createExpense(GROUP_ID, request("20.00", "USD"), ALICE);

        assertThat(created.originalAmount()).isEqualByComparingTo("20.00");
        assertThat(created.originalCurrency()).isEqualTo("USD");
        assertThat(created.exchangeRate()).isEqualByComparingTo("1.7056");
        // 20.00 x 1.7056 = 34.112, rounded to the cent
        assertThat(created.amount()).isEqualByComparingTo("34.11");

        Expense saved = savedExpense();
        assertThat(saved.getOriginalAmount()).isEqualByComparingTo("20.00");
        assertThat(saved.getOriginalCurrency()).isEqualTo("USD");
        assertThat(saved.getAmount()).isEqualByComparingTo("34.11");
    }

    @Test
    void ac1_balancesUseTheConvertedAmount() {
        expenseService.createExpense(GROUP_ID, request("20.00", "USD"), ALICE);

        // Alice paid NZD 34.11 and owes half of it, so she is owed the other half.
        assertThat(group.getMember(ALICE).getNetBalance()).isEqualByComparingTo("-17.05");
        assertThat(group.getMember(BOB).getNetBalance()).isEqualByComparingTo("17.05");
    }

    @Test
    void ac1_usesTheRateForTheExpenseDate() {
        expenseService.createExpense(GROUP_ID, request("20.00", "USD"), ALICE);

        assertThat(rateLookups).containsExactly("USD->NZD@2026-08-01");
    }

    @Test
    void ac1_noCurrencyMeansTheGroupBaseCurrency() {
        ExpenseResponse created = expenseService.createExpense(GROUP_ID, request("42.50", null), ALICE);

        assertThat(created.originalCurrency()).isEqualTo("NZD");
        assertThat(created.exchangeRate()).isEqualByComparingTo("1");
        assertThat(created.amount()).isEqualByComparingTo("42.50");
    }

    @Test
    void ac3_currencyCodeIsNormalised() {
        ExpenseResponse created = expenseService.createExpense(GROUP_ID, request("20.00", " usd "), ALICE);

        assertThat(created.originalCurrency()).isEqualTo("USD");
    }

    @Test
    void ac3_unsupportedCurrencyIsRejectedBeforeAnythingIsSaved() {
        CreateExpenseRequest request = request("20.00", "XYZ");

        assertThatThrownBy(() -> expenseService.createExpense(GROUP_ID, request, ALICE))
                .isInstanceOf(UnsupportedCurrencyException.class);
        verify(expenseRepository, never()).save(any());
        assertThat(rateLookups).isEmpty();
    }

    @Test
    void amountWorthLessThanACentOnceConvertedIsRejected() {
        ExpenseService tinyRates = new ExpenseService(expenseRepository, groupRepository, expenseShareRepository,
                new CurrencyService(), (from, to, date) -> new BigDecimal("0.0001"));
        CreateExpenseRequest request = request("10.00", "IDR");

        assertThatThrownBy(() -> tinyRates.createExpense(GROUP_ID, request, ALICE))
                .isInstanceOf(InvalidExpenseAmountException.class)
                .hasMessage("Amount is less than 0.01 NZD once converted");
        verify(expenseRepository, never()).save(any());
    }

    @Test
    void largeForeignAmountSavesOnceConverted() {
        // Review of #75: USD 100,000 is KRW 135,000,000, which used to fail with a 500.
        ExpenseResponse created = krwGroupService().createExpense(GROUP_ID, request("100000.00", "USD"), ALICE);

        assertThat(created.amount()).isEqualByComparingTo("135000000.00");
        assertThat(created.originalAmount()).isEqualByComparingTo("100000.00");
    }

    @Test
    void convertedAmountTooLargeToStoreIsRejectedBeforeAnythingIsSaved() {
        // USD 9,000,000,000 x 1,350 = KRW 12,150,000,000,000, over the DECIMAL(15,2) limit.
        ExpenseService krwGroup = krwGroupService();
        CreateExpenseRequest request = request("9000000000.00", "USD");

        assertThatThrownBy(() -> krwGroup.createExpense(GROUP_ID, request, ALICE))
                .isInstanceOf(InvalidExpenseAmountException.class)
                .hasMessage("Amount is more than 9,999,999,999,999.99 KRW once converted");
        verify(expenseRepository, never()).save(any());
        verify(expenseShareRepository, never()).save(any());
    }

    /** A KRW group where 1 USD = 1,350 KRW. */
    private ExpenseService krwGroupService() {
        ExpenseGroup krwGroup = new ExpenseGroup("Seoul trip", null, User.Currency.KRW, group.getMember(ALICE).getUser());
        krwGroup.addMember(group.getMember(BOB).getUser());
        when(groupRepository.findByIdAndMembersUserId(GROUP_ID, ALICE)).thenReturn(Optional.of(krwGroup));
        return new ExpenseService(expenseRepository, groupRepository, expenseShareRepository,
                new CurrencyService(), (from, to, date) -> from.equals(to) ? BigDecimal.ONE : new BigDecimal("1350"));
    }

    private CreateExpenseRequest request(String amount, String currency) {
        return new CreateExpenseRequest(
                new BigDecimal(amount), "Petrol", ALICE, List.of(ALICE, BOB), EXPENSE_DATE, currency);
    }

    private Expense savedExpense() {
        ArgumentCaptor<Expense> captor = ArgumentCaptor.forClass(Expense.class);
        verify(expenseRepository).save(captor.capture());
        return captor.getValue();
    }

    private static User user(long id, String username) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(id);
        when(user.getUsername()).thenReturn(username);
        return user;
    }
}
