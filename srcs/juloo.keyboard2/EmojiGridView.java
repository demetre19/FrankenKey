package juloo.keyboard2;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.AttributeSet;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class EmojiGridView extends GridView
  implements GridView.OnItemClickListener
{
  public static final int GROUP_LAST_USE = -1;

  private static final String LAST_USE_PREF = "emoji_last_use";

  private List<Emoji> _emojiArray;
  private HashMap<Emoji, Integer> _lastUsed;
  private int _currentGroup;
  private String _searchQuery = "";

  /*
   ** TODO: adapt column width and emoji size
   ** TODO: use ArraySet instead of Emoji[]
   */
  public EmojiGridView(Context context, AttributeSet attrs)
  {
    super(context, attrs);
    Emoji.init(context.getResources());
    setOnItemClickListener(this);
    loadLastUsed();
    setEmojiGroup((_lastUsed.size() == 0) ? 0 : GROUP_LAST_USE);
  }

  public void setEmojiGroup(int group)
  {
    _currentGroup = group;
    if (_searchQuery.length() == 0)
      showEmojiList((group == GROUP_LAST_USE) ? getLastEmojis() : Emoji.getEmojisByGroup(group));
  }

  public void setSearchQuery(String query)
  {
    _searchQuery = query == null ? "" : query.trim();
    if (_searchQuery.length() == 0)
      showEmojiList((_currentGroup == GROUP_LAST_USE) ? getLastEmojis() : Emoji.getEmojisByGroup(_currentGroup));
    else
      showEmojiList(Emoji.search(_searchQuery));
  }

  private void showEmojiList(List<Emoji> emojis)
  {
    _emojiArray = emojis;
    setAdapter(new EmojiViewAdpater(getContext(), _emojiArray));
  }

  public void onItemClick(AdapterView<?> parent, View v, int pos, long id)
  {
    Emoji emoji = _emojiArray.get(pos);
    Integer used = _lastUsed.get(emoji);
    _lastUsed.put(emoji, (used == null) ? 1 : used.intValue() + 1);
    EmojiSearchView pane = getEmojiSearchView();
    if (pane == null || !pane.insertEmoji(emoji))
    {
      Config config = Config.globalConfig();
      config.handler.key_up(emoji.kv(), Pointers.Modifiers.EMPTY, null);
    }
    saveLastUsed(); // TODO: opti
  }

  private EmojiSearchView getEmojiSearchView()
  {
    ViewParent parent = getParent();
    while (parent != null)
    {
      if (parent instanceof EmojiSearchView)
        return (EmojiSearchView)parent;
      parent = parent.getParent();
    }
    return null;
  }

  private List<Emoji> getLastEmojis()
  {
    List<Emoji> list = new ArrayList<>(_lastUsed.keySet());
    Collections.sort(list, new Comparator<Emoji>()
        {
          public int compare(Emoji a, Emoji b)
          {
            return _lastUsed.get(b) - _lastUsed.get(a);
          }
        });
    return list;
  }

  private void saveLastUsed()
  {
    SharedPreferences.Editor edit;
    try { edit = emojiSharedPreferences().edit(); }
    catch (Exception _e) { return; }
    HashSet<String> set = new HashSet<String>();
    for (Emoji emoji : _lastUsed.keySet())
      set.add(String.valueOf(_lastUsed.get(emoji)) + "-" + emoji.kv().getString());
    edit.putStringSet(LAST_USE_PREF, set);
    edit.apply();
  }

  private void loadLastUsed()
  {
    _lastUsed = new HashMap<Emoji, Integer>();
    SharedPreferences prefs;
    // Storage might not be available (eg. the device is locked), avoid
    // crashing.
    try { prefs = emojiSharedPreferences(); }
    catch (Exception _e) { return; }
    Set<String> lastUseSet = prefs.getStringSet(LAST_USE_PREF, null);
    if (lastUseSet != null)
      for (String emojiData : lastUseSet)
      {
        String[] data = emojiData.split("-", 2);
        Emoji emoji;
        if (data.length != 2)
          continue ;
        emoji = Emoji.getEmojiByString(data[1]);
        if (emoji == null)
          continue ;
        _lastUsed.put(emoji, Integer.valueOf(data[0]));
      }
  }

  SharedPreferences emojiSharedPreferences()
  {
    return getContext().getSharedPreferences("emoji_last_use", Context.MODE_PRIVATE);
  }

  static class EmojiView extends TextView
  {
    public EmojiView(Context context)
    {
      super(context);
    }

    public void setEmoji(Emoji emoji)
    {
      setText(emoji.kv().getString());
    }
  }

  static class EmojiViewAdpater extends BaseAdapter
  {
    Context _button_context;

    List<Emoji> _emojiArray;

    public EmojiViewAdpater(Context context, List<Emoji> emojiArray)
    {
      _button_context = new ContextThemeWrapper(context, R.style.emojiGridButton);
      _emojiArray = emojiArray;
    }

    public int getCount()
    {
      if (_emojiArray == null)
        return (0);
      return (_emojiArray.size());
    }

    public Object getItem(int pos)
    {
      return (_emojiArray.get(pos));
    }

    public long getItemId(int pos)
    {
      return (pos);
    }

    public View getView(int pos, View convertView, ViewGroup parent)
    {
      EmojiView view = (EmojiView)convertView;

      if (view == null)
        view = new EmojiView(_button_context);
      view.setEmoji(_emojiArray.get(pos));
      return view;
    }
  }
}
