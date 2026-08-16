package com.matrixlive.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.util.UUID;

@Entity
@Table(name = "registration_fields", uniqueConstraints = @UniqueConstraint(columnNames = {"activityId", "fieldKey"}))
public class RegistrationField {
  @Id
  @GeneratedValue
  private UUID id;

  @Column(nullable = false)
  private UUID activityId;

  @Column(nullable = false, length = 80)
  private String fieldKey;

  @Column(nullable = false, length = 160)
  private String label;

  @Column(nullable = false, length = 24)
  private String type;

  @Column(columnDefinition = "text")
  private String options;

  @Column(nullable = false)
  private boolean required;

  @Column(nullable = false)
  private boolean enabled;

  @Column(nullable = false)
  private int displayOrder;

  @Version
  private long version;

  protected RegistrationField() { }

  public RegistrationField(UUID activityId, String fieldKey, String label, String type, String options,
      boolean required, int displayOrder) {
    this.activityId = activityId;
    this.fieldKey = fieldKey;
    this.label = label;
    this.type = type;
    this.options = options == null ? "" : options;
    this.required = required;
    this.enabled = true;
    this.displayOrder = displayOrder;
  }

  public UUID getId() { return id; }
  public UUID getActivityId() { return activityId; }
  public String getFieldKey() { return fieldKey; }
  public String getLabel() { return label; }
  public String getType() { return type; }
  public String getOptions() { return options; }
  public boolean isRequired() { return required; }
  public boolean isEnabled() { return enabled; }
  public int getDisplayOrder() { return displayOrder; }
  public long getVersion() { return version; }

  public void update(String label, String type, String options, Boolean required, Integer displayOrder, Boolean enabled) {
    if (label != null) this.label = label;
    if (type != null) this.type = type;
    if (options != null) this.options = options;
    if (required != null) this.required = required;
    if (displayOrder != null) this.displayOrder = displayOrder;
    if (enabled != null) this.enabled = enabled;
  }
}
