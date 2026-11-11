# 5 — Uživatelská příručka

Příručka pro všechny, kdo PosliToDal používají — od hosta, který jen
prohlíží, po administrátora. Nepředpokládá žádné technické znalosti.

Obsah:
1. [Začínáme](#1-začínáme) · 2. [Prohlížení a hledání](#2-prohlížení-a-hledání)
3. [Registrace a účet](#3-registrace-a-účet) · 4. [Prodej](#4-prodej)
5. [Chat a rezervace](#5-chat-a-rezervace) · 6. [Nabídka vlastní ceny](#6-nabídka-vlastní-ceny)
7. [Oblíbené a upozornění](#7-oblíbené-a-upozornění) · 8. [Hodnocení a námitky](#8-hodnocení-a-námitky)
9. [Ochrana spotřebitele](#9-ochrana-spotřebitele) · 10. [Moje obchody](#10-moje-obchody)
11. [Prodej jako obchodník](#11-prodej-jako-obchodník) · 12. [Administrace](#12-administrace)
13. [Soukromí a bezpečnost](#13-soukromí-a-bezpečnost) · 14. [Časté dotazy](#14-časté-dotazy)

---

## 1. Začínáme

**PosliToDal** je bazar použitého zboží: lidé tu prodávají věci, které už
nepotřebují, a nakupují od sebe. Ke všemu můžete přistupovat bez účtu —
jen nakupovat, prodávat a psát si zprávy musíte po přihlášení.

| Kde | Adresa |
|---|---|
| Web | `http://localhost:3000` |
| Android | aplikace *PosliToDal* |

Přihlášení je v pravém horním rohu (** Více → Přihlásit** na mobilu).
Účet zakládá registrací — trvá to půl minuty.

---

## 2. Prohlížení a hledání

### Katalog

Na úvodní stránce vidíte nejnovější inzeráty. Scrollováním dolů načítáte další.
Nad katalogem je řádek **Naposledy zobrazené** — rychlý návrat k tomu, co
jste právě prohlíželi (uloženo jen v tomto prohlížeči).

### Hledání

Klikněte na **Vyhledávací pole** v hlavičce nebo na **Kategorie**. Můžete
kombinovat:

- **Slovo nebo jeho část** — napište `kůň` a najdete i „Vintage koňárna“.
- **Kategorie** — Elektronika, Sport, Nábytek, Móda, Auto-moto, Dům a zahrada,
  Ostatní.
- **Cena** — od a do v Kč.
- **Okruh kolem místa** — zadejte PSČ (např. `110 00`), stiskněte **Použít okolí**
  a doplňte poloměr v kilometrech. PSČ se automaticky předvyplní z vašeho
  profilu.
- **Řazení** — Relevance, Nejnovější, Nejlevnější, Nejdražší.

Tip: pokud vám z profilu předvyplní PSČ, stačí doplnit okruh a hledat.

### Detail inzerátu

Kliknutím na kartu se dostanete na detail, kde najdete:

- **velkou fotografii** a náhledy dalších (kliknutím otevřete zvětšenou verzi;
  v ní fungují klávesy ← → a `Esc`),
- **mapu polohy** — pokud prodejce souhlasil se zveřejněním přesného místa,
  jinak uvidíte jen město (to je běžné a kvůli soukromí),
- **prodejce** s jeho hodnocením a odznaky — kliknutím otevřete jeho profil,
- **informaci o spotřebitelských právech** (viz [kapitola 9](#9-ochrana-spotřebitele)),
- **hodnocení prodejce** a možnost nahlásit inzerát.

---

## 3. Registrace a účet

### Registrace

1. Klikněte **Registrace**.
2. Vyplňte e-mail, heslo (minimálně 8 znaků), jméno a příjmení.
3. Zvolte, jestli jste **soukromník** nebo **obchodník** (prodej zboží
   z povinného výběru v podnikání). Obchodník navíc vyplní IČO, nahraje doklad
   a čestné prohlášení — viz [kapitola 11](#11-prodej-jako-obchodník).
4. Potvrďte registraci.

Heslo se nikam neukládá v čitelné podobě.

### Můj účet

Do účtu se dostanete přes avatar v pravém horním rohu → **Můj účet**. Zde
upravíte:

- **Daňový profil** — jméno, datum narození, adresa, DIČ, IČO, IBAN. Údaje jsou
  **šifrované** a vidí je jen správce bazaru, protože se jimi hlásíte finančním
  úřadům (viz [kapitola 13](#13-soukromí-a-bezpečnost)).
  *IBAN kontrolujeme formát i kontrolní součet hned, jak píšete.*
- **Stav hlášení do finančního úřadu** — uvidíte, kolik jste leto prodali,
  jaký máte obrat a zda vás to už k hlášení opravňuje.
- **Moje inzeráty** — editace a smazání.
- **Uložené inzeráty** — přechod do oblíbených.
- **Oprávnění pro web** — souhlas s cookies, analytikou či marketingem.
  Souhlas můžete kdykoli změnit.
- **Moje data** — export všech osobních údajů (JSON) a smazání účtu.

### Přehled obchodů

Odkaz **📦 Moje obchody** v hlavičce (na mobilu *Více → Moje obchody*) zobrazí
souhrn: kolik prodejů jste uzavřeli, jaký máte obrat a jaké jste získali
hodnocení. Níže je seznam jednotlivých obchodů s jejich stavem.

---

## 4. Prodej

### Než začnete

Pro vložení inzerátu musíte mít **vyplněný daňový profil**. Bez něj systém
vložení odmítne a ukáže odkaz přímo do formuláře.

### Vložení inzerátu

1. Klikněte **Prodat**.
2. **Rychlé doplnění** (volitelné):
   - *Hledat přes Google Lens* — otevře Lens v novém okně, fotku tam přetáhněte
     a název zkopírujte zpět.
   - *Načíst čárový kód z fotky* — prohlížeč přečte kód z první fotografie
     (Chrome nebo Edge).
   - *Odhadnout kategorii a cenu* — systech navrhne kategorii a ukáže, za kolik
     se podobné zboží prodává.
3. Vyplňte **název** a **popis**. Uveďte stav, případné vady a co k věci patří —
   upřímný popis šetří čas oběma stranám.
4. **Cena** (celé Kč), **kategorie**, **stav**, **lokalita** (město).
5. **Přesná poloha** (volitelné) — zadejte PSČ, klikněte do mapy nebo použijte
   *📍 Moje poloha*. Poloha se předvyplní z profilu; pokud se liší, systém
   vám nabídne profil aktualizovat. **Bez polohy uvidí kupci jen město** —
   přesnou polohu zveřejňujte jen tehdy, když vám to vyhovuje.
6. **Fotky** — až 10, JPEG/PNG/WebP, do 5 MB. První je hlavní; pořadí měníte
   šipkami. Fotky můžete i přetáhnout nebo vložit ze schránky (`Ctrl+V`).
   Server z fotek automaticky odstraňuje metadata EXIF.
7. **Pokračovat na náhled** — uvidíte přesně to, co uvidí ostatní.
8. **Publikovat**.

Rozepsaný koncept se ukládá do prohlížeče, takže když se vrátíte, neztratíte
ho. Koncept smažete tlačítkem *Začít znovu*.

### Úprava a smazání

**Můj účet → Moje inzeráty → Upravit**. Lze změnit text, cenu, kategorii, stav,
město i polohu a přidat nebo smazat fotografie. **Pokud snížíte cenu, všem,
kdo mají inzerát v oblíbených, automaticky přijde upozornění.**

Smazání je měkké — inzerát zmizí z katalogu, ale zůstává jako doklad
uskutečněného obchodu.

### Duplikace

V detailu vlastního inzerátu je **Duplikovat inzerát** — otevře předvyplněný
formulář (fotky se neduplikují).

---

## 5. Chat a rezervace

### Rezervace jedním kliknutím

**Koupit nyní** vloží rezervaci a zároveň otevře chat s předvyplněnou zprávou,
že věc rezervujete. Prodejce tak hned ví, o co jde.

Rezervace je **jeden klik** — když stisknete omylem dvakrát, nevzniknou dvě,
vrátí se ta samá.

### Komunikace

V chatu můžete použít **rychlé odpovědi** (tlačítka nad polem): *Je to ještě
dostupné?*, *Šla by sleva?*, *Kdy by bylo možné předání?*, *Pošlete prosím více
fotek.*

Na kartě inzerátu vidíte počet zájemců (🔥 n× má zájem).

### Dokončení obchodu

Po předání potvrdí obchod **kupující i prodejce** v *Moje obchody*
(tlačítko *Potvrdit předání* / *Potvrdit prodej*). Dokončený obchod otevírá
možnost ohodnotit prodejce a — jde-li o nákup od obchodníka — právo odstoupení.

Když předání nevyjde, rezervaci můžete **zrušit**.

---

## 6. Nabídka vlastní ceny

Pokud vám cena nevyhovuje, v detailu inzerátu vyplňte pole **Vaše nabídka
(Kč)** a stiskněte **Nabídnout cenu**. Otevře se chat s předvyplněnou
zprávou, ve které je vaše částka i název věci.

Nabídka je tedy **zpráva s konkrétní částkou** — odpověď, zda ji prodejce
přijímá, dostanete v chatu. Rezervace vznikne až poté, když prodejce
souhlasí a vy stisknete *Koupit nyní*.

> **Tip:** Nabídka mívá vyšší šanci než dotaz typu „Kolik byste chtěl?“.
> Konkrétní částka je pro prodejce snazší posoudit.

---

## 7. Oblíbené a upozornění

### Uložení

Tlačítko **Přidat do oblíbených** (srdíčko) na kartě nebo v detailu.
Přihlášení uživatele je ukládá do **profilu** — uvidí je na každém zařízení.
Jako host si srdíčka držíte jen v tomto prohlížeči; po přihlášení se
automaticicky přesunou do profilu.

*Uložené inzeráty → Oblíbené* (odkaz v hlavičce).

### Upozornění na zlevnění

U každého uloženého inzerátu si systek pamatuje cenu z doby uložení:

- v **Oblíbených** uvidíte zelený odznak *−500 Kč od uložení*,
- jakmile prodejce **sníží cenu**, přijde vám **upozornění** v zvonku
  v pravém horním rohu (a na mobilu *Více → Upozornění*),
- nepřečtená upozornění odlišuje dvojtečka, tlačítko *Přečíst vše* je označí
  všechna najednou.

Stejné zlevnění se neopakuje — po přečtení může přijít další.

---

## 8. Hodnocení a námitky

### Hodnocení prodejce

Po **dokončeném obchodu** vám v detailu inzerátu (i v *Moje obchody*) nabídne
tlačítko **⭐ Ohnotit prodejce**. Hodnocení je od **1 do 5 hvězd** plus text.

Důležité vlastnosti:

- **ověřený nákup** — hodnocení dostanou jen kupující dokončené transakce,
- **jedna recenze na obchod** — nelze přepsat,
- hodnocení je **veřejné** a započítává se do průměru prodejce.

### Odpověď prodejce

Prodejce může na každé hodnocení reagovat **jednou veřejnou odpovědí**
(tlačítko *Odpovědět* u recenze). Jeho odpověď se zobrazí pod hodnocením.

### Námitka proti hodnocení

Cítíte-li hodnocení jako nespravedlivé, můžete se **proti němu ohradit**
(tlačítko *⚖️ Ohradit se proti tomuto hodnocení* pod recenzí):

1. Napište důvod (minimálně 10 znaků) a podajte námitku.
2. **Administrátor ji posoudí.** U recenze se pak objeví *Námitka se řeší*.
3. Rozhodnutí:
   - **námitka potvrzena** → recenze se odstraní a nepočítá se do hodnocení,
   - **námitka zamítnuta** → recenze zůstává v platnosti.

Do rozhodnutí **vaše hodnocení pro ostatní kupce nemění** — to je záměr,
aby se hodnocení nedalo snadno mazat. Námitky evidujeme, abychom opakované
neférové hodnocení postihli (viz odznak *Bez stížností*).

### Odznaky pro poctivé obchodníky

U jména prodejce se zobrazují odznaky odvozené z **reálné historie**, ne
reklamou:

| Odznak | Znamená |
|---|---|
| ✓ Ověřený obchodník | doklad totožnosti potvrdil administrátor |
| Důvěryhodný prodejce | hodnocení 4,5+ a žádná potvrzená námitka |
| Vysoké hodnocení | průměr 4,8+ z 5 a více hodnocení |
| Zkušený prodejce | 10 a více dokončených prodejů |
| Bez stížností | žádná potvrzená námitka proti jeho hodnocení |

Odznaky se objeví až po skutečném výkonu — nový účet žádné nemá.

---

## 9. Ochrana spotřebitele

### Právo na odstoupení (14 dní)

Když kupujete **od obchodníka**, máte podle § 1829 občanského zákoníku
**14 dní na odstoupení od smlouvy** bez udání důvodu.

- U každého inzerátu obchodníka najdete **modré upozornění** s vysvětlením.
- Po dokončení obchodu se v *Moje obchody* objeví tlačítko
  **Odstoupit od smlouvy (do 18. 10.)** s konkrétním datem.
- Po stisknutí můžete uvést důvod (není povinný).

**Kdy právo odstoupení neplatí:** u nákupu od soukromné osoby — tam vám
systém vysvětlí, že se neuplatní, a nabídne vhodnější řešení (dohoda
s prodejcem). Po odstoupení se obchod označí *Odstoupeno od smlouvy* a
prodejci ani vám se do obratu pro daňové účely nezapočítá.

> Vždy si nechte dohodu písemně (zpráva v chatu) — u soukromé osoby vám
> právní ochrana 14 dní neposkytuje.

### Spotřebitelské informace

U každého detailu inzerátu je běžně dostupné, jestli 14denní právo platí.
Prodejci typu ukládá **neměnný snímek** — změna profilu později vaše právo
neodstraní.

---

## 10. Moje obchody

*Moje obchody* (📦 v hlavičce) ukazuje souhrn a seznam všech vašich obchodů.
Přepínač nahoře filtruje **Vše / Nákupy / Prodeje**.

U každého obchodu vidíte stav a dostupné akce:

| Stav | Akce |
|---|---|
| **Rezervováno** | Potvrdit předání / prodej, nebo zrušit rezervaci |
| **Dokončeno** | Ohnotit prodejce (jako kupující), odstoupit od smlouvy (u obchodníka) |
| **Zrušeno** | nic — rezervace skončila bez obchodu |
| **Odstoupeno od smlouvy** | nic — právo spotřebitele bylo uplatněno |

Nahoře najdete čísla: dokončené prodeje, obrat, **získané hodnocení** a
počet čekajících obchodů.

---

## 11. Prodej jako obchodník

Pokud prodáváte zboží z podnikání, zvolte při registraci **Obchodník**.
Systím vás pak podle nařízení (DSA) a zákona o obchodní registraci
podrobněji ověří — to je pro obchodníky běžné a zákonné.

### Co musíte mít

1. **Vyplněný daňový profil** včetně **IČO** a **IBAN** (k výpočtu ceny,
   prodeji a případnému vrácení peněz).
2. **Čestné prohlášení** — potvrzení, že nejde o spotřební zboží.
3. **Kopii dokladu totožnosti** — nahrajete v účtu (obrázek nebo PDF, do 5 MB).

### Jak to funguje

1. **Nahrajte doklad** v *Můj účet → Daňový profil → Ověření obchodníka*.
2. Doklad má stav *Čeká na posouzení administrátorem*. Prodejovat můžete
   mezitím — potvrzení není podmínkou.
3. Administrátor doklad posoudí a schválí (odznak **✓ Ověřený obchodník**)
   nebo odmítne s vysvětlením. **Stav uvidíte v účtu**, obsah dokladu
   se nikdy nezobrazuje.
4. Když doklad znovu nahrajete (např. protože se změnil), čeká na nové
   posouzení.

### Povinnosti obchodníka

- Uvádět stav zboží a případné vady — jinak může být prodej spotřebitelsky
  nepoctivý.
- Respektovat **14denní právo na odstoupení**.
- Vést evidenci pro **daňové výkazy** (viz níže).

### Daňové výkazy (DAC7)

Systém automaticky připravuje výkaz pro finanční úřady. Uvidíte v účtu
položku **DAC7 stav**: počet prodejů, obrat a zda vás to opravňuje k hlášení
(prah: **30 prodejů nebo 50 000 Kč ročně**). Výkaz se sestavuje každý rok
a jeho vyhotovení eviduje administrátor.

---

## 12. Administrace

Do administrace se dostanete z hlavní nabídky jen přihlášený administrátor.
Má šest sekcí:

### Moderační fronta

Hlášení uživatelů na inzeráty, recenze a chaty. Důvěryhodné hlásiče
(administrátor a uživatelé s nejméně třemi potvrzenými hlášeními) jsou
nahoře. U každého hlášení: **Ponechat** nebo **Odstranit** (inzerát zmizí
z nabídky, recenze se skryje).

### Námitky na hodnocení

Otevřené námitky prodejců. U každé se rozhodne **Potvrdit námitku**
(recenze se odstraní) nebo **Zamítnout** (recenze zůstane). Volitelná poznámka
se uloží do auditu a zobrazí v rozhodnutí.

### Ověření obchodníků

Obchodníci s nahraným dokladem, kteří čekají na posouzení. **Potvrdit doklad**
udělí odznak *Ověřený obchodník*, **Odmítnout** vrátí prodejce k nahrání
nového. Poznámka se prodejci zobrazí v účtu.

### Uživatelé

Výpis uživatelů s vyhledáváním podle e-mailu nebo jména. **Blokovat** účet
zablokuje přihlášení (poznámka se uloží do auditu), **Odblokovat** vrátí
přístup. Administratory blokovat nelze.

### Audit log

Záznamy důležitých akcí: rozhodnutí moderace, námitky, ověření, blokace,
generování daňových výkazů. Filtruje se podle názvu akce. **Samotné čtení
auditu se také zaznamenávává.**

### Daňové výkazy (DAC7)

Souhrn za rok (počet prodejců, prodejů, obrat) a dva exporty:

- **Souhrn prodejců** — agregace po prodejcích s DIČ, IČO a IBAN,
- **Detail prodejů** — jednotlivé prodeje s datem a částkou,

ve formátu CSV nebo XML. Výkaz se generuje automaticky každý **5. ledna**
a jeho vyhotovení je v auditu.

---

## 13. Soukromí a bezpečnost

### Vaše údaje

- **Heslo** se neukládá čitelně — používá se bcrypt.
- **Daňové údaje** (jméno, datum narození, adresa, DIČ, IČO, IBAN) jsou
  **šifrované** (AES-GCM) a nikdo je nevidí v aplikaci. Přístup k nim má jen
  správce kvůli zákonným povinnostem.
- **Export dat** — v *Můj účet → Moje data* dostanete všechny své údaje
  jedním souborem. Daňové údaje v exportu záměrně **nejsou**.
- **Smazání účtu** — *Můj účet → Smazat účet*. Účet přestane fungovat
  okamžitě. **Daňový profil zůstává archivovaný**, protože ho musíme
  uchovat pro daňové hlášení (zákonná retence).
- **Souhlasy** — cookies, analytiku a marketing řídíte sami; souhlas můžete
  kdykoli odebrat.
- **Poloha** — přesné souřadnice zveřejníte jen tehdy, když to v inzerátu
  nastavíte. Jinak se ukazuje jen město.

### Doporučení

- používejte silné a jedinečné heslo,
- na veřejném Wi-Fi přihlašujte přes `https`,
- před odevzdáním věci domluvte předání písemně v chatu.

---

## 14. Časté dotazy

**Nemůžu vložit inzerát, píše „doplňte daňový profil“.**
Vyplňte *Můj účet → Daňový profil*. Bez jména a IBANu systém nepovolí
publikovat — jde o zákonnou povinnost.

**Obchodník: „musí mít IČO, doklad a čestné prohlášení“.**
Jste v části pro obchodníka. IČO vyplňte v daňovém profilu, doklad nahrajte
tlačítkem *Nahrát doklad*. Doklad musí být nahrát **před uložením profilu**.

**Rezervoval jsem věc, ale prodejce neodpovídá.**
V *Moje obchody* rezervaci zrušíte a můžete hledat dál.

**Zakoupil jsem něco od obchodníka a chci to vrátit.**
*Moje obchody → Odstoupit od smlouvy* (do 14 dní od dokončení). Datum
konečného termínu vidíte na tlačítku.

**Koupil jsem od soukromé osoby a věc nevyhovuje.**
14denní právo odstoupení se neuplatní. Domluvte se v chatu — u soukromé
osoby je to věc dohody. Případ poškození hlaste (viz níže).

**Hodnocení není spravedlivé.**
Prodej může podat námitku (viz [kapitola 8](#8-hodnocení-a-námitky)). Jako
kupující můžete inzerát nahlásit přes *Nahlásit inzerát*.

**Přijel mi zvoník s odkazem na inzerát.**
Otevře se detail inzerátu, který prodejce opravdu snížil. Upřímné
upozornění vám chybí jen tehdy, když jste nepřihlášeni nebo nemáte srdíčko.

**Nefunguje přihlášení po 10 pokusech.**
Z bezpečnosti je tu omezení 10 přihlášení za minutu na stejnou síť. Zkuste to
za chvíli.

**Změnil jsem adresu serveru na mobilu.**
*Více → Adresa serveru*. Výchozí `http://10.0.2.2:8081` odpovídá hostiteli
z pohledu emulátoru; na skutečném telefonu zadejte adresu počítače v síti.

**Chci někoho nahlásit.**
V detailu inzerátu *Nahlásit inzerát* a napište důvod. Zpracuje ho
administrátor v moderační frontě.