/** Naposledy zobrazené inzeráty — držíme je jen v tomto prohlížeči (localStorage). */
const KEY = "bazar-recent";
const MAX = 8;

export function readRecent(): number[] {
  try {
    const arr: unknown = JSON.parse(localStorage.getItem(KEY) ?? "[]");
    return Array.isArray(arr) ? arr.filter((x): x is number => Number.isInteger(x)) : [];
  } catch {
    return [];
  }
}

export function pushRecent(id: number): void {
  if (!Number.isInteger(id)) return;
  try {
    const arr = [id, ...readRecent().filter((x) => x !== id)].slice(0, MAX);
    localStorage.setItem(KEY, JSON.stringify(arr));
  } catch {
    /* plné úložiště — ignorovat */
  }
}