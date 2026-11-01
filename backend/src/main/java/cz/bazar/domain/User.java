package cz.bazar.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

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

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public String getEmail() { return email; }
  public void setEmail(String email) { this.email = email; }
  public String getPasswordHash() { return passwordHash; }
  public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
  public String getDisplayName() { return displayName; }
  public void setDisplayName(String displayName) { this.displayName = displayName; }
  public String getSellerType() { return sellerType; }
  public void setSellerType(String sellerType) { this.sellerType = sellerType; }
  public boolean isAdmin() { return admin; }
  public void setAdmin(boolean admin) { this.admin = admin; }
  public OffsetDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
  public OffsetDateTime getDeletedAt() { return deletedAt; }
  public void setDeletedAt(OffsetDateTime deletedAt) { this.deletedAt = deletedAt; }
  public OffsetDateTime getBlockedAt() { return blockedAt; }
  public void setBlockedAt(OffsetDateTime v) { this.blockedAt = v; }
  public String getBlockReason() { return blockReason; }
  public void setBlockReason(String v) { this.blockReason = v; }
  public boolean isBlocked() { return blockedAt != null; }
}
