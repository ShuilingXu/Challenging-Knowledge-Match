package com.matrixlive.service;

import static com.matrixlive.api.ApiModels.*;

import com.matrixlive.domain.SiteSettings;
import com.matrixlive.media.StorageProperties;
import com.matrixlive.repository.SiteSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SiteSettingsService {
  private static final String DEFAULT_DOMAIN = "localhost";
  private static final String DEFAULT_NAME = "Matrix Live";

  private final SiteSettingsRepository repository;
  private final StorageProperties storageProperties;

  public SiteSettingsService(SiteSettingsRepository repository, StorageProperties storageProperties) {
    this.repository = repository;
    this.storageProperties = storageProperties;
  }

  @Transactional(readOnly = true)
  public SiteSettingsResponse get() { return toResponse(current()); }

  @Transactional
  public SiteSettingsResponse update(UpdateSiteSettingsRequest request) {
    SiteSettings settings = current();
    settings.update(value(request.domain(), settings.getDomain(), DEFAULT_DOMAIN),
        value(request.siteName(), settings.getSiteName(), DEFAULT_NAME),
        value(request.logoUrl(), settings.getLogoUrl(), null),
        value(request.footerCode(), settings.getFooterCode(), null),
        value(request.storageEndpoint(), settings.getStorageEndpoint(), storageProperties.getEndpoint()),
        value(request.storageBucket(), settings.getStorageBucket(), storageProperties.getBucket()));
    return toResponse(settings);
  }

  private SiteSettings current() {
    return repository.findById(1L).orElseGet(() -> repository.save(new SiteSettings(DEFAULT_DOMAIN, DEFAULT_NAME, null, null)));
  }

  private String value(String requested, String current, String fallback) {
    if (requested == null) return current == null ? fallback : current;
    String cleaned = requested.trim();
    return cleaned.isEmpty() ? fallback : cleaned;
  }

  private SiteSettingsResponse toResponse(SiteSettings settings) {
    return new SiteSettingsResponse(settings.getDomain(), settings.getSiteName(), settings.getLogoUrl(), settings.getFooterCode(),
        fallback(settings.getStorageEndpoint(), storageProperties.getEndpoint()),
        fallback(settings.getStorageBucket(), storageProperties.getBucket()));
  }

  private String fallback(String value, String defaultValue) {
    return value == null || value.isBlank() ? defaultValue : value;
  }
}
