package nz.ac.auckland.se310.fairshare.service;

import nz.ac.auckland.se310.fairshare.dto.CreateExpenseRequest;
import nz.ac.auckland.se310.fairshare.dto.ExpenseResponse;
import nz.ac.auckland.se310.fairshare.exception.GroupAccessDeniedException;
import nz.ac.auckland.se310.fairshare.exception.ExpenseNotFoundException;
import nz.ac.auckland.se310.fairshare.exception.InvalidExpenseAmountException;
import nz.ac.auckland.se310.fairshare.exception.InvalidPayerException;
import nz.ac.auckland.se310.fairshare.model.Expense;
import nz.ac.auckland.se310.fairshare.model.ExpenseGroup;
import nz.ac.auckland.se310.fairshare.model.ExpenseShare;
import nz.ac.auckland.se310.fairshare.model.RecurringExpense;
import nz.ac.auckland.se310.fairshare.model.UserInGroup;
import nz.ac.auckland.se310.fairshare.repository.ExpenseRepository;
import nz.ac.auckland.se310.fairshare.repository.ExpenseShareRepository;
import nz.ac.auckland.se310.fairshare.repository.ExpenseGroupRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

@Service
public class ExpenseService {

    private static final int MONEY_SCALE = 2;
    private static final int RATE_SCALE = 8; // matches expense.exchange_rate DECIMAL(18,8)
    // The largest value expense.amount, a DECIMAL(15,2), can hold.
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999999999.99");

    private final ExpenseRepository expenseRepository;
    private final ExpenseGroupRepository groupRepository;
    private final ExpenseShareRepository expenseShareRepository;
    private final CurrencyService currencyService;
    private final ExchangeRateProvider exchangeRateProvider;

    public ExpenseService(ExpenseRepository expenseRepository, ExpenseGroupRepository groupRepository,
                          ExpenseShareRepository expenseShareRepository, CurrencyService currencyService,
                          ExchangeRateProvider exchangeRateProvider) {
        this.expenseRepository = expenseRepository;
        this.groupRepository = groupRepository;
        this.expenseShareRepository = expenseShareRepository;
        this.currencyService = currencyService;
        this.exchangeRateProvider = exchangeRateProvider;
    }

    /** The amount as entered, and its value in the group's base currency. */
    private record Conversion(BigDecimal originalAmount, String originalCurrency,
                              BigDecimal exchangeRate, BigDecimal amount) {}

    /**
     * Validates that the current user belongs to the group, confirms the payer is a member, and then
     * saves the expense and its equal-share breakdown for the selected participants.
     */
    @Transactional
    public ExpenseResponse createExpense(Long groupId, CreateExpenseRequest request, Long currentUserId) {
        ExpenseGroup group = groupRepository.findByIdAndMembersUserId(groupId, currentUserId)
                .orElseThrow(GroupAccessDeniedException::new); // AC8

        UserInGroup payer = group.getMember(request.paidByUserId());
        if (payer == null) {
            throw new InvalidPayerException(request.paidByUserId()); // AC5
        }

        List<UserInGroup> members = request.participantUserIds().stream()
                .distinct() // duplicate IDs must not be counted more than once in the split
                .map(group::getMember)
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparingLong(m -> m.getUser().getId()))
                .toList();
        if (members.isEmpty()) {
            throw new IllegalStateException("No valid participants found in the group for the expense");
        }

        LocalDate expenseDate = request.expenseDate() != null ? request.expenseDate() : LocalDate.now(); // AC6
        // Converted before anything is saved, so a failed rate lookup leaves no partial expense behind.
        Conversion conversion = convert(group, request, expenseDate);

        Expense expense = new Expense(
                group, payer.getUser(), conversion.amount(), request.description().trim(), expenseDate);
        expense.setConversion(conversion.originalAmount(), conversion.originalCurrency(),
                conversion.exchangeRate(), conversion.amount());
        Expense saved = expenseRepository.save(expense);

        applyEqualSplit(payer, conversion.amount(), members, saved); // AC1, AC4

