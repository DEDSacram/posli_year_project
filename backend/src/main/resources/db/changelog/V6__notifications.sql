-- V6__notifications.sql — upozornění na zlevnění inzerátů uložených do oblíbených
-- Upozornění vzniká při reálné změně ceny (PUT /listings/:id), ne jen při čtení.
CREATE TABLE IF NOT EXISTS notifications (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  type TEXT NOT NULL CHECK (type IN ('price_drop')),
  listing_id BIGINT NOT NULL REFERENCES listings(id) ON DELETE CASCADE,
  title TEXT NOT NULL,
  old_price_czk INTEGER,
  new_price_czk INTEGER,
  created_at TIMESTAMPTZ DEFAULT now(),
  read_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS notifications_user_idx ON notifications(user_id, id DESC);

-- Jedno nepřečtené upozornění na daný inzerát a cenu (ochrana proti duplicitám při opakovaném uložení).
CREATE UNIQUE INDEX IF NOT EXISTS notifications_price_drop_uq
  ON notifications(user_id, listing_id, new_price_czk)
  WHERE type = 'price_drop' AND read_at IS NULL;