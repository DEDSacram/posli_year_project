# Webová aplikace (React + TypeScript + Vite + TailwindCSS)

Běží jako statický build za nginx, REST API volá přes `VITE_API_URL`.

## Příkazy

```bash
npm install        # instalace závislostí
npm run dev        # vývojový server na :5173 (proxy na API z .env)
npm run typecheck  # kontrola typů (tsc, bez emitování)
npm run build      # typecheck + produkční build do dist/
npm run lint       # oxlint
npm run preview    # náhled produkčního buildu
```

## Struktura

| Cesta | Obsah |
|---|---|
| `src/types.ts` | Typy odpovědí REST API (odraz backendu v `backend/`) |
| `src/api.ts` | Jediný HTTP klient — autorizace, obnova tokenu, české chyby |
| `src/auth.tsx` | `AuthProvider` / `useAuth` — relace a přihlášení |
| `src/alerts.tsx` | `AlertsProvider` / `useAlerts` — zlevnění a nepřečtené chaty |
| `src/components/` | UI: karty, formulářová pole, mapa, chat, widgety |
| `src/pages/` | Stránky odpovídající cestám v `src/App.tsx` |

## Konvence

- Cesty jsou české (`/inzerat/:id`, `/obchody`, `/admin/dac7`), backend vrací anglické klíče.
- Chyby API přicházejí jako `{code, messageCs}` — na obrazovku jde `messageCs` (`ApiError`).
- Stránkování vždy `{data, nextCursor}`, limit max 50.
- Souřadnice zůstávají uvnitř filtrů; uživatel vidí jen PSČ, místo a okruh v km.