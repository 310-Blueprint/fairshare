-- #14: amounts in currencies such as KRW, IDR and JPY can pass 99,999,999.99 (the DECIMAL(10,2)
-- limit) in everyday use, e.g. USD 100,000 is about KRW 135,000,000.
-- Expenses now go up to 9,999,999,999,999.99. Shares and settlements match the DECIMAL(19,2)
-- net balances they are built from, so they can always hold whatever an expense or balance does.
ALTER TABLE expense
    MODIFY amount          DECIMAL(15,2) NOT NULL,
    MODIFY original_amount DECIMAL(15,2) NOT NULL;

ALTER TABLE expense_share
    MODIFY share_amount DECIMAL(19,2) NOT NULL;

ALTER TABLE settlement
    MODIFY amount DECIMAL(19,2) NOT NULL;
