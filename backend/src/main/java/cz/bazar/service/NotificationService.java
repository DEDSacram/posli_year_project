package cz.bazar.service;

import cz.bazar.domain.Favorite;
import cz.bazar.domain.Listing;
import cz.bazar.domain.Notification;
import cz.bazar.domain.User;
import cz.bazar.dto.Dtos.Page;
import cz.bazar.exception.ApiException;
import cz.bazar.repository.FavoriteRepository;
import cz.bazar.repository.NotificationRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Upozornění v profilu — dnes pouze zlevnění inzerátu, který uživatel
 * uložil do oblíbených (zadání: „Upozornění na zlevnění“).
 * Upozornění vzniká při reálné změně ceny, deduplikuje se na (uživatel, inzerát, cena).
 */
@Service
public class NotificationService {
  private static final int MAX_LIMIT = 50;

  private final NotificationRepository notifications;
  private final FavoriteRepository favorites;
  private final CurrentUserService current;

  public NotificationService(NotificationRepository notifications, FavoriteRepository favorites,
                             CurrentUserService current) {
    this.notifications = notifications; this.favorites = favorites; this.current = current;
  }

  @Transactional(readOnly = true)
  public Page<Map<String, Object>> mine(Long cursor, int limit) {
    User me = current.require();
    limit = Math.min(Math.max(limit, 1), MAX_LIMIT);
    List<Notification> rows = cursor == null
        ? notifications.findByUserIdOrderByIdDesc(me.getId(), PageRequest.of(0, limit + 1))
        : notifications.findByUserIdAndIdLessThanOrderByIdDesc(me.getId(), cursor, PageRequest.of(0, limit + 1));
    String next = null;
    List<Notification> page = rows;
    if (rows.size() > limit) {
      page = rows.subList(0, limit);
      next = String.valueOf(page.get(page.size() - 1).getId());
    }
    return new Page<>(page.stream().map(this::toMap).toList(), next);
  }

  @Transactional(readOnly = true)
  public Map<String, Object> unreadCount() {
    User me = current.require();
    return Map.of("unread", notifications.countByUserIdAndReadAtIsNull(me.getId()));
  }

  @Transactional
  public Map<String, Object> markRead(Long id) {
    User me = current.require();
    if (!notifications.existsById(id)) throw ApiException.notFound("NOTIFICATION_NOT_FOUND", "Upozornění nenalezeno.");
    // Klíč v dotazu zajišťuje, že uživatel označí jen vlastní upozornění.
    notifications.markRead(id, me.getId(), OffsetDateTime.now());
    return Map.of("id", id, "read", true);
  }

  @Transactional
  public Map<String, Object> markAllRead() {
    User me = current.require();
    int changed = notifications.markAllRead(me.getId(), OffsetDateTime.now());
    return Map.of("marked", changed);
  }

  /**
   * Vyvolá se po uložení inzerátu, jehož cena klesla. Rozešle upozornění všem,
   * kdo má inzerát v oblíbených (mimo samotného prodejce); duplikáty přeskočí.
   *
   * @return počet vytvořených upozornění
   */
  @Transactional
  public int notifyPriceDrop(Listing listing, int oldPrice, int newPrice) {
    if (newPrice >= oldPrice) return 0;
    OffsetDateTime now = OffsetDateTime.now();
    String title = "Zlevnění: " + listing.getTitle();
    int created = 0;
    List<Favorite> targets = favorites.findByListingId(listing.getId());
    for (Favorite f : targets) {
      if (listing.getSellerId().equals(f.getUserId())) continue; // prodejce se sám neupozorňuje
      if (f.getUserId() == null) continue;
      if (!notifications.findUnreadByTarget(f.getUserId(), listing.getId(),
          Notification.TYPE_PRICE_DROP, newPrice).isEmpty()) {
        continue; // stejné zlevnění už visí nepřečtené
      }
      Notification n = new Notification();
      n.setUserId(f.getUserId());
      n.setType(Notification.TYPE_PRICE_DROP);
      n.setListingId(listing.getId());
      n.setTitle(title);
      n.setOldPriceCzk(oldPrice);
      n.setNewPriceCzk(newPrice);
      n.setCreatedAt(now);
      notifications.save(n);
      created++;
    }
    return created;
  }

  public Map<String, Object> toMap(Notification n) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", n.getId());
    m.put("type", n.getType());
    m.put("listingId", n.getListingId());
    m.put("title", n.getTitle());
    m.put("oldPriceCzk", n.getOldPriceCzk());
    m.put("newPriceCzk", n.getNewPriceCzk());
    m.put("read", n.isRead());
    m.put("createdAt", n.getCreatedAt());
    return m;
  }
}