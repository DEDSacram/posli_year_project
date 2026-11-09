# Android aplikace — Kotlin + Retrofit

Nativní klient stejného REST API jako web (`../backend`). Zrcadlo všech funkcí:
katalog, hledání včetně okolí, detail, prodej s fotkami, chat, profily,
přehled obchodů, upozornění na zlevnění, účet a administrace.

## Sestavení

Vyžaduje JDK 17 (Gradle 8.10 nepodporuje JDK 21+), Android SDK 34 a `local.properties`
s `sdk.dir`.

```bash
./gradlew :app:assembleDebug          # APK → app/build/outputs/apk/debug/
./gradlew :app:testDebugUnitTest      # unit testy (JUnit 4, bez emulátoru)
./gradlew :app:installDebug           # instalace na připojené zařízení
```

## Struktura

| Cesta | Obsah |
|---|---|
| `api/BazarApi.kt` | Retrofit rozhraní 1:1 k REST API |
| `api/BazarClient.kt` | OkHttp + `Authorization: Bearer …`, tokeny v `SharedPreferences` |
| `util/J.kt` | Bezpečné čtení Gson JSON, české formátování cen |
| `util/Ui.kt` | Callback vracený na UI vlákno + toast při chybě |
| `util/Validate.kt` | Kontrola vstupů před odesláním (shodná s webem) |
| `util/ImageLoader.kt` | Fotky inzerátů s paměťovou cache |
| `ui/` | Obrazovky a fragmenty |

## Poznámky k API

- `10.0.2.2` je adresa hostitelského počítače z emulátoru — výchozí adresa serveru
  jde přepsat v *Více → Adresa serveru*.
- Chyby přicházejí jako `{code, messageCs}`; `J.errMsg` zobrazí českou hlášku.
- Stránkování je kurzorové: `{data, nextCursor}`, limit max 50.
- Token se pošle jen pokud je uložený; odhlášení ho vymaže.