package cz.bazar.repository;

import cz.bazar.domain.Consent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ConsentRepository extends JpaRepository<Consent, Long> {
  List<Consent> findByUserIdOrderByIdDesc(Long userId);
}
