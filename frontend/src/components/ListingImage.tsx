import { useEffect, useState, type ImgHTMLAttributes, type ReactElement } from "react";
import { fetchListingImageUrl } from "../api";

/** Průhledný 1×1 px obrázek, dokud se fotka nestáhne (žádný "rozbitý" ikonový placeholder). */
const BLANK = "data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///ywAAAAAAQABAAACAUwAOw==";

type ListingImageProps = Omit<ImgHTMLAttributes<HTMLImageElement>, "src"> & {
  listingId: number;
  image: { id: number };
};

/**
 * <img> s autentizací. Prohlížeč k `<img src>` neposílá Authorization,
 * proto data načteme přes fetch se stejným tokenem jako ostatní API volání
 * a výsledek zobrazíme přes blob: URL (nutné pro dokončené inzeráty).
 */
export default function ListingImage({
  listingId, image, alt = "", ...rest
}: ListingImageProps): ReactElement {
  const [loaded, setLoaded] = useState<{ id: number; url: string } | null>(null);
  const imgId = image.id;
  const src = loaded?.id === imgId ? loaded.url : BLANK;

  useEffect(() => {
    let active = true;
    let objectUrl: string | null = null;
    fetchListingImageUrl(listingId, { id: imgId })
      .then((url) => {
        objectUrl = url;
        if (active) setLoaded({ id: imgId, url });
        else URL.revokeObjectURL(url);
      })
      .catch(() => { /* ponecháme prázdný obrázek, ostatní obsah zůstane */ });
    return () => {
      active = false;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [listingId, imgId]);

  return <img {...rest} src={src} alt={alt} />;
}
