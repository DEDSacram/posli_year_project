# 2 — Reference REST API

Základní URL zákazníka: `http://localhost:8081`. Všechny odpovědi jsou JSON
v UTF-8, kódování českých znaků je zaručeno hlavičkou `Content-Type`.

## Konvence

### Autentizace

Chráněné endpointy vyžadují hlavičku:

```
Authorization: Bearer <accessToken>
```

Access token platí 15 minut, refresh token 30 dní. Bez tokenu server vrací
**401**; s neplatným oprávněním **403**.

### Stránkování

Výpisy vrací jednotný tvar a kurzor (poslední viděné `id`):

```json
{ "data": [ ... ], "nextCursor": "42" }
```

`nextCursor` je `null` na poslední straně. Limit je 1–50 (výchozí 20).

### Chyby

```json
{ "code": "DAC7_PROFILE_REQUIRED", "messageCs": "Pro publikaci doplňte daňový profil…" }
```

`code` je strojový kód pro programátora, `messageCs` hláška pro uživatele.
Klienty zobrazují `messageCs` přímo.

| HTTP | Kód | Význam |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Vstup neprošel validací |
| 400 | `INVALID_*` | Chybná hodnota (geografie, cena, role, …) |
| 401 | `UNAUTHORIZED` | Chybí přihlášení |
| 401 | `INVALID_CREDENTIALS` | Špatný e-mail nebo heslo |
| 401 | `INVALID_TOKEN` | Neplatný nebo vypršelý token |
| 403 | `NOT_PARTICIPANT` | Nejste účastníkem transakce |
| 403 | `NOT_OWNER` | Nejste vlastníkem |
| 403 | `ACCOUNT_BLOCKED` | Účet zablokoval administrátor |
| 409 | `EMAIL_EXISTS` | E-mail už existuje |
| 409 | `REVIEW_EXISTS` | Transakce už byla hodnocena |
| 409 | `DISPUTE_OPEN` | K recenzi už je otevřená námitka |
| 422 | `DAC7_PROFILE_REQUIRED` | Chybí daňový profil prodejce |
| 422 | `TRADER_PROFILE_INCOMPLETE` | Obchodník nemá IČO, doklad nebo čestné prohlášení |
| 429 | `RATE_LIMITED` | Překročen limit požadavků |
| 500 | `INTERNAL_ERROR` | Neočekávaná chyba |

---

## Zdravotní stav

| Metoda | Cesta | Popis |
|---|---|---|
| GET | `/health` | `{"status":"UP","db":"UP"}` — bez autentizace |

## Autentizace — `/auth`

| Metoda | Cesta | Vstup | Popis |
|---|---|---|---|
| POST | `/auth/register` | `{email, password, displayName, sellerType}` | Vrátí `{id, token, refreshToken}` |
| POST | `/auth/login` | `{email, password}` | Vrátí `{id, token, refreshToken}` |
| POST | `/auth/refresh` | `{refreshToken}` | Vymění refresh za novou dvojici |

`password` musí mít alespoň 8 znaků, `sellerType` je `private` nebo `trader`.

## Uživatel a prodejce

| Metoda | Cesta | Popis |
|---|---|---|
| GET | `/users/{id}` | Veřejný profil (bez daňových údajů) |
| GET | `/users/{id}/badges` | Odznaky prodejce — viz níže |
| GET | `/users/{id}/rating` | `{average, count}` průměrné hodnocení |
| GET | `/users/{id}/reviews` | Viditelné recenze (kurzor, limit) |
| PATCH | `/users/me/seller-info` | Uloží daňový profil (AES-GCM) |
| POST | `/users/me/id-document` | multipart `file` — doklad obchodníka (max 5 MB) |
| GET | `/seller/me/dac7-status` | `{salesCount, totalAmountCzk, isReportable}` |
| GET | `/seller/me/verification` | Stav ověření dokladu obchodníka |
| GET | `/seller/me/location` | Město a PSČ z profilu (předvyplnění) |
| PATCH | `/seller/me/location` | `{city, postalCode}` — uloží polohu do profilu |
| GET | `/users/me/export` | Export všech osobních údajů (GDPR) |
| DELETE | `/users/me` | Soft-delete účtu |
| POST | `/consents` | `{purpose, granted}` — souhlas (GDPR) |

