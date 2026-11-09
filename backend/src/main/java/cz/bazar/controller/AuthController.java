package cz.bazar.controller;

import cz.bazar.dto.Dtos.*;
import cz.bazar.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {
  private final AuthService auth;
  public AuthController(AuthService auth) { this.auth = auth; }

  @PostMapping("/register")
  public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest r, HttpServletRequest req) {
    var res = auth.register(r, req.getRemoteAddr());
    return ResponseEntity.status(201).body(res);
  }

  @PostMapping("/login")
  public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest r, HttpServletRequest req) {
    String ip = req.getRemoteAddr();
    return ResponseEntity.ok(auth.login(r, ip));
  }

  @PostMapping("/refresh")
  public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshRequest r) {
    return ResponseEntity.ok(auth.refresh(r.refreshToken()));
  }
}
