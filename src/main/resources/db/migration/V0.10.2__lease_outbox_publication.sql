ALTER TABLE async_outbox_message ADD COLUMN claim_token CHAR(36) NULL,
    ADD COLUMN claim_until DATETIME(3) NULL,
    ADD CONSTRAINT ck_async_outbox_claim CHECK(
      (claim_token IS NULL AND claim_until IS NULL)
      OR (status='PENDING' AND claim_token IS NOT NULL AND claim_until IS NOT NULL));
