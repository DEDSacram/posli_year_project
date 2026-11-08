package cz.bazar.service;

import cz.bazar.domain.Review;
import cz.bazar.domain.SaleTransaction;
import cz.bazar.domain.User;
import cz.bazar.dto.Dtos.*;
import cz.bazar.exception.ApiException;
import cz.bazar.repository.ReviewRepository;
import cz.bazar.repository.SaleTransactionRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;

@Service
public class ReviewService {
  private final ReviewRepository reviews;
  private final SaleTransactionRepository transactions;
  private final CurrentUserService current;
  private final DisputeService disputes;

  public ReviewService(ReviewRepository reviews, SaleTransactionRepository transactions,
                       CurrentUserService current, DisputeService disputes) {
    this.reviews = reviews; this.transactions = transactions;
    this.current = current; this.disputes = disputes;
  }

  @Transactional
  public Review create(Long transactionId, ReviewCreate r) {
    User me = current.require();
    SaleTransaction t = transactions.findById(transactionId)
        .orElseThrow(() -> ApiException.notFound("TRANSACTION_NOT_FOUND", "Transakce nenalezena."));
    if (!"completed".equals(t.getStatus())) throw ApiException.badRequest("NOT_COMPLETED", "Hodnotit lze jen dokončenou transakci.");
    if (!t.getBuyerId().equals(me.getId())) throw ApiException.forbidden("ONLY_BUYER", "Hodnotit může jen kupující.");
    if (reviews.findByTransactionId(transactionId).isPresent())
      throw ApiException.conflict("REVIEW_EXISTS", "Transakce již byla hodnocena.");
    Review rev = new Review();
    rev.setTransactionId(transactionId);
    rev.setReviewerId(me.getId());
    rev.setRevieweeId(t.getSellerId());
    rev.setListingId(t.getListingId());
    rev.setRating((short) r.rating());
    rev.setText(r.text());
    rev.setVerifiedPurchase(true); // overeny nakup
    rev.setModerationStatus("visible");
    rev.setCreatedAt(OffsetDateTime.now());
    return reviews.save(rev);
  }

  @Transactional
  public Review respond(Long reviewId, ReviewResponseCreate r) {
    User me = current.require();
    Review rev = reviews.findById(reviewId)
        .orElseThrow(() -> ApiException.notFound("REVIEW_NOT_FOUND", "Recenze nenalezena."));
    if (!rev.getRevieweeId().equals(me.getId()))
      throw ApiException.forbidden("NOT_REVIEWEE", "Odpovědět může jen hodnocený.");
    if (rev.getResponseText() != null)
      throw ApiException.conflict("RESPONSE_EXISTS", "Odpověď již existuje.");
    rev.setResponseText(r.text());
    return reviews.save(rev);
  }

  @Transactional(readOnly = true)
  public Page<Map<String, Object>> visibleForUser(Long userId, Long cursor, int limit) {
    limit = Math.min(Math.max(limit, 1), 50);
    List<Review> rows = cursor == null
        ? reviews.findByRevieweeIdAndModerationStatusOrderByIdDesc(userId, "visible", PageRequest.of(0, limit + 1))
        : reviews.findByRevieweeIdAndModerationStatusAndIdLessThanOrderByIdDesc(userId, "visible", cursor, PageRequest.of(0, limit + 1));
    String next = null;
    List<Review> page = rows;
    if (rows.size() > limit) {
      page = rows.subList(0, limit);
      next = String.valueOf(page.get(page.size() - 1).getId());
    }
    return new Page<>(page.stream().map(this::toMap).toList(), next);
  }

  @Transactional(readOnly = true)
  public Map<String, Object> toMap(Review r) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", r.getId());
    m.put("transactionId", r.getTransactionId());
    m.put("reviewerId", r.getReviewerId());
    m.put("revieweeId", r.getRevieweeId());
    m.put("listingId", r.getListingId());
    m.put("rating", r.getRating());
    m.put("text", r.getText());
    m.put("verifiedPurchase", r.isVerifiedPurchase());
    m.put("moderationStatus", r.getModerationStatus());
    m.put("responseText", r.getResponseText());
    // Námitka prodejce: open = čeká na admin, upheld/rejected = rozhodnuto.
    m.put("disputeStatus", disputes.statusOf(r.getId()));
    m.put("createdAt", r.getCreatedAt());
    return m;
  }
}
