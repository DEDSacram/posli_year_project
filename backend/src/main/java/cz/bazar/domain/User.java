package cz.bazar.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Setter
@Getter
@Entity
@Table(name = "users")
public class User {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(nullable = false, unique = true, columnDefinition = "citext")
  private String email;
  @Column(name = "password_hash", nullable = false)
  private String passwordHash;
  @Column(name = "display_name", nullable = false)
  private String displayName;
  @Column(name = "seller_type", nullable = false)
  private String sellerType;
  @Column(name = "is_admin", nullable = false)
  private boolean admin = false;
  @Column(name = "created_at")
  private OffsetDateTime createdAt;
  @Column(name = "deleted_at")
  private OffsetDateTime deletedAt;
  /** Blokace administrátorem (odlišná od GDPR soft-delete v deleted_at). */
  @Column(name = "blocked_at")
  private OffsetDateTime blockedAt;
  @Column(name = "block_reason")
  private String blockReason;

    public boolean isBlocked() { return blockedAt != null; }
}
