package cz.bazar.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Setter
@Getter
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

}
