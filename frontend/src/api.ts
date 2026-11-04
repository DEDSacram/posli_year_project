import type {
  AdminUser, ApiErrorBody, AuditEntry, AuthResponse, Badges, ChatMessage, Consent, Conversation,
  Dac7Status, Dac7Summary, DealOverview, Dispute, GeoPlace, Listing, ListingImage, ListingPayload,
  ModerationReport, Notification, Page, PendingVerification, Rating, Report,
  Review, RightsInfo, SaleTransaction, Tokens, UserProfile, VerificationState,
  WithdrawalRights,
} from "./types";

/** Adresa REST API (v produkci z VITE_API_URL, v devu z docker-compose). */
export const API_URL: string = import.meta.env.VITE_API_URL ?? "http://localhost:8081";

const TOKENS_KEY = "bazar-tokens";

/** Chyba volání API — nese HTTP stav, kód a českou hlášku. */
export class ApiError extends Error {
  readonly status: number;
  readonly code?: string;

  constructor(status: number, message: string, code?: string) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    if (code !== undefined) this.code = code;
  }
}

function loadTokens(): Tokens | null {
  try {
    return JSON.parse(localStorage.getItem(TOKENS_KEY) ?? "null") as Tokens | null;
  } catch {
    return null;
  }
}

export function saveTokens(t: Tokens | null): void {
  if (t) localStorage.setItem(TOKENS_KEY, JSON.stringify(t));
  else localStorage.removeItem(TOKENS_KEY);
}

async function refreshTokens(): Promise<Tokens | null> {
  const t = loadTokens();
  if (!t?.refreshToken) return null;
  const res = await fetch(`${API_URL}/auth/refresh`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ refreshToken: t.refreshToken }),
  });
  if (!res.ok) {
    saveTokens(null);
    return null;
  }
  const data = (await res.json()) as Tokens;
  saveTokens(data);
  return data;
}

interface RequestOptions {
  method?: string;
  body?: unknown;
  form?: FormData;
  auth?: boolean;
  /** Vnitřní příznak — zabraňuje nekonečné smyčce při obnově tokenu. */
  retried?: boolean;
}

async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = "GET", body, form, auth = false, retried = false } = options;
  const headers: Record<string, string> = {};
  let payload: BodyInit | undefined;
  if (form) {
    payload = form;
  } else if (body !== undefined) {
    headers["Content-Type"] = "application/json";
    payload = JSON.stringify(body);
  }
  if (auth) {
    const t = loadTokens();
    if (t?.token) headers.Authorization = `Bearer ${t.token}`;
  }

  const res = await fetch(`${API_URL}${path}`, { method, headers, body: payload });

  // Vypršel přístupový token — zkusíme ho jednou vyměnit a požadavek zopakovat.
  if (res.status === 401 && auth && !retried) {
    const next = await refreshTokens();
    if (next?.token) return request<T>(path, { method, body, form, auth, retried: true });
  }

  if (res.status === 204) return undefined as T;

  const contentType = res.headers.get("content-type") ?? "";
  const isJson = contentType.includes("application/json");
  const data: unknown = isJson ? await res.json().catch(() => null) : await res.text();

  if (!res.ok) {
    if (isJson && data && typeof data === "object") {
      const e = data as Partial<ApiErrorBody>;
      throw new ApiError(res.status, e.messageCs ?? `Chyba ${res.status}`, e.code);
    }
    const text = typeof data === "string" ? data.trim() : "";
    throw new ApiError(res.status, text || `Chyba ${res.status}`);
  }
  return data as T;
}

/** Stažení souboru z chráněného endpointu (admin exporty) — načte token z úložiště. */
export async function downloadFile(path: string, fallbackName: string): Promise<void> {
  const t = loadTokens();
  if (!t?.token) throw new ApiError(401, "Nejste přihlášeni.");
  const res = await fetch(`${API_URL}${path}`, { headers: { Authorization: `Bearer ${t.token}` } });
  if (!res.ok) throw new ApiError(res.status, "Export selhal.");
  const blob = await res.blob();
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = fallbackName;
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 5000);
}

type Id = number | string;

