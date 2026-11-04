package cz.bazar.repository;

import cz.bazar.domain.Favorite;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface FavoriteRepository extends JpaRepository<Favorite, Long> {
  List<Favorite> findByUserIdOrderByIdDesc(Long userId);
  boolean existsByUserIdAndListingId(Long userId, Long listingId);
  void deleteByUserIdAndListingId(Long userId, Long listingId);
  void deleteByUserId(Long userId);
  java.util.Optional<Favorite> findByUserIdAndListingId(Long userId, Long listingId);

  /** Všichni, kdo mají inzerát v oblíbených — příjemci upozornění na zlevnění. */
  java.util.List<Favorite> findByListingId(Long listingId);
}
