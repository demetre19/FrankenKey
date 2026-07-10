package juloo.keyboard2.snippets;

public final class SnippetSlot
{
  public static final int PAGE_SIZE = 7;

  private final int _index;
  private final String _phrase;
  private final String _customLabel;

  private SnippetSlot(int index, String phrase, String customLabel)
  {
    if (index < 0)
      throw new IllegalArgumentException("index must be non-negative");
    _index = index;
    _phrase = phrase == null ? "" : phrase;
    _customLabel = customLabel == null ? "" : customLabel;
  }

  public static SnippetSlot of(int index, String phrase, String customLabel)
  {
    return new SnippetSlot(index, phrase, customLabel);
  }

  public int getIndex()
  {
    return _index;
  }

  public String getPhrase()
  {
    return _phrase;
  }

  public String getCustomLabel()
  {
    return _customLabel;
  }

  public boolean isConfigured()
  {
    return !_phrase.isEmpty();
  }

  public String getDisplayLabel()
  {
    if (!_customLabel.isEmpty())
      return _customLabel;
    if (_phrase.isEmpty())
      return String.valueOf(_index + 1);
    return firstCodePoints(_phrase, 2);
  }

  private static String firstCodePoints(String text, int maxCodePoints)
  {
    int count = text.codePointCount(0, text.length());
    int end = text.offsetByCodePoints(0, Math.min(maxCodePoints, count));
    return text.substring(0, end);
  }
}
