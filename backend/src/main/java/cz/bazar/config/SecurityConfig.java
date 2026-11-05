package cz.bazar.config;

import cz.bazar.security.CustomUserDetailsService;
import cz.bazar.security.JwtFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ContentSecurityPolicyHeaderWriter;
import org.springframework.web.cors.*;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
  private final JwtFilter jwtFilter;
  private final String corsOrigins;

  public SecurityConfig(JwtFilter jwtFilter, @Value("${app.cors-origins:http://localhost:3000}") String corsOrigins) {
    this.jwtFilter = jwtFilter;
    this.corsOrigins = corsOrigins;
  }

  @Bean
  public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }

  @Bean
  public SecurityFilterChain chain(HttpSecurity http) throws Exception {
    http
      .csrf(c -> c.disable())
      .cors(c -> c.configurationSource(corsSource()))
      .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
      // Bez přihlášení vracíme 401 (ne Spring default 403), aby klient poznal,
      // že chybí token, a ne že je token neplatný.
      .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
      .headers(h -> h
        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'; img-src 'self' data:; script-src 'self'"))
        .referrerPolicy(r -> r.policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)))
      .authorizeHttpRequests(a -> a
        .requestMatchers("/health", "/actuator/health", "/actuator/info", "/auth/**", "/error").permitAll()
        .requestMatchers(HttpMethod.GET, "/listings/search", "/listings/feed", "/listings/*", "/listings/*/rights-info",
            "/listings/*/images/*", "/users/*", "/users/*/badges", "/users/*/rating", "/users/*/reviews").permitAll()
        .requestMatchers(HttpMethod.GET, "/geo/**").permitAll()
        .requestMatchers(HttpMethod.GET, "/chat/interest").permitAll()
        .requestMatchers("/admin/**").hasRole("ADMIN")
        .anyRequest().authenticated())
      .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }

  @Bean
  public CorsConfigurationSource corsSource() {
    CorsConfiguration cfg = new CorsConfiguration();
    cfg.setAllowedOrigins(Arrays.stream(corsOrigins.split(",")).map(String::trim).toList());
    cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    cfg.setAllowedHeaders(List.of("*"));
    cfg.setAllowCredentials(true);
    UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
    src.registerCorsConfiguration("/**", cfg);
    return src;
  }
}
