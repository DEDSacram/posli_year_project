import { useEffect, useState, type ReactElement } from "react";
import { Link } from "react-router-dom";
import { api, fmtPrice } from "../api";
import { useAuth } from "../auth";
import { useAlerts } from "../alerts";
import type { Listing } from "../types";
import ListingCard from "../components/ListingCard";
import { isLoggedIn, useFavIds } from "../components/favs";
import { useBuy, useContact, useQuickView } from "./Home";

/**
 * Uložené inzeráty. Přihlášený uživatel je má v profilu (vidí je na každém
 * zařízení), host si drží srdíčka v tomto prohlížeči a při přihlášení se
 * sloučí do profilu.
 */
export default function Favorites(): ReactElement {
  const { user } = useAuth();
  const { markAllNotificationsRead, refresh } = useAlerts();
  const { ids, remove, clearAll } = useFavIds();
  const [items, setItems] = useState<Listing[]>([]);
  const [missing, setMissing] = useState<number[]>([]);
  const [loading, setLoading] = useState(true);
  const buy = useBuy();
  const contact = useContact();
  const [setQuick, quickView] = useQuickView(contact);

  useEffect(() => {
    let live = true;
    const load = async () => {
      setLoading(true);
      try {
        if (isLoggedIn()) {
          // Jeden dotaz včetně pahýlů po smazaných inzerátech.
          const d = await api.myFavorites();
          if (!live) return;
          const rows = d.data ?? [];
          setItems(rows.filter((r) => !r.removed));
          setMissing(rows.filter((r) => r.removed).map((r) => r.id));
        } else if (ids.length) {
          const found: Listing[] = [];
          const gone: number[] = [];
          await Promise.all(
            ids.map(async (id) => {
              try {
                found.push(await api.listing(id));
              } catch {
                gone.push(id);
              }
            }),
          );
          if (!live) return;
          found.sort((a, b) => ids.indexOf(a.id) - ids.indexOf(b.id));
          setItems(found);
          setMissing(gone);
        } else {
          setItems([]);
          setMissing([]);
        }
      } catch {
        if (live) {
          setItems([]);
          setMissing([]);
        }
      } finally {
        if (live) setLoading(false);
      }
    };
    void load();
    return () => {
      live = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user?.id, ids.join(",")]);

  const clear = async () => {
    if (!confirm("Opravdu odebrat všechny uložené inzeráty?")) return;
    await clearAll();
    await markAllNotificationsRead();
    await refresh();
  };

  return (
    <div className="container mx-auto px-4 lg:px-8 py-8">
      <div className="flex items-center justify-between mb-2">
        <h1 className="text-3xl font-bold text-navydeep">
          Uložené inzeráty {ids.length > 0 && <span className="text-lg text-gray-500">({ids.length})</span>}
        </h1>
        {ids.length > 0 && (
          <button onClick={() => void clear()} className="text-sm text-gray-500 hover:text-brand font-medium">
            Odebrat vše
          </button>
        )}
      </div>

      <p className="text-xs text-gray-500 mb-2">
        {user
          ? "Uloženo ve vašem profilu — uvidíte je na každém zařízení po přihlášení. Když prodejce sníží cenu, dostanete o tom vědomost v oznamovacím zvonku."
          : "Nejste přihlášeni — srdíčka se drží jen v tomto prohlížeči. Po přihlášení se přesunou do profilu."}
      </p>

      {!!items.some((i) => i.priceDrop && i.priceDrop > 0) && (
        <div className="bg-green-50 border border-green-200 rounded-lg px-4 py-3 mb-6 text-sm text-green-800">
          🏷️ U {items.filter((i) => (i.priceDrop ?? 0) > 0).length} uložených inzerátů klesla cena od
          uložení. Upozornění najdete také v oznamovacím zvonku u profilu.
        </div>
      )}

      {loading && <p className="text-gray-500">Načítám…</p>}

      {!loading && !ids.length && (
        <div className="bg-white border border-gray-200 rounded-xl p-8 text-center">
          <p className="text-4xl mb-3">♡</p>
          <p className="font-semibold text-lg">Zatím tu nic nemáte</p>
          <p className="text-sm text-gray-500 mt-1 mb-4">
            U inzerátu klikněte na „Přidat do oblíbených“ a najdete ho tady.
          </p>
          <Link
            to="/hledat"
            className="inline-block bg-navy hover:bg-navydeep text-white rounded px-6 py-2 text-sm font-semibold"
          >
            Procházet inzeráty
          </Link>
        </div>
      )}

      {!loading && !!missing.length && (
        <div className="bg-amber-50 border border-amber-200 rounded-lg px-4 py-3 mb-6 text-sm">
          {missing.length}× uložený inzerát už není dostupný (prodaný/smazaný).{" "}
          <button
            onClick={() => missing.forEach((id) => void remove(id))}
            className="font-semibold text-navy hover:underline"
          >
            Odebrat nedostupné
          </button>
        </div>
      )}

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-x-8 gap-y-12">
        {items.map((it) => (
          <div key={it.id} className="relative">
            {(it.priceDrop ?? 0) > 0 && (
              <span className="absolute -top-2 left-2 z-10 bg-green-600 text-white text-xs font-bold rounded-full px-2.5 py-0.5 shadow">
                −{fmtPrice(it.priceDrop as number)} od uložení
              </span>
            )}
            <ListingCard
              item={it}
              onBuy={(i) => void buy(i)}
              onContact={(i) => void contact(i)}
              onQuick={setQuick}
            />
          </div>
        ))}
      </div>

      {!loading && !!missing.length && !items.length && (
        <p className="text-gray-500 mt-4">Žádný z uložených inzerátů už není dostupný.</p>
      )}
      {quickView}
    </div>
  );
}