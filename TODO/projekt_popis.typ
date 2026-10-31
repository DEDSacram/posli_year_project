#set document(title: "PosliToDal — Popis projektu", author: "Student")
#set page(paper: "a4", margin: (x: 1cm, y: 1cm))
#set text(font: "Noto Serif", size: 10.5pt, lang: "cs")
#set heading(numbering: "1.1")
#show heading: set text(font: "Noto Sans")
#set par(justify: true, leading: 0.6em)
#show raw: set text(font: "DejaVu Sans Mono")
#show raw.where(block: true): set text(size: 8.5pt)
#show table: set text(font: "Noto Sans", size: 9pt)

= Cíl
Cílem práce je navrhnout a implementovat webovou a mobilní aplikaci, která bude
sloužit jako C2C bazar použitého zboží pro legální provoz v ČR. Aplikace umožní
správu inzerátů, komunikaci mezi prodejci a kupci přes chat, rezervace a
dokončování obchodů a hodnocení prodejců. Systém bude rozdělen podle rolí
(admin, prodejce, kupec) a nabídne každému aktérovi specifické funkce. Součástí
je soulad s DAC7 (report prodejních dat), DSA (ověření obchodníků, moderace) a
GDPR (souhlasy, export, soft-delete).

= Aktéři
1. Admin
- Dozor nad fungováním systému: moderační fronta hlášení, bezodkladný takedown
  (`approve | remove`) s auditem.
- Rozhoduje námitky prodejce proti hodnocení (`uphold | reject`) a doklady
  obchodníků (`approve | reject`) s poznámkou pro prodejce.
- Výpis uživatelů, blokace účtů, čtení audit logu.
- DAC7 souhrn i export prodejních dat za rok (CSV/XML, agregace i detail).
2. Prodejce (soukromník | obchodník)
- Evidence a správa inzerátů (přidávání, editace, mazání, fotky s pořadím).
- Obchodník: IČO + kopie dokladu + čestné prohlášení (DSA), odznak „Ověřený
  obchodník" po schválení administrátorem, 14denní právo odstoupení
  u spotřebitelů.
- Přístup k recenzím a hodnocení ze strany kupců + jedna veřejná odpověď.
- Námitka proti neférovému hodnocení (jedna otevřená na recenzi).
- Dokončování a rušení transakcí; přehled obchodů a získaného hodnocení.
- Odznaky odvozené z historie: důvěryhodnost, vysoké hodnocení, zkušenost,
   čistý rekord.
3. Kupec (přihlášený) a host
- Prohlížení a vyhledávání inzerátů (i část slova, kategorie, cena, PSČ-okruh).
- Rezervace inzerátu, chat s prodejcem, nabídka vlastní ceny.
- Přidávání recenzí a hvězdičkového hodnocení po dokončeném nákupu.
- Oblíbené inzeráty na profilu včetně upozornění na zlevnění (zvoník).
- Uplatnění práva odstoupení u nákupu od obchodníka (14 dní).
- Host: prohlížení a hledání bez účtu; nákup a prodej až po registraci.

= Funkcionality aplikace
1. Evidence inzerátů
- Prodejce vkládá inzeráty s podrobným popisem (název, popis, cena, kategorie,
  stav, město + GPS se souhlasem, až 10 fotografií s volbou pořadí, EXIF strip).
- Filtrování a vyhledávání podle textu (i část slova), kategorie, ceny, lokality
  (PSČ + okruh) a řazení (relevance, novost, cena).
- Detail inzerátu: galerie, mapa polohy, prodejce s hodnocením, spotřebitelské
  info (právo odstoupení), sdílení.
