package cz.bazar.controller;

import cz.bazar.dto.Dtos.BlockRequest;
import cz.bazar.dto.Dtos.DisputeResolve;
import cz.bazar.dto.Dtos.VerificationDecision;
import cz.bazar.service.AdminService;
import cz.bazar.service.AuditService;
import cz.bazar.service.CurrentUserService;
import cz.bazar.service.Dac7Service;
import cz.bazar.service.DisputeService;
import cz.bazar.service.ModerationService;
import cz.bazar.service.SellerService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/** Administrace: moderace, námitky na hodnocení, ověřování obchodníků, audit a daňové výkazy. */
@RestController
@RequestMapping("/admin")
public class AdminController {
  private final ModerationService moderation;
  private final Dac7Service dac7;
  private final DisputeService disputes;
  private final SellerService sellers;
  private final AdminService admin;
  private final AuditService audit;
  private final CurrentUserService current;

  public AdminController(ModerationService moderation, Dac7Service dac7, DisputeService disputes,
                         SellerService sellers, AdminService admin, AuditService audit,
                         CurrentUserService current) {
    this.moderation = moderation; this.dac7 = dac7; this.disputes = disputes;
    this.sellers = sellers; this.admin = admin; this.audit = audit; this.current = current;
  }

  // ---------- moderace ----------

  @GetMapping("/moderation/queue")
  public Map<String, Object> queue(@RequestParam(defaultValue = "20") int limit) {
    var adminUser = current.require();
    audit.log(adminUser.getId(), "MODERATION_QUEUE_READ", "moderation", null);
    return Map.of("data", moderation.queue(limit));
  }

  @PostMapping("/moderation/{id}/approve")
  public Map<String, Object> approve(@PathVariable Long id) {
    return moderation.toMap(moderation.decide(id, "approve"));
  }

  @PostMapping("/moderation/{id}/remove")
  public Map<String, Object> remove(@PathVariable Long id) {
    return moderation.toMap(moderation.decide(id, "remove"));
  }

  // ---------- námitky proti hodnocení ----------

  @GetMapping("/disputes")
  public Map<String, Object> disputeQueue(@RequestParam(defaultValue = "20") int limit) {
    return Map.of("data", disputes.queue(limit), "openCount", disputes.openCount());
  }

  @PostMapping("/disputes/{id}/resolve")
  public Map<String, Object> resolveDispute(@PathVariable Long id, @RequestBody DisputeResolve r) {
    boolean uphold = "uphold".equals(r.decision());
    return disputes.toMap(disputes.resolve(id, uphold, r.note()));
  }

  // ---------- ověřování obchodníků (DSA) ----------

  @GetMapping("/verifications")
  public Map<String, Object> verifications() {
    return Map.of("data", sellers.pendingVerifications());
  }

  @PostMapping("/verifications/{userId}/approve")
  public Map<String, Object> approveVerification(@PathVariable Long userId,
      @RequestBody(required = false) VerificationDecision r) {
    return sellers.decideVerification(userId, true, r == null ? null : r.note());
  }

  @PostMapping("/verifications/{userId}/reject")
  public Map<String, Object> rejectVerification(@PathVariable Long userId,
      @RequestBody(required = false) VerificationDecision r) {
    return sellers.decideVerification(userId, false, r == null ? null : r.note());
  }

  // ---------- dozor: audit a uživatelé ----------

  @GetMapping("/audit")
  public Map<String, Object> auditTrail(@RequestParam(required = false) String action,
      @RequestParam(required = false) Long cursor, @RequestParam(defaultValue = "50") int limit) {
    return admin.auditTrail(action, cursor, limit);
  }

  @GetMapping("/users")
  public Map<String, Object> users(@RequestParam(defaultValue = "") String q,
      @RequestParam(required = false) Long cursor, @RequestParam(defaultValue = "50") int limit) {
    return admin.users(q, cursor, limit);
  }

  @PostMapping("/users/{id}/block")
  public Map<String, Object> block(@PathVariable Long id, @RequestBody(required = false) BlockRequest r) {
    return admin.block(id, r == null ? null : r.reason());
  }

  @PostMapping("/users/{id}/unblock")
  public Map<String, Object> unblock(@PathVariable Long id) {
    return admin.unblock(id);
  }

  // ---------- daňové výkazy (DAC7) ----------

  /** Souhrn za rok v JSON — pro dashboard administrace. */
  @GetMapping("/dac7/summary")
  public Map<String, Object> dac7Summary(@RequestParam int year) {
    return dac7.summary(year);
  }

  /**
   * Výkaz ve formátu CSV nebo XML. `detail=true` vrací detail prodejů jednotlivě
   * (pro kontrolu před odevzdáním), jinou agregaci po prodejcích.
   */
  @GetMapping("/dac7/export")
  public void dac7Export(@RequestParam int year,
      @RequestParam(defaultValue = "csv") String format,
      @RequestParam(defaultValue = "false") boolean detail,
      HttpServletResponse res) throws IOException {
    writeDac7Export(year, format, detail, res);
  }

  @PostMapping("/dac7/export")
  public void dac7ExportPost(@RequestParam int year,
      @RequestParam(defaultValue = "csv") String format,
      @RequestParam(defaultValue = "false") boolean detail,
      HttpServletResponse res) throws IOException {
    writeDac7Export(year, format, detail, res);
  }

  private void writeDac7Export(int year, String format, boolean detail, HttpServletResponse res) throws IOException {
    String f = format.toLowerCase();
    if (!List.of("csv", "xml").contains(f)) {
      res.sendError(400, "Neplatný formát.");
      return;
    }
    String suffix = detail ? "-sales" : "";
    if ("xml".equals(f)) {
      res.setContentType("application/xml");
      res.setHeader("Content-Disposition", "attachment; filename=\"dac7-" + year + suffix + ".xml\"");
      res.getWriter().write(detail ? dac7.exportSalesXml(year) : dac7.exportXml(year));
    } else {
      res.setContentType("text/csv");
      res.setHeader("Content-Disposition", "attachment; filename=\"dac7-" + year + suffix + ".csv\"");
      res.getWriter().write(detail ? dac7.exportSalesCsv(year) : dac7.exportCsv(year));
    }
  }
}