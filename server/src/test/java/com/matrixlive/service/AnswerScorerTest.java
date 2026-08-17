package com.matrixlive.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;
import java.util.List;
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

  @Test
  void fuzzyTextMatchingNormalizesWhitespaceAndCase() {
    assertEquals(true, AnswerScorer.matchesText("  The  Answer ", List.of("the answer"), "FUZZY"));
    assertEquals(true, AnswerScorer.matchesText("the answer is clear", List.of("answer"), "FUZZY"));
    assertEquals(false, AnswerScorer.matchesText("another answer", List.of("the answer"), "FUZZY"));
  }

  @Test
  void regexTextMatchingUsesTheWholeAnswer() {
    assertEquals(true, AnswerScorer.matchesText("Answer 42", List.of("answer\\s+\\d+"), "REGEX"));
    assertEquals(false, AnswerScorer.matchesText("prefix Answer 42 suffix", List.of("answer\\s+\\d+"), "REGEX"));
  }
}
