##  Cíl
Chceme postavit moderní C2C bazar, kde prodej použitých věcí funguje jednoduše, rychle a bezpečně. Web i mobilní aplikace propojí prodejce a kupce na jednom místě — od vystavení inzerátu přes chat a rezervaci až po hodnocení. Celý systém navrhneme tak, aby mohl být vystaven i z pohledu české a evropské legislativy (ochrana spotřebitele, ověřování obchodníků, daňový reporting a ochrana osobních údajů).

##  Aktéři
- **Prodejce** — během pár minut vystaví inzerát s fotkami, komunikuje s kupci a má přehled o svých obchodech i hodnocení.
- **Kupec** — snadno najde, co hledá, zeptá se prodejce přes chat, zarezervuje si zboží a po nákupu ohodnotí prodejce.
- **Admin** — dohlíží na bezpečný provoz, řeší hlášení a připravuje zákonné výkazy.

##  Funkcionality aplikace
1. Přehledný katalog s fotkami, chytré vyhledávání včetně hledání v okolí, mapa polohy a detail, kde je vše podstatné na jednom místě.
2. Ověřené recenze a hodnocení prodejců, odznaky pro poctivé obchodníky a možnost se proti neférovému hodnocení ohradit.
3. Rezervace na jedno kliknutí, chat s prodejcem, nabídka vlastní ceny a přehled o stavu obchodu. Oblíbené inzeráty navíc samy upozorní na zlevnění.

##  Návrh technologií a postupu
- **Webová aplikace** — React s TypeScriptem, Vite a TailwindCSS; mapy Leaflet.
- **Mobilní aplikace pro Android** — aplikace v Kotlinu, API volání přes Retrofit.
- **Serverová část** — Java se Spring Bootem, REST API společné pro web i mobil, role a práva uživatelů, bezpečné přihlašování (JWT), hashování hesel a šifrování citlivých údajů.
- **Databáze** — PostgreSQL s fulltextovým vyhledáváním a vyhledáváním podle polohy.
- **Nasazení** — Docker Compose (databáze + server + webový klient), healthchecky jednotlivých služeb.

##  Očekávaný výstup
- Funkční webová a mobilní aplikace propojující prodejce a kupce.
- Katalog inzerátů s vyhledáváním, mapou a přehlednými detaily.
- Chat a rezervační systém, díky kterému se obchod dotáhne do konce.
- Systém recenzí budující důvěru mezi uživateli.
- Aplikace připravená na nasazení včetně legislativních náležitostí (spotřebitelská práva, moderace obsahu, daňové výkazy).