package cz.bazar.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "seller_tax_profile")
public class SellerTaxProfile {
  public static final String STATUS_PENDING = "pending";
  public static final String STATUS_VERIFIED = "verified";
  public static final String STATUS_REJECTED = "rejected";

  @Id
  @Column(name = "user_id")
  private Long userId;
  @Column(name = "full_name", nullable = false)
  private byte[] fullName;
  @Column(name = "date_of_birth", nullable = false)
  private byte[] dateOfBirth;
  @Column(nullable = false)
  private byte[] street;
  @Column(nullable = false)
  private byte[] city;
  @Column(name = "postal_code", nullable = false)
  private byte[] postalCode;
  @Column(nullable = false)
  private byte[] country;
  @Column(name = "tax_id")
  private byte[] taxId;
  @Column
  private byte[] ico;
  @Column(nullable = false)
  private byte[] iban;
  @Column(name = "self_cert", nullable = false)
  private boolean selfCert = false;
  @Column(name = "id_doc_bytea")
  private byte[] idDoc;
  @Column(nullable = false)
  private boolean verified = false;
  /** pending | verified | rejected — rozhodnutí administrátora (DSA ověřování obchodníků). */
  @Column(name = "verification_status", nullable = false)
  private String verificationStatus = STATUS_PENDING;
  @Column(name = "verification_note")
  private String verificationNote;
  @Column(name = "verified_at")
  private OffsetDateTime verifiedAt;
  @Column(name = "verified_by")
  private Long verifiedBy;
  @Column(name = "updated_at")
  private OffsetDateTime updatedAt;

  public Long getUserId() { return userId; }
  public void setUserId(Long userId) { this.userId = userId; }
  public byte[] getFullName() { return fullName; }
  public void setFullName(byte[] fullName) { this.fullName = fullName; }
  public byte[] getDateOfBirth() { return dateOfBirth; }
  public void setDateOfBirth(byte[] dateOfBirth) { this.dateOfBirth = dateOfBirth; }
  public byte[] getStreet() { return street; }
  public void setStreet(byte[] street) { this.street = street; }
  public byte[] getCity() { return city; }
  public void setCity(byte[] city) { this.city = city; }
  public byte[] getPostalCode() { return postalCode; }
  public void setPostalCode(byte[] postalCode) { this.postalCode = postalCode; }
  public byte[] getCountry() { return country; }
  public void setCountry(byte[] country) { this.country = country; }
  public byte[] getTaxId() { return taxId; }
  public void setTaxId(byte[] taxId) { this.taxId = taxId; }
  public byte[] getIco() { return ico; }
  public void setIco(byte[] ico) { this.ico = ico; }
  public byte[] getIban() { return iban; }
  public void setIban(byte[] iban) { this.iban = iban; }
  public boolean isSelfCert() { return selfCert; }
  public void setSelfCert(boolean selfCert) { this.selfCert = selfCert; }
  public byte[] getIdDoc() { return idDoc; }
  public void setIdDoc(byte[] idDoc) { this.idDoc = idDoc; }
  public boolean isVerified() { return verified; }
  public void setVerified(boolean verified) { this.verified = verified; }
  public String getVerificationStatus() { return verificationStatus; }
  public void setVerificationStatus(String v) { this.verificationStatus = v; }
  public String getVerificationNote() { return verificationNote; }
  public void setVerificationNote(String v) { this.verificationNote = v; }
  public OffsetDateTime getVerifiedAt() { return verifiedAt; }
  public void setVerifiedAt(OffsetDateTime v) { this.verifiedAt = v; }
  public Long getVerifiedBy() { return verifiedBy; }
  public void setVerifiedBy(Long v) { this.verifiedBy = v; }
  /** Nastaví stav i odvozený příznak verified, aby obě reprezentace souhlasily. */
  public void applyVerificationStatus(String status) {
    this.verificationStatus = status;
    this.verified = STATUS_VERIFIED.equals(status);
  }
  public OffsetDateTime getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
