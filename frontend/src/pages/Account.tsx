import {
  useEffect, useState, type FormEvent, type ReactElement, type ReactNode,
} from "react";
import { Link, useNavigate } from "react-router-dom";
import { API_URL, api, fmtPrice } from "../api";
import { useAuth } from "../auth";
import type { Consent, Dac7Status, Listing, VerificationState } from "../types";
import { ErrorBox } from "./Auth";
import Field from "../components/Field";
import { useFavIds } from "../components/favs";
import { vCzechPostal, vDob, vIban, vPersonName, vRequired } from "../components/validation";

function Section({ title, children }: { title: string; children: ReactNode }): ReactElement {
  return (
    <div className="bg-white border border-gray-200 rounded-xl p-5">
      <h2 className="text-lg font-bold text-navydeep mb-3">{title}</h2>
      {children}
    </div>
  );
}

const infoRules = {
  firstName: vPersonName("Jméno"),
  lastName: vPersonName("Příjmení"),
  dateOfBirth: vDob,
  street: vRequired("Ulice"),
  city: vRequired("Město"),
  postalCode: vCzechPostal,
  country: vRequired("Země"),
  iban: vIban,
};

const PURPOSE_LABELS: Record<string, [string, string]> = {
  marketing: ["Marketing", "Souhlas se zasíláním obchodních sdělení."],
  cookies: ["Cookies", "Souhlas s ukládáním cookies v prohlížeči."],
  analytics: ["Analytika", "Souhlas s měřením návštěvnosti."],
  location: ["Poloha", "Souhlas se zadáváním polohy u inzerátů."],
};

const purposeLabel = (p: string) => PURPOSE_LABELS[p]?.[0] ?? p;
const purposeDesc = (p: string) => PURPOSE_LABELS[p]?.[1] ?? "Individuální souhlas.";

interface InfoState {
  sellerType: string;
  firstName: string;
  lastName: string;
  dateOfBirth: string;
  street: string;
  city: string;
  postalCode: string;
  country: string;
  taxId: string;
  ico: string;
  iban: string;
  selfCertification: boolean;
}

const VERIFICATION_TEXT: Record<VerificationState["status"], string> = {
  pending: "Doklad čeká na posouzení administrátorem. Prodejovat můžete i bez potvrzení.",
  verified: "Administrátor váš doklad potvrdil — u profilu máte odznak „Ověřený obchodník“.",
  rejected: "Administrátor doklad odmítl. Nahrajte ho prosím znovu.",
};

