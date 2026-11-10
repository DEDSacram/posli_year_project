import {
  useEffect, useRef, useState, type FormEvent, type ReactElement,
} from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { api } from "../api";
import { useAuth } from "../auth";
import { useAlerts } from "../alerts";
import type { ChatMessage, Conversation } from "../types";

const nameCache = new Map<number, string>();
const titleCache = new Map<number, string>();

async function resolveName(userId: number): Promise<string> {
  const cached = nameCache.get(userId);
  if (cached) return cached;
  let name = `#${userId}`;
  try {
    const u = await api.profile(userId);
    name = u.displayName || name;
  } catch {
    /* jméno neznáme */
  }
  nameCache.set(userId, name);
  return name;
}

async function resolveTitle(listingId: number): Promise<string> {
  const cached = titleCache.get(listingId);
  if (cached) return cached;
  let title = `inzerát #${listingId}`;
  try {
    const l = await api.listing(listingId);
    title = l.title || title;
  } catch {
    /* nadpis neznáme */
  }
  titleCache.set(listingId, title);
  return title;
}

const QUICK_REPLIES: readonly string[] = [
  "Je to ještě dostupné?",
  "Šla by sleva?",
  "Kdy by bylo možné předání?",
  "Pošlete prosím více fotek.",
];

/** Seznam konverzací a samotný chat 1:1 navázaný na inzerát. */
export default function Chat(): ReactElement | null {
  const { id } = useParams();
  const { user } = useAuth();
  const { markConversationSeen, refresh } = useAlerts();
  const nav = useNavigate();
  const [convs, setConvs] = useState<Conversation[]>([]);
  const [labels, setLabels] = useState<Record<number, string>>({});
  const [names, setNames] = useState<Record<number, string>>({});
  const [titles, setTitles] = useState<Record<number, string>>({});
  const [msgs, setMsgs] = useState<ChatMessage[]>([]);
  const [draft, setDraft] = useState("");
  const bottom = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!user) {
      nav("/prihlaseni");
      return;
    }
    api
      .conversations()
      .then(async (d) => {
        const list = d.data ?? [];
        setConvs(list);
        const map: Record<number, string> = {};
        const nm: Record<number, string> = {};
        const ti: Record<number, string> = {};
        for (const c of list) {
          const otherId = c.buyerId === user.id ? c.sellerId : c.buyerId;
          const [n, t] = await Promise.all([resolveName(otherId), resolveTitle(c.listingId)]);
          map[c.id] = `${n} · ${t}`;
          nm[c.id] = n;
          ti[c.id] = t;
        }
        setLabels(map);
        setNames(nm);
        setTitles(ti);
      })
      .catch(() => {
        /* seznam konverzací se nepodařilo načíst */
      });
  }, [user, nav]);

  useEffect(() => {
    if (!id || !user) return;
    api
      .messages(Number(id))
      .then((p) => {
        const arr = [...(p.data ?? [])].reverse();
        setMsgs(arr);
        const last = arr[arr.length - 1];
        if (last) {
          markConversationSeen(Number(id), last.id);
          void refresh();
        }
      })
      .catch(() => setMsgs([]));
  }, [id, user, markConversationSeen, refresh]);

  useEffect(() => {
    bottom.current?.scrollIntoView({ behavior: "smooth" });
  }, [msgs]);

  if (!user) return null;

  const active = convs.find((c) => String(c.id) === String(id));

  const send = async (e: FormEvent) => {
    e.preventDefault();
    const text = draft.trim();
    if (!text) return;
    try {
      const m = await api.sendMessage(Number(id), text);
      setMsgs((prev) => [...prev, m]);
      markConversationSeen(Number(id), m.id);
      setDraft("");
    } catch (err) {
      alert((err as Error).message);
    }
  };

  return (
    <div className="container mx-auto px-4 py-8 max-w-5xl grid md:grid-cols-3 gap-6">
      <div className="bg-white border border-gray-200 rounded-xl p-3">
        <h2 className="font-bold text-navydeep px-2 py-1">Konverzace</h2>
        {!convs.length && <p className="text-sm text-gray-500 px-2">Zatím žádné.</p>}
        {convs.map((c) => (
          <Link
            key={c.id}
            to={`/chat/${c.id}`}
            title={labels[c.id] || `inzerát #${c.listingId}`}
            className={`block rounded px-2 py-2 text-sm hover:bg-cream truncate ${
              String(c.id) === String(id) ? "bg-cream font-semibold" : ""
            }`}
          >
            {labels[c.id] || `inzerát #${c.listingId}`}
          </Link>
        ))}
      </div>

      <div className="md:col-span-2 bg-white border border-gray-200 rounded-xl p-4 flex flex-col min-h-[400px]">
        {!id && <p className="text-sm text-gray-500">Vyberte konverzaci.</p>}
        {id && (
          <>
            {active && (
              <div className="flex items-center justify-between gap-2 border-b border-gray-100 pb-2 mb-3 text-sm">
                <p className="min-w-0 truncate text-gray-700">
                  {user.id === active.buyerId ? "Prodejce" : "Kupující"}:{" "}
                  <strong>{names[active.id] || "…"}</strong>
                  <span className="text-gray-500"> · {titles[active.id] || `inzerát #${active.listingId}`}</span>
                </p>
                <Link
                  to={`/inzerat/${active.listingId}`}
                  className="shrink-0 border border-navy text-navy rounded px-3 py-1 font-semibold hover:bg-navy hover:text-white"
                >
                  Otevřít inzerát →
                </Link>
              </div>
            )}

            <div className="flex-1 flex flex-col gap-2 overflow-y-auto mb-3">
              {msgs.map((m) => (
                <div
                  key={m.id}
                  className={`max-w-[80%] rounded-lg px-3 py-2 text-sm [overflow-wrap:anywhere] ${
                    m.senderId === user.id ? "self-end bg-navy text-white" : "self-start bg-gray-100"
                  }`}
                >
                  {m.bodyText}
                </div>
              ))}
              {!msgs.length && <p className="text-sm text-gray-500">Zatím bez zpráv.</p>}
              <div ref={bottom} />
            </div>

            <div className="flex flex-wrap gap-1.5 mb-2">
              {QUICK_REPLIES.map((q) => (
                <button
                  key={q}
                  type="button"
                  onClick={() => setDraft((d) => (d ? `${d} ${q}` : q))}
                  className="text-xs border border-gray-300 rounded-full px-2.5 py-1 hover:border-navy hover:text-navy"
                >
                  {q}
                </button>
              ))}
            </div>

            <form onSubmit={send} className="flex gap-2">
              <input
                value={draft}
                onChange={(e) => setDraft(e.target.value)}
                placeholder="Napsat zprávu…"
                maxLength={8192}
                className="flex-1 border border-gray-300 rounded px-3 py-2 text-sm"
              />
              <button className="bg-brand hover:bg-branddark text-white rounded px-4 py-2 text-sm font-semibold">
                Odeslat
              </button>
            </form>
          </>
        )}
      </div>
    </div>
  );
}