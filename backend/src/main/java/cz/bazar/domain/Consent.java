package cz.bazar.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Setter
@Getter
@Entity
@Table(name = "consents")
public class Consent {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(name = "user_id", nullable = false)
  private Long userId;
  @Column(nullable = false)
  private String purpose;
  @Column(nullable = false)
  private boolean granted;
  @Column(name = "created_at")
  private OffsetDateTime createdAt;

}
