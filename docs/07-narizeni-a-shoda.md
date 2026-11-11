# 7 — Nařízení a shoda s legislativou

Cílem je bazar, který může reálně provozovat v ČR. Tento dokument popisuje,
které požadavky zadání jsou splněny, kde jsou hranice a proč.

**Upozornění:** tento dokument je studentská práce, nikoli právní poradenství.
Skutečný provoz vyžaduje právní přezkum a případně notáře.

---

## Shrnutí

| Oblast | Právní základ | Jak je to v systému řešeno |
|---|---|---|
| Spotřebitelská práva | § 1829 a násl. OZ | Snapshot typu prodejce, 14denní lhůta, tlačítko odstoupení |
| Ověřování obchodníků | Nařízení EU 2022/2063 (DSA) + zákon o obchodní registraci | IČO, doklad, čestné prohlášení, schválení administrátorem |
| Daňové hlášení | Směrnice 2011/16/EU (DAC7), implementace v DZ | Automatický roční výkaz CSV/XML, práh pro reportovatelnost |
| Ochrana osobních údajů | GDPR | Souhlasy, export, soft-delete, šifrování, minimalizace |
| Modrace obsahu | DSA + zákon o kybernetické bezpečnosti | Vlastní fronta s prioritou důvěryhodných hlásičů, audit |
| Spotřebitelské informace | § 1699 OZ a navazující | `rights-info` s českým vysvětlením v detailu inzerátu |

---

## 1. Ochrana spotřebitele

### Právo odstoupení od smlouvy

§ 1829 OZ dává spotřebiteli právo odstoupit od smlouvy **bez udání důvodu
do 14 dní** od jejího uzavření. U nákupu od podnikatele je toto právo
základní součástí každé smlouvy.

Implementace:

1. **Neměnný snapshot typu prodejce.** `listings.seller_type_snapshot` a
   `transactions.seller_type_snapshot` se zapíšou při vložení inzerátu
   a při rezervaci. Kdyby prodejce později změnil profil z obchodníka na
   soukromníka, právo kupce se tím **neodstraní** — právní vztah vznikl v okamžiku
   prodeje.
2. **Informační závazek.** `GET /listings/{id}/rights-info` vrací
   `{withdrawalApplies, sellerType, explanationCs}`; web i mobil zobrazují
   znění s odkazem na § 1829 a § 1837 (výjimky).
3. **Uplatnění práva.** `POST /transactions/{id}/withdraw` — jen kupující, jen
   u obchodníka, jen do 14 dní od `completed_at`. Kontrola lhůty je na serveru
   (`TransactionService.withdraw`), tlačítko v UI se objeví jen když je nárok
   skutečně uplatnitelný (`GET .../withdrawal-rights`).
4. **Důsledek.** Stav `withdrawn` je odlišný od `completed`, takže odstoupený
   obchod **nespadne do daňového výkazu** ani do příjmů prodejce.

### Vyloučení práva u soukromé osoby

Právo odstoupení je zákonné právní jednání typické pro podnikatelskou činnost;
u prodeje mezi fyzickými osobami (soukromá osoba nepodniká) se neuplatní,
pokud o tom strany výslovně nepojednají jinak. Systém proto u soukromých
prodejců právo nehlásí a místo toho výslovně vysvětluje, že se neuplatní.
Nejde o obcházení zákona, ale o jeho **řádné poskytnutí informací** — účastník
transakce tak dostane věcně správné poučení.

### Spotřebitelské informace a e-shopy

Zákon o právech spotřebitelů (§ 1699 a násl. OZ po novelách) vyžaduje
před uzavřením smlouvy sdělit řadu údajů — mimo jiné **identifikaci
prodejce, celkovou cenu včetně daní a poplatků, způsob platby, dodací
podmínky a právo na odstoupení**. Aplikace tyto údaje zobrazuje v detailu
inzerátu (prodejce s hodnocením, cena ve výši `priceCzk`, lokalita,
informace o odstoupení). Zbývající náležitosti — zejména způsob dodání a
platební podmínky — jsou předmětem dohody v chatu, což odpovídá povaze
fyzického předání a je to běžná praxe obdobných bazarů.

---

## 2. Ověřování obchodníků (DSA)

