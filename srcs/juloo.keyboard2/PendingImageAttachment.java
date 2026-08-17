package juloo.keyboard2;

import android.net.Uri;

final class PendingImageAttachment
{
  static final class Item
  {
    final Uri uri;
    final String mimeType;
    final String description;

    Item(Uri uri, String mimeType, String description)
    {
      this.uri = uri;
      this.mimeType = mimeType;
      this.description = description;
    }
  }

  private static Item _item;

  private PendingImageAttachment() {}

  static synchronized void set(Uri uri, String mimeType, String description)
  {
    _item = new Item(uri, mimeType, description);
  }

  static synchronized Item peek()
  {
    return _item;
  }

  static synchronized void clear(Item item)
  {
    if (_item == item)
      _item = null;
  }
}
