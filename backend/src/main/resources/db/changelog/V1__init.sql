-- V1__init.sql — zakladni schema dle design_doc.pdf kap. 4
CREATE EXTENSION IF NOT EXISTS citext;
CREATE EXTENSION IF NOT EXISTS cube;
CREATE EXTENSION IF NOT EXISTS earthdistance;
-- pg_search (ParadeDB) nemusi byt dostupna vsude: vytvarime tolerantne
DO $$ BEGIN
  CREATE EXTENSION IF NOT EXISTS pg_search;
EXCEPTION WHEN OTHERS THEN
  RAISE NOTICE 'pg_search neni dostupna, fulltext bude resen fallbackem (ILIKE): %', SQLERRM;
END $$;

CREATE TABLE users (
  id BIGSERIAL PRIMARY KEY,
  email CITEXT UNIQUE NOT NULL,
  password_hash TEXT NOT NULL,
  display_name TEXT NOT NULL,
  seller_type TEXT NOT NULL CHECK (seller_type IN ('private','trader')),
  created_at TIMESTAMPTZ DEFAULT now(),
  deleted_at TIMESTAMPTZ
);

CREATE TABLE seller_tax_profile (
  user_id BIGINT PRIMARY KEY REFERENCES users(id),
  full_name BYTEA NOT NULL,
  date_of_birth BYTEA NOT NULL,
  street BYTEA NOT NULL,
  city BYTEA NOT NULL,
  postal_code BYTEA NOT NULL,
  country BYTEA NOT NULL,
  tax_id BYTEA,
  ico BYTEA,
  iban BYTEA NOT NULL,
  self_cert BOOLEAN NOT NULL DEFAULT FALSE,
  id_doc_bytea BYTEA,
  verified BOOLEAN NOT NULL DEFAULT FALSE,
  updated_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE listings (
  id BIGSERIAL PRIMARY KEY,
  seller_id BIGINT NOT NULL REFERENCES users(id),
  title TEXT NOT NULL,
  description TEXT NOT NULL,
  price_czk INT NOT NULL CHECK (price_czk > 0),
  category TEXT NOT NULL,
  condition TEXT NOT NULL,
  city TEXT NOT NULL,
  lat DOUBLE PRECISION,
  lon DOUBLE PRECISION,
  seller_type_snapshot TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'active',
  created_at TIMESTAMPTZ DEFAULT now()
);

-- BM25 index jen pokud existuje pg_search (ParadeDB)
DO $$ BEGIN
  CREATE INDEX listings_bm25_idx ON listings
    USING bm25 (id, title, description, category, condition, city)
    WITH (key_field = 'id');
EXCEPTION WHEN OTHERS THEN
  RAISE NOTICE 'BM25 index preskocen (pg_search chybi): %', SQLERRM;
END $$;
CREATE INDEX listings_status_idx ON listings(status);
CREATE INDEX listings_seller_idx ON listings(seller_id);

CREATE TABLE listing_images (
  id BIGSERIAL PRIMARY KEY,
  listing_id BIGINT NOT NULL REFERENCES listings(id) ON DELETE CASCADE,
  data BYTEA NOT NULL,
  mime TEXT NOT NULL,
  sort_order INT NOT NULL DEFAULT 0
);

CREATE TABLE transactions (
  id BIGSERIAL PRIMARY KEY,
  listing_id BIGINT NOT NULL REFERENCES listings(id),
  buyer_id BIGINT NOT NULL REFERENCES users(id),
  seller_id BIGINT NOT NULL REFERENCES users(id),
  price_czk INT NOT NULL,
  status TEXT NOT NULL DEFAULT 'reserved'
    CHECK (status IN ('reserved','completed','cancelled')),
  seller_type_snapshot TEXT NOT NULL,
  completed_at TIMESTAMPTZ
);

CREATE TABLE reviews (
  id BIGSERIAL PRIMARY KEY,
  transaction_id BIGINT UNIQUE NOT NULL REFERENCES transactions(id),
  reviewer_id BIGINT NOT NULL REFERENCES users(id),
  reviewee_id BIGINT NOT NULL REFERENCES users(id),
  listing_id BIGINT NOT NULL REFERENCES listings(id),
  rating SMALLINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
  text TEXT NOT NULL,
  is_verified_purchase BOOLEAN NOT NULL DEFAULT TRUE,
  moderation_status TEXT NOT NULL DEFAULT 'visible',
  created_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE conversations (
  id BIGSERIAL PRIMARY KEY,
  listing_id BIGINT NOT NULL REFERENCES listings(id),
  buyer_id BIGINT NOT NULL REFERENCES users(id),
  seller_id BIGINT NOT NULL REFERENCES users(id),
  created_at TIMESTAMPTZ DEFAULT now(),
  UNIQUE (buyer_id, seller_id, listing_id)
);

CREATE TABLE messages (
  id BIGSERIAL PRIMARY KEY,
  conversation_id BIGINT NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
  sender_id BIGINT NOT NULL REFERENCES users(id),
  body_text TEXT NOT NULL,
  sent_at TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX messages_conv_idx ON messages(conversation_id, id);

CREATE TABLE reports (
  id BIGSERIAL PRIMARY KEY,
  target_type TEXT NOT NULL CHECK (target_type IN ('listing','review','chat')),
  target_id BIGINT NOT NULL,
  reporter_id BIGINT NOT NULL REFERENCES users(id),
  reason TEXT NOT NULL,
  trusted_flagger BOOLEAN NOT NULL DEFAULT FALSE,
  status TEXT NOT NULL DEFAULT 'open',
  created_at TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX reports_status_idx ON reports(status, trusted_flagger DESC, id);
