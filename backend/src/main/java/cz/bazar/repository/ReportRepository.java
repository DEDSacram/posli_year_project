package cz.bazar.repository;

import cz.bazar.domain.Report;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import org.springframework.data.domain.Pageable;

public interface ReportRepository extends JpaRepository<Report, Long> {
  List<Report> findByStatusOrderByTrustedFlaggerDescIdAsc(String status, Pageable pageable);

  long countByReporterIdAndStatus(Long reporterId, String status);

  long countByStatus(String status);
}
