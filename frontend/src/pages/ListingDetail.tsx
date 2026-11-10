import {
  useCallback, useEffect, useState, type ReactElement,
} from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { api, fmtPrice, imageUrl } from "../api";
import { useAuth } from "../auth";
import type { Listing, Review, SaleTransaction } from "../types";
import { Placeholder } from "../components/ListingCard";
import { useFav } from "../components/favs";
import ListingMap from "../components/ListingMap";
import { InterestBadge, bustInterest, useInterestCount } from "../components/interest";
import { pushRecent } from "../components/recent";
import { copyLink, lensSearchByUrl, lensUploadUrl, whatsappShareUrl } from "../components/share";
import {
  SellerBadges, SellerRating, VerifiedReview, WithdrawalBanner,
} from "../components/widgets";
import { INTEREST_TEMPLATE, offerMessage, reserveMessage } from "./Home";

export default function ListingDetail(): ReactElement {
  const { id } = useParams();
  const { user } = useAuth();
  const nav = useNavigate();
  const [item, setItem] = useState<Listing | null>(null);
  const [active, setActive] = useState(0);
  const [reviews, setReviews] = useState<Review[]>([]);
  const [error, setError] = useState("");
  const [fav, flip] = useFav(Number(id));
  const [reportReason, setReportReason] = useState("");
  const [reviewText, setReviewText] = useState("");
  const [reviewRating, setReviewRating] = useState(5);
  const [eligibleTx, setEligibleTx] = useState<SaleTransaction[]>([]);
  const [chosenTx, setChosenTx] = useState("");
  const [responseDrafts, setResponseDrafts] = useState<Record<number, string>>({});
  const [disputeFor, setDisputeFor] = useState<number | null>(null);
  const [disputeReason, setDisputeReason] = useState("");
  const [lightbox, setLightbox] = useState(false);
  const [copied, setCopied] = useState(false);
  const [interestBusy, setInterestBusy] = useState(false);
  const [offerPrice, setOfferPrice] = useState("");
  const [offerBusy, setOfferBusy] = useState(false);
  const interest = useInterestCount(item?.id ?? null);

  const load = useCallback(async () => {
    try {
      const data = await api.listing(Number(id));
      setItem(data);
      pushRecent(data.id);
      const rev = await api
        .reviewsOfUser(data.sellerId)
        .catch(() => ({ data: [] as Review[], nextCursor: null }));
      setReviews(rev.data ?? []);
    } catch (e) {
      setError((e as Error).message);
    }
  }, [id]);

  /** Moje dokončené nákupy tohoto inzerátu bez recenze — výběr místo ručního ID. */
  const loadEligible = useCallback(async (listingId: number, buyerId: number) => {
    try {
      const d = await api.myTransactions("buyer");
      const list = (d.data ?? []).filter(
        (t) => t.buyerId === buyerId && t.listingId === listingId && t.status === "completed" && !t.hasReview,
      );
      setEligibleTx(list);
      if (list.length === 1) setChosenTx(String(list[0].id));
    } catch {
      /* nepřihlášený nebo chyba — sekce se skryje */
    }
  }, []);

  useEffect(() => {
    void load();
    setActive(0);
    setLightbox(false);
  }, [load]);

  useEffect(() => {
    if (user && item && user.id !== item.sellerId) void loadEligible(item.id, user.id);
    else {
      setEligibleTx([]);
      setChosenTx("");
    }
  }, [user, item, loadEligible]);

  // Galerie: šipky a Escape ve fullscreen náhledu (nutné před early-returny).
  useEffect(() => {
    if (!lightbox) return;
    const count = (item?.images ?? []).length;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") setLightbox(false);
      if (e.key === "ArrowRight") setActive((a) => (a + 1) % Math.max(1, count));
      if (e.key === "ArrowLeft") setActive((a) => (a - 1 + Math.max(1, count)) % Math.max(1, count));
    };
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [lightbox, item]);

  if (error) {
    return (
      <div className="container mx-auto px-4 py-10">
        <p className="text-brand">{error}</p>
      </div>
    );
  }
  if (!item) {
    return (
      <div className="container mx-auto px-4 py-10">
        <p className="text-gray-500">Načítám…</p>
      </div>
    );
  }

  const images = item.images ?? [];
  const isOwner = !!user && user.id === item.sellerId;

  const guard = (): boolean => {
    if (user) return true;
    nav("/prihlaseni");
    return false;
  };

  const buy = async () => {
    if (!guard()) return;
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
  };

  const contact = async () => {
    if (!guard()) return;
    try {
      const conv = await api.conversation(item.id);
      bustInterest(item.id);
      nav(`/chat/${conv.id}`);
    } catch (e) {
      alert((e as Error).message);
    }
  };

  /** Rychlá reakce jedním klikem: konverzace + předvyplněná zpráva zájmu. */
  const quickInterest = async () => {
    if (!guard()) return;
    setInterestBusy(true);
    try {
      const conv = await api.conversation(item.id);
      bustInterest(item.id);
      try {
        await api.sendMessage(conv.id, INTEREST_TEMPLATE);
      } catch {
        /* konverzace už existuje */
      }
      nav(`/chat/${conv.id}`);
    } catch (e) {
      alert((e as Error).message);
    } finally {
      setInterestBusy(false);
    }
  };

  /** Nabídnout cenu: konverzace + zpráva s nabízenou částkou. */
  const makeOffer = async () => {
    if (!guard()) return;
    const price = Math.round(Number(offerPrice.replace(/\s/g, "")));
    if (!Number.isFinite(price) || price < 1) {
      alert("Zadejte nabízenou cenu v Kč.");
      return;
    }
    setOfferBusy(true);
    try {
      const conv = await api.conversation(item.id);
      bustInterest(item.id);
      await api.sendMessage(conv.id, offerMessage(item, price));
      nav(`/chat/${conv.id}`);
    } catch (e) {
      alert((e as Error).message);
    } finally {
      setOfferBusy(false);
    }
  };

  const share = async () => {
    const ok = await copyLink(window.location.href);
    setCopied(ok);
    setTimeout(() => setCopied(false), 2000);
  };

  const remove = async () => {
    if (!confirm("Opravdu smazat inzerát?")) return;
    try {
      await api.deleteListing(item.id);
      nav("/");
    } catch (e) {
      alert((e as Error).message);
    }
  };

  const report = async () => {
    if (!reportReason.trim()) {
      alert("Napište důvod hlášení.");
      return;
    }
    try {
      await api.report("listing", item.id, reportReason.trim());
      setReportReason("");
      alert("Hlášení odesláno. Děkujeme.");
    } catch (e) {
      alert((e as Error).message);
    }
  };

  const sendReview = async () => {
    if (!chosenTx) {
      alert("Vyberte nákup.");
      return;
    }
    if (!reviewText.trim()) {
      alert("Napište text recenze.");
      return;
    }
    try {
      await api.reviewTransaction(Number(chosenTx), reviewRating, reviewText.trim());
      setReviewText("");
      await load();
      if (user) await loadEligible(item.id, user.id);
    } catch (e) {
      alert((e as Error).message);
    }
  };

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
    <div className="bg-creamdark">
      <div className="container mx-auto px-4 lg:px-8 py-10 max-w-6xl">
        <div className="flex flex-col md:flex-row gap-8 lg:gap-12">
          <div className="w-full md:w-[55%] lg:w-[60%] flex flex-col gap-4">
            <div className="w-full bg-[#ebe7df] rounded-xl overflow-hidden aspect-[4/3]">
              {images.length ? (
                <button
                  onClick={() => setLightbox(true)}
                  className="w-full h-full block"
                  title="Zvětšit (šipky / Esc)"
                >
                  <img
                    src={imageUrl(item.id, images[active] ?? images[0])}
                    alt={item.title}
                    className="w-full h-full object-cover"
                  />
                </button>
              ) : (
                <Placeholder />
              )}
            </div>
            {!!images.length && (
              <div className="flex gap-4">
                {images.slice(0, 4).map((t, i) => (
                  <button
                    key={t.id}
                    onClick={() => setActive(i)}
                    aria-label={`Fotka ${i + 1}`}
                    className={`w-1/4 aspect-[4/3] bg-[#ebe7df] rounded-lg overflow-hidden border-2 ${
                      i === active ? "border-navydeep" : "border-transparent hover:border-gray-400"
                    }`}
                  >
                    <img src={imageUrl(item.id, t)} alt="" className="w-full h-full object-cover" />
                  </button>
                ))}
              </div>
            )}
          </div>

          <div className="w-full md:w-[45%] lg:w-[40%] flex flex-col min-w-0 pt-2 md:pt-4">
            <h1 className="text-3xl md:text-4xl font-bold text-navydeep leading-tight [overflow-wrap:anywhere] mb-2">
              {item.title}
            </h1>
            <div className="mb-3">
              <InterestBadge count={interest} />
            </div>
            <div className="flex flex-col gap-1 text-[17px] text-gray-800 mb-6">
              <p>Stav: {item.condition}</p>
              <p>Kategorie: {item.category}</p>
              <p>Lokalita: {item.city}</p>
            </div>
            <div className="text-5xl font-bold text-navydeep mb-8">{fmtPrice(item.priceCzk)}</div>

            {!isOwner ? (
              <div className="flex flex-col gap-3 w-full mb-4">
                <button
                  onClick={() => void buy()}
                  className="w-full bg-brand hover:bg-branddark text-white rounded-lg py-3.5 text-lg font-semibold shadow-sm"
                >
                  Koupit nyní
                </button>
                <button
                  onClick={() => void quickInterest()}
                  disabled={interestBusy}
                  className="w-full bg-navy hover:bg-navydeep text-white rounded-lg py-2.5 font-semibold disabled:opacity-50"
                >
                  {interestBusy ? "Otevírám…" : "💬 Mám zájem — napsat prodejci"}
                </button>
                <button
                  onClick={() => void flip()}
                  className="w-full bg-[#a7a9ac] hover:bg-[#949699] text-gray-900 rounded-lg py-3.5 text-lg font-medium shadow-sm"
                >
                  {fav ? "✓ V oblíbených" : "Přidat do oblíbených"}
                </button>
                <button
                  onClick={() => void contact()}
                  className="w-full border border-navy text-navy rounded-lg py-2.5 font-semibold hover:bg-navy hover:text-white"
                >
                  Napsat prodejci
                </button>
                <div className="flex gap-2">
                  <input
                    value={offerPrice}
                    onChange={(e) => setOfferPrice(e.target.value)}
                    onKeyDown={(e) => {
                      if (e.key === "Enter") {
                        e.preventDefault();
                        void makeOffer();
                      }
                    }}
                    type="number"
                    min={1}
                    placeholder={`Vaše nabídka (Kč, cena ${fmtPrice(item.priceCzk)})`}
                    className="flex-1 min-w-0 border border-gray-300 rounded-lg px-3 py-2.5 text-sm focus:outline-none focus:ring-1 focus:ring-navy"
                  />
                  <button
                    onClick={() => void makeOffer()}
                    disabled={offerBusy}
                    className="shrink-0 bg-navy hover:bg-navydeep text-white rounded-lg px-4 py-2.5 font-semibold disabled:opacity-50"
                  >
                    {offerBusy ? "Posílám…" : "Nabídnout cenu"}
                  </button>
                </div>
                <div className="flex flex-wrap gap-2 text-sm">
                  <button
                    onClick={() => void share()}
                    className="border border-gray-300 rounded px-3 py-1.5 hover:border-navy hover:text-navy"
                  >
                    {copied ? "✓ Odkaz zkopírován" : "🔗 Sdílet odkaz"}
                  </button>
                  <a
                    href={whatsappShareUrl(
                      `${item.title} — ${fmtPrice(item.priceCzk)} ${window.location.href}`,
                    )}
                    target="_blank"
                    rel="noreferrer"
                    className="border border-gray-300 rounded px-3 py-1.5 hover:border-navy hover:text-navy"
                  >
                    WhatsApp
                  </a>
                  <a
                    href={images.length ? lensSearchByUrl(imageUrl(item.id, images[0])) : lensUploadUrl()}
                    target="_blank"
                    rel="noreferrer"
                    title="Najít podobné přes Google Lens"
                    className="border border-gray-300 rounded px-3 py-1.5 hover:border-navy hover:text-navy"
                  >
                    🔍 Lens
                  </a>
                </div>
              </div>
            ) : (
              <div className="flex flex-col gap-3 w-full mb-8">
                <Link
                  to={`/inzerat/${item.id}/upravit`}
                  className="w-full text-center bg-navy hover:bg-navydeep text-white rounded-lg py-2.5 font-semibold"
                >
                  Upravit inzerát
                </Link>
                <Link
                  to={`/prodat?copy=${item.id}`}
                  className="w-full text-center border border-navy text-navy rounded-lg py-2.5 font-semibold hover:bg-navy hover:text-white"
                >
                  Duplikovat inzerát
                </Link>
                <button
                  onClick={() => void remove()}
                  className="w-full bg-brand hover:bg-branddark text-white rounded-lg py-2.5 font-semibold"
                >
                  Smazat inzerát
                </button>
              </div>
            )}

            <div className="text-lg text-gray-800 mb-2 flex items-center gap-2 flex-wrap">
              <Link to={`/profil/${item.sellerId}`} className="hover:text-navy">
                Prodejce: {item.sellerName || `#${item.sellerId}`}
              </Link>
              <SellerRating userId={item.sellerId} showCount />
              <SellerBadges userId={item.sellerId} />
            </div>
            <div className="mb-6">
              <WithdrawalBanner listingId={item.id} />
            </div>
            <div className="text-[16px] text-gray-800 leading-relaxed pr-4 [overflow-wrap:anywhere]">
              <p>{item.description}</p>
            </div>
          </div>
        </div>

        <div className="mt-10">
          <h2 className="text-xl font-bold text-navydeep mb-3">Poloha nabídky</h2>
          <ListingMap lat={item.lat} lon={item.lon} title={item.title} />
        </div>

        {lightbox && images.length > 0 && (
          <div
            className="fixed inset-0 z-[60] flex items-center justify-center bg-black/85 p-4"
            role="dialog"
            aria-modal="true"
            aria-label="Zvětšená fotka"
          >
            <button
              onClick={() => setLightbox(false)}
              aria-label="Zavřít"
              className="absolute top-4 right-4 w-10 h-10 rounded-full bg-white/20 hover:bg-brand text-white text-xl font-bold"
            >
              ✕
            </button>
            {images.length > 1 && (
              <button
                onClick={() => setActive((a) => (a - 1 + images.length) % images.length)}
                aria-label="Předchozí"
                className="absolute left-3 w-10 h-10 rounded-full bg-white/20 hover:bg-brand text-white text-xl font-bold"
              >
                ‹
              </button>
            )}
            <img
              src={imageUrl(item.id, images[active] ?? images[0])}
              alt={item.title}
              className="max-h-[85vh] max-w-[90vw] object-contain rounded"
            />
            {images.length > 1 && (
              <button
                onClick={() => setActive((a) => (a + 1) % images.length)}
                aria-label="Další"
                className="absolute right-3 w-10 h-10 rounded-full bg-white/20 hover:bg-brand text-white text-xl font-bold"
              >
                ›
              </button>
            )}
            <p className="absolute bottom-4 text-white/70 text-sm">
              {active + 1} / {images.length} · šipky / Esc
            </p>
          </div>
        )}

        <div className="mt-10 grid md:grid-cols-2 gap-8">
          <div>
            <h2 className="text-xl font-bold text-navydeep mb-3">Recenze prodejce</h2>
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

                  {/* Námitka proti hodnocení — prodejce může napadnout jen jednou a bez otevřené námitky. */}
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
                            Námitku posoudí administrátor. Nemá vliv na vaše hodnocení pro ostatní
                            kupce, dokud ji nevyřeší.
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
                </div>
              ))}
              {!reviews.length && <p className="text-sm text-gray-500">Zatím žádné recenze.</p>}
            </div>
          </div>

          <div>
            <h2 className="text-xl font-bold text-navydeep mb-3">Nahlásit inzerát</h2>
            <div className="flex gap-2">
              <input
                value={reportReason}
                onChange={(e) => setReportReason(e.target.value)}
                placeholder="Důvod…"
                className="flex-1 border border-gray-300 rounded px-3 py-2 text-sm"
              />
              <button
                onClick={() => void report()}
                className="bg-navy text-white rounded px-4 py-2 text-sm font-semibold"
              >
                Nahlásit
              </button>
            </div>

            {user && !isOwner && (
              <div className="mt-6">
                <h3 className="font-semibold mb-2">Napsat recenzi</h3>
                {eligibleTx.length > 0 ? (
                  <div className="flex flex-col gap-2">
                    <label className="text-sm">
                      Dokončený nákup
                      <select
                        value={chosenTx}
                        onChange={(e) => setChosenTx(e.target.value)}
                        className="ml-2 border border-gray-300 rounded px-2 py-2 text-sm"
                      >
                        {eligibleTx.map((t) => (
                          <option key={t.id} value={t.id}>
                            #{t.id} · {t.listingTitle || `inzerát #${t.listingId}`} ·{" "}
                            {fmtPrice(t.priceCzk)}
                          </option>
                        ))}
                      </select>
                    </label>
                    <div className="flex gap-2 items-center">
                      <select
                        value={reviewRating}
                        onChange={(e) => setReviewRating(Number(e.target.value))}
                        aria-label="Počet hvězd"
                        className="border border-gray-300 rounded px-2 py-2 text-sm"
                      >
                        {[5, 4, 3, 2, 1].map((n) => (
                          <option key={n} value={n}>
                            {n} ★
                          </option>
                        ))}
                      </select>
                      <input
                        value={reviewText}
                        onChange={(e) => setReviewText(e.target.value)}
                        placeholder="Text recenze…"
                        className="flex-1 border border-gray-300 rounded px-3 py-2 text-sm"
                      />
                      <button
                        onClick={() => void sendReview()}
                        className="bg-navy text-white rounded px-4 py-2 text-sm font-semibold"
                      >
                        Odeslat
                      </button>
                    </div>
                  </div>
                ) : (
                  <p className="text-sm text-gray-500">
                    Hodnotit můžete po dokončeném nákupu tohoto inzerátu (Koupit nyní → dokončit
                    transakci v sekci <Link to="/obchody" className="underline">Moje obchody</Link>).
                  </p>
                )}
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}