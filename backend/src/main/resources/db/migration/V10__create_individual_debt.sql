CREATE TABLE individual_debt
(
    id          BIGINT AUTO_INCREMENT NOT NULL,
    creator_id  BIGINT                NOT NULL,
    payer_id    BIGINT                NOT NULL,
    debtor_id   BIGINT                NOT NULL,
    amount      DECIMAL(19, 2)        NOT NULL,
    description VARCHAR(255)          NOT NULL,
    debt_date   date                  NOT NULL,
    created_at  TIMESTAMP             NOT NULL,
    CONSTRAINT pk_individual_debt PRIMARY KEY (id)
);

ALTER TABLE individual_debt
    ADD CONSTRAINT FK_INDIVIDUAL_DEBT_ON_CREATOR FOREIGN KEY (creator_id) REFERENCES users (id);

ALTER TABLE individual_debt
    ADD CONSTRAINT FK_INDIVIDUAL_DEBT_ON_PAYER FOREIGN KEY (payer_id) REFERENCES users (id);

ALTER TABLE individual_debt
    ADD CONSTRAINT FK_INDIVIDUAL_DEBT_ON_DEBTOR FOREIGN KEY (debtor_id) REFERENCES users (id);
