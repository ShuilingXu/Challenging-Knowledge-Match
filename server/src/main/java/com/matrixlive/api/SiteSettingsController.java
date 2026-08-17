package com.matrixlive.api;

import static com.matrixlive.api.ApiModels.*;

import com.matrixlive.service.SiteSettingsService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SiteSettingsController {
  private final SiteSettingsService service;

  public SiteSettingsController(SiteSettingsService service) { this.service = service; }

  @GetMapping("/api/site-settings")
  public SiteSettingsResponse get() { return service.get(); }

  @GetMapping("/api/admin/site-settings")
  public AdminSiteSettingsResponse getAdmin() { return service.getAdmin(); }

  @PatchMapping("/api/admin/site-settings")
  public AdminSiteSettingsResponse update(@Valid @RequestBody UpdateSiteSettingsRequest request) {
    return service.update(request);
  }
}
