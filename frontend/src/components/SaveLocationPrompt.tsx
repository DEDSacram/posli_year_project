import { useEffect, useState, type ReactElement } from "react";
import { Link } from "react-router-dom";
import { api } from "../api";

const norm = (s: string | null | undefined) => String(s || "").replace(/\s/g, "").toLowerCase();
const fmt = (city: string | null, zip: string | null) =>
  [city, zip].filter(Boolean).join(", ");

interface Picked {
  city?: string;
  zip?: string;
}

interface SaveLocationPromptProps {
  picked: Picked | null;
  profile: { hasProfile: boolean; city: string | null; postalCode: string | null } | null;
  onSaved?: () => void;
}

/**
 * Když se poloha inzerátu liší od profilu, zeptáme se, zda ji uložit
 * do profilu, aby se příště předvyplnila.
 */
export default function SaveLocationPrompt({
  picked, profile, onSaved,
}: SaveLocationPromptProps): ReactElement | null {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [dismissed, setDismissed] = useState(false);
  const [done, setDone] = useState("");

  const key = `${picked?.city}|${picked?.zip}`;
  useEffect(() => {
    setDismissed(false);
    setDone("");
    setError("");
  }, [key]);

  const same =
    !!profile?.hasProfile &&
    norm(picked?.city) === norm(profile.city) &&
    norm(picked?.zip) === norm(profile.postalCode);

  if (!picked || (!picked.city && !picked.zip) || dismissed) return null;
  if (same && !done) return null;
  if (done) {
    return (
      <p className="bg-green-50 border border-green-200 text-green-800 rounded px-3 py-2 text-sm">
        {done}
      </p>
    );
  }

  const save = async () => {
    setBusy(true);
    setError("");
    try {
      await api.saveLocation(picked.city || "", picked.zip || "");
      setDone(`Poloha ${fmt(picked.city ?? null, picked.zip ?? null)} uložena do profilu — příště se předvyplní.`);
      onSaved?.();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="bg-amber-50 border border-amber-200 rounded px-3 py-2 text-sm flex flex-col gap-1.5">
      <p>
        Poloha inzerátu (<strong>{fmt(picked.city ?? null, picked.zip ?? null)}</strong>)
        {profile?.hasProfile ? (
          <>
            {" "}
            se liší od profilu (<strong>{fmt(profile.city, profile.postalCode)}</strong>). Chcete
            profil aktualizovat?
          </>
        ) : (
          <> — chcete si ji uložit do profilu pro příští inzeráty?</>
        )}
      </p>
      {profile?.hasProfile ? (
        <div className="flex flex-wrap gap-2">
          <button
            type="button"
            onClick={() => void save()}
            disabled={busy}
            className="border border-navy text-navy rounded px-3 py-1 font-semibold hover:bg-navy hover:text-white disabled:opacity-50"
          >
            {busy ? "Ukládám…" : "Ano, změnit polohu v profilu"}
          </button>
          <button
            type="button"
            onClick={() => setDismissed(true)}
            className="text-gray-500 hover:text-gray-800 font-medium px-2 py-1"
          >
            Nechat profil beze změny
          </button>
        </div>
      ) : (
        <p>
          Nejdřív si vyplňte daňový profil v{" "}
          <Link to="/ucet" className="font-semibold text-navy hover:underline">
            Můj účet
          </Link>{" "}
          — pak si do něj polohu uložíte jedním klikem.
        </p>
      )}
      {error && <span className="text-red-600 text-xs">{error}</span>}
    </div>
  );
}