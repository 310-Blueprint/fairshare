-- #16 AC1: what each expense was for. Nullable, because expenses recorded before this and
-- recurring occurrences have no category; the spending overview shows them as uncategorised.
-- VARCHAR2 is Oracle-specific; this project uses MySQL, where VARCHAR is required.
ALTER TABLE expense
    ADD COLUMN category VARCHAR(20) NULL AFTER description; -- NOSONAR
