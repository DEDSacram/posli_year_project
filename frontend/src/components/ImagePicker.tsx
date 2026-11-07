import { useRef, type DragEvent, type ReactElement } from "react";

/** Fotka čekající na nahrání — pořadí v poli je pořadí nahrávání na backend. */
export interface PendingImage {
  key: string;
  file: File;
  url: string;
}

export const MAX_PHOTOS = 10;
const MAX_SIZE = 5 * 1024 * 1024;
const ALLOWED = ["image/jpeg", "image/png", "image/webp"];

let uid = 0;

export function imageFileError(f: File): string {
  if (!ALLOWED.includes(f.type)) return `"${f.name}": povolen je pouze JPEG, PNG nebo WebP.`;
  if (f.size > MAX_SIZE) return `"${f.name}": soubor je větší než 5 MB.`;
  return "";
}

/**
 * Sdílený výběr fotek: náhledy, změna pořadí (← →), mazání.
 * První fotka je hlavní (`sortOrder = 0` na backendu).
 */
export default function ImagePicker({
  items,
  onChange,
}: {
  items: PendingImage[];
  onChange: (next: PendingImage[]) => void;
}): ReactElement {
  const inputRef = useRef<HTMLInputElement>(null);

  const add = (list: File[]) => {
    const errors: string[] = [];
    const next = [...items];
    for (const f of list) {
      const e = imageFileError(f);
      if (e) {
        errors.push(e);
        continue;
      }
      if (next.length >= MAX_PHOTOS) {
        errors.push(`Lze přidat nejvýš ${MAX_PHOTOS} fotek.`);
        break;
      }
      next.push({ key: `${Date.now()}-${uid++}`, file: f, url: URL.createObjectURL(f) });
    }
    if (errors.length) alert(errors.join("\n"));
    onChange(next);
    if (inputRef.current) inputRef.current.value = "";
  };

  const removeAt = (i: number) => {
    URL.revokeObjectURL(items[i].url);
    onChange(items.filter((_, j) => j !== i));
  };

  const move = (i: number, dir: -1 | 1) => {
    const j = i + dir;
    if (j < 0 || j >= items.length) return;
    const next = [...items];
    [next[i], next[j]] = [next[j], next[i]];
    onChange(next);
  };

  /** Vložení ze schránky (Ctrl+V / snímek obrazovky) — stejná validace jako výběr souborů. */
  const paste = (e: React.ClipboardEvent) => {
    const files = [...(e.clipboardData?.files ?? [])].filter((f) => f.type.startsWith("image/"));
    if (files.length) {
      e.preventDefault();
      add(files);
    }
  };

  const drop = (e: DragEvent) => {
    e.preventDefault();
    const files = [...(e.dataTransfer?.files ?? [])].filter((f) => f.type.startsWith("image/"));
    if (files.length) add(files);
  };

  return (
    <div onPaste={paste} onDragOver={(e) => e.preventDefault()} onDrop={drop}>
      <p className="text-sm font-medium text-gray-700 mb-2">
        Fotky{" "}
        {items.length > 0 && (
          <span className="font-normal text-gray-500">
            ({items.length}/{MAX_PHOTOS}, první je hlavní)
          </span>
        )}
      </p>
      <div className="flex gap-2 flex-wrap">
        {items.map((it, i) => (
          <div key={it.key} className="relative w-24">
            <div
              className={`aspect-square rounded-lg overflow-hidden border-2 ${
                i === 0 ? "border-brand" : "border-gray-300"
              }`}
            >
              <img src={it.url} alt={`Fotka ${i + 1}`} className="w-full h-full object-cover" />
            </div>
            {i === 0 && (
              <span className="absolute top-1 left-1 bg-brand text-white text-[10px] font-bold rounded px-1.5 py-0.5">
                HLAVNÍ
              </span>
            )}
            <button
              type="button"
              onClick={() => removeAt(i)}
              aria-label={`Odebrat fotku ${i + 1}`}
              className="absolute top-1 right-1 bg-black/60 hover:bg-brand text-white text-xs rounded-full w-5 h-5 flex items-center justify-center"
            >
              ✕
            </button>
            <div className="flex gap-1 mt-1">
              <button
                type="button"
                onClick={() => move(i, -1)}
                disabled={i === 0}
                aria-label="Přesunout doleva"
                className="flex-1 border border-gray-300 rounded text-xs py-0.5 disabled:opacity-30 hover:bg-gray-100"
              >
                ←
              </button>
              <button
                type="button"
                onClick={() => move(i, 1)}
                disabled={i === items.length - 1}
                aria-label="Přesunout doprava"
                className="flex-1 border border-gray-300 rounded text-xs py-0.5 disabled:opacity-30 hover:bg-gray-100"
              >
                →
              </button>
            </div>
          </div>
        ))}
        {items.length < MAX_PHOTOS && (
          <button
            type="button"
            onClick={() => inputRef.current?.click()}
            className="w-24 aspect-square rounded-lg border-2 border-dashed border-gray-300 hover:border-navy text-gray-400 hover:text-navy flex flex-col items-center justify-center gap-1 text-xs font-medium"
          >
            <span className="text-2xl leading-none">+</span>
            Přidat fotky
          </button>
        )}
      </div>
      <input
        ref={inputRef}
        type="file"
        accept="image/jpeg,image/png,image/webp"
        multiple
        onChange={(e) => add([...(e.target.files ?? [])])}
        className="hidden"
      />
      <p className="text-gray-400 text-xs mt-1">
        JPEG / PNG / WebP, max 5 MB za soubor. Pořadí změníte šipkami. Jde i přetáhnout nebo vložit ze
        schránky (Ctrl+V).
      </p>
    </div>
  );
}