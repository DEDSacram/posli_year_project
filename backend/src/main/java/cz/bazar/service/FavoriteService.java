package cz.bazar.service;

import cz.bazar.domain.Favorite;
import cz.bazar.domain.Listing;
import cz.bazar.domain.User;
import cz.bazar.exception.ApiException;
import cz.bazar.repository.FavoriteRepository;
import cz.bazar.repository.ListingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Oblíbené inzeráty navázané na uživatelský profil (ne na zařízení). */
@Service
public class FavoriteService {
  private final FavoriteRepository favorites;
  private final ListingRepository listings;
  private final ListingService listingService;
  private final CurrentUserService current;

  public FavoriteService(FavoriteRepository favorites, ListingRepository listings,
                         ListingService listingService, CurrentUserService current) {
    this.favorites = favorites; this.listings = listings;
    this.listingService = listingService; this.current = current;
  }

  @Transactional(readOnly = true)
  public List<Long> ids() {
    User me = current.require();
    return favorites.findByUserIdOrderByIdDesc(me.getId()).stream()
        .map(Favorite::getListingId).toList();
  }

  /** Plné inzeráty + pahýly {id, removed:true} pro smazané/odstraněné. */
  @Transactional(readOnly = true)
  public List<Map<String, Object>> mine() {
    User me = current.require();
    List<Map<String, Object>> out = new ArrayList<>();
    for (Favorite f : favorites.findByUserIdOrderByIdDesc(me.getId())) {
      Listing l = listings.findById(f.getListingId()).orElse(null);
      if (l == null || "removed".equals(l.getStatus()) || "deleted".equals(l.getStatus())) {
        Map<String, Object> stub = new LinkedHashMap<>();
        stub.put("id", f.getListingId());
        stub.put("removed", true);
        out.add(stub);
      } else {
        Map<String, Object> m = listingService.toMap(l);
        // Zlevnění od uložení (badge v Oblíbených).
        if (f.getPriceAtSave() != null && l.getPriceCzk() < f.getPriceAtSave()) {
          m.put("savedPriceCzk", f.getPriceAtSave());
          m.put("priceDrop", f.getPriceAtSave() - l.getPriceCzk());
        }
        out.add(m);
      }
    }
    return out;
  }

  @Transactional
  public Map<String, Object> add(Long listingId) {
    User me = current.require();
    Listing l = listings.findById(listingId)
        .orElseThrow(() -> ApiException.notFound("LISTING_NOT_FOUND", "Inzerát nenalezen."));
    if ("removed".equals(l.getStatus()))
      throw ApiException.notFound("LISTING_REMOVED", "Inzerát byl odstraněn moderací.");
    if ("deleted".equals(l.getStatus()))
      throw ApiException.notFound("LISTING_NOT_FOUND", "Inzerát nenalezen.");
    if (!favorites.existsByUserIdAndListingId(me.getId(), listingId)) {
      Favorite f = new Favorite();
      f.setUserId(me.getId());
      f.setListingId(listingId);
      f.setCreatedAt(OffsetDateTime.now());
      f.setPriceAtSave(l.getPriceCzk());
      favorites.save(f);
    } else {
      // Starší srdíčko bez uložené ceny — doplnit pro badge zlevnění.
      favorites.findByUserIdAndListingId(me.getId(), listingId).ifPresent(f -> {
        if (f.getPriceAtSave() == null) {
          f.setPriceAtSave(l.getPriceCzk());
          favorites.save(f);
        }
      });
    }
    return Map.of("favorited", true, "listingId", listingId);
  }

  @Transactional
  public Map<String, Object> remove(Long listingId) {
    User me = current.require();
    favorites.deleteByUserIdAndListingId(me.getId(), listingId);
    return Map.of("favorited", false, "listingId", listingId);
  }
}
