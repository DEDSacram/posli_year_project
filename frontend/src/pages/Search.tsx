import { useCallback, useEffect, useState, type FormEvent, type ReactElement } from "react";
import { useSearchParams } from "react-router-dom";
import { api } from "../api";
import type { Listing } from "../types";
import ListingCard from "../components/ListingCard";
import { DistanceFilter } from "../components/widgets";
import { CATEGORIES, catLabel } from "../components/options";
import { useBuy, useContact, useQuickView } from "./Home";

const SORTS: ReadonlyArray<readonly [string, string]> = [
  ["relevance", "Relevance"],
  ["newest", "Nejnovější"],
  ["priceAsc", "Nejlevnější"],
  ["priceDesc", "Nejdražší"],
];

interface Geo {
  lat: string;
  lon: string;
  radiusKm: string;
}

const EMPTY_GEO: Geo = { lat: "", lon: "", radiusKm: "" };

/** Katalog s fulltextovým vyhledáváním, filtrem kategorie, ceny a polohy. */
export default function Search(): ReactElement {
  const [params, setParams] = useSearchParams();
  const [q, setQ] = useState(params.get("q") ?? "");
  const [category, setCategory] = useState(params.get("category") ?? "");
  const [minPrice, setMinPrice] = useState("");
  const [maxPrice, setMaxPrice] = useState("");
  const [sort, setSort] = useState("relevance");
  const [geo, setGeo] = useState<Geo>(EMPTY_GEO);
  const [items, setItems] = useState<Listing[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const buy = useBuy();
  const contact = useContact();
  const [setQuick, quickView] = useQuickView(contact);

  const run = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const p = new URLSearchParams();
      if (q.trim()) p.set("q", q.trim());
      if (category) p.set("category", category);
      if (minPrice) p.set("minPrice", minPrice);
      if (maxPrice) p.set("maxPrice", maxPrice);
      if (sort) p.set("sort", sort);
      if (geo.lat && geo.lon && geo.radiusKm) {
        p.set("lat", geo.lat);
        p.set("lon", geo.lon);
        p.set("radiusKm", geo.radiusKm);
      }
      p.set("limit", "24");
      const page = await api.searchListings(p);
      setItems(page.data ?? []);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setLoading(false);
    }
  }, [q, category, minPrice, maxPrice, sort, geo]);

  // Dotaz z adresního řádku (např. z hledacího pole v hlavičce).
  useEffect(() => {
    setQ(params.get("q") ?? "");
    setCategory(params.get("category") ?? "");
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [params]);

  useEffect(() => {
    void run();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const submit = (e: FormEvent) => {
    e.preventDefault();
    setParams(q.trim() ? { q: q.trim() } : {});
    void run();
  };

  return (
    <div className="container mx-auto px-4 lg:px-8 py-8">
      <form
        onSubmit={submit}
        className="bg-white border border-gray-200 rounded-lg p-4 flex flex-col gap-3 mb-8"
      >
        <div className="flex flex-wrap gap-2">
          <input
            value={q}
            onChange={(e) => setQ(e.target.value)}
            placeholder="Hledat…"
            aria-label="Hledat"
            className="flex-1 min-w-[200px] border border-gray-300 rounded px-3 py-1.5 text-sm"
          />
          <select
            value={category}
            onChange={(e) => setCategory(e.target.value)}
            aria-label="Kategorie"
            className="border border-gray-300 rounded px-2 py-1.5 text-sm"
          >
            <option value="">Vše</option>
            {CATEGORIES.map(([v, l]) => (
              <option key={v} value={v}>
                {l}
              </option>
            ))}
          </select>
          <select
            value={sort}
            onChange={(e) => setSort(e.target.value)}
            aria-label="Řazení"
            className="border border-gray-300 rounded px-2 py-1.5 text-sm"
          >
            {SORTS.map(([v, l]) => (
              <option key={v} value={v}>
                {l}
              </option>
            ))}
          </select>
        </div>
        <div className="flex flex-wrap gap-2 items-end">
          <label className="text-sm">
            Cena od
            <input
              type="number"
              value={minPrice}
              onChange={(e) => setMinPrice(e.target.value)}
              className="ml-1 w-24 border border-gray-300 rounded px-2 py-1 text-sm"
            />
          </label>
          <label className="text-sm">
            do
            <input
              type="number"
              value={maxPrice}
              onChange={(e) => setMaxPrice(e.target.value)}
              className="ml-1 w-24 border border-gray-300 rounded px-2 py-1 text-sm"
            />
          </label>
          <DistanceFilter value={geo} onChange={setGeo} />
          <button className="bg-brand hover:bg-branddark text-white rounded px-5 py-1.5 text-sm font-semibold">
            Hledat
          </button>
        </div>
      </form>

      {loading && <p className="text-gray-500">Hledám…</p>}
      {error && <p className="text-brand text-sm">{error}</p>}

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

      {!loading && !items.length && !error && (
        <p className="text-gray-500">
          Nic jsme nenašli. Zkuste jiný dotaz nebo jinou kategorii ({catLabel(category)}).
        </p>
      )}
      {quickView}
    </div>
  );
}