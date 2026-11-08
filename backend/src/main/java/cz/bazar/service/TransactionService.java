package cz.bazar.service;

import cz.bazar.domain.Listing;
import cz.bazar.domain.SaleTransaction;
import cz.bazar.domain.User;
import cz.bazar.dto.Dtos.*;
import cz.bazar.exception.ApiException;
import cz.bazar.repository.ListingRepository;
import cz.bazar.repository.ReviewRepository;
import cz.bazar.repository.SaleTransactionRepository;
import cz.bazar.security.ApiRateLimiter;
import cz.bazar.security.RateLimitProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class TransactionService {
  /** Právo spotřebitele na odstoupení od smlouvy: 14 dní od uzavření (§ 1829 OZ). */
  public static final int WITHDRAWAL_DAYS = 14;

  private final SaleTransactionRepository transactions;
  private final ListingRepository listings;
  private final ReviewRepository reviewRepo;
  private final CurrentUserService current;
  private final ApiRateLimiter rateLimiter;
  private final RateLimitProperties limits;
  private final AuditService audit;

  public TransactionService(SaleTransactionRepository transactions, ListingRepository listings,
                            ReviewRepository reviewRepo, CurrentUserService current,
                            ApiRateLimiter rateLimiter, RateLimitProperties limits,
                            AuditService audit) {
    this.transactions = transactions; this.listings = listings;
    this.reviewRepo = reviewRepo; this.current = current;
    this.rateLimiter = rateLimiter; this.limits = limits; this.audit = audit;
  }

  @Transactional
  public SaleTransaction reserve(Long listingId) {
    User buyer = current.require();
    rateLimiter.check("tx:" + buyer.getId(), limits.getReservation(),
        "Příliš mnoho rezervací. Zkuste to později.");
    Listing l = listings.findById(listingId)
        .orElseThrow(() -> ApiException.notFound("LISTING_NOT_FOUND", "Inzerát nenalezen."));
    if (!"active".equals(l.getStatus())) throw ApiException.badRequest("LISTING_NOT_ACTIVE", "Inzerát není aktivní.");
    if (l.getSellerId().equals(buyer.getId())) throw ApiException.badRequest("OWN_LISTING", "Vlastní inzerát nelze rezervovat.");
    // Idempotence: stejný kupec + inzerát → vrátit existující rezervaci místo duplikátu.
    var existing = transactions.findFirstByListingIdAndBuyerIdAndStatusOrderByIdDesc(listingId, buyer.getId(), "reserved");
    if (existing.isPresent()) return existing.get();
    SaleTransaction t = new SaleTransaction();
    t.setListingId(listingId);
    t.setBuyerId(buyer.getId());
    t.setSellerId(l.getSellerId());
    t.setPriceCzk(l.getPriceCzk()); // snapshot ceny, ne priznani prodejce
    t.setStatus("reserved");
    t.setSellerTypeSnapshot(l.getSellerTypeSnapshot());
    return transactions.save(t);
  }

  @Transactional
  public SaleTransaction complete(Long id) {
    User me = current.require();
    SaleTransaction t = require(id);
    requireParticipant(t, me);
    if (!"reserved".equals(t.getStatus())) throw ApiException.badRequest("INVALID_STATUS", "Transakce není ve stavu reserved.");
    t.setStatus("completed");
    t.setCompletedAt(OffsetDateTime.now()); // completed = znalost provedeni pro DAC7
    SaleTransaction saved = transactions.save(t);
    audit.log(me.getId(), "TRANSACTION_COMPLETED", "transaction", id);
    return saved;
  }

  @Transactional
  public SaleTransaction cancel(Long id) {
    User me = current.require();
    SaleTransaction t = require(id);
    requireParticipant(t, me);
    if (!"reserved".equals(t.getStatus())) throw ApiException.badRequest("INVALID_STATUS", "Transakce není ve stavu reserved.");
    t.setStatus("cancelled");
    SaleTransaction saved = transactions.save(t);
    audit.log(me.getId(), "TRANSACTION_CANCELLED", "transaction", id);
    return saved;
  }

  /**
   * Odstoupení spotřebitele od smlouvy (§ 1829 OZ). Smí ho učinit jen kupující,
   * jen u obchodníka a jen do 14 dní od dokončení obchodu.
   */
  @Transactional
  public SaleTransaction withdraw(Long id, String reason) {
    User me = current.require();
    SaleTransaction t = require(id);
    if (!t.getBuyerId().equals(me.getId()))
      throw ApiException.forbidden("ONLY_BUYER", "Odstoupit může jen kupující.");
    if (!"completed".equals(t.getStatus()))
      throw ApiException.badRequest("INVALID_STATUS", "Odstoupení je možné jen u dokončeného obchodu.");
    if (!"trader".equals(t.getSellerTypeSnapshot()))
      throw ApiException.badRequest("WITHDRAWAL_NOT_APPLIES", "Právo odstoupení se vztahuje jen na nákupy od obchodníka.");
    if (t.getCompletedAt() == null)
      throw ApiException.badRequest("WITHDRAWAL_NOT_APPLIES", "U obchodu chybí datum dokončení.");
    OffsetDateTime deadline = t.getCompletedAt().plusDays(WITHDRAWAL_DAYS);
    if (OffsetDateTime.now().isAfter(deadline))
      throw ApiException.badRequest("WITHDRAWAL_EXPIRED",
          "Lhůta 14 dní pro odstoupení už uplynula.");
    t.setStatus("withdrawn");
    t.setWithdrawnAt(OffsetDateTime.now());
    t.setWithdrawalReason(reason);
    SaleTransaction saved = transactions.save(t);
    audit.log(me.getId(), "TRANSACTION_WITHDRAWN", "transaction", id);
    return saved;
  }

  /**
   * Právo odstoupení pro danou transakci — používá UI k zobrazení tlačítka i data konce lhůty.
   */
  @Transactional(readOnly = true)
  public Map<String, Object> withdrawalRights(Long id) {
    User me = current.require();
    SaleTransaction t = require(id);
    requireParticipant(t, me);
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("applies", "trader".equals(t.getSellerTypeSnapshot()));
    m.put("days", WITHDRAWAL_DAYS);
    m.put("completedAt", t.getCompletedAt());
    OffsetDateTime deadline = t.getCompletedAt() == null ? null : t.getCompletedAt().plusDays(WITHDRAWAL_DAYS);
    m.put("deadline", deadline);
    m.put("eligible", "completed".equals(t.getStatus()) && deadline != null
        && !OffsetDateTime.now().isAfter(deadline) && "trader".equals(t.getSellerTypeSnapshot()));
    m.put("explanationCs", "trader".equals(t.getSellerTypeSnapshot())
        ? "Nákup od obchodníka — máte " + WITHDRAWAL_DAYS + " dní na odstoupení od smlouvy dle § 1829 OZ."
        : "Nákup od soukromé osoby — právo odstoupení se neuplatní.");
    return m;
  }

  public Map<String, Object> toMap(SaleTransaction t) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", t.getId());
    m.put("listingId", t.getListingId());
    m.put("listingTitle", listings.findById(t.getListingId()).map(Listing::getTitle).orElse(null));
    m.put("buyerId", t.getBuyerId());
    m.put("sellerId", t.getSellerId());
    m.put("priceCzk", t.getPriceCzk());
    m.put("status", t.getStatus());
    m.put("sellerType", t.getSellerTypeSnapshot());
    m.put("completedAt", t.getCompletedAt());
    m.put("withdrawnAt", t.getWithdrawnAt());
    m.put("role", roleOf(t));
    m.put("hasReview", reviewRepo.existsByTransactionId(t.getId()));
    return m;
  }

  /**
   * Transakce přihlášeného — kupce i prodejce, s volitelným omezením na roli
   * (`?role=buyer|seller`), nejnovější první; pro výběr recenze a přehled stavu obchodu.
   */
  @Transactional(readOnly = true)
  public List<Map<String, Object>> mine(String role) {
    User me = current.require();
    String r = role == null ? "all" : role.toLowerCase();
    if (!Set.of("all", "buyer", "seller").contains(r))
      throw ApiException.badRequest("INVALID_ROLE", "Role musí být buyer, seller nebo all.");
    List<SaleTransaction> all = switch (r) {
      case "buyer" -> transactions.findByBuyerIdOrderByIdDesc(me.getId());
      case "seller" -> transactions.findBySellerIdOrderByIdDesc(me.getId());
      default -> transactions.findByBuyerIdOrSellerIdOrderByIdDesc(me.getId(), me.getId());
    };
    return all.stream().map(this::toMap).toList();
  }

  /**
   * Přehled obchodů — souhrn prodejů a nákupů včetně získaného hodnocení
   * (zadání: „Přehled obchodů prodejce“).
   */
  @Transactional(readOnly = true)
  public Map<String, Object> overview() {
    User me = current.require();
    Long id = me.getId();
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("asSeller", side(id, true));
    out.put("asBuyer", side(id, false));
    return out;
  }

  private Map<String, Object> side(Long userId, boolean seller) {
    Map<String, Object> m = new LinkedHashMap<>();
    var rows = seller ? transactions.countByStatusForSeller(userId) : transactions.countByStatusForBuyer(userId);
    long reserved = 0, completed = 0, cancelled = 0, withdrawn = 0;
    for (Object[] r : rows) {
      String status = String.valueOf(r[0]);
      long n = r[1] instanceof Long v ? v : 0;
      switch (status) {
        case "reserved" -> reserved = n;
        case "completed" -> completed = n;
        case "cancelled" -> cancelled = n;
        case "withdrawn" -> withdrawn = n;
        default -> { /* neznámý stav přeskočit */ }
      }
    }
    m.put("reserved", reserved);
    m.put("completed", completed);
    m.put("cancelled", cancelled);
    m.put("withdrawn", withdrawn);
    if (seller) {
      m.put("revenueCzk", transactions.sumCompletedBySeller(userId));
      // Získané hodnocení = průměr z recenzí, které dostal jako prodejce.
      var stats = reviewRepo.ratingStats(userId);
      Object[] s = stats.isEmpty() ? null : stats.get(0);
      double avg = s != null && s[0] instanceof Number n ? n.doubleValue() : 0;
      long count = s != null && s[1] instanceof Number n ? n.longValue() : 0;
      m.put("ratingAverage", Math.round(avg * 10.0) / 10.0);
      m.put("ratingCount", count);
    }
    return m;
  }

  private SaleTransaction require(Long id) {
    return transactions.findById(id)
        .orElseThrow(() -> ApiException.notFound("TRANSACTION_NOT_FOUND", "Transakce nenalezena."));
  }

  private void requireParticipant(SaleTransaction t, User me) {
    if (!t.getBuyerId().equals(me.getId()) && !t.getSellerId().equals(me.getId()))
      throw ApiException.forbidden("NOT_PARTICIPANT", "Nejste účastníkem transakce.");
  }

  /** "buyer" | "seller" z pohledu přihlášeného; null, pokud není přihlášen ani účastník. */
  private String roleOf(SaleTransaction t) {
    Long me = current.currentUserId();
    if (me == null) return null;
    if (me.equals(t.getBuyerId())) return "buyer";
    if (me.equals(t.getSellerId())) return "seller";
    return null;
  }
}