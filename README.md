# PosliToDal — C2C bazar

Webová a mobilní aplikace pro prodej použitého zboží mezi lidmi. Jedno REST API
obsluhuje web i Android klienta. Systém rozlišuje role **prodejce**, **kupce**
a **administrátora** a je navržen tak, aby šel provozovat v souladu s českou
a evropskou legislativou (spotřebitelská práva, ověřování obchodníků, daňové
výkazy DAC7, GDPR).

## Obsah repozitáře

| Cesta | Obsah |
|---|---|
| `backend/` | Spring Boot 3 REST API (Java 17), PostgreSQL + Liquibase |
| `frontend/` | Webový klient (React 19 + TypeScript + Vite + TailwindCSS, Leaflet) |
| `android/` | Nativní Android klient (Kotlin + Retrofit) |
| `design/` | Návrhové mockupy a logo |
| `docs/` | Dokumentace — architektura, API, databáze, testy, **uživatelská příručka** |
| `TODO/` | Pracovní podklady k zadání a průběžnému stavu projektu |
| `docker-compose.yml` | Nasazení celé soustavy včetně health checků |

## Rychlé spuštění

```bash
cp .env.example .env
# produkční hodnoty:
#   JWT_SECRET            – nejméně 32 bajtů (např. openssl rand -hex 32)
#   TAX_ENCRYPTION_KEY    – base64 32 náhodných bajtů (openssl rand -base64 32)
#   ADMIN_EMAIL           – e-mail, který má roli administrátora
docker compose up --build -d
docker compose ps                      # všechny služby healthy
curl http://localhost:8081/health      # {"status":"UP","db":"UP"}
```

Web běží na <http://localhost:3000>, API na <http://localhost:8081>.

## Funkce

**Katalog** — vystavení inzerátu s až 10 fotografiemi, fulltextové hledávání
(BM25 i část slova), filtry podle kategorie, ceny a polohy (okruh v km), mapa
Leaflet, chytré doplňování formuláře a náhled před publikací.

**Komunikace a rezervace** — rezervace jedním kliknutím, chat 1:1 navázaný na
inzerát, nabídka vlastní ceny, upozornění na zlevnění uložených inzerátů.

**Hodnocení** — hodnocení prodejce po dokončení obchodu („ověřený nákup“),
veřejná odpověď prodejce, námitka proti neférovému hodnocení a odznaky
odvozené z reálné historie obchodů.

**Administrace a legislativu** — moderační fronta, námitky, ověřování
obchodníků, audit log, daňové výkazy DAC7 (CSV/XML), export a smazání dat
(GDPR) a právo spotřebitele na odstoupení od smlouvy.

## Testy

```bash
./backend/mvnw.sh test     # 85 unit testů (JUnit 5 + Mockito)
./backend/mvnw.sh verify   # + 61 integračních testů (Testcontainers/PostgreSQL)
cd frontend && npm run build   # kontrola typů + produkční build
cd android && ./gradlew :app:testDebugUnitTest   # 38 unit testů
```

Podrobnosti: [`docs/04-testy.md`](docs/04-testy.md).

## Dokumentace

| Dokument | Obsah |
|---|---|
| [`docs/01-architektura.md`](docs/01-architektura.md) | Komponenty, datový tok, bezpečnost, role |
| [`docs/02-api.md`](docs/02-api.md) | Reference REST API (68 endpointů) |
| [`docs/03-databaze.md`](docs/03-databaze.md) | Schéma, indexy, migrace |
| [`docs/04-testy.md`](docs/04-testy.md) | Testovací strategie a co je pokryto |
| [`docs/05-uzivatelska-prirucka.md`](docs/05-uzivatelska-prirucka.md) | **Uživatelská příručka** (CZ) |
| [`docs/06-nasazeni.md`](docs/06-nasazeni.md) | Nasazení, konfigurace, health checky |
| [`docs/07-narizeni-a-shoda.md`](docs/07-narizeni-a-shoda.md) | Soulad s legislativou |