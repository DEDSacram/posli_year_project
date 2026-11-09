import {
  useCallback, useEffect, useState, type ReactElement,
} from "react";
import { Link, useNavigate } from "react-router-dom";
import { api } from "../api";
import { useAuth } from "../auth";
import type { Listing } from "../types";
import ListingCard from "../components/ListingCard";
import QuickView from "../components/QuickView";
import { bustInterest } from "../components/interest";
import { readRecent } from "../components/recent";

export const INTEREST_TEMPLATE =
  "Dobrý den, mám o vaši nabídku zájem. Je ještě dostupná?";

export const reserveMessage = (item: Listing, txId: number): string =>
  `✅ Rezervuji „${item.title}“ za ${Number(item.priceCzk).toLocaleString("cs-CZ")} Kč ` +
  `(rezervace #${txId}). Domluvme si předání.`;

export const offerMessage = (item: Listing, price: number): string =>
  `💰 Nabízím ${Number(price).toLocaleString("cs-CZ")} Kč za „${item.title}“. Platí?`;

/**
 * „Koupit nyní“ — rezervuje inzerát a otevře chat s předvyplněnou zprávou,
 * aby prodejce hned věděl, o co jde.
 */
export function useBuy(): (item: Listing) => Promise<void> {
  const { user } = useAuth();
  const nav = useNavigate();

  return useCallback(
    async (item: Listing) => {
      if (!user) {
        nav("/prihlaseni");
        return;
      }
      try {
        const tx = await api.reserve(item.id);
        const conv = await api.conversation(item.id);
        bustInterest(item.id);
        try {
          await api.sendMessage(conv.id, reserveMessage(item, tx.id));
        } catch {
          /* rezervace platí i bez zprávy */
        }
        nav(`/chat/${conv.id}`);
      } catch (e) {
        alert((e as Error).message);
      }
    },
    [user, nav],
  );
}

/** Rychlá reakce — jedním klikem otevře konverzaci s předvyplněnou zprávou zájmu. */
export function useContact(): (item: Listing) => Promise<void> {
  const { user } = useAuth();
  const nav = useNavigate();

  return useCallback(
    async (item: Listing) => {
      if (!user) {
        nav("/prihlaseni");
        return;
      }
      try {
        const conv = await api.conversation(item.id);
        bustInterest(item.id);
        try {
          await api.sendMessage(conv.id, INTEREST_TEMPLATE);
        } catch {
          /* konverzace existuje — stačí ji otevřít */
        }
        nav(`/chat/${conv.id}`);
      } catch (e) {
        alert((e as Error).message);
      }
    },
    [user, nav],
  );
}

export type QuickViewHandle = [(item: Listing) => void, ReactElement | null];

/** Rychlý náhled bez odchodu z feedu (sdílený stav pro Home + Hledat + Oblíbené). */
export function useQuickView(contact?: (item: Listing) => Promise<void>): QuickViewHandle {
  const [quick, setQuick] = useState<Listing | null>(null);
  const buy = useBuy();
  const fallbackContact = useContact();
  const contactFn = contact ?? fallbackContact;

  const view = quick ? (
    <QuickView
      item={quick}
      onClose={() => setQuick(null)}
      onBuy={(it) => {
        setQuick(null);
        void buy(it);
      }}
      onContact={(it) => {
        setQuick(null);
        void contactFn(it);
      }}
    />
  ) : null;

  return [setQuick, view];
}

export default function Home(): ReactElement {
  const [items, setItems] = useState<Listing[]>([]);
  const [cursor, setCursor] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [recent, setRecent] = useState<Listing[]>([]);
  const buy = useBuy();
  const contact = useContact();
  const [setQuick, quickView] = useQuickView(contact);

  const load = useCallback(async (from: number | null) => {
    setLoading(true);
    setError("");
    try {
      const page = await api.feed(from, 12);
      setItems((prev) => (from ? [...prev, ...(page.data ?? [])] : (page.data ?? [])));
      setCursor(page.nextCursor ? Number(page.nextCursor) : null);
    } catch {
      setError("Nepodařilo se načíst inzeráty.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load(null);
  }, [load]);

  useEffect(() => {
    // Naposledy zobrazené (jen toto zařízení) — dotáhneme nadpisy.
    const ids = readRecent();
    if (!ids.length) return;
    void Promise.all(ids.map((id) => api.listing(id).catch(() => null)))
      .then((rows) => setRecent(rows.filter((r): r is Listing => r !== null)));
  }, []);

  return (
    <div className="container mx-auto px-4 lg:px-8 py-8">
      {error && <p className="text-brand mb-4">{error}</p>}

      {!!recent.length && (
        <div className="mb-8">
          <h2 className="text-sm font-semibold text-gray-500 mb-2">Naposledy zobrazené</h2>
          <div className="flex gap-2 overflow-x-auto pb-1">
            {recent.map((r) => (
              <Link
                key={r.id}
                to={`/inzerat/${r.id}`}
                className="shrink-0 max-w-[220px] truncate bg-white border border-gray-200 rounded-full px-3 py-1.5 text-sm hover:border-navy"
              >
                {r.title}
              </Link>
            ))}
          </div>
        </div>
      )}

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-x-8 gap-y-12">
        {items.map((it) => (
          <ListingCard
            key={it.id}
            item={it}
            onBuy={(i) => void buy(i)}
            onContact={(i) => void contact(i)}
            onQuick={setQuick}
          />
        ))}
      </div>

      {loading && <p className="mt-8 text-gray-500">Načítám…</p>}

      {!loading && cursor !== null && (
        <div className="mt-8 text-center">
          <button
            onClick={() => void load(cursor)}
            className="bg-navy hover:bg-navydeep text-white rounded px-6 py-2 text-sm font-semibold"
          >
            Načíst další
          </button>
        </div>
      )}

      {!loading && !items.length && !error && (
        <p className="text-gray-500">Zatím tu nejsou žádné inzeráty.</p>
      )}
      {quickView}
    </div>
  );
}