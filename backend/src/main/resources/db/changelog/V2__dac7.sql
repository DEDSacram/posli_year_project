-- V2__dac7.sql — DAC7 / GDPR / moderace / admin doplnky
ALTER TABLE users ADD COLUMN IF NOT EXISTS is_admin BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE reviews ADD COLUMN IF NOT EXISTS response_text TEXT;

CREATE TABLE IF NOT EXISTS consents (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id),
  purpose TEXT NOT NULL,
  granted BOOLEAN NOT NULL,
  created_at TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX IF NOT EXISTS consents_user_idx ON consents(user_id);

CREATE TABLE IF NOT EXISTS audit_log (
  id BIGSERIAL PRIMARY KEY,
  actor_id BIGINT REFERENCES users(id),
  action TEXT NOT NULL,
  target_type TEXT,
  target_id BIGINT,
  created_at TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX IF NOT EXISTS audit_log_actor_idx ON audit_log(actor_id);

-- DAC7 agregacni index: dokoncene prodeje prodejce
CREATE INDEX IF NOT EXISTS transactions_seller_completed_idx
  ON transactions(seller_id, completed_at) WHERE status = 'completed';
