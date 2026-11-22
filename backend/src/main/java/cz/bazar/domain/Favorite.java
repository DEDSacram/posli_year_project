package cz.bazar.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Setter
@Getter
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

}
