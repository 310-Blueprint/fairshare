package nz.ac.auckland.se310.fairshare.service;

import nz.ac.auckland.se310.fairshare.dto.CounterpartyBalanceResponse;
import nz.ac.auckland.se310.fairshare.dto.CreateIndividualDebtRequest;
import nz.ac.auckland.se310.fairshare.dto.IndividualDebtResponse;
import nz.ac.auckland.se310.fairshare.dto.NetBalanceResponse;
import nz.ac.auckland.se310.fairshare.dto.UpdateIndividualDebtRequest;
import nz.ac.auckland.se310.fairshare.exception.IndividualDebtAccessDeniedException;
import nz.ac.auckland.se310.fairshare.exception.IndividualDebtNotFoundException;
import nz.ac.auckland.se310.fairshare.exception.InvalidDebtEntryException;
import nz.ac.auckland.se310.fairshare.exception.UserNotFoundException;
import nz.ac.auckland.se310.fairshare.model.Expense;
import nz.ac.auckland.se310.fairshare.model.ExpenseGroup;
import nz.ac.auckland.se310.fairshare.model.ExpenseShare;
import nz.ac.auckland.se310.fairshare.model.IndividualDebt;
import nz.ac.auckland.se310.fairshare.model.Settlement;
import nz.ac.auckland.se310.fairshare.model.User;
import nz.ac.auckland.se310.fairshare.model.UserInGroup;
import nz.ac.auckland.se310.fairshare.repository.ExpenseGroupRepository;
import nz.ac.auckland.se310.fairshare.repository.ExpenseRepository;
import nz.ac.auckland.se310.fairshare.repository.ExpenseShareRepository;
import nz.ac.auckland.se310.fairshare.repository.IndividualDebtRepository;
import nz.ac.auckland.se310.fairshare.repository.SettlementRepository;
import nz.ac.auckland.se310.fairshare.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class IndividualDebtService {

    private static final int MONEY_SCALE = 2;

    private final IndividualDebtRepository individualDebtRepository;
    private final UserRepository userRepository;
    private final ExpenseGroupRepository groupRepository;
    private final ExpenseRepository expenseRepository;
    private final ExpenseShareRepository expenseShareRepository;
    private final SettlementRepository settlementRepository;

    public IndividualDebtService(IndividualDebtRepository individualDebtRepository, UserRepository userRepository,
                                  ExpenseGroupRepository groupRepository, ExpenseRepository expenseRepository,
                                  ExpenseShareRepository expenseShareRepository, SettlementRepository settlementRepository) {
        this.individualDebtRepository = individualDebtRepository;
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
        this.expenseRepository = expenseRepository;
        this.expenseShareRepository = expenseShareRepository;
        this.settlementRepository = settlementRepository;
    }

    // #3 AC1: the current user is always the payer when an entry is first created.
    @Transactional
    public IndividualDebtResponse createDebt(Long currentUserId, CreateIndividualDebtRequest request) {
        User payer = userRepository.findById(currentUserId)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + currentUserId));
        User debtor = findUser(request.counterpartyIdentifier().trim());

        requireDifferentUsers(payer, debtor); // AC9

        IndividualDebt debt = new IndividualDebt(payer, payer, debtor,
                request.amount().setScale(MONEY_SCALE, RoundingMode.HALF_UP), request.description().trim(), request.date());
        return toResponse(individualDebtRepository.save(debt), currentUserId);
    }

    @Transactional(readOnly = true)
    public List<IndividualDebtResponse> listMyDebts(Long currentUserId) {
        return individualDebtRepository.findByPayerIdOrDebtorIdOrderByDebtDateDesc(currentUserId, currentUserId)
                .stream()
                .map(debt -> toResponse(debt, currentUserId))
                .toList();
    }

    // #3 AC7, AC8: only the creator can edit; any of payer, debtor, amount, description or date can change.
    @Transactional
    public IndividualDebtResponse updateDebt(Long entryId, Long currentUserId, UpdateIndividualDebtRequest request) {
        IndividualDebt debt = individualDebtRepository.findById(entryId)
                .orElseThrow(IndividualDebtNotFoundException::new);
        requireCreator(debt, currentUserId);

        User payer = findUser(request.payerIdentifier().trim());
        User debtor = findUser(request.debtorIdentifier().trim());
        requireDifferentUsers(payer, debtor); // AC9

        debt.setPayer(payer);
        debt.setDebtor(debtor);
        debt.setAmount(request.amount().setScale(MONEY_SCALE, RoundingMode.HALF_UP));
        debt.setDescription(request.description().trim());
        debt.setDebtDate(request.date());

        return toResponse(individualDebtRepository.save(debt), currentUserId);
    }

    // #3 AC7
    @Transactional
    public void deleteDebt(Long entryId, Long currentUserId) {
        IndividualDebt debt = individualDebtRepository.findById(entryId)
                .orElseThrow(IndividualDebtNotFoundException::new);
        requireCreator(debt, currentUserId);
        individualDebtRepository.delete(debt);
    }

    /**
     * #3 AC2, AC3, AC5, AC6, AC10: the live, combined net balance between two users - every
     * individual debt entry between them, plus every group-split debt from a group they both
     * belong to, reduced to one explicitly-directed figure. Symmetric by construction: computing
     * this from either user's perspective negates every term, so the result is identical either way.
     */
    @Transactional(readOnly = true)
    public NetBalanceResponse getNetBalance(Long currentUserId, Long otherUserId) {
        if (Objects.equals(currentUserId, otherUserId)) {
            throw new InvalidDebtEntryException("Cannot view a balance with yourself");
        }
        User other = userRepository.findById(otherUserId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + otherUserId));

        List<IndividualDebt> entries = individualDebtRepository.findBetweenUsers(currentUserId, otherUserId);

        // Positive means currentUserId owes otherUserId.
        BigDecimal aOwesB = BigDecimal.ZERO;
        for (IndividualDebt entry : entries) {
            if (entry.getDebtor().getId().equals(currentUserId) && entry.getPayer().getId().equals(otherUserId)) {
                aOwesB = aOwesB.add(entry.getAmount());
            } else if (entry.getDebtor().getId().equals(otherUserId) && entry.getPayer().getId().equals(currentUserId)) {
                aOwesB = aOwesB.subtract(entry.getAmount());
            }
        }

        aOwesB = aOwesB.add(computeGroupSplitBalance(currentUserId, otherUserId));
        aOwesB = aOwesB.setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        List<IndividualDebtResponse> breakdown = entries.stream()
                .map(entry -> toResponse(entry, currentUserId))
                .toList();

        return buildNetBalanceResponse(currentUserId, otherUserId, other.getUsername(), aOwesB, breakdown);
    }

    // #3 AC4: the overview for the individual debts page - every counterparty this user has any relationship with.
    @Transactional(readOnly = true)
    public List<CounterpartyBalanceResponse> getBalancesOverview(Long currentUserId) {
        Set<Long> counterpartyIds = individualDebtRepository.findCounterpartyIds(currentUserId).stream()
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));

        for (ExpenseGroup group : groupRepository.findByMembersUserIdOrderByCreatedAtDesc(currentUserId)) {
            for (UserInGroup member : group.getMembers()) {
                Long memberId = member.getUser().getId();
                if (!memberId.equals(currentUserId)) {
                    counterpartyIds.add(memberId);
                }
            }
        }

        return counterpartyIds.stream()
                .map(otherId -> getNetBalance(currentUserId, otherId))
                .map(this::toCounterpartyResponse)
                .toList();
    }

    /**
     * #3 AC3: the pairwise group-split contribution between two users, across every group they
     * both belong to. Only expenses where one of them is the payer and the other a participant
     * create a direct debt between the pair; completed settlements adjust for cash already moved.
     */
    private BigDecimal computeGroupSplitBalance(Long userAId, Long userBId) {
        BigDecimal aOwesB = BigDecimal.ZERO;

        for (ExpenseGroup group : groupRepository.findSharedGroups(userAId, userBId)) {
            for (Expense expense : expenseRepository.findByGroupIdOrderByExpenseDateDesc(group.getId())) {
                Long payerId = expense.getPaidBy().getId();
                if (!payerId.equals(userAId) && !payerId.equals(userBId)) {
                    continue; // neither of the pair paid - no direct debt between them from this expense
                }

                List<ExpenseShare> shares = expenseShareRepository.findByExpenseId(expense.getId());
                if (payerId.equals(userBId)) {
                    BigDecimal shareA = shareOf(shares, userAId);
                    aOwesB = aOwesB.add(shareA);
                } else {
                    BigDecimal shareB = shareOf(shares, userBId);
                    aOwesB = aOwesB.subtract(shareB);
                }
            }

            for (Settlement settlement : settlementRepository.findByGroupId(group.getId())) {
                if (settlement.getSettlementDate() == null) {
                    continue; // not yet paid - the underlying expense debt above already reflects it
                }
                Long fromId = settlement.getFromUser().getId();
                Long toId = settlement.getToUser().getId();
                boolean isPairSettlement = (fromId.equals(userAId) && toId.equals(userBId))
                        || (fromId.equals(userBId) && toId.equals(userAId));
                if (!isPairSettlement) {
                    continue;
                }
                if (fromId.equals(userAId)) {
                    aOwesB = aOwesB.subtract(settlement.getAmount());
                } else {
                    aOwesB = aOwesB.add(settlement.getAmount());
                }
            }
        }

        return aOwesB;
    }

    private BigDecimal shareOf(List<ExpenseShare> shares, Long userId) {
        return shares.stream()
                .filter(share -> share.getUser().getId().equals(userId))
                .map(ExpenseShare::getShareAmount)
                .findFirst()
                .orElse(BigDecimal.ZERO);
    }

    private NetBalanceResponse buildNetBalanceResponse(Long currentUserId, Long otherUserId, String otherUsername,
                                                        BigDecimal aOwesB, List<IndividualDebtResponse> entries) {
        int comparison = aOwesB.compareTo(BigDecimal.ZERO);
        if (comparison == 0) {
            return new NetBalanceResponse(otherUserId, otherUsername, null, null,
                    BigDecimal.ZERO.setScale(MONEY_SCALE), true, entries);
        }
        if (comparison > 0) {
            return new NetBalanceResponse(otherUserId, otherUsername, currentUserId, otherUserId, aOwesB, false, entries);
        }
        return new NetBalanceResponse(otherUserId, otherUsername, otherUserId, currentUserId, aOwesB.negate(), false, entries);
    }

    private CounterpartyBalanceResponse toCounterpartyResponse(NetBalanceResponse balance) {
        return new CounterpartyBalanceResponse(balance.otherUserId(), balance.otherUsername(),
                balance.fromUserId(), balance.toUserId(), balance.amount(), balance.settled());
    }

    private void requireCreator(IndividualDebt debt, Long currentUserId) {
        if (!debt.getCreator().getId().equals(currentUserId)) {
            throw new IndividualDebtAccessDeniedException(); // AC7
        }
    }

    private void requireDifferentUsers(User a, User b) {
        if (a.getId().equals(b.getId())) {
            throw new InvalidDebtEntryException("The counterparty cannot be yourself"); // AC9
        }
    }

    private User findUser(String identifier) {
        return userRepository.findByEmailIgnoreCase(identifier)
                .orElseGet(() -> findUserByUsername(identifier));
    }

    private User findUserByUsername(String username) {
        List<User> matches = userRepository.findAllByUsernameIgnoreCase(username);
        if (matches.isEmpty()) {
            throw new UserNotFoundException("No matching user was found");
        }
        if (matches.size() > 1) {
            throw new InvalidDebtEntryException("Multiple users match that username; use an email address");
        }
        return matches.getFirst();
    }

    private IndividualDebtResponse toResponse(IndividualDebt debt, Long currentUserId) {
        return new IndividualDebtResponse(
                debt.getId(),
                debt.getPayer().getId(),
                debt.getPayer().getUsername(),
                debt.getDebtor().getId(),
                debt.getDebtor().getUsername(),
                debt.getAmount(),
                debt.getDescription(),
                debt.getDebtDate(),
                debt.getCreator().getId().equals(currentUserId));
    }
}
