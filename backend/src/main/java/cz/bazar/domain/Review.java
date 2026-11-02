package cz.bazar.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "reviews")
public class Review {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(name = "transaction_id", nullable = false, unique = true)
  private Long transactionId;
  @Column(name = "reviewer_id", nullable = false)
  private Long reviewerId;
  @Column(name = "reviewee_id", nullable = false)
  private Long revieweeId;
  @Column(name = "listing_id", nullable = false)
  private Long listingId;
  @Column(nullable = false)
  private Short rating;
  @Column(nullable = false)
  private String text;
  @Column(name = "is_verified_purchase", nullable = false)
  private boolean verifiedPurchase = true;
  @Column(name = "moderation_status", nullable = false)
  private String moderationStatus = "visible";
  @Column(name = "response_text")
  private String responseText;
  @Column(name = "created_at")
  private OffsetDateTime createdAt;

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public Long getTransactionId() { return transactionId; }
  public void setTransactionId(Long transactionId) { this.transactionId = transactionId; }
  public Long getReviewerId() { return reviewerId; }
  public void setReviewerId(Long reviewerId) { this.reviewerId = reviewerId; }
  public Long getRevieweeId() { return revieweeId; }
  public void setRevieweeId(Long revieweeId) { this.revieweeId = revieweeId; }
  public Long getListingId() { return listingId; }
  public void setListingId(Long listingId) { this.listingId = listingId; }
  public Short getRating() { return rating; }
  public void setRating(Short rating) { this.rating = rating; }
  public String getText() { return text; }
  public void setText(String text) { this.text = text; }
  public boolean isVerifiedPurchase() { return verifiedPurchase; }
  public void setVerifiedPurchase(boolean v) { this.verifiedPurchase = v; }
  public String getModerationStatus() { return moderationStatus; }
  public void setModerationStatus(String s) { this.moderationStatus = s; }
  public String getResponseText() { return responseText; }
  public void setResponseText(String responseText) { this.responseText = responseText; }
  public OffsetDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
