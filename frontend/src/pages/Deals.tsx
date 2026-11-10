import {
  useCallback, useEffect, useState, type ReactElement,
} from "react";
import { Link } from "react-router-dom";
import { api, fmtDateDay, fmtPrice } from "../api";
import { useAuth } from "../auth";
import { useAlerts } from "../alerts";
import type { DealOverview, SaleTransaction, WithdrawalRights } from "../types";
import { ErrorBox } from "./Auth";
import { Stars } from "../components/widgets";

type Role = "all" | "buyer" | "seller";

const STATUS_LABEL: Record<SaleTransaction["status"], string> = {
  reserved: "Rezervováno",
  completed: "Dokončeno",
  cancelled: "Zrušeno",
  withdrawn: "Odstoupeno od smlouvy",
};

const STATUS_STYLE: Record<SaleTransaction["status"], string> = {
  reserved: "bg-blue-50 border-blue-200 text-navydeep",
  completed: "bg-green-50 border-green-200 text-green-800",
  cancelled: "bg-gray-100 border-gray-200 text-gray-600",
  withdrawn: "bg-amber-50 border-amber-200 text-amber-800",
};

function Stat({ label, value, hint }: { label: string; value: string; hint?: string }): ReactElement {
  return (
    <div className="border border-gray-200 rounded-lg px-3 py-2 bg-white">
      <p className="text-xs text-gray-500">{label}</p>
      <p className="text-lg font-bold text-navydeep">{value}</p>
      {hint && <p className="text-[11px] text-gray-400">{hint}</p>}
    </div>
  );
}

/**
 * Přehled obchodů: souhrn prodejů a nákupů včetně získaného hodnocení,
 * stavu jednotlivých rezervací, dokončení obchodu, hodnocení prodejce
 * a práva spotřebitele na odstoupení od smlouvy.
 */
