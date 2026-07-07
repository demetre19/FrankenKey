package juloo.keyboard2;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

public final class GifContentProvider extends ContentProvider
{
  @Override
  public boolean onCreate()
  {
    return true;
  }

  @Override
  public String getType(Uri uri)
  {
    return resolve(uri) != null ? GifInserter.GIF_MIME_TYPE : null;
  }

  @Override
  public Cursor query(Uri uri, String[] projection, String selection,
      String[] selectionArgs, String sortOrder)
  {
    ResolvedGif gif = resolve(uri);
    if (gif == null)
      return null;
    if (projection == null)
      projection = new String[]{ OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE };
    MatrixCursor cursor = new MatrixCursor(projection, 1);
    MatrixCursor.RowBuilder row = cursor.newRow();
    for (String column : projection)
    {
      if (OpenableColumns.DISPLAY_NAME.equals(column))
        row.add(gif.displayName);
      else if (OpenableColumns.SIZE.equals(column))
        row.add((long)gif.size);
      else
        row.add(null);
    }
    return cursor;
  }

  @Override
  public ParcelFileDescriptor openFile(Uri uri, String mode)
      throws FileNotFoundException
  {
    if (mode == null || !mode.equals("r"))
      throw new FileNotFoundException(uri.toString());
    final ResolvedGif gif = resolve(uri);
    if (gif == null)
      throw new FileNotFoundException(uri.toString());
    final Context context = getContext();
    if (context == null)
      throw new FileNotFoundException(uri.toString());
    return openPipeHelper(uri, GifInserter.GIF_MIME_TYPE, null, null,
        new PipeDataWriter<Object>() {
          public void writeDataToPipe(ParcelFileDescriptor output, Uri uri,
              String mimeType, Bundle opts, Object args)
          {
            copyGif(context, gif, output);
          }
        });
  }

  @Override
  public Uri insert(Uri uri, ContentValues values)
  {
    return null;
  }

  @Override
  public int delete(Uri uri, String selection, String[] selectionArgs)
  {
    return 0;
  }

  @Override
  public int update(Uri uri, ContentValues values, String selection,
      String[] selectionArgs)
  {
    return 0;
  }

  private ResolvedGif resolve(Uri uri)
  {
    if (uri == null)
      return null;
    List<String> segments = uri.getPathSegments();
    if (segments.size() != 2)
      return null;
    String kind = segments.get(0);
    String id = segments.get(1);
    if ("local".equals(kind))
    {
      GifResult item = GifLibrary.byId(id);
      if (item == null)
        return null;
      return ResolvedGif.local(item);
    }
    if ("cache".equals(kind))
    {
      File file = cachedGif(id);
      if (file == null || !file.isFile())
        return null;
      return ResolvedGif.cached(id, file);
    }
    return null;
  }

  private File cachedGif(String fileName)
  {
    Context context = getContext();
    if (context == null || fileName == null || fileName.indexOf('/') != -1 ||
        fileName.indexOf("..") != -1 || !fileName.endsWith(".gif"))
      return null;
    return new File(new File(context.getCacheDir(), "giphy_gifs"), fileName);
  }

  static void copyGif(Context context, ResolvedGif gif,
      ParcelFileDescriptor output)
  {
    byte[] buffer = new byte[8192];
    try (
        InputStream input = gif.open(context);
        OutputStream out = new ParcelFileDescriptor.AutoCloseOutputStream(output))
    {
      int read;
      while ((read = input.read(buffer)) != -1)
        out.write(buffer, 0, read);
    }
    catch (IOException _e)
    {
    }
  }

  static final class ResolvedGif
  {
    final GifResult local;
    final File cacheFile;
    final String displayName;
    final int size;

    private ResolvedGif(GifResult local, File cacheFile, String displayName,
        int size)
    {
      this.local = local;
      this.cacheFile = cacheFile;
      this.displayName = displayName;
      this.size = size;
    }

    static ResolvedGif local(GifResult item)
    {
      return new ResolvedGif(item, null, item.fileName(), item.size);
    }

    static ResolvedGif cached(String fileName, File file)
    {
      return new ResolvedGif(null, file, fileName,
          (int)Math.min(Integer.MAX_VALUE, file.length()));
    }

    InputStream open(Context context) throws IOException
    {
      if (local != null)
        return context.getResources().openRawResource(local.rawResId);
      return new FileInputStream(cacheFile);
    }
  }
}
