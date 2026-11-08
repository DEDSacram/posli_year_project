/**
 * Sdílení a „Hledat přes Lens“.
 * Bez backendu a bez externích dat: Google nemá veřejné Lens fill API,
 * proto odkaz otevře lens.google.com a uživatel tam fotku vloží.
 */
export function lensUploadUrl(): string {
  return "https://lens.google.com/upload";
}

export function lensSearchByUrl(imageSrc: string): string {
  try {
    const abs = new URL(imageSrc, window.location.origin).toString();
    return `https://lens.google.com/uploadbyurl?url=${encodeURIComponent(abs)}`;
  } catch {
    return lensUploadUrl();
  }
}

export function whatsappShareUrl(text: string): string {
  return `https://wa.me/?text=${encodeURIComponent(text)}`;
}

/** Zkopíruje text do schránky; vrací false, pokud prohlížeč nedovolil. */
export async function copyLink(text: string): Promise<boolean> {
  try {
    await navigator.clipboard.writeText(text);
    return true;
  } catch {
    try {
      const ta = document.createElement("textarea");
      ta.value = text;
      document.body.appendChild(ta);
      ta.select();
      document.execCommand("copy");
      ta.remove();
      return true;
    } catch {
      return false;
    }
  }
}