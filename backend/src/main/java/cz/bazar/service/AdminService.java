package cz.bazar.service;

import cz.bazar.domain.AuditLog;
import cz.bazar.domain.User;
import cz.bazar.exception.ApiException;
import cz.bazar.repository.AuditLogRepository;
import cz.bazar.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Správa systému administrátorem: čtení auditní stopy, výpis uživatelů
 * a blokace účtů. Každý zásah je zapsán do audit logu.
 */
@Service
public class AdminService {
  private static final int MAX_LIMIT = 50;

  private final AuditLogRepository auditLogRepo;
  private final UserRepository users;
  private final CurrentUserService current;
  private final AuditService audit;

  public AdminService(AuditLogRepository auditLogRepo, UserRepository users,
                      CurrentUserService current, AuditService audit) {
    this.auditLogRepo = auditLogRepo; this.users = users; this.current = current; this.audit = audit;
  }

  /**
   * Audit log — stránkovaný, volitelně filtrovaný podle akce.
   * Čtení je samo o sobě citlivé, proto se také zaloguje — transakce proto musí být
   * zapisovací (na read-only transakci PostgreSQL INSERT odmítne).
   */
  @Transactional
  public Map<String, Object> auditTrail(String action, Long cursor, int limit) {
    User admin = current.require();
    int n = Math.min(Math.max(limit, 1), MAX_LIMIT);
    String filter = action == null || action.isBlank() ? null : action.strip();
    List<AuditLog> rows;
    if (filter != null) {
      rows = cursor == null
          ? auditLogRepo.findByActionOrderByIdDesc(filter, PageRequest.of(0, n + 1))
          : auditLogRepo.findByActionAndIdLessThanOrderByIdDesc(filter, cursor, PageRequest.of(0, n + 1));
    } else {
      rows = cursor == null
          ? auditLogRepo.findByOrderByIdDesc(PageRequest.of(0, n + 1))
          : auditLogRepo.findByIdLessThanOrderByIdDesc(cursor, PageRequest.of(0, n + 1));
    }
    String next = null;
    List<AuditLog> page = rows;
    if (rows.size() > n) {
      page = rows.subList(0, n);
      next = String.valueOf(page.get(page.size() - 1).getId());
    }
    audit.log(admin.getId(), "AUDIT_TRAIL_READ", "audit", null);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("data", page.stream().map(this::toMap).toList());
    out.put("nextCursor", next);
    return out;
  }

  /** Výpis uživatelů pro administraci, volitelně s fulltextovým filtrem. */
  @Transactional(readOnly = true)
  public Map<String, Object> users(String q, Long cursor, int limit) {
    User admin = current.require();
    int n = Math.min(Math.max(limit, 1), MAX_LIMIT);
    String query = q == null ? "" : q.strip();
    List<User> rows = cursor == null
        ? users.searchFirst(query, PageRequest.of(0, n + 1))
        : users.search(query, cursor, PageRequest.of(0, n + 1));
    String next = null;
    List<User> page = rows;
    if (rows.size() > n) {
      page = rows.subList(0, n);
      next = String.valueOf(page.get(page.size() - 1).getId());
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("data", page.stream().map(this::toUserMap).toList());
    out.put("nextCursor", next);
    out.put("readBy", admin.getId());
    return out;
  }

  /** Blokace účtu — odlišná od GDPR soft-delete, data se nemění. */
  @Transactional
  public Map<String, Object> block(Long userId, String reason) {
    User admin = current.require();
    User target = users.findById(userId)
        .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "Uživatel nenalezen."));
    if (target.isAdmin())
      throw ApiException.forbidden("CANNOT_BLOCK_ADMIN", "Administrátora nelze zablokovat.");
    if (target.getId().equals(admin.getId()))
      throw ApiException.forbidden("CANNOT_BLOCK_SELF", "Nelze zablokovat vlastní účet.");
    target.setBlockedAt(OffsetDateTime.now());
    target.setBlockReason(reason);
    users.save(target);
    audit.log(admin.getId(), "USER_BLOCKED", "user", userId);
    return toUserMap(target);
  }

  @Transactional
  public Map<String, Object> unblock(Long userId) {
    User admin = current.require();
    User target = users.findById(userId)
        .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "Uživatel nenalezen."));
    target.setBlockedAt(null);
    target.setBlockReason(null);
    users.save(target);
    audit.log(admin.getId(), "USER_UNBLOCKED", "user", userId);
    return toUserMap(target);
  }

  private Map<String, Object> toMap(AuditLog a) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", a.getId());
    m.put("actorId", a.getActorId());
    m.put("action", a.getAction());
    m.put("targetType", a.getTargetType());
    m.put("targetId", a.getTargetId());
    m.put("createdAt", a.getCreatedAt());
    return m;
  }

  private Map<String, Object> toUserMap(User u) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", u.getId());
    m.put("email", u.getEmail());
    m.put("displayName", u.getDisplayName());
    m.put("sellerType", u.getSellerType());
    m.put("isAdmin", u.isAdmin());
    m.put("blocked", u.isBlocked());
    m.put("blockReason", u.getBlockReason());
    m.put("deleted", u.getDeletedAt() != null);
    m.put("createdAt", u.getCreatedAt());
    return m;
  }
}