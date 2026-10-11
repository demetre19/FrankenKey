package juloo.keyboard2;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Temporary diagnostics for the inline-prediction ghost. Appends one line
    per event to Download/frankenkey-ghost.log via MediaStore — readable by
    the user in any file manager. Cheap, synchronous, no permission needed. */
public final class GhostLog
{
  private GhostLog() {}

  private static final String NAME = "frankenkey-ghost.log";
  private static Uri _uri = null;
  private static boolean _resolved = false;
  private static final SimpleDateFormat TS =
    new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);

  public static synchronized void log(Context ctx, String event)
  {
    if (ctx == null)
      return;
    try
    {
      Uri uri = resolve(ctx.getContentResolver());
      if (uri == null)
        return;
      String line = TS.format(new Date()) + " " + event + "\n";
      OutputStream out =
        ctx.getContentResolver().openOutputStream(uri, "wa");
      if (out == null)
        return;
      out.write(line.getBytes("UTF-8"));
      out.close();
    }
    catch (Throwable ignored) {}
  }

  private static Uri resolve(ContentResolver cr)
  {
    if (_resolved)
      return _uri;
    _resolved = true;
    try
    {
      Uri collection = MediaStore.Downloads.getContentUri(
          MediaStore.VOLUME_EXTERNAL_PRIMARY);
      String[] proj = { MediaStore.MediaColumns._ID };
      try (android.database.Cursor c = cr.query(collection, proj,
          MediaStore.MediaColumns.DISPLAY_NAME + "=? AND "
              + MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ?",
          new String[] { NAME, Environment.DIRECTORY_DOWNLOADS + "%" },
          null))
      {
        if (c != null && c.moveToFirst())
        {
          _uri = Uri.withAppendedPath(collection, c.getString(0));
          return _uri;
        }
      }
      ContentValues v = new ContentValues();
      v.put(MediaStore.MediaColumns.DISPLAY_NAME, NAME);
      v.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
      v.put(MediaStore.MediaColumns.RELATIVE_PATH,
          Environment.DIRECTORY_DOWNLOADS);
      _uri = cr.insert(collection, v);
    }
    catch (Throwable ignored) {}
    return _uri;
  }
}
