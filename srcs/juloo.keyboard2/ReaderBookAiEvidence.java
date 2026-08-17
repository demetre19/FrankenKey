package juloo.keyboard2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Durable, output-neutral Book AI evidence state. Network dispatch stays native. */
final class ReaderBookAiEvidence
{
  static final String PIPELINE_VERSION = "reader-book-ai-v1";
  private static final Pattern WORD = Pattern.compile("\\S+");

  enum Feature
  {
    SUMMARY_ONE, SUMMARY_TWO, QUIZ, CHAT
  }

  static final class Work
  {
    final String evidenceId;
    final String evidenceIdentity;
    final ReaderBookAiPlanner.Chunk chunk;
    final int paragraphStart;
    final int paragraphEnd;
    final int rawWordStart;
    final int rawWordEnd;

    Work(String evidenceId, String evidenceIdentity,
        ReaderBookAiPlanner.Chunk chunk, int paragraphStart, int paragraphEnd,
        int rawWordStart, int rawWordEnd)
    {
      this.evidenceId = evidenceId;
      this.evidenceIdentity = evidenceIdentity;
      this.chunk = chunk;
      this.paragraphStart = paragraphStart;
      this.paragraphEnd = paragraphEnd;
      this.rawWordStart = rawWordStart;
      this.rawWordEnd = rawWordEnd;
    }
  }

  private final ReaderAiStore store;
  private final ReaderAiService.Article source;
  private final ReaderAiOpenRouter.Model model;
  private final ReaderBookAiPlanner.EvidencePlan plan;
  private final String jobId;
  private final String promptHash;
  private final List<Work> work;
  private final LinkedHashSet<String> completed;
  private ReaderAiStore.BookJob job;

  static ReaderBookAiEvidence open(ReaderAiStore store,
      ReaderAiService.Article source, Feature feature, String prompt,
      ReaderAiOpenRouter.Model model)
  {
    if (store == null)
      throw new IllegalArgumentException("Reader AI store is required");
    if (source == null || !source.isBook())
      throw new IllegalArgumentException("Book AI requires a parsed EPUB");
    if (feature == null)
      throw new IllegalArgumentException("Book AI feature is required");
    if (prompt == null || prompt.trim().isEmpty())
      throw new IllegalArgumentException("Book AI prompt is required");
    if (model == null || model.id == null || model.id.trim().isEmpty())
      throw new IllegalArgumentException("OpenRouter model is required");
    return new ReaderBookAiEvidence(store, source, feature, prompt, model);
  }

  private ReaderBookAiEvidence(ReaderAiStore store,
      ReaderAiService.Article source, Feature feature, String prompt,
      ReaderAiOpenRouter.Model model)
  {
    this.store = store;
    this.source = source;
    this.model = model;
    plan = ReaderBookAiPlanner.plan(source.contentHash, model.id,
        model.contextLength, source.bookChapters);
    promptHash = ReaderAiRequest.contentHash(prompt);
    jobId = ReaderAiRequest.cacheKey("BOOK_" + feature.name(), promptHash,
        model.id, "", source.contentHash, PIPELINE_VERSION + ':' + plan.identity);
    work = Collections.unmodifiableList(buildWork(plan, source.contentHash,
          model.id));
    completed = new LinkedHashSet<>(store.completedBookEvidence(
          source.contentHash, model.id, PIPELINE_VERSION, plan.identity));
    completed.retainAll(plan.evidenceKeys);
    long now = System.currentTimeMillis();
    ReaderAiStore.BookJob existing = store.loadBookJob(jobId);
    long createdAt = existing == null ? now : existing.createdAt;
    ReaderAiStore.BookJobStatus status = completed.size() == work.size()
      ? ReaderAiStore.BookJobStatus.COMPLETE
      : ReaderAiStore.BookJobStatus.RUNNING;
    job = new ReaderAiStore.BookJob(jobId, source.readerItemId,
        source.contentHash, feature.name(), promptHash, model.id,
        PIPELINE_VERSION, plan.identity, status, completed, createdAt, now);
    store.saveBookJob(job);
  }

