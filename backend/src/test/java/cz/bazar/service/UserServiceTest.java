package cz.bazar.service;

import cz.bazar.domain.SellerTaxProfile;
import cz.bazar.domain.User;
import cz.bazar.exception.ApiException;
import cz.bazar.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Odznaky „poctivých obchodníků“ se odvozují z hodnocení, prodejů a potvrzených námek. */
class UserServiceTest {

  private UserRepository users;
  private SellerTaxProfileRepository profiles;
  private ReviewRepository reviewRepo;
  private SaleTransactionRepository transactions;
  private DisputeService disputes;
  private UserService service;

  @BeforeEach
  void setUp() {
    users = mock(UserRepository.class);
    profiles = mock(SellerTaxProfileRepository.class);
    reviewRepo = mock(ReviewRepository.class);
    transactions = mock(SaleTransactionRepository.class);
    disputes = mock(DisputeService.class);
    service = new UserService(users, profiles, reviewRepo, transactions, disputes);
    when(transactions.countCompletedBySeller(anyLong())).thenReturn(0L);
    when(disputes.upheldAgainst(anyLong())).thenReturn(0L);
    when(reviewRepo.ratingStats(anyLong())).thenReturn(new java.util.ArrayList<>());
  }

  private User user(Long id, String sellerType) {
    User u = new User();
    u.setId(id);
    u.setEmail("u" + id + "@test.cz");
    u.setDisplayName("Uživatel");
    u.setSellerType(sellerType);
    return u;
  }

  /** ratingStats vrací List<Object[]> — řádek sestavíme přes singletonList, ne přes varargs. */
  private void stats(double avg, long count) {
    Object[] row = new Object[]{avg, count};
    when(reviewRepo.ratingStats(1L))
        .thenReturn(new ArrayList<Object[]>(Collections.singletonList(row)));
  }

  @Test
  void newSellerHasNoMeritBadges() {
    when(users.findById(1L)).thenReturn(java.util.Optional.of(user(1L, "private")));

    var b = service.badges(1L);

    assertThat(b).containsEntry("privateSeller", true).containsEntry("trader", false)
        .containsEntry("trustedSeller", false).containsEntry("experienced", false)
        .containsEntry("highlyRated", false).containsEntry("cleanRecord", true)
        .containsEntry("ratingCount", 0L).containsEntry("completedSales", 0L);
  }

  @Test
  void trustedSellerNeedsThreeReviewsAtFourAndAHalf() {
    when(users.findById(1L)).thenReturn(java.util.Optional.of(user(1L, "private")));
    stats(4.5, 3);

    assertThat(service.badges(1L)).containsEntry("trustedSeller", true);
  }

  @Test
  void justUnderThresholdIsNotTrusted() {
    when(users.findById(1L)).thenReturn(java.util.Optional.of(user(1L, "private")));
    stats(4.5, 2); // málo recenzí

    assertThat(service.badges(1L)).containsEntry("trustedSeller", false);
  }

  @Test
  void highAverageWithoutEnoughReviewsIsNotTrusted() {
    when(users.findById(1L)).thenReturn(java.util.Optional.of(user(1L, "private")));
    stats(5.0, 2);

    assertThat(service.badges(1L)).containsEntry("trustedSeller", false).containsEntry("highlyRated", false);
  }

  @Test
  void highlyRatedNeedsFiveReviewsAtFourPointEight() {
    when(users.findById(1L)).thenReturn(java.util.Optional.of(user(1L, "private")));
    stats(4.8, 5);

    assertThat(service.badges(1L)).containsEntry("highlyRated", true);
  }

  @Test
  void experiencedNeedsTenCompletedSales() {
    when(users.findById(1L)).thenReturn(java.util.Optional.of(user(1L, "private")));
    when(transactions.countCompletedBySeller(1L)).thenReturn(10L);

    assertThat(service.badges(1L)).containsEntry("experienced", true);
  }

  @Test
  void upheldDisputeRevokesTrustAndCleanRecord() {
    when(users.findById(1L)).thenReturn(java.util.Optional.of(user(1L, "private")));
    stats(4.9, 6);
    when(disputes.upheldAgainst(1L)).thenReturn(1L);

    var b = service.badges(1L);

    assertThat(b).containsEntry("trustedSeller", false).containsEntry("cleanRecord", false)
        .containsEntry("highlyRated", true); // vysoké hodnocení zůstává fakt
  }

  @Test
  void verifiedTraderRequiresAdminApproval() {
    when(users.findById(1L)).thenReturn(java.util.Optional.of(user(1L, "trader")));
    SellerTaxProfile p = new SellerTaxProfile();
    p.setUserId(1L);
    p.applyVerificationStatus(SellerTaxProfile.STATUS_VERIFIED);
    when(profiles.findById(1L)).thenReturn(java.util.Optional.of(p));

    assertThat(service.badges(1L)).containsEntry("verifiedTrader", true)
        .containsEntry("hasDac7Profile", true).containsEntry("trader", true);
  }

  @Test
  void pendingTraderIsNotVerified() {
    when(users.findById(1L)).thenReturn(java.util.Optional.of(user(1L, "trader")));
    SellerTaxProfile p = new SellerTaxProfile();
    p.setUserId(1L);
    p.applyVerificationStatus(SellerTaxProfile.STATUS_PENDING);
    when(profiles.findById(1L)).thenReturn(java.util.Optional.of(p));

    assertThat(service.badges(1L)).containsEntry("verifiedTrader", false).containsEntry("trader", true);
  }

  @Test
  void ratingIsRoundedToOneDecimal() {
    when(users.existsById(1L)).thenReturn(true);
    stats(4.3333, 3);

    assertThat(service.rating(1L)).containsEntry("average", 4.3).containsEntry("count", 3L);
  }

  @Test
  void ratingOfUnknownUserIs404() {
    when(users.existsById(99L)).thenReturn(false);
    assertThatThrownBy(() -> service.rating(99L))
        .isInstanceOf(ApiException.class)
        .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("USER_NOT_FOUND"));
  }

  @Test
  void publicProfileHidesDeletedUser() {
    User u = user(1L, "private");
    u.setDeletedAt(OffsetDateTime.now());
    when(users.findById(1L)).thenReturn(java.util.Optional.of(u));

    assertThatThrownBy(() -> service.publicProfile(1L))
        .isInstanceOf(ApiException.class)
        .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(404));
  }

  @Test
  void publicProfileExposesNoTaxData() {
    when(users.findById(1L)).thenReturn(java.util.Optional.of(user(1L, "trader")));

    var p = service.publicProfile(1L);

    assertThat(p).containsOnlyKeys("id", "displayName", "sellerType", "createdAt");
  }
}