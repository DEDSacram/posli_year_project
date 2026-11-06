import { useEffect, useRef, useState, type ReactElement } from "react";
import { api } from "../api";
import type { Badges, Rating, Review } from "../types";
import { useProfileLocation } from "./LocationPicker";

/** Odznaky odvozené z historie obchodů a hodnocení; prázdná = ještě nic nevím. */
const ratingCache = new Map<number, Rating>();
const badgeCache = new Map<number, Badges>();

function useCached<T>(cache: Map<number, T>, key: string, userId: number, load: () => Promise<T>): T | null {
  const [value, setValue] = useState<T | null>(cache.get(userId) ?? null);
  useEffect(() => {
    const cached = cache.get(userId);
    if (cached !== undefined) {
      setValue(cached);
      return;
    }
    let live = true;
    load()
      .then((d) => {
        cache.set(userId, d);
        if (live) setValue(d);
      })
      .catch(() => {
        /* hodnocení ani odznaky nemusí být — UI jen neukáže nic */
      });
    return () => {
      live = false;
    };
  }, [cache, key, userId, load]);
  return value;
}

/** Průměrné hodnocení prodejce vedle jména (★★★★☆). */
export function SellerRating({
  userId,
  showCount = false,
}: {
  userId: number;
  showCount?: boolean;
}): ReactElement | null {
  const r = useCached(ratingCache, "rating", userId, () => api.rating(userId));
  if (!r || !r.count) return null;
  return (
    <span className="ml-0.5">
      <Stars rating={r.average} />
      {showCount && <span className="text-gray-500 text-xs ml-1">({r.count}×)</span>}
    </span>
  );
}

export function Stars({ rating }: { rating: number | null | undefined }): ReactElement {
  const full = Math.round(rating ?? 0);
  return (
    <span className="text-green-700 tracking-widest">
      {"★".repeat(full)}
      {"☆".repeat(Math.max(0, 5 - full))}
    </span>
  );
}

/** Jeden odznak — stejná vizuální podstata jako v mockupu. */
function Chip({
  tone,
  title,
  children,
}: {
  tone: "navy" | "green" | "gold" | "gray";
  title?: string;
  children: ReactElement | string;
}): ReactElement {
  const tones = {
    navy: "bg-navy text-white",
    green: "bg-green-700 text-white",
    gold: "bg-amber-500 text-white",
    gray: "bg-gray-200 text-gray-700",
  } as const;
  return (
    <span className={`inline-block text-xs font-semibold rounded px-2 py-0.5 ${tones[tone]}`} title={title}>
      {children}
    </span>
  );
}

/**
 * Odznaky prodejce: typ (obchodník/soukromník), administrátorem ověřený
 * doklad a zásluhy odvozené z hodnocení a historie obchodů.
 */
export function SellerBadges({
  userId,
  onlyMerit = false,
}: {
  userId: number;
  onlyMerit?: boolean;
}): ReactElement | null {
  const badges = useCached(badgeCache, "badges", userId, () => api.badges(userId));
  if (!badges) return null;

  const merits: Array<[boolean, string, string]> = [
    [badges.verifiedTrader, "✓ Ověřený obchodník", "Doklad totožnosti potvrdil administrátor"],
    [badges.trustedSeller, "Důvěryhodný prodejce", "Hodnocení 4,5+ a žádná potvrzená námitka"],
    [badges.highlyRated, "Vysoké hodnocení", "Průměr 4,8+ z 5 a více hodnocení"],
    [badges.experienced, "Zkušený prodejce", "10 a více dokončených prodejů"],
    [badges.cleanRecord, "Bez stížností", "Žádná potvrzená námitka proti hodnocení"],
  ];

  const shown = merits.filter(([on]) => on);
  if (onlyMerit) {
    if (!shown.length) return null;
    return (
      <span className="inline-flex flex-wrap gap-1">
        {shown.map(([, label, title]) => (
          <Chip key={label} tone="green" title={title}>
            {label}
          </Chip>
        ))}
      </span>
    );
  }

  return (
    <span className="inline-flex flex-wrap gap-1">
      {badges.trader ? (
        <Chip tone="navy">Obchodník</Chip>
      ) : (
        <Chip tone="gray">Soukromník</Chip>
      )}
      {shown.map(([, label, title], i) => (
        <Chip key={label} tone={i === 0 && badges.verifiedTrader ? "navy" : shown.length > 1 ? "gold" : "green"} title={title}>
          {label}
        </Chip>
      ))}
    </span>
  );
}

/** Jen typ prodejce — používá se v kompaktních místech (karta inzerátu). */
export function SellerBadge({ userId }: { userId: number }): ReactElement | null {
  const badges = useCached(badgeCache, "badges", userId, () => api.badges(userId));
  if (!badges) return null;
  return badges.trader ? (
    <Chip tone="navy">Obchodník</Chip>
  ) : (
    <Chip tone="gray">Soukromník</Chip>
  );
}

/** Upozornění na právo odstoupení od smlouvy (u obchodníka). */
export function WithdrawalBanner({ listingId }: { listingId: number }): ReactElement | null {
  const [info, setInfo] = useState<{ withdrawalApplies: boolean; explanationCs: string } | null>(null);
  useEffect(() => {
    api
      .rightsInfo(listingId)
      .then(setInfo)
      .catch(() => setInfo(null));
  }, [listingId]);
  if (!info) return null;
  return (
    <div
      className={`rounded-lg border px-4 py-3 text-sm ${
        info.withdrawalApplies
          ? "bg-blue-50 border-blue-200 text-navydeep"
          : "bg-gray-100 border-gray-200 text-gray-700"
      }`}
    >
      <strong>{info.withdrawalApplies ? "14denní odstoupení platí" : "Bez odstoupení"}</strong>
      <p className="mt-1">{info.explanationCs}</p>
    </div>
  );
}

