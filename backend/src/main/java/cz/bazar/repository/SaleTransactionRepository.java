package cz.bazar.repository;

import cz.bazar.domain.SaleTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface SaleTransactionRepository extends JpaRepository<SaleTransaction, Long> {
  @Query("select count(t) from SaleTransaction t where t.sellerId = :sellerId and t.status = 'completed' and year(t.completedAt) = :year")
  long countCompletedBySellerAndYear(Long sellerId, int year);

  @Query("select coalesce(sum(t.priceCzk),0) from SaleTransaction t where t.sellerId = :sellerId and t.status = 'completed' and year(t.completedAt) = :year")
  long sumCompletedBySellerAndYear(Long sellerId, int year);

  @Query("select t.sellerId, count(t), sum(t.priceCzk) from SaleTransaction t where t.status='completed' and year(t.completedAt)=:year group by t.sellerId")
  List<Object[]> aggregateBySellerAndYear(int year);

  Optional<SaleTransaction> findByIdAndBuyerId(Long id, Long buyerId);

  Optional<SaleTransaction> findFirstByListingIdAndBuyerIdAndStatusOrderByIdDesc(Long listingId, Long buyerId, String status);

  List<SaleTransaction> findByBuyerIdOrSellerIdOrderByIdDesc(Long buyerId, Long sellerId);

  List<SaleTransaction> findBySellerIdOrderByIdDesc(Long sellerId);

  List<SaleTransaction> findByBuyerIdOrderByIdDesc(Long buyerId);

  /** Dokončené prodeje prodejce pro přehled obchodů a odznaky. */
  @Query("select count(t) from SaleTransaction t where t.sellerId = :sellerId and t.status = 'completed'")
  long countCompletedBySeller(Long sellerId);

  @Query("select coalesce(sum(t.priceCzk),0) from SaleTransaction t where t.sellerId = :sellerId and t.status = 'completed'")
  long sumCompletedBySeller(Long sellerId);

  /** Prodeje prodejce za daný rok s detaily pro daňový výkaz (DAC7). */
  @Query("select t from SaleTransaction t where t.sellerId = :sellerId and t.status = 'completed' "
      + "and year(t.completedAt) = :year order by t.completedAt asc")
  List<SaleTransaction> findCompletedBySellerAndYear(@Param("sellerId") Long sellerId, @Param("year") int year);

  /** Všechny dokončené obchody za rok s detaily pro daňový výkaz (DAC7). */
  @Query("select t from SaleTransaction t where t.status = 'completed' and year(t.completedAt) = :year "
      + "order by t.sellerId asc, t.completedAt asc")
  List<SaleTransaction> findAllCompletedByYear(int year);

  @Query("select t.status, count(t) from SaleTransaction t where t.sellerId = :sellerId group by t.status")
  List<Object[]> countByStatusForSeller(Long sellerId);

  @Query("select t.status, count(t) from SaleTransaction t where t.buyerId = :buyerId group by t.status")
  List<Object[]> countByStatusForBuyer(Long buyerId);
}
