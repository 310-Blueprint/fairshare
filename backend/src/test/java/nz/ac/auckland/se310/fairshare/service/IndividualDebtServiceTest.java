package nz.ac.auckland.se310.fairshare.service;

import nz.ac.auckland.se310.fairshare.UserRepository;
import nz.ac.auckland.se310.fairshare.dto.CreateIndividualDebtRequest;
import nz.ac.auckland.se310.fairshare.dto.NetBalanceResponse;
import nz.ac.auckland.se310.fairshare.dto.UpdateIndividualDebtRequest;
import nz.ac.auckland.se310.fairshare.exception.IndividualDebtAccessDeniedException;
import nz.ac.auckland.se310.fairshare.exception.InvalidDebtEntryException;
import nz.ac.auckland.se310.fairshare.model.Expense;
import nz.ac.auckland.se310.fairshare.model.ExpenseGroup;
import nz.ac.auckland.se310.fairshare.model.ExpenseShare;
import nz.ac.auckland.se310.fairshare.model.IndividualDebt;
import nz.ac.auckland.se310.fairshare.model.User;
import nz.ac.auckland.se310.fairshare.repository.ExpenseGroupRepository;
import nz.ac.auckland.se310.fairshare.repository.ExpenseRepository;
import nz.ac.auckland.se310.fairshare.repository.ExpenseShareRepository;
import nz.ac.auckland.se310.fairshare.repository.IndividualDebtRepository;
import nz.ac.auckland.se310.fairshare.repository.SettlementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * #3: individual debt creation, authorization and the combined net-balance calculation.
 * Runs without a database - all repositories are stubbed.
 */
class IndividualDebtServiceTest {

    private static final long ALICE = 10L;
    private static final long BOB = 20L;
    private static final long GROUP_ID = 1L;
    private static final long EXPENSE_ID = 100L;

