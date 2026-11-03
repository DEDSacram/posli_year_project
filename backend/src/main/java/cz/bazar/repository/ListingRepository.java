package cz.bazar.repository;

import cz.bazar.domain.Listing;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import org.springframework.data.domain.Pageable;

public interface ListingRepository extends JpaRepository<Listing, Long> {
  List<Listing> findByStatusOrderByIdDesc(String status, Pageable pageable);
  List<Listing> findByStatusAndIdLessThanOrderByIdDesc(String status, Long cursor, Pageable pageable);
  List<Listing> findBySellerIdOrderByIdDesc(Long sellerId, Pageable pageable);
  List<Listing> findByStatusAndCategory(String status, String category, Pageable pageable);
  long countByStatusAndCategory(String status, String category);
}
