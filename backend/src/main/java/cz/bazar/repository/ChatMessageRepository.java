package cz.bazar.repository;

import cz.bazar.domain.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import org.springframework.data.domain.Pageable;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
  List<ChatMessage> findByConversationIdOrderByIdDesc(Long conversationId, Pageable pageable);
  List<ChatMessage> findByConversationIdAndIdLessThanOrderByIdDesc(Long conversationId, Long cursor, Pageable pageable);
}
