package cz.bazar.domain;

import jakarta.persistence.*;

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

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public Long getListingId() { return listingId; }
  public void setListingId(Long listingId) { this.listingId = listingId; }
  public byte[] getData() { return data; }
  public void setData(byte[] data) { this.data = data; }
  public String getMime() { return mime; }
  public void setMime(String mime) { this.mime = mime; }
  public int getSortOrder() { return sortOrder; }
  public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
}
