package cz.bazar.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtService {
  private final SecretKey key;
  private final long accessMs;
  private final long refreshMs;

  public JwtService(@Value("${app.jwt.secret}") String secret,
                    @Value("${app.jwt.access-minutes:15}") long accessMinutes,
                    @Value("${app.jwt.refresh-days:30}") long refreshDays) {
    byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
    if (bytes.length < 32) {
      byte[] padded = new byte[32];
      System.arraycopy(bytes, 0, padded, 0, bytes.length);
      bytes = padded;
    }
    this.key = Keys.hmacShaKeyFor(bytes);
    this.accessMs = accessMinutes * 60_000L;
    this.refreshMs = refreshDays * 24L * 3600_000L;
  }

  public String accessToken(Long userId, String email, boolean admin) {
    return build(userId, email, admin, "access", accessMs);
  }

  public String refreshToken(Long userId, String email) {
    return build(userId, email, false, "refresh", refreshMs);
  }

  private String build(Long userId, String email, boolean admin, String type, long ttl) {
    long now = System.currentTimeMillis();
    return Jwts.builder()
        .subject(String.valueOf(userId))
        .claim("email", email)
        .claim("admin", admin)
        .claim("typ", type)
        .issuedAt(new Date(now))
        .expiration(new Date(now + ttl))
        .signWith(key)
        .compact();
  }

  public io.jsonwebtoken.Claims parse(String token) {
    return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
  }
}
