import { useEffect, useState, type ReactElement } from "react";
import { api } from "../api";
import { useAuth } from "../auth";

/** Banner o souborech cookie (výklad souhlasu GDPR). */
export default function ConsentBanner(): ReactElement | null {
  const { user } = useAuth();
  const [visible, setVisible] = useState(false);

  useEffect(() => {
    if (!localStorage.getItem("bazar-consent")) setVisible(true);
  }, []);

  const answer = async (granted: boolean) => {
    localStorage.setItem("bazar-consent", granted ? "granted" : "denied");
    setVisible(false);
    if (user) {
      try {
        await api.consent("cookies", granted);
      } catch {
        /* banner nesmí nikdy rozbít stránku */
      }
    }
  };

  if (!visible) return null;
  return (
    <div className="fixed bottom-0 inset-x-0 z-50 bg-navydeep text-white px-4 py-3">
      <div className="container mx-auto flex flex-col md:flex-row items-center justify-between gap-3 text-sm">
        <p>
          Používáme soubory cookies pro základní funkčnost bazaru. Podrobnosti v podmínkách.
        </p>
        <div className="flex gap-2">
          <button
            onClick={() => void answer(true)}
            className="bg-white text-navydeep rounded px-4 py-1.5 font-semibold text-sm"
          >
            Souhlasím
          </button>
          <button
            onClick={() => void answer(false)}
            className="border border-white/60 rounded px-4 py-1.5 text-sm"
          >
            Odmítnout
          </button>
        </div>
      </div>
    </div>
  );
}