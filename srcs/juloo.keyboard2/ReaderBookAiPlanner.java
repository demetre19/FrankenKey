package juloo.keyboard2;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure chapter-aware planning shared by local EPUB summaries, quizzes, and chat. */
final class ReaderBookAiPlanner
{
  static final int MAX_SOURCE_BYTES = 6 * 1024 * 1024;
  static final int MAX_CHAPTERS = 200;
  static final int MAX_CHUNKS = 240;
  static final int BATCH_SIZE = 4;
  static final int MAX_EMPTY_RETRIES = 1;
  static final int MAX_QUIZ_REPAIRS = 1;
  static final int CONTEXT_RESERVE_TOKENS = 8_000;
  static final int MIN_CHUNK_TOKENS = 1_000;
  static final int MAX_CHUNK_TOKENS = 24_000;

  private static final String EVIDENCE_VERSION = "reader-book-ai-evidence-v1";
  private static final Pattern TERM = Pattern.compile("[\\p{L}\\p{N}]{3,}");

  static final class Chapter
  {
    final String title;
    final String text;

    Chapter(String title, String text)
    {
      this.title = cleanTitle(title);
      this.text = normalizeText(text);
    }
  }

  static final class Chunk
  {
    final int index;
    final int chapterIndex;
    final String chapterTitle;
    final String text;

    Chunk(int index, int chapterIndex, String chapterTitle, String text)
    {
      this.index = index;
      this.chapterIndex = chapterIndex;
      this.chapterTitle = chapterTitle;
      this.text = text;
    }
  }

  static final class EvidencePlan
  {
    final String identity;
    final int chunkTokens;
    final List<Chunk> chunks;
    final List<String> evidenceKeys;

    EvidencePlan(String identity, int chunkTokens, List<Chunk> chunks,
        List<String> evidenceKeys)
    {
      this.identity = identity;
      this.chunkTokens = chunkTokens;
      this.chunks = Collections.unmodifiableList(chunks);
      this.evidenceKeys = Collections.unmodifiableList(evidenceKeys);
    }
  }

  static final class Passage
  {
    final int chapterIndex;
    final String chapterTitle;
    final String text;
    final int score;

    Passage(int chapterIndex, String chapterTitle, String text, int score)
    {
      this.chapterIndex = chapterIndex;
      this.chapterTitle = chapterTitle;
      this.text = text;
      this.score = score;
    }
  }

  private ReaderBookAiPlanner() {}

  static EvidencePlan plan(String contentHash, String modelId,
      int contextLength, List<Chapter> chapters)
  {
    String checkedHash = checkedIdentityPart(contentHash, "Book fingerprint");
    String checkedModel = checkedIdentityPart(modelId, "Model");
    List<Chapter> checkedChapters = checkedChapters(chapters);
    int chunkTokens = evidenceChunkTokens(contextLength);
    List<Chunk> chunks = chunks(checkedChapters, chunkTokens);
    String identity = sha256(EVIDENCE_VERSION + '\u001f' + checkedHash
        + '\u001f' + checkedModel + '\u001f' + chunkTokens);
    List<String> evidenceKeys = new ArrayList<>(chunks.size());
    for (Chunk chunk : chunks)
      evidenceKeys.add(sha256(EVIDENCE_VERSION + '\u001f' + checkedModel
            + '\u001f' + chunk.chapterTitle + '\u001f' + chunk.text));
    return new EvidencePlan(identity, chunkTokens, chunks, evidenceKeys);
  }
  static List<Chapter> readableChapters(List<Chapter> chapters)
  {
    return Collections.unmodifiableList(checkedChapters(chapters));
  }

  static int evidenceChunkTokens(int contextLength)
  {
    int available = Math.max(0, contextLength - CONTEXT_RESERVE_TOKENS);
    return Math.min(MAX_CHUNK_TOKENS,
        Math.max(MIN_CHUNK_TOKENS, available / 2));
  }

  static List<String> nextBatch(EvidencePlan plan, Set<String> completed)
  {
    if (plan == null)
      throw new IllegalArgumentException("Evidence plan is required");
    Set<String> done = completed == null
      ? Collections.<String>emptySet() : completed;
    List<String> result = new ArrayList<>(BATCH_SIZE);
    for (String key : plan.evidenceKeys)
    {
      if (!done.contains(key))
        result.add(key);
      if (result.size() == BATCH_SIZE)
        break;
    }
    return result;
  }

