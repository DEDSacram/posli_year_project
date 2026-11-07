package cz.bazar.service;

import cz.bazar.domain.*;
import cz.bazar.dto.Dtos.*;
import cz.bazar.exception.ApiException;
import cz.bazar.repository.*;
import cz.bazar.security.ApiRateLimiter;
import cz.bazar.security.RateLimitProperties;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;

@Service
public class ChatService {
  private final ConversationRepository conversations;
  private final ChatMessageRepository messages;
  private final ListingRepository listings;
  private final CurrentUserService current;
  private final ApiRateLimiter rateLimiter;
  private final RateLimitProperties limits;

  public ChatService(ConversationRepository conversations, ChatMessageRepository messages,
                     ListingRepository listings, CurrentUserService current,
                     ApiRateLimiter rateLimiter, RateLimitProperties limits) {
    this.conversations = conversations; this.messages = messages;
    this.listings = listings; this.current = current;
    this.rateLimiter = rateLimiter; this.limits = limits;
  }

  @Transactional
  public Conversation getOrCreate(Long listingId) {
    User me = current.require();
    Listing l = listings.findById(listingId)
        .orElseThrow(() -> ApiException.notFound("LISTING_NOT_FOUND", "Inzerát nenalezen."));
    if (l.getSellerId().equals(me.getId()))
      throw ApiException.badRequest("OWN_LISTING", "Vlastní inzerát nepotřebuje konverzaci.");
    var existing = conversations.findByBuyerIdAndSellerIdAndListingId(me.getId(), l.getSellerId(), listingId);
    if (existing.isPresent()) return existing.get();
    Conversation c = new Conversation();
    c.setListingId(listingId);
    c.setBuyerId(me.getId());
    c.setSellerId(l.getSellerId());
    c.setCreatedAt(OffsetDateTime.now());
    try {
      return conversations.save(c);
    } catch (DataIntegrityViolationException e) {
      // dedup zavod: nacti existujici
      return conversations.findByBuyerIdAndSellerIdAndListingId(me.getId(), l.getSellerId(), listingId)
          .orElseThrow(() -> e);
    }
  }

  @Transactional
  public ChatMessage send(MessageCreate r) {
    User me = current.require();
    rateLimiter.check("chat:" + me.getId(), limits.getChat(), "Příliš mnoho zpráv. Zpomalte prosím.");
    if (r.bodyText() == null || r.bodyText().isBlank())
      throw ApiException.badRequest("EMPTY_MESSAGE", "Zpráva je prázdná.");
    if (r.bodyText().length() > 8192)
      throw ApiException.badRequest("MESSAGE_TOO_LONG", "Zpráva je delší než 8 KB.");
    Conversation c = conversations.findById(r.conversationId())
        .orElseThrow(() -> ApiException.notFound("CONVERSATION_NOT_FOUND", "Konverzace nenalezena."));
    if (!c.getBuyerId().equals(me.getId()) && !c.getSellerId().equals(me.getId()))
      throw ApiException.forbidden("NOT_PARTICIPANT", "Nejste účastníkem konverzace.");
    ChatMessage m = new ChatMessage();
    m.setConversationId(c.getId());
    m.setSenderId(me.getId());
    m.setBodyText(r.bodyText()); // plaintext na serveru kvuli moderaci (TLS na transportu)
    m.setSentAt(OffsetDateTime.now());
    return messages.save(m);
  }

  public Page<Map<String, Object>> history(Long conversationId, Long cursor, int limit) {
    User me = current.require();
    limit = Math.min(Math.max(limit, 1), 50);
    Conversation c = conversations.findById(conversationId)
        .orElseThrow(() -> ApiException.notFound("CONVERSATION_NOT_FOUND", "Konverzace nenalezena."));
    if (!c.getBuyerId().equals(me.getId()) && !c.getSellerId().equals(me.getId()))
      throw ApiException.forbidden("NOT_PARTICIPANT", "Nejste účastníkem konverzace.");
    List<ChatMessage> rows = cursor == null
        ? messages.findByConversationIdOrderByIdDesc(conversationId, PageRequest.of(0, limit + 1))
        : messages.findByConversationIdAndIdLessThanOrderByIdDesc(conversationId, cursor, PageRequest.of(0, limit + 1));
    String next = null;
    List<ChatMessage> page = rows;
    if (rows.size() > limit) {
      page = rows.subList(0, limit);
      next = String.valueOf(page.get(page.size() - 1).getId());
    }
    return new Page<>(page.stream().map(m -> {
      Map<String, Object> x = new LinkedHashMap<>();
      x.put("id", m.getId());
      x.put("conversationId", m.getConversationId());
      x.put("senderId", m.getSenderId());
      x.put("bodyText", m.getBodyText());
      x.put("sentAt", m.getSentAt());
      return x;
    }).toList(), next);
  }

  public List<Map<String, Object>> myConversations() {
    User me = current.require();
    return conversations.findByBuyerIdOrSellerIdOrderByIdDesc(me.getId(), me.getId()).stream().map(c -> {
      Map<String, Object> x = new LinkedHashMap<>();
      x.put("id", c.getId());
      x.put("listingId", c.getListingId());
      x.put("buyerId", c.getBuyerId());
      x.put("sellerId", c.getSellerId());
      x.put("createdAt", c.getCreatedAt());
      return x;
    }).toList();
  }

  /** Počet zájemců o inzerát = distinct kupující s konverzací (sociální důkaz, veřejné). */
  @Transactional(readOnly = true)
  public Map<String, Object> interest(Long listingId) {
    listings.findById(listingId)
        .orElseThrow(() -> ApiException.notFound("LISTING_NOT_FOUND", "Inzerát nenalezen."));
    long distinctBuyers = conversations.findByListingId(listingId).stream()
        .map(Conversation::getBuyerId).distinct().count();
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("listingId", listingId);
    out.put("interestCount", distinctBuyers);
    return out;
  }
}
