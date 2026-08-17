package juloo.keyboard2;

import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Synchronous Reader AI generation engine. Call only from a worker thread. */
final class ReaderAiService
{
  static final class Article
  {
    final String readerItemId;
    final String title;
    final String sourceUrl;
    final String sourceHost;
    final String author;
    final String contentHash;
    final String text;

    Article(String readerItemId, String title, String sourceUrl,
        String sourceHost, String author, String contentHash, String text)
    {
      this.readerItemId = readerItemId;
      this.title = title;
      this.sourceUrl = sourceUrl;
      this.sourceHost = sourceHost;
      this.author = author;
      this.contentHash = contentHash;
      this.text = text;
    }
  }

  static final class ChatTurn
  {
    final String question;
    final String answer;

    ChatTurn(String question, String answer)
    {
      this.question = question;
      this.answer = answer;
    }
  }

  static final class Result
  {
    final String markdown;
    final String cacheKey;
    final boolean cached;
    final int requestCount;

    Result(String markdown, String cacheKey, boolean cached, int requestCount)
    {
      this.markdown = markdown;
      this.cacheKey = cacheKey;
      this.cached = cached;
      this.requestCount = requestCount;
    }
  }

  private final ReaderAiOpenRouter client;
  private final ReaderAiCache cache;

  ReaderAiService(ReaderAiOpenRouter client, ReaderAiCache cache)
  {
    this.client = client;
    this.cache = cache;
  }

  boolean needsMultipleCalls(Article article, String prompt,
      ReaderAiOpenRouter.Model model)
  {
    return !ReaderAiRequest.fitsSingleRequest(article.text, prompt,
        model == null ? 0 : model.contextLength);
  }

  Result summary(String apiKey, ReaderAiOpenRouter.Model model, Article article,
      String label, String prompt) throws IOException, JSONException
  {
    String requestContent = ReaderAiRequest.sourceMessage(article.title,
        article.sourceUrl, article.text);
    String cacheKey = ReaderAiRequest.cacheKey(label, prompt, model.id,
        article.sourceUrl, article.contentHash, requestContent);
    String cached = cache.get(cacheKey);
    if (cached != null)
      return new Result(cached, cacheKey, true, 0);

    List<String> chunks = ReaderAiRequest.chunks(article.text, prompt,
        model.contextLength);
    String result;
    int requests = 0;
    if (chunks.size() == 1)
    {
      result = generate(apiKey, model.id, prompt, requestContent);
      requests = 1;
    }
    else
    {
      List<String> partials = new ArrayList<>(chunks.size());
      for (int index = 0; index < chunks.size(); index++)
      {
        String source = "Part " + (index + 1) + " of " + chunks.size()
          + ". Preserve source order and summarize only this part.\n\n"
          + ReaderAiRequest.sourceMessage(article.title, article.sourceUrl,
              chunks.get(index));
        partials.add(generate(apiKey, model.id, prompt, source));
        requests++;
      }
      StringBuilder synthesis = new StringBuilder(
          "Combine the following ordered partial analyses into one complete, non-duplicative response. Follow the system instruction exactly. Do not mention chunks or partial analyses.\n\n");
      for (int index = 0; index < partials.size(); index++)
        synthesis.append("BEGIN PARTIAL ").append(index + 1).append(" OF ")
          .append(partials.size()).append("\n").append(partials.get(index))
          .append("\nEND PARTIAL ").append(index + 1).append("\n\n");
      result = generate(apiKey, model.id, prompt, synthesis.toString());
      requests++;
    }
    cache.put(cacheKey, result);
    return new Result(result, cacheKey, false, requests);
  }

  String directChat(String apiKey, String modelId, Article article,
      List<ChatTurn> turns, String question) throws IOException, JSONException
  {
    List<ReaderAiOpenRouter.Message> messages = new ArrayList<>();
    messages.add(new ReaderAiOpenRouter.Message("system",
          ReaderAiRequest.DIRECT_CHAT_PROMPT));
    messages.add(new ReaderAiOpenRouter.Message("user",
          ReaderAiRequest.sourceMessage(article.title, article.sourceUrl,
            article.text)));
    appendTurns(messages, turns);
    messages.add(new ReaderAiOpenRouter.Message("user", checkedQuestion(question)));
    return client.generate(apiKey, modelId, messages);
  }

  String followUp(String apiKey, String modelId, Article article,
      String originatingPrompt, String initialSummary, List<ChatTurn> turns,
      String question) throws IOException, JSONException
  {
    List<ReaderAiOpenRouter.Message> messages = new ArrayList<>();
    messages.add(new ReaderAiOpenRouter.Message("system", originatingPrompt));
    messages.add(new ReaderAiOpenRouter.Message("user",
          ReaderAiRequest.sourceMessage(article.title, article.sourceUrl,
            article.text)));
    messages.add(new ReaderAiOpenRouter.Message("assistant", initialSummary));
    appendTurns(messages, turns);
    messages.add(new ReaderAiOpenRouter.Message("user",
          "Question: " + checkedQuestion(question)));
    return client.generate(apiKey, modelId, messages);
  }

  String quiz(String apiKey, String modelId, Article article, String prompt,
      int questionCount) throws IOException, JSONException
  {
    if (questionCount != 6 && questionCount != 10 && questionCount != 12
        && questionCount != 20)
      throw new IllegalArgumentException("Unsupported article quiz size");
    return generate(apiKey, modelId, prompt,
        ReaderAiRequest.quizSourceMessage(article.title, article.sourceUrl,
          article.text, questionCount));
  }

  void cancel()
  {
    client.cancel();
  }

  static String chatMarkdown(List<ChatTurn> turns)
  {
    if (turns == null || turns.isEmpty())
      return "";
    StringBuilder result = new StringBuilder();
    for (ChatTurn turn : turns)
    {
      if (result.length() > 0)
        result.append("\n\n");
      result.append("## You\n\n").append(turn.question)
        .append("\n\n## AI\n\n").append(turn.answer);
    }
    return result.toString();
  }

  private String generate(String apiKey, String modelId, String prompt,
      String source) throws IOException, JSONException
  {
    return client.generate(apiKey, modelId, messages(prompt, source));
  }

  private static List<ReaderAiOpenRouter.Message> messages(String prompt,
      String source)
  {
    List<ReaderAiOpenRouter.Message> messages = new ArrayList<>(2);
    messages.add(new ReaderAiOpenRouter.Message("system", prompt));
    messages.add(new ReaderAiOpenRouter.Message("user", source));
    return messages;
  }

  private static void appendTurns(List<ReaderAiOpenRouter.Message> messages,
      List<ChatTurn> turns)
  {
    for (ChatTurn turn : turns == null ? Collections.<ChatTurn>emptyList() : turns)
    {
      messages.add(new ReaderAiOpenRouter.Message("user", turn.question));
      messages.add(new ReaderAiOpenRouter.Message("assistant", turn.answer));
    }
  }

  private static String checkedQuestion(String value)
  {
    String question = value == null ? "" : value.trim();
    if (question.isEmpty())
      throw new IllegalArgumentException("Enter a question");
    if (question.length() > 2000)
      throw new IllegalArgumentException("Question is longer than 2,000 characters");
    return question;
  }
}