/** Tenká vrstva nad REST API — jedna metoda na endpoint, bez logiky UI. */
export const api = {
  // ---------- auth ----------
  register: (email: string, password: string, displayName: string, sellerType: string) =>
    request<AuthResponse>("/auth/register", { method: "POST", body: { email, password, displayName, sellerType } }),
  login: (email: string, password: string) =>
    request<AuthResponse>("/auth/login", { method: "POST", body: { email, password } }),

  // ---------- uživatelé ----------
  profile: (id: Id) => request<UserProfile>(`/users/${id}`),
  badges: (id: Id) => request<Badges>(`/users/${id}/badges`),
  rating: (id: Id) => request<Rating>(`/users/${id}/rating`),
  reviewsOfUser: (id: Id, cursor?: number | null) =>
    request<Page<Review>>(`/users/${id}/reviews${cursor ? `?cursor=${cursor}` : ""}`),
  saveSellerInfo: (payload: Record<string, unknown>) =>
    request<{ status: string }>("/users/me/seller-info", { method: "PATCH", body: payload, auth: true }),
  dac7Status: () => request<Dac7Status>("/seller/me/dac7-status", { auth: true }),
  myVerification: () => request<VerificationState>("/seller/me/verification", { auth: true }),
  /** Nahrání dokladu totožnosti obchodníka (musí před dokončením profilu). */
  uploadIdDocument: (file: File) => upload<VerificationState>("/users/me/id-document", file),
  myLocation: () => request<{ hasProfile: boolean; city: string | null; postalCode: string | null; country: string | null }>(
    "/seller/me/location", { auth: true }),
  saveLocation: (city: string, postalCode: string) =>
    request<unknown>("/seller/me/location", { method: "PATCH", body: { city, postalCode }, auth: true }),
  exportMe: () => request<Record<string, unknown>>("/users/me/export", { auth: true }),
  deleteMe: () => request<{ status: string }>("/users/me", { method: "DELETE", auth: true }),
  consent: (purpose: string, granted: boolean) =>
    request<Consent>("/consents", { method: "POST", body: { purpose, granted }, auth: true }),

  // ---------- inzeráty ----------
  feed: (cursor?: number | null, limit = 12) =>
    request<Page<Listing>>(`/listings/feed?limit=${limit}${cursor ? `&cursor=${cursor}` : ""}`),
  searchListings: (params: URLSearchParams) =>
    request<Page<Listing>>(`/listings/search?${params.toString()}`),
  listing: (id: Id) => request<Listing>(`/listings/${id}`),
  createListing: (payload: ListingPayload) =>
    request<Listing>("/listings", { method: "POST", body: payload, auth: true }),
  updateListing: (id: Id, payload: Partial<ListingPayload>) =>
    request<Listing>(`/listings/${id}`, { method: "PUT", body: payload, auth: true }),
  deleteListing: (id: Id) => request<unknown>(`/listings/${id}`, { method: "DELETE", auth: true }),
  uploadListingImage: (id: Id, file: File) => upload(`/listings/${id}/images`, file),
  deleteListingImage: (id: Id, imgId: Id) =>
    request<unknown>(`/listings/${id}/images/${imgId}`, { method: "DELETE", auth: true }),
  listingImages: (id: Id) => request<ListingImage[]>(`/listings/${id}/images`, { auth: true }),
  rightsInfo: (id: Id) => request<RightsInfo>(`/listings/${id}/rights-info`),
  suggest: (title: string, category: string) =>
    request<{ categoryGuess?: string | null; medianPriceCzk?: number | null; sampleSize?: number | null }>(
      `/listings/suggest?title=${encodeURIComponent(title)}&category=${encodeURIComponent(category)}`),

  // ---------- oblíbené ----------
  favoriteIds: () => request<{ ids: number[] }>("/favorites/ids", { auth: true }),
  myFavorites: () => request<Page<Listing>>("/favorites/mine", { auth: true }),
  addFavorite: (listingId: Id) =>
    request<{ favorited: boolean }>(`/favorites/${listingId}`, { method: "POST", auth: true }),
  removeFavorite: (listingId: Id) =>
    request<{ favorited: boolean }>(`/favorites/${listingId}`, { method: "DELETE", auth: true }),

  // ---------- upozornění ----------
  notifications: (cursor?: number | null) =>
    request<Page<Notification>>(`/notifications${cursor ? `?cursor=${cursor}` : ""}`, { auth: true }),
  unreadCount: () => request<{ unread: number }>("/notifications/unread-count", { auth: true }),
  markRead: (id: Id) => request<{ id: number; read: boolean }>(`/notifications/${id}/read`, { method: "POST", auth: true }),
  markAllRead: () => request<{ marked: number }>("/notifications/read-all", { method: "POST", auth: true }),

  // ---------- transakce ----------
  myTransactions: (role: "all" | "buyer" | "seller" = "all") =>
    request<Page<SaleTransaction>>(`/transactions/mine?role=${role}`, { auth: true }),
  dealOverview: () => request<DealOverview>("/transactions/overview", { auth: true }),
  reserve: (listingId: number) =>
    request<SaleTransaction>("/transactions", { method: "POST", body: { listingId }, auth: true }),
  completeTransaction: (id: Id) =>
    request<SaleTransaction>(`/transactions/${id}/complete`, { method: "POST", auth: true }),
  cancelTransaction: (id: Id) =>
    request<SaleTransaction>(`/transactions/${id}/cancel`, { method: "POST", auth: true }),
  withdrawTransaction: (id: Id, reason?: string) =>
    request<SaleTransaction>(`/transactions/${id}/withdraw`, { method: "POST", body: { reason }, auth: true }),
  withdrawalRights: (id: Id) => request<WithdrawalRights>(`/transactions/${id}/withdrawal-rights`, { auth: true }),
  reviewTransaction: (id: Id, rating: number, text: string) =>
    request<Review>(`/transactions/${id}/review`, { method: "POST", body: { rating, text }, auth: true }),

  // ---------- hodnocení a námitky ----------
  respondToReview: (reviewId: Id, text: string) =>
    request<Review>(`/reviews/${reviewId}/response`, { method: "POST", body: { text }, auth: true }),
  disputeReview: (reviewId: Id, reason: string) =>
    request<Dispute>(`/reviews/${reviewId}/dispute`, { method: "POST", body: { reason }, auth: true }),
  myDisputes: () => request<Page<Dispute>>("/reviews/disputes/mine", { auth: true }),

  // ---------- chat ----------
  conversations: () => request<Page<Conversation>>("/chat/conversations", { auth: true }),
  conversation: (listingId: number) =>
    request<Conversation>("/chat/conversations", { method: "POST", body: { listingId }, auth: true }),
  messages: (conversationId: Id, cursor?: number | null) =>
    request<Page<ChatMessage>>(`/chat/conversations/${conversationId}/messages${cursor ? `?cursor=${cursor}` : ""}`, { auth: true }),
  sendMessage: (conversationId: number, bodyText: string) =>
    request<ChatMessage>("/chat/messages", { method: "POST", body: { conversationId, bodyText }, auth: true }),
  interestCount: (listingId: number) =>
    request<{ interestCount: number }>(`/chat/interest?listingId=${listingId}`),

  // ---------- moderace ----------
  report: (targetType: string, targetId: Id, reason: string) =>
    request<Report>("/moderation/reports", { method: "POST", body: { targetType, targetId, reason }, auth: true }),

  // ---------- geo ----------
  zipToCoords: (zip: string) => request<GeoPlace>(`/geo/zip?zip=${encodeURIComponent(zip)}`),
  reverseGeocode: (lat: number, lon: number) =>
    request<GeoPlace>(`/geo/reverse?lat=${lat}&lon=${lon}`),

  // ---------- administrace ----------
  adminQueue: (limit = 50) => request<{ data: ModerationReport[] }>(`/admin/moderation/queue?limit=${limit}`, { auth: true }),
  adminDecideReport: (id: Id, decision: "approve" | "remove") =>
    request<ModerationReport>(`/admin/moderation/${id}/${decision}`, { method: "POST", body: {}, auth: true }),
  adminDisputes: (limit = 50) =>
    request<{ data: Dispute[]; openCount: number }>(`/admin/disputes?limit=${limit}`, { auth: true }),
  adminResolveDispute: (id: Id, decision: "uphold" | "reject", note?: string) =>
    request<Dispute>(`/admin/disputes/${id}/resolve`, { method: "POST", body: { decision, note }, auth: true }),
  adminVerifications: () =>
    request<{ data: PendingVerification[] }>("/admin/verifications", { auth: true }),
  adminDecideVerification: (userId: Id, approve: boolean, note?: string) =>
    request<VerificationState>(
      `/admin/verifications/${userId}/${approve ? "approve" : "reject"}`,
      { method: "POST", body: { note }, auth: true }),
  adminAudit: (params: URLSearchParams) =>
    request<Page<AuditEntry>>(`/admin/audit?${params.toString()}`, { auth: true }),
  adminUsers: (params: URLSearchParams) =>
    request<Page<AdminUser>>(`/admin/users?${params.toString()}`, { auth: true }),
  adminBlockUser: (id: Id, reason: string) =>
    request<AdminUser>(`/admin/users/${id}/block`, { method: "POST", body: { reason }, auth: true }),
  adminUnblockUser: (id: Id) =>
    request<AdminUser>(`/admin/users/${id}/unblock`, { method: "POST", auth: true }),
  adminDac7Summary: (year: number) => request<Dac7Summary>(`/admin/dac7/summary?year=${year}`, { auth: true }),
};

async function upload<T>(path: string, file: File): Promise<T> {
  const form = new FormData();
  form.append("file", file);
  return request<T>(path, { method: "POST", form, auth: true });
}

/** URL obrázku inzerátu (backend drží binární data v `listing_images`). */
export const imageUrl = (listingId: Id, img: { id: number }): string =>
  `${API_URL}/listings/${listingId}/images/${img.id}`;

export const fmtPrice = (n: number): string => `${Number(n).toLocaleString("cs-CZ")} Kč`;

export const fmtDate = (iso: string | null | undefined): string =>
  iso ? new Date(iso).toLocaleString("cs-CZ") : "—";

export const fmtDateDay = (iso: string | null | undefined): string =>
  iso ? new Date(iso).toLocaleDateString("cs-CZ") : "—";

/** Zkrátí dlouhý text na jednu větu (např. v seznamu hlášení). */
export const ellipsis = (s: string | null | undefined, max = 80): string =>
  !s ? "" : s.length > max ? `${s.slice(0, max)}…` : s;