  static int[] summaryChapterTargets(List<Chapter> chapters, boolean detailed)
  {
    List<Chapter> checked = checkedChapters(chapters);
    int minimum = detailed ? 180 : 120;
    double ratio = detailed ? 0.10 : 0.07;
    int maximum = detailed ? 16_000 : 12_000;
    int sourceWords = 0;
    int[] weights = new int[checked.size()];
    for (int index = 0; index < checked.size(); index++)
    {
      weights[index] = wordCount(checked.get(index).text);
      sourceWords += weights[index];
    }
    int target = Math.min(maximum,
        Math.max(checked.size() * minimum, (int)Math.round(sourceWords * ratio)));
    return allocate(weights, target, minimum);
  }


  static boolean isQuizCountSupported(int count)
  {
    return count == 6 || count == 10 || count == 12 || count == 20;
  }

  static boolean shouldRetryEmptyResponse(int retriesAlreadyUsed)
  {
    return retriesAlreadyUsed < MAX_EMPTY_RETRIES;
  }

  static boolean shouldRepairQuiz(int repairsAlreadyUsed)
  {
    return repairsAlreadyUsed < MAX_QUIZ_REPAIRS;
  }

  static List<Passage> retrievePassages(List<Chapter> chapters,
      String question)
  {
    List<Chapter> checked = checkedChapters(chapters);
    Set<String> terms = terms(question);
    List<Passage> matches = new ArrayList<>();
    for (int chapterIndex = 0; chapterIndex < checked.size(); chapterIndex++)
    {
      Chapter chapter = checked.get(chapterIndex);
      String[] paragraphs = chapter.text.split("\\n{2,}");
      for (String paragraph : paragraphs)
      {
        String lower = paragraph.toLowerCase(Locale.ROOT);
        int score = 0;
        for (String term : terms)
          if (lower.contains(term))
            score++;
        if (score > 0)
          matches.add(new Passage(chapterIndex, chapter.title,
                bounded(paragraph, 8_000), score));
      }
    }
    if (!matches.isEmpty())
    {
      matches.sort(Comparator.comparingInt((Passage passage) -> passage.score)
          .reversed().thenComparingInt(passage -> passage.chapterIndex));
      return Collections.unmodifiableList(new ArrayList<>(matches.subList(0,
              Math.min(12, matches.size()))));
    }

    List<Passage> fallback = new ArrayList<>();
    for (int index = 0; index < Math.min(3, checked.size()); index++)
    {
      Chapter chapter = checked.get(index);
      fallback.add(new Passage(index, chapter.title,
            bounded(chapter.text, 8_000), 0));
    }
    return Collections.unmodifiableList(fallback);
  }

  private static List<Chapter> checkedChapters(List<Chapter> chapters)
  {
    if (chapters == null || chapters.isEmpty())
      throw new IllegalArgumentException("Book has no readable chapters");
    if (chapters.size() > MAX_CHAPTERS)
      throw new IllegalArgumentException("Book has more than "
          + MAX_CHAPTERS + " chapters");
    long bytes = 0;
    List<Chapter> result = new ArrayList<>(chapters.size());
    for (int index = 0; index < chapters.size(); index++)
    {
      Chapter source = chapters.get(index);
      if (source == null || source.text.isEmpty())
        continue;
      String title = source.title.isEmpty() ? "Chapter " + (index + 1)
        : source.title;
      Chapter checked = new Chapter(title, source.text);
      bytes += checked.text.getBytes(StandardCharsets.UTF_8).length;
      if (bytes > MAX_SOURCE_BYTES)
        throw new IllegalArgumentException("Book text is larger than 6 MiB");
      result.add(checked);
    }
    if (result.isEmpty())
      throw new IllegalArgumentException("Book has no readable chapters");
    return result;
  }

