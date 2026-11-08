package cz.bazar.service;

import cz.bazar.domain.Review;
import cz.bazar.domain.ReviewDispute;
import cz.bazar.domain.User;
import cz.bazar.exception.ApiException;
import cz.bazar.repository.ReviewDisputeRepository;
import cz.bazar.repository.ReviewRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Námitky prodejce proti neférovému hodnocení („Námitky proti hodnocení“).
 * Podat smí jen hodnocený prodejce, a to nejvýše jednou otevřenou námitku na recenzi;
 * otevřenou námitku smí vyhodnotit jen admin.
 */
@Service
public class DisputeService {
  private final ReviewDisputeRepository disputes;
  private final ReviewRepository reviews;
  private final CurrentUserService current;
  private final AuditService audit;

  public DisputeService(ReviewDisputeRepository disputes, ReviewRepository reviews,
                        CurrentUserService current, AuditService audit) {
    this.disputes = disputes; this.reviews = reviews;
    this.current = current; this.audit = audit;
  }

  @Transactional
  public ReviewDispute create(Long reviewId, String reason) {
    User me = current.require();
    Review rev = reviews.findById(reviewId)
        .orElseThrow(() -> ApiException.notFound("REVIEW_NOT_FOUND", "Recenze nenalezena."));
    if (!rev.getRevieweeId().equals(me.getId()))
      throw ApiException.forbidden("NOT_REVIEWEE", "Námitku může podat jen hodnocený prodejce.");
    if (!"visible".equals(rev.getModerationStatus()))
      throw ApiException.badRequest("REVIEW_NOT_VISIBLE", "Recenze už není veřejně viditelná.");
    if (disputes.findByReviewIdAndStatus(reviewId, ReviewDispute.STATUS_OPEN).isPresent())
      throw ApiException.conflict("DISPUTE_OPEN", "K této recenzi už existuje otevřená námitka.");
    ReviewDispute d = new ReviewDispute();
    d.setReviewId(reviewId);
    d.setDisputerId(me.getId());
    d.setReason(reason);
    d.setStatus(ReviewDispute.STATUS_OPEN);
    d.setCreatedAt(OffsetDateTime.now());
    ReviewDispute saved = disputes.save(d);
    audit.log(me.getId(), "REVIEW_DISPUTE_OPEN", "review", reviewId);
    return saved;
  }

  /** Námitky podané přihlášeným prodejcem (pro jeho profil a seznam hodnocení). */
  @Transactional(readOnly = true)
  public List<Map<String, Object>> mine() {
    User me = current.require();
    return disputes.findByDisputerIdOrderByIdDesc(me.getId()).stream().map(this::toMap).toList();
  }

  /** Otevřená námitka k dané recenzi — pro zobrazení stavu námitky. */
  @Transactional(readOnly = true)
  public Map<String, Object> openForReview(Long reviewId) {
    return disputes.findByReviewIdAndStatus(reviewId, ReviewDispute.STATUS_OPEN)
        .map(this::toMap).orElse(null);
  }

  /** Poslední námitka (kterákoliv stav) — kratší tvar pro API hodnocení. */
  @Transactional(readOnly = true)
  public String statusOf(Long reviewId) {
    List<ReviewDispute> rows = disputes.findLatestForReview(reviewId);
    return rows.isEmpty() ? null : rows.get(0).getStatus();
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> queue(int limit) {
    int n = Math.min(Math.max(limit, 1), 50);
    return disputes.findByStatusOrderByIdAsc(ReviewDispute.STATUS_OPEN,
            org.springframework.data.domain.PageRequest.of(0, n)).stream()
        .map(this::toMap).toList();
  }

  @Transactional(readOnly = true)
  public long openCount() {
    return disputes.countByStatus(ReviewDispute.STATUS_OPEN);
  }

  /**
   * Rozhodnutí administrátora. `uphold` = námitka oprávněná → recenze se odstraní
   * z veřejného zobrazení a nepočítá se do hodnocení; `reject` = námitka zamítnuta,
   * recenze zůstává v platnosti.
   */
  @Transactional
  public ReviewDispute resolve(Long id, boolean uphold, String note) {
    User admin = current.require();
    ReviewDispute d = disputes.findById(id)
        .orElseThrow(() -> ApiException.notFound("DISPUTE_NOT_FOUND", "Námitka nenalezena."));
    if (!ReviewDispute.STATUS_OPEN.equals(d.getStatus()))
      throw ApiException.conflict("DISPUTE_RESOLVED", "Námitka už byla rozhodnuta.");
    d.setStatus(uphold ? ReviewDispute.STATUS_UPHELD : ReviewDispute.STATUS_REJECTED);
    d.setResolutionNote(note);
    d.setResolvedBy(admin.getId());
    d.setResolvedAt(OffsetDateTime.now());
    if (uphold) {
      reviews.findById(d.getReviewId()).ifPresent(r -> {
        r.setModerationStatus("removed");
        reviews.save(r);
      });
    }
    ReviewDispute saved = disputes.save(d);
    audit.log(admin.getId(), uphold ? "REVIEW_DISPUTE_UPHELD" : "REVIEW_DISPUTE_REJECTED",
        "review", d.getReviewId());
    return saved;
  }

  /** Počet potvrzených námek proti uživateli — vstup pro odznak „bez stížností“. */
  @Transactional(readOnly = true)
  public long upheldAgainst(Long userId) {
    // Námitky vede vždy reviewee, takže stačí počítat podle jeho ID.
    return disputes.countByDisputerIdAndStatus(userId, ReviewDispute.STATUS_UPHELD);
  }

  public Map<String, Object> toMap(ReviewDispute d) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", d.getId());
    m.put("reviewId", d.getReviewId());
    m.put("disputerId", d.getDisputerId());
    m.put("reason", d.getReason());
    m.put("status", d.getStatus());
    m.put("resolutionNote", d.getResolutionNote());
    m.put("createdAt", d.getCreatedAt());
    m.put("resolvedAt", d.getResolvedAt());
    return m;
  }
}