# 3 — Databáze

PostgreSQL 16 v obraze ParadeDB (přidává extenzi `pg_search` pro BM25
fulltext). Všechno schéma vlastní **Liquibase**; Hibernate je jen v režimu
`validate`, tedy schéma nikdy nevytváří.

## Migrace

Soubor `backend/src/main/resources/db/changelog/db.changelog-master.xml`
registruje jednotlivé změny:

| Verze | Témat |
|---|---|
| `V1__init` | Extenze (citext, cube, earthdistance, pg_search) a základ: `users`, `seller_tax_profile`, `listings`, `listing_images`, `transactions`, `reviews`, `conversations`, `messages`, `reports` |
| `V2__dac7` | `users.is_admin`, `reviews.response_text`, tabulky `consents` a `audit_log`, index dokončených prodejů |
| `V3__favorites` | Tabulka `favorites` s UNIQUE `(user_id, listing_id)` |
| `V4__favorite_price` | `favorites.price_at_save` pro rozpoznání zlevnění |
| `V5__reserve_idempotency` | Sjednocení duplicitních rezervací + UNIQUE index na jednu aktivní rezervaci |
| `V6__notifications` | Tabulka `notifications` + index, bránící duplicitám upozornění |
| `V7__review_disputes` | Tabulka `review_disputes` + UNIQUE index na jednu otevřenou námitku |
| `V8__trader_verification` | Stavy ověření dokladu obchodníka (`verification_status`, poznámka, kdo a kdy) |
| `V9__withdrawal` | Stav `withdrawn` v transakcích + čas a důvod odstoupení |
| `V10__admin_governance` | `users.blocked_at`, `users.block_reason`, indexy pro čtení auditu |

Přidání migrace: nový `V11__nazev.sql` + `changeSet` v masteru. Změny už
nasazené migrace se upravovat nesmějí — `checksum` by rozdíl odhalil.

## Tabulky

### `users`

| Sloupec | Typ | Poznámka |
|---|---|---|
| `id` | BIGSERIAL PK | |
| `email` | CITEXT | UNIQUE, case-insensitive |
| `password_hash` | TEXT | bcrypt (12 kol) |
| `display_name` | TEXT | veřejné zobrazované jméno |
| `seller_type` | TEXT | `private` \| `trader` |
| `is_admin` | BOOLEAN | doplněno `V2` |
| `created_at`, `deleted_at` | TIMESTAMPTZ | `deleted_at` = GDPR soft-delete |
| `blocked_at`, `block_reason` | TIMESTAMPTZ, TEXT | doplněno `V10` |

### `seller_tax_profile`

Daňové údaje prodejce. **Všechny osobní údaje jsou šifrované** (AES-GCM 256)
a uložené jako `BYTEA`:

`full_name`, `date_of_birth`, `street`, `city`, `postal_code`, `country`,
`tax_id`, `ico`, `iban`.

Nesifrované: `user_id` (PK, FK), `self_cert` (čestné prohlášení),
`id_doc_bytea` (doklad), `verified` a `verification_status` (odkud a kdy
potvrdil administrátor), `verified_by`, `verified_at`, `verification_note`,
`updated_at`.

> Obsah šifrovaných sloupců se nikdy nevrací veřejným endpointem. Ve výkazu
> DAC7 se DIČ/IČO/IBAN dešifrují až při jeho sestavení administrátorem.

### `listings`

`id`, `seller_id` → users, `title`, `description`, `price_czk` (> 0),
`category`, `condition`, `city`, `lat`, `lon`, `seller_type_snapshot`,
`status` (`active` / `deleted` / `removed`), `created_at`.

`seller_type_snapshot` je neměnný: právo spotřebitele na odstoupení se řídí
typem prodejce **v okamžiku prodeje**, i kdyby prodejce později změnil profil.
Stejný snapshot se kopíruje do `transactions`.

Indexy: BM25 přes `id, title, description, category, condition, city`
(tolerantně — bez `pg_search` se přeskočí a vyhledávání padá na ILIKE),
`listings_status_idx`, `listings_seller_idx`.

### `listing_images`

`id`, `listing_id` (ON DELETE CASCADE), `data` BYTEA, `mime`, `sort_order`.
První fotografka (`sort_order = 0`) je hlavní. Při nahrání se EXIF metadata
odstraňují překódováním přes ImageIO.

### `transactions`

`id`, `listing_id`, `buyer_id`, `seller_id`, `price_czk` (snapshot),
`status`, `seller_type_snapshot`, `completed_at`, `withdrawn_at`,
`withdrawal_reason`.

