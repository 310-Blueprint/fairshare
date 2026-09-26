package nz.ac.auckland.se310.fairshare.model;

import jakarta.persistence.*;

@Entity
@Table(
        name = "recurring_expense_participant",
        uniqueConstraints =
        @UniqueConstraint(name = "uq_rep_user_recurring_expense", columnNames = {"user_id", "recurring_expense_id"}))
public class RecurringExpenseParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "recurring_expense_participant_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recurring_expense_id", nullable = false)
    private RecurringExpense recurringExpense;

    protected RecurringExpenseParticipant() {} // JPA

    public RecurringExpenseParticipant(User user, RecurringExpense recurringExpense) {
        this.user = user;
        this.recurringExpense = recurringExpense;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public RecurringExpense getRecurringExpense() { return recurringExpense; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RecurringExpenseParticipant other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
