package cz.bazar.repository;

import cz.bazar.domain.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
  List<Notification> findByUserIdOrderByIdDesc(Long userId, Pageable pageable);

  List<Notification> findByUserIdAndIdLessThanOrderByIdDesc(Long userId, Long cursor, Pageable pageable);

  long countByUserIdAndReadAtIsNull(Long userId);

  /** Nepřečtené upozornění daného typu pro daný inzerát a cenu — dedup před vložením. */
  @Query("select n from Notification n where n.userId = :userId and n.listingId = :listingId "
      + "and n.type = :type and n.newPriceCzk = :price and n.readAt is null")
  List<Notification> findUnreadByTarget(@Param("userId") Long userId,
      @Param("listingId") Long listingId,
      @Param("type") String type,
      @Param("price") Integer price);

  @Modifying
  @Query("update Notification n set n.readAt = :at where n.id = :id and n.userId = :userId and n.readAt is null")
  int markRead(@Param("id") Long id, @Param("userId") Long userId, @Param("at") OffsetDateTime at);

  @Modifying
  @Query("update Notification n set n.readAt = :at where n.userId = :userId and n.readAt is null")
  int markAllRead(@Param("userId") Long userId, @Param("at") OffsetDateTime at);
}