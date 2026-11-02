-- V5__reserve_idempotency.sql — jedna aktivní rezervace na kupce + inzerát
-- 1) Sjednocení starých duplikátů: ponechat nejstarší reserved, zbytek zrušit.
UPDATE transactions t SET status = 'cancelled'
WHERE t.status = 'reserved'
  AND t.id NOT IN (
    SELECT MIN(id) FROM transactions
    WHERE status = 'reserved'
    GROUP BY listing_id, buyer_id
  );
-- 2) Pojistka na úrovni DB proti závodu (race) dvou paralelních rezervací.
CREATE UNIQUE INDEX IF NOT EXISTS transactions_one_reserved_per_buyer_listing
  ON transactions(listing_id, buyer_id) WHERE status = 'reserved';
