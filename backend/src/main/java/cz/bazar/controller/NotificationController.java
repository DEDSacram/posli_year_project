package cz.bazar.controller;

import cz.bazar.dto.Dtos.Page;
import cz.bazar.service.NotificationService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Upozornění v profilu — zlevnění inzerátů uložených do oblíbených. */
@RestController
@RequestMapping("/notifications")
public class NotificationController {
  private final NotificationService notifications;

  public NotificationController(NotificationService notifications) {
    this.notifications = notifications;
  }

  @GetMapping
  public Page<Map<String, Object>> list(@RequestParam(required = false) Long cursor,
                                        @RequestParam(defaultValue = "20") int limit) {
    return notifications.mine(cursor, limit);
  }

  @GetMapping("/unread-count")
  public Map<String, Object> unreadCount() {
    return notifications.unreadCount();
  }

  @PostMapping("/{id}/read")
  public Map<String, Object> markRead(@PathVariable Long id) {
    return notifications.markRead(id);
  }

  @PostMapping("/read-all")
  public Map<String, Object> markAllRead() {
    return notifications.markAllRead();
  }
}