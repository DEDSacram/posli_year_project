package cz.bazar.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

/** Upozornění v profilu uživatele — dnes pouze zlevnění inzerátu z oblíbených. */
@Entity
@Table(name = "notifications")
public class Notification {
  public static final String TYPE_PRICE_DROP = "price_drop";

  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(name = "user_id", nullable = false)
  private Long userId;
  @Column(nullable = false)
  private String type;
  @Column(name = "listing_id", nullable = false)
  private Long listingId;
  @Column(nullable = false)
  private String title;
  @Column(name = "old_price_czk")
  private Integer oldPriceCzk;
  @Column(name = "new_price_czk")
  private Integer newPriceCzk;
  @Column(name = "created_at")
  private OffsetDateTime createdAt;
  @Column(name = "read_at")
  private OffsetDateTime readAt;

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public Long getUserId() { return userId; }
  public void setUserId(Long v) { this.userId = v; }
  public String getType() { return type; }
  public void setType(String v) { this.type = v; }
  public Long getListingId() { return listingId; }
  public void setListingId(Long v) { this.listingId = v; }
  public String getTitle() { return title; }
  public void setTitle(String v) { this.title = v; }
  public Integer getOldPriceCzk() { return oldPriceCzk; }
  public void setOldPriceCzk(Integer v) { this.oldPriceCzk = v; }
  public Integer getNewPriceCzk() { return newPriceCzk; }
  public void setNewPriceCzk(Integer v) { this.newPriceCzk = v; }
  public OffsetDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }
  public OffsetDateTime getReadAt() { return readAt; }
  public void setReadAt(OffsetDateTime v) { this.readAt = v; }
  public boolean isRead() { return readAt != null; }
}