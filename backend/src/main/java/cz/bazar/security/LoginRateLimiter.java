package cz.bazar.security;

import cz.bazar.exception.ApiException;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/** Rate-limit pro /auth/login: {@code app.rate-limit.login} pokusů / okno / IP. */
@Component
public class LoginRateLimiter {
  private final ConcurrentHashMap<String, Deque<Long>> buckets = new ConcurrentHashMap<>();
  private final RateLimitProperties props;

  public LoginRateLimiter(RateLimitProperties props) {
    this.props = props;
  }

  public void check(String ip) {
    if (!props.isEnabled()) return;
    long window = props.getWindowMs();
    int max = props.getLogin();
    long now = System.currentTimeMillis();
    Deque<Long> q = buckets.computeIfAbsent(ip == null ? "unknown" : ip, k -> new ArrayDeque<>());
    synchronized (q) {
      while (!q.isEmpty() && now - q.peekFirst() > window) q.pollFirst();
      if (q.size() >= max) {
        throw ApiException.tooMany("RATE_LIMITED", "Příliš mnoho pokusů o přihlášení. Zkuste to později.");
      }
      q.addLast(now);
    }
  }
}