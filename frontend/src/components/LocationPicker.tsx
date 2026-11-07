import { useEffect, useRef, useState, type ReactElement } from "react";
import {
  CircleMarker, MapContainer, TileLayer, Tooltip, useMap, useMapEvents,
} from "react-leaflet";
import { api } from "../api";
import type { GeoPlace } from "../types";

const DEFAULT_CENTER: [number, number] = [49.82, 15.47];

export interface ProfileLocation {
  hasProfile: boolean;
  city: string | null;
  postalCode: string | null;
  country: string | null;
}

/** Poloha z daňového profilu (město + PSČ) pro předvyplnění formuláře. */
export function useProfileLocation(): [ProfileLocation | null, () => void] {
  const [profile, setProfile] = useState<ProfileLocation | null>(null);
  const [tick, setTick] = useState(0);

  useEffect(() => {
    let live = true;
    api
      .myLocation()
      .then((l) => {
        if (live) setProfile(l);
      })
      .catch(() => {
        if (live) setProfile(null);
      });
    return () => {
      live = false;
    };
  }, [tick]);

  return [profile, () => setTick((t) => t + 1)];
}

export function resolveZip(zip: string): Promise<GeoPlace> {
  return api.zipToCoords(String(zip ?? "").replace(/\s/g, ""));
}

export function reverseGeocode(lat: number, lon: number): Promise<GeoPlace> {
  return api.reverseGeocode(lat, lon);
}

function ClickToSet({ onPick }: { onPick: (lat: number, lon: number) => void }): null {
  useMapEvents({
    click(e) {
      onPick(e.latlng.lat, e.latlng.lng);
    },
  });
  return null;
}

function Recenter({ lat, lon }: { lat: number; lon: number }): null {
  const map = useMap();
  const first = useRef(true);
  useEffect(() => {
    map.setView([lat, lon], first.current ? 12 : map.getZoom());
    first.current = false;
  }, [lat, lon, map]);
  return null;
}

interface LocationPickerProps {
  lat: string;
  lon: string;
  onChange: (next: { lat: string; lon: string }) => void;
  onPlace?: (place: { city: string; zip: string }) => void;
  initialZip?: string;
}

/**
 * Výběr polohy inzerátu: PSČ → souřadnice, tlačítko „Moje poloha“ nebo klik do mapy.
 * `onPlace` doplní formuláři lokalitu; prázdné souřadnice = zveřejníme jen město.
 */