```sql
CHECK (status IN ('reserved', 'completed', 'cancelled', 'withdrawn'))
```

Indexy: `transactions_seller_completed_idx` (částečný, na dokončené prodeje
podle prodejce a data) a `transactions_one_reserved_per_buyer_listing`
(částečný UNIQUE) — ten zabraňuje dvěma souběžným rezervacím téhož inzerátu
jedním kupcem, i na úrovni databáze.

`completed_at` je zásadní pro DAC7: určuje rok pro daňový výkaz. Stav
`withdrawn` je odlišný, takže odstoupené obchody do výkazu nespadají.

### `reviews`

`id`, `transaction_id` (UNIQUE), `reviewer_id`, `reviewee_id`, `listing_id`,
`rating` (1–5), `text`, `is_verified_purchase`, `moderation_status`
(`visible` / `removed`), `response_text`, `created_at`.

### `review_disputes`

`id`, `review_id` (CASCADE), `disputer_id`, `reason`, `status`
(`open` / `upheld` / `rejected`), `resolution_note`, `resolved_by`,
`created_at`, `resolved_at`.

`UNIQUE (review_id) WHERE status = 'open'` — dokud admin nerozhodne, nelze podat
další. Námitka vede vždy hodnoceného prodejce, takže počet potvrzených námek
propočítáme přímo po `disputer_id`.

### `conversations` a `messages`

`conversations`: `id`, `listing_id`, `buyer_id`, `seller_id`, `created_at`,
`UNIQUE (buyer_id, seller_id, listing_id)`.
`messages`: `id`, `conversation_id` (CASCADE), `sender_id`, `body_text`
(max 8 KB), `sent_at`, index `(conversation_id, id)`.

Text zpráv se ukládá v plaintextu, aby šel moderovat; přenos je chráněn TLS.

### `favorites`

`id`, `user_id` (CASCADE), `listing_id` (CASCADE), `created_at`,
`price_at_save`, `UNIQUE (user_id, listing_id)`.

### `notifications`

`id`, `user_id` (CASCADE), `type` (`price_drop`), `listing_id` (CASCADE),
`title`, `old_price_czk`, `new_price_czk`, `created_at`, `read_at`.

```sql
CREATE UNIQUE INDEX notifications_price_drop_uq
  ON notifications (user_id, listing_id, new_price_czk)
  WHERE type = 'price_drop' AND read_at IS NULL;
```

Díky tomu se stejné zlevnění nevykreslí dvakrát, dokud ho uživatel
nepřečte.

### `reports`, `consents`, `audit_log`

- `reports`: `target_type` (`listing`/`review`/`chat`), `target_id`,
  `reporter_id`, `reason`, `trusted_flagger`, `status`, `created_at`.
  Index `(status, trusted_flagger DESC, id)` — fronta řadí důvěryhodné hlásiče
  nahoru.
- `consents`: `user_id`, `purpose`, `granted`, `created_at`.
- `audit_log`: `actor_id`, `action`, `target_type`, `target_id`, `created_at`.
  Indexy podle akce a data pro čtení v administraci.

## Přehled vazeb

```
users ──1:1── seller_tax_profile
  │
  ├──1:N── listings ──1:N── listing_images
  │            │
  │            └──1:N── transactions ──1:1── reviews ──1:N── review_disputes
  │                                      │
  ├──1:N── favorites ─────────────────────┘
  ├──1:N── notifications
  ├──1:N── consents
  ├──1:N── audit_log (actor)
  ├──1:N── reports (reporter)
  └──1:N── conversations (buyer | seller) ──1:N── messages
```

Vztahy jsou cizí klíče bez ORM anotací (`@OneToMany` se nepoužívá) —
integritu hlídá databáze a JPA entity se načítají jednotlivě.

## Retence a mazání

- **Soft-delete uživatelů**: `deleted_at`; přihlášení i obnova tokenu odmítnou
  (chyba `ACCOUNT_DELETED`).
- **Daňový profil zůstává** — zákonná retence pro plnění ohlašovací povinnosti
  (DAC7). Export uživatele daňové údaje **neobsahuje**.
- **Inzeráty**: soft-delete (`status = 'deleted'`), transakce zůstávají —
  dokládají uskutečněný prodej.
- **Odstranění moderací**: `status = 'removed'`; obsah se nezobrazuje, ale
  záznam zůstává kvůli auditu.
- **Kaskády**: fotografie, oblíbené, upozornění a konverzace se mažou s
  nadřazeným záznamem.