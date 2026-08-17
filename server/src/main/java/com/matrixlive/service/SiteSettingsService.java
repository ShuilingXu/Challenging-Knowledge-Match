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
  public SiteSettingsResponse get() { return toPublicResponse(current()); }

  @Transactional(readOnly = true)
  public AdminSiteSettingsResponse getAdmin() { return toAdminResponse(current()); }

  @Transactional
  public AdminSiteSettingsResponse update(UpdateSiteSettingsRequest request) {
    SiteSettings settings = current();
    boolean clearCredentials = Boolean.TRUE.equals(request.clearStorageCredentials());
    settings.update(value(request.domain(), settings.getDomain(), DEFAULT_DOMAIN),
        value(request.siteName(), settings.getSiteName(), DEFAULT_NAME),
        value(request.logoUrl(), settings.getLogoUrl(), null),
        value(request.footerCode(), settings.getFooterCode(), null),
        request.storageEnabled(),
        storageInput(request.storageEndpoint(), settings.getStorageEndpoint(), storageProperties.getEndpoint()),
        value(request.storageRegion(), settings.getStorageRegion(), storageProperties.getRegion()),
        value(request.storageBucket(), settings.getStorageBucket(), storageProperties.getBucket()),
        secretValue(request.storageAccessKey(), settings.getStorageAccessKey(), clearCredentials),
        secretValue(request.storageSecretKey(), settings.getStorageSecretKey(), clearCredentials),
        secretValue(request.storageSessionToken(), settings.getStorageSessionToken(), clearCredentials),
        storageInput(request.storagePublicBaseUrl(), settings.getStoragePublicBaseUrl(), storageProperties.getPublicBaseUrl()),
        addressingStyle(request.storageAddressingStyle(), settings.getStorageAddressingStyle(), storageProperties.getAddressingStyle()));
    return toAdminResponse(settings);
  }

  @Transactional(readOnly = true)
  public StorageConfiguration storageConfiguration() {
    SiteSettings settings = current();
    return new StorageConfiguration(
        settings.getStorageEnabled() == null ? storageProperties.isEnabled() : settings.getStorageEnabled(),
        fallback(settings.getStorageEndpoint(), storageProperties.getEndpoint()),
        fallback(settings.getStorageRegion(), storageProperties.getRegion()),
        fallback(settings.getStorageBucket(), storageProperties.getBucket()),
        fallback(settings.getStorageAccessKey(), storageProperties.getAccessKey()),
        fallback(settings.getStorageSecretKey(), storageProperties.getSecretKey()),
        fallback(settings.getStorageSessionToken(), storageProperties.getSessionToken()),
        fallback(settings.getStoragePublicBaseUrl(), storageProperties.getPublicBaseUrl()),
        addressingStyle(null, settings.getStorageAddressingStyle(), storageProperties.getAddressingStyle()));
  }

  private SiteSettings current() {
    return repository.findById(1L).orElseGet(() -> repository.save(new SiteSettings(DEFAULT_DOMAIN, DEFAULT_NAME, null, null)));
  }

  private String value(String requested, String current, String fallback) {
    if (requested == null) return current == null ? fallback : current;
    String cleaned = requested.trim();
    return cleaned.isEmpty() ? fallback : cleaned;
  }

  private String storageInput(String requested, String current, String fallback) {
    if (requested == null) return current == null ? fallback : current;
    return requested.trim();
  }

  private String secretValue(String requested, String current, boolean clearCredentials) {
    // A newly entered credential wins even when the operator also checked the
    // clear box. This makes rotation a single atomic settings update; an
    // empty value with clear=true still removes the stored credential.
    if (requested != null && !requested.isBlank()) return requested.trim();
    return clearCredentials ? null : current;
  }

  private SiteSettingsResponse toPublicResponse(SiteSettings settings) {
    return new SiteSettingsResponse(settings.getDomain(), settings.getSiteName(), settings.getLogoUrl(), settings.getFooterCode());
  }

  private AdminSiteSettingsResponse toAdminResponse(SiteSettings settings) {
    StorageConfiguration storage = storageConfiguration(settings);
    return new AdminSiteSettingsResponse(settings.getDomain(), settings.getSiteName(), settings.getLogoUrl(), settings.getFooterCode(),
        storage.enabled(), storage.endpoint(), storage.region(), storage.bucket(), storage.accessKey(),
        hasText(storage.secretKey()), hasText(storage.sessionToken()), storage.publicBaseUrl(), storage.addressingStyle());
  }

  private String fallback(String value, String defaultValue) {
    return value == null || value.isBlank() ? defaultValue : value;
  }

  private StorageConfiguration storageConfiguration(SiteSettings settings) {
    return new StorageConfiguration(
        settings.getStorageEnabled() == null ? storageProperties.isEnabled() : settings.getStorageEnabled(),
        fallback(settings.getStorageEndpoint(), storageProperties.getEndpoint()),
        fallback(settings.getStorageRegion(), storageProperties.getRegion()),
        fallback(settings.getStorageBucket(), storageProperties.getBucket()),
        fallback(settings.getStorageAccessKey(), storageProperties.getAccessKey()),
        fallback(settings.getStorageSecretKey(), storageProperties.getSecretKey()),
        fallback(settings.getStorageSessionToken(), storageProperties.getSessionToken()),
        fallback(settings.getStoragePublicBaseUrl(), storageProperties.getPublicBaseUrl()),
        addressingStyle(null, settings.getStorageAddressingStyle(), storageProperties.getAddressingStyle()));
  }

  private boolean hasText(String value) { return value != null && !value.isBlank(); }

  private String addressingStyle(String requested, String current, String fallback) {
    String value = value(requested, current, fallback);
    if (value == null || value.isBlank()) return "AUTO";
    String normalized = value.trim().toUpperCase(java.util.Locale.ROOT);
    return switch (normalized) {
      case "PATH", "VIRTUAL", "AUTO" -> normalized;
      default -> throw new DomainException(org.springframework.http.HttpStatus.BAD_REQUEST,
          "Object storage addressing style must be AUTO, PATH, or VIRTUAL");
    };
  }

  public record StorageConfiguration(boolean enabled, String endpoint, String region, String bucket, String accessKey,
      String secretKey, String sessionToken, String publicBaseUrl, String addressingStyle) { }
}
