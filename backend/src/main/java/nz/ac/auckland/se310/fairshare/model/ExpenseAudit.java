package nz.ac.auckland.se310.fairshare.model;

import jakarta.persistence.*;
import java.time.Instant;

/** An immutable snapshot that survives deletion of the expense or changes to the actor's name. */
@Entity
@Table(name = "expense_audit")
public class ExpenseAudit {
    public enum Action { EDITED, DELETED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false, updatable = false)
    private ExpenseGroup group;

    // Deliberately not a foreign key to expense: deleted expenses still have an audit trail.
    @Column(name = "expense_id", nullable = false, updatable = false)
    private Long expenseId;

    @Column(name = "expense_description", nullable = false, updatable = false, length = 255)
    private String expenseDescription;

    @Column(name = "actor_user_id", nullable = false, updatable = false)
    private Long actorUserId;

    @Column(name = "actor_username", nullable = false, updatable = false, length = 255)
    private String actorUsername;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 10)
    private Action action;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;

    protected ExpenseAudit() {}

    public ExpenseAudit(Expense expense, User actor, Action action) {
        this.group = expense.getGroup();
        this.expenseId = expense.getId();
        this.expenseDescription = expense.getDescription();
        this.actorUserId = actor.getId();
        this.actorUsername = actor.getUsername();
        this.action = action;
        this.changedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getExpenseId() { return expenseId; }
    public String getExpenseDescription() { return expenseDescription; }
    public Long getActorUserId() { return actorUserId; }
    public String getActorUsername() { return actorUsername; }
    public Action getAction() { return action; }
    public Instant getChangedAt() { return changedAt; }
}
