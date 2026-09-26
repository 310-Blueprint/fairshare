package nz.ac.auckland.se310.fairshare.repository;

import nz.ac.auckland.se310.fairshare.model.RecurringExpense;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RecurringExpenseRepository extends JpaRepository<RecurringExpense, Long> {

    // AC1: a group's recurring expenses, most recently started first
    List<RecurringExpense> findByGroupIdOrderByStartDateDesc(Long groupId);

    Optional<RecurringExpense> findByIdAndGroupId(Long recurringExpenseId, Long groupId);

    // AC2: active recurring expenses whose next occurrence is due on or before the given date
    List<RecurringExpense> findByActiveTrueAndNextDueDateLessThanEqual(LocalDate date);

    // A member can't leave the group while they're still the payer of an active recurring expense in it
    boolean existsByGroupIdAndPaidBy_IdAndActiveTrue(Long groupId, Long userId);
}
