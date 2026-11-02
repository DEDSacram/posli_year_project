package cz.bazar.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

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

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public Long getConversationId() { return conversationId; }
  public void setConversationId(Long conversationId) { this.conversationId = conversationId; }
  public Long getSenderId() { return senderId; }
  public void setSenderId(Long senderId) { this.senderId = senderId; }
  public String getBodyText() { return bodyText; }
  public void setBodyText(String bodyText) { this.bodyText = bodyText; }
  public OffsetDateTime getSentAt() { return sentAt; }
  public void setSentAt(OffsetDateTime sentAt) { this.sentAt = sentAt; }
}
