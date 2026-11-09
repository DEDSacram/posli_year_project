import { useCallback, useEffect, useState, type ReactElement } from "react";
import { Link, useParams } from "react-router-dom";
import { api, fmtDate } from "../api";
import { useAuth } from "../auth";
import type { Review, UserProfile } from "../types";
import {
  SellerBadges, SellerRating, Stars, VerifiedReview,
} from "../components/widgets";

/** Veřejný profil prodejce: odznaky, hodnocení, recenze a možnost odpovědi či námitky. */
export default function Profile(): ReactElement {
  const { id } = useParams();
  const { user } = useAuth();
  const sellerId = Number(id);
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [reviews, setReviews] = useState<Review[]>([]);
  const [responseDrafts, setResponseDrafts] = useState<Record<number, string>>({});
  const [disputeFor, setDisputeFor] = useState<number | null>(null);
  const [disputeReason, setDisputeReason] = useState("");

  const load = useCallback(async () => {
    try {
      const p = await api.profile(sellerId);
      setProfile(p);
    } catch {
      setProfile(null);
    }
    try {
      const rev = await api.reviewsOfUser(sellerId);
      setReviews(rev.data ?? []);
    } catch {
      setReviews([]);
    }
  }, [sellerId]);

  useEffect(() => {
    void load();
  }, [load]);

  if (!profile) {
    return (
      <div className="container mx-auto px-4 py-10">
        <p className="text-gray-500">Načítám…</p>
      </div>
    );
  }

  const sendDispute = async (reviewId: number) => {
    if (disputeReason.trim().length < 10) {
      alert("Napište důvod námitky (alespoň 10 znaků).");
      return;
    }
    try {
      await api.disputeReview(reviewId, disputeReason.trim());
      setDisputeFor(null);
      setDisputeReason("");
      await load();
      alert("Námitka odeslána. Administrátor ji posoudí.");
    } catch (e) {
      alert((e as Error).message);
    }
  };

  return (
    <div className="container mx-auto px-4 py-10 max-w-3xl">
      <h1 className="text-3xl font-bold text-navydeep">{profile.displayName}</h1>

      <div className="mt-2 flex items-center gap-3 flex-wrap">
        <SellerRating userId={sellerId} showCount />
        <SellerBadges userId={sellerId} />
      </div>

      <p className="text-xs text-gray-500 mt-2">
        Na bazaru od {fmtDate(profile.createdAt)} · člen od{" "}
        {new Date(profile.createdAt).toLocaleDateString("cs-CZ")}
      </p>

      <h2 className="text-xl font-bold text-navydeep mt-8 mb-3">Recenze</h2>
      <div className="flex flex-col gap-3">
        {reviews.map((r) => (
          <div key={r.id}>
            <VerifiedReview review={r} />

            {user && user.id === r.revieweeId && !r.responseText && (
              <div className="flex gap-2 mt-2 ml-4">
                <input
                  value={responseDrafts[r.id] ?? ""}
                  onChange={(e) => setResponseDrafts((d) => ({ ...d, [r.id]: e.target.value }))}
                  placeholder="Vaše odpověď (1×)…"
                  className="flex-1 border border-gray-300 rounded px-3 py-1.5 text-sm"
                />
                <button
                  onClick={() => {
                    void api
                      .respondToReview(r.id, (responseDrafts[r.id] ?? "").trim())
                      .then(load)
                      .catch((e: Error) => alert(e.message));
                  }}
                  className="border border-navy text-navy rounded px-3 py-1.5 text-sm font-semibold hover:bg-navy hover:text-white"
                >
                  Odpovědět
                </button>
              </div>
            )}

            {user && user.id === r.revieweeId && !r.disputeStatus && (
              <div className="ml-4 mt-2">
                <button
                  onClick={() => setDisputeFor(disputeFor === r.id ? null : r.id)}
                  className="text-xs text-gray-500 hover:text-brand font-medium"
                >
                  ⚖️ Ohradit se proti tomuto hodnocení
                </button>
                {disputeFor === r.id && (
                  <div className="mt-2 border border-gray-200 rounded-lg p-3 bg-gray-50 flex flex-col gap-2">
                    <p className="text-xs text-gray-600">
                      Námitku posoudí administrátor. Po jejím potvrzení se recenze odstraní a nepočítá
                      se do vašeho hodnocení.
                    </p>
                    <textarea
                      value={disputeReason}
                      onChange={(e) => setDisputeReason(e.target.value)}
                      rows={3}
                      maxLength={1000}
                      placeholder="Proč považujete hodnocení za nespravedlivé? (min. 10 znaků)"
                      className="w-full border border-gray-300 rounded px-3 py-2 text-sm"
                    />
                    <button
                      onClick={() => void sendDispute(r.id)}
                      className="self-start bg-navy hover:bg-navydeep text-white rounded px-4 py-1.5 text-sm font-semibold"
                    >
                      Podat námitku
                    </button>
                  </div>
                )}
              </div>
            )}

            {r.disputeStatus === "upheld" && (
              <p className="text-xs text-gray-400 ml-4 mt-1">
                Námitka byla potvrzena — {fmtDate(r.createdAt)}
              </p>
            )}
          </div>
        ))}
        {!reviews.length && <p className="text-sm text-gray-500">Zatím žádné viditelné recenze.</p>}
      </div>

      {user?.id === sellerId && (
        <p className="mt-6 text-sm text-gray-600 flex items-center gap-2">
          Vaše hodnocení: <Stars rating={0} />
          <Link to="/obchody" className="text-brand font-semibold hover:underline">
            Přehled obchodů a získané hodnocení →
          </Link>
        </p>
      )}

      <p className="mt-6 text-xs text-gray-400">
        Prodejce zveřejňuje jen město, nikoli přesnou adresu. Přesnou polohu lze zobrazit pouze
        s jeho souhlasem při vložení inzerátu.
      </p>
    </div>
  );
}