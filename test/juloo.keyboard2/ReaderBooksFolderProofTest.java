package juloo.keyboard2;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.UriPermission;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowContentResolver;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 35)
public class ReaderBooksFolderProofTest
{
  private static final String AUTHORITY = "books.documents";
  private static final String EPUB_MIME = "application/epub+zip";
  private static final String METHOD_CREATE_DOCUMENT = "android:createDocument";
  private static final String RESULT_URI = "uri";
  private static final Uri TREE_URI = Uri.parse(
      "content://books.documents/tree/primary%3ABooks");
  private static final Uri TARGET_URI = Uri.parse(
      "content://books.documents/document/primary%3ABooks%2FShared.epub");

  @Before
  public void setUp()
  {
    Context context = RuntimeEnvironment.getApplication();
    context.deleteDatabase("reader_library.db");
    releasePersistedPermissions(context.getContentResolver());
  }

  @After
  public void tearDown()
  {
    Context context = RuntimeEnvironment.getApplication();
    releasePersistedPermissions(context.getContentResolver());
    context.deleteDatabase("reader_library.db");
  }

  @Test
  public void picker_requests_only_durable_scoped_tree_access()
  {
    Intent intent = ReaderBooksFolder.pickerIntent();

    assertEquals(Intent.ACTION_OPEN_DOCUMENT_TREE, intent.getAction());
    assertTrue((intent.getFlags() &
          Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0);
    assertTrue((intent.getFlags() &
          Intent.FLAG_GRANT_WRITE_URI_PERMISSION) != 0);
    assertTrue((intent.getFlags() &
          Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) != 0);
    assertTrue((intent.getFlags() &
          Intent.FLAG_GRANT_PREFIX_URI_PERMISSION) != 0);
  }

  @Test
  public void tree_grant_persists_and_revocation_is_detectable()
  {
    ContentResolver resolver = resolver();
    int access = Intent.FLAG_GRANT_READ_URI_PERMISSION |
      Intent.FLAG_GRANT_WRITE_URI_PERMISSION;

    resolver.takePersistableUriPermission(TREE_URI, access);

    UriPermission permission = permissionFor(
        resolver.getPersistedUriPermissions(), TREE_URI);
    assertNotNull("The selected Books tree must remain available after the picker activity ends.",
        permission);
    assertTrue("Referencing books requires durable read access.",
        permission.isReadPermission());
    assertTrue("Copying an outside share into the user-owned Books tree requires durable write access.",
        permission.isWritePermission());

    resolver.releasePersistableUriPermission(TREE_URI, access);

    assertNull("Revoked Books-folder access must be detectable so the library can offer reconnect instead of failing silently.",
        permissionFor(resolver.getPersistedUriPermissions(), TREE_URI));
  }

  @Test
  public void temporary_share_stream_copies_once_into_user_owned_tree()
      throws Exception
  {
    Context context = RuntimeEnvironment.getApplication();
    ContentResolver resolver = context.getContentResolver();
    ShadowContentResolver shadow = Shadows.shadowOf(resolver);
    ProofDocumentsProvider provider =
      Robolectric.setupContentProvider(ProofDocumentsProvider.class, AUTHORITY);
    byte[] epub = "PK\u0003\u0004proof-epub".getBytes(StandardCharsets.ISO_8859_1);
    Uri shared = Uri.parse("content://share.provider/incoming/book.epub");
    ByteArrayOutputStream stored = new ByteArrayOutputStream();
    shadow.registerInputStreamSupplier(shared,
        () -> new ByteArrayInputStream(epub));
    shadow.registerOutputStreamSupplier(TARGET_URI, () -> stored);
    Intent grant = new Intent().setData(TREE_URI).addFlags(
        Intent.FLAG_GRANT_READ_URI_PERMISSION |
        Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
    assertEquals(TREE_URI, ReaderBooksFolder.retain(context, grant));

    ReaderBooksFolder.Prepared prepared =
      ReaderBooksFolder.prepare(context, shared, "Shared.epub");

    assertEquals("The canonical copy must be created in the user-selected Books tree.",
        TARGET_URI, prepared.documentUri);
    assertEquals(TREE_URI, prepared.treeUri);
    assertTrue("An outside temporary share must create exactly one durable copy.",
        prepared.createdCopy);
    assertEquals(EPUB_MIME, provider.requestedMimeType);
    assertEquals("Shared.epub", provider.requestedDisplayName);
    assertArrayEquals("The temporary ACTION_SEND stream must become the one canonical user-owned Books file.",
        epub, stored.toByteArray());

    ReaderBooksFolder.Prepared referenced =
      ReaderBooksFolder.prepare(context, TARGET_URI, "Shared.epub");
    assertEquals(TARGET_URI, referenced.documentUri);
    assertFalse("An EPUB already inside the selected Books tree must be referenced instead of copied.",
        referenced.createdCopy);
  }

  private static ContentResolver resolver()
  {
    Context context = RuntimeEnvironment.getApplication();
    return context.getContentResolver();
  }

  private static UriPermission permissionFor(List<UriPermission> permissions,
      Uri uri)
  {
    for (UriPermission permission : permissions)
      if (uri.equals(permission.getUri()))
        return permission;
    return null;
  }

  private static void releasePersistedPermissions(ContentResolver resolver)
  {
    for (UriPermission permission : new ArrayList<>(
          resolver.getPersistedUriPermissions()))
    {
      int flags = (permission.isReadPermission()
          ? Intent.FLAG_GRANT_READ_URI_PERMISSION : 0) |
        (permission.isWritePermission()
          ? Intent.FLAG_GRANT_WRITE_URI_PERMISSION : 0);
      if (flags != 0)
        resolver.releasePersistableUriPermission(permission.getUri(), flags);
    }
  }


  public static final class ProofDocumentsProvider extends ContentProvider
  {
    String requestedMimeType;
    String requestedDisplayName;

    @Override
    public boolean onCreate()
    {
      return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras)
    {
      if (METHOD_CREATE_DOCUMENT.equals(method))
      {
        requestedMimeType = extras.getString(DocumentsContract.Document.COLUMN_MIME_TYPE);
        requestedDisplayName = extras.getString(
            DocumentsContract.Document.COLUMN_DISPLAY_NAME);
        Bundle result = new Bundle();
        result.putParcelable(RESULT_URI, TARGET_URI);
        return result;
      }
      return super.call(method, arg, extras);
    }

    @Override public Cursor query(Uri uri, String[] projection,
        String selection, String[] selectionArgs, String sortOrder)
    {
      return null;
    }

    @Override public String getType(Uri uri) { return EPUB_MIME; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection,
        String[] selectionArgs) { return 0; }
    @Override public int update(Uri uri, ContentValues values,
        String selection, String[] selectionArgs) { return 0; }
  }
}