export default function LocationPicker({
  lat, lon, onChange, onPlace, initialZip,
}: LocationPickerProps): ReactElement {
  const [zip, setZip] = useState(initialZip || "");
  const [place, setPlace] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [geoBusy, setGeoBusy] = useState(false);
  const [resolving, setResolving] = useState(false);
  const [error, setError] = useState("");
  const touched = useRef(false);

  useEffect(() => {
    if (initialZip && !touched.current) setZip(initialZip);
  }, [initialZip]);

  const numLat = lat === "" || lat == null ? null : Number(lat);
  const numLon = lon === "" || lon == null ? null : Number(lon);
  const hasPos =
    numLat != null && numLon != null && !Number.isNaN(numLat) && !Number.isNaN(numLon);

  /** Z bodu vytáhne město/PSČ a doplní je do formuláře. */
  const resolvePlace = async (la: number, lo: number) => {
    setResolving(true);
    try {
      const r = await reverseGeocode(la, lo);
      setPlace(r.city || r.zip || null);
      if (r.zip) {
        touched.current = true;
        setZip(r.zip);
      }
      if (onPlace && (r.city || r.zip)) onPlace({ city: r.city || "", zip: r.zip || "" });
    } catch {
      setPlace(null);
    } finally {
      setResolving(false);
    }
  };

  const pick = (la: number, lo: number, label?: string, resolve = false) => {
    onChange({
      lat: String(Math.round(la * 10000) / 10000),
      lon: String(Math.round(lo * 10000) / 10000),
    });
    if (label !== undefined) setPlace(label);
    else if (resolve) void resolvePlace(la, lo);
    else setPlace(null);
  };

  const findByZip = async () => {
    const clean = zip.replace(/\s/g, "");
    if (!/^\d{3,10}$/.test(clean)) {
      setError("Zadejte PSČ číslicemi (např. 110 00).");
      return;
    }
    setBusy(true);
    setError("");
    try {
      const g = await resolveZip(clean);
      setPlace(g.city || g.zip);
      pick(g.lat, g.lon, g.city || g.zip);
      onPlace?.({ city: g.city || "", zip: clean });
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  };

  /** Přesná poloha zařízení přes Geolocation API. */
  const useMyLocation = () => {
    if (!("geolocation" in navigator)) {
      setError("Váš prohlížeč sdílení polohy nepodporuje — zadejte PSČ nebo klikněte do mapy.");
      return;
    }
    setGeoBusy(true);
    setError("");
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        setGeoBusy(false);
        pick(pos.coords.latitude, pos.coords.longitude, undefined, true);
      },
      (err) => {
        setGeoBusy(false);
        setError(
          err.code === 1
            ? "Sdílení polohy jste zamítli. Povolte ho v nastavení prohlížeče (zámek v adresním řádku) a zkuste to znovu."
            : err.code === 3
              ? "Zjištění polohy trvalo příliš dlouho, zkuste to znovu."
              : "Polohu se nepodařilo zjistit — zadejte PSČ nebo klikněte do mapy.",
        );
      },
      { enableHighAccuracy: true, timeout: 10_000, maximumAge: 60_000 },
    );
  };

  return (
    <div className="flex flex-col gap-2">
      <div className="flex flex-wrap gap-2 items-end">
        <label className="text-sm">
          <span className="font-medium text-gray-700">PSČ</span>
          <input
            value={zip}
            onChange={(e) => {
              touched.current = true;
              setZip(e.target.value);
            }}
            onKeyDown={(e) => {
              if (e.key === "Enter") {
                e.preventDefault();
                void findByZip();
              }
            }}
            className="ml-2 w-24 border border-gray-300 rounded px-2 py-1.5 text-sm"
            placeholder="110 00"
            inputMode="numeric"
          />
        </label>
        <button
          type="button"
          onClick={() => void findByZip()}
          disabled={busy}
          className="border border-navy text-navy rounded px-3 py-1.5 text-sm font-semibold hover:bg-navy hover:text-white disabled:opacity-50"
        >
          {busy ? "Hledám…" : "Najít polohu"}
        </button>
        <button
          type="button"
          onClick={useMyLocation}
          disabled={geoBusy}
          className="border border-navy text-navy rounded px-3 py-1.5 text-sm font-semibold hover:bg-navy hover:text-white disabled:opacity-50"
        >
          {geoBusy ? "Zjišťuji…" : "📍 Moje poloha"}
        </button>
        {hasPos && (
          <button
            type="button"
            onClick={() => {
              onChange({ lat: "", lon: "" });
              setPlace(null);
            }}
            className="text-sm text-gray-500 hover:text-brand font-medium"
          >
            Odebrat polohu
          </button>
        )}
      </div>
      {error && <span className="text-red-600 text-xs">{error}</span>}
      <div className="rounded-xl overflow-hidden border border-gray-200 z-0">
        <MapContainer
          center={hasPos ? [numLat, numLon] : DEFAULT_CENTER}
          zoom={hasPos ? 12 : 7}
          scrollWheelZoom={false}
          className="w-full h-56"
        >
          <TileLayer
            attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
            url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
          />
          <ClickToSet onPick={(la, lo) => pick(la, lo, undefined, true)} />
          {hasPos && <Recenter lat={numLat} lon={numLon} />}
          {hasPos && (
            <CircleMarker
              center={[numLat, numLon]}
              radius={12}
              pathOptions={{ color: "#1a4480", fillColor: "#1a4480", fillOpacity: 0.6 }}
            >
              <Tooltip>Poloha inzerátu</Tooltip>
            </CircleMarker>
          )}
        </MapContainer>
        <p className="text-xs text-gray-500 px-3 py-1.5 bg-white">
          {hasPos
            ? resolving
              ? "Načítám název místa…"
              : place
                ? `Poloha: ${place} — kliknutím do mapy ji změníte, město se doplní samo.`
                : "Poloha nastavena — kliknutím do mapy ji změníte."
            : "Klikněte do mapy, použijte 📍 Moje poloha, nebo zadejte PSČ. Bez polohy se ukáže jen lokalita (město)."}
        </p>
      </div>
    </div>
  );
}