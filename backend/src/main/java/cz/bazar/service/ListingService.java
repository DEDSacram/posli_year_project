package cz.bazar.service;

import cz.bazar.domain.Listing;
import cz.bazar.domain.ListingImage;
import cz.bazar.domain.User;
import cz.bazar.dto.Dtos.*;
import cz.bazar.exception.ApiException;
import cz.bazar.repository.ListingImageRepository;
import cz.bazar.repository.ListingRepository;
import cz.bazar.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.OffsetDateTime;
import java.util.*;
import javax.imageio.ImageIO;

@Service
public class ListingService {
  private static final Set<String> ALLOWED_MIME = Set.of("image/jpeg", "image/png", "image/webp");
  private final ListingRepository listings;
  private final ListingImageRepository images;
  private final SellerService sellers;
  private final CurrentUserService current;
  private final UserRepository users;
  private final NotificationService notificationService;

  public ListingService(ListingRepository listings, ListingImageRepository images,
                        SellerService sellers, CurrentUserService current, UserRepository users,
                        NotificationService notificationService) {
    this.listings = listings; this.images = images; this.sellers = sellers; this.current = current;
    this.users = users; this.notificationService = notificationService;
  }

  @Transactional
  public Listing create(ListingCreate r) {
    User me = current.require();
    sellers.requirePublishable(me);
    Listing l = new Listing();
    l.setSellerId(me.getId());
    l.setTitle(r.title().strip());
    l.setDescription(r.description().strip());
    l.setPriceCzk(r.priceCzk());
    l.setCategory(r.category());
    l.setConditionValue(r.condition());
    l.setCity(r.city());
    // Poloha jen se souhlasem (explicitne predana lat/lon), nikdy z DAC7 adresy
    if (r.lat() != null || r.lon() != null) {
      if (r.lat() == null || r.lon() == null) throw ApiException.badRequest("INVALID_GEO", "Zeměpisná šířka i délka musí být zadány společně.");
      if (r.lat() < -90 || r.lat() > 90 || r.lon() < -180 || r.lon() > 180)
        throw ApiException.badRequest("INVALID_GEO", "Neplatné souřadnice.");
      l.setLat(r.lat()); l.setLon(r.lon());
    }
    l.setSellerTypeSnapshot(me.getSellerType());
    l.setStatus("active");
    l.setCreatedAt(OffsetDateTime.now());
    return listings.save(l);
  }

  @Transactional
  public Listing update(Long id, ListingUpdate r) {
    User me = current.require();
    Listing l = listings.findById(id).orElseThrow(() -> ApiException.notFound("LISTING_NOT_FOUND", "Inzerát nenalezen."));
    if (!l.getSellerId().equals(me.getId())) throw ApiException.forbidden("NOT_OWNER", "Nejste vlastníkem inzerátu.");
    if (!"active".equals(l.getStatus())) throw ApiException.badRequest("LISTING_NOT_ACTIVE", "Lze upravit jen aktivní inzerát.");
    if (r.title() != null) {
      if (r.title().strip().isEmpty()) throw ApiException.badRequest("INVALID_TITLE", "Název nesmí být prázdný.");
      l.setTitle(r.title().strip());
    }
    if (r.description() != null) {
      if (r.description().strip().isEmpty()) throw ApiException.badRequest("INVALID_DESCRIPTION", "Popis nesmí být prázdný.");
      l.setDescription(r.description().strip());
    }
    if (r.priceCzk() != null) {
      if (r.priceCzk() < 1) throw ApiException.badRequest("INVALID_PRICE", "Cena musí být alespoň 1 Kč.");
      int oldPrice = l.getPriceCzk();
      l.setPriceCzk(r.priceCzk());
      // Upozornění na zlevnění těm, kdo mají inzerát v oblíbených (jen při poklesu ceny).
      if (r.priceCzk() < oldPrice) {
        notificationService.notifyPriceDrop(l, oldPrice, r.priceCzk());
      }
    }
    if (r.category() != null) {
      if (r.category().strip().isEmpty()) throw ApiException.badRequest("INVALID_CATEGORY", "Vyberte kategorii.");
      l.setCategory(r.category().strip());
    }
    if (r.condition() != null) {
      if (r.condition().strip().isEmpty()) throw ApiException.badRequest("INVALID_CONDITION", "Vyberte stav.");
      l.setConditionValue(r.condition().strip());
    }
    if (r.city() != null) {
      if (r.city().strip().isEmpty()) throw ApiException.badRequest("INVALID_CITY", "Zadejte město.");
      l.setCity(r.city().strip());
    }
    // Poloha jen jako pár, ve validním rozsahu (stejně jako při vytvoření).
    if (r.lat() != null || r.lon() != null) {
      if (r.lat() == null || r.lon() == null)
        throw ApiException.badRequest("INVALID_GEO", "Zeměpisná šířka i délka musí být zadány společně.");
      if (r.lat() < -90 || r.lat() > 90 || r.lon() < -180 || r.lon() > 180)
        throw ApiException.badRequest("INVALID_GEO", "Neplatné souřadnice.");
      l.setLat(r.lat()); l.setLon(r.lon());
    }
    return listings.save(l);
  }

