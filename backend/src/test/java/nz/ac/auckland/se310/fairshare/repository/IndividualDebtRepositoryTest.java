package nz.ac.auckland.se310.fairshare.repository;

import nz.ac.auckland.se310.fairshare.UserRepository;
import nz.ac.auckland.se310.fairshare.model.ExpenseGroup;
import nz.ac.auckland.se310.fairshare.model.IndividualDebt;
import nz.ac.auckland.se310.fairshare.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * #2: the custom queries behind individual debts, run against MySQL with the real Flyway migrations.
 * Needs Docker.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class IndividualDebtRepositoryTest {

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    @Autowired
    private IndividualDebtRepository individualDebtRepository;
    @Autowired
    private ExpenseGroupRepository groupRepository;
    @Autowired
    private UserRepository userRepository;

    private User alice;
    private User bob;
    private User carol;
    private User dave;

    @BeforeEach
    void setUp() {
        alice = saveUser("alice");
        bob = saveUser("bob");
        carol = saveUser("carol");
        dave = saveUser("dave");
    }

    @Test
    void findBetweenUsersReturnsEntriesInEitherDirectionNewestFirst() {
        IndividualDebt older = saveDebt(alice, bob, LocalDate.of(2026, 7, 1));
        IndividualDebt newer = saveDebt(bob, alice, LocalDate.of(2026, 8, 1));
        saveDebt(alice, carol, LocalDate.of(2026, 8, 2)); // a different pair

        assertThat(individualDebtRepository.findBetweenUsers(alice.getId(), bob.getId()))
                .containsExactly(newer, older);
        assertThat(individualDebtRepository.findBetweenUsers(bob.getId(), alice.getId()))
                .containsExactly(newer, older);
    }

    @Test
    void findCounterpartyIdsListsEachOtherPersonOnce() {
        saveDebt(alice, bob, LocalDate.of(2026, 7, 1));
        saveDebt(bob, alice, LocalDate.of(2026, 7, 2));
        saveDebt(carol, alice, LocalDate.of(2026, 7, 3));
        saveDebt(bob, dave, LocalDate.of(2026, 7, 4)); // Alice isn't part of this one

        assertThat(individualDebtRepository.findCounterpartyIds(alice.getId()))
                .containsExactlyInAnyOrder(bob.getId(), carol.getId());
    }

    @Test
    void findByPayerOrDebtorReturnsOnlyTheUsersOwnEntries() {
        IndividualDebt asPayer = saveDebt(alice, bob, LocalDate.of(2026, 7, 1));
        IndividualDebt asDebtor = saveDebt(carol, alice, LocalDate.of(2026, 8, 1));
        saveDebt(bob, carol, LocalDate.of(2026, 8, 2));

        assertThat(individualDebtRepository.findByPayerIdOrDebtorIdOrderByDebtDateDesc(alice.getId(), alice.getId()))
                .containsExactly(asDebtor, asPayer);
    }

    @Test
    void findSharedGroupsReturnsOnlyGroupsBothUsersBelongTo() {
        ExpenseGroup both = saveGroup("Flat", alice, bob);
        ExpenseGroup withCarol = saveGroup("Trip", alice, bob, carol);
        saveGroup("Alice only", alice, carol);
        saveGroup("Bob only", bob, dave);

        List<ExpenseGroup> shared = groupRepository.findSharedGroups(alice.getId(), bob.getId());

        assertThat(shared).containsExactlyInAnyOrder(both, withCarol);
        assertThat(groupRepository.findSharedGroups(carol.getId(), dave.getId())).isEmpty();
    }

    private User saveUser(String username) {
        return userRepository.save(new User(username, "password", username + "@example.com",
                User.Country.NEW_ZEALAND, User.Currency.NZD));
    }

    private IndividualDebt saveDebt(User payer, User debtor, LocalDate date) {
        return individualDebtRepository.saveAndFlush(new IndividualDebt(payer, payer, debtor,
                new BigDecimal("10.00"), "NZD", "Test", date));
    }

    private ExpenseGroup saveGroup(String name, User creator, User... others) {
        ExpenseGroup group = new ExpenseGroup(name, null, User.Currency.NZD, creator);
        for (User other : others) {
            group.addMember(other);
        }
        return groupRepository.saveAndFlush(group);
    }
}
