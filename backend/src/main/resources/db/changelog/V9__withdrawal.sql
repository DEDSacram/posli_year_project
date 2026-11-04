-- V9__withdrawal.sql — právo spotřebitele na odstoupení od smlouvy (14 dní, § 1829 OZ)
-- Stav `withdrawn` se od completed liší, takže dotazy DAC7 (status = 'completed')
-- odstoupené obchody do výkazu nespadají.
ALTER TABLE transactions DROP CONSTRAINT IF EXISTS transactions_status_check;
ALTER TABLE transactions
  ADD CONSTRAINT transactions_status_check CHECK (status IN ('reserved', 'completed', 'cancelled', 'withdrawn'));

ALTER TABLE transactions ADD COLUMN IF NOT EXISTS withdrawn_at TIMESTAMPTZ;
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS withdrawal_reason TEXT;

CREATE INDEX IF NOT EXISTS transactions_completed_idx
  ON transactions(completed_at) WHERE status = 'completed';