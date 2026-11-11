package cz.bazar.service;

import cz.bazar.domain.Review;
import cz.bazar.domain.ReviewDispute;
import cz.bazar.domain.User;
import cz.bazar.exception.ApiException;
import cz.bazar.repository.ReviewDisputeRepository;
import cz.bazar.repository.ReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Námitky proti hodnocení: oprávnění, jedna otevřená námitka, rozhodnutí admina. */
class DisputeServiceTest {

  private ReviewDisputeRepository disputes;
  private ReviewRepository reviews;
  private CurrentUserService current;
  private AuditService audit;
  private DisputeService service;

  @BeforeEach
  void setUp() {
    disputes = mock(ReviewDisputeRepository.class);
    reviews = mock(ReviewRepository.class);
    current = mock(CurrentUserService.class);
    audit = mock(AuditService.class);
    service = new DisputeService(disputes, reviews, current, audit);
  }

  private static User user(Long id) {
    User u = new User();
    u.setId(id);
    u.setEmail("u" + id + "@test.cz");
    u.setDisplayName("U" + id);
    u.setSellerType("private");
    return u;
  }

  private static Review review(Long id, Long reviewee, String moderationStatus) {
    Review r = new Review();
    r.setId(id);
    r.setTransactionId(id * 10);
    r.setReviewerId(5L);
    r.setRevieweeId(reviewee);
    r.setListingId(id * 100);
    r.setRating((short) 5);
    r.setText("text");
    r.setModerationStatus(moderationStatus);
    r.setCreatedAt(OffsetDateTime.now());
    return r;
  }

  @Test
  void revieweeCanFileDispute() {
    when(current.require()).thenReturn(user(7L));
    when(reviews.findById(3L)).thenReturn(java.util.Optional.of(review(3L, 7L, "visible")));
    when(disputes.findByReviewIdAndStatus(3L, ReviewDispute.STATUS_OPEN)).thenReturn(java.util.Optional.empty());
    when(disputes.save(any())).thenAnswer(inv -> inv.getArgument(0));

    var d = service.create(3L, "Reakce neodpovídá realitě.");

    assertThat(d.getReviewId()).isEqualTo(3L);
    assertThat(d.getDisputerId()).isEqualTo(7L);
    assertThat(d.getStatus()).isEqualTo(ReviewDispute.STATUS_OPEN);
    assertThat(d.getResolvedAt()).isNull();
    verify(audit).log(7L, "REVIEW_DISPUTE_OPEN", "review", 3L);
  }

