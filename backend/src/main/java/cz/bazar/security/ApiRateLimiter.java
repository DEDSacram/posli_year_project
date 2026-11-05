package cz.bazar.security;

import cz.bazar.exception.ApiException;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/** Obecný in-memory rate-limit (klouzavé okno) pro zapisové endpointy: chat, hlášení, rezervace. */
@Component
public class ApiRateLimiter {
  private final ConcurrentHashMap<String, Deque<Long>> buckets = new ConcurrentHashMap<>();
  private final RateLimitProperties props;

  public ApiRateLimiter(RateLimitProperties props) {
    this.props = props;
  }

  /**
   * @param key      identifikátor klienta (IP, nebo ID uživatele)
   * @param max      maximální počet požadavků v okně
   * @param windowMs délka okna v milisekundách
   * @param msg      česká chybová hláška pro klienta
   */
  public void check(String key, int max, long windowMs, String msg) {
    if (!props.isEnabled()) return;
    long now = System.currentTimeMillis();
    Deque<Long> q = buckets.computeIfAbsent(key == null ? "unknown" : key, k -> new ArrayDeque<>());
    synchronized (q) {
      while (!q.isEmpty() && now - q.peekFirst() > windowMs) q.pollFirst();
      if (q.size() >= max) {
        throw ApiException.tooMany("RATE_LIMITED", msg);
      }
      q.addLast(now);
    }
  }

  /** Stejné jako {@link #check(String, int, long, String)}, ale s oknem z konfigurace. */
  public void check(String key, int max, String msg) {
    check(key, max, props.getWindowMs(), msg);
  }
}