package cz.bazar.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
public class JwtFilter extends OncePerRequestFilter {
  private final JwtService jwt;

  public JwtFilter(JwtService jwt) { this.jwt = jwt; }

  @Override
  protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    String h = req.getHeader("Authorization");
    if (h != null && h.startsWith("Bearer ")) {
      String token = h.substring(7);
      try {
        var claims = jwt.parse(token);
        if (!"refresh".equals(claims.get("typ", String.class)) || req.getRequestURI().startsWith("/auth/refresh")) {
          // access token pro bezne pozadavky; refresh endpoint resi controller
        }
        if ("access".equals(claims.get("typ", String.class))) {
          String email = claims.get("email", String.class);
          Boolean admin = claims.get("admin", Boolean.class);
          List<SimpleGrantedAuthority> auth = new ArrayList<>();
          auth.add(new SimpleGrantedAuthority("ROLE_USER"));
          if (Boolean.TRUE.equals(admin)) auth.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
          var authentication = new UsernamePasswordAuthenticationToken(email, null, auth);
          authentication.setDetails(claims.getSubject());
          SecurityContextHolder.getContext().setAuthentication(authentication);
        }
      } catch (Exception ignored) {
        SecurityContextHolder.clearContext();
      }
    }
    chain.doFilter(req, res);
  }
}
