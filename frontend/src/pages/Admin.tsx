import {
  useCallback, useEffect, useRef, useState, type ReactElement, type ReactNode,
} from "react";
import { Link } from "react-router-dom";
import {
  api, downloadFile, ellipsis, fmtDate, fmtPrice, type ApiError,
} from "../api";
import { useAuth } from "../auth";
import type {
  AdminUser, AuditEntry, Dac7Summary, Dispute, ModerationReport, PendingVerification,
} from "../types";

type Section = "moderation" | "disputes" | "verifications" | "users" | "audit" | "dac7";

const SECTIONS: ReadonlyArray<readonly [Section, string]> = [
  ["moderation", "Moderační fronta"],
  ["disputes", "Námitky na hodnocení"],
  ["verifications", "Ověření obchodníků"],
  ["users", "Uživatelé"],
  ["audit", "Audit log"],
  ["dac7", "Daňové výkazy (DAC7)"],
];

function Panel({
  title,
  children,
  innerRef,
}: {
  title: string;
  children: ReactNode;
  innerRef?: React.RefObject<HTMLDivElement | null>;
}): ReactElement {
  return (
    <div
      ref={innerRef}
      className="bg-white border border-gray-200 rounded-xl p-5 scroll-mt-4"
    >
      <h2 className="text-lg font-bold text-navydeep mb-3">{title}</h2>
      {children}
    </div>
  );
}

function Empty({ children }: { children: ReactNode }): ReactElement {
  return <p className="text-sm text-gray-500">{children}</p>;
}