  @Transactional
  public void delete(Long id) {
    User me = current.require();
    Listing l = listings.findById(id).orElseThrow(() -> ApiException.notFound("LISTING_NOT_FOUND", "Inzerát nenalezen."));
    if (!l.getSellerId().equals(me.getId())) throw ApiException.forbidden("NOT_OWNER", "Nejste vlastníkem inzerátu.");
    l.setStatus("deleted"); // soft-delete, transakce zustavaji
    listings.save(l);
  }

  public Listing get(Long id) {
    Listing l = listings.findById(id).orElseThrow(() -> ApiException.notFound("LISTING_NOT_FOUND", "Inzerát nenalezen."));
    if ("removed".equals(l.getStatus())) throw ApiException.notFound("LISTING_REMOVED", "Inzerát byl odstraněn moderací.");
    if ("deleted".equals(l.getStatus())) throw ApiException.notFound("LISTING_NOT_FOUND", "Inzerát nenalezen.");
    return l;
  }

  public RightsInfo rightsInfo(Long id) {
    Listing l = get(id);
    boolean trader = "trader".equals(l.getSellerTypeSnapshot());
    String cs = trader
        ? "Prodejce je obchodník – máte 14denní právo na odstoupení od smlouvy dle § 1829 OZ (není-li výjimka dle § 1837)."
        : "Prodejce je soukromá osoba – 14denní odstoupení se neuplatní.";
    return new RightsInfo(trader, l.getSellerTypeSnapshot(), cs);
  }

  @Transactional
  public ListingImage addImage(Long listingId, MultipartFile file) {
    User me = current.require();
    Listing l = listings.findById(listingId).orElseThrow(() -> ApiException.notFound("LISTING_NOT_FOUND", "Inzerát nenalezen."));
    if (!l.getSellerId().equals(me.getId())) throw ApiException.forbidden("NOT_OWNER", "Nejste vlastníkem inzerátu.");
    if (file == null || file.isEmpty()) throw ApiException.badRequest("EMPTY_FILE", "Soubor je prázdný.");
    if (file.getSize() > 5 * 1024 * 1024) throw ApiException.badRequest("FILE_TOO_LARGE", "Obrázek je větší než 5 MB.");
    String mime = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
    if (!ALLOWED_MIME.contains(mime)) throw ApiException.badRequest("INVALID_MIME", "Povolen je pouze image/jpeg, image/png nebo image/webp.");
    try {
      byte[] bytes = stripMetadata(file.getBytes(), mime);
      ListingImage img = new ListingImage();
      img.setListingId(listingId);
      img.setData(bytes);
      img.setMime(mime);
      List<ListingImage> existing = images.findByListingIdOrderBySortOrderAscIdAsc(listingId);
      img.setSortOrder(existing.size());
      return images.save(img);
    } catch (ApiException e) { throw e; }
    catch (Exception e) { throw ApiException.badRequest("UPLOAD_FAILED", "Nahrání obrázku selhalo."); }
  }

