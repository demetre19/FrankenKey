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
    return editorAcceptsImage(info, GIF_MIME_TYPE);
  }

  static boolean acceptsGifMimeType(String mimeType)
  {
    return GIF_MIME_TYPE.equals(mimeType) || "image/*".equals(mimeType) ||
        "*/*".equals(mimeType);
  }

  public static boolean editorAcceptsImage(EditorInfo info, String contentMimeType)
  {
    if (info == null || contentMimeType == null)
      return false;
    String[] mimeTypes = EditorInfoCompat.getContentMimeTypes(info);
    for (String mimeType : mimeTypes)
      if (acceptsContentMimeType(mimeType, contentMimeType))
        return true;
    return false;
  }

  static boolean acceptsContentMimeType(String acceptedMimeType,
      String contentMimeType)
  {
    if (acceptedMimeType == null || contentMimeType == null)
      return false;
    if ("*/*".equals(acceptedMimeType))
      return true;
    if (acceptedMimeType.equals(contentMimeType))
      return true;
    if (acceptedMimeType.endsWith("/*"))
      return contentMimeType.startsWith(
          acceptedMimeType.substring(0, acceptedMimeType.length() - 1));
    return false;
  }

  public static boolean insertGif(Context context, InputConnection conn,
      EditorInfo info)
  {
    return insertGif(context, conn, info, GifLibrary.LOCAL[0]);
  }

  public static boolean insertGif(Context context, InputConnection conn,
      EditorInfo info, GifResult item)
  {
    if (item == null)
      return false;
    Uri uri = item.isCached() ? GifLibrary.cacheUri(context, item) :
      GifLibrary.localUri(context, item);
    String description = item.title == null || item.title.length() == 0 ?
      GIF_DESCRIPTION : item.title;
    return insertImage(context, conn, info, uri, GIF_MIME_TYPE, description);
  }

  public static boolean insertImage(Context context, InputConnection conn,
      EditorInfo info, Uri uri, String mimeType, String description)
  {
    if (conn == null || uri == null || !editorAcceptsImage(info, mimeType))
      return false;
    String label = description == null || description.length() == 0 ?
      "FrankenKey image" : description;
    InputContentInfoCompat content = new InputContentInfoCompat(uri,
        new ClipDescription(label, new String[]{ mimeType }), null);
    int flags = 0;
    if (VERSION.SDK_INT >= VERSION_CODES.N_MR1)
      flags |= InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION;
    return InputConnectionCompat.commitContent(conn, info, content, flags, null);
  }
}
