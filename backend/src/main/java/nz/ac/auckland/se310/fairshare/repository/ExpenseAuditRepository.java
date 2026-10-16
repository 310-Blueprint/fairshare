package nz.ac.auckland.se310.fairshare.repository;

import nz.ac.auckland.se310.fairshare.model.ExpenseAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ExpenseAuditRepository extends JpaRepository<ExpenseAudit, Long> {
    List<ExpenseAudit> findByGroupIdOrderByChangedAtDescIdDesc(Long groupId);
}
