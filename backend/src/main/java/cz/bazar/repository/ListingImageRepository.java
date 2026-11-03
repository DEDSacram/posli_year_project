package cz.bazar.repository;

import cz.bazar.domain.ListingImage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ListingImageRepository extends JpaRepository<ListingImage, Long> {
  List<ListingImage> findByListingIdOrderBySortOrderAscIdAsc(Long listingId);
}
