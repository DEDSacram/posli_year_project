package cz.bazar.service;

import cz.bazar.domain.AuditLog;
import cz.bazar.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class AuditService {
  private final AuditLogRepository repo;

  public void log(Long actorId, String action, String targetType, Long targetId) {
    AuditLog a = new AuditLog();
    a.setActorId(actorId);
    a.setAction(action);
    a.setTargetType(targetType);
    a.setTargetId(targetId);
    a.setCreatedAt(OffsetDateTime.now());
    repo.save(a);
  }
}
