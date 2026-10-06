package juloo.keyboard2;

import android.content.SharedPreferences;

/**
 * Actions assignable to the Reader AI omnibutton tap and swipe sectors.
 * Persisted in the keyboard SharedPreferences as
 * "reader_ai_button_<sector>" string ids.
 */
public enum ReaderAiAction
{
  NONE("none", R.string.reader_ai_action_none),
  OPEN_CHAT("open_chat", R.string.reader_ai_action_open_chat),
  VOICE("voice", R.string.reader_ai_action_voice),
  SUMMARY_ONE("summary_one", R.string.reader_ai_action_summary_one),
  SUMMARY_TWO("summary_two", R.string.reader_ai_action_summary_two),
  QUIZ("quiz", R.string.reader_ai_action_quiz),
  SHARE("share", R.string.reader_ai_action_share),
  SAVED("saved", R.string.reader_ai_action_saved),
  SPEED_READ("speed_read", R.string.reader_ai_action_speed_read),
  AI_SETTINGS("ai_settings", R.string.reader_ai_action_ai_settings),
  LOAD_CLIPBOARD("load_clipboard", R.string.reader_ai_action_load_clipboard),
  READ_CLIPBOARD("read_clipboard", R.string.reader_ai_action_read_clipboard);

  public final String id;
  public final int labelRes;

  ReaderAiAction(String id, int labelRes)
  {
    this.id = id;
    this.labelRes = labelRes;
  }

  public static ReaderAiAction ofId(String id)
  {
    if (id != null)
      for (ReaderAiAction action : values())
        if (action.id.equals(id))
          return action;
    return null;
  }

  /** The tap plus the eight swipe sectors around the centered AI button. */
  public enum Sector
  {
    TAP("tap", R.string.pref_reader_ai_button_tap,
        ReaderAiAction.OPEN_CHAT),
    UP("up", R.string.pref_reader_ai_button_swipe_up,
        ReaderAiAction.SUMMARY_ONE),
    UP_RIGHT("up_right", R.string.pref_reader_ai_button_swipe_up_right,
        ReaderAiAction.SUMMARY_TWO),
    RIGHT("right", R.string.pref_reader_ai_button_swipe_right,
        ReaderAiAction.QUIZ),
    DOWN_RIGHT("down_right", R.string.pref_reader_ai_button_swipe_down_right,
        ReaderAiAction.SHARE),
    DOWN("down", R.string.pref_reader_ai_button_swipe_down,
        ReaderAiAction.SAVED),
    DOWN_LEFT("down_left", R.string.pref_reader_ai_button_swipe_down_left,
        ReaderAiAction.SPEED_READ),
    LEFT("left", R.string.pref_reader_ai_button_swipe_left,
        ReaderAiAction.AI_SETTINGS),
    UP_LEFT("up_left", R.string.pref_reader_ai_button_swipe_up_left,
        ReaderAiAction.OPEN_CHAT);

    public final String key;
    public final int labelRes;
    public final ReaderAiAction defaultAction;

    Sector(String key, int labelRes, ReaderAiAction defaultAction)
    {
      this.key = key;
      this.labelRes = labelRes;
      this.defaultAction = defaultAction;
    }

    public String prefKey()
    {
      return "reader_ai_button_" + key;
    }
  }

  public static ReaderAiAction actionFor(SharedPreferences prefs, Sector sector)
  {
    ReaderAiAction action =
      ofId(prefs.getString(sector.prefKey(), sector.defaultAction.id));
    return action != null ? action : sector.defaultAction;
  }

  public static void setAction(SharedPreferences prefs, Sector sector,
      ReaderAiAction action)
  {
    prefs.edit().putString(sector.prefKey(), action.id).apply();
  }

  public static boolean aiOnRight(SharedPreferences prefs)
  {
    return prefs.getBoolean("reader_ai_button_ai_right", true);
  }

  public static void setAiOnRight(SharedPreferences prefs, boolean right)
  {
    prefs.edit().putBoolean("reader_ai_button_ai_right", right).apply();
  }

  /**
   * Maps a release offset to a sector. [dx] right, [dy] down (screen
   * coordinates); sectors are 45 degrees wide centered on the compass
   * directions.
   */
  public static Sector sectorFor(float dx, float dy)
  {
    double angle = Math.atan2(-dy, dx); // up = +90deg
    int index = (int)Math.floor((angle + Math.PI / 8.0) / (Math.PI / 4.0));
    // index 0 = right, counter-clockwise; normalize to 0..7
    index = ((index % 8) + 8) % 8;
    switch (index)
    {
      case 0: return Sector.RIGHT;
      case 1: return Sector.UP_RIGHT;
      case 2: return Sector.UP;
      case 3: return Sector.UP_LEFT;
      case 4: return Sector.LEFT;
      case 5: return Sector.DOWN_LEFT;
      case 6: return Sector.DOWN;
      default: return Sector.DOWN_RIGHT;
    }
  }
}
