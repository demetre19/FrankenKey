package juloo.keyboard2;

import android.content.ClipDescription;
import android.content.Context;
import android.net.Uri;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import androidx.core.view.inputmethod.EditorInfoCompat;
import androidx.core.view.inputmethod.InputConnectionCompat;
import androidx.core.view.inputmethod.InputContentInfoCompat;

public final class GifInserter
{
  public static final String GIF_MIME_TYPE = "image/gif";
  static final String GIF_DESCRIPTION = "FrankenKey GIF";

  private GifInserter() {}

  public static boolean editorAcceptsGif(EditorInfo info)
  {
    if (info == null)
      return false;
    String[] mimeTypes = EditorInfoCompat.getContentMimeTypes(info);
    for (String mimeType : mimeTypes)
      if (acceptsGifMimeType(mimeType))
        return true;
    return false;
  }

  static boolean acceptsGifMimeType(String mimeType)
  {
    return GIF_MIME_TYPE.equals(mimeType) || "image/*".equals(mimeType) ||
        "*/*".equals(mimeType);
  }

  public static boolean insertGif(Context context, InputConnection conn,
      EditorInfo info)
  {
    return insertGif(context, conn, info, GifLibrary.LOCAL[0]);
  }

  public static boolean insertGif(Context context, InputConnection conn,
      EditorInfo info, GifResult item)
  {
    if (context == null || conn == null || item == null || !editorAcceptsGif(info))
      return false;
    Uri uri = item.isCached() ? GifLibrary.cacheUri(context, item) :
      GifLibrary.localUri(context, item);
    String description = item.title == null || item.title.length() == 0 ?
      GIF_DESCRIPTION : item.title;
    InputContentInfoCompat content = new InputContentInfoCompat(uri,
        new ClipDescription(description, new String[]{ GIF_MIME_TYPE }), null);
    int flags = 0;
    if (VERSION.SDK_INT >= VERSION_CODES.N_MR1)
      flags |= InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION;
    return InputConnectionCompat.commitContent(conn, info, content, flags, null);
  }
}
