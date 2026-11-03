package cz.bazar.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "transactions")
public class SaleTransaction {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(name = "listing_id", nullable = false)
  private Long listingId;
  @Column(name = "buyer_id", nullable = false)
  private Long buyerId;
  @Column(name = "seller_id", nullable = false)
  private Long sellerId;
  @Column(name = "price_czk", nullable = false)
  private Integer priceCzk;
  @Column(nullable = false)
  private String status = "reserved";
  @Column(name = "seller_type_snapshot", nullable = false)
  private String sellerTypeSnapshot;
  @Column(name = "completed_at")
  private OffsetDateTime completedAt;
  /** Odstoupení spotřebitele od smlouvy (14 dní od dokončení, § 1829 OZ). */
  @Column(name = "withdrawn_at")
  private OffsetDateTime withdrawnAt;
  @Column(name = "withdrawal_reason")
  private String withdrawalReason;

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public Long getListingId() { return listingId; }
  public void setListingId(Long listingId) { this.listingId = listingId; }
  public Long getBuyerId() { return buyerId; }
  public void setBuyerId(Long buyerId) { this.buyerId = buyerId; }
  public Long getSellerId() { return sellerId; }
  public void setSellerId(Long sellerId) { this.sellerId = sellerId; }
  public Integer getPriceCzk() { return priceCzk; }
  public void setPriceCzk(Integer priceCzk) { this.priceCzk = priceCzk; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public String getSellerTypeSnapshot() { return sellerTypeSnapshot; }
  public void setSellerTypeSnapshot(String s) { this.sellerTypeSnapshot = s; }
  public OffsetDateTime getCompletedAt() { return completedAt; }
  public void setCompletedAt(OffsetDateTime completedAt) { this.completedAt = completedAt; }
  public OffsetDateTime getWithdrawnAt() { return withdrawnAt; }
  public void setWithdrawnAt(OffsetDateTime v) { this.withdrawnAt = v; }
  public String getWithdrawalReason() { return withdrawalReason; }
  public void setWithdrawalReason(String v) { this.withdrawalReason = v; }
}
