-- V7__review_disputes.sql — námitky prodejce proti neférovému hodnocení
-- Prodejce (hodnocený) podá námitku, admin ji rozhodne: uphold → recenze se odstraní,
-- reject → recenze zůstává platná. Námitka je nerozhodnutá jen do rozhodnutí adminu.
CREATE TABLE IF NOT EXISTS review_disputes (
  id BIGSERIAL PRIMARY KEY,
  review_id BIGINT NOT NULL REFERENCES reviews(id) ON DELETE CASCADE,
  disputer_id BIGINT NOT NULL REFERENCES users(id),
  reason TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'open' CHECK (status IN ('open', 'upheld', 'rejected')),
  resolution_note TEXT,
  resolved_by BIGINT REFERENCES users(id),
  created_at TIMESTAMPTZ DEFAULT now(),
  resolved_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS review_disputes_review_idx ON review_disputes(review_id);
CREATE INDEX IF NOT EXISTS review_disputes_status_idx ON review_disputes(status, id);

-- Jedna otevřená námitka na recenzi (další už nelze podat, dokud admin nerozhodne).
CREATE UNIQUE INDEX IF NOT EXISTS review_disputes_one_open_uq
  ON review_disputes(review_id) WHERE status = 'open';

-- Počet potvrzených námek proti prodejci (pro odznaky "bez stížností").
CREATE INDEX IF NOT EXISTS review_disputes_disputer_idx ON review_disputes(disputer_id, status);