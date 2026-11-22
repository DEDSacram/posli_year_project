package cz.bazar.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Setter
@Getter
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

}
