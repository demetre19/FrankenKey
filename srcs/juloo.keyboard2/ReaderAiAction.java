package juloo.keyboard2;

/** Omnibutton actions that call OpenRouter. None assigned by default. */
public enum ReaderAiAction
{
  FIX_GRAMMAR("fix_grammar", R.string.reader_ai_action_fix_grammar);

  public final String id;
  public final int labelRes;

  ReaderAiAction(String id_, int labelRes_)
  {
    id = id_;
    labelRes = labelRes_;
  }

  public static ReaderAiAction fromId(String id)
  {
    for (ReaderAiAction action : values())
      if (action.id.equals(id))
        return action;
    return null;
  }
}
