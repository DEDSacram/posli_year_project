package cz.bazar.service;

import cz.bazar.domain.Report;
import cz.bazar.domain.User;
import cz.bazar.dto.Dtos.*;
import cz.bazar.exception.ApiException;
import cz.bazar.repository.*;
import cz.bazar.security.ApiRateLimiter;
import cz.bazar.security.RateLimitProperties;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;

@Service
public class ModerationService {
  private final ReportRepository reports;
  private final ListingRepository listings;
  private final ReviewRepository reviews;
  private final CurrentUserService current;
  private final AuditService audit;
  private final ApiRateLimiter rateLimiter;
  private final RateLimitProperties limits;

  public ModerationService(ReportRepository reports, ListingRepository listings, ReviewRepository reviews,
                           CurrentUserService current, AuditService audit,
                           ApiRateLimiter rateLimiter, RateLimitProperties limits) {
    this.reports = reports; this.listings = listings; this.reviews = reviews;
    this.current = current; this.audit = audit;
    this.rateLimiter = rateLimiter; this.limits = limits;
  }

  @Transactional
  public Report report(ReportCreate r) {
    User me = current.require();
    rateLimiter.check("report:" + me.getId(), limits.getReport(), "Příliš mnoho hlášení. Zkuste to později.");
    Report rep = new Report();
    rep.setTargetType(r.targetType());
    rep.setTargetId(r.targetId());
    rep.setReporterId(me.getId());
    rep.setReason(r.reason());
    // DSA trusted flagger určuje server, ne klient: admin nebo ověřená historie
    // (alespoň TRUSTED_MIN_REMOVALS dřívějších hlášení vedoucích k takedownu).
    rep.setTrustedFlagger(isTrustedFlagger(me));
    rep.setStatus("open");
    rep.setCreatedAt(OffsetDateTime.now());
    return reports.save(rep);
  }

  /** Hranice důvěryhodnosti nahlašovatele (úspěšné takedowny). */
  public static final long TRUSTED_MIN_REMOVALS = 3L;

  public boolean isTrustedFlagger(User reporter) {
    if (reporter.isAdmin()) return true;
    return reports.countByReporterIdAndStatus(reporter.getId(), "removed") >= TRUSTED_MIN_REMOVALS;
  }

  public List<Map<String, Object>> queue(int limit) {
    limit = Math.min(Math.max(limit, 1), 50);
    return reports.findByStatusOrderByTrustedFlaggerDescIdAsc("open", PageRequest.of(0, limit)).stream()
        .map(this::toMap).toList();
  }

  @Transactional
  public Report decide(Long id, String decision) {
    User admin = current.require();
    Report rep = reports.findById(id)
        .orElseThrow(() -> ApiException.notFound("REPORT_NOT_FOUND", "Hlášení nenalezeno."));
    if (!"approve".equals(decision) && !"remove".equals(decision)) {
      throw ApiException.badRequest("INVALID_DECISION", "Rozhodnutí musí být approve nebo remove.");
    }
    if ("remove".equals(decision)) {
      applyTakedown(rep);
      rep.setStatus("removed");
    } else {
      rep.setStatus("approved");
    }
    audit.log(admin.getId(), "MODERATION_" + decision.toUpperCase(), rep.getTargetType(), rep.getTargetId());
    return reports.save(rep);
  }

  /** Bezodkladny takedown: server primo cte obsah a rozhodne. */
  private void applyTakedown(Report rep) {
    switch (rep.getTargetType()) {
      case "listing" -> listings.findById(rep.getTargetId()).ifPresent(l -> {
        l.setStatus("removed");
        listings.save(l);
      });
      case "review" -> reviews.findById(rep.getTargetId()).ifPresent(r -> {
        r.setModerationStatus("removed");
        reviews.save(r);
      });
      case "chat" -> { /* chat takedown: audit, obsah zustava pro dukaz; resi admin */ }
      default -> throw ApiException.badRequest("INVALID_TARGET", "Neplatný typ cíle.");
    }
  }

  public Map<String, Object> toMap(Report r) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", r.getId());
    m.put("targetType", r.getTargetType());
    m.put("targetId", r.getTargetId());
    m.put("reporterId", r.getReporterId());
    m.put("reason", r.getReason());
    m.put("trustedFlagger", r.isTrustedFlagger());
    m.put("status", r.getStatus());
    m.put("createdAt", r.getCreatedAt());
    return m;
  }
}
