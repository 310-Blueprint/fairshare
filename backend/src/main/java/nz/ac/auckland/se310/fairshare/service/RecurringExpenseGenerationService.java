package nz.ac.auckland.se310.fairshare.service;

import nz.ac.auckland.se310.fairshare.exception.InvalidPayerException;
import nz.ac.auckland.se310.fairshare.model.ExpenseGroup;
import nz.ac.auckland.se310.fairshare.model.RecurringExpense;
import nz.ac.auckland.se310.fairshare.model.UserInGroup;
import nz.ac.auckland.se310.fairshare.repository.RecurringExpenseParticipantRepository;
import nz.ac.auckland.se310.fairshare.repository.RecurringExpenseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
public class RecurringExpenseGenerationService {

    private static final Logger log = LoggerFactory.getLogger(RecurringExpenseGenerationService.class);

    private final RecurringExpenseRepository recurringExpenseRepository;
    private final RecurringExpenseParticipantRepository participantRepository;
    private final ExpenseService expenseService;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public RecurringExpenseGenerationService(RecurringExpenseRepository recurringExpenseRepository,
                                              RecurringExpenseParticipantRepository participantRepository,
                                              ExpenseService expenseService,
                                              Clock clock,
                                              PlatformTransactionManager transactionManager) {
        this.recurringExpenseRepository = recurringExpenseRepository;
        this.participantRepository = participantRepository;
        this.expenseService = expenseService;
        this.clock = clock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * Generates an Expense for every occurrence of every active recurring expense that has come
     * due, up to and including today. A schedule that was missed for a while (e.g. the server
     * was down) backfills one expense per missed occurrence, each dated on its actual due date;
     * advancing nextDueDate after each one makes a repeat run for the same day a no-op (AC2).
     *
     * Each recurring expense runs in its own transaction. If its payer or every participant has
     * since left the group, that one is skipped and logged rather than aborting the whole run -
     * otherwise a single invalid recurrence would block every other due recurrence from generating.
     */
    public int generateDueExpenses() {
        LocalDate today = LocalDate.now(clock);
        int generated = 0;

        List<Long> dueRecurringExpenseIds = recurringExpenseRepository
                .findByActiveTrueAndNextDueDateLessThanEqual(today).stream()
                .map(RecurringExpense::getId)
                .toList();

        for (Long recurringExpenseId : dueRecurringExpenseIds) {
            try {
                Integer generatedForThisOne = transactionTemplate.execute(
                        status -> generateDueOccurrencesForId(recurringExpenseId));
                generated += generatedForThisOne;
            } catch (RuntimeException e) {
                log.error("Skipping recurring expense {}: failed to generate its due occurrences",
                        recurringExpenseId, e);
            }
        }

        return generated;
    }

    private int generateDueOccurrencesForId(Long recurringExpenseId) {
        return recurringExpenseRepository.findById(recurringExpenseId)
                .map(recurringExpense -> generateDueOccurrences(recurringExpense, LocalDate.now(clock)))
                .orElse(0);
    }

    /**
     * Generates any occurrences already due for a single recurring expense, using the same
     * backfill logic as the daily job. Called right after creation so a start date that's today
     * or earlier produces its expenses immediately, rather than waiting for the next scheduled run.
     */
    @Transactional
    public int generateDueOccurrencesFor(RecurringExpense recurringExpense) {
        return generateDueOccurrences(recurringExpense, LocalDate.now(clock));
    }

    private int generateDueOccurrences(RecurringExpense recurringExpense, LocalDate today) {
        ExpenseGroup group = recurringExpense.getGroup();

        UserInGroup payer = group.getMember(recurringExpense.getPaidBy().getId());
        if (payer == null) {
            throw new InvalidPayerException(recurringExpense.getPaidBy().getId());
        }

        List<UserInGroup> members = resolveMembers(recurringExpense, group);
        if (members.isEmpty()) {
            throw new IllegalStateException(
                    "No valid participants found in the group for recurring expense " + recurringExpense.getId());
        }

        int generated = 0;
        while (recurringExpense.isActive() && !recurringExpense.getNextDueDate().isAfter(today)) {
            expenseService.createRecurringOccurrence(
                    recurringExpense, payer, members, recurringExpense.getNextDueDate());
            recurringExpense.advanceAfterGenerating(); // AC4: ends the series once past endDate
            generated++;
        }

        recurringExpenseRepository.save(recurringExpense);
        return generated;
    }

    private List<UserInGroup> resolveMembers(RecurringExpense recurringExpense, ExpenseGroup group) {
        return participantRepository.findByRecurringExpenseId(recurringExpense.getId()).stream()
                .map(participant -> group.getMember(participant.getUser().getId()))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingLong(m -> m.getUser().getId()))
                .toList();
    }
}