    private final IndividualDebtRepository individualDebtRepository = mock(IndividualDebtRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final ExpenseGroupRepository groupRepository = mock(ExpenseGroupRepository.class);
    private final ExpenseRepository expenseRepository = mock(ExpenseRepository.class);
    private final ExpenseShareRepository expenseShareRepository = mock(ExpenseShareRepository.class);
    private final SettlementRepository settlementRepository = mock(SettlementRepository.class);

    private User alice;
    private User bob;
    private IndividualDebtService service;

    @BeforeEach
    void setUp() {
        alice = user(ALICE, "alice");
        bob = user(BOB, "bob");

        when(userRepository.findById(ALICE)).thenReturn(Optional.of(alice));
        when(userRepository.findById(BOB)).thenReturn(Optional.of(bob));
        when(individualDebtRepository.save(any(IndividualDebt.class))).then(returnsFirstArg());
        when(individualDebtRepository.findBetweenUsers(anyLong(), anyLong())).thenReturn(List.of());
        when(groupRepository.findSharedGroups(anyLong(), anyLong())).thenReturn(List.of());

        service = new IndividualDebtService(individualDebtRepository, userRepository, groupRepository,
                expenseRepository, expenseShareRepository, settlementRepository);
    }

    @Test
    void ac9_selfDebtIsRejectedOnCreate() {
        when(userRepository.findByEmailIgnoreCase("alice")).thenReturn(Optional.empty());
        when(userRepository.findAllByUsernameIgnoreCase("alice")).thenReturn(List.of(alice));

        CreateIndividualDebtRequest request = new CreateIndividualDebtRequest(
                "alice", new BigDecimal("10.00"), "Coffee", LocalDate.now());

        assertThatThrownBy(() -> service.createDebt(ALICE, request))
                .isInstanceOf(InvalidDebtEntryException.class);
        verify(individualDebtRepository, never()).save(any());
    }

    @Test
    void ac1_createPersistsCurrentUserAsPayerAndSelectedUserAsDebtor() {
        when(userRepository.findByEmailIgnoreCase("bob")).thenReturn(Optional.empty());
        when(userRepository.findAllByUsernameIgnoreCase("bob")).thenReturn(List.of(bob));

        CreateIndividualDebtRequest request = new CreateIndividualDebtRequest(
                "bob", new BigDecimal("25.00"), "Lunch", LocalDate.now());

        var response = service.createDebt(ALICE, request);

        assertThat(response.payerUserId()).isEqualTo(ALICE);
        assertThat(response.debtorUserId()).isEqualTo(BOB);
        assertThat(response.amount()).isEqualByComparingTo("25.00");
        assertThat(response.canEdit()).isTrue();
    }

    @Test
    void ac7_onlyCreatorCanEditOrDelete() {
        IndividualDebt debt = new IndividualDebt(alice, alice, bob, new BigDecimal("10.00"), "Coffee", LocalDate.now());
        when(individualDebtRepository.findById(1L)).thenReturn(Optional.of(debt));

        UpdateIndividualDebtRequest update = new UpdateIndividualDebtRequest(
                "alice", "bob", new BigDecimal("15.00"), "Coffee", LocalDate.now());

        assertThatThrownBy(() -> service.updateDebt(1L, BOB, update))
                .isInstanceOf(IndividualDebtAccessDeniedException.class);
        assertThatThrownBy(() -> service.deleteDebt(1L, BOB))
                .isInstanceOf(IndividualDebtAccessDeniedException.class);
        verify(individualDebtRepository, never()).delete(any());
    }

    @Test
    void ac8_creatorEditRecalculatesTheEntry() {
        IndividualDebt debt = new IndividualDebt(alice, alice, bob, new BigDecimal("10.00"), "Coffee", LocalDate.now());
        when(individualDebtRepository.findById(1L)).thenReturn(Optional.of(debt));
        when(userRepository.findByEmailIgnoreCase("alice")).thenReturn(Optional.empty());
        when(userRepository.findAllByUsernameIgnoreCase("alice")).thenReturn(List.of(alice));
        when(userRepository.findByEmailIgnoreCase("bob")).thenReturn(Optional.empty());
        when(userRepository.findAllByUsernameIgnoreCase("bob")).thenReturn(List.of(bob));

        UpdateIndividualDebtRequest update = new UpdateIndividualDebtRequest(
                "alice", "bob", new BigDecimal("15.00"), "Dinner", LocalDate.now());

        var response = service.updateDebt(1L, ALICE, update);

        assertThat(response.amount()).isEqualByComparingTo("15.00");
        assertThat(response.description()).isEqualTo("Dinner");
    }

    @Test
    void ac6_noEntriesOrGroupDebtsMeansSettled() {
        NetBalanceResponse balance = service.getNetBalance(ALICE, BOB);

        assertThat(balance.settled()).isTrue();
        assertThat(balance.amount()).isEqualByComparingTo("0.00");
        assertThat(balance.fromUserId()).isNull();
        assertThat(balance.toUserId()).isNull();
    }

    @Test
    void ac3_combinesIndividualEntryWithGroupSplitDebt() {
        // Group expense: Bob paid 80, split evenly, so Alice owes Bob 40.
        setUpSharedGroupExpense(bob, new BigDecimal("40.00"), new BigDecimal("40.00"));
        // Individual entry: Bob owes Alice 15.
        IndividualDebt entry = new IndividualDebt(bob, alice, bob, new BigDecimal("15.00"), "Taxi", LocalDate.now());
        when(individualDebtRepository.findBetweenUsers(ALICE, BOB)).thenReturn(List.of(entry));
        when(individualDebtRepository.findBetweenUsers(BOB, ALICE)).thenReturn(List.of(entry));

        NetBalanceResponse balance = service.getNetBalance(ALICE, BOB);

        // 40 (Alice owes Bob from the group split) - 15 (Bob owes Alice individually) = 25
        assertThat(balance.settled()).isFalse();
        assertThat(balance.fromUserId()).isEqualTo(ALICE);
        assertThat(balance.toUserId()).isEqualTo(BOB);
        assertThat(balance.amount()).isEqualByComparingTo("25.00");
        assertThat(balance.entries()).hasSize(1);
    }

    @Test
    void ac10_netBalanceIsSymmetricRegardlessOfViewpoint() {
        setUpSharedGroupExpense(bob, new BigDecimal("40.00"), new BigDecimal("40.00"));
        IndividualDebt entry = new IndividualDebt(bob, alice, bob, new BigDecimal("15.00"), "Taxi", LocalDate.now());
        when(individualDebtRepository.findBetweenUsers(ALICE, BOB)).thenReturn(List.of(entry));
        when(individualDebtRepository.findBetweenUsers(BOB, ALICE)).thenReturn(List.of(entry));

        NetBalanceResponse fromAlice = service.getNetBalance(ALICE, BOB);
        NetBalanceResponse fromBob = service.getNetBalance(BOB, ALICE);

        assertThat(fromAlice.fromUserId()).isEqualTo(fromBob.fromUserId());
        assertThat(fromAlice.toUserId()).isEqualTo(fromBob.toUserId());
        assertThat(fromAlice.amount()).isEqualByComparingTo(fromBob.amount());
        assertThat(fromAlice.settled()).isEqualTo(fromBob.settled());
    }

    private void setUpSharedGroupExpense(User payer, BigDecimal aliceShare, BigDecimal bobShare) {
        ExpenseGroup group = mock(ExpenseGroup.class);
        when(group.getId()).thenReturn(GROUP_ID);
        when(groupRepository.findSharedGroups(ALICE, BOB)).thenReturn(List.of(group));
        when(groupRepository.findSharedGroups(BOB, ALICE)).thenReturn(List.of(group));

        Expense expense = mock(Expense.class);
        when(expense.getId()).thenReturn(EXPENSE_ID);
        when(expense.getPaidBy()).thenReturn(payer);
        when(expenseRepository.findByGroupIdOrderByExpenseDateDesc(GROUP_ID)).thenReturn(List.of(expense));

        ExpenseShare aliceExpenseShare = new ExpenseShare(alice, expense, aliceShare);
        ExpenseShare bobExpenseShare = new ExpenseShare(bob, expense, bobShare);
        when(expenseShareRepository.findByExpenseId(EXPENSE_ID)).thenReturn(List.of(aliceExpenseShare, bobExpenseShare));
        when(settlementRepository.findByGroupId(GROUP_ID)).thenReturn(List.of());
    }

    private static User user(long id, String username) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(id);
        when(user.getUsername()).thenReturn(username);
        return user;
    }
}
