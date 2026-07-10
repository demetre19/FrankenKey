package juloo.keyboard2;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class ClipboardPinView extends NonScrollGridView
{
  /** Preference file name that store pinned clipboards. */
  static final String PERSIST_FILE_NAME = "clipboards";
  /** Preference name for pinned clipboards. */
  static final String PERSIST_PREF = "pinned";

  List<ClipboardHistoryService.ClipboardEntry> _entries;
  ClipboardPinEntriesAdapter _adapter;
  SharedPreferences _persist_store;
  private static final int TIPS_HIDE_PIN_COUNT = 3;

  public ClipboardPinView(Context ctx, AttributeSet attrs)
  {
    super(ctx, attrs);
    _entries = new ArrayList<ClipboardHistoryService.ClipboardEntry>();
    // Storage is not be available in direct-boot mode.
    _persist_store = null;
    try
    {
      _persist_store =
        ctx.getSharedPreferences("pinned_clipboards", Context.MODE_PRIVATE);
      load_from_prefs(_persist_store, _entries);
    }
    catch (Exception _e) {}
    _adapter = this.new ClipboardPinEntriesAdapter();
    setAdapter(_adapter);
    update_tips_visibility();
  }

  /** Pin clipboard text and persist the change. */
  public void add_entry(String text)
  {
    add_entry(ClipboardHistoryService.ClipboardEntry.text(text));
  }

  /** Pin a clipboard entry and persist the change. */
  public void add_entry(ClipboardHistoryService.ClipboardEntry entry)
  {
    if (entry == null || entry.isEmpty())
      return;
    _entries.add(0,entry);
    _adapter.notifyDataSetChanged();
    persist();
    invalidate();
    update_tips_visibility();
  }

  /** Remove the entry at index [pos] and persist the change. */
  public void remove_entry(int pos)
  {
    if (pos < 0 || pos >= _entries.size())
      return;
    _entries.remove(pos);
    _adapter.notifyDataSetChanged();
    persist();
    invalidate();
    update_tips_visibility();
  }

  /** Send the specified entry to the editor. */
  public void paste_entry(int pos)
  {
    ClipboardHistoryService.paste(_entries.get(pos));
  }

  /** Open text clips for editing; image clips paste directly. */
  public void open_entry(final int pos, View source)
  {
    final ClipboardHistoryService.ClipboardEntry clip = _entries.get(pos);
    if (clip.isImage())
    {
      paste_entry(pos);
      return;
    }
    ClipboardItemEditorView editor = ClipboardItemEditorView.findFrom(source);
    if (editor == null)
    {
      paste_entry(pos);
      return;
    }
    editor.open(clip.text, new ClipboardItemEditorView.Listener()
    {
      @Override
      public void onPasteEdited(String editedText)
      {
        ClipboardHistoryService.ClipboardEntry edited =
          ClipboardHistoryService.ClipboardEntry.text(editedText);
        if (!edited.isEmpty())
        {
          _entries.set(pos, edited);
          _adapter.notifyDataSetChanged();
          persist();
        }
        ClipboardHistoryService.paste(editedText);
      }
    });
  }

  @Override
  protected void onAttachedToWindow()
  {
    super.onAttachedToWindow();
    update_tips_visibility();
  }

  void update_tips_visibility()
  {
    View tips = find_tips_section();
    if (tips != null)
      tips.setVisibility(_entries.size() >= TIPS_HIDE_PIN_COUNT
          ? View.GONE : View.VISIBLE);
  }

  private View find_tips_section()
  {
    View parent = this;
    while (parent != null)
    {
      View tips = parent.findViewById(R.id.clipboard_tips_section);
      if (tips != null)
        return tips;
      if (!(parent.getParent() instanceof View))
        return null;
      parent = (View)parent.getParent();
    }
    return null;
  }

  static void load_from_prefs(SharedPreferences store,
      List<ClipboardHistoryService.ClipboardEntry> dst)
  {
    String arr_s = store.getString(PERSIST_PREF, null);
    if (arr_s == null)
      return;
    try
    {
      JSONArray arr = new JSONArray(arr_s);
      for (int i = 0; i < arr.length(); i++)
      {
        Object item = arr.get(i);
        ClipboardHistoryService.ClipboardEntry entry = entry_from_json(item);
        if (entry != null && !entry.isEmpty())
          dst.add(entry);
      }
    }
    catch (JSONException _e) {}
  }

  void persist()
  {
    if (_persist_store == null)
      return;
    JSONArray arr = new JSONArray();
    for (int i = 0; i < _entries.size(); i++)
      arr.put(entry_to_json(_entries.get(i)));
    _persist_store.edit()
      .putString(PERSIST_PREF, arr.toString())
      .apply();
  }

  private static Object entry_to_json(ClipboardHistoryService.ClipboardEntry entry)
  {
    if (!entry.isImage())
      return entry.text;
    JSONObject obj = new JSONObject();
    try
    {
      obj.put("type", "image");
      obj.put("uri", entry.uri);
      obj.put("mimeType", entry.mimeType);
      obj.put("label", entry.displayText());
    }
    catch (JSONException _e) {}
    return obj;
  }

  private static ClipboardHistoryService.ClipboardEntry entry_from_json(Object item)
      throws JSONException
  {
    if (item instanceof String)
      return ClipboardHistoryService.ClipboardEntry.text((String)item);
    if (!(item instanceof JSONObject))
      return null;
    JSONObject obj = (JSONObject)item;
    if (!"image".equals(obj.optString("type")))
      return ClipboardHistoryService.ClipboardEntry.text(obj.optString("text"));
    return ClipboardHistoryService.ClipboardEntry.image(obj.optString("uri"),
        obj.optString("mimeType"), obj.optString("label", "Screenshot"));
  }

  class ClipboardPinEntriesAdapter extends BaseAdapter
  {
    public ClipboardPinEntriesAdapter() {}

    @Override
    public int getCount() { return _entries.size(); }
    @Override
    public Object getItem(int pos) { return _entries.get(pos); }
    @Override
    public long getItemId(int pos) { return _entries.get(pos).displayText().hashCode(); }

    @Override
    public View getView(final int pos, View v, ViewGroup _parent)
    {
      if (v == null)
        v = View.inflate(getContext(), R.layout.clipboard_pin_entry, null);
      enforceFixedCard(v, R.id.clipboard_pin_text);
      bind_entry(v, _entries.get(pos));
      v.setOnClickListener(
          new View.OnClickListener()
          {
            @Override
            public void onClick(View v) { open_entry(pos, v); }
          });
      v.findViewById(R.id.clipboard_pin_paste).setOnClickListener(
          new View.OnClickListener()
          {
            @Override
            public void onClick(View v) { paste_entry(pos); }
          });
      v.findViewById(R.id.clipboard_pin_remove).setOnClickListener(
          new View.OnClickListener()
          {
            @Override
            public void onClick(View v)
            {
              AlertDialog d = new AlertDialog.Builder(getContext())
                .setTitle(R.string.clipboard_remove_confirm)
                .setPositiveButton(R.string.clipboard_remove_confirmed,
                    new DialogInterface.OnClickListener(){
                      public void onClick(DialogInterface _dialog, int _which)
                      {
                        remove_entry(pos);
                      }
                    })
                .setNegativeButton(android.R.string.cancel, null)
                .create();
              Utils.show_dialog_on_ime(d, v.getWindowToken());
            }
          });
      return v;
    }

    private void bind_entry(View v, ClipboardHistoryService.ClipboardEntry entry)
    {
      TextView text = (TextView)v.findViewById(R.id.clipboard_pin_text);
      ImageView image = (ImageView)v.findViewById(R.id.clipboard_pin_image);
      text.setText(entry.displayText());
      if (!entry.isImage())
      {
        if (image != null)
          image.setVisibility(View.GONE);
        text.setVisibility(View.VISIBLE);
        return;
      }
      if (image != null)
      {
        image.setVisibility(View.VISIBLE);
        try { image.setImageURI(Uri.parse(entry.uri)); }
        catch (Exception _e) { image.setVisibility(View.GONE); }
      }
      text.setVisibility(View.VISIBLE);
    }
  }
}
