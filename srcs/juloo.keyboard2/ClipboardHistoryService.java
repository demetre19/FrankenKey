package juloo.keyboard2;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.ContentUris;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build.VERSION;
import android.os.Handler;
import android.provider.MediaStore;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public final class ClipboardHistoryService
{
  /** Start the service on startup and start listening to clipboard changes. */
  public static void on_startup(Context ctx, ClipboardPasteCallback cb)
  {
    get_service(ctx);
    _paste_callback = cb;
  }

  /** Start the service if it hasn't been started before. Returns [null] if the
      feature is unsupported. */
  public static ClipboardHistoryService get_service(Context ctx)
  {
    if (VERSION.SDK_INT <= 11)
      return null;
    if (_service == null)
      _service = new ClipboardHistoryService(ctx);
    return _service;
  }

  public static void set_history_enabled(boolean e)
  {
    Config.globalConfig().set_clipboard_history_enabled(e);
    if (_service == null)
      return;
    if (e)
      _service.add_current_clip();
    else
      _service.clear_history();
  }

  public static void refresh_screenshot_observer()
  {
    if (_service != null)
      _service.start_screenshot_observer_if_allowed();
  }

  static boolean hasScreenshotReadPermission(Context ctx)
  {
    if (VERSION.SDK_INT < 23)
      return true;
    if (VERSION.SDK_INT >= 34
        && ctx.checkSelfPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
          == PackageManager.PERMISSION_GRANTED)
      return true;
    String permission = VERSION.SDK_INT >= 33
      ? Manifest.permission.READ_MEDIA_IMAGES
      : Manifest.permission.READ_EXTERNAL_STORAGE;
    return ctx.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
  }

  /** Send the given string to the editor. */
  public static void paste(String clip)
  {
    if (_paste_callback != null)
      _paste_callback.paste_from_clipboard_pane(clip);
  }

  /** Send the given clipboard entry to the editor. */
  public static void paste(ClipboardEntry clip)
  {
    if (clip == null || _paste_callback == null)
      return;
    if (clip.isImage())
      _paste_callback.paste_image_from_clipboard_pane(clip.uri,
          clip.mimeType, clip.displayText());
    else
      _paste_callback.paste_from_clipboard_pane(clip.text);
  }

  /** Paste the current system clipboard through the keyboard's own text/content
      path. Returns false when there is no supported clipboard content. */
  public static boolean paste_current_clip()
  {
    if (_service == null)
      return false;
    ClipboardEntry clip = _service.get_current_clip_entry();
    if (clip == null)
      return false;
    paste(clip);
    return true;
  }

  /** The maximum size limits the amount of user data stored in memory but also
      gives a sense to the user that the history is not persisted and can be
      forgotten as soon as the app stops. */
  public static final int MAX_HISTORY_SIZE = 50;

  static ClipboardHistoryService _service = null;
  static ClipboardPasteCallback _paste_callback = null;

  Context _ctx;
  ClipboardManager _cm;
  List<HistoryEntry> _history;
  OnClipboardHistoryChange _listener = null;
  ContentObserver _screenshotObserver = null;


  ClipboardHistoryService(Context ctx)
  {
    _ctx = ctx.getApplicationContext() == null ? ctx : ctx.getApplicationContext();
    _history = new ArrayList<HistoryEntry>();
    _cm = (ClipboardManager)ctx.getSystemService(Context.CLIPBOARD_SERVICE);
    _cm.addPrimaryClipChangedListener(this.new SystemListener());
    start_screenshot_observer_if_allowed();
  }

  public synchronized List<String> clear_expired_and_get_history()
  {
    List<ClipboardEntry> entries = clear_expired_and_get_entries();
    List<String> dst = new ArrayList<String>();
    for (ClipboardEntry entry : entries)
      if (!entry.isImage())
        dst.add(entry.text);
    return dst;
  }

  public synchronized List<ClipboardEntry> clear_expired_and_get_entries()
  {
    long now_ms = System.currentTimeMillis();
    List<ClipboardEntry> dst = new ArrayList<ClipboardEntry>();
    Iterator<HistoryEntry> it = _history.iterator();
    while (it.hasNext())
    {
      HistoryEntry ent = it.next();
      if (ent.expiry_timestamp <= now_ms)
        it.remove();
      else
        dst.add(ent.content);
    }
    return dst;
  }

  /** This will call [on_clipboard_history_change]. */
  public synchronized void remove_history_entry(String clip)
  {
    remove_history_entry(ClipboardEntry.text(clip));
  }

  /** This will call [on_clipboard_history_change]. */
  public synchronized void remove_history_entry(ClipboardEntry clip)
  {
    int last_pos = _history.size() - 1;
    boolean last_pos_changed = false;
    for (int pos = last_pos; pos >= 0; pos--)
    {
      if (!_history.get(pos).content.sameContent(clip))
        continue;
      // Removing the current clipboard, clear the system clipboard.
      if (pos == 0)
        last_pos_changed = true;
      _history.remove(pos);
    }
    if (last_pos_changed)
    {
      if (VERSION.SDK_INT >= 28)
        _cm.clearPrimaryClip();
      else
        _cm.setText("");
    }
    if (_listener != null)
      _listener.on_clipboard_history_change();
  }

  /** Replace the first matching history entry and notify the active view. */
  public synchronized void replace_history_entry(ClipboardEntry oldClip,
      ClipboardEntry newClip)
  {
    if (newClip == null || newClip.isEmpty())
      return;
    for (int pos = 0; pos < _history.size(); pos++)
    {
      HistoryEntry entry = _history.get(pos);
      if (!entry.content.sameContent(oldClip))
        continue;
      _history.set(pos, new HistoryEntry(newClip, entry.expiry_timestamp));
      if (_listener != null)
        _listener.on_clipboard_history_change();
      return;
    }
  }

  /** Add clipboard text to the history, skipping consecutive duplicates and
      empty strings. */
  public synchronized void add_clip(String clip)
  {
    add_clip(ClipboardEntry.text(clip));
  }

  /** Add clipboard entries to the history, skipping consecutive duplicates and
      empty entries. */
  public synchronized void add_clip(ClipboardEntry clip)
  {
    if (!Config.globalConfig().clipboard_history_enabled)
      return;
    int size = _history.size();
    if (clip == null || clip.isEmpty()
        || (size > 0 && _history.get(0).content.sameContent(clip)))
      return;
    if (size >= MAX_HISTORY_SIZE)
      _history.remove(size - 1);
    _history.add(0,new HistoryEntry(clip));
    if (_listener != null)
      _listener.on_clipboard_history_change();
  }

  public synchronized void clear_history()
  {
    _history.clear();
    if (_listener != null)
      _listener.on_clipboard_history_change();
  }

  public void set_on_clipboard_history_change(OnClipboardHistoryChange l) { _listener = l; }

  public static interface OnClipboardHistoryChange
  {
    public void on_clipboard_history_change();
  }

  /** Add what is currently in the system clipboard into the history. */
  void add_current_clip()
  {
    ClipData clip = get_primary_clip();
    if (clip == null)
      return;
    int count = clip.getItemCount();
    for (int i = 0; i < count; i++)
    {
      ClipboardEntry entry = clip_item_entry(clip, i);
      if (entry != null)
        add_clip(entry);
    }
  }

  String get_current_clip_text()
  {
    ClipboardEntry entry = get_current_clip_entry();
    return entry == null || entry.isImage() ? null : entry.text;
  }

  ClipboardEntry get_current_clip_entry()
  {
    ClipData clip = get_primary_clip();
    if (clip == null)
      return null;
    int count = clip.getItemCount();
    for (int i = 0; i < count; i++)
    {
      ClipboardEntry entry = clip_item_entry(clip, i);
      if (entry != null && !entry.isEmpty())
        return entry;
    }
    return null;
  }

  private void start_screenshot_observer_if_allowed()
  {
    if (!Config.globalConfig().clipboard_save_screenshots
        || !hasScreenshotReadPermission(_ctx))
      return;
    if (_screenshotObserver != null)
    {
      add_latest_screenshot_from_media_store();
      return;
    }
    _screenshotObserver = new ContentObserver(new Handler(_ctx.getMainLooper()))
    {
      @Override
      public void onChange(boolean selfChange)
      {
        add_latest_screenshot_from_media_store();
      }

      @Override
      public void onChange(boolean selfChange, Uri uri)
      {
        add_latest_screenshot_from_media_store();
      }
    };
    try
    {
      _ctx.getContentResolver().registerContentObserver(
          MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true,
          _screenshotObserver);
    }
    catch (Exception _e)
    {
      _screenshotObserver = null;
      return;
    }
    add_latest_screenshot_from_media_store();
  }

  void add_latest_screenshot_from_media_store()
  {
    ClipboardEntry entry = latest_screenshot_entry();
    if (entry != null)
      add_clip(entry);
  }

  ClipboardEntry latest_screenshot_entry()
  {
    if (!hasScreenshotReadPermission(_ctx))
      return null;
    String pathColumn = VERSION.SDK_INT >= 29
      ? MediaStore.Images.Media.RELATIVE_PATH
      : MediaStore.Images.Media.DATA;
    String[] projection = new String[]{
      MediaStore.Images.Media._ID,
      MediaStore.Images.Media.DISPLAY_NAME,
      pathColumn,
      MediaStore.Images.Media.MIME_TYPE
    };
    Cursor cursor = null;
    try
    {
      cursor = _ctx.getContentResolver().query(
          MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, null, null,
          MediaStore.Images.Media.DATE_ADDED + " DESC");
      if (cursor == null)
        return null;
      int inspected = 0;
      while (cursor.moveToNext() && inspected++ < 25)
      {
        String displayName = getString(cursor, MediaStore.Images.Media.DISPLAY_NAME);
        String path = getString(cursor, pathColumn);
        if (!is_screenshot_candidate(displayName, path))
          continue;
        long id = cursor.getLong(cursor.getColumnIndexOrThrow(
              MediaStore.Images.Media._ID));
        String mimeType = getString(cursor, MediaStore.Images.Media.MIME_TYPE);
        if (!is_image_mime_type(mimeType))
          mimeType = "image/png";
        Uri uri = ContentUris.withAppendedId(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);
        return ClipboardEntry.image(uri.toString(), mimeType,
            displayName == null || displayName.length() == 0
            ? "Screenshot" : displayName);
      }
    }
    catch (Exception _e) {}
    finally
    {
      if (cursor != null)
        cursor.close();
    }
    return null;
  }

  static boolean is_screenshot_candidate(String displayName, String path)
  {
    String haystack = ((displayName == null ? "" : displayName) + " "
        + (path == null ? "" : path)).toLowerCase(Locale.US);
    return haystack.contains("screenshot")
      || haystack.contains("screen shot")
      || haystack.contains("screenshots");
  }

  private static String getString(Cursor cursor, String column)
  {
    int index = cursor.getColumnIndex(column);
    return index < 0 || cursor.isNull(index) ? null : cursor.getString(index);
  }

  private ClipData get_primary_clip()
  {
    // getPrimaryClip might throw when the keyboard is disconnected.
    try { return _cm.getPrimaryClip(); } catch (Exception _e) { return null; }
  }

  private ClipboardEntry clip_item_entry(ClipData clip, int index)
  {
    ClipData.Item item = clip.getItemAt(index);
    ClipboardEntry image = clip_item_image(clip.getDescription(), item);
    if (image != null)
      return image;
    String text = clip_item_text(item);
    return text == null ? null : ClipboardEntry.text(text);
  }

  private ClipboardEntry clip_item_image(ClipDescription description,
      ClipData.Item item)
  {
    if (!Config.globalConfig().clipboard_save_screenshots)
      return null;
    if (item == null || item.getUri() == null)
      return null;
    Uri uri = item.getUri();
    String mimeType = image_mime_type(description, uri);
    if (mimeType == null)
      return null;
    return ClipboardEntry.image(uri.toString(), mimeType,
        image_label(description));
  }

  private String image_mime_type(ClipDescription description, Uri uri)
  {
    if (description != null)
    {
      for (int i = 0; i < description.getMimeTypeCount(); i++)
      {
        String mimeType = description.getMimeType(i);
        if (is_image_mime_type(mimeType))
          return mimeType;
      }
    }
    try
    {
      String mimeType = _ctx.getContentResolver().getType(uri);
      if (is_image_mime_type(mimeType))
        return mimeType;
    }
    catch (Exception _e) {}
    return null;
  }

  private String image_label(ClipDescription description)
  {
    if (description != null && description.getLabel() != null
        && description.getLabel().length() > 0)
      return description.getLabel().toString();
    return "Screenshot";
  }

  private static boolean is_image_mime_type(String mimeType)
  {
    return mimeType != null && mimeType.startsWith("image/");
  }

  private String clip_item_text(ClipData.Item item)
  {
    if (item == null)
      return null;
    CharSequence text = item.coerceToText(_ctx);
    return text == null ? null : text.toString();
  }

  int get_history_ttl_minutes() {
    return Config.globalConfig().clipboard_history_duration;
  }

  final class SystemListener implements ClipboardManager.OnPrimaryClipChangedListener
  {
    public SystemListener() {}

    @Override
    public void onPrimaryClipChanged()
    {
      add_current_clip();
    }
  }

  static final class HistoryEntry
  {
    public final ClipboardEntry content;

    /** Time at which the entry expires. */
    public final long expiry_timestamp;

    public HistoryEntry(ClipboardEntry c)
    {
      this(c, expiryTimestampFromConfig());
    }

    public HistoryEntry(ClipboardEntry c, long expiryTimestamp)
    {
      content = c;
      expiry_timestamp = expiryTimestamp;
    }

    private static long expiryTimestampFromConfig()
    {
      final int historyTtlMinutes = Config.globalConfig().clipboard_history_duration;
      if (historyTtlMinutes >= 0)
        return System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(historyTtlMinutes);
      return Long.MAX_VALUE;
    }
  }

  public static final class ClipboardEntry
  {
    public final String text;
    public final String uri;
    public final String mimeType;

    private ClipboardEntry(String text_, String uri_, String mimeType_)
    {
      text = text_;
      uri = uri_;
      mimeType = mimeType_;
    }

    public static ClipboardEntry text(String text)
    {
      return new ClipboardEntry(text, null, null);
    }

    public static ClipboardEntry image(String uri, String mimeType, String label)
    {
      return new ClipboardEntry(label, uri, mimeType);
    }

    public boolean isImage()
    {
      return uri != null && uri.length() > 0 && mimeType != null
        && mimeType.startsWith("image/");
    }

    public boolean isEmpty()
    {
      return isImage() ? uri.length() == 0 : text == null || text.length() == 0;
    }

    public String displayText()
    {
      if (isImage())
        return text == null || text.length() == 0 ? "Screenshot" : text;
      return text;
    }

    public boolean sameContent(ClipboardEntry other)
    {
      if (other == null)
        return false;
      if (isImage() || other.isImage())
        return safeEquals(uri, other.uri) && safeEquals(mimeType, other.mimeType);
      return safeEquals(text, other.text);
    }

    private static boolean safeEquals(String a, String b)
    {
      return a == null ? b == null : a.equals(b);
    }
  }

  public interface ClipboardPasteCallback
  {
    public void paste_from_clipboard_pane(String content);
    public void paste_image_from_clipboard_pane(String uri, String mimeType,
        String description);
  }
}
