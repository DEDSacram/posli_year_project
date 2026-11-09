import {
  useEffect, useRef, useState, type FormEvent, type ReactElement,
} from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { api, fmtPrice, type ApiError } from "../api";
import { useAuth } from "../auth";
import type { ListingPayload } from "../types";
import Field, { SelectField, validateForm } from "../components/Field";
import ImagePicker, { type PendingImage } from "../components/ImagePicker";
import ListingMap from "../components/ListingMap";
import LocationPicker, { resolveZip, useProfileLocation, type ProfileLocation } from "../components/LocationPicker";
import { Placeholder } from "../components/ListingCard";
import SmartFill from "../components/SmartFill";
import SaveLocationPrompt from "../components/SaveLocationPrompt";
import { combine, vLat, vLon, vPrice, vRequired, type Validator } from "../components/validation";
import { CATEGORIES, CONDITIONS, catLabel } from "../components/options";
import { ErrorBox } from "./Auth";

type SellForm = {
  title: string;
  description: string;
  priceCzk: string;
  category: string;
  condition: string;
  city: string;
  lat: string;
  lon: string;
}

const EMPTY_FORM: SellForm = {
  title: "",
  description: "",
  priceCzk: "",
  category: "",
  condition: "",
  city: "",
  lat: "",
  lon: "",
};

const rules: Record<keyof SellForm, Validator> = {
  title: combine(vRequired("Název"), (v) =>
    String(v ?? "").trim().length < 3 ? "Název musí mít aspoň 3 znaky." : "",
  ),
  description: combine(vRequired("Popis"), (v) =>
    String(v ?? "").trim().length < 10 ? "Popis musí mít aspoň 10 znaků." : "",
  ),
  priceCzk: vPrice,
  category: vRequired("Kategorie"),
  condition: vRequired("Stav"),
  city: vRequired("Lokalita"),
  lat: vLat,
  lon: vLon,
};

/** Koncept se ukládá do localStorage (bez fotek, ty jsou až ve Files). */
const DRAFT_KEY = "bazar-sell-draft";

function readDraft(): Partial<SellForm> | null {
  try {
    const d: unknown = JSON.parse(localStorage.getItem(DRAFT_KEY) ?? "null");
    if (d && typeof d === "object") return d as Partial<SellForm>;
  } catch {
    /* poškozený koncept — začít čistě */
  }
  return null;
}

function toPayload(form: SellForm): ListingPayload {
  const body: ListingPayload = {
    title: form.title.trim(),
    description: form.description.trim(),
    priceCzk: Number(form.priceCzk),
    category: form.category.trim(),
    condition: form.condition.trim(),
    city: form.city.trim(),
  };
  if (form.lat && form.lon) {
    body.lat = Number(form.lat);
    body.lon = Number(form.lon);
  }
  return body;
}

