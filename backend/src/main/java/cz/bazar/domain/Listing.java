package cz.bazar.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Setter
@Getter
@Entity
@Table(name = "listings")
public class Listing {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(name = "seller_id", nullable = false)
  private Long sellerId;
  @Column(nullable = false)
  private String title;
  @Column(nullable = false)
  private String description;
  @Column(name = "price_czk", nullable = false)
  private Integer priceCzk;
  @Column(nullable = false)
  private String category;
  @Column(name = "condition", nullable = false)
  private String conditionValue;
  @Column(nullable = false)
  private String city;
  private Double lat;
  private Double lon;
  @Column(name = "seller_type_snapshot", nullable = false)
  private String sellerTypeSnapshot;
  @Column(nullable = false)
  private String status = "active";
  @Column(name = "created_at")
  private OffsetDateTime createdAt;

}
