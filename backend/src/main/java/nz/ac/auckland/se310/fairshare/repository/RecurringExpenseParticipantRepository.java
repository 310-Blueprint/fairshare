package nz.ac.auckland.se310.fairshare.repository;

import nz.ac.auckland.se310.fairshare.model.RecurringExpenseParticipant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RecurringExpenseParticipantRepository extends JpaRepository<RecurringExpenseParticipant, Long> {
    List<RecurringExpenseParticipant> findByRecurringExpenseId(Long recurringExpenseId);

    // A member can't leave the group while they're still a participant of an active recurring expense in it
    boolean existsByRecurringExpense_GroupIdAndUser_IdAndRecurringExpense_ActiveTrue(Long groupId, Long userId);
}