### Odznaky (`GET /users/{id}/badges`)

Část je statická (typ prodejce, existence daňového profilu), část se
odvozuje z historie obchodů:

```json
{
  "trader": true,
  "privateSeller": false,
  "hasDac7Profile": true,
  "verifiedTrader": true,
  "trustedSeller": true,
  "highlyRated": true,
  "experienced": false,
  "cleanRecord": false,
  "ratingAverage": 4.7,
  "ratingCount": 12,
  "completedSales": 8
}
```

| Odznak | Podmínka |
|---|---|
| `verifiedTrader` | Obchodník, jehož doklad potvrdil administrátor |
| `trustedSeller` | Hodnocení ≥ 4,5 z ≥ 3 hodnocení a žádná potvrzená námitka |
| `highlyRated` | Hodnocení ≥ 4,8 z ≥ 5 hodnocení |
| `experienced` | 10 a více dokončených prodejů |
| `cleanRecord` | Žádná potvrzená námitka proti jeho hodnocení |

## Inzeráty — `/listings`

| Metoda | Cesta | Popis |
|---|---|---|
| POST | `/listings` | Vložení (vyžaduje daňový profil) |
| GET | `/listings/{id}` | Detail inzerátu |
| PUT | `/listings/{id}` | Úprava vlastního inzerátu |
| DELETE | `/listings/{id}` | Soft-delete |
| POST | `/listings/{id}/images` | multipart `file` — JPEG/PNG/WebP, max 5 MB |
| DELETE | `/listings/{id}/images/{imgId}` | Smazání fotografie |
| GET | `/listings/{id}/images/{imgId}` | Binární data fotografie |
| GET | `/listings/{id}/rights-info` | Spotřebitelská práva k inzerátu |
| GET | `/listings/feed` | Katalog s kurzorem |
| GET | `/listings/search` | Hledání — viz níže |
| GET | `/listings/suggest` | Odhad kategorie a medianu ceny podle názvu |

Vstup: `{title, description, priceCzk, category, condition, city, lat?, lon?}`.
Souřadnice jsou volitelné a musí být zadány **obě** dohromady (jinak 400) —
publikují se jen když je prodejce výslovně pošle.

### `GET /listings/search`

| Parametr | Význam |
|---|---|
| `q` | Dotaz, max 200 znaků; hledá celá slova (BM25) i části slov |
| `category` | Přesná hodnota kategorie |
| `minPrice`, `maxPrice` | Rozsah ceny v Kč |
| `lat`, `lon`, `radiusKm` | Poloha a okruh (0–2000 km), musí být všechny tři |
| `sort` | `relevance` (výchozí), `newest`, `priceAsc`, `priceDesc` |
| `cursor`, `limit` | Stránkování (limit max 50) |

## Oblíbené — `/favorites`

| Metoda | Cesta | Popis |
|---|---|---|
| GET | `/favorites/ids` | `{ids: [...]}` pro synchronizaci klienta |
| GET | `/favorites/mine` | Uložené inzeráty včetně zlevnění od uložení |
| POST | `/favorites/{listingId}` | Uložení (zapíše cenu do `price_at_save`) |
| DELETE | `/favorites/{listingId}` | Odebrání |

Odpověď `/favorites/mine` navíc obsahuje `savedPriceCzk` a `priceDrop` pro
inzeráty, jejichž cena od uložení klesla.

## Upozornění — `/notifications`

| Metoda | Cesta | Popis |
|---|---|---|
| GET | `/notifications` | Upozornění v profilu (kurzor, limit) |
| GET | `/notifications/unread-count` | `{unread: 2}` pro odznak v hlavičce |
| POST | `/notifications/{id}/read` | Označí jedno jako přečtené |
| POST | `/notifications/read-all` | Označí vše |

