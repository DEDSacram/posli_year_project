import { useEffect, useState } from "react";
import { api } from "../api";

/**
 * Počet zájemců o inzerát (GET /chat/interest?listingId=). Veřejný endpoint,
 * výsledek držíme v paměti, aby se nestahoval znovu pro každou kartu.
 */
const cache = new Map<number, number>();

export function useInterestCount(listingId: number | null): number | null {
  const [count, setCount] = useState<number | null>(
    listingId != null && cache.has(listingId) ? (cache.get(listingId) as number) : null,
  );

  useEffect(() => {
    if (listingId == null) return;
    const cached = cache.get(listingId);
    if (cached !== undefined) {
      setCount(cached);
      return;
    }
    let live = true;
    api
      .interestCount(listingId)
      .then((d) => {
        cache.set(listingId, d.interestCount ?? 0);
        if (live) setCount(cache.get(listingId) ?? 0);
      })
      .catch(() => {
        if (live) setCount(null);
      });
    return () => {
      live = false;
    };
  }, [listingId]);

  return count;
}

/** Zneplatní cache po nové rezervaci — zájemců přibylo. */
export function bustInterest(listingId: number): void {
  cache.delete(listingId);
}

export function InterestBadge({ count }: { count: number | null }): React.ReactElement | null {
  if (count == null || count < 1) return null;
  return (
    <span className="inline-flex items-center gap-1 text-xs font-semibold text-navy bg-blue-50 border border-blue-200 rounded-full px-2.5 py-0.5">
      🔥 {count}× má zájem
    </span>
  );
}