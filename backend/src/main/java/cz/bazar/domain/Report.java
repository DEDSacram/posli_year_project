package cz.bazar.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

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

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public String getTargetType() { return targetType; }
  public void setTargetType(String targetType) { this.targetType = targetType; }
  public Long getTargetId() { return targetId; }
  public void setTargetId(Long targetId) { this.targetId = targetId; }
  public Long getReporterId() { return reporterId; }
  public void setReporterId(Long reporterId) { this.reporterId = reporterId; }
  public String getReason() { return reason; }
  public void setReason(String reason) { this.reason = reason; }
  public boolean isTrustedFlagger() { return trustedFlagger; }
  public void setTrustedFlagger(boolean trustedFlagger) { this.trustedFlagger = trustedFlagger; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public OffsetDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