  @Test
  void otherUserCannotFileDispute() {
    when(current.require()).thenReturn(user(8L));
    when(reviews.findById(3L)).thenReturn(java.util.Optional.of(review(3L, 7L, "visible")));

    assertThatThrownBy(() -> service.create(3L, "nepravda"))
        .isInstanceOf(ApiException.class)
        .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("NOT_REVIEWEE"));
    verify(disputes, never()).save(any());
  }

  @Test
  void secondOpenDisputeIsRejected() {
    when(current.require()).thenReturn(user(7L));
    when(reviews.findById(3L)).thenReturn(java.util.Optional.of(review(3L, 7L, "visible")));
    when(disputes.findByReviewIdAndStatus(3L, ReviewDispute.STATUS_OPEN))
        .thenReturn(java.util.Optional.of(new ReviewDispute()));

    assertThatThrownBy(() -> service.create(3L, "podruhé"))
        .isInstanceOf(ApiException.class)
        .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("DISPUTE_OPEN"));
  }

  @Test
  void cannotDisputeRemovedReview() {
    when(current.require()).thenReturn(user(7L));
    when(reviews.findById(3L)).thenReturn(java.util.Optional.of(review(3L, 7L, "removed")));

    assertThatThrownBy(() -> service.create(3L, "x"))
        .isInstanceOf(ApiException.class)
        .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("REVIEW_NOT_VISIBLE"));
  }

  @Test
  void missingReviewReturns404() {
    when(current.require()).thenReturn(user(7L));
    when(reviews.findById(3L)).thenReturn(java.util.Optional.empty());
    assertThatThrownBy(() -> service.create(3L, "x"))
        .isInstanceOf(ApiException.class)
        .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(404));
  }

  @Test
  void upholdRemovesReviewFromPublicView() {
    when(current.require()).thenReturn(user(1L)); // admin
    ReviewDispute d = new ReviewDispute();
    d.setId(11L);
    d.setReviewId(3L);
    d.setDisputerId(7L);
    d.setStatus(ReviewDispute.STATUS_OPEN);
    Review r = review(3L, 7L, "visible");
    when(disputes.findById(11L)).thenReturn(java.util.Optional.of(d));
    when(reviews.findById(3L)).thenReturn(java.util.Optional.of(r));
    when(disputes.save(any())).thenAnswer(inv -> inv.getArgument(0));

    service.resolve(11L, true, "Reakce neodpovídá uskutečněnému stavu");

    assertThat(r.getModerationStatus()).isEqualTo("removed");
    verify(reviews).save(r);
    verify(audit).log(1L, "REVIEW_DISPUTE_UPHELD", "review", 3L);
  }

  @Test
  void rejectKeepsReviewVisible() {
    when(current.require()).thenReturn(user(1L));
    ReviewDispute d = new ReviewDispute();
    d.setId(11L);
    d.setReviewId(3L);
    d.setStatus(ReviewDispute.STATUS_OPEN);
    when(disputes.findById(11L)).thenReturn(java.util.Optional.of(d));
    when(disputes.save(any())).thenAnswer(inv -> inv.getArgument(0));

    var out = service.resolve(11L, false, "námitka neodůvodněná");

    assertThat(out.getStatus()).isEqualTo(ReviewDispute.STATUS_REJECTED);
    assertThat(out.getResolutionNote()).isEqualTo("námitka neodůvodněná");
    assertThat(out.getResolvedBy()).isEqualTo(1L);
    assertThat(out.getResolvedAt()).isNotNull();
    verify(reviews, never()).save(any());
    verify(audit).log(1L, "REVIEW_DISPUTE_REJECTED", "review", 3L);
  }

  @Test
  void alreadyResolvedDisputeCannotBeDecidedAgain() {
    when(current.require()).thenReturn(user(1L));
    ReviewDispute d = new ReviewDispute();
    d.setId(11L);
    d.setStatus(ReviewDispute.STATUS_REJECTED);
    when(disputes.findById(11L)).thenReturn(java.util.Optional.of(d));

    assertThatThrownBy(() -> service.resolve(11L, true, "znovu"))
        .isInstanceOf(ApiException.class)
        .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("DISPUTE_RESOLVED"));
  }

  @Test
  void statusOfReturnsLatestDisputeState() {
    ReviewDispute latest = new ReviewDispute();
    latest.setStatus(ReviewDispute.STATUS_UPHELD);
    when(disputes.findLatestForReview(3L)).thenReturn(List.of(latest));

    assertThat(service.statusOf(3L)).isEqualTo(ReviewDispute.STATUS_UPHELD);
  }

  @Test
  void statusOfIsNullWhenNoDisputeExists() {
    when(disputes.findLatestForReview(3L)).thenReturn(List.of());
    assertThat(service.statusOf(3L)).isNull();
  }

  @Test
  void upheldCountFeedsBadges() {
    when(disputes.countByDisputerIdAndStatus(7L, ReviewDispute.STATUS_UPHELD)).thenReturn(2L);
    assertThat(service.upheldAgainst(7L)).isEqualTo(2L);
  }

  @Test
  void mineReturnsDisputesOfLoggedInSeller() {
    when(current.require()).thenReturn(user(7L));
    ReviewDispute d = new ReviewDispute();
    d.setId(1L);
    d.setReviewId(3L);
    d.setReason("x");
    d.setStatus(ReviewDispute.STATUS_OPEN);
    when(disputes.findByDisputerIdOrderByIdDesc(7L)).thenReturn(List.of(d));

    ArgumentCaptor<Long> captor = ArgumentCaptor.forClass(Long.class);
    var out = service.mine();
    verify(disputes).findByDisputerIdOrderByIdDesc(captor.capture());
    assertThat(captor.getValue()).isEqualTo(7L);
    assertThat(out).hasSize(1);
  }
}