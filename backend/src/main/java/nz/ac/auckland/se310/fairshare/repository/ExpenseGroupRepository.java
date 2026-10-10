package nz.ac.auckland.se310.fairshare.repository;

import nz.ac.auckland.se310.fairshare.model.ExpenseGroup;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ExpenseGroupRepository extends JpaRepository<ExpenseGroup, Long> {

    // AC8: only returns the group if the requesting user is a member
    @EntityGraph(attributePaths = {"members", "members.user"})
    Optional<ExpenseGroup> findByIdAndMembersUserId(Long groupId, Long userId);

    // AC5: the user's groups overview, newest first
    @EntityGraph(attributePaths = {"members", "members.user"})
    List<ExpenseGroup> findByMembersUserIdOrderByCreatedAtDesc(Long userId);

    // #2 AC3: groups both users belong to, so group-split debts can be combined with individual entries.
    @EntityGraph(attributePaths = {"members", "members.user"})
    @Query(
            "select g from ExpenseGroup g join g.members m1 join g.members m2 "
                    + "where m1.user.id = :userA and m2.user.id = :userB")
    List<ExpenseGroup> findSharedGroups(Long userA, Long userB);
}