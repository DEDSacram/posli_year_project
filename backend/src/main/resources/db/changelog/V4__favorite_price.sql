-- V4__favorite_price.sql — cena v okamžiku uložení pro badge zlevnění
ALTER TABLE favorites ADD COLUMN IF NOT EXISTS price_at_save INTEGER;
