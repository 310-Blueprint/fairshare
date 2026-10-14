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
