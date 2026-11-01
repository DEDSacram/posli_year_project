-- V3__favorites.sql — oblíbené inzeráty navázané na uživatelský profil
CREATE TABLE IF NOT EXISTS favorites (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  listing_id BIGINT NOT NULL REFERENCES listings(id) ON DELETE CASCADE,
  created_at TIMESTAMPTZ DEFAULT now(),
  CONSTRAINT favorites_user_listing_uq UNIQUE (user_id, listing_id)
);
CREATE INDEX IF NOT EXISTS favorites_user_idx ON favorites(user_id);