  private static List<Chunk> chunks(List<Chapter> chapters, int maxTokens)
  {
    int maxChars = Math.max(4_000, maxTokens * 4);
    List<Chunk> result = new ArrayList<>();
    for (int chapterIndex = 0; chapterIndex < chapters.size(); chapterIndex++)
    {
      Chapter chapter = chapters.get(chapterIndex);
      StringBuilder current = new StringBuilder();
      for (String rawParagraph : chapter.text.split("\\n{2,}"))
      {
        String paragraph = rawParagraph.trim();
        if (paragraph.isEmpty())
          continue;
        if (paragraph.length() > maxChars)
        {
          flush(result, chapterIndex, chapter.title, current);
          for (int start = 0; start < paragraph.length();)
          {
            int end = safeEnd(paragraph, start, maxChars);
            addChunk(result, chapterIndex, chapter.title,
                paragraph.substring(start, end));
            start = end;
          }
        }
        else
        {
          int separator = current.length() == 0 ? 0 : 2;
          if (current.length() + separator + paragraph.length() > maxChars)
            flush(result, chapterIndex, chapter.title, current);
          if (current.length() > 0)
            current.append("\n\n");
          current.append(paragraph);
        }
      }
      flush(result, chapterIndex, chapter.title, current);
    }
    if (result.isEmpty())
      throw new IllegalArgumentException("Book has no safe AI chunks");
    return result;
  }

  private static void flush(List<Chunk> result, int chapterIndex,
      String chapterTitle, StringBuilder current)
  {
    if (current.length() == 0)
      return;
    addChunk(result, chapterIndex, chapterTitle, current.toString());
    current.setLength(0);
  }

  private static void addChunk(List<Chunk> result, int chapterIndex,
      String chapterTitle, String text)
  {
    if (result.size() >= MAX_CHUNKS)
      throw new IllegalArgumentException("Book needs more than "
          + MAX_CHUNKS + " safe AI chunks");
    result.add(new Chunk(result.size(), chapterIndex, chapterTitle, text));
  }

  private static int safeEnd(String value, int start, int length)
  {
    int end = Math.min(value.length(), start + length);
    if (end < value.length() && end > start
        && Character.isHighSurrogate(value.charAt(end - 1))
        && Character.isLowSurrogate(value.charAt(end)))
      end--;
    return end;
  }

  private static int[] allocate(int[] weights, int total, int minimum)
  {
    int[] result = new int[weights.length];
    java.util.Arrays.fill(result, minimum);
    int remaining = Math.max(0, total - weights.length * minimum);
    if (remaining == 0)
      return result;
    long weightTotal = 0;
    for (int weight : weights)
      weightTotal += Math.max(0, weight);
    boolean equalWeights = weightTotal == 0;
    if (equalWeights)
      weightTotal = weights.length;
    int distributable = remaining;
    double[] remainders = new double[weights.length];
    for (int index = 0; index < weights.length; index++)
    {
      double share = distributable * (equalWeights
          ? 1.0 : Math.max(0, weights[index])) / weightTotal;
      int whole = (int)Math.floor(share);
      result[index] += whole;
      remainders[index] = share - whole;
      remaining -= whole;
    }
    while (remaining > 0)
    {
      int best = 0;
      for (int index = 1; index < remainders.length; index++)
        if (remainders[index] > remainders[best])
          best = index;
      result[best]++;
      remainders[best] = -1;
      remaining--;
    }
    return result;
  }

  private static Set<String> terms(String question)
  {
    Set<String> result = new HashSet<>();
    Matcher matcher = TERM.matcher(question == null ? ""
        : question.toLowerCase(Locale.ROOT));
    while (matcher.find())
      result.add(matcher.group());
    return result;
  }

  private static int wordCount(String value)
  {
    String clean = value == null ? "" : value.trim();
    return clean.isEmpty() ? 0 : clean.split("\\s+").length;
  }

  private static String normalizeText(String value)
  {
    return value == null ? "" : value.replace("\r\n", "\n")
      .replace('\r', '\n').trim();
  }

  private static String cleanTitle(String value)
  {
    return value == null ? "" : value.replace('\r', ' ')
      .replace('\n', ' ').trim();
  }

  private static String bounded(String value, int max)
  {
    String clean = value == null ? "" : value.trim();
    return clean.length() <= max ? clean : clean.substring(0,
        safeEnd(clean, 0, max));
  }

  private static String checkedIdentityPart(String value, String label)
  {
    String checked = value == null ? "" : value.trim();
    if (checked.isEmpty())
      throw new IllegalArgumentException(label + " is required");
    return checked;
  }

  private static String sha256(String value)
  {
    try
    {
      byte[] digest = MessageDigest.getInstance("SHA-256")
        .digest(value.getBytes(StandardCharsets.UTF_8));
      StringBuilder result = new StringBuilder(digest.length * 2);
      for (byte part : digest)
        result.append(String.format(Locale.US, "%02x", part & 0xff));
      return result.toString();
    }
    catch (NoSuchAlgorithmException impossible)
    {
      throw new AssertionError(impossible);
    }
  }
}
