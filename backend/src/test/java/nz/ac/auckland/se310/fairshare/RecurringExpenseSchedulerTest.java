package nz.ac.auckland.se310.fairshare;

import nz.ac.auckland.se310.fairshare.dto.CreateGroupRequest;
import nz.ac.auckland.se310.fairshare.dto.CreateRecurringExpenseRequest;
import nz.ac.auckland.se310.fairshare.model.RecurringExpense;
import nz.ac.auckland.se310.fairshare.repository.ExpenseGroupRepository;
import nz.ac.auckland.se310.fairshare.repository.ExpenseRepository;
import nz.ac.auckland.se310.fairshare.repository.ExpenseShareRepository;
import nz.ac.auckland.se310.fairshare.repository.RecurringExpenseParticipantRepository;
import nz.ac.auckland.se310.fairshare.repository.RecurringExpenseRepository;
import nz.ac.auckland.se310.fairshare.service.ExpenseGroupService;
import nz.ac.auckland.se310.fairshare.service.RecurringExpenseScheduler;
import nz.ac.auckland.se310.fairshare.service.RecurringExpenseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.Scheduled;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import({TestCurrentUserConfig.class, TestClockConfig.class})
class RecurringExpenseSchedulerTest {

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4"));

    @Autowired ExpenseGroupService groupService;
    @Autowired RecurringExpenseService recurringExpenseService;
    @Autowired RecurringExpenseScheduler scheduler;
    @Autowired ExpenseGroupRepository groupRepository;
    @Autowired RecurringExpenseRepository recurringExpenseRepository;
    @Autowired RecurringExpenseParticipantRepository participantRepository;
    @Autowired ExpenseRepository expenseRepository;
    @Autowired ExpenseShareRepository expenseShareRepository;
    @Autowired UserRepository userRepository;
    @Autowired TestClockConfig.MutableClock clock;

    @BeforeEach
    void setUp() {
        expenseShareRepository.deleteAll();
        expenseRepository.deleteAll();
        participantRepository.deleteAll();
        recurringExpenseRepository.deleteAll();
        groupRepository.deleteAll();

        // Keeps "today" safely before the fixture's start date, so creating the recurring expense
        // doesn't itself trigger generation - this test controls that explicitly below.
        clock.setInstant(LocalDate.of(2025, 1, 1).atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    @Test
    void runsTheGenerationServiceForDueRecurringExpenses() {
        Long aliceId = userRepository.findByEmail("alice@test.com").orElseThrow().getId();
        Long bobId = userRepository.findByEmail("bob@test.com").orElseThrow().getId();
        Long groupId = groupService.createGroup(new CreateGroupRequest("Flat 3", null), aliceId).id();
        groupService.addMember(groupId, "bob@test.com", aliceId);

        LocalDate start = LocalDate.of(2026, 1, 1);
        recurringExpenseService.createRecurringExpense(groupId,
                new CreateRecurringExpenseRequest(new BigDecimal("500.00"), "Rent", aliceId, List.of(aliceId, bobId),
                        RecurringExpense.Frequency.MONTHLY, start, null), aliceId);

        clock.setInstant(start.atStartOfDay(ZoneId.systemDefault()).toInstant());
        scheduler.generateDueExpenses();

        assertThat(expenseRepository.findByGroupIdOrderByExpenseDateDesc(groupId)).hasSize(1);
    }

    @Test
    void isScheduledToRunOnceADay() throws NoSuchMethodException {
        Scheduled scheduled = RecurringExpenseScheduler.class
                .getMethod("generateDueExpenses")
                .getAnnotation(Scheduled.class);

        assertThat(scheduled).isNotNull();
        assertThat(scheduled.cron()).isEqualTo("0 0 0 * * *");
    }
}
