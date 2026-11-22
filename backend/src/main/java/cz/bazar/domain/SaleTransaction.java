package cz.bazar.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Setter
@Getter
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

}