/** Účet: daňový profil prodejce, ověření dokladu, moje inzeráty, souhlasy a GDPR. */
export default function Account(): ReactElement | null {
  const { user, logout } = useAuth();
  const nav = useNavigate();
  const { ids: favIds } = useFavIds();
  const [status, setStatus] = useState<Dac7Status | null>(null);
  const [verification, setVerification] = useState<VerificationState | null>(null);
  const [mine, setMine] = useState<Listing[]>([]);
  const [grants, setGrants] = useState<Consent[]>([]);
  const [msg, setMsg] = useState("");
  const [err, setErr] = useState("");
  const [doc, setDoc] = useState<File | null>(null);
  const [consent, setConsent] = useState({ purpose: "marketing", granted: true });
  const [info, setInfo] = useState<InfoState>({
    sellerType: "private",
    firstName: "",
    lastName: "",
    dateOfBirth: "",
    street: "",
    city: "",
    postalCode: "",
    country: "CZ",
    taxId: "",
    ico: "",
    iban: "",
    selfCertification: false,
  });

  useEffect(() => {
    if (!user) {
      nav("/prihlaseni");
      return;
    }
    api.dac7Status().then(setStatus).catch(() => setStatus(null));
    api.myVerification().then(setVerification).catch(() => setVerification(null));
    api
      .feed(undefined, 50)
      .then((p) => setMine((p.data ?? []).filter((l) => l.sellerId === user.id)))
      .catch(() => setMine([]));
    api
      .exportMe()
      .then((d) => setGrants((d.consents as Consent[]) ?? []))
      .catch(() => setGrants([]));
  }, [user, nav]);

  if (!user) return null;

  const set = <K extends keyof InfoState>(k: K, v: InfoState[K]) =>
    setInfo((f) => ({ ...f, [k]: v }));

  const saveInfo = async (e: FormEvent) => {
    e.preventDefault();
    setMsg("");
    setErr("");
    for (const [k, fn] of Object.entries(infoRules)) {
      const em = fn(info[k as keyof InfoState]);
      if (em) {
        setErr(em);
        return;
      }
    }
    try {
      await api.saveSellerInfo({
        sellerType: info.sellerType,
        fullName: `${info.firstName.trim()} ${info.lastName.trim()}`,
        dateOfBirth: info.dateOfBirth.trim(),
        street: info.street.trim(),
        city: info.city.trim(),
        postalCode: info.postalCode.trim(),
        country: info.country.trim(),
        taxId: info.taxId.trim() || undefined,
        ico: info.ico.trim() || undefined,
        iban: info.iban.replace(/[\s-]/g, ""),
        selfCertification: info.selfCertification,
      });
      setMsg("Daňový profil uložen.");
    } catch (e) {
      setErr((e as Error).message);
    }
  };

  const uploadDoc = async () => {
    if (!doc) return;
    setMsg("");
    setErr("");
    try {
      await api.uploadIdDocument(doc);
      setMsg("Doklad nahrán a čeká na posouzení administrátorem.");
      setDoc(null);
      setVerification(await api.myVerification());
    } catch (e) {
      setErr((e as Error).message);
    }
  };

  const sendConsent = async () => {
    setMsg("");
    setErr("");
    try {
      await api.consent(consent.purpose, consent.granted);
      setMsg("Oprávnění evidováno.");
      const d = await api.exportMe();
      setGrants((d.consents as Consent[]) ?? []);
    } catch (e) {
      setErr((e as Error).message);
    }
  };

  const exportMe = async () => {
    const data = await api.exportMe();
    const blob = new Blob([JSON.stringify(data, null, 2)], { type: "application/json" });
    const a = document.createElement("a");
    a.href = URL.createObjectURL(blob);
    a.download = "moje-data.json";
    a.click();
    setTimeout(() => URL.revokeObjectURL(a.href), 5000);
  };

  const deleteMe = async () => {
    if (!confirm("Opravdu smazat účet? (soft-delete, daňový profil zůstává z DAC7 důvodů)")) return;
    await api.deleteMe();
    logout();
    nav("/");
  };

  return (
    <div className="container mx-auto px-4 py-10 max-w-3xl flex flex-col gap-5">
      <h1 className="text-3xl font-bold text-navydeep">Můj účet</h1>
      {msg && (
        <p className="bg-green-50 border border-green-200 text-green-800 rounded px-3 py-2 text-sm">{msg}</p>
      )}
      <ErrorBox message={err} />

      <Section title="Přehled obchodů">
        <div className="flex flex-wrap items-center gap-3">
          <Link
            to="/obchody"
            className="bg-navy hover:bg-navydeep text-white rounded px-4 py-2 text-sm font-semibold"
          >
            📦 Zobrazit moje obchody a hodnocení
          </Link>
          {status && (
            <p className="text-sm text-gray-700">
              Prodeje letos: <strong>{status.salesCount}</strong> · obrat:{" "}
              <strong>{fmtPrice(status.totalAmountCzk)}</strong> ·{" "}
              {status.isReportable ? (
                <span className="text-brand font-semibold">
                  reportovatelné pro úřady (30+ prodejů nebo 2 000 €)
                </span>
              ) : (
                <span className="text-green-700">pod prahem hlášení</span>
              )}
            </p>
          )}
        </div>
      </Section>

      <Section title="Daňový profil prodejce">
        <form onSubmit={saveInfo} className="grid grid-cols-1 sm:grid-cols-2 gap-4" noValidate>
          <label className="text-sm">
            <span className="font-medium text-gray-700">Typ prodejce</span>
            <select
              value={info.sellerType}
              onChange={(e) => set("sellerType", e.target.value)}
              className="w-full border border-gray-300 rounded px-3 py-2 text-sm mt-1"
            >
              <option value="private">Soukromník</option>
              <option value="trader">Obchodník</option>
            </select>
          </label>
          <Field
            label="Datum narození"
            required
            value={info.dateOfBirth}
            onChange={(v) => set("dateOfBirth", v)}
            validate={infoRules.dateOfBirth}
            placeholder="např. 1990-05-05"
            hint="Formát RRRR-MM-DD."
          />
          <Field
            label="Jméno"
            required
            value={info.firstName}
            onChange={(v) => set("firstName", v)}
            validate={infoRules.firstName}
            placeholder="např. Jan"
            autoComplete="given-name"
          />
          <Field
            label="Příjmení"
            required
            value={info.lastName}
            onChange={(v) => set("lastName", v)}
            validate={infoRules.lastName}
            placeholder="např. Novák"
            autoComplete="family-name"
          />
          <Field
            label="Ulice a číslo"
            required
            value={info.street}
            onChange={(v) => set("street", v)}
            validate={infoRules.street}
            placeholder="např. Dlouhá 12"
            autoComplete="street-address"
          />
          <Field
            label="Město"
            required
            value={info.city}
            onChange={(v) => set("city", v)}
            validate={infoRules.city}
            placeholder="např. Praha"
            autoComplete="address-level2"
          />
          <Field
            label="PSČ"
            required
            value={info.postalCode}
            onChange={(v) => set("postalCode", v)}
            validate={infoRules.postalCode}
            placeholder="např. 110 00"
            autoComplete="postal-code"
          />
          <Field
            label="Země"
            required
            value={info.country}
            onChange={(v) => set("country", v)}
            validate={infoRules.country}
            placeholder="např. CZ"
          />
          <Field
            label="DIČ (volitelné)"
            value={info.taxId}
            onChange={(v) => set("taxId", v)}
            placeholder="např. CZ12345678"
          />
          <Field
            label="IČO (povinné pro obchodníka)"
            value={info.ico}
            onChange={(v) => set("ico", v)}
            placeholder="např. 12345678"
          />
          <div className="sm:col-span-2">
            <Field
              label="IBAN"
              required
              value={info.iban}
              onChange={(v) => set("iban", v)}
              validate={infoRules.iban}
              placeholder="např. CZ65 0800 0000 1920 0014 5399"
              hint="Kontrolujeme formát i kontrolní součet okamžitě při psaní."
            />
          </div>
          <label className="text-sm sm:col-span-2 flex items-center gap-2">
            <input
              type="checkbox"
              checked={info.selfCertification}
              onChange={(e) => set("selfCertification", e.target.checked)}
            />
            Čestné prohlášení (povinné pro obchodníka)
          </label>
          <button className="sm:col-span-2 bg-navy hover:bg-navydeep text-white rounded py-2 font-semibold text-sm">
            Uložit profil
          </button>
        </form>

        {info.sellerType === "trader" && (
          <div className="mt-4 pt-4 border-t border-gray-100">
            <p className="text-sm font-medium text-gray-700">Ověření obchodníka (DSA)</p>
            {verification && (
              <p
                className={`text-xs mt-1 mb-2 rounded px-2 py-1 border ${
                  verification.status === "verified"
                    ? "bg-green-50 border-green-200 text-green-800"
                    : verification.status === "rejected"
                      ? "bg-amber-50 border-amber-200 text-amber-800"
                      : "bg-blue-50 border-blue-200 text-blue-900"
                }`}
              >
                {VERIFICATION_TEXT[verification.status]}
                {verification.note && ` — poznámka: ${verification.note}`}
              </p>
            )}
            <div className="flex flex-wrap gap-2 items-center">
              <input
                type="file"
                accept="image/*,application/pdf"
                onChange={(e) => setDoc(e.target.files?.[0] ?? null)}
                className="text-sm"
              />
              <button
                onClick={() => void uploadDoc()}
                disabled={!doc}
                className="border border-navy text-navy rounded px-3 py-1.5 text-sm font-semibold hover:bg-navy hover:text-white disabled:opacity-50"
              >
                {verification?.hasDocument ? "Nahrát nový doklad" : "Nahrát doklad"}
              </button>
            </div>
            <p className="text-xs text-gray-500 mt-2">
              Obrázek nebo PDF, max 5 MB. Obchodník musí mít IČO, doklad a čestné prohlášení.
              Doklad posoudí administrátor; zobrazuje se veřejně jen jeho stav, ne obsah.
            </p>
          </div>
        )}
      </Section>

      <Section title="Moje inzeráty">
        {!mine.length && (
          <p className="text-sm text-gray-500">
            Zatím žádné.{" "}
            <Link to="/prodat" className="text-brand font-semibold">
              Prodat
            </Link>
          </p>
        )}
        {mine.map((l) => (
          <div
            key={l.id}
            className="flex justify-between items-center gap-2 py-2 border-b border-gray-100 text-sm"
          >
            <Link to={`/inzerat/${l.id}`} className="font-medium hover:text-navy truncate">
              {l.title} · {fmtPrice(l.priceCzk)}
            </Link>
            <span className="flex gap-3 shrink-0">
              <Link to={`/inzerat/${l.id}/upravit`} className="text-navy font-semibold">
                Upravit
              </Link>
              <button
                onClick={() => {
                  void api
                    .deleteListing(l.id)
                    .then(() => setMine(mine.filter((x) => x.id !== l.id)))
                    .catch((e: Error) => setErr(e.message));
                }}
                className="text-brand"
              >
                Smazat
              </button>
            </span>
          </div>
        ))}
      </Section>

      <Section title={`Uložené inzeráty${favIds.length ? ` (${favIds.length})` : ""}`}>
        {!favIds.length && (
          <p className="text-sm text-gray-500">
            Zatím žádné. U inzerátu klikněte na „Přidat do oblíbených“.
          </p>
        )}
        {!!favIds.length && (
          <Link
            to="/oblibene"
            className="inline-block bg-navy hover:bg-navydeep text-white rounded px-4 py-2 text-sm font-semibold"
          >
            ♡ Zobrazit uložené ({favIds.length})
          </Link>
        )}
      </Section>

      <Section title="Oprávnění pro web">
        {!grants.length && (
          <p className="text-sm text-gray-500 mb-3">Zatím jste webu neudělili žádná oprávnění.</p>
        )}
        {!!grants.length && (
          <ul className="mb-4 flex flex-col gap-2">
            {grants.map((g, i) => (
              <li
                key={i}
                className="flex items-center justify-between gap-2 border border-gray-100 rounded px-3 py-2 text-sm"
              >
                <span>
                  <strong>{purposeLabel(g.purpose)}</strong>
                  <span className="block text-xs text-gray-500">
                    {purposeDesc(g.purpose)}
                    {g.createdAt ? ` · ${new Date(g.createdAt).toLocaleString("cs-CZ")}` : ""}
                  </span>
                </span>
                {g.granted ? (
                  <span className="text-xs font-semibold text-green-700 bg-green-50 border border-green-200 rounded px-2 py-0.5">
                    Povoleno
                  </span>
                ) : (
                  <span className="text-xs font-semibold text-gray-500 bg-gray-100 border border-gray-200 rounded px-2 py-0.5">
                    Odmítnuto
                  </span>
                )}
              </li>
            ))}
          </ul>
        )}
        <div className="flex flex-wrap gap-2 items-center">
          <select
            value={consent.purpose}
            onChange={(e) => setConsent({ ...consent, purpose: e.target.value })}
            className="border border-gray-300 rounded px-2 py-1.5 text-sm"
          >
            {Object.keys(PURPOSE_LABELS).map((p) => (
              <option key={p} value={p}>
                {PURPOSE_LABELS[p][0]}
              </option>
            ))}
          </select>
          <label className="text-sm flex items-center gap-1">
            <input
              type="checkbox"
              checked={consent.granted}
              onChange={(e) => setConsent({ ...consent, granted: e.target.checked })}
            />
            povolit
          </label>
          <button
            onClick={() => void sendConsent()}
            className="border border-navy text-navy rounded px-3 py-1.5 text-sm font-semibold"
          >
            Uložit volbu
          </button>
        </div>
      </Section>

      <Section title="Moje data">
        <div className="flex flex-wrap gap-2">
          <button
            onClick={() => void exportMe()}
            className="border border-navy text-navy rounded px-3 py-1.5 text-sm font-semibold"
          >
            Export dat (JSON)
          </button>
          <button
            onClick={() => void deleteMe()}
            className="bg-brand text-white rounded px-3 py-1.5 text-sm font-semibold"
          >
            Smazat účet
          </button>
        </div>
        <p className="text-xs text-gray-500 mt-2">
          Smazání je soft-delete; daňový profil zůstává z DAC7 důvodů. Backend: {API_URL}
        </p>
      </Section>
    </div>
  );
}