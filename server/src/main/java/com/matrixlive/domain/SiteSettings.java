package com.matrixlive.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Singleton platform-level presentation settings. */
@Entity
@Table(name = "site_settings")
public class SiteSettings {
  @Id
  private Long id;

  @Column(nullable = false, length = 255)
  private String domain;

  @Column(nullable = false, length = 160)
  private String siteName;

  @Column(length = 1024)
  private String logoUrl;

  @Column(columnDefinition = "text")
  private String footerCode;

  @Column(length = 1024)
  private String storageEndpoint;

  @Column(length = 160)
  private String storageBucket;

  private Boolean storageEnabled;

  @Column(length = 80)
  private String storageRegion;

  @Column(length = 512)
  private String storageAccessKey;

  @Column(length = 2048)
  private String storageSecretKey;

  @Column(length = 4096)
  private String storageSessionToken;

  @Column(length = 1024)
  private String storagePublicBaseUrl;

  @Column(length = 16)
  private String storageAddressingStyle;

  protected SiteSettings() { }

  public SiteSettings(String domain, String siteName, String logoUrl, String footerCode) {
    this.id = 1L;
    this.domain = domain;
    this.siteName = siteName;
    this.logoUrl = logoUrl;
    this.footerCode = footerCode;
  }

  public Long getId() { return id; }
  public String getDomain() { return domain; }
  public String getSiteName() { return siteName; }
  public String getLogoUrl() { return logoUrl; }
  public String getFooterCode() { return footerCode; }
  public String getStorageEndpoint() { return storageEndpoint; }
  public String getStorageBucket() { return storageBucket; }
  public Boolean getStorageEnabled() { return storageEnabled; }
  public String getStorageRegion() { return storageRegion; }
  public String getStorageAccessKey() { return storageAccessKey; }
  public String getStorageSecretKey() { return storageSecretKey; }
  public String getStorageSessionToken() { return storageSessionToken; }
  public String getStoragePublicBaseUrl() { return storagePublicBaseUrl; }
  public String getStorageAddressingStyle() { return storageAddressingStyle; }

  public void update(String domain, String siteName, String logoUrl, String footerCode, Boolean storageEnabled,
      String storageEndpoint, String storageRegion, String storageBucket, String storageAccessKey,
      String storageSecretKey, String storageSessionToken, String storagePublicBaseUrl, String storageAddressingStyle) {
    this.domain = domain;
    this.siteName = siteName;
    this.logoUrl = logoUrl;
    this.footerCode = footerCode;
    if (storageEnabled != null) this.storageEnabled = storageEnabled;
    this.storageEndpoint = storageEndpoint;
    this.storageRegion = storageRegion;
    this.storageBucket = storageBucket;
    this.storageAccessKey = storageAccessKey;
    this.storageSecretKey = storageSecretKey;
    this.storageSessionToken = storageSessionToken;
    this.storagePublicBaseUrl = storagePublicBaseUrl;
    this.storageAddressingStyle = storageAddressingStyle;
  }
}
