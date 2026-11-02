package cz.bazar.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "favorites",
    uniqueConstraints = @UniqueConstraint(name = "favorites_user_listing_uq",
        columnNames = {"user_id", "listing_id"}))
public class Favorite {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(name = "user_id", nullable = false)
  private Long userId;
  @Column(name = "listing_id", nullable = false)
  private Long listingId;
  @Column(name = "created_at")
  private OffsetDateTime createdAt;
  @Column(name = "price_at_save")
  private Integer priceAtSave;

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public Long getUserId() { return userId; }
  public void setUserId(Long userId) { this.userId = userId; }
  public Long getListingId() { return listingId; }
  public void setListingId(Long listingId) { this.listingId = listingId; }
  public OffsetDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
  public Integer getPriceAtSave() { return priceAtSave; }
  public void setPriceAtSave(Integer priceAtSave) { this.priceAtSave = priceAtSave; }
}
