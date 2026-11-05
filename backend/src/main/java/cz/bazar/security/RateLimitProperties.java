package cz.bazar.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Limity na zapisové endpointy (ochrana proti zneužití a spamu).
 * Konfigurovatelné přes {@code app.rate-limit.*}, aby je šlo doladit bez zásahu do kódu;
 * v testech se vypnou ({@code enabled=false}).
 */
@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {
  /** Vypnutí všech limitů — používá se jen v automatizovaných testech. */
  private boolean enabled = true;
  /** Přihlášení na IP. */
  private int login = 10;
  /** Registrace na IP. */
  private int register = 5;
  /** Zprávy v chatu na uživatele. */
  private int chat = 30;
  /** Hlášení na uživatele. */
  private int report = 10;
  /** Rezervace na uživatele. */
  private int reservation = 20;
  /** Okno v milisekundách (klouzavé). */
  private long windowMs = 60_000L;

  public boolean isEnabled() { return enabled; }
  public void setEnabled(boolean enabled) { this.enabled = enabled; }
  public int getLogin() { return login; }
  public void setLogin(int login) { this.login = login; }
  public int getRegister() { return register; }
  public void setRegister(int register) { this.register = register; }
  public int getChat() { return chat; }
  public void setChat(int chat) { this.chat = chat; }
  public int getReport() { return report; }
  public void setReport(int report) { this.report = report; }
  public int getReservation() { return reservation; }
  public void setReservation(int reservation) { this.reservation = reservation; }
  public long getWindowMs() { return windowMs; }
  public void setWindowMs(long windowMs) { this.windowMs = windowMs; }
}