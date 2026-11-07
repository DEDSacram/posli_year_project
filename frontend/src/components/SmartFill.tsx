import { useState, type ReactElement } from "react";
import { api, fmtPrice } from "../api";
import type { PendingImage } from "./ImagePicker";
import { catLabel } from "./options";
import { lensUploadUrl } from "./share";

interface Suggestion {
  categoryGuess?: string | null;
  medianPriceCzk?: number | null;
  sampleSize?: number | null;
}

interface SmartFillProps {
  title: string;
  category: string;
  photos: PendingImage[];
  onApply: (patch: { title?: string; category?: string }) => void;
}

/** Z názvu fotky vytvoří rozumný název (přeskočí IMG_1234, DSC0001, screenshot…). */
export function fileNameHint(file: File | undefined): string {
  if (!file?.name) return "";
  const base = file.name
    .replace(/\.[a-z0-9]+$/i, "")
    .replace(/[_+]+/g, " ")
    .replace(/-+/g, " ")
    .trim();
  if (base.length < 3) return "";
  if (/^img|^dsc|^photo|^pxl|^screenshot/i.test(base)) return "";
  return base.length > 80 ? base.slice(0, 80) : base;
}

/** Minimální tvar BarcodeDetectoru, který používáme (je v libovolném prohlížeči). */
interface DetectedBarcode {
  rawValue: string;
}
interface BarcodeDetectorLike {
  detect(source: ImageBitmap): Promise<DetectedBarcode[]>;
}
type BarcodeDetectorCtor = new (options: { formats: string[] }) => BarcodeDetectorLike;

const detectorCtor = (window as unknown as { BarcodeDetector?: BarcodeDetectorCtor }).BarcodeDetector;

/**
 * Rychlé doplnění formuláře z fotek — vše běží na zařízení, žádný externí
 * backend: odkaz na Google Lens (nemá veřejné API), čtení čárového kódu
 * přes BarcodeDetector a odhad kategorie s medianou ceny z našeho katalogu.
 */
export default function SmartFill({
  title, category, photos, onApply,
}: SmartFillProps): ReactElement {
  const [suggest, setSuggest] = useState<Suggestion | null>(null);
  const [suggestBusy, setSuggestBusy] = useState(false);
  const [code, setCode] = useState<string | null>(null);
  const [codeBusy, setCodeBusy] = useState(false);
  const [codeError, setCodeError] = useState("");

  const firstFile = photos[0]?.file;
  const nameHint = fileNameHint(firstFile);

  const fetchSuggest = async () => {
    setSuggestBusy(true);
    try {
      const d = await api.suggest(title || "", category || "");
      setSuggest(d);
    } catch {
      setSuggest(null);
    } finally {
      setSuggestBusy(false);
    }
  };

  const scanCode = async () => {
    setCodeError("");
    setCode(null);
    if (!firstFile) {
      setCodeError("Nejprve přidejte fotku s čárovým kódem.");
      return;
    }
    if (!detectorCtor) {
      setCodeError("Váš prohlížeč čtení kódů nepodporuje — zkuste Chrome nebo Edge na mobilu.");
      return;
    }
    setCodeBusy(true);
    try {
      const bmp = await createImageBitmap(firstFile);
      const detector = new detectorCtor({
        formats: ["ean_13", "ean_8", "upc_a", "upc_e", "qr_code", "code_128", "code_39"],
      });
      const found = await detector.detect(bmp);
      if (!found?.length) {
        setCodeError("Žádný kód na první fotce jsem nenašel. Zkuste ostrou fotku kódu zblízka.");
      } else {
        setCode(found[0].rawValue || "");
      }
    } catch {
      setCodeError("Čtení kódu selhalo — zkuste ostřejší fotku.");
    } finally {
      setCodeBusy(false);
    }
  };

  return (
    <div className="border border-dashed border-gray-300 rounded-xl p-3 bg-gray-50/60 flex flex-col gap-2">
      <p className="text-sm font-semibold text-gray-700">
        ⚡ Rychlé doplnění{" "}
        <span className="font-normal text-gray-400">(z fotek, na zařízení)</span>
      </p>
      <div className="flex flex-wrap gap-2">
        <a
          href={lensUploadUrl()}
          target="_blank"
          rel="noreferrer"
          className="border border-navy text-navy rounded px-3 py-1.5 text-sm font-semibold hover:bg-navy hover:text-white"
        >
          🔍 Hledat přes Google Lens
        </a>
        <button
          type="button"
          onClick={() => void scanCode()}
          disabled={codeBusy}
          className="border border-navy text-navy rounded px-3 py-1.5 text-sm font-semibold hover:bg-navy hover:text-white disabled:opacity-50"
        >
          {codeBusy ? "Čtu kód…" : "📷 Načíst čárový kód z fotky"}
        </button>
        <button
          type="button"
          onClick={() => void fetchSuggest()}
          disabled={suggestBusy || !title.trim()}
          title="Odhad kategorie + obvyklá cena podle názvu"
          className="border border-navy text-navy rounded px-3 py-1.5 text-sm font-semibold hover:bg-navy hover:text-white disabled:opacity-50"
        >
          {suggestBusy ? "Hádám…" : "💡 Odhadnout kategorii a cenu"}
        </button>
      </div>
      <p className="text-xs text-gray-400">
        Lens se otevře v novém okně — fotku tam přetáhněte a název pak zkopírujte sem. Kód i odhad
        běží lokálně nebo z našeho katalogu, nic se neposílá Googlu.
      </p>

      {nameHint && (
        <p className="text-sm bg-white border border-gray-200 rounded px-3 py-2">
          Název ze jména souboru: <strong>{nameHint}</strong>{" "}
          <button
            type="button"
            onClick={() => onApply({ title: nameHint })}
            className="text-navy font-semibold hover:underline text-sm"
          >
            Použít
          </button>
        </p>
      )}

      {code && (
        <p className="text-sm bg-white border border-gray-200 rounded px-3 py-2">
          Nalezený kód: <strong className="font-mono">{code}</strong>{" "}
          <button
            type="button"
            onClick={() => onApply({ title: `${title ? `${title} ` : ""}[${code}]` })}
            className="text-navy font-semibold hover:underline text-sm"
          >
            Vložit do názvu
          </button>
        </p>
      )}
      {codeError && <p className="text-xs text-red-600">{codeError}</p>}

      {suggest && (
        <div className="text-sm bg-white border border-gray-200 rounded px-3 py-2 flex flex-col gap-1">
          {!!suggest.categoryGuess && (
            <p>
              Odhad kategorie: <strong>{catLabel(suggest.categoryGuess)}</strong>{" "}
              {suggest.categoryGuess !== category && (
                <button
                  type="button"
                  onClick={() => onApply({ category: suggest.categoryGuess as string })}
                  className="text-navy font-semibold hover:underline text-sm"
                >
                  Použít
                </button>
              )}
            </p>
          )}
          {suggest.medianPriceCzk != null ? (
            <p className="text-gray-600">
              Podobné se prodává zhruba za{" "}
              <strong className="text-gray-900">{fmtPrice(suggest.medianPriceCzk)}</strong>
              <span className="text-gray-400"> (z {suggest.sampleSize}× aktivních)</span>
            </p>
          ) : (
            <p className="text-gray-500">
              V této kategorii zatím nemáme dost dat pro odhad ceny.
            </p>
          )}
        </div>
      )}
    </div>
  );
}