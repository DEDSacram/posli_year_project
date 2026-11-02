package cz.bazar.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

/** Námitka prodejce proti hodnocení; rozhoduje administrátor. */
@Entity
@Table(name = "review_disputes")
public class ReviewDispute {
  public static final String STATUS_OPEN = "open";
  public static final String STATUS_UPHELD = "upheld";
  public static final String STATUS_REJECTED = "rejected";

  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(name = "review_id", nullable = false)
  private Long reviewId;
  @Column(name = "disputer_id", nullable = false)
  private Long disputerId;
  @Column(nullable = false)
  private String reason;
  @Column(nullable = false)
  private String status = STATUS_OPEN;
  @Column(name = "resolution_note")
  private String resolutionNote;
  @Column(name = "resolved_by")
  private Long resolvedBy;
  @Column(name = "created_at")
  private OffsetDateTime createdAt;
  @Column(name = "resolved_at")
  private OffsetDateTime resolvedAt;

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public Long getReviewId() { return reviewId; }
  public void setReviewId(Long v) { this.reviewId = v; }
  public Long getDisputerId() { return disputerId; }
  public void setDisputerId(Long v) { this.disputerId = v; }
  public String getReason() { return reason; }
  public void setReason(String v) { this.reason = v; }
  public String getStatus() { return status; }
  public void setStatus(String v) { this.status = v; }
  public String getResolutionNote() { return resolutionNote; }
  public void setResolutionNote(String v) { this.resolutionNote = v; }
  public Long getResolvedBy() { return resolvedBy; }
  public void setResolvedBy(Long v) { this.resolvedBy = v; }
  public OffsetDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }
  public OffsetDateTime getResolvedAt() { return resolvedAt; }
  public void setResolvedAt(OffsetDateTime v) { this.resolvedAt = v; }
}