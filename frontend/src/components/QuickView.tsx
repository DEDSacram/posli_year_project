import { useEffect, type ReactElement } from "react";
import { Link } from "react-router-dom";
import { fmtPrice, imageUrl } from "../api";
import type { Listing } from "../types";
import { useFav } from "./favs";
import { SellerRating } from "./widgets";

interface QuickViewProps {
  item: Listing;
  onClose: () => void;
  onBuy: (item: Listing) => void;
  onContact: (item: Listing) => void;
}

/** Rychlý náhled inzerátu bez odchodu z feedu: fotka, cena, prodejce, akce. */
export default function QuickView({
  item, onClose, onBuy, onContact,
}: QuickViewProps): ReactElement {
  const [fav, flip] = useFav(item.id);
  const images = item.images ?? [];

  useEffect(() => {
    const close = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    document.addEventListener("keydown", close);
    document.body.style.overflow = "hidden";
    return () => {
      document.removeEventListener("keydown", close);
      document.body.style.overflow = "";
    };
  }, [onClose]);

  return (
    <div
      className="fixed inset-0 z-[60] flex items-center justify-center p-4"
      role="dialog"
      aria-modal="true"
      aria-label={`Rychlý náhled ${item.title}`}
    >
      <div className="absolute inset-0 bg-black/50" onClick={onClose} />
      <div className="relative bg-white rounded-xl shadow-xl max-w-lg w-full max-h-[90vh] overflow-y-auto p-5">
        <button
          onClick={onClose}
          aria-label="Zavřít náhled"
          className="absolute top-3 right-3 w-8 h-8 rounded-full bg-black/50 hover:bg-brand text-white font-bold"
        >
          ✕
        </button>
        <div className="rounded-lg overflow-hidden bg-gray-100 aspect-[4/3] mb-3">
          {images.length ? (
            <img
              src={imageUrl(item.id, images[0])}
              alt={item.title}
              className="w-full h-full object-cover"
            />
          ) : (
            <div className="w-full h-full flex items-center justify-center text-gray-400">Bez fotky</div>
          )}
        </div>
        <h3 className="text-xl font-bold leading-snug pr-8 [overflow-wrap:anywhere]">{item.title}</h3>
        <p className="text-2xl font-bold text-navydeep mt-1">{fmtPrice(item.priceCzk)}</p>
        <p className="text-[13px] text-gray-600 mt-1">
          Stav: {item.condition} · {item.city} · Prodejce: {item.sellerName || `#${item.sellerId}`}
          <SellerRating userId={item.sellerId} />
        </p>
        <p className="text-sm text-gray-700 mt-2 line-clamp-4 [overflow-wrap:anywhere]">{item.description}</p>
        <div className="flex flex-col gap-2 mt-4">
          <div className="flex gap-2">
            <button
              onClick={() => onBuy(item)}
              className="flex-1 bg-brand hover:bg-branddark text-white rounded py-2 font-semibold text-sm"
            >
              Koupit nyní
            </button>
            <button
              onClick={() => void flip()}
              className="flex-1 bg-btngray hover:bg-btngraydark text-gray-900 rounded py-2 font-medium text-sm"
            >
              {fav ? "✓ V oblíbených" : "Přidat do oblíbených"}
            </button>
          </div>
          <div className="flex gap-2">
            <button
              onClick={() => onContact(item)}
              className="flex-1 border border-navy text-navy rounded py-2 font-semibold text-sm hover:bg-navy hover:text-white"
            >
              💬 Mám zájem
            </button>
            <Link
              to={`/inzerat/${item.id}`}
              className="flex-1 text-center border border-gray-300 rounded py-2 font-semibold text-sm hover:bg-gray-100"
            >
              Otevřít detail →
            </Link>
          </div>
        </div>
      </div>
    </div>
  );
}