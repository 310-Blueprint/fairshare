package nz.ac.auckland.se310.fairshare;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import nz.ac.auckland.se310.fairshare.dto.CreateGroupRequest;
import nz.ac.auckland.se310.fairshare.dto.CreateRecurringExpenseRequest;
import nz.ac.auckland.se310.fairshare.dto.RecurringExpenseResponse;
import nz.ac.auckland.se310.fairshare.exception.GroupAccessDeniedException;
import nz.ac.auckland.se310.fairshare.exception.InvalidEndDateException;
import nz.ac.auckland.se310.fairshare.exception.InvalidPayerException;
import nz.ac.auckland.se310.fairshare.model.RecurringExpense;
import nz.ac.auckland.se310.fairshare.model.User;
import nz.ac.auckland.se310.fairshare.repository.ExpenseGroupRepository;
import nz.ac.auckland.se310.fairshare.repository.RecurringExpenseParticipantRepository;
import nz.ac.auckland.se310.fairshare.repository.RecurringExpenseRepository;
import nz.ac.auckland.se310.fairshare.service.ExpenseGroupService;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.list;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import({TestCurrentUserConfig.class, TestClockConfig.class})
class RecurringExpenseIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4"));

    private static final String CAROL_EMAIL = "carol@test.com";
    private static final String AMOUNT_FIELD = "amount";
    private static final String RENT = "Rent";
    private static final String RENT_AMOUNT = "500.00";
    private static final LocalDate START_DATE = LocalDate.of(2026, 1, 1);

    @Autowired ExpenseGroupService groupService;
    @Autowired RecurringExpenseService recurringExpenseService;
    @Autowired ExpenseGroupRepository groupRepository;
    @Autowired RecurringExpenseRepository recurringExpenseRepository;
    @Autowired RecurringExpenseParticipantRepository participantRepository;
    @Autowired UserRepository userRepository;
    @Autowired Validator validator;
    @Autowired TestClockConfig.MutableClock clock;

    private Long aliceId;
    private Long bobId;
    private Long carolId;
    private Long groupId;
    private List<Long> memberIds;

    @BeforeEach
    void setUp() {
        participantRepository.deleteAll();
        recurringExpenseRepository.deleteAll();
        groupRepository.deleteAll();

        // Keeps "today" safely before every fixture's start date, since creating a recurring
        // expense now also generates anything already due (AC2) - these tests aren't about that.
        clock.setInstant(LocalDate.of(2025, 1, 1).atStartOfDay(ZoneId.systemDefault()).toInstant());

        aliceId = userRepository.findByEmail("alice@test.com").orElseThrow().getId();
        bobId = userRepository.findByEmail("bob@test.com").orElseThrow().getId();
        carolId = userRepository.findByEmail(CAROL_EMAIL)
                .orElseGet(() -> userRepository.save(new User(
                        "carol", "x", CAROL_EMAIL, User.Country.NEW_ZEALAND, User.Currency.NZD)))
                .getId();
        memberIds = List.of(aliceId, bobId, carolId);

        groupId = groupService.createGroup(new CreateGroupRequest("Flat 3", null), aliceId).id();
        groupService.addMember(groupId, "bob@test.com", aliceId);
    }

    @Test
    void ac1_createsRecurringExpenseWithGivenDetailsAndAppearsInGroupList() {
        var request = new CreateRecurringExpenseRequest(
                new BigDecimal(RENT_AMOUNT), RENT, bobId, memberIds,
                RecurringExpense.Frequency.MONTHLY, START_DATE, null);

        RecurringExpenseResponse created = recurringExpenseService.createRecurringExpense(groupId, request, aliceId);

        assertThat(created.id()).isNotNull();
        assertThat(created.groupId()).isEqualTo(groupId);
        assertThat(created.amount()).isEqualByComparingTo(RENT_AMOUNT);
        assertThat(created.description()).isEqualTo(RENT);
        assertThat(created.paidByUserId()).isEqualTo(bobId);
        assertThat(created.paidByUsername()).isEqualTo("bob");
        assertThat(created.frequency()).isEqualTo(RecurringExpense.Frequency.MONTHLY);
        assertThat(created.startDate()).isEqualTo(START_DATE);
        assertThat(created.endDate()).isNull();
        assertThat(created.active()).isTrue();
        assertThat(created.participantUserIds()).containsExactlyInAnyOrder(aliceId, bobId);

        assertThat(recurringExpenseService.getRecurringExpensesForGroup(groupId, aliceId))
                .extracting(RecurringExpenseResponse::id)
                .containsExactly(created.id());
    }

    @Test
    void ac1_nextDueDateStartsAtTheStartDateWithNoOccurrencesGenerated() {
        var request = new CreateRecurringExpenseRequest(
                new BigDecimal(RENT_AMOUNT), RENT, aliceId, memberIds,
                RecurringExpense.Frequency.MONTHLY, START_DATE, null);

        RecurringExpenseResponse created = recurringExpenseService.createRecurringExpense(groupId, request, aliceId);

        assertThat(created.nextDueDate()).isEqualTo(START_DATE);
        RecurringExpense saved = recurringExpenseRepository.findById(created.id()).orElseThrow();
        assertThat(saved.getOccurrenceCount()).isZero();
    }

    @Test
    void ac1_listsGroupRecurringExpensesNewestStartDateFirst() {
        recurringExpenseService.createRecurringExpense(groupId, new CreateRecurringExpenseRequest(
                new BigDecimal(RENT_AMOUNT), RENT, aliceId, memberIds,
                RecurringExpense.Frequency.MONTHLY, START_DATE, null), aliceId);
        recurringExpenseService.createRecurringExpense(groupId, new CreateRecurringExpenseRequest(
                new BigDecimal("15.00"), "Netflix", bobId, memberIds,
                RecurringExpense.Frequency.MONTHLY, START_DATE.plusDays(10), null), aliceId);

        List<RecurringExpenseResponse> recurringExpenses =
                recurringExpenseService.getRecurringExpensesForGroup(groupId, bobId);

        assertThat(recurringExpenses)
                .extracting(RecurringExpenseResponse::description)
                .containsExactly("Netflix", RENT);
    }

    @Test
    void ac5_rejectsMissingFieldsAndNonPositiveAmounts() {
        assertThat(violations(new CreateRecurringExpenseRequest(
                null, "  ", null, memberIds, null, null, null)))
                .containsOnlyKeys(AMOUNT_FIELD, "description", "paidByUserId", "frequency", "startDate");

        assertThat(violations(new CreateRecurringExpenseRequest(
                BigDecimal.ZERO, RENT, aliceId, memberIds, RecurringExpense.Frequency.WEEKLY, START_DATE, null)))
                .extractingByKey(AMOUNT_FIELD, list(String.class))
                .contains("Amount must be a positive number");

        assertThat(violations(new CreateRecurringExpenseRequest(
                new BigDecimal("-5.00"), RENT, aliceId, memberIds, RecurringExpense.Frequency.WEEKLY, START_DATE, null)))
                .extractingByKey(AMOUNT_FIELD, list(String.class))
                .contains("Amount must be a positive number");
    }

    @Test
    void ac5_rejectsAmountsSmallerThanOneCent() {
        assertThat(violations(new CreateRecurringExpenseRequest(
                new BigDecimal("0.004"), RENT, aliceId, memberIds, RecurringExpense.Frequency.WEEKLY, START_DATE, null)))
                .extractingByKey(AMOUNT_FIELD, list(String.class))
                .contains("Amount must be at least 0.01");
    }

    @Test
    void ac5_rejectsAmountsWithMoreThanTwoDecimalPlaces() {
        assertThat(violations(new CreateRecurringExpenseRequest(
                new BigDecimal("10.005"), RENT, aliceId, memberIds, RecurringExpense.Frequency.WEEKLY, START_DATE, null)))
                .extractingByKey(AMOUNT_FIELD, list(String.class))
                .containsExactly("Amounts should only have up to 2 decimal places.");

        assertThat(violations(new CreateRecurringExpenseRequest(
                new BigDecimal("0.001"), RENT, aliceId, memberIds, RecurringExpense.Frequency.WEEKLY, START_DATE, null)))
                .extractingByKey(AMOUNT_FIELD, list(String.class))
                .contains("Amounts should only have up to 2 decimal places.");

        assertThat(violations(new CreateRecurringExpenseRequest(
                new BigDecimal("10.00"), RENT, aliceId, memberIds, RecurringExpense.Frequency.WEEKLY, START_DATE, null)))
                .isEmpty();

        assertThat(violations(new CreateRecurringExpenseRequest(
                new BigDecimal("10.01"), RENT, aliceId, memberIds, RecurringExpense.Frequency.WEEKLY, START_DATE, null)))
                .isEmpty();
    }

    @Test
    void ac5_rejectsWhenFrequencyIsUnset() {
        assertThat(violations(new CreateRecurringExpenseRequest(
                new BigDecimal(RENT_AMOUNT), RENT, aliceId, memberIds, null, START_DATE, null)))
                .extractingByKey("frequency", list(String.class))
                .containsExactly("Frequency is required");
    }

    @Test
    void ac5_rejectsAnEndDateBeforeTheStartDate() {
        var request = new CreateRecurringExpenseRequest(
                new BigDecimal(RENT_AMOUNT), RENT, aliceId, memberIds,
                RecurringExpense.Frequency.MONTHLY, START_DATE, START_DATE.minusDays(1));

        assertThatThrownBy(() -> recurringExpenseService.createRecurringExpense(groupId, request, aliceId))
                .isInstanceOf(InvalidEndDateException.class);
    }

    @Test
    void ac5_acceptsAnEndDateEqualToTheStartDate() {
        var request = new CreateRecurringExpenseRequest(
                new BigDecimal(RENT_AMOUNT), RENT, aliceId, memberIds,
                RecurringExpense.Frequency.MONTHLY, START_DATE, START_DATE);

        RecurringExpenseResponse created = recurringExpenseService.createRecurringExpense(groupId, request, aliceId);

        assertThat(created.endDate()).isEqualTo(START_DATE);
    }

    @Test
    void ac5_payerMustBeAGroupMember() {
        var request = new CreateRecurringExpenseRequest(
                new BigDecimal(RENT_AMOUNT), RENT, carolId, memberIds,
                RecurringExpense.Frequency.MONTHLY, START_DATE, null);

        assertThatThrownBy(() -> recurringExpenseService.createRecurringExpense(groupId, request, aliceId))
                .isInstanceOf(InvalidPayerException.class);
    }

    @Test
    void nonMemberCannotCreateOrViewRecurringExpenses() {
        var request = new CreateRecurringExpenseRequest(
                new BigDecimal(RENT_AMOUNT), RENT, aliceId, memberIds,
                RecurringExpense.Frequency.MONTHLY, START_DATE, null);

        assertThatThrownBy(() -> recurringExpenseService.createRecurringExpense(groupId, request, carolId))
                .isInstanceOf(GroupAccessDeniedException.class);
        assertThatThrownBy(() -> recurringExpenseService.getRecurringExpensesForGroup(groupId, carolId))
                .isInstanceOf(GroupAccessDeniedException.class);
    }

    @Test
    void getRecurringExpenseReturnsTheRequestedOne() {
        RecurringExpenseResponse created = recurringExpenseService.createRecurringExpense(groupId,
                new CreateRecurringExpenseRequest(new BigDecimal(RENT_AMOUNT), RENT, aliceId, memberIds,
                        RecurringExpense.Frequency.MONTHLY, START_DATE, null), aliceId);

        RecurringExpenseResponse fetched = recurringExpenseService.getRecurringExpense(groupId, created.id(), bobId);

        assertThat(fetched.id()).isEqualTo(created.id());
        assertThat(fetched.description()).isEqualTo(RENT);
    }

    /** A field can break more than one constraint at a time, so every message is kept. */
    private Map<String, List<String>> violations(CreateRecurringExpenseRequest request) {
        return validator.validate(request).stream()
                .collect(Collectors.groupingBy(
                        violation -> violation.getPropertyPath().toString(),
                        Collectors.mapping(ConstraintViolation::getMessage, Collectors.toList())));
    }
}
