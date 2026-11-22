package cz.bazar.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/** Námitka prodejce proti hodnocení; rozhoduje administrátor. */
@Setter
@Getter
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

}