  /**
   * EXIF/GPS strip: dekóduje pixely a znovu zakóduje bez metadat
   * (ImageIO metadata při zápisu nepřenáší). Formáty bez dekodéru
   * v JDK (webp) se uloží beze změny.
   */
  static byte[] stripMetadata(byte[] bytes, String mime) {
    String format = mime.endsWith("png") ? "png" : mime.endsWith("webp") ? "webp" : "jpeg";
    // webp nemá v JDK dekodér — ukládá se beze změny (EXIF riziko viz README/admin).
    if ("webp".equals(format)) return bytes;
    try {
      BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
      // jpeg/png JDK dekódovat umí — null = falšované MIME, ne obrázek.
      if (img == null) throw ApiException.badRequest("INVALID_IMAGE", "Soubor není platný obrázek.");
      if (img.getWidth() <= 0 || img.getHeight() <= 0
          || img.getWidth() > 8000 || img.getHeight() > 8000) {
        throw ApiException.badRequest("INVALID_IMAGE", "Obrázek má neplatné rozměry.");
      }
      ByteArrayOutputStream out = new ByteArrayOutputStream(bytes.length);
      if (!ImageIO.write(img, format, out)) return bytes;
      return out.toByteArray();
    } catch (ApiException e) { throw e; }
    catch (Exception e) {
      throw ApiException.badRequest("INVALID_IMAGE", "Soubor není platný obrázek.");
    }
  }

  @Transactional
  public void deleteImage(Long listingId, Long imgId) {
    User me = current.require();
    Listing l = listings.findById(listingId).orElseThrow(() -> ApiException.notFound("LISTING_NOT_FOUND", "Inzerát nenalezen."));
    if (!l.getSellerId().equals(me.getId())) throw ApiException.forbidden("NOT_OWNER", "Nejste vlastníkem inzerátu.");
    ListingImage img = images.findById(imgId).orElseThrow(() -> ApiException.notFound("IMAGE_NOT_FOUND", "Obrázek nenalezen."));
    if (!img.getListingId().equals(listingId)) throw ApiException.badRequest("IMAGE_MISMATCH", "Obrázek nepatří k inzerátu.");
    images.delete(img);
  }

  @Transactional(readOnly = true)
  public ListingImage getImage(Long listingId, Long imgId) {
    ListingImage img = images.findById(imgId)
        .orElseThrow(() -> ApiException.notFound("IMAGE_NOT_FOUND", "Obrázek nenalezen."));
    if (!img.getListingId().equals(listingId))
      throw ApiException.badRequest("IMAGE_MISMATCH", "Obrázek nepatří k inzerátu.");
    // Smazané/odstraněné inzeráty nevystavovat ani přes přímé URL obrázků.
    listings.findById(listingId).ifPresent(l -> {
      if ("removed".equals(l.getStatus()) || "deleted".equals(l.getStatus()))
        throw ApiException.notFound("LISTING_NOT_FOUND", "Inzerát nenalezen.");
    });
    return img;
  }

  public Page<Map<String, Object>> feed(Long cursor, int limit) {
    limit = Math.min(Math.max(limit, 1), 50);
    List<Listing> rows = cursor == null
        ? listings.findByStatusOrderByIdDesc("active", PageRequest.of(0, limit + 1))
        : listings.findByStatusAndIdLessThanOrderByIdDesc("active", cursor, PageRequest.of(0, limit + 1));
    return toPage(rows, limit);
  }

  public Page<Map<String, Object>> toPage(List<Listing> rows, int limit) {
    String next = null;
    List<Listing> page = rows;
    if (rows.size() > limit) {
      page = rows.subList(0, limit);
      next = String.valueOf(page.get(page.size() - 1).getId());
    }
    List<Map<String, Object>> data = page.stream().map(this::toMap).toList();
    return new Page<>(data, next);
  }

  /** Rychlé doplnění formuláře: odhad kategorie z názvu + medián ceny v kategorii. */
  @Transactional(readOnly = true)
  public Map<String, Object> suggest(String title, String category) {
    String guess = guessCategory(title);
    String cat = (category != null && !category.isBlank()) ? category.strip() : guess;
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("categoryGuess", guess);
    out.put("category", cat);
    if (cat == null || cat.isBlank()) {
      out.put("medianPriceCzk", null);
      out.put("sampleSize", 0);
      return out;
    }
    List<Listing> rows = listings.findByStatusAndCategory("active", cat,
        PageRequest.of(0, 200));
    List<Integer> prices = rows.stream().map(Listing::getPriceCzk)
        .filter(Objects::nonNull).sorted().toList();
    out.put("sampleSize", prices.size());
    out.put("medianPriceCzk", prices.isEmpty() ? null : prices.get(prices.size() / 2));
    return out;
  }

