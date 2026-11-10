package cz.bazar.controller;

import cz.bazar.service.FavoriteService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/favorites")
public class FavoriteController {
  private final FavoriteService favorites;
  public FavoriteController(FavoriteService favorites) { this.favorites = favorites; }

  @GetMapping("/ids")
  public Map<String, Object> ids() {
    return Map.of("ids", favorites.ids());
  }

  @GetMapping("/mine")
  public Map<String, Object> mine() {
    List<Map<String, Object>> data = favorites.mine();
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("data", data);
    out.put("nextCursor", null);
    return out;
  }

  @PostMapping("/{listingId}")
  public ResponseEntity<Map<String, Object>> add(@PathVariable Long listingId) {
    return ResponseEntity.status(201).body(favorites.add(listingId));
  }

  @DeleteMapping("/{listingId}")
  public Map<String, Object> remove(@PathVariable Long listingId) {
    return favorites.remove(listingId);
  }
}
