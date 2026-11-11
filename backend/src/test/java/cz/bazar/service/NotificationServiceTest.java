package cz.bazar.service;

import cz.bazar.domain.*;
import cz.bazar.exception.ApiException;
import cz.bazar.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Upozornění na zlevnění: vzniká jen při poklesu ceny, jen pro uživatele
 * s inzerátem v oblíbených, ne pro prodejce a bez duplicit.
 */
class NotificationServiceTest {

  private NotificationRepository notifications;
  private FavoriteRepository favorites;
  private CurrentUserService current;
  private NotificationService service;

  @BeforeEach
  void setUp() {
    notifications = mock(NotificationRepository.class);
    favorites = mock(FavoriteRepository.class);
    current = mock(CurrentUserService.class);
    service = new NotificationService(notifications, favorites, current);
    when(current.require()).thenReturn(user(1L));
  }

  private static User user(Long id) {
    User u = new User();
    u.setId(id);
    u.setEmail("u" + id + "@test.cz");
    u.setDisplayName("U" + id);
    u.setSellerType("private");
    return u;
  }

  private static Listing listing(Long id, Long sellerId, String title) {
    Listing l = new Listing();
    l.setId(id);
    l.setSellerId(sellerId);
    l.setTitle(title);
    l.setPriceCzk(1000);
    l.setStatus("active");
    return l;
  }

  private static Favorite fav(Long userId, Long listingId) {
    Favorite f = new Favorite();
    f.setUserId(userId);
    f.setListingId(listingId);
    return f;
  }

  @Test
  void priceDropNotifiesEveryFavoriteExceptSeller() {
    when(favorites.findByListingId(10L)).thenReturn(List.of(fav(1L, 10L), fav(2L, 10L), fav(99L, 10L)));
    when(notifications.findUnreadByTarget(anyLong(), anyLong(), anyString(), anyInt())).thenReturn(List.of());

    int created = service.notifyPriceDrop(listing(10L, 99L, "Kůň"), 5000, 3000);

    // 1 a 2 dostanou upozornění, 99 je prodejce
    assertThat(created).isEqualTo(2);
    ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
    verify(notifications, times(2)).save(captor.capture());
    assertThat(captor.getAllValues()).extracting(Notification::getUserId)
        .containsExactlyInAnyOrder(1L, 2L);
    assertThat(captor.getAllValues()).allSatisfy(n -> {
      assertThat(n.getType()).isEqualTo(Notification.TYPE_PRICE_DROP);
      assertThat(n.getListingId()).isEqualTo(10L);
      assertThat(n.getOldPriceCzk()).isEqualTo(5000);
      assertThat(n.getNewPriceCzk()).isEqualTo(3000);
      assertThat(n.getReadAt()).isNull();
      assertThat(n.getTitle()).contains("Kůň");
    });
  }

  @Test
  void priceIncreaseOrEqualCreatesNothing() {
    when(favorites.findByListingId(10L)).thenReturn(List.of(fav(1L, 10L)));

    assertThat(service.notifyPriceDrop(listing(10L, 2L, "X"), 3000, 5000)).isZero();
    assertThat(service.notifyPriceDrop(listing(10L, 2L, "X"), 3000, 3000)).isZero();

    verify(notifications, never()).save(any());
  }

  @Test
  void duplicateUnreadAlertForSamePriceIsSkipped() {
    when(favorites.findByListingId(10L)).thenReturn(List.of(fav(1L, 10L)));
    when(notifications.findUnreadByTarget(1L, 10L, Notification.TYPE_PRICE_DROP, 2000))
        .thenReturn(List.of(new Notification())); // už existuje

    assertThat(service.notifyPriceDrop(listing(10L, 2L, "X"), 4000, 2000)).isZero();
    verify(notifications, never()).save(any());
  }

  @Test
  void noFavoritesMeansNoNotifications() {
    when(favorites.findByListingId(10L)).thenReturn(List.of());
    assertThat(service.notifyPriceDrop(listing(10L, 2L, "X"), 4000, 2000)).isZero();
    verify(notifications, never()).save(any());
  }

  @Test
  void unreadCountIsScopedToCurrentUser() {
    when(notifications.countByUserIdAndReadAtIsNull(1L)).thenReturn(3L);
    assertThat(service.unreadCount()).containsEntry("unread", 3L);
  }

  @Test
  void markReadOnMissingNotificationReturns404() {
    when(notifications.existsById(404L)).thenReturn(false);
    assertThatThrownBy(() -> service.markRead(404L))
        .isInstanceOf(ApiException.class)
        .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("NOTIFICATION_NOT_FOUND"));
  }

  @Test
  void markAllReadReturnsChangedCount() {
    when(notifications.markAllRead(eq(1L), any(OffsetDateTime.class))).thenReturn(5);
    assertThat(service.markAllRead()).containsEntry("marked", 5);
  }

  @Test
  void listingPagingReturnsCursorWhenMoreRowsExist() {
    Notification a = new Notification();
    a.setId(10L); a.setUserId(1L); a.setType(Notification.TYPE_PRICE_DROP);
    a.setListingId(7L); a.setTitle("Zlevnění"); a.setCreatedAt(OffsetDateTime.now());
    Notification b = new Notification();
    b.setId(9L); b.setUserId(1L); b.setType(Notification.TYPE_PRICE_DROP);
    b.setListingId(6L); b.setTitle("Zlevnění"); b.setCreatedAt(OffsetDateTime.now());
    when(notifications.findByUserIdOrderByIdDesc(eq(1L), any()))
        .thenReturn(List.of(a, b)); // limit=1, ale dvě řádky

    var page = service.mine(null, 1);

    assertThat(page.data()).hasSize(1);
    assertThat(page.nextCursor()).isEqualTo("10");
    assertThat(page.data().get(0)).containsEntry("listingId", 7L).containsEntry("read", false);
  }

  @Test
  void markReadUsesCurrentUserScope() {
    when(notifications.existsById(10L)).thenReturn(true);
    when(notifications.markRead(eq(10L), eq(1L), any(OffsetDateTime.class))).thenReturn(1);
    assertThat(service.markRead(10L)).containsEntry("read", true);
    verify(notifications).markRead(eq(10L), eq(1L), any(OffsetDateTime.class));
  }

  @Test
  void optionalRepositoryValueNeverLeaksIntoNotification() {
    when(favorites.findByListingId(10L)).thenReturn(Optional.of(fav(1L, 10L)).stream().toList());
    when(notifications.findUnreadByTarget(anyLong(), anyLong(), anyString(), anyInt())).thenReturn(List.of());
    assertThat(service.notifyPriceDrop(listing(10L, 2L, "X"), 4000, 3000)).isEqualTo(1);
    verify(notifications).save(any(Notification.class));
  }
}