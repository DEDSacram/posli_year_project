package cz.bazar.controller;

import cz.bazar.service.GeoService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/geo")
public class GeoController {
  private final GeoService geo;
  public GeoController(GeoService geo) { this.geo = geo; }

  @GetMapping("/zip")
  public Map<String, Object> zip(@RequestParam String zip,
      @RequestParam(defaultValue = "cz") String country) {
    return geo.zipToCoords(zip, country);
  }

  @GetMapping("/reverse")
  public Map<String, Object> reverse(@RequestParam double lat, @RequestParam double lon) {
    return geo.reverse(lat, lon);
  }
}
