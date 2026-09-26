package nz.ac.auckland.se310.fairshare.service;

import nz.ac.auckland.se310.fairshare.dto.CreateRecurringExpenseRequest;
import nz.ac.auckland.se310.fairshare.dto.RecurringExpenseResponse;
import nz.ac.auckland.se310.fairshare.exception.GroupAccessDeniedException;
import nz.ac.auckland.se310.fairshare.exception.InvalidEndDateException;
import nz.ac.auckland.se310.fairshare.exception.InvalidPayerException;
import nz.ac.auckland.se310.fairshare.exception.RecurringExpenseNotFoundException;
import nz.ac.auckland.se310.fairshare.model.ExpenseGroup;
import nz.ac.auckland.se310.fairshare.model.RecurringExpense;
import nz.ac.auckland.se310.fairshare.model.RecurringExpenseParticipant;
import nz.ac.auckland.se310.fairshare.model.UserInGroup;
import nz.ac.auckland.se310.fairshare.repository.ExpenseGroupRepository;
import nz.ac.auckland.se310.fairshare.repository.RecurringExpenseParticipantRepository;
import nz.ac.auckland.se310.fairshare.repository.RecurringExpenseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
public class RecurringExpenseService {

    private final RecurringExpenseRepository recurringExpenseRepository;
    private final ExpenseGroupRepository groupRepository;
    private final RecurringExpenseParticipantRepository participantRepository;
    private final RecurringExpenseGenerationService generationService;

    public RecurringExpenseService(RecurringExpenseRepository recurringExpenseRepository,
                                    ExpenseGroupRepository groupRepository,
                                    RecurringExpenseParticipantRepository participantRepository,
                                    RecurringExpenseGenerationService generationService) {
        this.recurringExpenseRepository = recurringExpenseRepository;
        this.groupRepository = groupRepository;
        this.participantRepository = participantRepository;
        this.generationService = generationService;
    }

    /**
     * Validates that the current user belongs to the group, confirms the payer is a member, and
     * saves the recurring expense. Immediately generates any occurrence already due (e.g. a start
     * date in the past), rather than waiting for the next scheduled run; the daily job then takes
     * over for occurrences that fall due afterwards.
     */
    @Transactional
    public RecurringExpenseResponse createRecurringExpense(Long groupId, CreateRecurringExpenseRequest request, Long currentUserId) {
        ExpenseGroup group = groupRepository.findByIdAndMembersUserId(groupId, currentUserId)
                .orElseThrow(GroupAccessDeniedException::new); // AC1

        UserInGroup payer = group.getMember(request.paidByUserId());
        if (payer == null) {
            throw new InvalidPayerException(request.paidByUserId()); // AC5
        }

        validateEndDate(request.startDate(), request.endDate()); // AC5

        List<UserInGroup> members = resolveMembers(group, request.participantUserIds());

        RecurringExpense recurringExpense = new RecurringExpense(
                group, payer.getUser(), request.amount(), request.description().trim(),
                request.frequency(), request.startDate(), request.endDate());
        RecurringExpense saved = recurringExpenseRepository.save(recurringExpense);

        saveParticipants(saved, members);

        generationService.generateDueOccurrencesFor(saved); // AC2

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<RecurringExpenseResponse> getRecurringExpensesForGroup(Long groupId, Long currentUserId) {
        groupRepository.findByIdAndMembersUserId(groupId, currentUserId)
                .orElseThrow(GroupAccessDeniedException::new);

        return recurringExpenseRepository.findByGroupIdOrderByStartDateDesc(groupId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public RecurringExpenseResponse getRecurringExpense(Long groupId, Long recurringExpenseId, Long currentUserId) {
        groupRepository.findByIdAndMembersUserId(groupId, currentUserId)
                .orElseThrow(GroupAccessDeniedException::new);

        RecurringExpense recurringExpense = recurringExpenseRepository.findByIdAndGroupId(recurringExpenseId, groupId)
                .orElseThrow(RecurringExpenseNotFoundException::new);

        return toResponse(recurringExpense);
    }

    private List<UserInGroup> resolveMembers(ExpenseGroup group, List<Long> participantUserIds) {
        List<UserInGroup> members = participantUserIds.stream()
                .distinct() // duplicate IDs must not be counted more than once in the split
                .map(group::getMember)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingLong(m -> m.getUser().getId()))
                .toList();
        if (members.isEmpty()) {
            throw new IllegalStateException("No valid participants found in the group for the recurring expense");
        }
        return members;
    }

    private void validateEndDate(LocalDate startDate, LocalDate endDate) {
        if (endDate != null && endDate.isBefore(startDate)) {
            throw new InvalidEndDateException(); // AC5
        }
    }

    private void saveParticipants(RecurringExpense recurringExpense, List<UserInGroup> members) {
        for (UserInGroup member : members) {
            participantRepository.save(new RecurringExpenseParticipant(member.getUser(), recurringExpense));
        }
    }

    private RecurringExpenseResponse toResponse(RecurringExpense recurringExpense) {
        return new RecurringExpenseResponse(
                recurringExpense.getId(),
                recurringExpense.getGroup().getId(),
                recurringExpense.getPaidBy().getId(),
                recurringExpense.getPaidBy().getUsername(),
                recurringExpense.getAmount(),
                recurringExpense.getDescription(),
                recurringExpense.getFrequency(),
                recurringExpense.getStartDate(),
                recurringExpense.getEndDate(),
                recurringExpense.getNextDueDate(),
                recurringExpense.isActive(),
                participantRepository.findByRecurringExpenseId(recurringExpense.getId()).stream()
                        .map(p -> p.getUser().getId())
                        .sorted()
                        .toList());
    }
}
