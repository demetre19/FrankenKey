package juloo.keyboard2.snippets;

import android.view.inputmethod.InputConnection;

public final class SnippetInserter
{
  private SnippetInserter() {}

  public static boolean insert(InputConnection conn, String phrase)
  {
    if (conn == null)
      return false;
    conn.commitText(phrase == null ? "" : phrase, 1);
    return true;
  }
}