```json
{
  "id": 12,
  "type": "price_drop",
  "listingId": 25,
  "title": "Zlevnění: Vintage kočárek",
  "oldPriceCzk": 5000,
  "newPriceCzk": 3000,
  "read": false,
  "createdAt": "2026-10-04T23:04:13Z"
}
```

Upozornění vzniká při skutečném snížení ceny; duplicitám brání unikátní index
na `(user_id, listing_id, new_price_czk)` pro nepřečtené záznamy.

## Transakce a obchody

| Metoda | Cesta | Popis |
|---|---|---|
| POST | `/transactions` | Rezervace `{listingId}` — **idempotentní** |
| GET | `/transactions/mine?role=all\|buyer\|seller` | Transakce přihlášeného |
| GET | `/transactions/overview` | Přehled obchodů včetně získaného hodnocení |
| POST | `/transactions/{id}/complete` | Dokončení (nastaví `completed_at`) |
| POST | `/transactions/{id}/cancel` | Zrušení rezervace |
| POST | `/transactions/{id}/withdraw` | Odstoupení spotřebitele od smlouvy |
| GET | `/transactions/{id}/withdrawal-rights` | Lhůta a nárok na odstoupení |
| POST | `/transactions/{id}/review` | Hodnocení `{rating: 1–5, text}` |

Stavy: `reserved` → `completed` | `cancelled` | `withdrawn`.

Rezervace je **idempotentní** — opakovaný požadavek stejného kupce na stejný
inzerát vrátí existující rezervaci (na úrovni aplikace i DB indexem).

### `GET /transactions/overview`

```json
{
  "asSeller": { "reserved": 1, "completed": 12, "cancelled": 2, "withdrawn": 0,
                "revenueCzk": 48000, "ratingAverage": 4.7, "ratingCount": 9 },
  "asBuyer":  { "reserved": 0, "completed": 3, "cancelled": 1, "withdrawn": 1 }
}
```

### Odstoupení od smlouvy (§ 1829 OZ)

`POST /transactions/{id}/withdraw` — smí pouze kupující, pouze u obchodníka
(snapshot typu v transakci) a pouze do 14 dní od dokončení. Vstup volitelný
`{reason}`. Jinak `WITHDRAWAL_NOT_APPLIES` nebo `WITHDRAWAL_EXPIRED`.

`GET /transactions/{id}/withdrawal-rights` vrátí `{applies, days, completedAt,
deadline, eligible, explanationCs}` — UI podle toho zobrazí tlačítko i datum
konečného termínu.

## Hodnocení a námitky

| Metoda | Cesta | Popis |
|---|---|---|
| POST | `/reviews/{id}/response` | Veřejná odpověď prodejce (jednou) |
| POST | `/reviews/{id}/dispute` | Námitka prodejce proti hodnocení |
| GET | `/reviews/disputes/mine` | Námitky podané přihlášeným |

Hodnocit smí **jen kupující** a **jen dokončenou** transakci; jedna recenze na
transakci (`transaction_id` je UNIQUE). Recenze má příznak `isVerifiedPurchase`.

Námitku smí podat **jen hodnocený prodejce**, nejvýše jednu otevřenou na
recenzi (DB index). U veřejných recenzí se vrací `disputeStatus`
(`null` / `open` / `upheld` / `rejected`).

## Chat — `/chat`

| Metoda | Cesta | Popis |
|---|---|---|
| POST | `/chat/conversations` | Konverzace k inzerátu (deduplikace) |
| GET | `/chat/conversations` | Konverzace přihlášeného |
| POST | `/chat/messages` | `{conversationId, bodyText}` — max 8 KB |
| GET | `/chat/conversations/{id}/messages` | Zprávy (nejnovější první) |
| GET | `/chat/interest?listingId=` | Počet zájemců (veřejné) |

Konverzace je jednoznačná pro dvojici `(buyer, seller, listing)` — UNIQUE
v databázi, takže opakované „Mám zájem“ nevytvoří duplicity.

## Moderace a geo

