package nz.ac.auckland.se310.fairshare.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import nz.ac.auckland.se310.fairshare.model.ExpenseShare;

import java.time.LocalDate;
import java.util.List;

public interface ExpenseShareRepository extends JpaRepository<ExpenseShare, Long> {
    List<ExpenseShare> findByExpenseId(Long expenseId);

    // #12: every share in a group in one query, so an export does not query once per expense
    List<ExpenseShare> findByExpenseGroupId(Long groupId);

    // #16: the user's own shares of expenses dated within the period, only in groups they still
    // belong to (AC4). The expense, its group and its payer are fetched in the same query.
    @Query("select s from ExpenseShare s join fetch s.expense e join fetch e.group g join fetch e.paidBy "
            + "where s.user.id = :userId and e.expenseDate between :from and :to "
            + "and exists (select m from UserInGroup m where m.group = g and m.user.id = :userId)")
    List<ExpenseShare> findSpendingShares(Long userId, LocalDate from, LocalDate to);
}
