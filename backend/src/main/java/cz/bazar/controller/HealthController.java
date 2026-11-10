package cz.bazar.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.util.Map;

@RestController
public class HealthController {
  private final DataSource dataSource;
  public HealthController(DataSource dataSource) { this.dataSource = dataSource; }

  @GetMapping("/health")
  public ResponseEntity<Map<String, String>> health() {
    try (var c = dataSource.getConnection()) {
      boolean ok = c.isValid(2);
      if (!ok) return ResponseEntity.status(503).body(Map.of("status", "DOWN", "db", "DOWN"));
      return ResponseEntity.ok(Map.of("status", "UP", "db", "UP"));
    } catch (Exception e) {
      return ResponseEntity.status(503).body(Map.of("status", "DOWN", "db", "DOWN"));
    }
  }
}
