package cz.bazar.controller;

import cz.bazar.dto.Dtos.*;
import cz.bazar.service.DisputeService;
import cz.bazar.service.ReviewService;
import cz.bazar.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Transakce, hodnocení, námitky proti hodnocení a právo spotřebitele na odstoupení. */
@RestController
public class TransactionController {
  private final TransactionService transactions;
  private final ReviewService reviews;
  private final DisputeService disputes;

  public TransactionController(TransactionService transactions, ReviewService reviews,
                               DisputeService disputes) {
    this.transactions = transactions; this.reviews = reviews; this.disputes = disputes;
  }

  @GetMapping("/transactions/mine")
  public Map<String, Object> mine(@RequestParam(defaultValue = "all") String role) {
    List<Map<String, Object>> data = transactions.mine(role);
    Map<String, Object> out = new java.util.LinkedHashMap<>();
    out.put("data", data);
    out.put("nextCursor", null);
    return out;
  }

  /** Přehled obchodů — souhrn prodejů a nákupů včetně získaného hodnocení. */
  @GetMapping("/transactions/overview")
  public Map<String, Object> overview() {
    return transactions.overview();
  }

  @PostMapping("/transactions")
  public ResponseEntity<Map<String, Object>> reserve(@Valid @RequestBody TransactionCreate r) {
    var t = transactions.reserve(r.listingId());
    return ResponseEntity.status(201).body(transactions.toMap(t));
  }

  @PostMapping("/transactions/{id}/complete")
  public Map<String, Object> complete(@PathVariable Long id) {
    return transactions.toMap(transactions.complete(id));
  }

  @PostMapping("/transactions/{id}/cancel")
  public Map<String, Object> cancel(@PathVariable Long id) {
    return transactions.toMap(transactions.cancel(id));
  }

  /** Právo spotřebitele na odstoupení od smlouvy (§ 1829 OZ). */
  @PostMapping("/transactions/{id}/withdraw")
  public Map<String, Object> withdraw(@PathVariable Long id,
      @RequestBody(required = false) @Valid WithdrawalRequest r) {
    return transactions.toMap(transactions.withdraw(id, r == null ? null : r.reason()));
  }

  @GetMapping("/transactions/{id}/withdrawal-rights")
  public Map<String, Object> withdrawalRights(@PathVariable Long id) {
    return transactions.withdrawalRights(id);
  }

  @PostMapping("/transactions/{id}/review")
  public ResponseEntity<Map<String, Object>> review(@PathVariable Long id, @Valid @RequestBody ReviewCreate r) {
    var rev = reviews.create(id, r);
    return ResponseEntity.status(201).body(reviews.toMap(rev));
  }

  @PostMapping("/reviews/{id}/response")
  public Map<String, Object> respond(@PathVariable Long id, @Valid @RequestBody ReviewResponseCreate r) {
    return reviews.toMap(reviews.respond(id, r));
  }

  /** Námitka prodejce proti hodnocení — podat smí jen hodnocený. */
  @PostMapping("/reviews/{id}/dispute")
  public ResponseEntity<Map<String, Object>> dispute(@PathVariable Long id,
      @Valid @RequestBody DisputeCreate r) {
    return ResponseEntity.status(201).body(disputes.toMap(disputes.create(id, r.reason())));
  }

  /** Námitky podané přihlášeným prodejcem. */
  @GetMapping("/reviews/disputes/mine")
  public Map<String, Object> myDisputes() {
    return Map.of("data", disputes.mine());
  }
}