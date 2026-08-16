package com.matrixlive.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;
import org.junit.jupiter.api.Test;

class AnswerScorerTest {
  @Test
  void givesFullScoreForExactSelections() {
    assertEquals(100, AnswerScorer.score(Set.of("D", "A", "B"), Set.of("A", "B", "D"), 100));
  }

  @Test
  void givesPartialScoreOnlyForStrictCorrectSubset() {
    assertEquals(40, AnswerScorer.score(Set.of("A"), Set.of("A", "B", "D"), 100));
    assertEquals(0, AnswerScorer.score(Set.of("A", "C"), Set.of("A", "B", "D"), 100));
  }
}