| Metoda | Cesta | Popis |
|---|---|---|
| POST | `/moderation/reports` | `{targetType, targetId, reason}` |
| GET | `/geo/zip?zip=` | PSČ → souřadnice (cache v serveru) |
| GET | `/geo/reverse?lat=&lon=` | Souřadnice → místo (reverzní geokód) |

`targetType` je `listing`, `review` nebo `chat`. Důvěryhodnost hlásiče
(`trusted_flagger`) rozhoduje server — administrátor nebo uživatel
s nejméně třemi potvrzenými hlášeními; klient ji nemůže ovlivnit.

## Administrace — `/admin`

Celá skupina vyžaduje roli `ADMIN`.

### Moderace

| Metoda | Cesta | Popis |
|---|---|---|
| GET | `/admin/moderation/queue?limit=50` | Otevřená hlášení (samotné čtení se audituje) |
| POST | `/admin/moderation/{id}/approve` | Ponechat obsah |
| POST | `/admin/moderation/{id}/remove` | Odstranit (inzerát → `removed`, recenze → skrytá) |

### Námitky na hodnocení

| Metoda | Cesta | Popis |
|---|---|---|
| GET | `/admin/disputes?limit=50` | Otevřené námitky + `openCount` |
| POST | `/admin/disputes/{id}/resolve` | `{decision: "uphold"\|"reject", note?}` |

`uphold` = námitka oprávněná → recenze se odstraní a nepočítá se do hodnocení.
`reject` = recenze zůstává v platnosti. Obě rozhodnutí jdou do auditu.

### Ověřování obchodníků

| Metoda | Cesta | Popis |
|---|---|---|
| GET | `/admin/verifications` | Obchodníci s dokladem čekajícím na posouzení |
| POST | `/admin/verifications/{userId}/approve` | Potvrdit doklad → odznak „Ověřený obchodník“ |
| POST | `/admin/verifications/{userId}/reject` | Odmítnout (s poznámkou pro prodejce) |

Rozhodnutí lze vydat jen jednou; po nahrání nového dokladu je nutné nové.

### Uživatelé a audit

| Metoda | Cesta | Popis |
|---|---|---|
| GET | `/admin/users?q=&limit=50` | Výpis uživatelů s fulltextovým filtrem |
| POST | `/admin/users/{id}/block` | Blokace účtu (odmítne i přihlášení i refresh) |
| POST | `/admin/users/{id}/unblock` | Odblokování |
| GET | `/admin/audit?limit=50&action=` | Audit log (čtení se samo zaloguje) |

### Daňové výkazy (DAC7)

| Metoda | Cesta | Popis |
|---|---|---|
| GET | `/admin/dac7/summary?year=` | Souhrn za rok v JSON (pro dashboard) |
| GET/POST | `/admin/dac7/export?year=&format=csv\|xml&detail=false` | Výkaz k stažení |

`detail=false` dává agregaci po prodejcích, `detail=true` jednotlivé prodeje.
Výkaz se generuje automaticky každý **5. ledna ve 3:00**; vyhotovení je v auditu.

Reportovatelný prodejce: **30 a více prodejů** nebo **50 000 Kč a více** obratu
v daném roce (orientační práh 2 000 €). CSV odděluje sloupce středníkem a
escapuje ho; XML escapuje značky.

## Veřejné endpointy

Bez přihlášení lze volat:

```
GET /health, /actuator/health, /actuator/info
POST /auth/**
GET  /listings/search, /listings/feed, /listings/{id},
     /listings/{id}/rights-info, /listings/{id}/images/{imgId}
GET  /users/{id}, /users/{id}/badges, /users/{id}/rating, /users/{id}/reviews
GET  /geo/**, /chat/interest
```

## Limity požadavků

Klouzavé okno 60 s, hodnoty jdou překonfigurovat přes `app.rate-limit.*`
(v testech vypnuto přes `RATE_LIMIT_ENABLED=false`):

| Operace | Limit |
|---|---|
| Registrace (na IP) | 5 / min |
| Přihlášení (na IP) | 10 / min |
| Zprávy v chatu (uživatel) | 30 / min |
| Hlášení (uživatel) | 10 / min |
| Rezervace (uživatel) | 20 / min |