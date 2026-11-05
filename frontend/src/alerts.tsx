import {
  createContext, useCallback, useContext, useEffect, useRef, useState,
  type ReactNode,
} from "react";
import { api } from "./api";
import type { Notification } from "./types";

/** Nepřečtená upozornění (zlevnění uložených inzerátů) a nepřečtené chaty. */
export interface Alert {
  kind: "notification" | "chat";
  id: number | string;
  title: string;
  subtitle?: string;
  href: string;
}

interface AlertsValue {
  notifications: Notification[];
  unread: number;
  chatCount: number;
  alerts: Alert[];
  markNotificationRead: (id: number) => Promise<void>;
  markAllNotificationsRead: () => Promise<void>;
  markConversationSeen: (conversationId: number, latestId: number) => void;
  refresh: () => Promise<void>;
}

const AlertsCtx = createContext<AlertsValue | null>(null);

/**
 * Backend nemá doručenky zpráv, proto si držíme ID naposledy přečtené zprávy
 * v localStorage. Nepřečtená upozornění na zlevnění už backend vrací sám.
 */
const SEEN_KEY = "bazar-seen";

function loadSeen(): Record<string, number> {
  try {
    const parsed: unknown = JSON.parse(localStorage.getItem(SEEN_KEY) ?? "{}");
    return parsed && typeof parsed === "object" ? (parsed as Record<string, number>) : {};
  } catch {
    return {};
  }
}

function storeSeen(map: Record<string, number>): void {
  localStorage.setItem(SEEN_KEY, JSON.stringify(map));
}

export function AlertsProvider({
  user,
  children,
}: {
  user: { id: number } | null;
  children: ReactNode;
}): React.ReactElement {
  const [notifications, setNotifications] = useState<Notification[]>([]);
  const [chatAlerts, setChatAlerts] = useState<Alert[]>([]);
  const timer = useRef<ReturnType<typeof setInterval> | null>(null);

  const loadNotifications = useCallback(async () => {
    const page = await api.notifications();
    setNotifications(page.data ?? []);
  }, []);

  /** Zjistí nepřečtené zprávy: porovná poslední zprávu v konverzaci s uloženým ID. */
  const loadChat = useCallback(async () => {
    const convs = await api.conversations();
    const seen = loadSeen();
    const out: Alert[] = [];
    for (const c of convs.data ?? []) {
      try {
        const p = await api.messages(c.id, undefined);
        const latest = p.data?.[0];
        if (!latest || latest.senderId === user?.id) continue;
        if (latest.id <= (seen[c.id] ?? 0)) continue;
        let from = `#${latest.senderId}`;
        let listingTitle = `inzerát #${c.listingId}`;
        const [u, l] = await Promise.all([
          api.profile(latest.senderId).catch(() => null),
          api.listing(c.listingId).catch(() => null),
        ]);
        if (u) from = u.displayName || from;
        if (l) listingTitle = l.title || listingTitle;
        out.push({
          kind: "chat",
          id: c.id,
          title: from,
          subtitle: `${listingTitle} — ${latest.bodyText?.slice(0, 60) ?? ""}`,
          href: `/chat/${c.id}`,
        });
      } catch {
        /* jedna rozbitá konverzace nesmí rozbít odznak */
      }
    }
    setChatAlerts(out);
  }, [user?.id]);

  const refresh = useCallback(async () => {
    if (!user) {
      setNotifications([]);
      setChatAlerts([]);
      return;
    }
    try {
      await Promise.all([loadNotifications(), loadChat()]);
    } catch {
      /* offline nebo odhlášený — necháme poslední stav */
    }
  }, [user, loadNotifications, loadChat]);

  useEffect(() => {
    void refresh();
    if (timer.current) clearInterval(timer.current);
    if (user) timer.current = setInterval(() => void refresh(), 20_000);
    return () => {
      if (timer.current) clearInterval(timer.current);
    };
  }, [user, refresh]);

  const markNotificationRead = useCallback(
    async (id: number) => {
      setNotifications((prev) =>
        prev.map((n) => (n.id === id ? { ...n, read: true } : n)),
      );
      try {
        await api.markRead(id);
      } catch {
        void loadNotifications(); // nechápeme stav serveru, načteme znovu
      }
    },
    [loadNotifications],
  );

  const markAllNotificationsRead = useCallback(async () => {
    setNotifications((prev) => prev.map((n) => ({ ...n, read: true })));
    try {
      await api.markAllRead();
    } catch {
      void loadNotifications();
    }
  }, [loadNotifications]);

  const markConversationSeen = useCallback((conversationId: number, latestId: number) => {
    const seen = loadSeen();
    seen[conversationId] = Math.max(seen[conversationId] ?? 0, latestId);
    storeSeen(seen);
    setChatAlerts((prev) => prev.filter((a) => a.id !== conversationId));
  }, []);

  const unreadNotifications = notifications.filter((n) => !n.read);

  const alerts: Alert[] = [
    ...unreadNotifications.map((n) => ({
      kind: "notification" as const,
      id: n.id,
      title: n.title,
      subtitle:
        n.oldPriceCzk != null && n.newPriceCzk != null
          ? `Cena klesla z ${n.oldPriceCzk} Kč na ${n.newPriceCzk} Kč`
          : undefined,
      href: `/inzerat/${n.listingId}`,
    })),
    ...chatAlerts,
  ];

  return (
    <AlertsCtx.Provider
      value={{
        notifications,
        unread: unreadNotifications.length,
        chatCount: chatAlerts.length,
        alerts,
        markNotificationRead,
        markAllNotificationsRead,
        markConversationSeen,
        refresh,
      }}
    >
      {children}
    </AlertsCtx.Provider>
  );
}

export function useAlerts(): AlertsValue {
  const ctx = useContext(AlertsCtx);
  if (!ctx) throw new Error("useAlerts musí být použito uvnitř <AlertsProvider>.");
  return ctx;
}