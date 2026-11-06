package cz.bazar.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import cz.bazar.domain.User;
import cz.bazar.exception.ApiException;
import cz.bazar.repository.UserRepository;

@Service
public class CurrentUserService {
  private final UserRepository users;
  public CurrentUserService(UserRepository users) { this.users = users; }

  public User require() {
    Authentication a = SecurityContextHolder.getContext().getAuthentication();
    if (a == null || a.getName() == null || "anonymousUser".equals(a.getName())) {
      throw ApiException.unauthorized("UNAUTHORIZED", "Nejste přihlášeni.");
    }
    return users.findByEmailIgnoreCase(a.getName())
        .orElseThrow(() -> ApiException.unauthorized("UNAUTHORIZED", "Uživatel neexistuje."));
  }

  /** ID přihlášeného uživatele, nebo null pro hosta — bez výjimky (pro veřejné výpisy). */
  public Long currentUserId() {
    Authentication a = SecurityContextHolder.getContext().getAuthentication();
    if (a == null || a.getName() == null || "anonymousUser".equals(a.getName())) return null;
    return users.findByEmailIgnoreCase(a.getName()).map(User::getId).orElse(null);
  }
}
