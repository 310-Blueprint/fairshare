package nz.ac.auckland.se310.fairshare.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "individual_debt")
public class IndividualDebt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // #2 AC7: who recorded the entry. Immutable - unlike payer/debtor, this never changes on edit.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "creator_id", nullable = false, updatable = false)
    private User creator;

    // The person who is owed the money.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payer_id", nullable = false)
    private User payer;

    // The person who owes the money.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "debtor_id", nullable = false)
    private User debtor;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    // ISO 4217 code the amount was recorded in; converted for display, never on save.
    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, length = 255)
    private String description;

    @Column(name = "debt_date", nullable = false)
    private LocalDate debtDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected IndividualDebt() {} // JPA

    public IndividualDebt(User creator, User payer, User debtor, BigDecimal amount, String currency,
                          String description, LocalDate debtDate) {
        this.creator = creator;
        this.payer = payer;
        this.debtor = debtor;
        this.amount = amount;
        this.currency = currency;
        this.description = description;
        this.debtDate = debtDate;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public User getCreator() { return creator; }
    public User getPayer() { return payer; }
    public void setPayer(User payer) { this.payer = payer; }
    public User getDebtor() { return debtor; }
    public void setDebtor(User debtor) { this.debtor = debtor; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public LocalDate getDebtDate() { return debtDate; }
    public void setDebtDate(LocalDate debtDate) { this.debtDate = debtDate; }
    public Instant getCreatedAt() { return createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IndividualDebt other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
