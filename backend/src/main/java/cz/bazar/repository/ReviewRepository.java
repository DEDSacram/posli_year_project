package cz.bazar.repository;

import cz.bazar.domain.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

public interface ReviewRepository extends JpaRepository<Review, Long> {
  Optional<Review> findByTransactionId(Long transactionId);
  boolean existsByTransactionId(Long transactionId);
  List<Review> findByRevieweeIdAndModerationStatusAndIdLessThanOrderByIdDesc(Long revieweeId, String status, Long cursor, Pageable p);
  List<Review> findByRevieweeIdAndModerationStatusOrderByIdDesc(Long revieweeId, String status, Pageable p);

  @Query("select coalesce(avg(r.rating), 0), count(r) from Review r where r.revieweeId = :userId and r.moderationStatus = 'visible'")
  List<Object[]> ratingStats(Long userId);

  /** Počet odstraněných hodnocení (moderace nebo potvrzená námitka) — pro odznaky. */
  long countByRevieweeIdAndModerationStatus(Long revieweeId, String moderationStatus);

  List<Review> findByRevieweeIdOrderByIdDesc(Long revieweeId, Pageable p);
}
