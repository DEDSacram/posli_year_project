-- V8__trader_verification.sql — ověřování obchodníků (DSA)
-- Nahraný doklad čeká na rozhodnutí administrátora; stav se eviduje vedle existujícího
-- příznaku `verified`, který zůstává jako odvozený (verified = (status = 'verified')).
ALTER TABLE seller_tax_profile ADD COLUMN IF NOT EXISTS verification_status TEXT NOT NULL DEFAULT 'pending';
ALTER TABLE seller_tax_profile ADD COLUMN IF NOT EXISTS verification_note TEXT;
ALTER TABLE seller_tax_profile ADD COLUMN IF NOT EXISTS verified_at TIMESTAMPTZ;
ALTER TABLE seller_tax_profile ADD COLUMN IF NOT EXISTS verified_by BIGINT REFERENCES users(id);

CREATE INDEX IF NOT EXISTS seller_tax_profile_verif_idx
  ON seller_tax_profile(verification_status, updated_at DESC);