  synchronized List<Work> nextBatch()
  {
    if (job.status == ReaderAiStore.BookJobStatus.CANCELLED ||
        job.status == ReaderAiStore.BookJobStatus.COMPLETE)
      return Collections.emptyList();
    List<String> next = ReaderBookAiPlanner.nextBatch(plan, completed);
    List<Work> result = new ArrayList<>(next.size());
    for (String identity : next)
      for (Work candidate : work)
        if (candidate.evidenceIdentity.equals(identity))
        {
          result.add(candidate);
          break;
        }
    return Collections.unmodifiableList(result);
  }

  synchronized boolean record(Work item, String neutralEvidence,
      String provenance)
  {
    if (job.status == ReaderAiStore.BookJobStatus.CANCELLED ||
        job.status == ReaderAiStore.BookJobStatus.FAILED || item == null ||
        !work.contains(item) || neutralEvidence == null ||
        neutralEvidence.trim().isEmpty())
      return false;
    ReaderAiStore.BookEvidence evidence = new ReaderAiStore.BookEvidence(
        item.evidenceId, item.evidenceIdentity, source.contentHash, model.id,
        PIPELINE_VERSION, plan.identity, item.chunk.chapterIndex,
        item.paragraphStart, item.paragraphEnd, item.rawWordStart,
        item.rawWordEnd, neutralEvidence.trim(),
        provenance == null ? "" : provenance.trim(),
        System.currentTimeMillis());
    store.saveBookEvidence(evidence);
    completed.add(item.evidenceIdentity);
    ReaderAiStore.BookJobStatus status = completed.size() == work.size()
      ? ReaderAiStore.BookJobStatus.COMPLETE
      : ReaderAiStore.BookJobStatus.RUNNING;
    job = job.withState(status, completed, System.currentTimeMillis());
    store.saveBookJob(job);
    return true;
  }

  synchronized void cancel()
  {
    if (job.status == ReaderAiStore.BookJobStatus.COMPLETE)
      return;
    job = job.withState(ReaderAiStore.BookJobStatus.CANCELLED, completed,
        System.currentTimeMillis());
    store.saveBookJob(job);
  }

  synchronized void fail()
  {
    if (job.status == ReaderAiStore.BookJobStatus.COMPLETE ||
        job.status == ReaderAiStore.BookJobStatus.CANCELLED)
      return;
    job = job.withState(ReaderAiStore.BookJobStatus.FAILED, completed,
        System.currentTimeMillis());
    store.saveBookJob(job);
  }

  synchronized boolean isComplete()
  {
    return job.status == ReaderAiStore.BookJobStatus.COMPLETE;
  }

  synchronized ReaderAiStore.BookJobStatus status()
  {
    return job.status;
  }
  synchronized int completedCount()
  {
    return completed.size();
  }

  int workCount()
  {
    return work.size();
  }

  String jobId()
  {
    return jobId;
  }

  String chunkPlanHash()
  {
    return plan.identity;
  }

  List<ReaderAiStore.BookEvidence> evidence()
  {
    return store.loadBookEvidence(source.contentHash, model.id,
        PIPELINE_VERSION, plan.identity);
  }

  private static List<Work> buildWork(
      ReaderBookAiPlanner.EvidencePlan plan, String bookFingerprint,
      String modelId)
  {
    List<Work> result = new ArrayList<>(plan.chunks.size());
    int rawWord = 0;
    int chapter = -1;
    int paragraph = 0;
    for (int index = 0; index < plan.chunks.size(); index++)
    {
      ReaderBookAiPlanner.Chunk chunk = plan.chunks.get(index);
      if (chunk.chapterIndex != chapter)
      {
        chapter = chunk.chapterIndex;
        paragraph = 0;
      }
      int paragraphs = Math.max(1, chunk.text.split("\\n{2,}").length);
      int words = wordCount(chunk.text);
      String identity = plan.evidenceKeys.get(index);
      String evidenceId = ReaderAiRequest.cacheKey("BOOK_EVIDENCE", identity,
          modelId, "", bookFingerprint,
          PIPELINE_VERSION + ':' + plan.identity);
      result.add(new Work(evidenceId, identity, chunk, paragraph,
            paragraph + paragraphs, rawWord, rawWord + words));
      paragraph += paragraphs;
      rawWord += words;
    }
    return result;
  }

  private static int wordCount(String text)
  {
    int count = 0;
    Matcher matcher = WORD.matcher(text == null ? "" : text);
    while (matcher.find())
      count++;
    return count;
  }
}
