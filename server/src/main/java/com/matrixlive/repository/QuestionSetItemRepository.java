package com.matrixlive.repository;

import com.matrixlive.domain.QuestionSetItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionSetItemRepository extends JpaRepository<QuestionSetItem, UUID> {
  List<QuestionSetItem> findByQuestionSetIdOrderByDisplayOrderAsc(UUID questionSetId);
  void deleteByQuestionSetId(UUID questionSetId);
}
