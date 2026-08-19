package com.matrixlive.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.UUID;

@Entity
@Table(name = "question_set_items")
public class QuestionSetItem {
  @Id
  @GeneratedValue
  private UUID id;

  @Column(nullable = false)
  private UUID questionSetId;

  @Column(nullable = false)
  private UUID questionId;

  @Column(nullable = false)
  private int displayOrder;

  @Version
  private long version;

  protected QuestionSetItem() { }

  public QuestionSetItem(UUID questionSetId, UUID questionId, int displayOrder) {
    this.questionSetId = questionSetId;
    this.questionId = questionId;
    this.displayOrder = displayOrder;
  }

  public UUID getId() { return id; }
  public UUID getQuestionSetId() { return questionSetId; }
  public UUID getQuestionId() { return questionId; }
  public int getDisplayOrder() { return displayOrder; }
}
