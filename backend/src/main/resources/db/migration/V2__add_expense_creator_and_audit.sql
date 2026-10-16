-- Earlier records did not store their creator; use their recorded payer as the fallback owner.
ALTER TABLE recurring_expense ADD COLUMN created_by BIGINT NULL;
UPDATE recurring_expense SET created_by = paid_by WHERE created_by IS NULL;
ALTER TABLE recurring_expense MODIFY created_by BIGINT NOT NULL,
    ADD CONSTRAINT fk_recurring_expense_creator FOREIGN KEY (created_by) REFERENCES users(id);

ALTER TABLE expense ADD COLUMN created_by BIGINT NULL;
UPDATE expense SET created_by = paid_by WHERE created_by IS NULL;
UPDATE expense e JOIN recurring_expense r ON e.recurring_expense_id = r.recurring_expense_id
SET e.created_by = r.created_by
WHERE e.created_by <> r.created_by;
ALTER TABLE expense MODIFY created_by BIGINT NOT NULL,
    ADD CONSTRAINT fk_expense_creator FOREIGN KEY (created_by) REFERENCES users(id);

CREATE TABLE expense_audit (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    group_id BIGINT NOT NULL,
    expense_id BIGINT NOT NULL,
    expense_description VARCHAR(255) NOT NULL,
    actor_user_id BIGINT NOT NULL,
    actor_username VARCHAR(255) NOT NULL,
    action VARCHAR(10) NOT NULL,
    changed_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_expense_audit_group FOREIGN KEY (group_id)
        REFERENCES expense_group(group_id) ON DELETE CASCADE,
    INDEX idx_expense_audit_group_time (group_id, changed_at, id)
);
