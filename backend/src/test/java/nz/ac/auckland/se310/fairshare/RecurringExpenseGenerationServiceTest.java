package nz.ac.auckland.se310.fairshare;

import nz.ac.auckland.se310.fairshare.dto.CreateGroupRequest;
import nz.ac.auckland.se310.fairshare.dto.CreateRecurringExpenseRequest;
import nz.ac.auckland.se310.fairshare.dto.ExpenseResponse;
import nz.ac.auckland.se310.fairshare.dto.GroupMemberResponse;
import nz.ac.auckland.se310.fairshare.dto.RecurringExpenseResponse;
import nz.ac.auckland.se310.fairshare.model.Expense;
import nz.ac.auckland.se310.fairshare.model.ExpenseGroup;
import nz.ac.auckland.se310.fairshare.model.RecurringExpense;
import nz.ac.auckland.se310.fairshare.repository.ExpenseGroupRepository;
import nz.ac.auckland.se310.fairshare.repository.ExpenseRepository;
import nz.ac.auckland.se310.fairshare.repository.ExpenseShareRepository;
import nz.ac.auckland.se310.fairshare.repository.RecurringExpenseParticipantRepository;
import nz.ac.auckland.se310.fairshare.repository.RecurringExpenseRepository;
import nz.ac.auckland.se310.fairshare.service.ExpenseGroupService;
import nz.ac.auckland.se310.fairshare.service.ExpenseService;
import nz.ac.auckland.se310.fairshare.service.RecurringExpenseGenerationService;
import nz.ac.auckland.se310.fairshare.service.RecurringExpenseService;
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
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import({TestCurrentUserConfig.class, TestClockConfig.class})
class RecurringExpenseGenerationServiceTest {

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4"));

    private static final ZoneId ZONE = ZoneId.systemDefault();
    private static final String RENT = "Rent";
    private static final String RENT_AMOUNT = "500.00";

    @Autowired ExpenseGroupService groupService;
    @Autowired RecurringExpenseService recurringExpenseService;
    @Autowired RecurringExpenseGenerationService generationService;
    @Autowired ExpenseService expenseService;
    @Autowired ExpenseGroupRepository groupRepository;
    @Autowired RecurringExpenseRepository recurringExpenseRepository;
    @Autowired RecurringExpenseParticipantRepository participantRepository;
    @Autowired ExpenseRepository expenseRepository;
    @Autowired ExpenseShareRepository expenseShareRepository;
    @Autowired UserRepository userRepository;
    @Autowired TestClockConfig.MutableClock clock;

    private Long aliceId;
    private Long bobId;
    private Long groupId;
    private List<Long> memberIds;

    @BeforeEach
    void setUp() {
        expenseShareRepository.deleteAll();
        expenseRepository.deleteAll();
        participantRepository.deleteAll();
        recurringExpenseRepository.deleteAll();
        groupRepository.deleteAll();

        // Keeps "today" safely before every fixture's start date, so creating a recurring expense
        // doesn't itself trigger generation - each test controls that explicitly via generateAsOf.
        clock.setInstant(LocalDate.of(2025, 1, 1).atStartOfDay(ZONE).toInstant());

        aliceId = userRepository.findByEmail("alice@test.com").orElseThrow().getId();
        bobId = userRepository.findByEmail("bob@test.com").orElseThrow().getId();
        memberIds = List.of(aliceId, bobId);

        groupId = groupService.createGroup(new CreateGroupRequest("Flat 3", null), aliceId).id();
        groupService.addMember(groupId, "bob@test.com", aliceId);
    }

    /** Pins the shared test clock to the given "today", so backfill scenarios are deterministic. */
    private int generateAsOf(LocalDate date) {
        clock.setInstant(date.atStartOfDay(ZONE).toInstant());
        return generationService.generateDueExpenses();
    }

    private RecurringExpenseResponse createMonthlyRecurringExpense(LocalDate start, LocalDate end) {
        return recurringExpenseService.createRecurringExpense(groupId,
                new CreateRecurringExpenseRequest(new BigDecimal(RENT_AMOUNT), RENT, aliceId, memberIds,
                        RecurringExpense.Frequency.MONTHLY, start, end), aliceId);
    }

    @Test
    void ac2_generatesAnExpenseOnceTheStartDateIsReached() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        createMonthlyRecurringExpense(start, null);

        int generated = generateAsOf(start);

