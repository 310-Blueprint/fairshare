package nz.ac.auckland.se310.fairshare;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import nz.ac.auckland.se310.fairshare.dto.CreateExpenseRequest;
import nz.ac.auckland.se310.fairshare.dto.CreateGroupRequest;
import nz.ac.auckland.se310.fairshare.dto.ExpenseResponse;
import nz.ac.auckland.se310.fairshare.dto.GroupMemberResponse;
import nz.ac.auckland.se310.fairshare.dto.SettlementLine;
import nz.ac.auckland.se310.fairshare.dto.SettlementRequest;
import nz.ac.auckland.se310.fairshare.exception.ExchangeRateUnavailableException;
import nz.ac.auckland.se310.fairshare.exception.GroupAccessDeniedException;
import nz.ac.auckland.se310.fairshare.exception.InvalidPayerException;
import nz.ac.auckland.se310.fairshare.exception.UnsupportedCurrencyException;
import nz.ac.auckland.se310.fairshare.model.User;
import nz.ac.auckland.se310.fairshare.repository.ExpenseGroupRepository;
import nz.ac.auckland.se310.fairshare.repository.ExpenseRepository;
import nz.ac.auckland.se310.fairshare.repository.ExpenseShareRepository;
import nz.ac.auckland.se310.fairshare.repository.SettlementRepository;
import nz.ac.auckland.se310.fairshare.service.ExpenseGroupService;
import nz.ac.auckland.se310.fairshare.service.ExpenseService;
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
import java.time.Month;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.list;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import({TestCurrentUserConfig.class, TestExchangeRateConfig.class})
class ExpenseIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4"));

    private static final String CAROL_EMAIL = "carol@test.com";
    private static final String AMOUNT_FIELD = "amount";
    private static final String GROCERIES = "Groceries";
    private static final String GROCERIES_AMOUNT = "42.50";
    private static final String TAXI_AMOUNT = "10.00";

    @Autowired ExpenseGroupService groupService;
    @Autowired ExpenseService expenseService;
    @Autowired ExpenseGroupRepository groupRepository;
    @Autowired ExpenseRepository expenseRepository;
    @Autowired ExpenseShareRepository expenseShareRepository;
    @Autowired SettlementRepository settlementRepository;
    @Autowired UserRepository userRepository;
    @Autowired Validator validator;
    @Autowired TestExchangeRateConfig.StubExchangeRateProvider exchangeRates;

    private Long aliceId;
    private Long bobId;
    private Long carolId;
    private Long groupId;
    private List<Long> memberIds;

    @BeforeEach
    void setUp() {
        expenseShareRepository.deleteAll();
        expenseRepository.deleteAll();
        settlementRepository.deleteAll();
        groupRepository.deleteAll();

        aliceId = userRepository.findByEmail("alice@test.com").orElseThrow().getId();
        bobId = userRepository.findByEmail("bob@test.com").orElseThrow().getId();
        carolId = userRepository.findByEmail(CAROL_EMAIL)
                .orElseGet(() -> userRepository.save(new User(
                        "carol", "x", CAROL_EMAIL, User.Country.NEW_ZEALAND, User.Currency.NZD)))
                .getId();
        memberIds = List.of(aliceId, bobId, carolId);

        groupId = groupService.createGroup(new CreateGroupRequest("Flat 3", null), aliceId).id();
        groupService.addMember(groupId, "bob@test.com", aliceId);

        exchangeRates.reset();
        exchangeRates.setRate("USD", "NZD", "1.7056");
        exchangeRates.setRate("EUR", "NZD", "1.9500");
    }

    @Test
    void ac1_createsExpenseWithGivenDetails() {
        var request = new CreateExpenseRequest(
                new BigDecimal(GROCERIES_AMOUNT), GROCERIES, bobId, memberIds, LocalDate.of(2026, Month.AUGUST, 1));

        ExpenseResponse created = expenseService.createExpense(groupId, request, aliceId);

        assertThat(created.id()).isNotNull();
        assertThat(created.groupId()).isEqualTo(groupId);
        assertThat(created.amount()).isEqualByComparingTo(GROCERIES_AMOUNT);
        assertThat(created.description()).isEqualTo(GROCERIES);
        assertThat(created.paidByUserId()).isEqualTo(bobId);
        assertThat(created.paidByUsername()).isEqualTo("bob");
        assertThat(created.expenseDate()).isEqualTo(LocalDate.of(2026, Month.AUGUST, 1));
        assertThat(created.participantUserIds()).containsExactlyInAnyOrder(aliceId, bobId);
        assertThat(created.recurringExpenseId()).isNull(); // #13 AC3: not generated by a recurring expense
        assertThat(expenseRepository.findById(created.id())).isPresent();
    }

    @Test
    void ac1_balancesReflectTheNewExpense() { // Also tests #8 AC4 & AC5
        var request = new CreateExpenseRequest(new BigDecimal(GROCERIES_AMOUNT), GROCERIES, bobId, memberIds, null);

        expenseService.createExpense(groupId, request, aliceId);

        assertThat(balances()).containsOnly(
                Map.entry(aliceId, new BigDecimal("21.25")),
                Map.entry(bobId, new BigDecimal("-21.25")));
    }

    @Test
    void ac2AndAc3_rejectsMissingFieldsAndNonPositiveAmounts() {
        assertThat(violations(new CreateExpenseRequest(null, "  ", null, memberIds, null)))
                .containsOnlyKeys(AMOUNT_FIELD, "description", "paidByUserId");

        assertThat(violations(new CreateExpenseRequest(BigDecimal.ZERO, "Taxi", aliceId, memberIds, null)))
                .extractingByKey(AMOUNT_FIELD, list(String.class))
                .contains("Amount must be a positive number");

        assertThat(violations(new CreateExpenseRequest(new BigDecimal("-5.00"), "Taxi", aliceId, memberIds, null)))
                .extractingByKey(AMOUNT_FIELD, list(String.class))
                .contains("Amount must be a positive number");
    }

    @Test
    void ac3_rejectsAmountsSmallerThanOneCent() {
        assertThat(violations(new CreateExpenseRequest(new BigDecimal("0.004"), "Taxi", aliceId, memberIds, null)))
                .extractingByKey(AMOUNT_FIELD, list(String.class))
                .containsExactly("Amount must be at least 0.01");
    }

    @Test
    void rejectsAmountsTooLargeToStore() {
        // expense.original_amount is DECIMAL(15,2), so larger amounts would fail with a 500.
        assertThat(violations(new CreateExpenseRequest(new BigDecimal("10000000000000.00"), "Taxi", aliceId, memberIds, null)))
                .extractingByKey(AMOUNT_FIELD, list(String.class))
                .containsExactly("Amount must be at most 9,999,999,999,999.99");

        assertThat(violations(new CreateExpenseRequest(new BigDecimal("9999999999999.99"), "Taxi", aliceId, memberIds, null)))
                .isEmpty();
    }

    @Test
    void amountsAboveTheOldDecimal10LimitAreStored() {
        // Review of #75: 135,000,000 did not fit the old DECIMAL(10,2) columns.
        exchangeRates.setRate("USD", "NZD", "1350");
        var request = new CreateExpenseRequest(
                new BigDecimal("100000.00"), "Car", aliceId, memberIds, null, "USD");

        ExpenseResponse created = expenseService.createExpense(groupId, request, aliceId);

        assertThat(expenseService.getExpense(groupId, created.id(), aliceId).amount())
                .isEqualByComparingTo("135000000.00");
        assertThat(balances()).containsOnly(
                Map.entry(aliceId, new BigDecimal("-67500000.00")),
                Map.entry(bobId, new BigDecimal("67500000.00")));
        // Settling up stores the same size of amount in the settlement table.
        assertThat(groupService.computeSettlement(groupId, aliceId, new SettlementRequest(List.of())))
                .extracting(SettlementLine::amount)
                .containsExactly(new BigDecimal("67500000.00"));
    }

    @Test
    void ac4_splitsEquallyAcrossAllCurrentMembers() { // Also tests #8 AC1
        groupService.addMember(groupId, CAROL_EMAIL, aliceId);

        var request = new CreateExpenseRequest(new BigDecimal("90.00"), "Power bill", aliceId, memberIds, null);

        expenseService.createExpense(groupId, request, aliceId);

        assertThat(balances()).containsOnly(
                Map.entry(aliceId, new BigDecimal("-60.00")),
                Map.entry(bobId, new BigDecimal("30.00")),
                Map.entry(carolId, new BigDecimal("30.00")));
    }

    @Test
    void ac4_unevenSplitStillSumsToTheAmount() {
        groupService.addMember(groupId, CAROL_EMAIL, aliceId);

        var request = new CreateExpenseRequest(new BigDecimal("100.00"), "Internet", aliceId, List.of(aliceId, aliceId, bobId, carolId), null);

        expenseService.createExpense(groupId, request, aliceId);

        // The extra cent goes to the lowest user id, so the shares add back up to 100.00.
        assertThat(balances()).containsOnly(
                Map.entry(aliceId, new BigDecimal("-66.66")),
                Map.entry(bobId, new BigDecimal("33.33")),
                Map.entry(carolId, new BigDecimal("33.33")));
        assertThat(balances().values().stream().reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("0.00");
    }

    @Test
    void ac4_duplicateParticipantIdsAreCountedOnce() {
        var request = new CreateExpenseRequest(
                new BigDecimal("20.00"), "Taxi", aliceId, List.of(aliceId, aliceId, bobId), null);

        ExpenseResponse created = expenseService.createExpense(groupId, request, aliceId);

        assertThat(created.participantUserIds()).containsExactlyInAnyOrder(aliceId, bobId);
        assertThat(balances()).containsOnly(
                Map.entry(aliceId, new BigDecimal("-10.00")),
                Map.entry(bobId, new BigDecimal("10.00")));
    }

    @Test
    void ac5_payerMustBeAGroupMember() {
        var request = new CreateExpenseRequest(new BigDecimal(TAXI_AMOUNT), "Taxi", carolId, memberIds, null);

        assertThatThrownBy(() -> expenseService.createExpense(groupId, request, aliceId))
                .isInstanceOf(InvalidPayerException.class);
    }

    @Test
    void ac6_expenseDateDefaultsToTodayWhenOmitted() {
        var request = new CreateExpenseRequest(new BigDecimal(TAXI_AMOUNT), "Taxi", aliceId, memberIds, null);

        ExpenseResponse created = expenseService.createExpense(groupId, request, aliceId);

        assertThat(created.expenseDate()).isEqualTo(LocalDate.now());
    }

    @Test
    void ac6_rejectsAFutureDateAndAcceptsAPastOne() {
        assertThat(violations(new CreateExpenseRequest(
                new BigDecimal(TAXI_AMOUNT), "Taxi", aliceId, memberIds, LocalDate.now().plusDays(1))))
                .extractingByKey("expenseDate", list(String.class))
                .containsExactly("Expense date cannot be in the future");

        assertThat(violations(new CreateExpenseRequest(
                new BigDecimal(TAXI_AMOUNT), "Taxi", aliceId, memberIds, LocalDate.now().minusDays(30))))
                .isEmpty();
    }

    @Test
    void ac7_listsGroupExpensesForEveryMemberNewestFirst() {
        expenseService.createExpense(groupId, new CreateExpenseRequest(
                new BigDecimal(TAXI_AMOUNT), "Taxi", aliceId, memberIds, LocalDate.now().minusDays(3)), aliceId);
        expenseService.createExpense(groupId, new CreateExpenseRequest(
                new BigDecimal("20.00"), "Pizza", bobId, memberIds, LocalDate.now().minusDays(1)), aliceId);

        List<ExpenseResponse> expenses = expenseService.getExpensesForGroup(groupId, bobId);

        assertThat(expenses)
                .extracting(ExpenseResponse::description)
                .containsExactly("Pizza", "Taxi");
        assertThat(expenses.getFirst().paidByUsername()).isEqualTo("bob");
        assertThat(expenses.getFirst().amount()).isEqualByComparingTo("20.00");
        assertThat(expenses.getFirst().expenseDate()).isEqualTo(LocalDate.now().minusDays(1));
    }

    @Test
    void ac8_nonMemberCannotCreateOrViewExpenses() {
        var request = new CreateExpenseRequest(new BigDecimal(TAXI_AMOUNT), "Taxi", aliceId, memberIds, null);

        assertThatThrownBy(() -> expenseService.createExpense(groupId, request, carolId))
                .isInstanceOf(GroupAccessDeniedException.class);
        assertThatThrownBy(() -> expenseService.getExpensesForGroup(groupId, carolId))
                .isInstanceOf(GroupAccessDeniedException.class);
    }

    // Issue #8 tests:

    @Test
    void ac3_splitsAcrossASubsetOfMembers() {
        groupService.addMember(groupId, CAROL_EMAIL, aliceId);

        var request = new CreateExpenseRequest(new BigDecimal("90.00"), "Power bill", aliceId, List.of(aliceId, carolId), null);

        expenseService.createExpense(groupId, request, aliceId);

        assertThat(balances()).containsOnly(
                Map.entry(aliceId, new BigDecimal("-45.00")),
                Map.entry(bobId, new BigDecimal("0.00")),
                Map.entry(carolId, new BigDecimal("45.00")));
    }

    @Test
    void ac6_atLeastOneMemberMustBeIncludedInTheSplit() {
        var request = new CreateExpenseRequest(new BigDecimal(TAXI_AMOUNT), "Taxi", aliceId, List.of(), null);

        assertThat(violations(request))
                .extractingByKey("participantUserIds", list(String.class))
                .containsExactly("At least one participant is required");
    }

    @Test
    void ac7_editingAnExpenseUpdatesTheBalances() {
        var request = new CreateExpenseRequest(new BigDecimal(GROCERIES_AMOUNT), GROCERIES, bobId, memberIds, null);
        ExpenseResponse created = expenseService.createExpense(groupId, request, aliceId);

        var updateRequest = new CreateExpenseRequest(new BigDecimal("50.00"), "Groceries and snacks", bobId, memberIds, null);
        expenseService.updateExpense(groupId, updateRequest, aliceId, created.id());

        assertThat(balances()).containsOnly(
                Map.entry(bobId, new BigDecimal("-25.00")),
                Map.entry(aliceId, new BigDecimal("25.00")));
    }

    @Test
    void ac7_editingAnExpensePersistsTheNewExpenseDate() {
        LocalDate originalDate = LocalDate.of(2026, Month.AUGUST, 1);
        LocalDate updatedDate = LocalDate.of(2026, Month.AUGUST, 15);
        ExpenseResponse created = expenseService.createExpense(groupId, new CreateExpenseRequest(
                new BigDecimal(GROCERIES_AMOUNT), GROCERIES, bobId, memberIds, originalDate), aliceId);

        expenseService.updateExpense(groupId, new CreateExpenseRequest(
                new BigDecimal(GROCERIES_AMOUNT), GROCERIES, bobId, memberIds, updatedDate), aliceId, created.id());

        assertThat(expenseService.getExpense(groupId, created.id(), aliceId).expenseDate())
                .isEqualTo(updatedDate);
    }

    @Test 
    void ac7_removingAParticipantFromAnExpenseUpdatesTheBalances() {
        groupService.addMember(groupId, CAROL_EMAIL, aliceId);
        var request = new CreateExpenseRequest(new BigDecimal("90.00"), "Power bill", aliceId, List.of(aliceId, bobId, carolId), null);
        ExpenseResponse created = expenseService.createExpense(groupId, request, aliceId);

        var updateRequest = new CreateExpenseRequest(new BigDecimal("90.00"), "Power bill", aliceId, List.of(aliceId, bobId), null);
        expenseService.updateExpense(groupId, updateRequest, aliceId, created.id());

        assertThat(balances()).containsOnly(
                Map.entry(aliceId, new BigDecimal("-45.00")),
                Map.entry(bobId, new BigDecimal("45.00")),
                Map.entry(carolId, new BigDecimal("0.00")));
    }

    // Issue #14 tests:

    @Test
    void currency_ac1_foreignExpenseIsStoredWithOriginalAmountAndCurrency() {
        var request = new CreateExpenseRequest(
                new BigDecimal("20.00"), "Dinner", aliceId, memberIds, LocalDate.of(2026, Month.AUGUST, 1), "USD");

        ExpenseResponse created = expenseService.createExpense(groupId, request, aliceId);

        // Read back from the database, as the expense history does.
        ExpenseResponse stored = expenseService.getExpense(groupId, created.id(), bobId);
        assertThat(stored.originalAmount()).isEqualByComparingTo("20.00");
        assertThat(stored.originalCurrency()).isEqualTo("USD");
        assertThat(stored.exchangeRate()).isEqualByComparingTo("1.7056");
        assertThat(stored.amount()).isEqualByComparingTo("34.11");
        assertThat(expenseService.getExpensesForGroup(groupId, bobId))
                .extracting(ExpenseResponse::originalCurrency)
                .containsExactly("USD");
    }

    @Test
    void currency_ac1_balancesAreInTheGroupBaseCurrency() {
        var request = new CreateExpenseRequest(
                new BigDecimal("20.00"), "Dinner", aliceId, memberIds, null, "USD");

        expenseService.createExpense(groupId, request, aliceId);

        // NZD 34.11 split two ways; the extra cent goes to the lowest user id (alice).
        assertThat(balances()).containsOnly(
                Map.entry(aliceId, new BigDecimal("-17.05")),
                Map.entry(bobId, new BigDecimal("17.05")));
    }

    @Test
    void currency_ac1_existingClientsWithoutACurrencyUseTheGroupBaseCurrency() {
        var request = new CreateExpenseRequest(new BigDecimal(TAXI_AMOUNT), "Taxi", aliceId, memberIds, null);

        ExpenseResponse created = expenseService.createExpense(groupId, request, aliceId);

        assertThat(created.originalCurrency()).isEqualTo("NZD");
        assertThat(created.exchangeRate()).isEqualByComparingTo("1");
        assertThat(created.amount()).isEqualByComparingTo(TAXI_AMOUNT);
    }

    @Test
    void currency_ac1_editingTheCurrencyReconvertsAndRebalances() {
        ExpenseResponse created = expenseService.createExpense(groupId, new CreateExpenseRequest(
                new BigDecimal("20.00"), "Dinner", aliceId, memberIds, null, "USD"), aliceId);

        expenseService.updateExpense(groupId, new CreateExpenseRequest(
                new BigDecimal("20.00"), "Dinner", aliceId, memberIds, null, "EUR"), aliceId, created.id());

        ExpenseResponse updated = expenseService.getExpense(groupId, created.id(), aliceId);
        assertThat(updated.originalCurrency()).isEqualTo("EUR");
        assertThat(updated.amount()).isEqualByComparingTo("39.00");
        assertThat(balances()).containsOnly(
                Map.entry(aliceId, new BigDecimal("-19.50")),
                Map.entry(bobId, new BigDecimal("19.50")));
    }

    @Test
    void currency_ac3_unsupportedCurrencyIsRejected() {
        var request = new CreateExpenseRequest(
                new BigDecimal(TAXI_AMOUNT), "Taxi", aliceId, memberIds, null, "XYZ");

        assertThatThrownBy(() -> expenseService.createExpense(groupId, request, aliceId))
                .isInstanceOf(UnsupportedCurrencyException.class);
        assertThat(expenseRepository.count()).isZero();
    }

    @Test
    void currency_ac2_unavailableRateRejectsTheExpenseAndSavesNothing() {
        exchangeRates.reset(); // the rate service has nothing for USD -> NZD
        var request = new CreateExpenseRequest(
                new BigDecimal("20.00"), "Dinner", aliceId, memberIds, null, "USD");

        assertThatThrownBy(() -> expenseService.createExpense(groupId, request, aliceId))
                .isInstanceOf(ExchangeRateUnavailableException.class)
                .hasMessageContaining("from USD to NZD is unavailable");

        assertThat(expenseRepository.count()).isZero();
        assertThat(expenseShareRepository.count()).isZero();
        assertThat(balances()).containsOnly(
                Map.entry(aliceId, new BigDecimal("0.00")),
                Map.entry(bobId, new BigDecimal("0.00")));
    }

    @Test
    void currency_ac2_unavailableRateLeavesAnEditedExpenseUnchanged() {
        ExpenseResponse created = expenseService.createExpense(groupId, new CreateExpenseRequest(
                new BigDecimal("20.00"), "Dinner", aliceId, memberIds, null, "USD"), aliceId);
        Map<Long, BigDecimal> balancesBefore = balances();

        exchangeRates.reset();
        var update = new CreateExpenseRequest(
                new BigDecimal("50.00"), "Dinner and drinks", bobId, memberIds, null, "EUR");
        Long expenseId = created.id();

        assertThatThrownBy(() -> expenseService.updateExpense(groupId, update, aliceId, expenseId))
                .isInstanceOf(ExchangeRateUnavailableException.class);

        ExpenseResponse unchanged = expenseService.getExpense(groupId, expenseId, aliceId);
        assertThat(unchanged.description()).isEqualTo("Dinner");
        assertThat(unchanged.paidByUserId()).isEqualTo(aliceId);
        assertThat(unchanged.originalCurrency()).isEqualTo("USD");
        assertThat(unchanged.amount()).isEqualByComparingTo("34.11");
        assertThat(balances()).isEqualTo(balancesBefore);
    }

    private Map<Long, BigDecimal> balances() {
        return groupService.getMembers(groupId, aliceId).stream()
                .collect(Collectors.toMap(
                        GroupMemberResponse::userId, GroupMemberResponse::netBalance));
    }

    /** A field can break more than one constraint at a time, so every message is kept. */
    private Map<String, List<String>> violations(CreateExpenseRequest request) {
        return validator.validate(request).stream()
                .collect(Collectors.groupingBy(
                        violation -> violation.getPropertyPath().toString(),
                        Collectors.mapping(ConstraintViolation::getMessage, Collectors.toList())));
    }
}
