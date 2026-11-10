package cz.bazar.controller;

import cz.bazar.dto.Dtos.*;
import cz.bazar.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/chat")
public class ChatController {
  private final ChatService chat;
  public ChatController(ChatService chat) { this.chat = chat; }

  @PostMapping("/conversations")
  public ResponseEntity<Map<String, Object>> create(@Valid @RequestBody ConversationCreate r) {
    var c = chat.getOrCreate(r.listingId());
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", c.getId());
    m.put("listingId", c.getListingId());
    m.put("buyerId", c.getBuyerId());
    m.put("sellerId", c.getSellerId());
    return ResponseEntity.status(201).body(m);
  }

  @GetMapping("/conversations")
  public Map<String, Object> list() {
    List<Map<String, Object>> data = chat.myConversations();
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("data", data);
    out.put("nextCursor", null);
    return out;
  }

  @PostMapping("/messages")
  public ResponseEntity<Map<String, Object>> send(@Valid @RequestBody MessageCreate r) {
    var m = chat.send(r);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("id", m.getId());
    out.put("conversationId", m.getConversationId());
    out.put("senderId", m.getSenderId());
    out.put("bodyText", m.getBodyText());
    out.put("sentAt", m.getSentAt());
    return ResponseEntity.status(201).body(out);
  }

  @GetMapping("/conversations/{id}/messages")
  public Page<Map<String, Object>> history(@PathVariable Long id,
      @RequestParam(required = false) Long cursor, @RequestParam(defaultValue = "20") int limit) {
    return chat.history(id, cursor, limit);
  }

  @GetMapping("/interest")
  public Map<String, Object> interest(@RequestParam Long listingId) {
    return chat.interest(listingId);
  }
}
