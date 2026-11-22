package cz.bazar.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Setter
@Getter
@Entity
@Table(name = "reports")
public class Report {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(name = "target_type", nullable = false)
  private String targetType;
  @Column(name = "target_id", nullable = false)
  private Long targetId;
  @Column(name = "reporter_id", nullable = false)
  private Long reporterId;
  @Column(nullable = false)
  private String reason;
  @Column(name = "trusted_flagger", nullable = false)
  private boolean trustedFlagger = false;
  @Column(nullable = false)
  private String status = "open";
  @Column(name = "created_at")
  private OffsetDateTime createdAt;

}
