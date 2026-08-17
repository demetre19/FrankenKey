package juloo.keyboard2;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.webkit.MimeTypeMap;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.UUID;

public final class ImageAttachmentPickerActivity extends Activity
{
  private static final int REQUEST_IMAGE = 1;
  static final long MAX_IMAGE_BYTES = 64L * 1024L * 1024L;
  private static final String CACHE_DIRECTORY = "image_attachments";
  private static final int MAX_CACHED_ATTACHMENTS = 16;
  private static final long MAX_CACHE_AGE_MILLIS =
    7L * 24L * 60L * 60L * 1000L;
  private boolean _pickerStarted;

  static Intent pickerIntent(int sdkInt)
  {
    Intent intent;
    if (sdkInt >= 33)
      intent = new Intent(MediaStore.ACTION_PICK_IMAGES);
    else
      intent = documentPickerIntent();
    return intent.setType("image/*")
      .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
  }

  static Intent documentPickerIntent()
  {
    return new Intent(Intent.ACTION_OPEN_DOCUMENT)
      .addCategory(Intent.CATEGORY_OPENABLE)
      .setType("image/*")
      .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
          Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
  }

  @Override
  protected void onCreate(Bundle state)
  {
    super.onCreate(state);
    _pickerStarted = state != null && state.getBoolean("picker_started");
    if (!_pickerStarted)
      launchPicker();
  }

  @Override
  protected void onSaveInstanceState(Bundle state)
  {
    state.putBoolean("picker_started", _pickerStarted);
    super.onSaveInstanceState(state);
  }

  private void launchPicker()
  {
    _pickerStarted = true;
    try
    {
      startActivityForResult(pickerIntent(android.os.Build.VERSION.SDK_INT),
          REQUEST_IMAGE);
    }
    catch (ActivityNotFoundException primaryError)
    {
      try
      {
        startActivityForResult(documentPickerIntent(), REQUEST_IMAGE);
      }
      catch (ActivityNotFoundException fallbackError)
      {
        Toast.makeText(this, R.string.reader_attach_image_unavailable,
            Toast.LENGTH_SHORT).show();
        finish();
      }
    }
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data)
  {
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode == REQUEST_IMAGE && resultCode == RESULT_OK && data != null)
      accept(data);
    finish();
  }

  private void accept(Intent data)
  {
    Uri selectedUri = data.getData();
    if (selectedUri == null)
      return;
    String mimeType = data.getType();
    if (mimeType == null)
      mimeType = getContentResolver().getType(selectedUri);
    if (mimeType == null || !mimeType.startsWith("image/"))
    {
      Toast.makeText(this, R.string.reader_attach_image_invalid,
          Toast.LENGTH_SHORT).show();
      return;
    }
    try
    {
      Uri ownedUri = cacheSelection(this, selectedUri, mimeType);
      PendingImageAttachment.set(ownedUri, mimeType,
          selectedUri.getLastPathSegment());
    }
    catch (IOException | SecurityException | IllegalArgumentException error)
    {
      Toast.makeText(this, R.string.reader_attach_image_failed,
          Toast.LENGTH_SHORT).show();
    }
  }

  static synchronized Uri cacheSelection(Context context, Uri selectedUri,
      String mimeType) throws IOException
  {
    if (context == null || selectedUri == null || mimeType == null ||
        !mimeType.startsWith("image/"))
      throw new IllegalArgumentException("A selected image is required");
    File directory = new File(context.getCacheDir(), CACHE_DIRECTORY);
    if (!directory.isDirectory() && !directory.mkdirs())
      throw new IOException("Unable to create attachment cache");
    File temporary = new File(directory,
        "pending-" + UUID.randomUUID().toString() + ".tmp");
    try
    {
      copySelection(context, selectedUri, temporary);
      String extension = MimeTypeMap.getSingleton()
        .getExtensionFromMimeType(mimeType);
      if (extension == null || extension.isEmpty())
        extension = "image";
      File attachment = new File(directory,
          "attachment-" + UUID.randomUUID().toString() + "." + extension);
      if (!temporary.renameTo(attachment))
        throw new IOException("Unable to finish attachment cache");
      return FileProvider.getUriForFile(context,
          context.getPackageName() + ".attachments", attachment);
    }
    catch (IOException | RuntimeException error)
    {
      temporary.delete();
      throw error;
    }
  }

  private static void copySelection(Context context, Uri selectedUri,
      File temporary) throws IOException
  {
    try (
        InputStream input =
          context.getContentResolver().openInputStream(selectedUri);
        FileOutputStream output = new FileOutputStream(temporary))
    {
      if (input == null)
        throw new IOException("Unable to open selected image");
      byte[] buffer = new byte[8192];
      long total = 0;
      int read;
      while ((read = input.read(buffer)) != -1)
      {
        total += read;
        if (total > MAX_IMAGE_BYTES)
          throw new IOException("Selected image is too large");
        output.write(buffer, 0, read);
      }
      if (total == 0)
        throw new IOException("Selected image is empty");
    }
  }

  static synchronized void pruneCache(Context context)
  {
    if (context == null)
      return;
    File directory = new File(context.getCacheDir(), CACHE_DIRECTORY);
    File[] files = directory.listFiles(file ->
        file.isFile() && file.getName().startsWith("attachment-"));
    if (files == null)
      return;
    Arrays.sort(files,
        (first, second) -> Long.compare(second.lastModified(),
          first.lastModified()));
    long oldestAllowed = System.currentTimeMillis() - MAX_CACHE_AGE_MILLIS;
    for (int i = 0; i < files.length; i++)
      if (i >= MAX_CACHED_ATTACHMENTS ||
          files[i].lastModified() < oldestAllowed)
        files[i].delete();
  }
}
