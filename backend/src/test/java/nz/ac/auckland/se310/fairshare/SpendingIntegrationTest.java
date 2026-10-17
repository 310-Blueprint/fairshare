package nz.ac.auckland.se310.fairshare;

import nz.ac.auckland.se310.fairshare.dto.CreateExpenseRequest;
import nz.ac.auckland.se310.fairshare.dto.CreateGroupRequest;
import nz.ac.auckland.se310.fairshare.dto.ExpenseResponse;
import nz.ac.auckland.se310.fairshare.dto.SpendingSummaryResponse;
import nz.ac.auckland.se310.fairshare.dto.SpendingSummaryResponse.MonthlySpending;
import nz.ac.auckland.se310.fairshare.dto.SpendingSummaryResponse.SpendingBreakdownLine;
import nz.ac.auckland.se310.fairshare.exception.ExchangeRateUnavailableException;
import nz.ac.auckland.se310.fairshare.exception.SpendingAccessDeniedException;
import nz.ac.auckland.se310.fairshare.model.ExpenseCategory;
import nz.ac.auckland.se310.fairshare.model.User;
import nz.ac.auckland.se310.fairshare.repository.ExpenseGroupRepository;
import nz.ac.auckland.se310.fairshare.repository.ExpenseRepository;
import nz.ac.auckland.se310.fairshare.repository.ExpenseShareRepository;
import nz.ac.auckland.se310.fairshare.repository.SettlementRepository;
import nz.ac.auckland.se310.fairshare.service.ExpenseGroupService;
import nz.ac.auckland.se310.fairshare.service.ExpenseService;
import nz.ac.auckland.se310.fairshare.service.SpendingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/** #16: a member's own spending overview, built from their share of each group expense. */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import({TestCurrentUserConfig.class, TestExchangeRateConfig.class, TestClockConfig.class})
class SpendingIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4"));

    private static final String EVE_EMAIL = "eve@test.com";
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 14);
    private static final YearMonth OCTOBER = YearMonth.of(2026, 10);

    @Autowired SpendingService spendingService;
    @Autowired ExpenseGroupService groupService;
    @Autowired ExpenseService expenseService;
    @Autowired ExpenseGroupRepository groupRepository;
    @Autowired ExpenseRepository expenseRepository;
    @Autowired ExpenseShareRepository expenseShareRepository;
    @Autowired SettlementRepository settlementRepository;
    @Autowired UserRepository userRepository;
    @Autowired TestExchangeRateConfig.StubExchangeRateProvider exchangeRates;
    @Autowired TestClockConfig.MutableClock clock;

    private Long aliceId;
    private Long bobId;
    private Long eveId;
    private Long flatId;

    @BeforeEach
    void setUp() {
        expenseShareRepository.deleteAll();
        expenseRepository.deleteAll();
        settlementRepository.deleteAll();
        groupRepository.deleteAll();

        aliceId = userRepository.findByEmail("alice@test.com").orElseThrow().getId();
        bobId = userRepository.findByEmail("bob@test.com").orElseThrow().getId();
        // Eve's home currency is EUR, so groups she creates are in EUR.
        eveId = userRepository.findByEmail(EVE_EMAIL)
                .orElseGet(() -> userRepository.save(new User(
                        "eve", "x", EVE_EMAIL, User.Country.NEW_ZEALAND, User.Currency.EUR)))
                .getId();

        flatId = groupService.createGroup(new CreateGroupRequest("Flat", null), aliceId).id();
        groupService.addMember(flatId, "bob@test.com", aliceId);

        exchangeRates.reset();
        clock.setInstant(TODAY.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    @Test
    void ac1_totalsTheCurrentMonthByDefault() {
        // Alice's share is half of each: 30.00 + 10.00 in October, 50.00 in September.
        expense(flatId, aliceId, "60.00", ExpenseCategory.GROCERIES, LocalDate.of(2026, 10, 2));
        expense(flatId, bobId, "20.00", ExpenseCategory.TRANSPORT, LocalDate.of(2026, 10, 10));
        expense(flatId, bobId, "100.00", ExpenseCategory.GROCERIES, LocalDate.of(2026, 9, 20));

        SpendingSummaryResponse summary = spendingService.getSummary(aliceId, aliceId, null);

        assertThat(summary.month()).isEqualTo("2026-10");
        assertThat(summary.currency()).isEqualTo("NZD");
        assertThat(summary.total()).isEqualByComparingTo("40.00");
    }

    @Test
    void ac1_breaksTheMonthDownByCategoryGroupAndPerson() {
        Long tripId = groupService.createGroup(new CreateGroupRequest("Trip", null), aliceId).id();
        expense(flatId, aliceId, "60.00", ExpenseCategory.GROCERIES, LocalDate.of(2026, 10, 2));
        expense(flatId, bobId, "20.00", null, LocalDate.of(2026, 10, 3));
        expense(tripId, aliceId, "25.00", ExpenseCategory.TRANSPORT, LocalDate.of(2026, 10, 4));

        SpendingSummaryResponse summary = spendingService.getSummary(aliceId, aliceId, OCTOBER);

        assertThat(summary.total()).isEqualByComparingTo("65.00");
        assertThat(summary.byCategory())
                .extracting(SpendingBreakdownLine::key, SpendingBreakdownLine::label, SpendingBreakdownLine::amount)
                .containsExactly(
                        tuple("GROCERIES", "Groceries", new BigDecimal("30.00")),
                        tuple("TRANSPORT", "Transport", new BigDecimal("25.00")),
                        tuple("UNCATEGORISED", "Uncategorised", new BigDecimal("10.00")));
        assertThat(summary.byGroup())
                .extracting(SpendingBreakdownLine::key, SpendingBreakdownLine::label, SpendingBreakdownLine::amount)
                .containsExactly(
                        tuple(String.valueOf(flatId), "Flat", new BigDecimal("40.00")),
                        tuple(String.valueOf(tripId), "Trip", new BigDecimal("25.00")));
        // By who paid: Alice paid for 55.00 of her own spending, Bob for 10.00 of it.
        assertThat(summary.byPerson())
                .extracting(SpendingBreakdownLine::key, SpendingBreakdownLine::label, SpendingBreakdownLine::amount)
                .containsExactly(
                        tuple(String.valueOf(aliceId), "You", new BigDecimal("55.00")),
                        tuple(String.valueOf(bobId), "bob", new BigDecimal("10.00")));
    }

    @Test
    void ac1_anExpenseCategoryIsSavedAndCanBeChanged() {
        List<Long> memberIds = List.of(aliceId, bobId);
        ExpenseResponse created = expenseService.createExpense(flatId, new CreateExpenseRequest(
                new BigDecimal("12.00"), "Pizza", aliceId, memberIds, TODAY, null, ExpenseCategory.FOOD_AND_DRINK),
                aliceId);
        assertThat(created.category()).isEqualTo(ExpenseCategory.FOOD_AND_DRINK);

        expenseService.updateExpense(flatId, new CreateExpenseRequest(
                new BigDecimal("12.00"), "Pizza", aliceId, memberIds, TODAY, null, ExpenseCategory.ENTERTAINMENT),
                aliceId, created.id());
        assertThat(expenseService.getExpense(flatId, created.id(), aliceId).category())
                .isEqualTo(ExpenseCategory.ENTERTAINMENT);

        // Left out, an expense has no category.
        ExpenseResponse uncategorised = expenseService.createExpense(flatId, new CreateExpenseRequest(
                new BigDecimal("5.00"), "Misc", aliceId, memberIds, TODAY), aliceId);
        assertThat(uncategorised.category()).isNull();
    }

    @Test
    void ac1_onlyCountsTheUsersOwnShare() {
        // Bob pays for something only he shares in; it is not Alice's spending.
        expenseService.createExpense(flatId, new CreateExpenseRequest(new BigDecimal("80.00"), "Bob's bike",
                bobId, List.of(bobId), LocalDate.of(2026, 10, 5), null, ExpenseCategory.SHOPPING), bobId);
        expense(flatId, bobId, "20.00", ExpenseCategory.GROCERIES, LocalDate.of(2026, 10, 6));

        SpendingSummaryResponse summary = spendingService.getSummary(aliceId, aliceId, OCTOBER);

        assertThat(summary.total()).isEqualByComparingTo("10.00");
        assertThat(summary.byCategory()).extracting(SpendingBreakdownLine::key).containsExactly("GROCERIES");
    }

    @Test
    void ac1_convertsOtherGroupCurrenciesIntoTheHomeCurrency() {
        exchangeRates.setRate("EUR", "NZD", "2.00");
        Long euroTripId = groupService.createGroup(new CreateGroupRequest("Euro trip", null), eveId).id();
        groupService.addMember(euroTripId, "alice@test.com", eveId);
        expense(euroTripId, eveId, "30.00", ExpenseCategory.ACCOMMODATION, LocalDate.of(2026, 10, 1)); // EUR 15.00 each
        expense(flatId, aliceId, "10.00", ExpenseCategory.GROCERIES, LocalDate.of(2026, 10, 1));       // NZD 5.00 each

        SpendingSummaryResponse summary = spendingService.getSummary(aliceId, aliceId, OCTOBER);

        assertThat(summary.currency()).isEqualTo("NZD");
        assertThat(summary.total()).isEqualByComparingTo("35.00");
        assertThat(summary.byGroup()).extracting(SpendingBreakdownLine::label, SpendingBreakdownLine::amount)
                .containsExactly(tuple("Euro trip", new BigDecimal("30.00")), tuple("Flat", new BigDecimal("5.00")));
    }

    @Test
    void ac1_aMissingRateIsReportedRatherThanLeftOut() {
        Long euroTripId = groupService.createGroup(new CreateGroupRequest("Euro trip", null), eveId).id();
        groupService.addMember(euroTripId, "alice@test.com", eveId);
        expense(euroTripId, eveId, "30.00", null, LocalDate.of(2026, 10, 1));

        assertThatThrownBy(() -> spendingService.getSummary(aliceId, aliceId, OCTOBER))
                .isInstanceOf(ExchangeRateUnavailableException.class)
                .hasMessageContaining("your spending cannot be shown");
    }

    @Test
    void ac2_showsEachMonthWithTheChangeFromTheMonthBefore() {
        expense(flatId, bobId, "100.00", null, LocalDate.of(2026, 8, 15)); // 50.00
        expense(flatId, bobId, "40.00", null, LocalDate.of(2026, 9, 15));  // 20.00
        expense(flatId, bobId, "70.00", null, LocalDate.of(2026, 10, 1));  // 35.00

        SpendingSummaryResponse summary = spendingService.getSummary(aliceId, aliceId, OCTOBER);

        assertThat(summary.months()).hasSize(6);
        assertThat(summary.months())
                .extracting(MonthlySpending::month)
                .containsExactly("2026-05", "2026-06", "2026-07", "2026-08", "2026-09", "2026-10");
        assertThat(summary.months().getFirst().changeFromPreviousMonth()).isNull();
        assertThat(summary.months().subList(3, 6))
                .extracting(MonthlySpending::total, MonthlySpending::changeFromPreviousMonth)
                .containsExactly(
                        tuple(new BigDecimal("50.00"), new BigDecimal("50.00")),
                        tuple(new BigDecimal("20.00"), new BigDecimal("-30.00")),
                        tuple(new BigDecimal("35.00"), new BigDecimal("15.00")));
    }

    @Test
    void ac2_aSelectedPastMonthIsSummarisedWithItsOwnHistory() {
        expense(flatId, bobId, "40.00", ExpenseCategory.UTILITIES, LocalDate.of(2026, 9, 15));
        expense(flatId, bobId, "70.00", ExpenseCategory.GROCERIES, LocalDate.of(2026, 10, 1));

        SpendingSummaryResponse summary = spendingService.getSummary(aliceId, aliceId, YearMonth.of(2026, 9));

        assertThat(summary.month()).isEqualTo("2026-09");
        assertThat(summary.total()).isEqualByComparingTo("20.00");
        assertThat(summary.byCategory()).extracting(SpendingBreakdownLine::key).containsExactly("UTILITIES");
        assertThat(summary.months().getLast().month()).isEqualTo("2026-09");
    }

    @Test
    void ac3_aMonthWithNoExpensesHasAZeroTotalAndNoBreakdown() {
        expense(flatId, bobId, "40.00", null, LocalDate.of(2026, 9, 15));

        SpendingSummaryResponse summary = spendingService.getSummary(aliceId, aliceId, OCTOBER);

        assertThat(summary.total()).isEqualByComparingTo("0.00");
        assertThat(summary.byCategory()).isEmpty();
        assertThat(summary.byGroup()).isEmpty();
        assertThat(summary.byPerson()).isEmpty();
    }

    @Test
    void ac4_anotherUsersOverviewIsRejected() {
        assertThatThrownBy(() -> spendingService.getSummary(bobId, aliceId, OCTOBER))
                .isInstanceOf(SpendingAccessDeniedException.class)
                .hasMessage("You can only view your own spending");
    }

    @Test
    void ac4_groupsTheUserHasLeftStillCountTowardsTheirSpending() {
        // Alice pays for herself alone, so her balance is zero and she is free to leave.
        Long oldGroupId = groupService.createGroup(new CreateGroupRequest("Old flat", null), aliceId).id();
        groupService.addMember(oldGroupId, "bob@test.com", aliceId);
        expenseService.createExpense(oldGroupId, new CreateExpenseRequest(new BigDecimal("30.00"), "Lamp",
                aliceId, List.of(aliceId), LocalDate.of(2026, 10, 2)), aliceId);
        groupService.removeMember(oldGroupId, aliceId, bobId);
        expense(flatId, bobId, "20.00", null, LocalDate.of(2026, 10, 3));

        SpendingSummaryResponse summary = spendingService.getSummary(aliceId, aliceId, OCTOBER);

        assertThat(summary.total()).isEqualByComparingTo("40.00");
        assertThat(summary.byGroup()).extracting(SpendingBreakdownLine::label).containsExactly("Old flat", "Flat");
    }

    /** Splits the amount equally between every member of the group. */
    private void expense(Long groupId, Long payerId, String amount, ExpenseCategory category, LocalDate date) {
        List<Long> memberIds = groupRepository.findByIdAndMembersUserId(groupId, payerId).orElseThrow()
                .getMembers().stream().map(member -> member.getUser().getId()).toList();
        expenseService.createExpense(groupId, new CreateExpenseRequest(new BigDecimal(amount), "Expense",
                payerId, memberIds, date, null, category), payerId);
    }
}
