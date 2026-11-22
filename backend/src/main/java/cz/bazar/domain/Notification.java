package cz.bazar.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/** Upozornění v profilu uživatele — dnes pouze zlevnění inzerátu z oblíbených. */
@Setter
@Getter
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

    public boolean isRead() { return readAt != null; }
}