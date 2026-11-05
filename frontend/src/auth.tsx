import {
  createContext, useCallback, useContext, useEffect, useState,
  type ReactNode,
} from "react";
import { api, saveTokens } from "./api";
import type { Session } from "./types";
import { AUTH_EVT } from "./components/favs";

const USER_KEY = "bazar-user";

interface AuthValue {
  user: Session | null;
  loggedIn: boolean;
  login: (email: string, password: string) => Promise<void>;
  register: (email: string, password: string, displayName: string, sellerType: string) => Promise<void>;
  logout: () => void;
  /** Načte veřejné profilové údaje (zobrazované jméno) do relace. */
  refreshUser: () => Promise<void>;
}

const AuthCtx = createContext<AuthValue | null>(null);

function readSession(): Session | null {
  try {
    return JSON.parse(localStorage.getItem(USER_KEY) ?? "null") as Session | null;
  } catch {
    return null;
  }
}

/** Doplní do relace veřejné zobrazované jméno (avatar, jméno prodejce). */
async function loadDisplayName(id: number): Promise<string | undefined> {
  try {
    const p = await api.profile(id);
    return p.displayName;
  } catch {
    return undefined;
  }
}

export function AuthProvider({ children }: { children: ReactNode }): React.ReactElement {
  const [user, setUser] = useState<Session | null>(readSession);

  // Relace se zrcadlí v localStorage, aby přežila reload stránky.
  useEffect(() => {
    if (user) localStorage.setItem(USER_KEY, JSON.stringify(user));
    else localStorage.removeItem(USER_KEY);
  }, [user]);

  const refreshUser = useCallback(async () => {
    const current = readSession();
    if (!current || current.displayName) return;
    const displayName = await loadDisplayName(current.id);
    if (displayName) setUser({ id: current.id, displayName });
  }, []);

  useEffect(() => {
    void refreshUser();
  }, [refreshUser]);

  /** Uloží tokeny, načte profil a upozorní ostatní hooky (oblíbené, chat). */
  const establishSession = useCallback(async (id: number, token: string, refreshToken: string) => {
    saveTokens({ token, refreshToken });
    window.dispatchEvent(new Event(AUTH_EVT));
    setUser({ id, displayName: await loadDisplayName(id) });
  }, []);

  const login = useCallback(
    async (email: string, password: string) => {
      const data = await api.login(email, password);
      await establishSession(data.id, data.token, data.refreshToken);
    },
    [establishSession],
  );

  const register = useCallback(
    async (email: string, password: string, displayName: string, sellerType: string) => {
      const data = await api.register(email, password, displayName, sellerType);
      await establishSession(data.id, data.token, data.refreshToken);
    },
    [establishSession],
  );

  const logout = useCallback(() => {
    saveTokens(null);
    setUser(null);
    window.dispatchEvent(new Event(AUTH_EVT));
  }, []);

  return (
    <AuthCtx.Provider value={{ user, loggedIn: !!user, login, register, logout, refreshUser }}>
      {children}
    </AuthCtx.Provider>
  );
}

export function useAuth(): AuthValue {
  const ctx = useContext(AuthCtx);
  if (!ctx) throw new Error("useAuth musí být použito uvnitř <AuthProvider>.");
  return ctx;
}