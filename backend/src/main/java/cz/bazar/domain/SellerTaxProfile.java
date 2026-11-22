package cz.bazar.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Setter
@Getter
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

    /** Nastaví stav i odvozený příznak verified, aby obě reprezentace souhlasily. */
  public void applyVerificationStatus(String status) {
    this.verificationStatus = status;
    this.verified = STATUS_VERIFIED.equals(status);
  }

}
