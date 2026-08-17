package juloo.keyboard2;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputContentInfo;
import androidx.core.view.inputmethod.EditorInfoCompat;
import androidx.core.view.inputmethod.InputConnectionCompat;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 35)
public class ImageAttachmentTest
{
  @After
  public void clearPendingAttachment()
  {
    PendingImageAttachment.Item item = PendingImageAttachment.peek();
    if (item != null)
      PendingImageAttachment.clear(item);
  }

  @Test
  public void imageCapabilityRequiresRichImageMimeType()
  {
    EditorInfo imageEditor = new EditorInfo();
    EditorInfoCompat.setContentMimeTypes(imageEditor,
        new String[] { "image/png", "text/plain" });
    assertTrue(GifInserter.editorAcceptsAnyImage(imageEditor));

    EditorInfo wildcardEditor = new EditorInfo();
    EditorInfoCompat.setContentMimeTypes(wildcardEditor,
        new String[] { "*/*" });
    assertTrue(GifInserter.editorAcceptsAnyImage(wildcardEditor));

    EditorInfo textEditor = new EditorInfo();
    EditorInfoCompat.setContentMimeTypes(textEditor,
        new String[] { "text/plain" });
    assertFalse(GifInserter.editorAcceptsAnyImage(textEditor));
    assertFalse(GifInserter.editorAcceptsAnyImage(null));
  }


  @Test
  public void emptyImageSelectionIsRejected()
  {
    ImageAttachmentPickerActivity activity = Robolectric.buildActivity(
        ImageAttachmentPickerActivity.class).create().get();
    Uri selected = Uri.parse("content://picker.test/selected/empty");
    org.robolectric.Shadows.shadowOf(activity.getContentResolver())
      .registerInputStream(selected, new ByteArrayInputStream(new byte[0]));

    activity.onActivityResult(1, Activity.RESULT_OK, new Intent()
        .setDataAndType(selected, "image/png")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));

    assertNull("An empty image must never be offered to the editor.",
        PendingImageAttachment.peek());
  }

  @Test
  public void nonImageSelectionIsRejected()
  {
    ImageAttachmentPickerActivity activity = Robolectric.buildActivity(
        ImageAttachmentPickerActivity.class).create().get();
    Intent result = new Intent()
      .setDataAndType(Uri.parse("content://documents/not-an-image"),
          "text/plain");

    activity.onActivityResult(1, Activity.RESULT_OK, result);

    assertNull(PendingImageAttachment.peek());
  }

  @Test
  public void staleConsumerCannotClearNewerSelection()
  {
    PendingImageAttachment.set(Uri.parse("content://photos/first"),
        "image/png", "first");
    PendingImageAttachment.Item first = PendingImageAttachment.peek();
    PendingImageAttachment.set(Uri.parse("content://photos/second"),
        "image/webp", "second");

    PendingImageAttachment.clear(first);

    assertEquals("image/webp", PendingImageAttachment.peek().mimeType);
  }

  @Test
  public void ownedImageCanBeOpenedByAcceptingEditorAfterCommit()
      throws Exception
  {
    Context context = RuntimeEnvironment.getApplication();
    Uri selected = Uri.parse("content://picker.test/selected/84");
    byte[] expected = new byte[] { 1, 2, 3, 4, 5 };
    org.robolectric.Shadows.shadowOf(context.getContentResolver())
      .registerInputStream(selected, new ByteArrayInputStream(expected));
    ImageAttachmentPickerActivity activity = Robolectric.buildActivity(
        ImageAttachmentPickerActivity.class).create().get();
    activity.onActivityResult(1, Activity.RESULT_OK, new Intent()
        .setDataAndType(selected, "image/png")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
    PendingImageAttachment.Item pending = PendingImageAttachment.peek();
    assertNotNull(pending);
    assertNotEquals("Picker grants cannot be delegated reliably to editors.",
        selected, pending.uri);
    assertEquals(context.getPackageName() + ".attachments",
        pending.uri.getAuthority());

    EditorInfo editor = new EditorInfo();
    EditorInfoCompat.setContentMimeTypes(editor, new String[] { "image/*" });
    OpeningInputConnection connection = new OpeningInputConnection(context);

    assertTrue(GifInserter.insertImage(context, connection, editor,
        pending.uri, pending.mimeType, pending.description));
    assertEquals(pending.uri, connection.uri);
    assertTrue("The receiving editor requests the temporary URI permission.",
        connection.permissionRequested);
    assertTrue("The IME includes Android's temporary content grant flag.",
        connection.grantFlagReceived);
    assertEquals("The receiving editor opens every selected image byte.",
        expected.length, connection.openedBytes);
    Uri laterSelection = Uri.parse("content://picker.test/selected/later");
    org.robolectric.Shadows.shadowOf(context.getContentResolver())
      .registerInputStream(laterSelection,
          new ByteArrayInputStream(new byte[] { 9, 8, 7 }));
    Uri laterOwned = ImageAttachmentPickerActivity.cacheSelection(
        context, laterSelection, "image/png");
    assertNotEquals("Every committed image needs a stable unique URI.",
        pending.uri, laterOwned);
    try (InputStream delayed =
        context.getContentResolver().openInputStream(pending.uri))
    {
      assertArrayEquals(
          "A later pick must not replace bytes an editor opens asynchronously.",
          expected, readAll(delayed));
    }
  }

  private static final class OpeningInputConnection extends BaseInputConnection
  {
    private final Context _context;
    Uri uri;
    boolean permissionRequested;
    boolean grantFlagReceived;
    int openedBytes;

    OpeningInputConnection(Context context)
    {
      super(new View(context), true);
      _context = context;
    }

    @Override
    public boolean commitContent(InputContentInfo content, int flags,
        Bundle opts)
    {
      uri = content.getContentUri();
      grantFlagReceived = (flags &
          InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION) != 0;
      try
      {
        content.requestPermission();
        permissionRequested = true;
        try (InputStream stream =
            _context.getContentResolver().openInputStream(uri))
        {
          byte[] buffer = new byte[1024];
          int count;
          while ((count = stream.read(buffer)) != -1)
            openedBytes += count;
        }
        return openedBytes > 0;
      }
      catch (Exception error)
      {
        throw new AssertionError(error);
      }
    }
  }

  private static byte[] readAll(InputStream input) throws Exception
  {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    byte[] buffer = new byte[1024];
    int count;
    while ((count = input.read(buffer)) != -1)
      output.write(buffer, 0, count);
    return output.toByteArray();
  }
}
