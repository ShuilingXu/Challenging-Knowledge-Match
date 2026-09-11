package com.matrixlive.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Converter;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "questions")
public class Question {
  @Id
  @GeneratedValue
  private UUID id;

  @Column(nullable = false)
  private UUID activityId;

  @Column(nullable = false, length = 24)
  private String type;

  @Column(nullable = false, columnDefinition = "text")
  private String title;

  @Column(columnDefinition = "text")
  private String options;

  @Column(columnDefinition = "text")
  private String answers;

  @Column(nullable = false)
  private int fullScore;

  @Column(nullable = false)
  private int displayOrder;

  @Column(nullable = false)
  private boolean enabled;

  @Column(length = 2048)
  private String mediaUrl;

  @Convert(converter = MediaUrlsConverter.class)
  @Column(name = "media_urls", columnDefinition = "text")
  private List<String> mediaUrls;

  @Column(nullable = false)
  private int partialCreditPercent;

  @Column(columnDefinition = "text")
  private String textAcceptedAnswers;

  @Column(nullable = false, length = 24)
  private String textMatchMode;

  @Version
  private long version;

  protected Question() { }

  public Question(UUID activityId, String type, String title, String options, String answers, int fullScore) {
    this(activityId, type, title, options, answers, fullScore, 0, null, 40, "[]", "MANUAL");
  }

  public Question(UUID activityId, String type, String title, String options, String answers, int fullScore,
      int displayOrder, String mediaUrl, int partialCreditPercent, String textAcceptedAnswers, String textMatchMode) {
    this.activityId = activityId;
    this.type = type;
    this.title = title;
    this.options = options == null ? "" : options;
    this.answers = answers == null ? "" : answers;
    this.fullScore = fullScore;
    this.displayOrder = displayOrder;
    this.mediaUrl = mediaUrl;
    this.partialCreditPercent = partialCreditPercent;
    this.textAcceptedAnswers = textAcceptedAnswers == null ? "[]" : textAcceptedAnswers;
    this.textMatchMode = textMatchMode == null ? "MANUAL" : textMatchMode;
    this.enabled = true;
  }

  public UUID getId() { return id; }
  public UUID getActivityId() { return activityId; }
  public String getType() { return type; }
  public String getTitle() { return title; }
  public String getOptions() { return options; }
  public String getAnswers() { return answers; }
  public int getFullScore() { return fullScore; }
  public int getDisplayOrder() { return displayOrder; }
  public boolean isEnabled() { return enabled; }
  public String getMediaUrl() { return mediaUrl; }
  public List<String> getMediaUrls() {
    if (mediaUrls == null) {
      return mediaUrl == null || mediaUrl.isBlank() ? List.of() : List.of(mediaUrl);
    }
    return List.copyOf(mediaUrls);
  }
  public int getPartialCreditPercent() { return partialCreditPercent; }
  public String getTextAcceptedAnswers() { return textAcceptedAnswers; }
  public String getTextMatchMode() { return textMatchMode; }
  public long getVersion() { return version; }

  public void update(String type, String title, String options, String answers, Integer fullScore, Integer displayOrder,
      String mediaUrl, Integer partialCreditPercent, String textAcceptedAnswers, String textMatchMode, Boolean enabled) {
    if (type != null) this.type = type;
    if (title != null) this.title = title;
    if (options != null) this.options = options;
    if (answers != null) this.answers = answers;
    if (fullScore != null) this.fullScore = fullScore;
    if (displayOrder != null) this.displayOrder = displayOrder;
    if (mediaUrl != null) setMediaUrls(mediaUrl.isBlank() ? List.of() : List.of(mediaUrl));
    if (partialCreditPercent != null) this.partialCreditPercent = partialCreditPercent;
    if (textAcceptedAnswers != null) this.textAcceptedAnswers = textAcceptedAnswers;
    if (textMatchMode != null) this.textMatchMode = textMatchMode;
    if (enabled != null) this.enabled = enabled;
  }

  public void setMediaUrls(List<String> mediaUrls) {
    this.mediaUrls = List.copyOf(mediaUrls);
    this.mediaUrl = mediaUrls.isEmpty() ? "" : mediaUrls.get(0);
  }

  @Converter
  public static class MediaUrlsConverter implements AttributeConverter<List<String>, String> {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(List<String> values) {
      if (values == null) return null;
      try {
        return MAPPER.writeValueAsString(values);
      } catch (JsonProcessingException exception) {
        throw new IllegalArgumentException("Cannot serialize question media", exception);
      }
    }

    @Override
    public List<String> convertToEntityAttribute(String value) {
      if (value == null || value.isBlank()) return null;
      try {
        return MAPPER.readValue(value, new TypeReference<List<String>>() { });
      } catch (JsonProcessingException exception) {
        throw new IllegalArgumentException("Cannot deserialize question media", exception);
      }
    }
  }
}
