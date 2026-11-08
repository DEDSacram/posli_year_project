/** Validace formulářů s českými hláškami. Každý validátor: (hodnota) => textChyby | "". */

/** Vyžaduje vyplněnou hodnotu. */
export const vRequired = (label = "Pole") => (v: unknown): string =>
  String(v ?? "").trim() ? "" : `${label} je povinné.`;

export const vEmail = (v: unknown): string =>
  /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(String(v ?? "").trim())
    ? ""
    : "Zadejte platný e-mail (např. jan.novak@email.cz).";

export const vPassword = (v: unknown): string =>
  String(v ?? "").length >= 8 ? "" : "Heslo musí mít aspoň 8 znaků.";

export const vDisplayName = (v: unknown): string => {
  const s = String(v ?? "").trim();
  if (!s) return "Zobrazované jméno je povinné.";
  if (s.length < 2) return "Jméno je příliš krátké.";
  return "";
};

/** Jméno a příjmení se zadávají zvlášť, proto dvě různé pravidla. */
export const vPersonName = (label: string) => (v: unknown): string => {
  const s = String(v ?? "").trim();
  if (!s) return `${label} je povinné.`;
  if (s.length < 2) return `${label} je příliš krátké.`;
  if (!/^[A-Za-zÁČĎÉĚÍŇÓŘŠŤÚŮÝŽáčďéěíňóřšťúůýž'’\- ]+$/.test(s))
    return `${label} smí obsahovat jen písmena, mezery a pomlčky.`;
  return "";
};

export const vPrice = (v: unknown): string => {
  if (String(v ?? "").trim() === "") return "Cena je povinná.";
  const n = Number(v);
  if (!Number.isInteger(n) || n <= 0) return "Cena musí být celé kladné číslo v Kč.";
  if (n > 100_000_000) return "Cena je nereálně vysoká.";
  return "";
};

/** IBAN: obecný formát + kontrolní součet mod-97. český IBAN = CZ + 22 číslic. */
export function ibanCompact(s: unknown): string {
  return String(s ?? "").replace(/[\s-]/g, "").toUpperCase();
}

export function ibanValid(s: unknown): boolean {
  const v = ibanCompact(s);
  if (!/^[A-Z]{2}\d{2}[A-Z0-9]{11,30}$/.test(v)) return false;
  const rearranged = v.slice(4) + v.slice(0, 4);
  const digits = rearranged.replace(/[A-Z]/g, (c) => String(c.charCodeAt(0) - 55));
  let rem = 0;
  for (const ch of digits) rem = (rem * 10 + Number(ch)) % 97;
  return rem === 1;
}

export const vIban = (v: unknown): string => {
  const c = ibanCompact(v);
  if (!c) return "IBAN je povinný.";
  if (!/^[A-Z]{2}/.test(c)) return "IBAN začíná kódem země (např. CZ).";
  if (c.startsWith("CZ") && !/^CZ\d{22}$/.test(c))
    return "Český IBAN má tvar CZ + 22 číslic (celkem 24 znaků).";
  if (!/^[A-Z]{2}\d{2}[A-Z0-9]{11,30}$/.test(c))
    return "IBAN má špatný formát (2 písmena, 2 číslice, pak 11–30 znaků).";
  if (!ibanValid(c)) return "IBAN neprošel kontrolním součtem (zkontrolujte číslice).";
  return "";
};

export const vCzechPostal = (v: unknown): string => {
  const s = String(v ?? "").trim();
  if (!s) return "PSČ je povinné.";
  return /^\d{3} ?\d{2}$/.test(s) ? "" : "PSČ má tvar 110 00.";
};

export const vDob = (v: unknown): string => {
  const s = String(v ?? "").trim();
  if (!s) return "Datum narození je povinné.";
  if (!/^\d{4}-\d{2}-\d{2}$/.test(s)) return "Datum ve tvaru RRRR-MM-DD (např. 1990-05-05).";
  const d = new Date(s + "T00:00:00");
  if (Number.isNaN(d.getTime())) return "Neplatné datum.";
  if (d >= new Date()) return "Datum narození musí být v minulosti.";
  const age = (Date.now() - d.getTime()) / (365.25 * 24 * 3600 * 1000);
  if (age < 15) return "Musí vám být aspoň 15 let.";
  return "";
};

export const vLat = (v: unknown): string => {
  if (String(v ?? "").trim() === "") return "";
  const n = Number(v);
  return Number.isFinite(n) && n >= -90 && n <= 90 ? "" : "Šířka musí být −90 až 90.";
};

export const vLon = (v: unknown): string => {
  if (String(v ?? "").trim() === "") return "";
  const n = Number(v);
  return Number.isFinite(n) && n >= -180 && n <= 180 ? "" : "Délka musí být −180 až 180.";
};

export type Validator = (value: unknown) => string;

/** Složí více validátorů — vyhrává první chyba. */
export const combine =
  (...fns: Validator[]): Validator =>
  (v: unknown): string => {
    for (const f of fns) {
      const e = f(v);
      if (e) return e;
    }
    return "";
  };