package cz.bazar.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "conversations", uniqueConstraints = @UniqueConstraint(columnNames = {"buyer_id","seller_id","listing_id"}))
public class Conversation {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(name = "listing_id", nullable = false)
  private Long listingId;
  @Column(name = "buyer_id", nullable = false)
  private Long buyerId;
  @Column(name = "seller_id", nullable = false)
  private Long sellerId;
  @Column(name = "created_at")
  private OffsetDateTime createdAt;

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public Long getListingId() { return listingId; }
  public void setListingId(Long listingId) { this.listingId = listingId; }
  public Long getBuyerId() { return buyerId; }
  public void setBuyerId(Long buyerId) { this.buyerId = buyerId; }
  public Long getSellerId() { return sellerId; }
  public void setSellerId(Long sellerId) { this.sellerId = sellerId; }
  public OffsetDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
