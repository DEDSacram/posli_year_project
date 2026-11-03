package cz.bazar.repository;

import cz.bazar.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
  Optional<User> findByEmailIgnoreCase(String email);
  boolean existsByEmailIgnoreCase(String email);

  /** Výpis uživatelů pro administraci — filtrování podle e-mailu či jména. */
  @Query("select u from User u where (:q = '' or lower(u.email) like lower(concat('%', :q, '%')) "
      + "or lower(u.displayName) like lower(concat('%', :q, '%'))) and u.id < :cursor order by u.id desc")
  List<User> search(@Param("q") String q, @Param("cursor") Long cursor, Pageable pageable);

  @Query("select u from User u where (:q = '' or lower(u.email) like lower(concat('%', :q, '%')) "
      + "or lower(u.displayName) like lower(concat('%', :q, '%'))) order by u.id desc")
  List<User> searchFirst(@Param("q") String q, Pageable pageable);
}
