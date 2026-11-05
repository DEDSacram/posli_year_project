package cz.bazar.security;

import cz.bazar.domain.User;
import cz.bazar.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.authentication.DisabledException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class CustomUserDetailsService implements UserDetailsService {
  private final UserRepository users;
  private final String adminEmail;

  public CustomUserDetailsService(UserRepository users, @Value("${app.admin-email:admin@bazar.cz}") String adminEmail) {
    this.users = users;
    this.adminEmail = adminEmail;
  }

  @Override
  public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
    User u = users.findByEmailIgnoreCase(email)
        .orElseThrow(() -> new UsernameNotFoundException("Uživatel nenalezen."));
    // Blokovaný účet se nepřihlásí; je to jiný stav než GDPR soft-delete.
    if (u.isBlocked()) throw new DisabledException("Účet byl zablokován administrátorem.");
    List<SimpleGrantedAuthority> auth = new ArrayList<>();
    auth.add(new SimpleGrantedAuthority("ROLE_USER"));
    if (u.isAdmin() || u.getEmail().equalsIgnoreCase(adminEmail)) {
      auth.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }
    return new org.springframework.security.core.userdetails.User(u.getEmail(), u.getPasswordHash(), auth);
  }

  public boolean isAdminEmail(String email) {
    return email != null && email.equalsIgnoreCase(adminEmail);
  }
}
