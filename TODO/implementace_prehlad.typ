#set document(title: "PosliToDal — Plnění návrhu + co je navíc", author: "Student")
#set page(paper: "a4", margin: (x: 1cm, y: 1cm))
#set text(font: "Noto Serif", size: 10.5pt, lang: "cs")
#set heading(numbering: "1.1")
#show heading: set text(font: "Noto Sans")
#set par(justify: true, leading: 0.6em)
#show raw: set text(font: "DejaVu Sans Mono")
#show raw.where(block: true): set text(size: 8.5pt)
#show table: set text(font: "Noto Sans", size: 9pt)

= Legenda
#table(
  columns: (auto, 1fr),
  fill: (c, r) => if r == 0 { rgb("#e8eef7") },
  table.header([*Značka*], [*Význam*]),
  [#text(fill: rgb("#0a7a2e"))[*splněno*]], [Odpovídá `yearpro_doc.typ`, ověřeno v kódu.],
  [#text(fill: rgb("#a86a00"))[*částečně*]], [Funguje, ale s odchylkou či nedodělkem (detail v poznámce).],
  [#text(fill: rgb("#b00020"))[*chybí*]], [V návrhu je, v implementaci není.],
  [#text(fill: rgb("#1a56db"))[*navíc*]], [V návrhu není, implementace to umí (rozšíření nad rámec).],
)

Stav ověřen čtením kódu (`backend/...`, `frontend/src/...`, Liquibase `V1–V5`).
Odkazy typu `Soubor.java:12` míří na řádek s důkazem.

= Uživatelé, prodejce, DAC7
#table(
  columns: (auto, 1fr, auto),
  fill: (c, r) => if r == 0 { rgb("#e8eef7") },
  table.header([*Bod návrhu*], [*Důkaz*], [*Stav*]),
  [`POST /auth/register`], [`AuthController.java:16`, `AuthService.java:29` · validace `Dtos.java:8` · bcrypt(12) · `409 EMAIL_EXISTS`], [#text(fill: rgb("#0a7a2e"))[splněno]],
  [`POST /auth/login`], [`AuthController.java:22`, `AuthService.java:46` · `{token, refreshToken}` · rate-limit 10/min/IP `LoginRateLimiter.java:12`], [#text(fill: rgb("#0a7a2e"))[splněno]],
  [`GET /users/:id`], [`UserController.java:23`, `UserService.java:29` · jen id/displayName/sellerType/createdAt, 404 u smazaných], [#text(fill: rgb("#0a7a2e"))[splněno]],
  [`GET /users/:id/badges`], [`UserController.java:26`, `UserService.java:41` · trader/private/hasDac7Profile/email/phone], [#text(fill: rgb("#0a7a2e"))[splněno]],
  [`GET /users/:id/reviews`], [`UserController.java:32`, `ReviewService.java:63` · kurzor+limit, obsahuje `responseText`], [#text(fill: rgb("#0a7a2e"))[splněno]],
  [`PATCH /users/me/seller-info`], [`UserController.java:38`, `SellerService.java:42` · šifrovaný zápis · trader bez IČO/dokladu/selfCert → `422 TRADER_PROFILE_INCOMPLETE`], [#text(fill: rgb("#0a7a2e"))[splněno]],
  [`POST /users/me/id-document`], [`UserController.java:44`, `SellerService.java:89` · multipart 5 MB · `image/*`/pdf · `verified=false`], [#text(fill: rgb("#0a7a2e"))[splněno]],
  [`GET /seller/me/dac7-status`], [`UserController.java:50`, `SellerService.java:119` · `{salesCount, totalAmountCzk, isReportable}` · práh 30 / 50 000 Kč], [#text(fill: rgb("#0a7a2e"))[splněno]],
  [Blokace publikování `422`], [`SellerService.java:170 requirePublishable` · voláno z `ListingService.java:37`], [#text(fill: rgb("#0a7a2e"))[splněno]],
  [JWT 15 min + 30 dní · bcrypt · citext], [`JwtService.java:18`, `SecurityConfig.java:34`, `User.java:11` · návrh připouští bcrypt místo Argon2], [#text(fill: rgb("#0a7a2e"))[splněno]],
  [`GET /users/me/export` · `DELETE /users/me`], [`UserController.java:61`, `GdprService.java:41` · export bez daňových dat · soft-delete + mazání oblíbených · daňový profil *retence*], [#text(fill: rgb("#0a7a2e"))[splněno]],
  [`POST /consents` · `AuditLog`], [Consents `UserController.java:70` · audit ale jen moderace + DAC7 export, ne čtení fronty], [#text(fill: rgb("#a86a00"))[částečně]],
)

= Inzeráty, vyhledávání, transakce
#table(
  columns: (auto, 1fr, auto),
  fill: (c, r) => if r == 0 { rgb("#e8eef7") },
  table.header([*Bod návrhu*], [*Důkaz*], [*Stav*]),
  [`POST /listings`], [`ListingController.java:23`, `ListingService.java:34` · snapshot typu · `422 DAC7_PROFILE_REQUIRED`], [#text(fill: rgb("#0a7a2e"))[splněno]],
  [`PUT /listings/:id`], [`ListingController.java:32` `@Valid` · `ListingService.java:60` vlastník + aktivní + validace polí i geo-páru], [#text(fill: rgb("#0a7a2e"))[splněno]],
  [`DELETE /listings/:id`], [`ListingService.java:100` soft-delete · transakce zůstávají · `get()` 404 i pro smazané], [#text(fill: rgb("#0a7a2e"))[splněno]],
  [`POST /listings/:id/images`], [multipart→`bytea`, 5 MB, MIME jpeg/png/webp `ListingService.java:126` · *EXIF strip hotový* — re-encode přes ImageIO bez metadat, neplatné soubory → 400], [#text(fill: rgb("#0a7a2e"))[splněno]],
  [`DELETE /listings/:id/images/:img`], [`ListingController.java:50`, `ListingService.java:148` · vlastník · 404/400], [#text(fill: rgb("#0a7a2e"))[splněno]],
  [`GET /listings/:id/rights-info`], [`ListingController.java:64`, `ListingService.java:116` · `{withdrawalApplies, sellerType, explanationCs}` §1829/§1837], [#text(fill: rgb("#0a7a2e"))[splněno]],
  [`GET /listings/search`], [`SearchService.java:37` · BM25 + ILIKE podřetězec · earthdistance · q≤200 · allowlist řazení · validace geo · limit ≤50 · kurzor · vázané parametry], [#text(fill: rgb("#0a7a2e"))[splněno]],
  [`GET /listings/feed`], [`ListingController.java:82`, `ListingService.java:172` · stejný tvar `{data, nextCursor}`], [#text(fill: rgb("#0a7a2e"))[splněno]],
  [`POST /transactions`], [`TransactionService.java:31` · rezervace kupujícím · snapshot ceny · *idempotentní* (vrací existující)], [#text(fill: rgb("#0a7a2e"))[splněno]],
  [`.../complete` · `.../cancel`], [`TransactionService.java:51` · `reserved→completed|cancelled` · účastník · `completed_at`], [#text(fill: rgb("#0a7a2e"))[splněno]],
  [`.../review` · `/reviews/:id/response`], [`ReviewService.java:28,50` · 1–5 · jen dokončené/jen kupec · 409 · 1 odpověď hodnoceného · `verifiedPurchase`], [#text(fill: rgb("#0a7a2e"))[splněno]],
)

= Chat, moderace, GDPR, admin
#table(
  columns: (auto, 1fr, auto),
  fill: (c, r) => if r == 0 { rgb("#e8eef7") },
  table.header([*Bod návrhu*], [*Důkaz*], [*Stav*]),
  [`POST /chat/conversations`], [`ChatController.java:19`, `ChatService.java:29` · dedup triple + UNIQUE `V1:105` · nejde vlastní], [#text(fill: rgb("#0a7a2e"))[splněno]],
  [`POST /chat/messages`], [`ChatController.java:39`, `ChatService.java:52` · plaintext (komentář TLS/moderace) · max 8 KB], [#text(fill: rgb("#0a7a2e"))[splněno]],
  [`GET /chat/.../messages`], [`ChatController.java:51`, `ChatService.java:70` · kurzor · limit ≤50 · kontrola účasti], [#text(fill: rgb("#0a7a2e"))[splněno]],
  [`POST /moderation/reports`], [Endpoint + priorita fronty dle trusted `ModerationService.java:45` · *trusted určuje server* (admin nebo ≥3 úspěšné takedowny) `ModerationService.java:29` · větev chat u takedownu no-op], [#text(fill: rgb("#0a7a2e"))[splněno]],
  [`GET /admin/moderation/queue`], [`AdminController.java:22` · `hasRole ADMIN` · *čtení fronty se auditují* (`MODERATION_QUEUE_READ`)], [#text(fill: rgb("#0a7a2e"))[splněno]],
  [`POST /admin/moderation/:id/...`], [`AdminController.java:27`, `ModerationService.java:50` · approve/remove · audit], [#text(fill: rgb("#0a7a2e"))[splněno]],
  [`POST /admin/dac7/export`], [*GET i POST* `AdminController.java:37` · CSV/XML · reprodukovatelné agregace `Dac7Service.java:29`], [#text(fill: rgb("#0a7a2e"))[splněno]],
  [Cron 1× ročně], [`Dac7Scheduler.java:23` 5. 1. 03:00 · `@EnableScheduling` · DAC7 + *souhrn moderace* (open/approved/removed + audit)], [#text(fill: rgb("#0a7a2e"))[splněno]],
  [Datový model], [`V1:2` citext/cube/earthdistance + tolerantní pg_search · tax profil bytea AES-GCM · zprávy plaintext · poloha nikdy z DAC7 `ListingService.java:46` · snapshoty typu · soft-delete + retence], [#text(fill: rgb("#0a7a2e"))[splněno]],
  [Vázané parametry · CSP · vlastnictví], [`SearchService.java:90` vše přes `setParameter` · `SecurityConfig.java:42` CSP · ownership checks v Chat/Listing services], [#text(fill: rgb("#0a7a2e"))[splněno]],
  [XSS escapování · rate-limit], [Escapování DAC7 CSV/XML + React auto-escape + CSP + `Referrer-Policy: no-referrer` · rate-limit login (10/min/IP), registrace (5/min/IP), zprávy (30/min), hlášení (10/min), rezervace (20/min) via `ApiRateLimiter`], [#text(fill: rgb("#0a7a2e"))[splněno]],
  [CORS pro web], [`SecurityConfig.java:56` origins z `CORS_ORIGINS`, credentials], [#text(fill: rgb("#0a7a2e"))[splněno]],
)

= Web (React) — stránky a komponenty
#table(
  columns: (auto, 1fr, auto),
  fill: (c, r) => if r == 0 { rgb("#e8eef7") },
  table.header([*Bod návrhu*], [*Důkaz*], [*Stav*]),
  [`/` · `/hledat` · `/inzerat/:id`], [`App.jsx:23` `Home` · `:24` `Search` · `:25` `ListingDetail` · pouze české cesty, bez `/search` aliasu], [#text(fill: rgb("#0a7a2e"))[splněno]],
  [`/chat` · `/chat/:id` · `/profil/:id`], [`App.jsx:32` `Profile` · `:33` `Chat`], [#text(fill: rgb("#0a7a2e"))[splněno]],
  [`/seller-info` · `/admin/*`], [`/seller-info` → `/ucet` · `/admin/moderation` + `/admin/dac7` → `Admin` se scroll-sekcí (`App.jsx:30`, `Admin.jsx:5`)], [#text(fill: rgb("#0a7a2e"))[splněno]],
  [Registrace/přihlášení], [`App.jsx:27` `/prihlaseni` + `/registrace` → `Auth.jsx`], [#text(fill: rgb("#0a7a2e"))[splněno]],
  [`SellerBadge` · `WithdrawalBanner` · `VerifiedReview`], [`widgets.jsx:6,20,70` · napojeno v detailu i profilu], [#text(fill: rgb("#0a7a2e"))[splněno]],
  [`RadiusFilter` · `ConsentBanner`], [`DistanceFilter` `widgets.jsx:93` (PSČ + km) · `ConsentBanner.jsx:6` + správa v `/ucet`], [#text(fill: rgb("#0a7a2e"))[splněno]],
  [Mobil (Android/Java, Retrofit)], [Nativní aplikace `android/` (Java, Retrofit 1:1, minSdk 26) — feed, hledání i částí slov, detail, prodej s fotkami, chat, profily, účet, oblíbené, admin; `app-debug.apk` sestaven a otestován na emulátoru proti živému backendu], [#text(fill: rgb("#0a7a2e"))[splněno]],
)

= Navíc nad rámec návrhu (backend)
#table(
  columns: (auto, 1fr),
  fill: (c, r) => if r == 0 { rgb("#e8eef7") },
  table.header([*Rozšíření*], [*Kde*]),
  [`Oblíbené na profilu (per-user)`], [`FavoriteController.java:17` · `GET /favorites/ids|/mine`, `POST|DELETE /favorites/:id` · `V3`, `V4 price_at_save`],
  [`Návrh kategorie + medián ceny`], [`GET /listings/suggest` · `ListingService.java:193` keyword-odhad + medián],
  [`Moje transakce pro výběr recenze`], [`GET /transactions/mine` · `TransactionController.java:22`],
  [`Počet zájemců o inzerát`], [`GET /chat/interest` (veřejné) · `ChatController.java:57`],
  [`Seznam vlastních konverzací`], [`GET /chat/conversations` · `ChatController.java:30`],
  [`PSČ → souřadnice + reverzní geokód`], [`GET /geo/zip|/reverse` · `GeoController.java:14` (Nominatim cache)],
  [`Poloha na profilu (město + PSČ)`], [`GET|PATCH /seller/me/location` · `SellerService.java:129`],
  [`Agregátní hodnocení prodejce`], [`GET /users/:id/rating` · `UserController.java:29`],
  [`Refresh token endpoint`], [`POST /auth/refresh` · `AuthController.java:28`],
  [`Hybridní vyhledávání`], [BM25 + ILIKE podřetězec (`play` → `PlayStation`) · Haversine fallback bez extenzí],
  [`Idempotentní rezervace + V5 index`], [1 rezervace na kupce+inzerát · DB unique index · dedup starých dat],
  [`Rotace šifrovacího klíče`], [`AesGcmService.java` fail-fast + legacy-decrypt fallback],
)

= Navíc nad rámec návrhu (frontend)
#table(
  columns: (auto, 1fr),
  fill: (c, r) => if r == 0 { rgb("#e8eef7") },
  table.header([*Rozšíření*], [*Kde*]),
  [Oblíbené + odznak zlevnění], [`pages/Favorites.jsx` · `/oblibene` · `priceDrop` · `FavLink` badge v hlavičce],
  [Rychlé akce na kartě], [`Mám zájem` · `Koupit nyní` s rezervační zprávou · `Nabídnout cenu` do chatu],
  [Rychlý náhled · sdílení · galerie], [`QuickView.jsx` · copy/WhatsApp/Lens · lightbox (Esc/šipky)],
  [Chytrý formulář prodeje], [`SmartFill.jsx` (Lens link-out, čtečka čárových kódů, odhad kategorie/ceny) · náhled před publikací · autosave konceptu · duplikace `?copy=` · `ImagePicker` s pořadím · paste/drag-drop],
  [Poloha ve formuláři], [`LocationPicker.jsx` (mapa/PSČ/geolokace, bez čísel) · prefill z profilu · `SaveLocationPrompt.jsx` dotaz na změnu profilu],
  [Interaktivní prvky], [mapa `ListingMap.jsx` · `SellerRating` hvězdičky · `Naposledy zobrazené` · notifikace nepřečtených + avatar menu · mobilní hamburger · validace IBAN mod-97],
  [Úprava inzerátu], [`/inzerat/:id/upravit` `EditListing.jsx` · vlastník],
)

= Doplněná implementace (2. kolo)
První kolo pokrylo návrh kromě testů a několika bodů, které v návrhu
byly jen okrajové. Následující bylo doplněno:

#table(
  columns: (auto, 1fr, auto),
  fill: (c, r) => if r == 0 { rgb("#e8eef7") },
  table.header([*Bod návrhu*], [*Důkaz*], [*Stav*]),
  [`POST /notifications`, `…/read`, `/read-all`], [`NotificationService.notifyPriceDrop` volaná z `ListingService.update` při poklesu ceny · `V6__notifications.sql` s UNIQUE indexem proti duplicitám · `NotificationController` · UI: zvoník v `Layout.tsx`, `Favorites.tsx` banner], [#text(fill: rgb("#0a7a2e"))[splněno]],
  [`POST /reviews/:id/dispute`], [`DisputeService` — jen hodnocený, jedna otevřená námitka (UNIQUE index) · admin `GET /admin/disputes` + `POST /admin/disputes/:id/resolve` (`uphold` odstraní recenzi, `reject` ponechá) · UI na detailu i profilu], [#text(fill: rgb("#0a7a2e"))[splněno]],
  [Odznaky z historie], [`UserService.badges()` — `verifiedTrader` z `seller_tax_profile.verification_status`, `trustedSeller` (≥4,5 z ≥3 a žádná potvrzená námitka), `highlyRated` (≥4,8 z ≥5), `experienced` (≥10 prodejů), `cleanRecord` · `SellerBadges` ve `widgets.tsx`], [#text(fill: rgb("#0a7a2e"))[splněno]],
  [Ověřování obchodníků (DSA)], [`SellerService.decideVerification()` + `pendingVerifications()` · `V8__trader_verification.sql` · `GET /admin/verifications`, `POST …/approve|reject` s poznámkou · nahrání dokladu vrací stav do `pending`], [#text(fill: rgb("#0a7a2e"))[splněno]],
  [Právo spotřebitele na odstoupění], [`V9__withdrawal.sql` (stav `withdrawn`) · `TransactionService.withdraw()` — jen kupující, jen obchodník, 14 dní od `completed_at` · `GET /transactions/:id/withdrawal-rights` vrací lhůtu a nárok · UI: `Deals.tsx`], [#text(fill: rgb("#0a7a2e"))[splněno]],
  [Přehled obchodů], [`GET /transactions/overview` (prodej/nákup, obrat, získané hodnocení) · `GET /transactions/mine?role=` · UI: `pages/Deals.tsx`, odkaz v hlavičce], [#text(fill: rgb("#0a7a2e"))[splněno]],
  [Daňový reporting], [`Dac7Service` — DIČ/IČO/IBAN ve výkazu, `?detail=true` (jednotlivé prodeje), `GET /admin/dac7/summary` pro dashboard, XML i CSV s escapováním], [#text(fill: rgb("#0a7a2e"))[splněno]],
  [Správa systému], [`AdminService` — `GET /admin/audit` (i čtení se audituje), `GET /admin/users?q=`, `POST /admin/users/:id/block|unblock` · `users.blocked_at` · `V10__admin_governance.sql`], [#text(fill: rgb("#0a7a2e"))[splněno]],
  [Testy], [*Backend:* 85 unit (JUnit 5 + Mockito) + 61 integračních (Testcontainers/PostgreSQL, přes HTTP), pokrytí řádků 74,8 % (JaCoCo) · *Android:* 38 unit vč. smluvního testu Retrofit proti MockWebServeru · *Web:* `tsc -b` v `npm run build`], [#text(fill: rgb("#0a7a2e"))[splněno]],
  [Jazyk a nástroje dle zadání], [*Web:* migrace na TypeScript (`strict`), `src/types.ts` jako odraz API, dělení kódu na routy · *Android:* migrace Java → Kotlin (22 souborů), `build.gradle.kts` s Kotlin pluginem · *Dokumentace:* `docs/` (architektura, API, databáze, testy, nasazení, shoda s legislativou, uživatelská příručka)], [#text(fill: rgb("#0a7a2e"))[splněno]],
)

= Shrnutí
- *Splněno:* celý návrh včetně dřívějších částečných bodů (EXIF strip, serverový trusted flagger, audit čtení fronty, DAC7 export, cron, rate-limity, nativní Android aplikace) a bodů z druhého kola: upozornění na zlevnění, námitky proti hodnocení, odznaky z historie, ověřování obchodníků, právo odstoupení, přehled obchodů, daňové výkazy a správa systému.
- *Navíc:* 12 backend + 7 frontend rozšíření z prvního kola a 9 položek z druhého kola (viz tabulka výše).
- *Testy:* 85 + 61 backend, 38 Android; kontrolní součet 0 chyb.
- *Jazyky:* Java 17 (backend), TypeScript (web), Kotlin (Android) — dle zadání.
