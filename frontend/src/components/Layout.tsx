import { useEffect, useRef, useState, type FormEvent, type ReactElement, type ReactNode } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../auth";
import { useAlerts } from "../alerts";
import { useFavIds } from "./favs";
import ConsentBanner from "./ConsentBanner";

export function Logo({ compact = false }: { compact?: boolean }): ReactElement {
  return (
    <Link to="/" className="flex items-center gap-2 shrink-0">
      <img src="/small_logo.png" alt="poslitodal" className={compact ? "h-6 w-auto" : "h-9 w-auto"} />
      <span className="flex flex-col leading-none">
        <span className={`font-bold text-navy tracking-tight ${compact ? "text-base" : "text-xl"}`}>
          poslitodal
        </span>
        <span className="font-bold text-brand text-xs tracking-wider uppercase">Bazar</span>
      </span>
    </Link>
  );
}

function SearchBox({ className = "", onDone }: { className?: string; onDone?: () => void }): ReactElement {
  const [q, setQ] = useState("");
  const nav = useNavigate();
  return (
    <form
      onSubmit={(e: FormEvent) => {
        e.preventDefault();
        onDone?.();
        const term = q.trim();
        nav(term ? `/hledat?q=${encodeURIComponent(term)}` : "/");
      }}
      className={`relative ${className}`}
    >
      <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none text-gray-400">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <circle cx="11" cy="11" r="8" />
          <line x1="21" y1="21" x2="16.65" y2="16.65" />
        </svg>
      </div>
      <input
        value={q}
        onChange={(e) => setQ(e.target.value)}
        placeholder="Hledat inzeráty…"
        aria-label="Hledat inzeráty"
        className="w-full pl-9 pr-4 py-1.5 bg-white border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-1 focus:ring-navy focus:border-navy"
      />
    </form>
  );
}