Článek 30 směrnice 2022/2063 (Digital Services Act) vyžaduje, aby platformy
podporující **prodej zboží nebo služeb** ověřovaly **podnikatele (obchodníky)**
a získaly od nich identifikační údaje. Česká právní úprava navazuje
na směrnici o vytváření trhu (OM) a na zákon o obchodní registraci.

Postup v systému:

1. **Prodej deklaruje typ** (`users.seller_type`) — soukromník / obchodník.
2. **Obchodník poskytne údaje**: IČO (do `seller_tax_profile.ico`, šifrovaně),
   adresu, prohlášení, že nejde o spotřební zboží (`self_cert`), a **kopii
   dokladu totožnosti** (`id_doc_bytea`).
3. **Administrátor rozhodne**: `POST /admin/verifications/{userId}/approve|reject`.
4. **Rozhodnutí je evidováno** (`verification_status`, `verified_by`,
   `verified_at`, `verification_note`) a zobrazuje se jako odznak
   **✓ Ověřený obchodník**.

Bezpečnostní hlediska:

- **Obsah dokladu se veřejně nezobrazuje** a API ho nevrací; je dostupný jen
  pro rozhodnutí v administraci.
- **Rozhodnutí nelze vydat dvakrát** — o tom rozhoduje stav
  `verification_status`, ne klient.
- **Nahraný doklad lze přijmout jen u obchodníka** (`422 NOT_A_TRADER`).
- **Jde o minimum**, nikoli o úplné KYC. U vysokých objemů by bylo nutné
  doplnit kontrolu insolvenčního rejstříku; zadání rozsah nepožaduje.

---

## 3. Daňové hlášení (DAC7)

Směrnice DAC7 ukládá platformám poskytujícím služby zprostředkování
obchodů mezi **podnikateli** povinnost identifikovat tyto transakce a
předat příslušnému finančnímu orgánu údaje o prodejci a transakci.

Implementace:

- **Rozsah.** Výkaz zahrnuje obchody dokončené v daném roce se stavem
  `completed` a se snapshotem prodejce `trader`. Stav `withdrawn` je
  odstoupený obchod a je výslovně vyřazen.
- **Prah reportovatelnosti.** 30 prodejů **nebo** 50 000 Kč obratu v roce.
  Odpovídá prahu směrnice (30 prodejů / 2 000 € při orientačním kurzu
  25 Kč/€ — viz `SellerService.DAC7_*`).
- **Obsah.** Jméno, e-mail, DIČ, IČO, IBAN, počet prodejů a součet částek.
  Detailní varianta navíc obsahuje jednotlivé prodeje s datem, částkou
  a typem prodejce.
- **Formáty.** CSV (oddělený středníkem, se escapováním) a XML
  (s escapováním značek). Jde o provozní výstup pro kontrolu před odevzdáním,
  nikoli o podání přes technický portál — to zůstává na provozovateli.
- **Automatizace.** Každý **5. ledna ve 3:00** (`Dac7Scheduler`) se výkaz
  sestaví a jeho vyhotovení zapíše do audit logu.
- **Souhrn pro dashboard** — `GET /admin/dac7/summary?year=` vrací počty a
  částky bez nutnosti stahovat soubor.

Údaje jsou dostupné výhradně administrátorovi a každé stažení exportu
je evidováno.

---

## 4. Ochrana osobních údajů (GDPR)

### Právní základ

| Účel | Právní základ |
|---|---|
| Vedení účtu a provoz bazaru | Plnění smlouvy / oprávněný zájem |
| Daňový profil a výkazy | **Zákonná povinnost** (ohlašovací povinnost DAC7) |
| Marketing | **Souhlas** |
| Cookies a analytika | **Souhlas** |

### Implementace práv

| Právo | Jak je řešeno |
|---|---|
| Právo na přístup | `GET /users/me/export` — export všech osobních údajů (JSON) |
| Právo na opravu | `PATCH /users/me/seller-info`, editace profilu |
| Právo na výmaz | `DELETE /users/me` — soft-delete (`deleted_at`) |
| Právo na přenositelnost | Totéž co export — strojově čitelný JSON |
| Právo na vznesení námitky | `POST /consents` — záznam souhlasu a jeho změn |
| Právo na omezení zpracování | Nejde u nás relevantní |

### Zásady, které dodržujeme

