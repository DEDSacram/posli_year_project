package cz.bazar.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.hibernate.exception.SQLGrammarException;
import org.springframework.stereotype.Service;
import cz.bazar.dto.Dtos.Page;
import cz.bazar.exception.ApiException;

import java.util.*;

import cz.bazar.domain.ListingImage;
import cz.bazar.domain.User;
import cz.bazar.repository.ListingImageRepository;
import cz.bazar.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class SearchService {
  private static final Set<String> SORT_ALLOW = Set.of("relevance", "newest", "priceAsc", "priceDesc");

  @PersistenceContext
  private EntityManager em;

  @Autowired
  private UserRepository users;

  @Autowired
  private ListingImageRepository images;

  /**
   * Vyhledavani dle design_doc kap. 8: BM25 (pdb.*) + earthdistance.
   * p_queries se vazou jako parametr, nikdy konkatenaci.
   */
  @SuppressWarnings("unchecked")
  public Page<Map<String, Object>> search(String q, String category, Integer minPrice, Integer maxPrice,
                                          Double lat, Double lon, Double radiusKm, String sort,
                                          Long cursor, int limit) {
    if (q != null && q.length() > 200) throw ApiException.badRequest("QUERY_TOO_LONG", "Dotaz je delší než 200 znaků.");
    if (sort == null) sort = "relevance";
    if (!SORT_ALLOW.contains(sort)) throw ApiException.badRequest("INVALID_SORT", "Neplatné řazení.");
    limit = Math.min(Math.max(limit, 1), 50);
    if ((lat != null || lon != null || radiusKm != null)) {
      if (lat == null || lon == null || radiusKm == null)
        throw ApiException.badRequest("INVALID_GEO", "Pro filtr polohy zadejte lat, lon i radiusKm.");
      if (lat < -90 || lat > 90 || lon < -180 || lon > 180)
        throw ApiException.badRequest("INVALID_GEO", "Neplatné souřadnice.");
      if (radiusKm <= 0 || radiusKm > 2000)
        throw ApiException.badRequest("INVALID_RADIUS", "Poloměr musí být 0–2000 km.");
    }

    String orderBy = switch (sort) {
      case "priceAsc" -> "l.price_czk asc, l.id desc";
      case "priceDesc" -> "l.price_czk desc, l.id desc";
      case "newest" -> "l.id desc";
      default -> "score desc, l.id desc";
    };

    // kurzor (key,id): jednodussi keyset pres id
    String cursorCond = cursor == null ? "" : " and l.id < :cursor ";
    boolean hasQ = q != null && !q.isBlank();
    // ParadeDB 0.25 neumí OR-short-circuit s prázdným parse → BM25 klauzule jen při neprázdném q.
    // BM25 hledá celá slova ("play" nenajde "playstation"), proto přidáváme ILIKE podřetězec
    // na titul/popis/kategorii/město — částečný dotaz tak vždy něco najde.
    String scoreCol = hasQ
        ? "case when l.id @@@ paradedb.parse(CAST(:q AS TEXT), lenient => true) then paradedb.score(l.id) else 0.1 end as score"
        : "0.0 as score";
    String bm25Cond = hasQ
        ? " and (l.id @@@ paradedb.parse(CAST(:q AS TEXT), lenient => true)"
            + " or l.title ilike '%' || CAST(:like AS TEXT) || '%'"
            + " or l.description ilike '%' || CAST(:like AS TEXT) || '%'"
            + " or l.category ilike '%' || CAST(:like AS TEXT) || '%'"
            + " or l.city ilike '%' || CAST(:like AS TEXT) || '%')"
        : "";

    String sql = """
        select l.id, l.seller_id, l.title, l.description, l.price_czk, l.category,
               l.condition, l.city, l.lat, l.lon, l.seller_type_snapshot, l.status, l.created_at,
               """ + scoreCol + "\n" + """
        from listings l
        where l.status = 'active'""" + bm25Cond + """
          and (CAST(:cat AS TEXT) is null or l.category = CAST(:cat AS TEXT))
          and l.price_czk between CAST(:min AS INTEGER) and CAST(:max AS INTEGER)
          and (CAST(:lat AS DOUBLE PRECISION) is null or (l.lat is not null and l.lon is not null and earth_distance(
                ll_to_earth(l.lat, l.lon), ll_to_earth(CAST(:lat AS DOUBLE PRECISION), CAST(:lon AS DOUBLE PRECISION))) <= CAST(:radiusM AS DOUBLE PRECISION)))
        """ + cursorCond + " order by " + orderBy + " limit :limit";

    try {
      Query query = em.createNativeQuery(sql);
      if (hasQ) {
        query.setParameter("q", q);
        query.setParameter("like", q.replace("\\", "").replace("%", "").replace("_", ""));
      }
      query.setParameter("cat", (category == null || category.isBlank()) ? null : category);
      query.setParameter("min", minPrice == null ? 0 : minPrice);
      query.setParameter("max", maxPrice == null ? Integer.MAX_VALUE : maxPrice);
      query.setParameter("lat", lat);
      query.setParameter("lon", lon);
      query.setParameter("radiusM", radiusKm == null ? null : radiusKm * 1000.0);
      if (cursor != null) query.setParameter("cursor", cursor);
      query.setParameter("limit", limit + 1);
      List<Object[]> rows = query.getResultList();
      return mapRows(rows, limit);
    } catch (Exception e) {
      // Fallback kdyz pg_search/earthdistance neni dostupna: bezpecny ILIKE + stejna pravidla
      return fallback(q, category, minPrice, maxPrice, lat, lon, radiusKm, sort, cursor, limit);
    }
  }

  @SuppressWarnings("unchecked")
  private Page<Map<String, Object>> fallback(String q, String category, Integer minPrice, Integer maxPrice,
                                             Double lat, Double lon, Double radiusKm, String sort,
                                             Long cursor, int limit) {
    String orderBy = switch (sort) {
      case "priceAsc" -> "l.price_czk asc, l.id desc";
      case "priceDesc" -> "l.price_czk desc, l.id desc";
      default -> "l.id desc";
    };
    String cursorCond = cursor == null ? "" : " and l.id < :cursor ";
    String sql = """
        select l.id, l.seller_id, l.title, l.description, l.price_czk, l.category,
               l.condition, l.city, l.lat, l.lon, l.seller_type_snapshot, l.status, l.created_at
        from listings l
        where l.status = 'active'
          and (CAST(:q AS TEXT) is null or CAST(:q AS TEXT) = '' or l.title ilike '%' || CAST(:q AS TEXT) || '%' or l.description ilike '%' || CAST(:q AS TEXT) || '%')
          and (CAST(:cat AS TEXT) is null or l.category = CAST(:cat AS TEXT))
          and l.price_czk between CAST(:min AS INTEGER) and CAST(:max AS INTEGER)
        """ + cursorCond + " order by " + orderBy + " limit :limit";
    Query query = em.createNativeQuery(sql);
    query.setParameter("q", q == null ? "" : q.replace("%", "").replace("_", ""));
    query.setParameter("cat", (category == null || category.isBlank()) ? null : category);
    query.setParameter("min", minPrice == null ? 0 : minPrice);
    query.setParameter("max", maxPrice == null ? Integer.MAX_VALUE : maxPrice);
    if (cursor != null) query.setParameter("cursor", cursor);
    query.setParameter("limit", limit + 1);
    List<Object[]> rows = query.getResultList();
    // geo-filtr v Jave pro fallback (Haversine)
    List<Object[]> filtered = new ArrayList<>();
    for (Object[] r : rows) {
      // poradi sloupcu fallback dotazu: 0 id,1 seller,2 title,3 desc,4 price,5 cat,6 cond,7 city,8 lat,9 lon
      if (lat != null && (r[8] == null || r[9] == null)) continue;
      if (lat != null) {
        double d = haversine(lat, lon, ((Number) r[8]).doubleValue(), ((Number) r[9]).doubleValue());
        if (d > radiusKm) continue;
      }
      filtered.add(r);
    }
    // fallback rows nemaji score sloupec -> doplnime 0
    List<Object[]> withScore = new ArrayList<>();
    for (Object[] r : filtered) {
      Object[] nr = Arrays.copyOf(r, r.length + 1);
      nr[r.length] = 0.0;
      withScore.add(nr);
    }
    return mapRows(withScore, limit);
  }

  private Page<Map<String, Object>> mapRows(List<Object[]> rows, int limit) {
    String next = null;
    List<Object[]> page = rows;
    if (rows.size() > limit) {
      page = rows.subList(0, limit);
      next = String.valueOf(((Number) page.get(page.size() - 1)[0]).longValue());
    }
    List<Map<String, Object>> data = new ArrayList<>();
    Map<Long, String> names = sellerNames(page);
    Map<Long, List<Map<String, Object>>> imagesForId = imagesFor(page);
    for (Object[] r : page) {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("id", ((Number) r[0]).longValue());
      m.put("sellerId", ((Number) r[1]).longValue());
      m.put("sellerName", names.get(((Number) r[1]).longValue()));
      m.put("title", r[2]);
      m.put("description", r[3]);
      m.put("priceCzk", ((Number) r[4]).intValue());
      m.put("category", r[5]);
      m.put("condition", r[6]);
      m.put("city", r[7] == null ? null : r[7]);
      // pozn: pri nativnim selectu s paradedb.score je poradi: ..., city, lat, lon, snapshot...
      // fallback i primarni dotaz maji stejne poradi do snapshotu
      m.put("lat", r[8]);
      m.put("lon", r[9]);
      m.put("sellerType", r[10]);
      m.put("status", r[11]);
      m.put("createdAt", r[12]);
      m.put("images", imagesForId.getOrDefault(((Number) r[0]).longValue(), List.of()));
      data.add(m);
    }
    return new Page<>(data, next);
  }

  private Map<Long, String> sellerNames(List<Object[]> rows) {
    Set<Long> ids = new HashSet<>();
    for (Object[] r : rows) ids.add(((Number) r[1]).longValue());
    Map<Long, String> out = new HashMap<>();
    if (!ids.isEmpty()) {
      for (User u : users.findAllById(ids)) out.put(u.getId(), u.getDisplayName());
    }
    return out;
  }

  private Map<Long, List<Map<String, Object>>> imagesFor(List<Object[]> rows) {
    Set<Long> ids = new HashSet<>();
    for (Object[] r : rows) ids.add(((Number) r[0]).longValue());
    Map<Long, List<Map<String, Object>>> out = new HashMap<>();
    if (ids.isEmpty()) return out;
    for (Long lid : ids) {
      List<Map<String, Object>> list = new ArrayList<>();
      for (ListingImage img : images.findByListingIdOrderBySortOrderAscIdAsc(lid)) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", img.getId());
        m.put("mime", img.getMime());
        m.put("sortOrder", img.getSortOrder());
        list.add(m);
      }
      out.put(lid, list);
    }
    return out;
  }

  private double haversine(double lat1, double lon1, double lat2, double lon2) {
    double R = 6371.0;
    double dLat = Math.toRadians(lat2 - lat1), dLon = Math.toRadians(lon2 - lon1);
    double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
        + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
        * Math.sin(dLon / 2) * Math.sin(dLon / 2);
    return 2 * R * Math.asin(Math.sqrt(a));
  }
}
