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

  public void update(String domain, String siteName, String logoUrl, String footerCode, String storageEndpoint,
      String storageBucket) {
    this.domain = domain;
    this.siteName = siteName;
    this.logoUrl = logoUrl;
    this.footerCode = footerCode;
    this.storageEndpoint = storageEndpoint;
    this.storageBucket = storageBucket;
  }
}
