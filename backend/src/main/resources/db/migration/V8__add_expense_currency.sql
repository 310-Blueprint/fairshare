-- #14: keep what the member actually entered alongside the converted amount.
-- expense.amount stays in the group's base currency so balances and settlements are unchanged.
ALTER TABLE expense
    ADD COLUMN original_amount   DECIMAL(10,2) NULL,
    ADD COLUMN original_currency CHAR(3)       NULL,
    ADD COLUMN exchange_rate     DECIMAL(18,8) NULL;

-- Every expense recorded before this was entered in the group's base currency.
UPDATE expense e
    JOIN expense_group g ON e.group_id = g.group_id
SET e.original_amount   = e.amount,
    e.original_currency = g.base_currency,
    e.exchange_rate     = 1;

ALTER TABLE expense
    MODIFY original_amount   DECIMAL(10,2) NOT NULL,
    MODIFY original_currency CHAR(3)       NOT NULL,
    MODIFY exchange_rate     DECIMAL(18,8) NOT NULL;