/**
 * Recenze se značkou ověřeného nákupu, veřejnou odpovědí prodejce
 * a stavem námitky, pokud prodejce hodnocení napadl.
 */
export function VerifiedReview({ review }: { review: Review }): ReactElement {
  const dispute = review.disputeStatus;
  return (
    <div className="border border-gray-200 rounded-lg p-3 bg-white">
      <div className="flex items-center gap-2 text-sm flex-wrap">
        <Stars rating={review.rating} />
        {review.verifiedPurchase && (
          <span className="text-xs font-medium text-green-700">Ověřený nákup</span>
        )}
        {dispute === "open" && (
          <span className="text-xs font-medium text-amber-700 bg-amber-50 border border-amber-200 rounded px-1.5 py-0.5">
            Námitka se řeší
          </span>
        )}
        {dispute === "rejected" && (
          <span className="text-xs font-medium text-gray-600 bg-gray-100 border border-gray-200 rounded px-1.5 py-0.5">
            Námitka zamítnuta
          </span>
        )}
      </div>
      <p className="text-sm mt-1">{review.text}</p>
      {review.responseText && (
        <p className="text-sm mt-2 pl-3 border-l-2 border-gray-300 text-gray-700">
          <strong>Odpověď prodejce:</strong> {review.responseText}
        </p>
      )}
    </div>
  );
}

/** Hledání v okolí podle PSČ — souřadnice zůstávají interní, uživatel vidí okruh v km. */
export function DistanceFilter({
  value,
  onChange,
}: {
  value: { lat: string; lon: string; radiusKm: string };
  onChange: (next: { lat: string; lon: string; radiusKm: string }) => void;
}): ReactElement {
  const [zip, setZip] = useState("");
  const [place, setPlace] = useState<{ city: string | null; zip: string } | null>(null);
  const [fromProfile, setFromProfile] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [profile] = useProfileLocation();
  const autoDone = useRef(false);

  // Předvyplnění z profilu proběhne jednou, a to jen když filtr ještě polohu nemá.
  useEffect(() => {
    if (autoDone.current || !profile?.hasProfile || !profile.postalCode) return;
    if (value.lat && value.lon) return;
    autoDone.current = true;
    const clean = profile.postalCode.replace(/\s/g, "");
    setZip(profile.postalCode);
    setBusy(true);
    api
      .zipToCoords(clean)
      .then((g) => {
        setPlace(g);
        setFromProfile(true);
        onChange({ lat: String(g.lat), lon: String(g.lon), radiusKm: value.radiusKm || "" });
      })
      .catch(() => {
        autoDone.current = false;
      })
      .finally(() => setBusy(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [profile]);

  const apply = async () => {
    const clean = zip.replace(/\s/g, "");
    if (!/^\d{3,10}$/.test(clean)) {
      setError("Zadejte PSČ číslicemi (např. 110 00).");
      return;
    }
    setBusy(true);
    setError("");
    try {
      const g = await api.zipToCoords(clean);
      setPlace(g);
      setFromProfile(false);
      onChange({ ...value, lat: String(g.lat), lon: String(g.lon) });
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  };

  const clear = () => {
    setPlace(null);
    setFromProfile(false);
    setZip("");
    onChange({ ...value, lat: "", lon: "" });
  };

  return (
    <div className="flex flex-col gap-1">
      <div className="flex flex-wrap gap-2 items-end min-h-[34px]">
        <label className="text-sm">
          PSČ
          <input
            value={zip}
            onChange={(e) => {
              setZip(e.target.value);
              setFromProfile(false);
            }}
            onKeyDown={(e) => e.key === "Enter" && void apply()}
            className="ml-1 w-24 border border-gray-300 rounded px-2 py-1 text-sm"
            placeholder="110 00"
            inputMode="numeric"
          />
        </label>
        <label className="text-sm">
          Okruh (km)
          <input
            type="number"
            step="any"
            min={1}
            value={value.radiusKm || ""}
            onChange={(e) => onChange({ ...value, radiusKm: e.target.value })}
            className="ml-1 w-20 border border-gray-300 rounded px-2 py-1 text-sm"
            placeholder="25"
          />
        </label>
        {!place ? (
          <button
            type="button"
            onClick={() => void apply()}
            disabled={busy}
            className="border border-navy text-navy rounded px-3 py-1 text-sm font-semibold hover:bg-navy hover:text-white disabled:opacity-50"
          >
            {busy ? "Hledám…" : "Použít okolí"}
          </button>
        ) : (
          <span className="text-sm bg-green-50 border border-green-200 text-green-800 rounded px-2 py-1">
            Okolí: {place.city || place.zip}
            {fromProfile ? " (z profilu)" : ""}
            <button
              type="button"
              onClick={clear}
              className="ml-2 font-bold"
              aria-label="Zrušit okolí"
            >
              ✕
            </button>
          </span>
        )}
      </div>
      {/* Rezervované místo pro hlášku, aby chyba neposouvala rozvržení. */}
      <span className={`text-red-600 text-xs min-h-[16px] ${error ? "" : "invisible"}`}>
        {error || " "}
      </span>
    </div>
  );
}