/** Formulář pro vložení inzerátu s náhledem, automatickým konceptem a rychlým doplněním. */
export default function Sell(): ReactElement {
  const nav = useNavigate();
  const { user } = useAuth();
  const [params] = useSearchParams();
  const copyId = params.get("copy");

  const initialDraft = copyId ? null : readDraft();
  const [form, setForm] = useState<SellForm>({ ...EMPTY_FORM, ...initialDraft });
  const [draftRestored, setDraftRestored] = useState(() => !!initialDraft);
  const [photos, setPhotos] = useState<PendingImage[]>([]);
  const [error, setError] = useState("");
  const [preview, setPreview] = useState(false);
  const [busy, setBusy] = useState(false);
  const [profileNote, setProfileNote] = useState("");
  const [pickedPlace, setPickedPlace] = useState<{ city: string; zip: string } | null>(null);
  const photosRef = useRef(photos);
  photosRef.current = photos;

  const [profile, refreshProfile] = useProfileLocation();
  const prefilled = useRef(false);

  // Duplikace existujícího inzerátu (?copy=id) — předvyplnit, fotky se neduplikují.
  useEffect(() => {
    if (!copyId || !user) return;
    api
      .listing(copyId)
      .then((d) => {
        if (d.sellerId !== user.id) return;
        setForm({
          title: `${d.title} (kopie)`,
          description: d.description,
          priceCzk: String(d.priceCzk ?? ""),
          category: d.category,
          condition: d.condition,
          city: d.city,
          lat: d.lat == null ? "" : String(d.lat),
          lon: d.lon == null ? "" : String(d.lon),
        });
        setProfileNote("Předvyplněno z kopie inzerátu — upravte a publikujte.");
      })
      .catch(() => {
        /* kopie není dostupná */
      });
  }, [copyId, user]);

  // Koncept do schránky zařízení při každé změně (fotky se neukládají).
  useEffect(() => {
    const t = setTimeout(() => {
      try {
        if (Object.values(form).some((v) => String(v ?? "").trim() !== "")) {
          localStorage.setItem(DRAFT_KEY, JSON.stringify(form));
        }
      } catch {
        /* plné úložiště — ignorovat */
      }
    }, 500);
    return () => clearTimeout(t);
  }, [form]);

  // Předvyplnění z daňového profilu: město + PSČ → souřadnice.
  useEffect(() => {
    if (!profile || prefilled.current) return;
    prefilled.current = true;
    if (!profile.hasProfile) return;
    if (profile.city) {
      setForm((f) => (f.city ? f : { ...f, city: profile.city as string }));
    }
    if (profile.postalCode) {
      resolveZip(profile.postalCode)
        .then((g) => {
          setForm((f) => (f.lat || f.lon ? f : { ...f, lat: String(g.lat), lon: String(g.lon) }));
          setProfileNote(
            `Poloha předvyplněna z vašeho profilu (PSČ ${profile.postalCode} → ${g.city}). Můžete ji upravit.`,
          );
        })
        .catch(() => {
          if (profile.city) setProfileNote("Město předvyplněno z vašeho profilu, přesnou polohu doplňte níže.");
        });
    } else if (profile.city) {
      setProfileNote("Město předvyplněno z vašeho profilu.");
    }
  }, [profile]);

  useEffect(() => {
    if (!user) nav("/prihlaseni");
  }, [user, nav]);

  // Uvolnění object-URL při odchodu ze stránky.
  useEffect(
    () => () => {
      photosRef.current.forEach((p) => URL.revokeObjectURL(p.url));
    },
    [],
  );

  useEffect(() => {
    if (preview) window.scrollTo(0, 0);
  }, [preview]);

  const set = <K extends keyof SellForm>(k: K, v: SellForm[K]) =>
    setForm((f) => ({ ...f, [k]: v }));

  const checkValid = (): boolean => {
    const errs = validateForm(form, rules);
    if ((form.lat || form.lon) && !(form.lat && form.lon)) {
      errs.lon = "Polohu nastavte celou (mapou/PSČ), nebo ji tlačítkem odeberte.";
    }
    if (Object.keys(errs).length) {
      setError(`Zkontrolujte zvýrazněná pole: ${Object.values(errs)[0]}`);
      return false;
    }
    setError("");
    return true;
  };

  const publish = async () => {
    setBusy(true);
    try {
      const created = await api.createListing(toPayload(form));
      // Nahráváme ve zvoleném pořadí — backend čísluje sortOrder popořadě.
      for (const p of photosRef.current) {
        await api.uploadListingImage(created.id, p.file);
      }
      photosRef.current.forEach((p) => URL.revokeObjectURL(p.url));
      try {
        localStorage.removeItem(DRAFT_KEY);
      } catch {
        /* ignorovat */
      }
      setDraftRestored(false);
      nav(`/inzerat/${created.id}`);
    } catch (err) {
      const apiErr = err as ApiError;
      setPreview(false);
      setError(
        apiErr.code === "DAC7_PROFILE_REQUIRED"
          ? `${apiErr.message} Doplňte jej v sekci Můj účet → Daňový profil.`
          : apiErr.message,
      );
    } finally {
      setBusy(false);
    }
  };

  const submit = (e: FormEvent) => {
    e.preventDefault();
    if (checkValid()) setPreview(true);
  };

  if (preview) {
    const body = toPayload(form);
    return (
      <div className="container mx-auto px-4 py-10 max-w-2xl">
        <h1 className="text-3xl font-bold text-navydeep mb-2">Náhled inzerátu</h1>
        <p className="text-sm text-gray-500 mb-6">
          Takto uvidí váš inzerát ostatní. Fotky jsou ve zvoleném pořadí.
        </p>
        <div className="bg-white border border-gray-200 rounded-xl p-6 flex flex-col gap-4">
          {photos.length ? (
            <div>
              <div className="rounded-lg overflow-hidden border border-gray-200 aspect-[4/3] bg-gray-100">
                <img src={photos[0].url} alt={body.title} className="w-full h-full object-cover" />
              </div>
              {photos.length > 1 && (
                <div className="grid grid-cols-5 gap-1 mt-1">
                  {photos.slice(1, 6).map((p) => (
                    <div key={p.key} className="aspect-square rounded overflow-hidden border border-gray-300">
                      <img src={p.url} alt="" className="w-full h-full object-cover" />
                    </div>
                  ))}
                </div>
              )}
            </div>
          ) : (
            <div className="aspect-[4/3] rounded-lg overflow-hidden">
              <Placeholder />
            </div>
          )}
          <div>
            <h2 className="text-2xl font-bold">{body.title}</h2>
            <p className="text-[26px] font-bold text-navydeep mt-1">{fmtPrice(body.priceCzk)}</p>
            <div className="text-sm text-gray-700 mt-2 flex flex-col gap-0.5">
              <p>Stav: {body.condition}</p>
              <p>Kategorie: {catLabel(body.category)}</p>
              <p>Lokalita: {body.city}</p>
              <p>Prodejce: Vy</p>
            </div>
          </div>
          <div>
            <h3 className="font-semibold mb-1">Popis</h3>
            <p className="text-sm text-gray-700 whitespace-pre-wrap">{body.description}</p>
          </div>
          <div>
            <h3 className="font-semibold mb-2">Poloha</h3>
            <ListingMap lat={body.lat ?? null} lon={body.lon ?? null} title={body.title} />
          </div>
          <ErrorBox message={error} />
          <div className="flex flex-col sm:flex-row gap-2">
            <button
              onClick={() => setPreview(false)}
              disabled={busy}
              className="flex-1 border border-navy text-navy rounded py-2.5 font-semibold hover:bg-navy hover:text-white disabled:opacity-50"
            >
              Zpět k úpravám
            </button>
            <button
              onClick={() => void publish()}
              disabled={busy}
              className="flex-1 bg-brand hover:bg-branddark text-white rounded py-2.5 font-semibold disabled:opacity-50"
            >
              {busy ? "Publikuji…" : "Publikovat inzerát"}
            </button>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="container mx-auto px-4 py-10 max-w-2xl">
      <h1 className="text-3xl font-bold text-navydeep mb-6">Prodat</h1>
      <form onSubmit={submit} className="bg-white border border-gray-200 rounded-xl p-6 flex flex-col gap-4" noValidate>
        <ErrorBox message={error} />
        {error.includes("Daňový profil") && (
          <Link to="/ucet" className="text-brand font-semibold text-sm">
            → Přejít na Daňový profil
          </Link>
        )}

        {draftRestored && (
          <p className="bg-blue-50 border border-blue-200 text-blue-900 rounded px-3 py-2 text-sm">
            Obnoven rozepsaný koncept z minula.{" "}
            <button
              type="button"
              onClick={() => {
                setForm(EMPTY_FORM);
                try {
                  localStorage.removeItem(DRAFT_KEY);
                } catch {
                  /* ignorovat */
                }
                setDraftRestored(false);
              }}
              className="font-semibold hover:underline"
            >
              Začít znovu
            </button>
          </p>
        )}

        <SmartFill
          title={form.title}
          category={form.category}
          photos={photos}
          onApply={(patch) => setForm((f) => ({ ...f, ...patch }))}
        />

        <Field
          label="Název"
          required
          value={form.title}
          onChange={(v) => set("title", v)}
          validate={rules.title}
          placeholder="např. Vintage hnědá kožená brašna"
        />
        <Field
          label="Popis"
          required
          textarea
          value={form.description}
          onChange={(v) => set("description", v)}
          validate={rules.description}
          placeholder="Popište stav, případné vady, příslušenství…"
        />

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <Field
            label="Cena (Kč)"
            required
            type="number"
            min={1}
            value={form.priceCzk}
            onChange={(v) => set("priceCzk", v)}
            validate={rules.priceCzk}
            placeholder="např. 3500"
          />
          <SelectField
            label="Kategorie"
            required
            value={form.category}
            onChange={(v) => set("category", v)}
            validate={rules.category}
            options={CATEGORIES}
          />
          <SelectField
            label="Stav"
            required
            value={form.condition}
            onChange={(v) => set("condition", v)}
            validate={rules.condition}
            options={CONDITIONS}
            hint="Detail popište v popisu inzerátu."
          />
          <Field
            label="Lokalita"
            required
            value={form.city}
            onChange={(v) => set("city", v)}
            validate={rules.city}
            placeholder="např. Praha 5"
          />
        </div>

        {profileNote && (
          <p className="bg-green-50 border border-green-200 text-green-800 rounded px-3 py-2 text-sm -mt-1">
            {profileNote}
          </p>
        )}

        <div>
          <p className="text-sm font-medium text-gray-700 mb-1">
            Přesná poloha{" "}
            <span className="font-normal text-gray-400">
              (volitelné — mapa v inzerátu a hledání v okolí)
            </span>
          </p>
          <LocationPicker
            lat={form.lat}
            lon={form.lon}
            onChange={({ lat, lon }) => {
              set("lat", lat);
              set("lon", lon);
            }}
            onPlace={({ city, zip }) => {
              // Klik do mapy / Moje poloha / PSČ = vědomá volba místa → lokalitu přepsat.
              if (city) setForm((f) => ({ ...f, city }));
              if (city || zip) setPickedPlace({ city, zip });
            }}
            initialZip={profile?.postalCode ?? ""}
          />
          <div className="mt-2">
            <SaveLocationPrompt
              picked={pickedPlace}
              profile={profile as ProfileLocation | null}
              onSaved={() => {
                refreshProfile();
                if (pickedPlace) {
                  setPickedPlace({ city: pickedPlace.city, zip: pickedPlace.zip });
                }
              }}
            />
          </div>
        </div>

        <ImagePicker items={photos} onChange={setPhotos} />

        <button className="bg-brand hover:bg-branddark text-white rounded py-2.5 font-semibold">
          Pokračovat na náhled
        </button>
      </form>
    </div>
  );
}