/** Avatar s odznakem nepřečtených oznámení a rozbalovacím menu. */
function ProfileMenu(): ReactElement {
  const { user, logout } = useAuth();
  const { alerts, unread, chatCount, markNotificationRead, markAllNotificationsRead } = useAlerts();
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  const nav = useNavigate();

  useEffect(() => {
    const close = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener("mousedown", close);
    return () => document.removeEventListener("mousedown", close);
  }, []);

  const go = (to: string) => {
    setOpen(false);
    nav(to);
  };

  const openAlert = (alert: { kind: string; id: number | string; href: string }) => {
    if (alert.kind === "notification") void markNotificationRead(Number(alert.id));
    go(alert.href);
  };

  const total = unread + chatCount;
  const initial = (user?.displayName || "?").trim().charAt(0).toUpperCase() || "?";

  return (
    <div ref={ref} className="relative">
      <button
        onClick={() => setOpen((o) => !o)}
        aria-label="Profil a oznámení"
        aria-expanded={open}
        className="relative w-9 h-9 rounded-full bg-navy text-white font-bold flex items-center justify-center hover:bg-navydeep"
      >
        {initial}
        {total > 0 && (
          <span className="absolute -top-1 -right-1 min-w-[20px] h-5 px-1 rounded-full bg-brand text-white text-xs font-bold flex items-center justify-center">
            {total > 9 ? "9+" : total}
          </span>
        )}
      </button>

      {open && (
        <div className="absolute right-0 mt-2 w-80 bg-white border border-gray-200 rounded-lg shadow-lg overflow-hidden z-50">
          <div className="px-4 py-2 border-b border-gray-100 font-semibold text-sm flex items-center justify-between">
            <span>{user?.displayName || "Můj účet"}</span>
            {unread > 0 && (
              <button
                onClick={() => void markAllNotificationsRead()}
                className="text-xs font-normal text-navy hover:underline"
              >
                Přečíst vše
              </button>
            )}
          </div>

          <div className="max-h-72 overflow-y-auto">
            {alerts.length === 0 && (
              <p className="px-4 py-3 text-sm text-gray-500">Žádná nová oznámení.</p>
            )}
            {alerts.map((a) => (
              <button
                key={`${a.kind}-${a.id}`}
                onClick={() => openAlert(a)}
                className="w-full text-left px-4 py-2.5 hover:bg-cream border-b border-gray-50"
              >
                <p className="text-sm font-semibold">
                  {a.kind === "chat" ? "💬" : "🏷️"} {a.title}
                </p>
                {a.subtitle && <p className="text-xs text-gray-600 truncate">{a.subtitle}</p>}
              </button>
            ))}
          </div>

          <div className="border-t border-gray-100 text-sm">
            <button onClick={() => go("/ucet")} className="block w-full text-left px-4 py-2 hover:bg-cream">
              👤 Můj účet
            </button>
            <button onClick={() => go("/chat")} className="block w-full text-left px-4 py-2 hover:bg-cream">
              ✉️ Zprávy
              {chatCount > 0 && <span className="ml-1 text-xs font-bold text-brand">({chatCount} nové)</span>}
            </button>
            <button onClick={() => go("/obchody")} className="block w-full text-left px-4 py-2 hover:bg-cream">
              📦 Moje obchody
            </button>
            <button onClick={() => go("/oblibene")} className="block w-full text-left px-4 py-2 hover:bg-cream">
              ♡ Oblíbené
            </button>
            <button
              onClick={() => {
                setOpen(false);
                logout();
                nav("/");
              }}
              className="block w-full text-left px-4 py-2 hover:bg-cream text-gray-600"
            >
              Odhlásit
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

const NAV_LINKS: ReadonlyArray<readonly [string, string]> = [
  ["/hledat", "Kategorie"],
  ["/prodat", "Prodat"],
  ["/obchody", "Moje obchody"],
];

function FavLink({ className = "", onNavigate, mobile = false }: {
  className?: string;
  onNavigate?: () => void;
  mobile?: boolean;
}): ReactElement {
  const { ids } = useFavIds();
  const count = ids.length;
  const badge = count > 9 ? "9+" : String(count);

  if (mobile) {
    return (
      <Link to="/oblibene" onClick={onNavigate} className={className}>
        ♡ Oblíbené{" "}
        {count > 0 && <span className="ml-1 text-xs font-bold text-brand">({count})</span>}
      </Link>
    );
  }
  return (
    <Link to="/oblibene" className={`${className} relative`}>
      ♡ Oblíbené
      {count > 0 && (
        <span className="ml-1 inline-flex items-center justify-center min-w-[20px] h-5 px-1 rounded-full bg-brand text-white text-xs font-bold">
          {badge}
        </span>
      )}
    </Link>
  );
}

export default function Layout({ children }: { children: ReactNode }): ReactElement {
  const { user } = useAuth();
  const [mobileOpen, setMobileOpen] = useState(false);

  return (
    <div className="min-h-screen flex flex-col text-gray-800">
      <header className="bg-white border-b border-gray-200 sticky top-0 z-50">
        <div className="container mx-auto px-4 lg:px-8 py-3 flex items-center justify-between gap-3">
          <Logo />

          <nav className="hidden md:flex items-center space-x-6 text-sm font-medium text-gray-700">
            <Link to="/hledat" className="flex items-center gap-1 hover:text-navy">
              Kategorie
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <polyline points="6 9 12 15 18 9" />
              </svg>
            </Link>
            <Link to="/prodat" className="hover:text-navy">
              Prodat
            </Link>
            <Link to="/obchody" className="hover:text-navy">
              Moje obchody
            </Link>
            <FavLink className="hover:text-navy" />
            <Link to={user ? "/ucet" : "/prihlaseni"} className="hover:text-navy">
              Můj účet
            </Link>
            {user && (
              <Link to="/chat" className="hover:text-navy">
                Zprávy
              </Link>
            )}
          </nav>

          <div className="flex items-center gap-2">
            <SearchBox className="hidden md:block md:min-w-[250px]" />
            {user ? (
              <ProfileMenu />
            ) : (
              <div className="hidden md:flex items-center gap-3 text-sm font-medium">
                <Link to="/prihlaseni" className="hover:text-navy">
                  Přihlásit
                </Link>
                <Link to="/registrace" className="text-brand font-semibold hover:text-branddark">
                  Registrace
                </Link>
              </div>
            )}
            <button
              onClick={() => setMobileOpen((o) => !o)}
              aria-label="Menu"
              aria-expanded={mobileOpen}
              className="md:hidden w-9 h-9 flex flex-col items-center justify-center gap-1.5 border border-gray-300 rounded-md"
            >
              <span className={`block w-5 h-0.5 bg-navydeep transition-transform ${mobileOpen ? "translate-y-[7px] rotate-45" : ""}`} />
              <span className={`block w-5 h-0.5 bg-navydeep ${mobileOpen ? "opacity-0" : ""}`} />
              <span className={`block w-5 h-0.5 bg-navydeep transition-transform ${mobileOpen ? "-translate-y-[7px] -rotate-45" : ""}`} />
            </button>
          </div>
        </div>

        {mobileOpen && (
          <div className="md:hidden border-t border-gray-200 bg-white px-4 py-3 flex flex-col gap-1 text-[15px] font-medium">
            <SearchBox className="mb-2" onDone={() => setMobileOpen(false)} />
            {NAV_LINKS.map(([to, label]) => (
              <Link
                key={to}
                to={to}
                onClick={() => setMobileOpen(false)}
                className="py-2 border-b border-gray-100 hover:text-navy"
              >
                {label}
              </Link>
            ))}
            <FavLink
              mobile
              onNavigate={() => setMobileOpen(false)}
              className="py-2 border-b border-gray-100 hover:text-navy"
            />
            {user ? (
              <>
                <Link to="/ucet" onClick={() => setMobileOpen(false)} className="py-2 border-b border-gray-100 hover:text-navy">
                  👤 Můj účet
                </Link>
                <Link to="/chat" onClick={() => setMobileOpen(false)} className="py-2 border-b border-gray-100 hover:text-navy">
                  ✉️ Zprávy
                </Link>
                <Link to="/admin" onClick={() => setMobileOpen(false)} className="py-2 hover:text-navy">
                  Administrace
                </Link>
              </>
            ) : (
              <>
                <Link to="/prihlaseni" onClick={() => setMobileOpen(false)} className="py-2 border-b border-gray-100 hover:text-navy">
                  Přihlásit
                </Link>
                <Link to="/registrace" onClick={() => setMobileOpen(false)} className="py-2 text-brand font-semibold">
                  Registrace
                </Link>
              </>
            )}
          </div>
        )}
      </header>

      <main className="flex-grow">{children}</main>

      <footer className="bg-footgray py-6 mt-8">
        <div className="container mx-auto px-4 lg:px-8 flex flex-col md:flex-row justify-between items-center gap-4">
          <img src="/small_logo.png" alt="poslitodal" className="h-7 w-auto" />
          <div className="flex flex-wrap justify-center gap-x-6 gap-y-2 text-sm text-gray-600 font-medium">
            <Link to="/hledat" className="hover:text-gray-900">Kategorie</Link>
            <Link to="/prodat" className="hover:text-gray-900">Prodat</Link>
            <Link to="/obchody" className="hover:text-gray-900">Moje obchody</Link>
            <Link to="/oblibene" className="hover:text-gray-900">Oblíbené</Link>
            <Link to={user ? "/ucet" : "/prihlaseni"} className="hover:text-gray-900">Můj účet</Link>
            <Link to="/hledat" className="hover:text-gray-900">Lokality</Link>
          </div>
          <p className="text-xs text-gray-500 max-w-sm text-center md:text-right">
            C2C bazar použitého zboží. Obchodujte férově — respektujte právo spotřebitele na odstoupení
            od smlouvy a zákon o ochraně osobních údajů.
          </p>
        </div>
      </footer>
      <ConsentBanner />
    </div>
  );
}