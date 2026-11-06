package cz.bazar.service;

import cz.bazar.domain.SellerTaxProfile;
import cz.bazar.domain.User;
import cz.bazar.exception.ApiException;
import cz.bazar.repository.ReviewRepository;
import cz.bazar.repository.SaleTransactionRepository;
import cz.bazar.repository.SellerTaxProfileRepository;
import cz.bazar.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Veřejné údaje o uživateli a odznaky. Veřejné endpointy nikdy nečtou
 * daňový profil (viz {@link SellerTaxProfile}) — jen jeho stav ověření.
 */
@Service
public class UserService {
  /** Odznaky založené na historii obchodů a hodnocení (zadání: „odznaky pro poctivé obchodníky“). */
  static final int TRUSTED_MIN_REVIEWS = 3;
  static final double TRUSTED_MIN_AVG = 4.5;
  static final int EXPERIENCED_MIN_SALES = 10;
  static final int HIGHLY_RATED_MIN_REVIEWS = 5;
  static final double HIGHLY_RATED_MIN_AVG = 4.8;

  private final UserRepository users;
  private final SellerTaxProfileRepository profiles;
  private final ReviewRepository reviewRepo;
  private final SaleTransactionRepository transactions;
  private final DisputeService disputes;

  public UserService(UserRepository users, SellerTaxProfileRepository profiles,
                     ReviewRepository reviewRepo, SaleTransactionRepository transactions,
                     DisputeService disputes) {
    this.users = users; this.profiles = profiles;
    this.reviewRepo = reviewRepo; this.transactions = transactions;
    this.disputes = disputes;
  }

  public Map<String, Object> publicProfile(Long id) {
    User u = users.findById(id).orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "Uživatel nenalezen."));
    if (u.getDeletedAt() != null) throw ApiException.notFound("USER_NOT_FOUND", "Uživatel nenalezen.");
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", u.getId());
    m.put("displayName", u.getDisplayName());
    m.put("sellerType", u.getSellerType());
    m.put("createdAt", u.getCreatedAt());
    // verejne endpointy danovy profil nectu
    return m;
  }

  /**
   * Odznaky prodejce. Část je statická (typ prodejce, existence daňového profilu,
   * administrátorem potvrzený doklad), část se odvozuje z reálné historie:
   * průměrné hodnocení, počet dokončených prodejů a potvrzené námitky.
   */
  @Transactional(readOnly = true)
  public Map<String, Object> badges(Long id) {
    User u = users.findById(id).orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "Uživatel nenalezen."));
    SellerTaxProfile p = profiles.findById(id).orElse(null);
    boolean isTrader = "trader".equals(u.getSellerType());
    boolean verifiedTrader = p != null && p.isVerified();

    var stats = ratingStats(id);
    double avg = stats.average;
    long reviewCount = stats.count;
    long completedSales = transactions.countCompletedBySeller(id);
    long upheld = disputes.upheldAgainst(id);

    boolean trustedSeller = reviewCount >= TRUSTED_MIN_REVIEWS && avg >= TRUSTED_MIN_AVG && upheld == 0;
    boolean highlyRated = reviewCount >= HIGHLY_RATED_MIN_REVIEWS && avg >= HIGHLY_RATED_MIN_AVG;
    boolean experienced = completedSales >= EXPERIENCED_MIN_SALES;
    boolean cleanRecord = upheld == 0;

    Map<String, Object> m = new LinkedHashMap<>();
    m.put("trader", isTrader);
    m.put("privateSeller", !isTrader);
    m.put("hasDac7Profile", p != null);
    m.put("verifiedTrader", verifiedTrader);
    m.put("trustedSeller", trustedSeller);
    m.put("highlyRated", highlyRated);
    m.put("experienced", experienced);
    m.put("cleanRecord", cleanRecord);
    m.put("ratingAverage", Math.round(avg * 10.0) / 10.0);
    m.put("ratingCount", reviewCount);
    m.put("completedSales", completedSales);
    return m;
  }

  /** Prumerne hodnoceni prodejce z viditelnych recenzi (pro hvezdicky u jmena). */
  @Transactional(readOnly = true)
  public Map<String, Object> rating(Long id) {
    if (!users.existsById(id)) throw ApiException.notFound("USER_NOT_FOUND", "Uživatel nenalezen.");
    var stats = ratingStats(id);
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("average", Math.round(stats.average * 10.0) / 10.0);
    m.put("count", stats.count);
    return m;
  }

  private record Stats(double average, long count) {}

  @Transactional(readOnly = true)
  public Stats ratingStats(Long userId) {
    List<Object[]> rows = reviewRepo.ratingStats(userId);
    Object[] s = rows.isEmpty() ? null : rows.get(0);
    double avg = s != null && s[0] instanceof Number n ? n.doubleValue() : 0;
    long count = s != null && s[1] instanceof Number m ? m.longValue() : 0;
    return new Stats(avg, count);
  }
}