package cz.bazar.service;

import cz.bazar.domain.User;
import cz.bazar.dto.Dtos.*;
import cz.bazar.exception.ApiException;
import cz.bazar.repository.UserRepository;
import cz.bazar.security.CustomUserDetailsService;
import cz.bazar.security.JwtService;
import cz.bazar.security.ApiRateLimiter;
import cz.bazar.security.LoginRateLimiter;
import cz.bazar.security.RateLimitProperties;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Service
public class AuthService {
  private final UserRepository users;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwt;
  private final LoginRateLimiter rateLimiter;
  private final ApiRateLimiter apiLimiter;
  private final RateLimitProperties limits;
  private final CustomUserDetailsService userDetails;

  public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwt,
                     LoginRateLimiter rateLimiter, ApiRateLimiter apiLimiter,
                     RateLimitProperties limits, CustomUserDetailsService userDetails) {
    this.users = users; this.passwordEncoder = passwordEncoder;
    this.jwt = jwt; this.rateLimiter = rateLimiter; this.apiLimiter = apiLimiter;
    this.limits = limits; this.userDetails = userDetails;
  }

  public AuthResponse register(RegisterRequest r, String ip) {
    apiLimiter.check("register:" + ip, limits.getRegister(), "Příliš mnoho registrací. Zkuste to později.");
    if (users.existsByEmailIgnoreCase(r.email())) {
      throw ApiException.conflict("EMAIL_EXISTS", "E-mail je již registrován.");
    }
    User u = new User();
    u.setEmail(r.email().toLowerCase());
    u.setPasswordHash(passwordEncoder.encode(r.password()));
    u.setDisplayName(r.displayName());
    u.setSellerType(r.sellerType());
    u.setCreatedAt(OffsetDateTime.now());
    if (userDetails.isAdminEmail(r.email())) u.setAdmin(true);
    users.save(u);
    boolean admin = u.isAdmin();
    return new AuthResponse(u.getId(), jwt.accessToken(u.getId(), u.getEmail(), admin),
        jwt.refreshToken(u.getId(), u.getEmail()));
  }

  public AuthResponse login(LoginRequest r, String ip) {
    rateLimiter.check(ip);
    User u = users.findByEmailIgnoreCase(r.email())
        .orElseThrow(() -> ApiException.unauthorized("INVALID_CREDENTIALS", "Neplatný e-mail nebo heslo."));
    // Přihlášení neprochází AuthenticationManagerem, proto stav účtu kontrolujeme zde.
    if (u.getDeletedAt() != null) {
      throw ApiException.unauthorized("ACCOUNT_DELETED", "Účet byl smazán.");
    }
    if (u.isBlocked()) {
      throw ApiException.forbidden("ACCOUNT_BLOCKED", "Účet byl zablokován administrátorem.");
    }
    if (!passwordEncoder.matches(r.password(), u.getPasswordHash())) {
      throw ApiException.unauthorized("INVALID_CREDENTIALS", "Neplatný e-mail nebo heslo.");
    }
    boolean admin = u.isAdmin() || userDetails.isAdminEmail(u.getEmail());
    return new AuthResponse(u.getId(), jwt.accessToken(u.getId(), u.getEmail(), admin),
        jwt.refreshToken(u.getId(), u.getEmail()));
  }

  public AuthResponse refresh(String refreshToken) {
    var claims = jwt.parse(refreshToken);
    if (!"refresh".equals(claims.get("typ", String.class))) {
      throw ApiException.unauthorized("INVALID_TOKEN", "Neplatný refresh token.");
    }
    Long id = Long.valueOf(claims.getSubject());
    User u = users.findById(id).orElseThrow(() -> ApiException.unauthorized("INVALID_TOKEN", "Neplatný refresh token."));
    if (u.getDeletedAt() != null || u.isBlocked()) {
      throw ApiException.forbidden("ACCOUNT_BLOCKED", "Účet byl smazán nebo zablokován.");
    }
    boolean admin = u.isAdmin() || userDetails.isAdminEmail(u.getEmail());
    return new AuthResponse(u.getId(), jwt.accessToken(u.getId(), u.getEmail(), admin),
        jwt.refreshToken(u.getId(), u.getEmail()));
  }
}
