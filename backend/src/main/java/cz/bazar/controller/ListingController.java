package cz.bazar.controller;

import cz.bazar.dto.Dtos.*;
import cz.bazar.service.ListingService;
import cz.bazar.service.SearchService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/listings")
public class ListingController {
  private final ListingService listings;
  private final SearchService search;

  public ListingController(ListingService listings, SearchService search) {
    this.listings = listings; this.search = search;
  }

  @PostMapping
  public ResponseEntity<Map<String, Object>> create(@Valid @RequestBody ListingCreate r) {
    var l = listings.create(r);
    return ResponseEntity.status(201).body(listings.toMap(l));
  }

  @GetMapping("/{id}")
  public Map<String, Object> get(@PathVariable Long id) { return listings.toMap(listings.get(id)); }

  @PutMapping("/{id}")
  public Map<String, Object> update(@PathVariable Long id, @Valid @RequestBody ListingUpdate r) {
    return listings.toMap(listings.update(id, r));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Map<String, String>> delete(@PathVariable Long id) {
    listings.delete(id);
    return ResponseEntity.ok(Map.of("status", "deleted"));
  }

  @PostMapping("/{id}/images")
  public ResponseEntity<Map<String, Object>> addImage(@PathVariable Long id,
      @RequestParam("file") MultipartFile file) {
    var img = listings.addImage(id, file);
    return ResponseEntity.status(201).body(Map.of("id", img.getId(), "mime", img.getMime(), "sortOrder", img.getSortOrder()));
  }

  @DeleteMapping("/{id}/images/{imgId}")
  public ResponseEntity<Map<String, String>> deleteImage(@PathVariable Long id, @PathVariable Long imgId) {
    listings.deleteImage(id, imgId);
    return ResponseEntity.ok(Map.of("status", "deleted"));
  }

  @GetMapping("/{id}/images/{imgId}")
  public ResponseEntity<byte[]> getImage(@PathVariable Long id, @PathVariable Long imgId) {
    var img = listings.getImage(id, imgId);
    return ResponseEntity.ok()
        .contentType(org.springframework.http.MediaType.parseMediaType(img.getMime()))
        .body(img.getData());
  }

  @GetMapping("/{id}/rights-info")
  public RightsInfo rightsInfo(@PathVariable Long id) { return listings.rightsInfo(id); }

  @GetMapping("/search")
  public Page<Map<String, Object>> search(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String category,
      @RequestParam(required = false) Integer minPrice,
      @RequestParam(required = false) Integer maxPrice,
      @RequestParam(required = false) Double lat,
      @RequestParam(required = false) Double lon,
      @RequestParam(required = false) Double radiusKm,
      @RequestParam(defaultValue = "relevance") String sort,
      @RequestParam(required = false) Long cursor,
      @RequestParam(defaultValue = "20") int limit) {
    return search.search(q, category, minPrice, maxPrice, lat, lon, radiusKm, sort, cursor, limit);
  }

  @GetMapping("/feed")
  public Page<Map<String, Object>> feed(@RequestParam(required = false) Long cursor,
      @RequestParam(defaultValue = "20") int limit) {
    return listings.feed(cursor, limit);
  }

  @GetMapping("/suggest")
  public Map<String, Object> suggest(@RequestParam(required = false) String title,
      @RequestParam(required = false) String category) {
    return listings.suggest(title, category);
  }
}