export default function Deals(): ReactElement {
  const { user } = useAuth();
  const { refresh } = useAlerts();
  const [role, setRole] = useState<Role>("all");
  const [items, setItems] = useState<SaleTransaction[]>([]);
  const [overview, setOverview] = useState<DealOverview | null>(null);
  const [rights, setRights] = useState<Record<number, WithdrawalRights>>({});
  const [reviewFor, setReviewFor] = useState<number | null>(null);
  const [reviewStars, setReviewStars] = useState(5);
  const [reviewText, setReviewText] = useState("");
  const [msg, setMsg] = useState("");
  const [err, setErr] = useState("");
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [tx, ov] = await Promise.all([api.myTransactions(role), api.dealOverview()]);
      setItems(tx.data ?? []);
      setOverview(ov);

      // Právo odstoupení stojí za dotaz na každou dokončenou koupi od obchodníka.
      const candidates = (tx.data ?? []).filter(
        (t) => t.status === "completed" && t.role === "buyer" && t.sellerType === "trader",
      );
      const entries = await Promise.all(
        candidates.map(async (t) => {
          try {
            return [t.id, await api.withdrawalRights(t.id)] as const;
          } catch {
            return [t.id, null] as const;
          }
        }),
      );
      const map: Record<number, WithdrawalRights> = {};
      for (const [id, r] of entries) if (r) map[id] = r;
      setRights(map);
    } catch (e) {
      setErr((e as Error).message);
    } finally {
      setLoading(false);
    }
  }, [role]);

  useEffect(() => {
    if (!user) return;
    void load();
  }, [user, load]);

  if (!user) {
    return (
      <div className="container mx-auto px-4 py-10 max-w-3xl">
        <p className="text-gray-700">
          Přihlaste se, abyste viděli své obchody.{" "}
          <Link to="/prihlaseni" className="text-brand font-semibold">
            Přihlásit
          </Link>
        </p>
      </div>
    );
  }

  const act = async (fn: () => Promise<unknown>, success: string) => {
    setErr("");
    setMsg("");
    try {
      await fn();
      setMsg(success);
      await load();
      await refresh();
    } catch (e) {
      setErr((e as Error).message);
    }
  };

  const submitReview = async (id: number) => {
    if (!reviewText.trim()) {
      setErr("Text recenze je povinný.");
      return;
    }
    await act(() => api.reviewTransaction(id, reviewStars, reviewText.trim()), "Recenze odeslána.");
    setReviewFor(null);
    setReviewText("");
    setReviewStars(5);
  };

  const withdraw = async (id: number) => {
    const reason = prompt("Důvod odstoupení od smlouvy (nepovinné):") ?? undefined;
    if (reason === null) return;
    await act(
      () => api.withdrawTransaction(id, reason || undefined),
      "Odstoupení zaznamenáno. Prodejce byl informován.",
    );
  };

  const seller = overview?.asSeller;
  const buyer = overview?.asBuyer;

  return (
    <div className="container mx-auto px-4 py-10 max-w-4xl flex flex-col gap-6">
      <h1 className="text-3xl font-bold text-navydeep">Moje obchody</h1>
      {msg && <p className="bg-green-50 border border-green-200 text-green-800 rounded px-3 py-2 text-sm">{msg}</p>}
      <ErrorBox message={err} />

      {overview && (
        <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
          <Stat
            label="Prodeje (dokončeno)"
            value={String(seller?.completed ?? 0)}
            hint={`obrat ${fmtPrice(seller?.revenueCzk ?? 0)}`}
          />
          <Stat
            label="Získané hodnocení"
            value={seller?.ratingCount ? `${seller.ratingAverage.toFixed(1)} ★` : "—"}
            hint={seller?.ratingCount ? `${seller.ratingCount}× hodnocení` : "zatím bez hodnocení"}
          />
          <Stat label="Čeká na dokončení" value={String(seller?.reserved ?? 0)} hint="rezervace v prodeji" />
          <Stat label="Nákupy" value={String(buyer?.completed ?? 0)} hint={`${buyer?.reserved ?? 0} v rezervaci`} />
        </div>
      )}

      {seller && (seller.completed > 0 || seller.ratingCount > 0) && (
        <p className="text-sm text-gray-600 flex items-center gap-2">
          Vaše hodnocení prodejce: <Stars rating={seller.ratingAverage} />
          <span className="text-gray-500">({seller.ratingCount}×)</span>
          {seller.withdrawn > 0 && (
            <span className="text-gray-400">· {seller.withdrawn}× odstoupeno spotřebitelem</span>
          )}
        </p>
      )}

      <div className="flex gap-2">
        {(
          [
            ["all", "Vše"],
            ["buyer", "Nákupy"],
            ["seller", "Prodeje"],
          ] as const
        ).map(([r, label]) => (
          <button
            key={r}
            onClick={() => setRole(r)}
            className={`rounded px-4 py-1.5 text-sm font-semibold border ${
              role === r
                ? "bg-navy text-white border-navy"
                : "border-gray-300 text-gray-700 hover:border-navy"
            }`}
          >
            {label}
          </button>
        ))}
      </div>

      {loading && <p className="text-gray-500">Načítám…</p>}

      {!loading && !items.length && (
        <div className="bg-white border border-gray-200 rounded-xl p-8 text-center">
          <p className="font-semibold">Zatím tu žádné obchody</p>
          <p className="text-sm text-gray-500 mt-1 mb-4">
            Rezervujte inzerát tlačítkem „Koupit nyní“ nebo nabídněte prodejci svou cenu.
          </p>
          <Link to="/hledat" className="inline-block bg-navy hover:bg-navydeep text-white rounded px-6 py-2 text-sm font-semibold">
            Procházet inzeráty
          </Link>
        </div>
      )}

      {items.map((t) => {
        const right = rights[t.id];
        return (
          <div key={t.id} className="bg-white border border-gray-200 rounded-xl p-4 flex flex-col gap-3">
            <div className="flex flex-wrap items-start justify-between gap-2">
              <div className="min-w-0">
                <Link to={`/inzerat/${t.listingId}`} className="font-semibold hover:text-navy truncate block">
                  {t.listingTitle || `inzerát #${t.listingId}`}
                </Link>
                <p className="text-xs text-gray-500">
                  {t.role === "buyer" ? "Nákup" : "Prodej"} · {fmtPrice(t.priceCzk)}
                  {t.completedAt && ` · dokončeno ${fmtDateDay(t.completedAt)}`}
                </p>
              </div>
              <span className={`text-xs font-semibold rounded px-2 py-0.5 border shrink-0 ${STATUS_STYLE[t.status]}`}>
                {STATUS_LABEL[t.status]}
              </span>
            </div>

            <div className="flex flex-wrap gap-2">
              {t.role === "buyer" && t.status === "reserved" && (
                <>
                  <button
                    onClick={() => void act(() => api.completeTransaction(t.id), "Obchod dokončen.")}
                    className="bg-brand hover:bg-branddark text-white rounded px-3 py-1.5 text-sm font-semibold"
                  >
                    Potvrdit předání
                  </button>
                  <button
                    onClick={() => void act(() => api.cancelTransaction(t.id), "Rezervace zrušena.")}
                    className="border border-gray-300 rounded px-3 py-1.5 text-sm font-medium hover:border-brand"
                  >
                    Zrušit rezervaci
                  </button>
                </>
              )}

              {t.role === "seller" && t.status === "reserved" && (
                <>
                  <button
                    onClick={() => void act(() => api.completeTransaction(t.id), "Obchod dokončen.")}
                    className="bg-brand hover:bg-branddark text-white rounded px-3 py-1.5 text-sm font-semibold"
                  >
                    Potvrdit prodej
                  </button>
                  <button
                    onClick={() => void act(() => api.cancelTransaction(t.id), "Rezervace zrušena.")}
                    className="border border-gray-300 rounded px-3 py-1.5 text-sm font-medium hover:border-brand"
                  >
                    Zrušit rezervaci
                  </button>
                </>
              )}

              {t.role === "buyer" && t.status === "completed" && !t.hasReview && (
                <button
                  onClick={() => setReviewFor(reviewFor === t.id ? null : t.id)}
                  className="border border-navy text-navy rounded px-3 py-1.5 text-sm font-semibold hover:bg-navy hover:text-white"
                >
                  ⭐ Ohnotit prodejce
                </button>
              )}

              {t.hasReview && (
                <span className="text-xs text-green-700 self-center">✓ obchod ohodnocen</span>
              )}

              {right?.eligible && (
                <button
                  onClick={() => void withdraw(t.id)}
                  className="border border-amber-500 text-amber-800 rounded px-3 py-1.5 text-sm font-semibold hover:bg-amber-500 hover:text-white"
                  title={right.explanationCs}
                >
                  Odstoupit od smlouvy (do {fmtDateDay(right.deadline)})
                </button>
              )}
            </div>

            {right?.applies && !right.eligible && t.status === "completed" && (
              <p className="text-xs text-gray-500">{right.explanationCs}</p>
            )}

            {reviewFor === t.id && (
              <div className="border border-gray-200 rounded-lg p-3 bg-gray-50 flex flex-col gap-2">
                <p className="text-sm font-semibold">Ohodnotit prodejce</p>
                <div className="flex gap-1">
                  {[1, 2, 3, 4, 5].map((n) => (
                    <button
                      key={n}
                      type="button"
                      onClick={() => setReviewStars(n)}
                      aria-label={`${n} hvězdiček`}
                      className={`text-2xl leading-none ${n <= reviewStars ? "text-amber-500" : "text-gray-300"}`}
                    >
                      ★
                    </button>
                  ))}
                </div>
                <textarea
                  value={reviewText}
                  onChange={(e) => setReviewText(e.target.value)}
                  rows={3}
                  maxLength={2000}
                  placeholder="Jaký byl obchod? (rychlost, férovost, stav zboží)"
                  className="w-full border border-gray-300 rounded px-3 py-2 text-sm"
                />
                <div className="flex gap-2">
                  <button
                    onClick={() => void submitReview(t.id)}
                    className="bg-navy hover:bg-navydeep text-white rounded px-4 py-1.5 text-sm font-semibold"
                  >
                    Odeslat recenzi
                  </button>
                  <button
                    onClick={() => setReviewFor(null)}
                    className="border border-gray-300 rounded px-4 py-1.5 text-sm"
                  >
                    Zrušit
                  </button>
                </div>
              </div>
            )}
          </div>
        );
      })}
    </div>
  );
}