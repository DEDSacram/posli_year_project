package cz.bazar.repository;

import cz.bazar.domain.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {
  Optional<Conversation> findByBuyerIdAndSellerIdAndListingId(Long buyerId, Long sellerId, Long listingId);
  List<Conversation> findByBuyerIdOrSellerIdOrderByIdDesc(Long buyerId, Long sellerId);
  List<Conversation> findByListingId(Long listingId);
  long countByListingId(Long listingId);
}
