package juloo.keyboard2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ReaderBookAiPlannerProofTest
{
  @Test
  public void chapterAwarePlanPreservesOrderParagraphsAndSafeBounds()
  {
    List<ReaderBookAiPlanner.Chapter> chapters = Arrays.asList(
        new ReaderBookAiPlanner.Chapter("One",
          repeat('a', 3000) + "\n\n" + repeat('b', 3000) + "\n\n"
          + repeat('c', 3000)),
        new ReaderBookAiPlanner.Chapter("Two", repeat('z', 9001)));

    ReaderBookAiPlanner.EvidencePlan plan = ReaderBookAiPlanner.plan(
        repeat('a', 64), "model-a", 12_000, chapters);

    assertEquals(2_000, plan.chunkTokens);
    assertEquals(4, plan.chunks.size());
    assertEquals(0, plan.chunks.get(0).chapterIndex);
    assertEquals(0, plan.chunks.get(1).chapterIndex);
    assertEquals(1, plan.chunks.get(2).chapterIndex);
    assertEquals(1, plan.chunks.get(3).chapterIndex);
    assertEquals(repeat('a', 3000) + "\n\n" + repeat('b', 3000),
        plan.chunks.get(0).text);
    assertEquals(repeat('c', 3000), plan.chunks.get(1).text);
    assertEquals(repeat('z', 9001), plan.chunks.get(2).text
        + plan.chunks.get(3).text);
    assertTrue(plan.chunks.stream().allMatch(chunk -> chunk.text.length() <= 8000));
  }

  @Test
  public void neutralEvidenceIdentityReusesAcrossOutputsAndResumesFourAtATime()
  {
    List<ReaderBookAiPlanner.Chapter> chapters = Arrays.asList(
        new ReaderBookAiPlanner.Chapter("One", repeatedParagraphs(10, 3500)),
        new ReaderBookAiPlanner.Chapter("Two", repeatedParagraphs(10, 3500)));
    ReaderBookAiPlanner.EvidencePlan first = ReaderBookAiPlanner.plan(
        repeat('b', 64), "model-a", 12_000, chapters);
    ReaderBookAiPlanner.EvidencePlan sameForAnotherOutput =
      ReaderBookAiPlanner.plan(repeat('b', 64), "model-a", 12_000, chapters);
    ReaderBookAiPlanner.EvidencePlan changedModel = ReaderBookAiPlanner.plan(
        repeat('b', 64), "model-b", 12_000, chapters);

    assertEquals(first.identity, sameForAnotherOutput.identity);
    assertEquals(first.evidenceKeys, sameForAnotherOutput.evidenceKeys);
    assertNotEquals(first.identity, changedModel.identity);
    assertTrue(first.evidenceKeys.size() > ReaderBookAiPlanner.BATCH_SIZE);

    List<String> initial = ReaderBookAiPlanner.nextBatch(first,
        java.util.Collections.emptySet());
    assertEquals(ReaderBookAiPlanner.BATCH_SIZE, initial.size());
    Set<String> completed = new HashSet<>(initial);
    List<String> resumed = ReaderBookAiPlanner.nextBatch(first, completed);
    assertTrue(resumed.size() <= ReaderBookAiPlanner.BATCH_SIZE);
    assertTrue(resumed.stream().noneMatch(completed::contains));
  }

  @Test
  public void sourceAndChunkLimitsFailBeforeDispatch()
  {
    List<ReaderBookAiPlanner.Chapter> tooManyChapters = new ArrayList<>();
    for (int index = 0; index <= ReaderBookAiPlanner.MAX_CHAPTERS; index++)
      tooManyChapters.add(new ReaderBookAiPlanner.Chapter("Chapter " + index,
            "Readable text"));
    assertThrows(IllegalArgumentException.class, () ->
        ReaderBookAiPlanner.plan(repeat('c', 64), "model", 128_000,
          tooManyChapters));

    List<ReaderBookAiPlanner.Chapter> tooLarge = java.util.Collections.singletonList(
        new ReaderBookAiPlanner.Chapter("Large",
          repeat('x', ReaderBookAiPlanner.MAX_SOURCE_BYTES + 1)));
    assertThrows(IllegalArgumentException.class, () ->
        ReaderBookAiPlanner.plan(repeat('c', 64), "model", 128_000,
          tooLarge));

    List<ReaderBookAiPlanner.Chapter> tooManyChunks = java.util.Collections.singletonList(
        new ReaderBookAiPlanner.Chapter("Chunks", repeatedParagraphs(241, 4001)));
    assertThrows(IllegalArgumentException.class, () ->
        ReaderBookAiPlanner.plan(repeat('c', 64), "model", 8_000,
          tooManyChunks));
  }

  @Test
  public void summaryAndQuizAllocationCoverChaptersDeterministically()
  {
    List<ReaderBookAiPlanner.Chapter> chapters = new ArrayList<>();
    for (int index = 0; index < 10; index++)
      chapters.add(new ReaderBookAiPlanner.Chapter("Chapter " + (index + 1),
            repeatedWords(100 + index * 50)));

    int[] detailed = ReaderBookAiPlanner.summaryChapterTargets(chapters, true);
    int[] concise = ReaderBookAiPlanner.summaryChapterTargets(chapters, false);
    assertEquals(10, detailed.length);
    assertEquals(10, concise.length);
    assertTrue(Arrays.stream(detailed).allMatch(value -> value >= 180));
    assertTrue(Arrays.stream(concise).allMatch(value -> value >= 120));
    List<ReaderBookAiPlanner.Chapter> longChapters = Arrays.asList(
        new ReaderBookAiPlanner.Chapter("Long One", repeatedWords(5_000)),
        new ReaderBookAiPlanner.Chapter("Long Two", repeatedWords(5_000)));
    assertEquals(1_000, Arrays.stream(ReaderBookAiPlanner
          .summaryChapterTargets(longChapters, true)).sum());
    assertEquals(700, Arrays.stream(ReaderBookAiPlanner
          .summaryChapterTargets(longChapters, false)).sum());
    List<ReaderBookAiPlanner.Chapter> readable =
      ReaderBookAiPlanner.readableChapters(Arrays.asList(
          chapters.get(0), new ReaderBookAiPlanner.Chapter("Blank", "  "),
          chapters.get(1)));
    assertEquals(2, readable.size());
    for (int selected : new int[]{6, 10, 12, 20})
    {
      assertTrue(ReaderBookAiPlanner.isQuizCountSupported(selected));
      assertEquals("Book quiz size is selected per readable chapter.",
          selected * readable.size(), selected * 2);
    }
    assertFalse(ReaderBookAiPlanner.isQuizCountSupported(8));
  }

  @Test
  public void chatRetrievalIsBoundedGroundedAndHasThreeChapterFallback()
  {
    List<ReaderBookAiPlanner.Chapter> chapters = new ArrayList<>();
    for (int index = 0; index < 15; index++)
      chapters.add(new ReaderBookAiPlanner.Chapter("Chapter " + index,
            "Mercury evidence business action " + index + "\n\nOther material"));

    List<ReaderBookAiPlanner.Passage> matches =
      ReaderBookAiPlanner.retrievePassages(chapters,
          "What business action does the Mercury evidence support?");
    assertEquals(12, matches.size());
    assertTrue(matches.stream().allMatch(passage -> passage.score > 0));
    assertTrue(matches.stream().allMatch(passage ->
          passage.text.contains("Mercury evidence")));

    List<ReaderBookAiPlanner.Passage> fallback =
      ReaderBookAiPlanner.retrievePassages(chapters, "unmatchedword");
    assertEquals(3, fallback.size());
    assertEquals("Chapter 0", fallback.get(0).chapterTitle);
    assertEquals("Chapter 2", fallback.get(2).chapterTitle);
  }

  @Test
  public void retriesAndQuizRepairAreBoundedToOne()
  {
    assertTrue(ReaderBookAiPlanner.shouldRetryEmptyResponse(0));
    assertFalse(ReaderBookAiPlanner.shouldRetryEmptyResponse(1));
    assertTrue(ReaderBookAiPlanner.shouldRepairQuiz(0));
    assertFalse(ReaderBookAiPlanner.shouldRepairQuiz(1));
  }

  private static String repeatedParagraphs(int count, int length)
  {
    StringBuilder result = new StringBuilder();
    for (int index = 0; index < count; index++)
    {
      if (result.length() > 0)
        result.append("\n\n");
      result.append(repeat((char)('a' + index % 20), length));
    }
    return result.toString();
  }

  private static String repeatedWords(int count)
  {
    StringBuilder result = new StringBuilder();
    for (int index = 0; index < count; index++)
      result.append("word").append(index).append(' ');
    return result.toString();
  }

  private static String repeat(char value, int count)
  {
    char[] result = new char[count];
    Arrays.fill(result, value);
    return new String(result);
  }
}
