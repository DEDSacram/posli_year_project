package cz.bazar.repository;

import cz.bazar.domain.SellerTaxProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface SellerTaxProfileRepository extends JpaRepository<SellerTaxProfile, Long> {
  long countByVerificationStatus(String verificationStatus);

  /** Obchodníci s nedoloženým / čekajícím na ověření, nejstarší změna první. */
  @Query("select p from SellerTaxProfile p join cz.bazar.domain.User u on u.id = p.userId "
      + "where u.sellerType = 'trader' and p.idDoc is not null and u.deletedAt is null "
      + "and p.verificationStatus <> 'verified' order by p.updatedAt asc")
  List<SellerTaxProfile> findTradersAwaitingVerification();

  /** Obchodníci čekající na rozhodnutí (bez dokladu nelze rozhodnout). */
  @Query("select p from SellerTaxProfile p join cz.bazar.domain.User u on u.id = p.userId "
      + "where u.sellerType = 'trader' and p.idDoc is not null and u.deletedAt is null "
      + "and p.verificationStatus = 'pending' order by p.updatedAt asc")
  List<SellerTaxProfile> findPendingTraderVerifications();
}