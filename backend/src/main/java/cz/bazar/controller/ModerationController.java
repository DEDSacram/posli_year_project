package cz.bazar.controller;

import cz.bazar.dto.Dtos.*;
import cz.bazar.service.ModerationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
public class ModerationController {
  private final ModerationService moderation;
  public ModerationController(ModerationService moderation) { this.moderation = moderation; }

  @PostMapping("/moderation/reports")
  public ResponseEntity<Map<String, Object>> report(@Valid @RequestBody ReportCreate r) {
    var rep = moderation.report(r);
    return ResponseEntity.status(201).body(moderation.toMap(rep));
  }
}
