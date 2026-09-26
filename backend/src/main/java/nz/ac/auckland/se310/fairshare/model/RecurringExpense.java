package nz.ac.auckland.se310.fairshare.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "recurring_expense")
public class RecurringExpense {

    public enum Frequency {
        WEEKLY,
        FORTNIGHTLY,
        MONTHLY
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "recurring_expense_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false, updatable = false)
    private ExpenseGroup group;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "paid_by", nullable = false)
    private User paidBy;

    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "description", nullable = false, length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "frequency", nullable = false, length = 20, updatable = false)
    private Frequency frequency;

    @Column(name = "start_date", nullable = false, updatable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    // How many occurrences have been generated so far. Monthly due dates are computed as
    // startDate + occurrenceCount months (clamped to the shorter month), never by adding a
    // month to the previous due date, so a run of short months can't drift the schedule.
    @Column(name = "occurrence_count", nullable = false)
    private int occurrenceCount;

    @Column(name = "next_due_date", nullable = false)
    private LocalDate nextDueDate;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RecurringExpense() {} // JPA

    /**
     * Represents a recurring charge like rent that generates a new Expense in the group each
     * time its schedule comes due. Starts with no occurrences generated; the first occurrence
     * falls due on startDate itself.
     */
    public RecurringExpense(ExpenseGroup group, User paidBy, BigDecimal amount, String description,
                             Frequency frequency, LocalDate startDate, LocalDate endDate) {
        this.group = group;
        this.paidBy = paidBy;
        this.amount = amount;
        this.description = description;
        this.frequency = frequency;
        this.startDate = startDate;
        this.endDate = endDate;
        this.occurrenceCount = 0;
        this.nextDueDate = startDate;
        this.active = true;
        this.createdAt = Instant.now();
    }

    /**
     * The date of the nth occurrence (0-indexed, so occurrenceDate(0) is startDate). Monthly
     * occurrences are always startDate + n months rather than repeatedly adding a month to the
     * previous occurrence, so a short month (Jan 31 -> Feb 28) clamps only that one occurrence
     * instead of shifting every later one (Mar is still the 31st, not the 28th).
     */
    public LocalDate occurrenceDate(int occurrenceIndex) {
        return switch (frequency) {
            case WEEKLY -> startDate.plusWeeks(occurrenceIndex);
            case FORTNIGHTLY -> startDate.plusWeeks(occurrenceIndex * 2L);
            case MONTHLY -> startDate.plusMonths(occurrenceIndex);
        };
    }

    /**
     * Records that the occurrence currently due (nextDueDate) has been generated, and advances
     * the schedule to the following one. Ends the series instead if that next occurrence would
     * fall after endDate (AC4).
     */
    public void advanceAfterGenerating() {
        occurrenceCount++;
        LocalDate next = occurrenceDate(occurrenceCount);
        if (endDate != null && next.isAfter(endDate)) {
            active = false;
        } else {
            nextDueDate = next;
        }
    }

    public Long getId() { return id; }
    public ExpenseGroup getGroup() { return group; }
    public User getPaidBy() { return paidBy; }
    public BigDecimal getAmount() { return amount; }
    public String getDescription() { return description; }
    public Frequency getFrequency() { return frequency; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public int getOccurrenceCount() { return occurrenceCount; }
    public LocalDate getNextDueDate() { return nextDueDate; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RecurringExpense other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