  private static final List<Map.Entry<String, List<String>>> CATEGORY_KEYWORDS = List.of(
      Map.entry("elektronika", List.of("iphone", "telefon", "mobil", "notebook", "laptop", "pc", "monitor",
          "televize", "tv", "konzole", "playstation", "xbox", "nintendo", "sluchátk", "sluchatk",
          "reproduktor", "fotoaparát", "fotoaparat", "kamera", "tiskárn", "tiskarn", "tablet", "ipad",
          "nabíječk", "nabijeck", "klávesnic", "klavesnic", "myš", "apple", "samsung")),
      Map.entry("sport", List.of("kolo", "koloběžk", "kolobezk", "lyž", "lyz", "snowboard", "brusl",
          "fotbal", "hokej", "tenis", "posilov", "čink", "cink", "stan", "kemp", "turistik",
          "batoh", "golf", "kajak", "paddleboard", "běžeck", "bezeck", "fitness")),
      Map.entry("nabytok", List.of("stůl", "stul", "židl", "zidl", "skříň", "skrin", "postel",
          "pohovka", "gauč", "gauc", "křesl", "kresl", "komoda", "police", "polic", "nábyt",
          "nabyt", "lamp", "kuchyň", "kuchyn", "matrac", "koberec")),
      Map.entry("moda", List.of("bunda", "kabát", "kabat", "kalhot", "džín", "dzin", "tričk", "trick",
          "mikina", "šat", "sukn", "bot", "tenisk", "lodičk", "lodick", "kabelk", "brašn", "brasn",
          "hodink", "brýl", "bryl", "vintage", "kožen", "kozen", "nike", "adidas", "zara")),
      Map.entry("auto", List.of("auto", "motork", "motocykl", "pneu", "kola alu", "disk", "náhradn",
          "nahradn", "střech", "strech", "nosíč", "nosic", "autosedačk", "autoseda", "škoda",
          "skoda", "vw", "bmw", "audi", "ford", "kol ", "pneumatik")),
      Map.entry("dum", List.of("sekačk", "sekack", "vrtačk", "vrtack", "pila", "nářad", "narad",
          "gril", "bazén", "bazen", "květin", "kvetin", "zahrad", "trávník", "travnik", "kotel",
          "krb", "pračk", "prack", "lednic", "mrazák", "mrazak", "sporák", "sporak", "nádob",
          "nadob", "vysavač", "vysavac")));

  static String guessCategory(String title) {
    if (title == null || title.isBlank()) return "";
    String t = title.strip().toLowerCase();
    String best = "";
    int bestScore = 0;
    for (var e : CATEGORY_KEYWORDS) {
      int score = 0;
      for (String kw : e.getValue()) {
        if (t.contains(kw)) score += kw.length() >= 5 ? 2 : 1;
      }
      if (score > bestScore) { bestScore = score; best = e.getKey(); }
    }
    return bestScore > 0 ? best : "ostatni";
  }

  public Map<String, Object> toMap(Listing l) {    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", l.getId());
    m.put("sellerId", l.getSellerId());
    m.put("sellerName", users.findById(l.getSellerId()).map(User::getDisplayName).orElse(null));
    m.put("title", l.getTitle());
    m.put("description", l.getDescription());
    m.put("priceCzk", l.getPriceCzk());
    m.put("category", l.getCategory());
    m.put("condition", l.getConditionValue());
    m.put("city", l.getCity());
    m.put("lat", l.getLat());
    m.put("lon", l.getLon());
    m.put("sellerType", l.getSellerTypeSnapshot());
    m.put("status", l.getStatus());
    m.put("createdAt", l.getCreatedAt());
    m.put("images", images.findByListingIdOrderBySortOrderAscIdAsc(l.getId()).stream()
        .map(i -> Map.of("id", i.getId(), "mime", i.getMime(), "sortOrder", i.getSortOrder())).toList());
    return m;
  }
}
