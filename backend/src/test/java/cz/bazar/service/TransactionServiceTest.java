package cz.bazar.service;

import cz.bazar.domain.Listing;
import cz.bazar.domain.SaleTransaction;
import cz.bazar.domain.User;
import cz.bazar.dto.Dtos.Page;
import cz.bazar.exception.ApiException;
import cz.bazar.repository.ListingRepository;
import cz.bazar.repository.ReviewRepository;
import cz.bazar.repository.SaleTransactionRepository;
import cz.bazar.security.ApiRateLimiter;
import cz.bazar.security.RateLimitProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Rezervace, dokončení a právo spotřebitele na odstoupení (14 dní, § 1829 OZ). */
class TransactionServiceTest {

  private SaleTransactionRepository transactions;
  private ListingRepository listings;
  private ReviewRepository reviews;
  private CurrentUserService current;
  private TransactionService service;

  @BeforeEach
  void setUp() {
    transactions = mock(SaleTransactionRepository.class);
    listings = mock(ListingRepository.class);
    reviews = mock(ReviewRepository.class);
    current = mock(CurrentUserService.class);
    ApiRateLimiter limiter = mock(ApiRateLimiter.class);
    service = new TransactionService(transactions, listings, reviews, current, limiter,
        new RateLimitProperties(), mock(AuditService.class));
    when(current.currentUserId()).thenReturn(1L);
    when(transactions.save(any(SaleTransaction.class))).thenAnswer(inv -> inv.getArgument(0));
  }

  private static User user(Long id) {
    User u = new User();
    u.setId(id);
    u.setEmail("u" + id + "@test.cz");
    u.setDisplayName("U" + id);
    u.setSellerType("private");
    return u;
  }

  private SaleTransaction tx(String status, String sellerType, OffsetDateTime completedAt) {
    SaleTransaction t = new SaleTransaction();
    t.setId(50L);
    t.setListingId(7L);
    t.setBuyerId(1L);
    t.setSellerId(2L);
    t.setPriceCzk(1500);
    t.setStatus(status);
    t.setSellerTypeSnapshot(sellerType);
    t.setCompletedAt(completedAt);
    return t;
  }

  // ---------- odstoupení od smlouvy ----------

  @Test
  void buyerCanWithdrawWithin14DaysFromTrader() {
    when(current.require()).thenReturn(user(1L));
    SaleTransaction t = tx("completed", "trader", OffsetDateTime.now().minusDays(3));
    when(transactions.findById(50L)).thenReturn(Optional.of(t));

    var out = service.toMap(service.withdraw(50L, "vadný výrobek"));

    assertThat(out.get("status")).isEqualTo("withdrawn");
    assertThat(out.get("withdrawnAt")).isNotNull();
    assertThat(t.getWithdrawalReason()).isEqualTo("vadný výrobek");
  }