- **Minimalizace.** Veřejné endpointy (`/users/{id}`, `/users/{id}/badges`)
  **nikdy nečtou daňový profil** — vracejí ID, zobrazované jméno, typ prodejce
  a odznaky.
- **Šifrování.** Jméno, datum narození, adresa, DIČ, IČO a IBAN jsou
  šifrované AES-GCM 256 s 12bajtovým IV. Klíč pochází z prostředí,
  nikoli z kódu.
- **Export bez daňových údajů.** GDPR export záměrně neobsahuje daňový
  profil — ten má jiný právní základ (zákonná povinnost) a jinou
  zadržovací dobu.
- **Retence po výmazu.** Daňový profil zůstává archivovaný, protože zákon
  o daních stanovuje delší zadržovací dobu než GDPR. Účet samotný je
  nefunkční a nepřihlásitelný.
- **Souhlasy se evidují.** Každé rozhodnutí o souhlasu je zapsáno do
  `consents` s časovým razítkem — doklad splnění informační povinnosti.
- **Bez cookies před odsouhlasením.** Banner souhlasu je první věcí, kterou
  stranka načte; bez odpovědi se žádné analytické cookies nenasazují.

---

## 5. Modrace obsahu

Systém implementuje mechanismus oznamování a odstahování obsahu podle
článku 17 DSA:

- **Vlastní oznamovací mechanismus.** `POST /moderation/reports` — uživatel
  označí inzerát, recenzi či chat se zadaným důvodem.
- **Důvěryhodné oznamovatele** rozhoduje **server**: administrátor nebo uživatel
  s nejméně třemi potvrzenými hlášeními (`trusted_flagger`). Klient to
  nemůže ovlivnit.
- **Prioritizace.** Fronta řadí důvěryhodné hlásiče nahoru
  (`reports_status_idx`), u velkých platforem by následovalo časové
  pořadí.
- **Rozhodnutí a odstranění.** `approve` (ponechat) nebo `remove`
  (inzerát → `removed`, recenze → skrytá). **Každé rozhodnutí jde do auditu**
  včetně toho, kdo ho vydal.
- **Uživatelská smlouva a body zprávy.** V tomto rozsahu neimplementováno;
  pro komerční provoz by byla podmínkou. Uvedeno jako známé omezení.

---

## 6. Elektronické platby a smlouvy

Naplňujeme sjednocenou smlouvu — tzv. smlouva o přechodu vlastnictví věci
za protiplnění (ustanovení § 2131 a § 2132 OZ), uzavřenou přijetím
objednávky. Rezervace a následné potvrzení obchodu jsou její součástí.

**Bezhotovostní platba je záměrně mimo rozsah zadání.** Aplikace nepracuje
s platební bránou ani neuchovává peníze — platba se dohaduje na předání
přímo mezi účastníky. Tím se vyhýbáme nutnosti licencovat platební službu
a odpovídáme tomu, jak fungují srovnatelné C2C bazarové platformy.

---

## 7. Známé omezení

Otevřeně uvádíme, co systém **neřeší**:

1. **Uživatelská smlouva a body zprády** (DSA čl. 3) — chybí.
2. **Úplné KYC** — ověřujeme IČO a doklad, ne kontrolujeme insolvenční
   rejstřík.
3. **Odesílání zákonného výkazu technickým portálem** — systém výkaz připraví,
   podání provede provozovatel.
4. **Reklamace a řešení sporů** — mimo rozsah; jako obdobné platformy
   funguje odstoupení od smlouvy a moderační fronta.
5. **Přepočet kurzu** pro práh 2 000 € je orientační (25 Kč/€); v plném
   provozu by se použil úřední kurz ČNB.

---

## 8. Shrnutí opatření

**Prevence problémů:** daňový profil povinný pro vstup do systému, moderace
vlastního obsahu, důvěryhodní oznamovatelé, audit každého rozhodnutí, limit
pokusů proti zneužití, blokace účtů, šifrování osobních údajů, zálohovaný
klíč.

**Detekce problémů:** hlášení uživatelů, recenze a jejich námitky, audit log,
přehled obchodů pro prodejce, souhrn DAC7 pro administrátora, export dat
uživatelem.

**Řešení problémů:** rozhodnutí moderace s odstraněním, námitky na hodnocení
s rozhodnutím administrátora, potvrzení nebo zamítnutí odstoupení, blokace
účtů, oprava nebo smazání dat na vyžádání.