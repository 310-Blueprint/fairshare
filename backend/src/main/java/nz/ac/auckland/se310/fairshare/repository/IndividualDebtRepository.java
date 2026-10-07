package nz.ac.auckland.se310.fairshare.repository;

import nz.ac.auckland.se310.fairshare.model.IndividualDebt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface IndividualDebtRepository extends JpaRepository<IndividualDebt, Long> {

    // #3 AC1: a user's debt list includes entries where they are either side.
    List<IndividualDebt> findByPayerIdOrDebtorIdOrderByDebtDateDesc(Long payerId, Long debtorId);

    // #3 AC2: every entry between exactly these two users, either direction - the breakdown behind the net figure.
    @Query("select d from IndividualDebt d where (d.payer.id = :a and d.debtor.id = :b) "
            + "or (d.payer.id = :b and d.debtor.id = :a) order by d.debtDate desc")
    List<IndividualDebt> findBetweenUsers(@Param("a") Long a, @Param("b") Long b);

    // #3: distinct counterparties this user has an individual debt entry with, for the overview page.
    @Query("select distinct case when d.payer.id = :userId then d.debtor.id else d.payer.id end "
            + "from IndividualDebt d where d.payer.id = :userId or d.debtor.id = :userId")
    List<Long> findCounterpartyIds(@Param("userId") Long userId);
}
