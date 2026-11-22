package cz.bazar.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Setter
@Getter
@Entity
@Table(name = "messages")
public class ChatMessage {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(name = "conversation_id", nullable = false)
  private Long conversationId;
  @Column(name = "sender_id", nullable = false)
  private Long senderId;
  @Column(name = "body_text", nullable = false, length = 8192)
  private String bodyText;
  @Column(name = "sent_at")
  private OffsetDateTime sentAt;

}
