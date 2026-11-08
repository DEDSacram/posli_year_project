import { useCallback, useEffect, useState } from "react";
import { api } from "../api";
import type { ApiError } from "../api";

/**
 * Oblíbené navázané na uživatelský profil (backend); hosté mají lokální úložiště.
 * Při přihlášení se hostova lokální srdíčka sloučí do profilu.
 */
const KEY = "bazar-favs";
export const FAV_EVT = "bazar-favs-changed";
export const AUTH_EVT = "bazar-auth-changed";

export function isLoggedIn(): boolean {
  try {
    const t: unknown = JSON.parse(localStorage.getItem("bazar-tokens") ?? "null");
    return !!t && typeof t === "object" && "token" in t;
  } catch {
    return false;
  }
}

function readGuest(): number[] {
  try {
    const arr: unknown = JSON.parse(localStorage.getItem(KEY) ?? "[]");
    return Array.isArray(arr) ? arr.filter((x): x is number => Number.isInteger(x)) : [];
  } catch {
    return [];
  }
}

function writeGuest(ids: number[]): void {
  localStorage.setItem(KEY, JSON.stringify(ids));
}

function notify(): void {
  window.dispatchEvent(new Event(FAV_EVT));
}

/** Jednorázové sloučení hostových srdíček do profilu po přihlášení. */
async function mergeGuestIds(): Promise<void> {
  const guest = readGuest();
  if (!guest.length || !isLoggedIn()) return;
  for (const id of guest) {
    try {
      await api.addFavorite(id);
    } catch {
      /* inzerát mezitím zmizel — ignorovat */
    }
  }
  writeGuest([]);
}

async function loadIds(): Promise<number[]> {
  if (!isLoggedIn()) return readGuest();
  await mergeGuestIds();
  const d = await api.favoriteIds();
  return (d.ids ?? []).filter((x) => Number.isInteger(x));
}

export interface FavIds {
  ids: number[];
  toggle: (id: number) => Promise<void>;
  remove: (id: number) => Promise<void>;
  clearAll: () => Promise<void>;
}

export function useFavIds(): FavIds {
  const [ids, setIds] = useState<number[]>(() => (isLoggedIn() ? [] : readGuest()));

  const refresh = useCallback(async () => {
    try {
      setIds(await loadIds());
    } catch {
      if (!isLoggedIn()) setIds(readGuest());
    }
  }, []);

  useEffect(() => {
    refresh();
    window.addEventListener(FAV_EVT, refresh);
    window.addEventListener(AUTH_EVT, refresh);
    window.addEventListener("storage", refresh);
    return () => {
      window.removeEventListener(FAV_EVT, refresh);
      window.removeEventListener(AUTH_EVT, refresh);
      window.removeEventListener("storage", refresh);
    };
  }, [refresh]);

  const toggle = useCallback(
    async (id: number) => {
      if (!isLoggedIn()) {
        const guest = readGuest();
        writeGuest(guest.includes(id) ? guest.filter((x) => x !== id) : [...guest, id]);
        notify();
        return;
      }
      // Překlopíme optimisticky; API volíme mimo updater, protože updater musí být
      // čistý — StrictMode ho volá dvakrát a zdvojil by POST/DELETE.
      const has = ids.includes(id);
      setIds(has ? ids.filter((x) => x !== id) : [...ids, id]);
      try {
        if (has) await api.removeFavorite(id);
        else await api.addFavorite(id);
      } catch (e) {
        setIds(has ? [...ids, id] : ids.filter((x) => x !== id)); // rollback
        alert((e as ApiError).message);
      }
      notify();
    },
    [ids],
  );

  const remove = useCallback(async (id: number) => {
    if (isLoggedIn()) {
      setIds((prev) => prev.filter((x) => x !== id));
      try {
        await api.removeFavorite(id);
      } catch {
        /* už pryč */
      }
      notify();
    } else {
      writeGuest(readGuest().filter((x) => x !== id));
      notify();
    }
  }, []);

  const clearAll = useCallback(async () => {
    if (isLoggedIn()) {
      const current = await loadIds().catch(() => []);
      await Promise.all(current.map((id) => api.removeFavorite(id).catch(() => undefined)));
      setIds([]);
      notify();
    } else {
      writeGuest([]);
      notify();
    }
  }, []);

  return { ids, toggle, remove, clearAll };
}

export function useFav(id: number): [boolean, () => Promise<void>] {
  const { ids, toggle } = useFavIds();
  return [ids.includes(id), () => toggle(id)];
}