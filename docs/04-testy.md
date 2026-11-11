# 4 — Testy

Cílem je pokrýt pravidla, která rozhodují o bezpečnosti obchodu: oprávnění,
vlastnictví, stavy transakcí a legislativní hranice. Každý balíček má testy
odvozené z obchodního pravidla, ne ze struktur kódu.

## Backend

```bash
./backend/mvnw.sh test      # 85 unit testů (rychlé, bez kontejnerů)
./backend/mvnw.sh verify    #  + 61 integračních testů (Testcontainers)
```

Skript `backend/mvnw.sh` spouští Maven v kontejneru (lokálně nemusí být
nainstalovaný). `verify` navíc vyžaduje běžící Docker — Testcontainers v něm
zvedne PostgreSQL.

| Cíl | Testy |
|---|---|
| `test` | `**/*Test.java` — unit testy (JUnit 5, Mockito, AssertJ) |
| `verify` | navíc `**/*IT.java` — integrační testy přes HTTP |

### Unit testy (85)

| Třída | Co ověřuje |
|---|---|
| `AesGcmServiceTest` | Roundtrip šifrování, odmítnutí pozměněných dat, chybějící klíč fail-fast |
| `NotificationServiceTest` | Upozornění vzniká jen při poklesu, ne pro prodejce, duplikáty se přeskakují, stránkování |
| `DisputeServiceTest` | Námitku podá jen hodnocený, jedna otevřená, `uphold` odstraní recenzi, `reject` ji nechá |
| `TransactionServiceTest` | Odstoupení: jen kupující, jen obchodník, do 14 dnů; stavy `reserved→completed/cancelled`; idempotence rezervace; přehled obchodů; filtrování podle role |
| `UserServiceTest` | Prahové podmínky odznaků (3 a 5 hodnocení, 10 prodejů), potvrzená námitka zruší důvěryhodnost |
| `Dac7ServiceTest` | Práh reportovatelnosti (30 prodejů / 50 000 Kč), agregace, oddělení CSV a XML, escapování, nezalomený výkaz při nečitelném profilu |
| `SellerServiceTest` | Ověření dokladu: schválení, zamítnutí, podmínky (neobchodník, bez dokladu, druhé rozhodnutí), nahrání dokladu vrátí stav „čeká“ |

### Integrační testy (61)

Všechny běží proti skutečnému PostgreSQL v kontejneru (obraz ParadeDB, aby
byly dostupné i `pg_search` a `earthdistance`) a celou aplikaci přes HTTP —
tedy se projeví i chyby v serializaci, validaci a filtraci přístupu.

| Třída | Pokryté scénáře |
|---|---|
| `CoreApiIT` | Registrace (hash hesla, konflikt e-mailu), přihlášení, obnova tokenu, vlastnictví inzerátu, geo validace, fulltextové hledání, filtrování, chat, moderace, GDPR export a soft-delete |
| `PriceDropNotificationIT` | Snížení ceny vyvolá upozornění, ne pro prodejce, deduplikace, označení jako přečtené, izolace cizích upozornění, badge zlevnění |
| `ReviewDisputeIT` | Námitka → fronta → rozhodnutí, skrytí recenze, zákaz druhé námitky, oprávnění, dopad na odznaky |
| `TraderVerificationIT` | Fronta dokladů, schválení a zamítnutí s poznámen, nemožnost rozhodnout dvakrát, nahrání dokladu vrátí „čeká“, zákaz ověřit neobchodníka, zákaz pro běžného uživatele |
| `TransactionLifecycleIT` | Rezervace → dokončení → hodnocení, idempotence, jen kupující hodnotí, odstoupení práv a lhůt, přehled obchodů, filtrování rolí, spotřebitelská práva u inzerátu |
| `AdminGovernanceIT` | Audit log (včetně záznamu o vlastním čtení), filtrování podle akce, výpis uživatelů, blokace znemožní přihlášení, ochrana administrátora a sebe sama, souhrn a export DAC7 |

Každý IT běží v izolované PostgreSQL a stavy se mezi testy **neprodlužují**
(kontejner se zvedá jednou pro celý JVM). Testy, které potřebují vlastní
výsledky, používají unikátní názvy a filtry (`?category=Geo test 9911`),
aby výsledky nezkalila data sousedních tříd.

### Pokrytí kódu

JaCoCo (report v `backend/target/site/jacoco/index.html` po `verify`):

```
Řádky: 1531 / 2047 = 74,8 %
```

| Package | Pokrytí |
|---|---|
| `config` (bezpečnost, CORS) | 100 % |
| `domain` (entity, accessory) | 90 % |
| `crypto` (AES-GCM) | 87 % |
| `exception` | 89 % |
| `controller` | 80 % |
| `dto` | 83 % |
| `service` (obchodní logika) | 71 % |
| `security` (JWT, rate limit) | 69 % |
| `scheduler` (roční cron) | 29 % |

Nepokryté jsou hlavně triviální accessory, cron a odvozené převody
entit — testy cíleně na pravidla, ne na každý setter.

## Frontend

Kontrola typů je součástí buildu, takže chyba v kontraktu s API build zastaví:

```bash
cd frontend
npm run typecheck   # tsc, bez emitování
npm run build       # typecheck + produkční build
npm run lint        # oxlint
```

`tsconfig` používá `strict`, `noUnusedLocals`, `noUnusedParameters`
a `verbatimModuleSyntax`. Typy odpovědí API jsou v `src/types.ts` jako odraz
serveru, takže změna kontraktu na backendu se projeví chybou překladu.

## Android

```bash
cd android
./gradlew :app:testDebugUnitTest   # 38 testů
./gradlew :app:assembleDebug
```

Vyžaduje JDK 17 (Gradle 8.10 nepodporuje JDK 21+).

| Třída | Co ověřuje |
|---|---|
| `JTest` | Odolné čtení JSON: chybějící klíč, `null`, nestavový objekt → nikdy výjimka; české formátování ceny |
| `ValidateTest` | Stejná pravidla jako backend: e-mail, heslo, délky názvu a popisu, celočíselná kladná cena, PSČ, IBAN včetně kontrolního součtu mod-97 |
| `BazarApiTest` | Kontrakt s API přes MockWebServer: cesty, metody, parsování odpovědí, předání parametrů hledání, chybové stavy 422 a 403 |

Běží na JVM — nepotřebují emulátor.

## Co záměrně netestujeme

- **E2E testy přes UI.** Ověření plného toku (kliknutí v prohlížeči, emulátor)
  je křehké a pomalé; totéž chování kryjí integrační testy backendu přes
  HTTP a testy kontraktu v klientech.
- **Vizuální layout.** Ručně na emulátoru a v prohlížeči.
- **Cron pro DAC7** — spouští se ročně; jeho logika je pokryta testy
  `Dac7ServiceTest`, takže se testuje logika, ne plánovač.

## Jak přidat test

1. **Backend unit** — třída `*Test` v `src/test/java/cz/bazar/service/`, mocky
   repository, metoda `ok(...)`/`ne(...)` podle názvu testu.
2. **Backend integrační** — třída `*IT` v `src/test/java/cz/bazar/it/` dědí z
   `AbstractIntegrationTest`, volá API přes `get/post/...` helpery.
3. **Android** — `src/test/java/cz/bazar/android/util/` nebo `api/`.