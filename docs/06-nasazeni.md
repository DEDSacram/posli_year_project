# 6 — Nasazení

Cílová sestava běží v Docker Compose: PostgreSQL (ParadeDB), backend Spring Boot
a web za nginx. Volitelně se přidá Cloudflare tunel pro veřejný přístup.

## Služby

| Služba | Obraz | Port (host) | Health check |
|---|---|---|---|
| `db` | `paradedb/paradedb:latest` | 5433 | `pg_isready` |
| `backend` | build z `backend/` | 8081 | `GET /health` obsahuje `UP` |
| `frontend` | build z `frontend/` | 3000 | hlavní stránka odpovídá |
| `cloudflared` | `cloudflare/cloudflared:latest` | — | — |

Všechny tři hlavní služby mají health check a `depends_on … condition:
service_healthy`, takže backend startuje až po migracích a frontend až po
zprovoznění API.

## Konfigurace

Všechno se nastavuje přes `.env` (šablona v `.env.example`):

| Proměnná | Výchozí | Popis |
|---|---|---|
| `POSTGRES_USER` / `POSTGRES_PASSWORD` / `POSTGRES_DB` | `bazar` | Přihlášení do DB |
| `POSTGRES_PORT` | `5433` | Hostitelský port DB |
| `BACKEND_PORT` | `8081` | Hostitelský port API |
| `FRONTEND_PORT` | `3000` | Hostitelský port webu |
| `VITE_API_URL` | `http://localhost:8081` | Adresa API **zkompilovaná do buildu webu** |
| `JWT_SECRET` | výchozí jen pro vývoj | ≥ 32 bajtů, používá se k podpisu tokenů |
| `TAX_ENCRYPTION_KEY` | prázdný | base64 32 bajtů — **bez něj backend nefunguje** |
| `ADMIN_EMAIL` | `admin@bazar.cz` | E-mail s rolí administrátora |
| `CORS_ORIGINS` | `http://localhost:3000` | Povolené originy (oddělené čárkou) |
| `RATE_LIMIT_ENABLED` | `true` | Vypnutí limitů (jen testy) |
| `RATE_LIMIT_*` | dle tabulky v `02-api.md` | Limity na zápisy |

### Produkční hodnoty

```bash
openssl rand -hex 32      # → JWT_SECRET
openssl rand -base64 32   # → TAX_ENCRYPTION_KEY
```

`TAX_ENCRYPTION_KEY` je jediná hodnota, bez níž aplikace odmítne start
(fail-fast) — je to záruka, že se citlivé údaje neukládají nezašifrované.

> **Změna klíče za běhu:** `AesGcmService` umí dešifrovat i starým
> vývojovým klíčem, ale obecně je bezpečné změnu naplánovat s převodem dat.

## První spuštění

```bash
cp .env.example .env
$EDITOR .env                    # doplňte JWT_SECRET a TAX_ENCRYPTION_KEY
docker compose up --build -d
docker compose ps                # čekejte na „healthy“

curl http://localhost:8081/health
# {"status":"UP","db":"UP"}
curl -o /dev/null -w "%{http_code}\n" http://localhost:3000/
# 200
```

Migrace (Liquibase) se spustí automaticky při startu backendu. Kontrola:

```bash
docker compose exec db psql -U bazar -d bazar -c "select id from databasechangelog;"
```

## Administrátor

První administrátor vznikne při registraci e-mailu z `ADMIN_EMAIL`:

```bash
curl -X POST http://localhost:8081/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@bazar.cz","password":"silne-heslo-123","displayName":"Administrátor","sellerType":"private"}'
```

Pak se přihlaste a otevřete `http://localhost:3000/admin`.

Kromě e-mailu z `ADMIN_EMAIL` lze administrátora kdykoli povýšit přímo
v databázi (užitečné, pokud e-mail už obsadí někdo jiný):

```sql
UPDATE users SET is_admin = TRUE WHERE email = 'vas@email.cz';
```

## Vývoj

### Web

```bash
cd frontend
npm install
npm run dev          # http://localhost:5173
```

Vite načítá API z `VITE_API_URL` (`.env` v kořeni repozitáře).

### Backend

```bash
./backend/mvnw.sh -DskipTests compile        # jen kompilace
./backend/mvnw.sh spring-boot:run            # lokální běh
```

Bez běžícího Postgres v lokálním prostředí nepomůže — buď nechte Compose
spuštěný a směřujte datasource na `localhost:5433`, nebo spusťte celý
stack v Dockeru.

### Android

```bash
cd android
./gradlew :app:installDebug        # na připojené zařízení
./gradlew :app:testDebugUnitTest    # testy
```

Adresa API je defaultně `http://10.0.2.2:8081` (hostitel z pohledu
emulátoru); na fyzickém telefonu ji přepište v aplikaci.

## Cloudflare tunel (volitelné)

Sekce `cloudflared` v `docker-compose.yml` vystavuje web i API přes tunel.
Potřebuje:

1. přihlášení přes `cloudflared tunnel login`,
2. vytvoření tunelu a jeho ID,
3. konfigurace v `cloudflared.yml` (hostname, cesta k JSON credentials).

```bash
docker compose logs cloudflared
```

> Tunel vede veřejný provoz na **http**. Pro produkci ukončete TLS na
> reverzním proxy před tunelem a nastavte `CORS_ORIGINS` na `https` origin.

## Zálohy a obnova

```bash
# Záloha
docker compose exec -T db pg_dump -U bazar -d bazar > bazar-$(date +%F).sql

# Obnova
docker compose exec -T db psql -U bazar -d bazar < bazar-2026-10-05.sql
```

Zálohujte **i klíč `TAX_ENCRYPTION_KEY`** — bez něj zůstanou daňové údaje
v databázi nečitelné.

## Řešení problémů

| Projev | Příčina a řešení |
|---|---|
| Backend nenastartuje, hlásí chybějící `TAX_ENCRYPTION_KEY` | Doplňte proměnnou v `.env` |
| `/health` vrací `db: DOWN` | DB se ještě migruje — chvíli počkejte a zkontrolujte `docker compose logs db` |
| Web volá API, ale CORS hlásí chybu | Nastavte `CORS_ORIGINS` na skutečný origin webu |
| Přihlášení vrací 401 po chvíli | Access token platí 15 min; klient ho sám obnoví. Při trvalém problému zkontrolujte shodu `JWT_SECRET` mezi restarty |
| Frontend 404 na podstránkách | nginx vrací `index.html` (`try_files`) — viz `frontend/nginx.conf` |
| Změna `VITE_API_URL` se neprojeví | Proměnná se zapisuje do buildu; nutný `docker compose build frontend` |
| Registrace hlásí limit | Zapnutý rate limit 5/min na IP — normální chování, chvíli počkejte |
| `docker compose up` stahuje obraz znovu | Řešení je v `.env` (např. prázdné heslo k DB) |
| Gradle: nepodporovaná verze Javy | Gradle 8.10 potřebuje JDK 17 |