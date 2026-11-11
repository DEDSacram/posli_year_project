# 1 — Architektura

## Přehled

Aplikace tvoří tři komponenty a jedna databáze:

```
                    ┌──────────────┐        ┌──────────────┐
   prohlížeč ──────▶│   frontend   │        │    android   │◀──── telefon
                    │ React + TS   │        │ Kotlin       │
                    │ nginx (dist) │        │ Retrofit     │
                    └──────┬───────┘        └──────┬───────┘
                           │  HTTPS / JSON         │
                           └───────────┬────────────┘
                                       ▼
                            ┌─────────────────────┐
                            │      backend        │
                            │ Spring Boot 3 (Java)│
                            └──────────┬──────────┘
                                       ▼
                            ┌─────────────────────┐
                            │   PostgreSQL 16     │
                            │  ParadeDB image     │
                            │  BM25 + earthdist.  │
                            └─────────────────────┘
```

Web i mobil volají **stejné** REST API — je to jediný zdroj pravdy pro data
i pravidla. Rozhodnutí (vlastnictví, role, reportovatelnost, lhůty) se
vyhodnocují výhradně na serveru; klienty je jen zobrazují.

## Backend

Package `cz.bazar`, Java 17, Spring Boot 3.2.5.

### Vrstvy

```
controller/  HTTP, validace vstupu (@Valid), mapování na odpovědi
service/     obchodní logika, vlastnictví, role, výpočty
repository/  Spring Data JPA (+ @Query pro agregace a geo dotazy)
domain/      JPA entity (14) mapující tabulky
dto/         záznamy pro vstup/výstup s Bean Validation
security/    JWT, filtry, rate limiting
crypto/      AES-GCM pro daňový profil
config/      Spring Security, CORS, properties
scheduler/   roční výpočet DAC7
```

### Klíčová rozhodnutí

- **Žádné ORM vazby mezi entitami.** Vztahy jsou cizí klíče (`Long`) a integrity
  hlídá databáze. Umožňuje to mít kontrolovaný DDL v Liquibase místo
  schématu generovaného Hibernate.
- **Schema vlastníkem je Liquibase**, `ddl-auto: validate` slouží jen jako
  pojistka proti rozdílu mezi entitou a tabulkou.
- **Každý zápisový endpoint kontroluje vlastnictví a roli** na serveru
  (`ChatService`, `ListingService`, `TransactionService`, `ReviewService`).
- **Chyby jsou jednotné**: `{code, messageCs}` — strojový kód i česká hláška,
  kterou klient zobrazí přímo.

### Bezpečnost

| Opatření | Implementace |
|---|---|
| Hash hesel | bcrypt, 12 kol (`SecurityConfig.passwordEncoder`) |
| Přihlášení | JWT access 15 min + refresh 30 dní (`JwtService`) |
| Citlivé údaje | AES-GCM 256 v `seller_tax_profile`, klíč z `TAX_ENCRYPTION_KEY` |
| Role | `ROLE_USER` + `ROLE_ADMIN` (`users.is_admin` nebo e-mail z `ADMIN_EMAIL`) |
| Blokace účtu | `users.blocked_at` — odmítnutí i při obnově tokenu |
| Ochrana API | rate limiting na zápisy, `HttpStatusEntryPoint` (401 bez tokenu) |
| Hlavičky | CSP, `Referrer-Policy: no-referrer`, `X-Content-Type-Options` |
| CORS | povolené originy z `CORS_ORIGINS`, bez wildcardu |
| SQL | výhradně vázané parametry (`:name`), žádné skládání řetězců |

Soukromí polohy: přesné souřadnice se ukládají jen když je prodejce výslovně
pošle. Veřejné profily ani výkazy DAC7 souřadnice neobsahují.

## Frontend

React 19 + TypeScript 5 (strict) + Vite 8 + TailwindCSS 4, mapa přes Leaflet.

### Struktura

- `src/api.ts` — jediný HTTP klient. Automaticky obnoví vypršelý access token
  jedním retry, jinak vyčistí relaci. Chyby převádí na `ApiError` s českou hláškou.
- `src/types.ts` — typy odpovědí API; změna tvaru na serveru se projeví chybou
  v `tsc`.
- `src/auth.tsx` / `src/alerts.tsx` — kontexty relace a oznámení.
- `src/components/` a `src/pages/` — UI a obrazovky.

Kontrola typů je součástí `npm run build` (`tsc -b`), takže chyba typu
zastaví build.

## Android

Kotlin 2 + Retrofit 2 + OkHttp, minimální rozhraní frameworku (XML layouty,
žádné knihovny na ukládání stavu).

- `api/BazarApi.kt` — 1:1 se serverem.
- `util/J.kt` — odolné čtení JSON (chybějící nebo `null` pole nikdy neshodí).
- `util/Validate.kt` — stejná pravidla validace jako web, sdílená s testy.

Adresa servera jde přepsat v aplikaci (`Více → Adresa serveru`); výchozí
`http://10.0.2.2:8081` odpovídá hostiteli z pohledu emulátoru.

## Datový tok příkladu: zlevnění inzerátu

1. Prodejce v editačním formuláři sníží cenu (`PUT /listings/{id}`).
2. `ListingService.update()` zjistí pokles a volá
   `NotificationService.notifyPriceDrop(listing, old, new)`.
3. Ta najde všechny, kdo mají inzerát v oblíbených, přeskočí prodejce
   a duplikáty (unikátní index na `(user_id, listing_id, new_price_czk)`
   pro nepřečtené) a vloží řádek do `notifications` — vše v jedné transakci.
4. Klient při dalším načtení získá `GET /notifications`, zobrazí odznak
   v hlavičce a v seznamu *Upozornění*.

Pokud by krok 3 selhal, rollback by zrušil i samotné snížení ceny — upozornění
a změna ceny jsou buď obojí, nebo nic.

## Role a oprávnění

| Akce | Host | Kupce | Prodejce | Administrátor |
|---|:--:|:--:|:--:|:--:|
| Prohlížet katalog, hledat, detail | ✓ | ✓ | ✓ | ✓ |
| Uložit do oblíbených | localně | ✓ | ✓ | ✓ |
| Vložit inzerát | — | ✓ | ✓ | ✓ |
| Rezervovat, nabídnout cenu, chat | — | ✓ | ✓ | ✓ |
| Hodnotit prodejce | — | ✓ | — | ✓ |
| Odpovědět / ohradit se proti hodnocení | — | — | ✓ | ✓ |
| Dokončit / zrušit obchod | — | účastník | účastník | ✓ |
| Odstoupit od smlouvy | — | ✓ (u obchodníka) | — | ✓ |
| Vložit daňový profil, nahrát doklad | — | ✓ | ✓ | ✓ |
| Moderační fronta, námitky, audit, DAC7 | — | — | — | ✓ |

Oprávnění se nekontrolují v klientovi — server každý požadavek ověří
(`SecurityConfig` + kontrola vlastnictví v jednotlivých službách).