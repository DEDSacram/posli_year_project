import { Link } from "react-router-dom";
import { fmtPrice, imageUrl } from "../api";
import type { Listing } from "../types";
import { useFav } from "./favs";
import { SellerRating } from "./widgets";

/** Náhradník obrázku, dokud se fotka načítá nebo když inzerát žádnou nemá. */
export function Placeholder({ className = "" }: { className?: string }): React.ReactElement {
  return (
    <div
      className={`w-full h-full bg-gray-200 flex items-center justify-center text-gray-400 ${className}`}
    >
      <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5">
        <rect x="3" y="3" width="18" height="18" rx="2" />
        <circle cx="8.5" cy="8.5" r="1.5" />
        <polyline points="21 15 16 10 5 21" />
      </svg>
    </div>
  );
}

interface ListingCardProps {
  item: Listing;
  onBuy?: (item: Listing) => void;
  onContact?: (item: Listing) => void;
  onQuick?: (item: Listing) => void;
}

/**
 * Karta inzerátu dle `design/bazar_marketplace.html`:
 * horní řádek (40 % fotka / 60 % údaje + cena + tlačítka), dolní řádek
 * (náhledy / prodejce + popis).
 */
export default function ListingCard({
  item, onBuy, onContact, onQuick,
}: ListingCardProps): React.ReactElement {
  const [fav, flip] = useFav(item.id);
  const images = item.images ?? [];
  const thumbs = images.slice(0, 3);

  return (
    <div className="flex flex-col gap-3">
      <div className="flex gap-4 h-[220px]">
        <Link
          to={`/inzerat/${item.id}`}
          className="w-[40%] bg-gray-200 rounded-lg overflow-hidden border border-gray-200 shrink-0 block"
        >
          {images.length ? (
            <img
              src={imageUrl(item.id, images[0])}
              alt={item.title}
              className="w-full h-full object-cover"
              loading="lazy"
            />
          ) : (
            <Placeholder />
          )}
        </Link>
        <div className="w-[60%] flex flex-col min-w-0">
          <Link to={`/inzerat/${item.id}`}>
            <h3 className="text-[17px] font-bold leading-snug hover:text-navy [overflow-wrap:anywhere]">
              {item.title}
            </h3>
          </Link>
          <div className="text-[13px] text-gray-700 mt-1">
            <p>Stav: {item.condition}</p>
            <p>Kategorie: {item.category}</p>
            <p>Lokalita: {item.city}</p>
          </div>
          <p className="text-[22px] font-bold mt-2">{fmtPrice(item.priceCzk)}</p>
          <div className="mt-auto flex flex-col gap-1.5">
            <div className="flex gap-1.5">
              <button
                onClick={() => onBuy?.(item)}
                className="flex-1 bg-brand hover:bg-branddark text-white rounded py-1.5 font-semibold text-sm transition-colors shadow-sm"
              >
                Koupit nyní
              </button>
              {onQuick && (
                <button
                  onClick={() => onQuick(item)}
                  title="Rychlý náhled"
                  aria-label={`Rychlý náhled ${item.title}`}
                  className="px-3 bg-white border border-gray-300 hover:border-navy hover:text-navy rounded py-1.5 text-sm font-semibold transition-colors shadow-sm"
                >
                  👁
                </button>
              )}
            </div>
            <div className="flex gap-1.5">
              <button
                onClick={() => void flip()}
                className="flex-1 bg-btngray hover:bg-btngraydark text-gray-900 rounded py-1.5 font-medium text-sm transition-colors shadow-sm"
              >
                {fav ? "✓ V oblíbených" : "Přidat do oblíbených"}
              </button>
              {onContact && (
                <button
                  onClick={() => onContact(item)}
                  title="Rychlá reakce — napsat prodejci"
                  className="flex-1 border border-navy text-navy hover:bg-navy hover:text-white rounded py-1.5 font-semibold text-sm transition-colors shadow-sm"
                >
                  💬 Mám zájem
                </button>
              )}
            </div>
          </div>
        </div>
      </div>
      <div className="flex gap-4 items-start">
        {thumbs.length > 0 && (
          <div className="w-[40%] flex gap-1 shrink-0">
            {thumbs.map((t) => (
              <Link
                key={t.id}
                to={`/inzerat/${item.id}`}
                className="w-1/3 aspect-square bg-gray-200 rounded overflow-hidden border border-gray-300 block"
              >
                <img
                  src={imageUrl(item.id, t)}
                  alt=""
                  className="w-full h-full object-cover"
                  loading="lazy"
                />
              </Link>
            ))}
          </div>
        )}
        <div className={`${thumbs.length > 0 ? "w-[60%]" : "w-full"}`}>
          <p className="text-[13px] font-medium text-gray-800">
            Prodejce: {item.sellerName || `#${item.sellerId}`}
            <SellerRating userId={item.sellerId} />
          </p>
          <p className="text-[12px] text-gray-600 mt-1 line-clamp-3 leading-relaxed [overflow-wrap:anywhere]">
            {item.description}
          </p>
        </div>
      </div>
    </div>
  );
}