        assertThat(generated).isEqualTo(1);
        List<ExpenseResponse> expenses = expenseService.getExpensesForGroup(groupId, aliceId);
        assertThat(expenses).hasSize(1);
        assertThat(expenses.get(0).description()).isEqualTo(RENT);
        assertThat(expenses.get(0).expenseDate()).isEqualTo(start);
        assertThat(expenses.get(0).paidByUserId()).isEqualTo(aliceId);
    }

    @Test
    void ac2_generatedExpenseSplitsEquallyJustLikeAManualExpense() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        createMonthlyRecurringExpense(start, null);

        generateAsOf(start);

        assertThat(balances()).containsOnly(
                Map.entry(aliceId, new BigDecimal("-250.00")),
                Map.entry(bobId, new BigDecimal("250.00")));
    }

    @Test
    void ac2_doesNotGenerateBeforeTheStartDate() {
        LocalDate start = LocalDate.of(2026, 3, 1);
        createMonthlyRecurringExpense(start, null);

        int generated = generateAsOf(start.minusDays(1));

        assertThat(generated).isZero();
        assertThat(expenseService.getExpensesForGroup(groupId, aliceId)).isEmpty();
    }

    @Test
    void ac2_generatesImmediatelyOnCreateWhenTheStartDateIsAlreadyDue() {
        // "Today" is well after the chosen start date, as if someone backdated it on purpose.
        clock.setInstant(LocalDate.of(2026, 4, 15).atStartOfDay(ZONE).toInstant());

        RecurringExpenseResponse created = createMonthlyRecurringExpense(LocalDate.of(2026, 1, 1), null);

        assertThat(created.nextDueDate()).isEqualTo(LocalDate.of(2026, 5, 1));
        assertThat(expenseService.getExpensesForGroup(groupId, aliceId))
                .extracting(ExpenseResponse::expenseDate)
                .containsExactlyInAnyOrder(
                        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1),
                        LocalDate.of(2026, 3, 1), LocalDate.of(2026, 4, 1));
    }

    @Test
    void ac2_doesNotGenerateOnCreateWhenTheStartDateIsInTheFuture() {
        clock.setInstant(LocalDate.of(2026, 1, 1).atStartOfDay(ZONE).toInstant());

        RecurringExpenseResponse created = createMonthlyRecurringExpense(LocalDate.of(2026, 6, 1), null);

        assertThat(created.nextDueDate()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(expenseService.getExpensesForGroup(groupId, aliceId)).isEmpty();
    }

    @Test
    void skipsARecurringExpenseWhoseParticipantHasLeftTheGroupButStillGeneratesTheRest() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        RecurringExpenseResponse valid = createMonthlyRecurringExpense(start, null);
        RecurringExpenseResponse invalid = recurringExpenseService.createRecurringExpense(groupId,
                new CreateRecurringExpenseRequest(new BigDecimal("50.00"), "Gym", bobId, memberIds,
                        RecurringExpense.Frequency.MONTHLY, start, null), aliceId);

        // Simulates bob having left the group after "Gym" was created. The normal removal API now
        // blocks this (a separate fix), so this bypasses it directly to test generation's own
        // defence against a recurring expense left referencing someone no longer in the group.
        ExpenseGroup group = groupRepository.findByIdAndMembersUserId(groupId, aliceId).orElseThrow();
        group.removeMember(group.getMember(bobId));
        groupRepository.save(group);

        int generated = generateAsOf(start);

        assertThat(generated).isEqualTo(1); // only the still-valid "Rent" generated
        assertThat(expenseService.getExpensesForGroup(groupId, aliceId))
                .extracting(ExpenseResponse::description)
                .containsExactly(RENT);

        // The invalid recurring expense is left untouched (still active, still due) rather than
        // corrupted or silently disabled.
        RecurringExpense invalidEntity = recurringExpenseRepository.findById(invalid.id()).orElseThrow();
        assertThat(invalidEntity.isActive()).isTrue();
        assertThat(invalidEntity.getNextDueDate()).isEqualTo(start);
    }

    @Test
    void weeklyOccurrencesAreSevenDaysApart() {
        LocalDate start = LocalDate.of(2026, 1, 5);
        recurringExpenseService.createRecurringExpense(groupId,
                new CreateRecurringExpenseRequest(new BigDecimal("20.00"), "Cleaner", aliceId, memberIds,
                        RecurringExpense.Frequency.WEEKLY, start, null), aliceId);

        generateAsOf(start.plusWeeks(3));

        assertThat(expenseService.getExpensesForGroup(groupId, aliceId))
                .extracting(ExpenseResponse::expenseDate)
                .containsExactlyInAnyOrder(start, start.plusWeeks(1), start.plusWeeks(2), start.plusWeeks(3));
    }

    @Test
    void fortnightlyOccurrencesAreFourteenDaysApart() {
        LocalDate start = LocalDate.of(2026, 1, 5);
        recurringExpenseService.createRecurringExpense(groupId,
                new CreateRecurringExpenseRequest(new BigDecimal("20.00"), "Gardener", aliceId, memberIds,
                        RecurringExpense.Frequency.FORTNIGHTLY, start, null), aliceId);

        generateAsOf(start.plusWeeks(4));

        assertThat(expenseService.getExpensesForGroup(groupId, aliceId))
                .extracting(ExpenseResponse::expenseDate)
                .containsExactlyInAnyOrder(start, start.plusWeeks(2), start.plusWeeks(4));
    }

    @Test
    void monthlyOccurrencesAnchorToTheStartDateRatherThanDriftingOffAShortMonth() {
        // Jan 31 -> Feb 28 -> Mar 31 (not Mar 28): each occurrence is startDate + n months,
        // never a month added to the previous occurrence.
        LocalDate start = LocalDate.of(2026, 1, 31);
        createMonthlyRecurringExpense(start, null);

        generateAsOf(LocalDate.of(2026, 3, 31));

        assertThat(expenseService.getExpensesForGroup(groupId, aliceId))
                .extracting(ExpenseResponse::expenseDate)
                .containsExactlyInAnyOrder(
                        LocalDate.of(2026, 1, 31),
                        LocalDate.of(2026, 2, 28),
                        LocalDate.of(2026, 3, 31));
    }

    @Test
    void ac2_backfillsOneExpensePerMissedOccurrenceDatedOnItsActualDueDate() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        createMonthlyRecurringExpense(start, null);

        // Simulates the server being down for months: the first run only happens in April.
        int generated = generateAsOf(LocalDate.of(2026, 4, 15));

        assertThat(generated).isEqualTo(4); // Jan, Feb, Mar, Apr
        assertThat(expenseService.getExpensesForGroup(groupId, aliceId))
                .extracting(ExpenseResponse::expenseDate)
                .containsExactlyInAnyOrder(
                        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1),
                        LocalDate.of(2026, 3, 1), LocalDate.of(2026, 4, 1));
    }

    @Test
    void ac2_runningGenerationTwiceForTheSameDayDoesNotDuplicate() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        createMonthlyRecurringExpense(start, null);

        int firstRun = generateAsOf(start);
        int secondRun = generateAsOf(start);

        assertThat(firstRun).isEqualTo(1);
        assertThat(secondRun).isZero();
        assertThat(expenseService.getExpensesForGroup(groupId, aliceId)).hasSize(1);
    }

    @Test
    void ac4_stopsGeneratingOnceTheEndDateHasPassedAndMarksTheSeriesEnded() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 3, 1);
        RecurringExpenseResponse recurring = createMonthlyRecurringExpense(start, end);

        generateAsOf(LocalDate.of(2026, 6, 1));

        assertThat(expenseService.getExpensesForGroup(groupId, aliceId))
                .extracting(ExpenseResponse::expenseDate)
                .containsExactlyInAnyOrder(
                        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1), LocalDate.of(2026, 3, 1));
        assertThat(recurringExpenseRepository.findById(recurring.id()).orElseThrow().isActive()).isFalse();
    }

    @Test
    void ac4_endedRecurringExpensesAreNotPickedUpAgain() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        createMonthlyRecurringExpense(start, start);

        generateAsOf(start);
        int secondRun = generateAsOf(LocalDate.of(2026, 6, 1));

        assertThat(secondRun).isZero();
        assertThat(expenseService.getExpensesForGroup(groupId, aliceId)).hasSize(1);
    }

    @Test
    void generatedExpenseLinksBackToItsRecurringExpense() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        RecurringExpenseResponse recurring = createMonthlyRecurringExpense(start, null);

        generateAsOf(start);

        List<Expense> expenses = expenseRepository.findByGroupIdOrderByExpenseDateDesc(groupId);
        assertThat(expenses).hasSize(1);
        assertThat(expenses.get(0).getRecurringExpense().getId()).isEqualTo(recurring.id());

        // #13 AC3: the same link is visible through the expense history endpoint's response.
        assertThat(expenseService.getExpensesForGroup(groupId, aliceId))
                .extracting(ExpenseResponse::recurringExpenseId)
                .containsExactly(recurring.id());
    }

    private Map<Long, BigDecimal> balances() {
        return groupService.getMembers(groupId, aliceId).stream()
                .collect(Collectors.toMap(
                        GroupMemberResponse::userId, GroupMemberResponse::netBalance));
    }
}