- Návrh kategorie a mediánu ceny, duplikace inzerátu, autosave konceptu.
2. Recenze prodejců
- Kupci hodnotí prodejce po dokončeném nákupu (rychlost, férovost — text +
  hvězdičky 1–5, jedna recenze na transakci, „Ověřený nákup").
- Prodejce může jednou veřejně odpovědět.
- Prodejce může podat *námitku* proti hodnocení; rozhodne administrátor
  (`uphold` → recenze se odstraní a nepočítá se do hodnocení,
  `reject` → recenze zůstává).
- Agregované hodnocení a odznaky z historie jsou viditelné u jména prodejce
  v inzerátech i na profilu.
3. Rezervace a chat
- Kupec rezervuje inzerát (Koupit nyní) — prodejci přijde zpráva s číslem
  rezervace; Mám zájem / nabídka ceny zakládá konverzaci s předvyplněnou zprávou.
- Obě strany transakci dokončí nebo zruší; dokončení se počítá do DAC7.
- Konverzace 1:1 na inzerát (dedup), zprávy do 8 KB v plaintextu pro moderaci.
- Přehled obchodů (filtr nákupy/prodeje) se souhrnem prodejů, obratem
  a získaným hodnocením; u nákupu od obchodníka tlačítko odstoupení
  s datem konce 14denní lhůty.
4. Upozornění
- Snížení ceny uloženého inzerátu vyvolá notifikaci v profilu (zvoník
  v hlavičce, samostatná obrazovka v aplikaci); duplicitám brání DB index.

= Technické požadavky
1. Front-end (React + TypeScript)
- Moderní, responzivní rozhraní pro desktop i mobil: feed, hledání, detail,
  prodejní formulář, chat, profily, účet, oblíbené, obchody, administrace.
- TypeScript ve `strict` režimu; `tsc -b` je součástí buildu, takže chyba
  typu zastaví build. Typy odpovědí API v `src/types.ts`.
- Interaktivní komponenty (formuláře s validací vč. IBAN mod-97, mapy Leaflet
  pro polohu a PSČ, galerie s lightboxem), dělení kódu na routy (`React.lazy`).
2. Mobil (Android, Kotlin)
- Nativní aplikace zrcadlící web (Retrofit 1:1 k API): feed, hledání, detail,
  prodej s fotkami, chat, profily, účet, oblíbené, přehled obchodů,
  upozornění na zlevnění, admin.
3. Back-end (Java + Spring Boot)
- REST API (controller → service → repository, Bean Validation, Liquibase V1–V10).
- Role (admin, trader, private) a oprávnění (vlastnictví, účast v konverzaci,
  `hasRole("ADMIN")`); publikace až s platným DAC7 profilem (`422`).
- Transakce se snapshotem ceny a typu prodejce, recenze s námitkami,
  upozornění na zlevnění, reporty, souhlasy, audit, blokace účtů.
- Bezpečnost: JWT access 15 min + refresh 30 dní, bcrypt(12), AES-GCM daňový
  profil, rate-limity zapisů (konfigurovatelné), CSP, CORS, 401/403 rozlišené.
4. Databáze
- PostgreSQL (ParadeDB): `pg_search` (BM25 fulltext) + `earthdistance` (okruh),
  `citext`; `users`, `seller_tax_profile`, `listings`, `listing_images`,
  `transactions`, `reviews`, `review_disputes`, `conversations`, `messages`,
  `reports`, `favorites`, `notifications`, `consents`, `audit_log`.
5. Docker
- Tři služby na síti `bazar-net`: `db` (:5433), `backend` (:8081),
  `frontend` (:3000); healthchecky, restarty, `docker compose up --build -d`.
6. Testování (Unit testy a integrační testy)
- *Backend:* 85 unit testů (JUnit 5, Mockito, AssertJ) + 61 integračních
  testů přes HTTP proti skutečnému PostgreSQL v Testcontainers. Pokrytí
  řádků 74,8 % (JaCoCo report v `backend/target/site/jacoco`). Testy
  pokrývají oprávnění, vlastnictví, stavy transakcí, lhůtu odstoupení,
  prah reportovatelnosti DAC7 i daňové šifrování.
  Spuštění: `./backend/mvnw.sh test` (unit) a `./backend/mvnw.sh verify`
  (unit + integrace; potřebuje běžící Docker).
- *Android:* 38 unit testů včetně smluvního testu Retrofit API proti
  MockWebServeru (`./gradlew :app:testDebugUnitTest`).
- *Web:* `npm run build` zahrnuje `tsc -b`, takže chyba typu zastaví build;
  `src/types.ts` je typový odraz API.
- E2E testy přes UI záměrně nejsou — viz `docs/04-testy.md`, proč.

= Očekávaný výstup
- Funkční webová a mobilní aplikace s rolemi admin / prodejce / kupec (host).
- Modul správy inzerátů: evidence, úpravy, vyhledávání s geofiltem.
- Rezervační a komunikační systém (rezervace + chat + nabídky cen).
- Recenze prodejců s veřejným hodnocením a odpověďmi.
- Nasazení v Dockeru (React front-end, Spring Boot back-end, databáze).
- Dokumentace: návrh (`yearpro_doc.typ`), plnění (`implementace_prehlad.typ`),
  tento popis; testovací účty `demo@prodej.cz` / `kup@x.cz` / `admin@bazar.cz`
  (heslo `Heslo1234`).

= Hodnotící kritéria
1. Splnění funkčních požadavků — vše kromě integrace kalendáře (není v produktu).
2. Kvalita a čistota kódu — vrstvená architektura, validace, sdílené komponenty.
3. Bezpečnost — JWT, hashování, šifrování PII, autorizace, audit, rate-limity.
4. Uživatelská přívětivost a responzivní design — web + hamburger menu + nativní app.
5. Docker — oddělené služby, funkční `compose` nasazení.
6. Testování — *splněno* (85 unit + 61 integračních backendových,
   38 unit Android; pokrytí řádků backendu 74,8 %).
7. Dokumentace — technická v `docs/` (architektura, reference API, databáze,
   testy, nasazení, shoda s legislativou) a uživatelská příručka
   `docs/05-uzivatelska-prirucka.md` (web i aplikace krok za krokem).
