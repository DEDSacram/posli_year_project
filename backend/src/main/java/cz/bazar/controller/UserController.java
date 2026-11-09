package cz.bazar.controller;

import cz.bazar.dto.Dtos.*;
import cz.bazar.service.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
public class UserController {
  private final UserService users;
  private final SellerService sellers;
  private final ReviewService reviews;
  private final GdprService gdpr;

  public UserController(UserService users, SellerService sellers, ReviewService reviews, GdprService gdpr) {
    this.users = users; this.sellers = sellers; this.reviews = reviews; this.gdpr = gdpr;
  }

  @GetMapping("/users/{id}")
  public Map<String, Object> profile(@PathVariable Long id) { return users.publicProfile(id); }

  @GetMapping("/users/{id}/badges")
  public Map<String, Object> badges(@PathVariable Long id) { return users.badges(id); }

  @GetMapping("/users/{id}/rating")
  public Map<String, Object> rating(@PathVariable Long id) { return users.rating(id); }

  @GetMapping("/users/{id}/reviews")
  public Page<Map<String, Object>> userReviews(@PathVariable Long id,
      @RequestParam(required = false) Long cursor, @RequestParam(defaultValue = "20") int limit) {
    return reviews.visibleForUser(id, cursor, limit);
  }

  @PatchMapping("/users/me/seller-info")
  public ResponseEntity<Map<String, String>> sellerInfo(@Valid @RequestBody SellerInfoRequest r) {
    sellers.saveSellerInfo(r);
    return ResponseEntity.ok(Map.of("status", "ok"));
  }

  @PostMapping("/users/me/id-document")
  public ResponseEntity<Map<String, String>> idDocument(@RequestParam("file") MultipartFile file) {
    sellers.uploadIdDocument(file);
    return ResponseEntity.ok(Map.of("status", "ok"));
  }

  @GetMapping("/seller/me/dac7-status")
  public Dac7Status dac7Status() { return sellers.dac7Status(); }

  /** Stav administrátorového ověření dokladu obchodníka (jen prodejce, bez daňových údajů). */
  @GetMapping("/seller/me/verification")
  public Map<String, Object> myVerification() { return sellers.myVerification(); }

  @GetMapping("/seller/me/location")
  public Map<String, Object> myLocation() { return sellers.myLocation(); }

  @PatchMapping("/seller/me/location")
  public ResponseEntity<Map<String, Object>> saveLocation(@Valid @RequestBody LocationUpdate r) {
    return ResponseEntity.ok(sellers.saveLocation(r));
  }

  @GetMapping("/users/me/export")
  public Map<String, Object> exportMe() { return gdpr.exportMe(); }

  @DeleteMapping("/users/me")
  public ResponseEntity<Map<String, String>> deleteMe() {
    gdpr.softDeleteMe();
    return ResponseEntity.ok(Map.of("status", "deleted"));
  }

  @PostMapping("/consents")
  public ResponseEntity<Map<String, Object>> consent(@Valid @RequestBody ConsentCreate r) {
    var c = gdpr.consent(r);
    return ResponseEntity.status(201).body(Map.of("id", c.getId(), "purpose", c.getPurpose(), "granted", c.isGranted()));
  }
}
