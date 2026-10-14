-- Earlier records did not store their creator; use their recorded payer as the fallback owner.
ALTER TABLE recurring_expense ADD COLUMN created_by BIGINT NULL;
UPDATE recurring_expense SET created_by = paid_by;
ALTER TABLE recurring_expense MODIFY created_by BIGINT NOT NULL,
    ADD CONSTRAINT fk_recurring_expense_creator FOREIGN KEY (created_by) REFERENCES users(id);

ALTER TABLE expense ADD COLUMN created_by BIGINT NULL;
UPDATE expense SET created_by = paid_by;
UPDATE expense e JOIN recurring_expense r ON e.recurring_expense_id = r.recurring_expense_id
SET e.created_by = r.created_by;
ALTER TABLE expense MODIFY created_by BIGINT NOT NULL,
    ADD CONSTRAINT fk_expense_creator FOREIGN KEY (created_by) REFERENCES users(id);