  @Test
  void withdrawalAfter14DaysIsRejected() {
    when(current.require()).thenReturn(user(1L));
    SaleTransaction t = tx("completed", "trader", OffsetDateTime.now().minusDays(15));
    when(transactions.findById(50L)).thenReturn(Optional.of(t));

    assertThatThrownBy(() -> service.withdraw(50L, null))
        .isInstanceOf(ApiException.class)
        .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("WITHDRAWAL_EXPIRED"));
    assertThat(t.getStatus()).isEqualTo("completed");
  }

  @Test
  void withdrawalExactlyOnDay14IsStillAllowed() {
    when(current.require()).thenReturn(user(1L));
    SaleTransaction t = tx("completed", "trader", OffsetDateTime.now().minusDays(14).plusMinutes(5));
    when(transactions.findById(50L)).thenReturn(Optional.of(t));

    assertThat(service.toMap(service.withdraw(50L, null)).get("status")).isEqualTo("withdrawn");
  }

  @Test
  void privateSellerSaleHasNoWithdrawalRight() {
    when(current.require()).thenReturn(user(1L));
    SaleTransaction t = tx("completed", "private", OffsetDateTime.now().minusDays(1));
    when(transactions.findById(50L)).thenReturn(Optional.of(t));

    assertThatThrownBy(() -> service.withdraw(50L, null))
        .isInstanceOf(ApiException.class)
        .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("WITHDRAWAL_NOT_APPLIES"));
  }

  @Test
  void sellerCannotWithdrawOnBehalfOfBuyer() {
    when(current.require()).thenReturn(user(2L));
    SaleTransaction t = tx("completed", "trader", OffsetDateTime.now().minusDays(1));
    when(transactions.findById(50L)).thenReturn(Optional.of(t));

    assertThatThrownBy(() -> service.withdraw(50L, null))
        .isInstanceOf(ApiException.class)
        .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("ONLY_BUYER"));
  }

  @Test
  void cannotWithdrawNotCompletedTransaction() {
    when(current.require()).thenReturn(user(1L));
    SaleTransaction t = tx("reserved", "trader", null);
    when(transactions.findById(50L)).thenReturn(Optional.of(t));

    assertThatThrownBy(() -> service.withdraw(50L, null))
        .isInstanceOf(ApiException.class)
        .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("INVALID_STATUS"));
  }

  @Test
  void withdrawalRightsReportDeadlineAndEligibility() {
    when(current.require()).thenReturn(user(1L));
    SaleTransaction t = tx("completed", "trader", OffsetDateTime.now().minusDays(2));
    when(transactions.findById(50L)).thenReturn(Optional.of(t));

    var rights = service.withdrawalRights(50L);

    assertThat(rights).containsEntry("applies", true).containsEntry("eligible", true)
        .containsEntry("days", 14);
    assertThat((OffsetDateTime) rights.get("deadline")).isAfter(OffsetDateTime.now());
  }

  @Test
  void withdrawalRightsAreFalseForPrivateSeller() {
    when(current.require()).thenReturn(user(1L));
    when(transactions.findById(50L)).thenReturn(Optional.of(tx("completed", "private", OffsetDateTime.now())));

    var rights = service.withdrawalRights(50L);

    assertThat(rights).containsEntry("applies", false).containsEntry("eligible", false);
    assertThat((String) rights.get("explanationCs")).contains("soukrom");
  }

  @Test
  void outsiderCannotSeeWithdrawalRights() {
    when(current.require()).thenReturn(user(99L));
    when(transactions.findById(50L)).thenReturn(Optional.of(tx("completed", "trader", OffsetDateTime.now())));

    assertThatThrownBy(() -> service.withdrawalRights(50L))
        .isInstanceOf(ApiException.class)
        .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("NOT_PARTICIPANT"));
  }

  // ---------- stavy ----------

  @Test
  void completeStampsCompletedAt() {
    when(current.require()).thenReturn(user(1L));
    SaleTransaction t = tx("reserved", "private", null);
    when(transactions.findById(50L)).thenReturn(Optional.of(t));
    when(transactions.save(any())).thenAnswer(inv -> inv.getArgument(0));

    var out = service.toMap(service.complete(50L));

    assertThat(out.get("status")).isEqualTo("completed");
    assertThat((OffsetDateTime) t.getCompletedAt()).isNotNull();
  }

  @Test
  void outsiderCannotComplete() {
    when(current.require()).thenReturn(user(99L));
    when(transactions.findById(50L)).thenReturn(Optional.of(tx("reserved", "private", null)));

    assertThatThrownBy(() -> service.complete(50L))
        .isInstanceOf(ApiException.class)
        .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("NOT_PARTICIPANT"));
  }

  @Test
  void cancelRequiresReservedState() {
    when(current.require()).thenReturn(user(1L));
    when(transactions.findById(50L)).thenReturn(Optional.of(tx("completed", "private", OffsetDateTime.now())));

    assertThatThrownBy(() -> service.cancel(50L))
        .isInstanceOf(ApiException.class)
        .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("INVALID_STATUS"));
  }

  @Test
  void reservationIsIdempotentPerBuyerAndListing() {
    when(current.require()).thenReturn(user(1L));
    Listing l = new Listing();
    l.setId(7L);
    l.setSellerId(2L);
    l.setPriceCzk(1500);
    l.setStatus("active");
    l.setSellerTypeSnapshot("private");
    when(listings.findById(7L)).thenReturn(Optional.of(l));
    SaleTransaction existing = tx("reserved", "private", null);
    when(transactions.findFirstByListingIdAndBuyerIdAndStatusOrderByIdDesc(7L, 1L, "reserved"))
        .thenReturn(Optional.of(existing));

    assertThat(service.reserve(7L)).isSameAs(existing);
    verify(transactions, never()).save(any());
  }

  @Test
  void cannotReserveOwnListing() {
    when(current.require()).thenReturn(user(1L));
    Listing l = new Listing();
    l.setId(7L);
    l.setSellerId(1L);
    l.setStatus("active");
    when(listings.findById(7L)).thenReturn(Optional.of(l));

    assertThatThrownBy(() -> service.reserve(7L))
        .isInstanceOf(ApiException.class)
        .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("OWN_LISTING"));
  }

  @Test
  void reservationSnapshotsPrice() {
    when(current.require()).thenReturn(user(1L));
    Listing l = new Listing();
    l.setId(7L);
    l.setSellerId(2L);
    l.setPriceCzk(4200);
    l.setStatus("active");
    l.setSellerTypeSnapshot("trader");
    when(listings.findById(7L)).thenReturn(Optional.of(l));
    when(transactions.findFirstByListingIdAndBuyerIdAndStatusOrderByIdDesc(anyLong(), anyLong(), anyString()))
        .thenReturn(Optional.empty());
    when(transactions.save(any())).thenAnswer(inv -> inv.getArgument(0));

    var out = service.reserve(7L);

    assertThat(out.getPriceCzk()).isEqualTo(4200);
    assertThat(out.getSellerTypeSnapshot()).isEqualTo("trader");
  }

  // ---------- přehled obchodů ----------

  @Test
  void overviewAggregatesSellerSide() {
    when(current.require()).thenReturn(user(1L));
    when(transactions.countByStatusForSeller(1L)).thenReturn(List.<Object[]>of(
        new Object[]{"completed", 4L}, new Object[]{"reserved", 2L},
        new Object[]{"cancelled", 1L}, new Object[]{"withdrawn", 1L}));
    when(transactions.sumCompletedBySeller(1L)).thenReturn(9800L);
    Object[] stats = {4.75d, 8L};
    when(reviews.ratingStats(1L))
        .thenReturn(new ArrayList<Object[]>(Collections.singletonList(stats)));

    var out = service.overview();
    @SuppressWarnings("unchecked")
    var seller = (java.util.Map<String, Object>) out.get("asSeller");

    assertThat(seller).containsEntry("completed", 4L).containsEntry("reserved", 2L)
        .containsEntry("cancelled", 1L).containsEntry("withdrawn", 1L)
        .containsEntry("revenueCzk", 9800L)
        .containsEntry("ratingAverage", 4.8).containsEntry("ratingCount", 8L);
  }

  @Test
  void overviewBuyerSideHasNoRating() {
    when(current.require()).thenReturn(user(1L));
    when(transactions.countByStatusForBuyer(1L))
        .thenReturn(java.util.Collections.singletonList(new Object[]{"completed", 1L}));
    when(transactions.countByStatusForSeller(1L)).thenReturn(List.<Object[]>of());
    when(transactions.sumCompletedBySeller(1L)).thenReturn(0L);
    when(reviews.ratingStats(1L)).thenReturn(new java.util.ArrayList<>());

    var out = service.overview();
    @SuppressWarnings("unchecked")
    var buyer = (java.util.Map<String, Object>) out.get("asBuyer");

    assertThat(buyer).containsEntry("completed", 1L).containsEntry("reserved", 0L);
    assertThat(buyer).doesNotContainKey("revenueCzk");
    assertThat(buyer).doesNotContainKey("ratingAverage");
  }

  @Test
  void mineRejectsUnknownRole() {
    when(current.require()).thenReturn(user(1L));
    assertThatThrownBy(() -> service.mine("admin"))
        .isInstanceOf(ApiException.class)
        .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("INVALID_ROLE"));
  }

  @Test
  void mineFiltersBySellerRole() {
    when(current.require()).thenReturn(user(1L));
    SaleTransaction asSeller = tx("completed", "private", OffsetDateTime.now());
    asSeller.setSellerId(1L);  // prodejcem jsem já
    asSeller.setBuyerId(9L);   // kupcem nejsem
    when(transactions.findBySellerIdOrderByIdDesc(1L)).thenReturn(List.of(asSeller));
    when(listings.findById(7L)).thenReturn(Optional.empty());
    when(reviews.existsByTransactionId(anyLong())).thenReturn(false);

    var rows = service.mine("SELLER"); // velikost písmen není důležitá

    assertThat(rows).hasSize(1);
    assertThat(rows.get(0)).containsEntry("role", "seller");
    verify(transactions).findBySellerIdOrderByIdDesc(1L);
  }

  @Test
  void roleOfReportsBuyerForLoggedInBuyer() {
    when(current.require()).thenReturn(user(1L));
    when(transactions.findByBuyerIdOrderByIdDesc(1L)).thenReturn(List.of(tx("reserved", "private", null)));
    when(listings.findById(7L)).thenReturn(Optional.empty());
    when(reviews.existsByTransactionId(anyLong())).thenReturn(false);

    assertThat(service.mine("buyer").get(0)).containsEntry("role", "buyer");
  }

  @Test
  void toMapIncludesReviewFlag() {
    when(listings.findById(7L)).thenReturn(Optional.empty());
    when(reviews.existsByTransactionId(50L)).thenReturn(true);

    var m = service.toMap(tx("completed", "private", OffsetDateTime.now()));

    assertThat(m).containsEntry("hasReview", true).containsEntry("priceCzk", 1500);
  }

  @Test
  void guestSeesTransactionWithNullRole() {
    when(current.currentUserId()).thenReturn(null);
    when(listings.findById(7L)).thenReturn(Optional.empty());
    when(reviews.existsByTransactionId(anyLong())).thenReturn(false);

    assertThat(service.toMap(tx("reserved", "private", null))).containsEntry("role", null);
  }
}