        return toResponse(saved);
    }

    /**
     * Rewrites an existing expense by removing the old share allocation, applying the new payer and
     * participant list, and persisting the updated amount and metadata on the original expense.
     */
    @Transactional
    public void updateExpense(Long groupId, CreateExpenseRequest request, Long currentUserId, Long expenseId) {
        ExpenseGroup group = groupRepository.findByIdAndMembersUserId(groupId, currentUserId)
                .orElseThrow(GroupAccessDeniedException::new); // AC8

        Expense expense = expenseRepository.findByIdAndGroupId(expenseId, groupId)
                .orElseThrow(ExpenseNotFoundException::new);

        UserInGroup originalPayer = group.getMember(expense.getPaidBy().getId());
        UserInGroup payer = group.getMember(request.paidByUserId());
        if (payer == null) {
            throw new InvalidPayerException(request.paidByUserId());
        }

        // Rebuild the participant list from the current group membership so edits cannot use stale or invalid users.
        List<UserInGroup> members = request.participantUserIds().stream()
                .distinct() // duplicate IDs must not be counted more than once in the split
                .map(group::getMember)
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparingLong(m -> m.getUser().getId()))
                .toList();
        if (members.isEmpty()) {
            throw new IllegalStateException("No valid participants found in the group for the expense");
        }

        LocalDate expenseDate = request.expenseDate() != null ? request.expenseDate() : expense.getExpenseDate();
        Conversion conversion = convert(group, request, expenseDate);

        // updateSplit reverses the old amount, so the new one is only set on the expense afterwards.
        updateSplit(expense, originalPayer, payer, conversion.amount(), members);

        expense.setConversion(conversion.originalAmount(), conversion.originalCurrency(),
                conversion.exchangeRate(), conversion.amount());
        expense.setDescription(request.description().trim());
        expense.setPaidBy(payer.getUser());
        expense.setExpenseDate(expenseDate);

        expenseRepository.save(expense);
    }

    /**
     * Creates one occurrence of a recurring expense (AC2). Reuses the same equal-split logic as
     * a manually recorded expense; the caller has already resolved the payer and participants
     * against the group's current membership.
     */
    @Transactional
    public ExpenseResponse createRecurringOccurrence(RecurringExpense recurringExpense, UserInGroup payer,
                                                       List<UserInGroup> members, LocalDate occurrenceDate) {
        BigDecimal amount = recurringExpense.getAmount();

        Expense expense = new Expense(recurringExpense.getGroup(), payer.getUser(), amount,
                recurringExpense.getDescription(), occurrenceDate, recurringExpense);
        Expense saved = expenseRepository.save(expense);

        applyEqualSplit(payer, amount, members, saved);

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<ExpenseResponse> getExpensesForGroup(Long groupId, Long currentUserId) {
        groupRepository.findByIdAndMembersUserId(groupId, currentUserId)
                .orElseThrow(GroupAccessDeniedException::new); // AC8

        return expenseRepository.findByGroupIdOrderByExpenseDateDesc(groupId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ExpenseResponse getExpense(Long groupId, Long expenseId, Long currentUserId) {
        groupRepository.findByIdAndMembersUserId(groupId, currentUserId)
                .orElseThrow(GroupAccessDeniedException::new);

        Expense expense = expenseRepository.findByIdAndGroupId(expenseId, groupId)
                .orElseThrow(() -> new IllegalArgumentException("Expense not found in group"));

        return toResponse(expense);
    }

    /**
     * AC4: the amount is split equally across every current member. Cents left over by an
     * uneven division go to the lowest user ids, so the shares always add back up to the
     * amount the payer actually spent.
     */
    private void applyEqualSplit(UserInGroup payer, BigDecimal amount, List<UserInGroup> members, Expense expense) {
        long totalCents = amount.movePointRight(MONEY_SCALE).longValueExact();
        long baseShare = totalCents / members.size();
        long extraCents = totalCents % members.size();

        // Positive netBalance means the member owes money. Payer paid, so they are owed -> subtract
        payer.adjustNetBalance(amount.negate());

        for (int i = 0; i < members.size(); i++) {
            long shareCents = baseShare + (i < extraCents ? 1 : 0);
            // participants owe the share
            members.get(i).adjustNetBalance(cents(shareCents));
            try {
                expenseShareRepository.save(new ExpenseShare(members.get(i).getUser(), expense, cents(shareCents)));
            } catch (Exception e) {
                throw new RuntimeException("Failed to save expense share for user " + members.get(i).getUser().getId(), e);
            }
        }
    }

    private void updateSplit(Expense expense, UserInGroup originalPayer, UserInGroup newPayer,
                             BigDecimal newAmount, List<UserInGroup> members) {
        // First, reverse the previous split
        List<ExpenseShare> existingShares = expenseShareRepository.findByExpenseId(expense.getId());
        for (ExpenseShare share : existingShares) {
            UserInGroup member = expense.getGroup().getMember(share.getUser().getId());
            if (member != null) {
                // previously participants had added positive share amounts; remove that
                member.adjustNetBalance(share.getShareAmount().negate());
            }
            expenseShareRepository.delete(share);
        }
        // Flush so the deletes hit the DB before the new shares are inserted, otherwise the
        // unique constraint on (user_id, expense_id) can be violated by Hibernate's action ordering.
        expenseShareRepository.flush();
        // Revert the original payer's owed/owed-to adjustment
        originalPayer.adjustNetBalance(expense.getAmount());

        // Apply the new split
        applyEqualSplit(newPayer, newAmount, members, expense);
    }

    /**
     * #14 AC1: converts the entered amount into the group's base currency using the rate for the
     * expense date. A request with no currency is taken to be in the base currency.
     */
    private Conversion convert(ExpenseGroup group, CreateExpenseRequest request, LocalDate expenseDate) {
        String baseCurrency = group.getBaseCurrency().name();
        String currency = request.currency() == null
                ? baseCurrency
                : currencyService.requireSupported(request.currency()); // AC3

        BigDecimal originalAmount = request.amount().setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal rate = exchangeRateProvider.getRate(currency, baseCurrency, expenseDate)
                .setScale(RATE_SCALE, RoundingMode.HALF_UP);
        BigDecimal amount = originalAmount.multiply(rate).setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        // e.g. IDR 10 is worth well under one cent in NZD, which cannot be split or settled.
        if (amount.signum() <= 0) {
            throw new InvalidExpenseAmountException(
                    "Amount is less than 0.01 " + baseCurrency + " once converted");
        }
        // Converting into a currency like IDR multiplies the amount, so it can pass the column
        // limit even when the entered amount did not. A 400 rather than a failed save.
        if (amount.compareTo(MAX_AMOUNT) > 0) {
            throw new InvalidExpenseAmountException(
                    "Amount is more than 9,999,999,999,999.99 " + baseCurrency + " once converted");
        }
        return new Conversion(originalAmount, currency, rate, amount);
    }

    private BigDecimal cents(long value) {
        return BigDecimal.valueOf(value, MONEY_SCALE);
    }

    private ExpenseResponse toResponse(Expense expense) {
        return new ExpenseResponse(
                expense.getId(),
                expense.getGroup().getId(),
                expense.getPaidBy().getId(),
                expense.getPaidBy().getUsername(),
                expense.getAmount(),
                expense.getDescription(),
                expense.getExpenseDate(),
                expense.getCreatedAt(),
                expenseShareRepository.findByExpenseId(expense.getId()).stream()
                    .map(share -> share.getUser().getId())
                    .sorted()
                    .toList(),
                // AC3: lets the frontend mark this entry as recurring and link back to its source.
                // Safe on a lazy proxy - the id is known without initializing the full entity.
                expense.getRecurringExpense() != null ? expense.getRecurringExpense().getId() : null,
                expense.getOriginalAmount(),
                expense.getOriginalCurrency(),
                expense.getExchangeRate());
    }
}
