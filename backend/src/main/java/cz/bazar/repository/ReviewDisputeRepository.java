package cz.bazar.repository;

import cz.bazar.domain.ReviewDispute;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReviewDisputeRepository extends JpaRepository<ReviewDispute, Long> {
  Optional<ReviewDispute> findByReviewIdAndStatus(Long reviewId, String status);

  List<ReviewDispute> findByDisputerIdOrderByIdDesc(Long disputerId);

  List<ReviewDispute> findByStatusOrderByIdAsc(String status, Pageable pageable);

  long countByDisputerIdAndStatus(Long disputerId, String status);

  long countByStatus(String status);

  /** Poslední námitka (libovolného stavu) k dané recenzi — pro zobrazení stavu u hodnocení. */
  @Query("select d from ReviewDispute d where d.reviewId = :reviewId order by d.id desc")
  List<ReviewDispute> findLatestForReview(@Param("reviewId") Long reviewId);
}