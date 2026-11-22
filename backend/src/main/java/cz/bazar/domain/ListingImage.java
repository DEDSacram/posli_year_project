package cz.bazar.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
@Table(name = "listing_images")
public class ListingImage {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(name = "listing_id", nullable = false)
  private Long listingId;
  @Column(nullable = false)
  private byte[] data;
  @Column(nullable = false)
  private String mime;
  @Column(name = "sort_order", nullable = false)
  private int sortOrder = 0;

}
