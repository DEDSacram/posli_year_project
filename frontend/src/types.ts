/**
 * Typy odpovídající JSON, který vrací Spring Boot API (`/home/sacram/poslitodal/backend`).
 * Zdroj pravdy je server — když se tvar odpovědi změní, sem se propíše změna
 * a `tsc` v `npm run build` ji najde.
 */

export type SellerType = "private" | "trader";
export type TransactionStatus = "reserved" | "completed" | "cancelled" | "withdrawn";
export type DisputeStatus = "open" | "upheld" | "rejected";
export type VerificationStatus = "pending" | "verified" | "rejected";

/** Stránkovaný výpis — všude stejný tvar `{data, nextCursor}`. */
export interface Page<T> {
  data: T[];
  nextCursor: string | null;
}

export interface Tokens {
  token: string;
  refreshToken: string;
}

export interface AuthResponse extends Tokens {
  id: number;
}

export interface ListingImage {
  id: number;
  mime: string;
}

export interface Listing {
  id: number;
  sellerId: number;
  sellerName?: string | null;
  title: string;
  description: string;
  priceCzk: number;
  category: string;
  condition: string;
  city: string;
  lat?: number | null;
  lon?: number | null;
  sellerType: SellerType;
  status: string;
  createdAt: string;
  images?: ListingImage[];
  /** Pouze u GET /favorites/mine. */
  savedPriceCzk?: number;
  priceDrop?: number;
  /** Pahýl: inzerát byl smazán nebo odstraněn moderací. */
  removed?: boolean;
}

export interface ListingPayload {
  title: string;
  description: string;
  priceCzk: number;
  category: string;
  condition: string;
  city: string;
  lat?: number | null;
  lon?: number | null;
}

export interface RightsInfo {
  withdrawalApplies: boolean;
  sellerType: SellerType;
  explanationCs: string;
}

/** Právo odstoupení spotřebitele u konkrétní rezervace. */
export interface WithdrawalRights {
  applies: boolean;
  days: number;
  completedAt: string | null;
  deadline: string | null;
  eligible: boolean;
  explanationCs: string;
}

export interface SaleTransaction {
  id: number;
  listingId: number;
  listingTitle?: string | null;
  buyerId: number;
  sellerId: number;
  priceCzk: number;
  status: TransactionStatus;
  sellerType: SellerType;
  completedAt: string | null;
  withdrawnAt: string | null;
  role: "buyer" | "seller" | null;
  hasReview: boolean;
}

/** Přehled obchodů (GET /transactions/overview). */
export interface DealOverview {
  asSeller: SellerOverviewSide;
  asBuyer: BuyerOverviewSide;
}

interface BaseOverviewSide {
  reserved: number;
  completed: number;
  cancelled: number;
  withdrawn: number;
}

export interface SellerOverviewSide extends BaseOverviewSide {
  revenueCzk: number;
  ratingAverage: number;
  ratingCount: number;
}

export type BuyerOverviewSide = BaseOverviewSide;

export interface Review {
  id: number;
  transactionId: number;
  reviewerId: number;
  revieweeId: number;
  listingId: number;
  rating: number;
  text: string;
  verifiedPurchase: boolean;
  moderationStatus: string;
  responseText: string | null;
  disputeStatus: DisputeStatus | null;
  createdAt: string;
}

export interface Rating {
  average: number;
  count: number;
}

/** Odznaky prodejce — část statická, část odvozená z historie (GET /users/:id/badges). */
export interface Badges {
  trader: boolean;
  privateSeller: boolean;
  hasDac7Profile: boolean;
  verifiedTrader: boolean;
  trustedSeller: boolean;
  highlyRated: boolean;
  experienced: boolean;
  cleanRecord: boolean;
  ratingAverage: number;
  ratingCount: number;
  completedSales: number;
}

export interface Dispute {
  id: number;
  reviewId: number;
  disputerId: number;
  reason: string;
  status: DisputeStatus;
  resolutionNote: string | null;
  createdAt: string;
  resolvedAt: string | null;
}

export interface Dac7Status {
  salesCount: number;
  totalAmountCzk: number;
  isReportable: boolean;
}

export interface VerificationState {
  status: VerificationStatus;
  hasDocument: boolean;
  verifiedAt: string | null;
  note: string | null;
}

export interface PendingVerification {
  userId: number;
  displayName?: string;
  status: VerificationStatus;
  hasIco: boolean;
  selfCert: boolean;
  submittedAt: string | null;
  verified: boolean;
}

export interface UserProfile {
  id: number;
  displayName: string;
  sellerType: SellerType;
  createdAt: string;
}

/** Přihlášený uživatel v klientovi (uložený v localStorage). */
export interface Session {
  id: number;
  displayName?: string;
}

export interface Notification {
  id: number;
  type: "price_drop";
  listingId: number;
  title: string;
  oldPriceCzk: number | null;
  newPriceCzk: number | null;
  read: boolean;
  createdAt: string;
}

export interface Conversation {
  id: number;
  listingId: number;
  buyerId: number;
  sellerId: number;
  createdAt: string;
}

export interface ChatMessage {
  id: number;
  conversationId: number;
  senderId: number;
  bodyText: string;
  sentAt: string;
}

export interface ModerationReport {
  id: number;
  targetType: "listing" | "review" | "chat";
  targetId: number;
  reporterId: number;
  reason: string;
  trustedFlagger: boolean;
  status: string;
  createdAt: string;
}

/** Odpověď POST /moderation/reports (totéž co vládl v seznamu fronty). */
export type Report = ModerationReport;

export interface AuditEntry {
  id: number;
  actorId: number | null;
  action: string;
  targetType: string | null;
  targetId: number | null;
  createdAt: string;
}

export interface AdminUser {
  id: number;
  email: string;
  displayName: string;
  sellerType: SellerType;
  isAdmin: boolean;
  blocked: boolean;
  blockReason: string | null;
  deleted: boolean;
  createdAt: string;
}

export interface Dac7Summary {
  year: number;
  sellerCount: number;
  reportableSellerCount: number;
  totalSales: number;
  totalAmountCzk: number;
  countThreshold: number;
  amountThresholdCzk: number;
  sellers: unknown[];
}

export interface Consent {
  id: number;
  purpose: string;
  granted: boolean;
  createdAt: string;
}

export interface GeoPlace {
  zip: string;
  city: string | null;
  lat: number;
  lon: number;
}

/** Chyba API — `code` je strojový kód, `messageCs` česká hláška pro uživatele. */
export interface ApiErrorBody {
  code: string;
  messageCs: string;
}