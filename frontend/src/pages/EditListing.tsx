import {
  useEffect, useState, type FormEvent, type ReactElement,
} from "react";
import { useNavigate, useParams } from "react-router-dom";
import { api, imageUrl } from "../api";
import { useAuth } from "../auth";
import { useAlerts } from "../alerts";
import type { ListingImage, ListingPayload } from "../types";
import ImagePicker, { type PendingImage } from "../components/ImagePicker";
import LocationPicker, { resolveZip, useProfileLocation } from "../components/LocationPicker";
import Field, { SelectField, validateForm } from "../components/Field";
import SaveLocationPrompt from "../components/SaveLocationPrompt";
import { combine, vLat, vLon, vPrice, vRequired, type Validator } from "../components/validation";
import { CATEGORIES, CONDITIONS } from "../components/options";
import { ErrorBox } from "./Auth";

type EditForm = {
  title: string;
  description: string;
  priceCzk: string;
  category: string;
  condition: string;
  city: string;
  lat: string;
  lon: string;
}

const rules: Record<keyof EditForm, Validator> = {
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

/** Úprava vlastního inzerátu. Při poklesu ceny dostanou jeho sledující upozornění. */
export default function EditListing(): ReactElement {
  const { id } = useParams();
  const { user } = useAuth();
  const { refresh } = useAlerts();
  const nav = useNavigate();
  const [form, setForm] = useState<EditForm | null>(null);
  const [images, setImages] = useState<ListingImage[]>([]);
  const [files, setFiles] = useState<PendingImage[]>([]);
  const [submitError, setSubmitError] = useState("");
  const [busy, setBusy] = useState(false);
  const [pickedPlace, setPickedPlace] = useState<{ city: string; zip: string } | null>(null);
  const [profile, refreshProfile] = useProfileLocation();
  const listingId = Number(id);

  useEffect(() => {
    if (!user) {
      nav("/prihlaseni");
      return;
    }
    api
      .listing(listingId)
      .then((d) => {
        if (d.sellerId !== user.id) {
          nav(`/inzerat/${listingId}`);
          return;
        }
        setForm({
          title: d.title,
          description: d.description,
          priceCzk: String(d.priceCzk ?? ""),
          category: d.category,
          condition: d.condition,
          city: d.city,
          lat: d.lat == null ? "" : String(d.lat),
          lon: d.lon == null ? "" : String(d.lon),
        });
        setImages(d.images ?? []);
      })
      .catch(() => nav("/"));
  }, [listingId, user, nav]);

  if (!form) {
    return (
      <div className="container mx-auto px-4 py-10">
        <p className="text-gray-500">Načítám…</p>
      </div>
    );
  }

  const set = <K extends keyof EditForm>(k: K, v: EditForm[K]) =>
    setForm((f) => (f ? { ...f, [k]: v } : f));

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const errs = validateForm(form, rules);
    if ((form.lat || form.lon) && !(form.lat && form.lon)) {
      errs.lon = "Polohu nastavte celou (mapou/PSČ), nebo ji tlačítkem odeberte.";
    }
    if (Object.keys(errs).length) {
      setSubmitError(`Zkontrolujte zvýrazněná pole: ${Object.values(errs)[0]}`);
      return;
    }
    setSubmitError("");
    setBusy(true);
    try {
      const body: ListingPayload = {
        title: form.title.trim(),
        description: form.description.trim(),
        priceCzk: Number(form.priceCzk),
        category: form.category.trim(),
        condition: form.condition.trim(),
        city: form.city.trim(),
      };
      if (form.lat !== "" && form.lon !== "") {
        body.lat = Number(form.lat);
        body.lon = Number(form.lon);
      }
      await api.updateListing(listingId, body);
      for (const p of files) {
        await api.uploadListingImage(listingId, p.file);
      }
      files.forEach((p) => URL.revokeObjectURL(p.url));
      await refresh(); // upozornění o zlevnění mohou přibýt
      nav(`/inzerat/${listingId}`);
    } catch (err) {
      setSubmitError((err as Error).message);
    } finally {
      setBusy(false);
    }
  };

  const deleteImage = async (imgId: number) => {
    if (!confirm("Smazat fotku?")) return;
    try {
      await api.deleteListingImage(listingId, imgId);
      setImages(images.filter((i) => i.id !== imgId));
    } catch (err) {
      setSubmitError((err as Error).message);
    }
  };

  return (
    <div className="container mx-auto px-4 py-10 max-w-2xl">
      <h1 className="text-3xl font-bold text-navydeep mb-6">Upravit inzerát</h1>
      <form
        onSubmit={submit}
        className="bg-white border border-gray-200 rounded-xl p-6 flex flex-col gap-4"
        noValidate
      >
        <ErrorBox message={submitError} />
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
          placeholder="Popište stav, vady, příslušenství…"
        />
        <div className="grid grid-cols-2 gap-4">
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

        <div>
          <div className="flex items-center justify-between mb-1">
            <p className="text-sm font-medium text-gray-700">
              Přesná poloha <span className="font-normal text-gray-400">(volitelné)</span>
            </p>
            {(!form.lat || !form.lon) && profile?.postalCode && (
              <button
                type="button"
                onClick={() => {
                  void resolveZip(profile.postalCode as string)
                    .then((g) => {
                      set("lat", String(g.lat));
                      set("lon", String(g.lon));
                    })
                    .catch((err: Error) => setSubmitError(err.message));
                }}
                className="text-xs font-semibold text-navy hover:underline"
              >
                Použít polohu z profilu (PSČ {profile.postalCode})
              </button>
            )}
          </div>
          <LocationPicker
            lat={form.lat}
            lon={form.lon}
            onChange={({ lat, lon }) => {
              set("lat", lat);
              set("lon", lon);
            }}
            onPlace={({ city, zip }) => {
              if (city) setForm((f) => (f ? { ...f, city } : f));
              if (city || zip) setPickedPlace({ city, zip });
            }}
            initialZip={profile?.postalCode ?? ""}
          />
          <div className="mt-2">
            <SaveLocationPrompt
              picked={pickedPlace}
              profile={profile}
              onSaved={() => refreshProfile()}
            />
          </div>
        </div>

        {!!images.length && (
          <div>
            <p className="text-sm font-medium text-gray-700 mb-2">Nahrané fotky</p>
            <div className="flex gap-2 flex-wrap">
              {images.map((img) => (
                <div
                  key={img.id}
                  className="relative w-20 aspect-square rounded overflow-hidden border border-gray-300"
                >
                  <img src={imageUrl(listingId, img)} alt="" className="w-full h-full object-cover" />
                  <button
                    type="button"
                    onClick={() => void deleteImage(img.id)}
                    aria-label="Smazat fotku"
                    className="absolute top-0 right-0 bg-brand text-white text-xs px-1.5 py-0.5"
                  >
                    ✕
                  </button>
                </div>
              ))}
            </div>
          </div>
        )}

        <ImagePicker items={files} onChange={setFiles} />

        <p className="text-xs text-gray-500">
          Snížíte-li cenu, všem, kdo mají inzerát v oblíbených, přijde automaticky upozornění.
        </p>

        <button
          disabled={busy}
          className="bg-brand hover:bg-branddark text-white rounded py-2.5 font-semibold disabled:opacity-50"
        >
          {busy ? "Ukládám…" : "Uložit změny"}
        </button>
      </form>
    </div>
  );
}