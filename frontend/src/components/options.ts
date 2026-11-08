/** Sdílené číselníky: kategorie musí sedět s filtrem v Hledat i se stávajícími daty. */
export const CATEGORIES: ReadonlyArray<readonly [string, string]> = [
  ["elektronika", "Elektronika"],
  ["sport", "Sport"],
  ["nabytok", "Nábytek"],
  ["moda", "Móda"],
  ["auto", "Auto-moto"],
  ["dum", "Dům a zahrada"],
  ["ostatni", "Ostatní"],
];

export const CONDITIONS: readonly string[] = ["Nové", "Výborný", "Velmi dobrý", "Dobrý"];

export const catLabel = (v: string | null | undefined): string => {
  if (!v) return "—";
  return CATEGORIES.find(([val]) => val === v)?.[1] ?? v;
};