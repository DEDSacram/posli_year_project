package cz.bazar.service;

import cz.bazar.domain.Consent;
import cz.bazar.domain.User;
import cz.bazar.dto.Dtos.*;
import cz.bazar.exception.ApiException;
import cz.bazar.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;

@Service
public class GdprService {
  private final UserRepository users;
  private final SellerTaxProfileRepository profiles;
  private final ListingRepository listings;
  private final ConsentRepository consents;
  private final FavoriteRepository favorites;
  private final CurrentUserService current;

  public GdprService(UserRepository users, SellerTaxProfileRepository profiles,
                     ListingRepository listings, ConsentRepository consents,
                     FavoriteRepository favorites, CurrentUserService current) {
    this.users = users; this.profiles = profiles; this.listings = listings;
    this.consents = consents; this.favorites = favorites; this.current = current;
  }

  @Transactional
  public Consent consent(ConsentCreate r) {
    User me = current.require();
    Consent c = new Consent();
    c.setUserId(me.getId());
    c.setPurpose(r.purpose());
    c.setGranted(r.granted());
    c.setCreatedAt(OffsetDateTime.now());
    return consents.save(c);
  }

  public Map<String, Object> exportMe() {
    User me = current.require();
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", me.getId());
    m.put("email", me.getEmail());
    m.put("displayName", me.getDisplayName());
    m.put("sellerType", me.getSellerType());
    m.put("createdAt", me.getCreatedAt());
    m.put("hasTaxProfile", profiles.findById(me.getId()).isPresent());
    m.put("consents", consents.findByUserIdOrderByIdDesc(me.getId()).stream().map(c ->
        Map.of("purpose", c.getPurpose(), "granted", c.isGranted(), "createdAt", String.valueOf(c.getCreatedAt()))).toList());
    m.put("listings", listings.findBySellerIdOrderByIdDesc(me.getId(), org.springframework.data.domain.PageRequest.of(0, 100))
        .stream().map(l -> Map.of("id", l.getId(), "title", l.getTitle(), "status", l.getStatus())).toList());
    m.put("favorites", favorites.findByUserIdOrderByIdDesc(me.getId()).stream()
        .map(f -> f.getListingId()).toList());
    return m; // prenositelnost dat; danove udaje jen na vyzadani prislusneho organu
  }

  @Transactional
  public void softDeleteMe() {
    User me = current.require();
    me.setDeletedAt(OffsetDateTime.now());
    me.setDisplayName("Smazaný uživatel");
    users.save(me);
    favorites.deleteByUserId(me.getId()); // právo na výmaz; danovy profil se uchovava (DAC7 retence)
  }
}
