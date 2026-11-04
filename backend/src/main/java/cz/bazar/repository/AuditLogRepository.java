package cz.bazar.repository;

import cz.bazar.domain.AuditLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
  List<AuditLog> findByOrderByIdDesc(Pageable pageable);

  List<AuditLog> findByActionAndIdLessThanOrderByIdDesc(String action, Long cursor, Pageable pageable);

  List<AuditLog> findByIdLessThanOrderByIdDesc(Long cursor, Pageable pageable);

  @Query("select a from AuditLog a where a.action = :action order by a.id desc")
  List<AuditLog> findByActionOrderByIdDesc(@Param("action") String action, Pageable pageable);
}