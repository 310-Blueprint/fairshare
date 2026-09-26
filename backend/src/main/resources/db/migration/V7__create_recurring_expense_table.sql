CREATE TABLE recurring_expense (
    recurring_expense_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    group_id              BIGINT        NOT NULL,
    paid_by               BIGINT        NOT NULL,
    amount                DECIMAL(10,2) NOT NULL,
    description            VARCHAR(255)  NOT NULL,
    frequency              VARCHAR(20)   NOT NULL,
    start_date             DATE          NOT NULL,
    end_date               DATE          NULL,
    occurrence_count       INT           NOT NULL DEFAULT 0,
    next_due_date          DATE          NOT NULL,
    active                 BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at             TIMESTAMP     NOT NULL,
    CONSTRAINT fk_recurring_expense_group
        FOREIGN KEY (group_id) REFERENCES expense_group (group_id),
    CONSTRAINT fk_recurring_expense_paid_by
        FOREIGN KEY (paid_by) REFERENCES users (id)
);

CREATE TABLE recurring_expense_participant (
    recurring_expense_participant_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    recurring_expense_id             BIGINT NOT NULL,
    user_id                          BIGINT NOT NULL,
    CONSTRAINT uq_rep_user_recurring_expense
        UNIQUE (user_id, recurring_expense_id),
    CONSTRAINT fk_rep_recurring_expense
        FOREIGN KEY (recurring_expense_id) REFERENCES recurring_expense (recurring_expense_id),
    CONSTRAINT fk_rep_user
        FOREIGN KEY (user_id) REFERENCES users (id)
);

ALTER TABLE expense
    ADD COLUMN recurring_expense_id BIGINT NULL,
    ADD CONSTRAINT fk_expense_recurring_expense
        FOREIGN KEY (recurring_expense_id) REFERENCES recurring_expense (recurring_expense_id),
    ADD CONSTRAINT uq_expense_recurring_expense_date
        UNIQUE (recurring_expense_id, expense_date);
