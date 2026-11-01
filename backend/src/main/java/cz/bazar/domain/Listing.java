package cz.bazar.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

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

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public Long getSellerId() { return sellerId; }
  public void setSellerId(Long sellerId) { this.sellerId = sellerId; }
  public String getTitle() { return title; }
  public void setTitle(String title) { this.title = title; }
  public String getDescription() { return description; }
  public void setDescription(String description) { this.description = description; }
  public Integer getPriceCzk() { return priceCzk; }
  public void setPriceCzk(Integer priceCzk) { this.priceCzk = priceCzk; }
  public String getCategory() { return category; }
  public void setCategory(String category) { this.category = category; }
  public String getConditionValue() { return conditionValue; }
  public void setConditionValue(String conditionValue) { this.conditionValue = conditionValue; }
  public String getCity() { return city; }
  public void setCity(String city) { this.city = city; }
  public Double getLat() { return lat; }
  public void setLat(Double lat) { this.lat = lat; }
  public Double getLon() { return lon; }
  public void setLon(Double lon) { this.lon = lon; }
  public String getSellerTypeSnapshot() { return sellerTypeSnapshot; }
  public void setSellerTypeSnapshot(String s) { this.sellerTypeSnapshot = s; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public OffsetDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
