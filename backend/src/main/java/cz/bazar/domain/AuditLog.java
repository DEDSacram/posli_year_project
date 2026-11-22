package cz.bazar.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Setter
@Getter
@Entity
@Table(name = "audit_log")
public class AuditLog {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(name = "actor_id")
  private Long actorId;
  @Column(nullable = false)
  private String action;
  @Column(name = "target_type")
  private String targetType;
  @Column(name = "target_id")
  private Long targetId;
  @Column(name = "created_at")
  private OffsetDateTime createdAt;

}