/** Administrace: moderace, námitky, ověření obchodníků, uživatelé, audit a daňové výkazy. */
export default function Admin({ section }: { section?: Section }): ReactElement {
  const { user } = useAuth();
  const [queue, setQueue] = useState<ModerationReport[]>([]);
  const [disputes, setDisputes] = useState<Dispute[]>([]);
  const [disputeNotes, setDisputeNotes] = useState<Record<number, string>>({});
  const [verifications, setVerifications] = useState<PendingVerification[]>([]);
  const [verifyNotes, setVerifyNotes] = useState<Record<number, string>>({});
  const [users, setUsers] = useState<AdminUser[]>([]);
  const [userQuery, setUserQuery] = useState("");
  const [audit, setAudit] = useState<AuditEntry[]>([]);
  const [auditAction, setAuditAction] = useState("");
  const [year, setYear] = useState(String(new Date().getFullYear()));
  const [format, setFormat] = useState("csv");
  const [summary, setSummary] = useState<Dac7Summary | null>(null);
  const [error, setError] = useState("");

  const moderationRef = useRef<HTMLDivElement>(null);
  const disputesRef = useRef<HTMLDivElement>(null);
  const verificationsRef = useRef<HTMLDivElement>(null);
  const usersRef = useRef<HTMLDivElement>(null);
  const auditRef = useRef<HTMLDivElement>(null);
  const dac7Ref = useRef<HTMLDivElement>(null);

  const loadAll = useCallback(async () => {
    setError("");
    try {
      const [q, d, v, u, a, s] = await Promise.all([
        api.adminQueue(50).catch(() => ({ data: [] as ModerationReport[] })),
        api.adminDisputes(50).catch(() => ({ data: [] as Dispute[], openCount: 0 })),
        api.adminVerifications().catch(() => ({ data: [] as PendingVerification[] })),
        api.adminUsers(new URLSearchParams({ q: userQuery, limit: "50" })).catch(() => ({
          data: [] as AdminUser[],
          nextCursor: null,
        })),
        api
          .adminAudit(new URLSearchParams({ limit: "50", ...(auditAction ? { action: auditAction } : {}) }))
          .catch(() => ({ data: [] as AuditEntry[], nextCursor: null })),
        api.adminDac7Summary(Number(year) || new Date().getFullYear()).catch(() => null),
      ]);
      setQueue(q.data ?? []);
      setDisputes(d.data ?? []);
      setVerifications(v.data ?? []);
      setUsers(u.data ?? []);
      setAudit(a.data ?? []);
      setSummary(s);
    } catch (e) {
      setError((e as ApiError).message);
    }
  }, [userQuery, auditAction, year]);

  useEffect(() => {
    if (user) void loadAll();
  }, [user, loadAll]);

  // Podrouty /admin/… přejíždějí na příslušnou sekci.
  useEffect(() => {
    const refs: Partial<Record<Section, React.RefObject<HTMLDivElement | null>>> = {
      dac7: dac7Ref,
      moderation: moderationRef,
      disputes: disputesRef,
      verifications: verificationsRef,
      users: usersRef,
      audit: auditRef,
    };
    if (!section) return;
    refs[section]?.current?.scrollIntoView({ block: "start" });
  }, [section, queue, disputes, verifications]);

  const guard = async (fn: () => Promise<unknown>) => {
    setError("");
    try {
      await fn();
      await loadAll();
    } catch (e) {
      setError((e as ApiError).message);
    }
  };

  const decide = (id: number, how: "approve" | "remove") =>
    guard(() => api.adminDecideReport(id, how));

  const resolveDispute = (id: number, decision: "uphold" | "reject") =>
    guard(() => api.adminResolveDispute(id, decision, disputeNotes[id] || undefined));

  const decideVerification = (userId: number, approve: boolean) =>
    guard(() => api.adminDecideVerification(userId, approve, verifyNotes[userId] || undefined));

  const download = async (detail: boolean) => {
    const suffix = detail ? "-sales" : "";
    const ext = format === "xml" ? "xml" : "csv";
    try {
      await downloadFile(
        `/admin/dac7/export?year=${year}&format=${format}${detail ? "&detail=true" : ""}`,
        `dac7-${year}${suffix}.${ext}`,
      );
    } catch (e) {
      setError((e as Error).message);
    }
  };

  if (!user) {
    return (
      <div className="container mx-auto px-4 py-10">
        <p>
          Přihlaste se jako administrátor.{" "}
          <Link to="/prihlaseni" className="text-brand font-semibold">
            Přihlásit
          </Link>
        </p>
      </div>
    );
  }

  return (
    <div className="container mx-auto px-4 py-10 max-w-5xl flex flex-col gap-6">
      <h1 className="text-3xl font-bold text-navydeep">Administrace</h1>

      <nav className="flex flex-wrap gap-2 text-sm">
        {SECTIONS.map(([key, label]) => (
          <Link
            key={key}
            to={`/admin/${key}`}
            className="border border-gray-300 rounded px-3 py-1 hover:border-navy hover:text-navy"
          >
            {label}
          </Link>
        ))}
      </nav>

      {error && (
        <p className="text-brand text-sm">
          {error} (Sekce vyžaduje roli ADMIN — e-mail musí být shodný s ADMIN_EMAIL.)
        </p>
      )}

      <Panel title="Moderační fronta" innerRef={moderationRef}>
        {!queue.length && <Empty>Fronta je prázdná.</Empty>}
        {queue.map((r) => (
          <div
            key={r.id}
            className="flex flex-wrap items-center justify-between gap-2 py-2 border-b border-gray-100 text-sm"
          >
            <span>
              #{r.id} · {r.targetType}:{r.targetId} · {ellipsis(r.reason, 70)}{" "}
              {r.trustedFlagger && <strong className="text-brand">(důvěryhodný hlásič)</strong>}
              <span className="text-gray-400"> · {fmtDate(r.createdAt)}</span>
            </span>
            <span className="flex gap-2 shrink-0">
              <button
                onClick={() => void decide(r.id, "approve")}
                className="border border-navy text-navy rounded px-3 py-1 text-sm"
              >
                Ponechat
              </button>
              <button
                onClick={() => void decide(r.id, "remove")}
                className="bg-brand text-white rounded px-3 py-1 text-sm"
              >
                Odstranit
              </button>
            </span>
          </div>
        ))}
      </Panel>

      <Panel title={`Námitky na hodnocení${disputes.length ? ` (${disputes.length} otevřených)` : ""}`} innerRef={disputesRef}>
        {!disputes.length && <Empty>Žádné otevřené námitky.</Empty>}
        {disputes.map((d) => (
          <div key={d.id} className="py-3 border-b border-gray-100 text-sm flex flex-col gap-2">
            <div className="flex flex-wrap justify-between gap-2">
              <span>
                <strong>#{d.id}</strong> · recenze #{d.reviewId} · prodejce #{d.disputerId} ·{" "}
                {fmtDate(d.createdAt)}
              </span>
              <span className="flex gap-2 shrink-0">
                <button
                  onClick={() => void resolveDispute(d.id, "uphold")}
                  className="bg-brand text-white rounded px-3 py-1 text-sm"
                >
                  Potvrdit námitku
                </button>
                <button
                  onClick={() => void resolveDispute(d.id, "reject")}
                  className="border border-navy text-navy rounded px-3 py-1 text-sm"
                >
                  Zamítnout
                </button>
              </span>
            </div>
            <p className="text-gray-700">{d.reason}</p>
            <input
              value={disputeNotes[d.id] ?? ""}
              onChange={(e) => setDisputeNotes((s) => ({ ...s, [d.id]: e.target.value }))}
              placeholder="Poznámka k rozhodnutí (volitelné, uloží se do auditu)…"
              className="border border-gray-300 rounded px-3 py-1.5 text-sm"
            />
            <p className="text-xs text-gray-500">
              Potvrzení námitky recenzi odstraní a nepočítá se do hodnocení prodejce; zamítnutí ji
              ponechá v platnosti.
            </p>
          </div>
        ))}
      </Panel>

      <Panel title="Ověření obchodníků" innerRef={verificationsRef}>
        {!verifications.length && (
          <Empty>Žádný obchodník nečeká na posouzení dokladu.</Empty>
        )}
        {verifications.map((v) => (
          <div key={v.userId} className="py-3 border-b border-gray-100 text-sm flex flex-col gap-2">
            <div className="flex flex-wrap justify-between gap-2">
              <span>
                <strong>{v.displayName || `#${v.userId}`}</strong> · #{v.userId} ·{" "}
                {v.hasIco ? "IČo má" : "IČo chybí"} ·{" "}
                {v.selfCert ? "čestné prohlášení ano" : "čestné prohlášení ne"} ·{" "}
                {fmtDate(v.submittedAt)}
              </span>
              <span className="flex gap-2 shrink-0">
                <button
                  onClick={() => void decideVerification(v.userId, true)}
                  className="bg-navy text-white rounded px-3 py-1 text-sm"
                >
                  Potvrdit
                </button>
                <button
                  onClick={() => void decideVerification(v.userId, false)}
                  className="bg-brand text-white rounded px-3 py-1 text-sm"
                >
                  Odmítnout
                </button>
              </span>
            </div>
            <input
              value={verifyNotes[v.userId] ?? ""}
              onChange={(e) => setVerifyNotes((s) => ({ ...s, [v.userId]: e.target.value }))}
              placeholder="Poznámka pro prodejce (zobrazí se v jeho účtu)…"
              className="border border-gray-300 rounded px-3 py-1.5 text-sm"
            />
          </div>
        ))}
        <p className="text-xs text-gray-500 mt-2">
          Po potvrzení se u prodejce objeví odznak „Ověřený obchodník“. Obsah dokladu se nikdy
          nezobrazuje — ukládá se šifrovaně a je dostupný jen pro rozhodnutí.
        </p>
      </Panel>

      <Panel title="Uživatelé" innerRef={usersRef}>
        <div className="flex gap-2 mb-3">
          <input
            value={userQuery}
            onChange={(e) => setUserQuery(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && void loadAll()}
            placeholder="Hledat podle e-mailu nebo jména…"
            className="flex-1 border border-gray-300 rounded px-3 py-1.5 text-sm"
          />
          <button
            onClick={() => void loadAll()}
            className="border border-navy text-navy rounded px-3 py-1.5 text-sm font-semibold"
          >
            Hledat
          </button>
        </div>
        {!users.length && <Empty>Žádní uživatelé.</Empty>}
        {users.map((u) => (
          <div
            key={u.id}
            className="flex flex-wrap items-center justify-between gap-2 py-2 border-b border-gray-100 text-sm"
          >
            <span>
              <strong>{u.displayName}</strong> · {u.email} · {u.sellerType}
              {u.isAdmin && " · admin"}
              {u.deleted && " · smazaný účet"}
              {u.blocked && (
                <span className="text-brand font-semibold">
                  {" "}
                  · zablokován{u.blockReason ? `: ${u.blockReason}` : ""}
                </span>
              )}
            </span>
            {!u.isAdmin &&
              (u.blocked ? (
                <button
                  onClick={() => void guard(() => api.adminUnblockUser(u.id))}
                  className="border border-navy text-navy rounded px-3 py-1 text-sm"
                >
                  Odblokovat
                </button>
              ) : (
                <button
                  onClick={() => {
                    const reason = prompt("Důvod blokace (zobrazí se v auditu):") ?? "";
                    if (!reason) return;
                    void guard(() => api.adminBlockUser(u.id, reason));
                  }}
                  className="border border-brand text-brand rounded px-3 py-1 text-sm"
                >
                  Blokovat
                </button>
              ))}
          </div>
        ))}
      </Panel>

      <Panel title="Audit log" innerRef={auditRef}>
        <div className="flex flex-wrap gap-2 mb-3">
          <input
            value={auditAction}
            onChange={(e) => setAuditAction(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && void loadAll()}
            placeholder="Filtrovat podle akce (např. TRADER_VERIFICATION_APPROVED)…"
            className="flex-1 border border-gray-300 rounded px-3 py-1.5 text-sm font-mono"
          />
          <button
            onClick={() => void loadAll()}
            className="border border-navy text-navy rounded px-3 py-1.5 text-sm font-semibold"
          >
            Filtrovat
          </button>
        </div>
        {!audit.length && <Empty>Záznamy nejsou.</Empty>}
        {audit.map((a) => (
          <div
            key={a.id}
            className="flex flex-wrap justify-between gap-2 py-1.5 border-b border-gray-50 text-xs font-mono"
          >
            <span>
              {fmtDate(a.createdAt)} · {a.action}
              {a.targetType ? ` · ${a.targetType}:${a.targetId ?? ""}` : ""}
            </span>
            <span className="text-gray-400">aktor #{a.actorId ?? "—"}</span>
          </div>
        ))}
      </Panel>

      <Panel title="Daňové výkazy (DAC7)" innerRef={dac7Ref}>
        {summary && (
          <div className="grid grid-cols-2 md:grid-cols-4 gap-2 mb-4 text-sm">
            <div>
              <p className="text-xs text-gray-500">Rok</p>
              <p className="font-bold text-navydeep">{summary.year}</p>
            </div>
            <div>
              <p className="text-xs text-gray-500">Prodejců</p>
              <p className="font-bold text-navydeep">{summary.sellerCount}</p>
            </div>
            <div>
              <p className="text-xs text-gray-500">Nad prahem</p>
              <p className="font-bold text-brand">{summary.reportableSellerCount}</p>
            </div>
            <div>
              <p className="text-xs text-gray-500">Prodejů / obrat</p>
              <p className="font-bold text-navydeep">
                {summary.totalSales} · {fmtPrice(summary.totalAmountCzk)}
              </p>
            </div>
          </div>
        )}

        <div className="flex flex-wrap gap-2 items-center">
          <input
            type="number"
            value={year}
            onChange={(e) => setYear(e.target.value)}
            aria-label="Rok"
            className="w-24 border border-gray-300 rounded px-2 py-1.5 text-sm"
          />
          <select
            value={format}
            onChange={(e) => setFormat(e.target.value)}
            aria-label="Formát"
            className="border border-gray-300 rounded px-2 py-1.5 text-sm"
          >
            <option value="csv">CSV</option>
            <option value="xml">XML</option>
          </select>
          <button
            onClick={() => void download(false)}
            className="bg-navy text-white rounded px-4 py-1.5 text-sm font-semibold"
          >
            Souhrn prodejců
          </button>
          <button
            onClick={() => void download(true)}
            className="border border-navy text-navy rounded px-4 py-1.5 text-sm font-semibold"
          >
            Detail prodejů
          </button>
        </div>
        <p className="text-xs text-gray-500 mt-2">
          Výkaz obsahuje prodejce nad práh {summary?.countThreshold ?? 30} prodejů nebo{" "}
          {fmtPrice(summary?.amountThresholdCzk ?? 50_000)} obratu ročně. Výkaz se generuje
          automaticky každý 5. ledna a jeho vyhotovení je v auditu.
        </p>
      </Panel>
    </div>
  );
}