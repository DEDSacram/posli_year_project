package cz.bazar.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import cz.bazar.exception.ApiException;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Prevod PSC na souradnice pro vzdalenostni vyhledavani (nikdo nebude vyplnovat lat/lon rucne).
 * Zdroj: Nominatim (OpenStreetMap), vysledky se cachuji v pameti.
 */
@Service
public class GeoService {
  private final HttpClient http = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(8))
      .build();
  private final ObjectMapper json = new ObjectMapper();
  private final ConcurrentHashMap<String, Map<String, Object>> cache = new ConcurrentHashMap<>();

  public Map<String, Object> zipToCoords(String zip, String countryCode) {
    String clean = zip == null ? "" : zip.replaceAll("\\s", "");
    if (!clean.matches("\\d{3,10}")) {
      throw ApiException.badRequest("INVALID_ZIP", "PSČ má obsahovat číslice (např. 110 00).");
    }
    String cc = (countryCode == null || countryCode.isBlank()) ? "cz" : countryCode.trim().toLowerCase();
    String key = cc + ":" + clean;
    Map<String, Object> hit = cache.get(key);
    if (hit != null) return hit;

    try {
      String url = "https://nominatim.openstreetmap.org/search?postalcode="
          + URLEncoder.encode(clean, StandardCharsets.UTF_8)
          + "&countrycodes=" + URLEncoder.encode(cc, StandardCharsets.UTF_8)
          + "&format=json&limit=1&addressdetails=1";
      HttpRequest req = HttpRequest.newBuilder(URI.create(url))
          .timeout(Duration.ofSeconds(8))
          .header("User-Agent", "poslitodal-bazar/1.0 (contact: admin@bazar.cz)")
          .header("Accept", "application/json")
          .GET().build();
      HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
      if (res.statusCode() != 200) {
        throw ApiException.badRequest("GEO_UNAVAILABLE", "Vyhledání PSČ se nezdařilo, zkuste to později.");
      }
      JsonNode arr = json.readTree(res.body());
      if (!arr.isArray() || arr.isEmpty()) {
        throw ApiException.notFound("ZIP_NOT_FOUND", "PSČ nebylo nalezeno.");
      }
      JsonNode first = arr.get(0);
      double lat = first.path("lat").asDouble(Double.NaN);
      double lon = first.path("lon").asDouble(Double.NaN);
      if (Double.isNaN(lat) || Double.isNaN(lon)) {
        throw ApiException.notFound("ZIP_NOT_FOUND", "PSČ nebylo nalezeno.");
      }
      String city = first.path("address").path("city").asText(
          first.path("address").path("town").asText(
          first.path("address").path("village").asText(
          first.path("display_name").asText(""))));
      Map<String, Object> out = new LinkedHashMap<>();
      out.put("zip", clean);
      out.put("city", city);
      out.put("lat", lat);
      out.put("lon", lon);
      cache.put(key, out);
      return out;
    } catch (ApiException e) {
      throw e;
    } catch (Exception e) {
      throw ApiException.badRequest("GEO_UNAVAILABLE", "Vyhledání PSČ se nezdařilo, zkuste to později.");
    }
  }

  /** Reverzní překlad souřadnic na město/PSČ (tlačítko Moje poloha, klik do mapy). */
  public Map<String, Object> reverse(double lat, double lon) {
    if (lat < -90 || lat > 90 || lon < -180 || lon > 180) {
      throw ApiException.badRequest("INVALID_GEO", "Neplatné souřadnice.");
    }
    String key = "r:" + Math.round(lat * 1000.0) / 1000.0 + "," + Math.round(lon * 1000.0) / 1000.0;
    Map<String, Object> hit = cache.get(key);
    if (hit != null) return hit;

    try {
      String url = "https://nominatim.openstreetmap.org/reverse?lat=" + lat + "&lon=" + lon
          + "&format=json&addressdetails=1&zoom=18&accept-language=cs";
      HttpRequest req = HttpRequest.newBuilder(URI.create(url))
          .timeout(Duration.ofSeconds(8))
          .header("User-Agent", "poslitodal-bazar/1.0 (contact: admin@bazar.cz)")
          .header("Accept", "application/json")
          .GET().build();
      HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
      if (res.statusCode() != 200) {
        throw ApiException.badRequest("GEO_UNAVAILABLE", "Rozpoznání místa se nezdařilo.");
      }
      JsonNode root = json.readTree(res.body());
      JsonNode addr = root.path("address");
      String city = firstNonBlank(
          addr.path("city").asText(""),
          addr.path("town").asText(""),
          addr.path("village").asText(""),
          addr.path("municipality").asText(""),
          addr.path("hamlet").asText(""));
      String zip = addr.path("postcode").asText("").replaceAll("\\s", "");
      Map<String, Object> out = new LinkedHashMap<>();
      out.put("city", city);
      out.put("zip", zip);
      out.put("lat", lat);
      out.put("lon", lon);
      cache.put(key, out);
      return out;
    } catch (ApiException e) {
      throw e;
    } catch (Exception e) {
      throw ApiException.badRequest("GEO_UNAVAILABLE", "Rozpoznání místa se nezdařilo.");
    }
  }

  private static String firstNonBlank(String... vals) {
    for (String v : vals) if (v != null && !v.isBlank()) return v;
    return "";
  }
}
