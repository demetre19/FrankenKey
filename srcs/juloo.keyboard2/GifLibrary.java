package juloo.keyboard2;

import android.content.Context;
import android.net.Uri;
import java.util.ArrayList;
import java.util.List;

final class GifLibrary
{
  static final GifResult[] LOCAL = new GifResult[]{
    GifResult.local("frankenkey_gif", "GIF", "gif frankenkey keyboard", R.raw.frankenkey_gif, 1559),
    GifResult.local("frankenkey_happy", "Happy", "happy smile yay excited", R.raw.frankenkey_happy, 3019),
    GifResult.local("frankenkey_yes", "Yes", "yes ok agree thumbs up", R.raw.frankenkey_yes, 2770),
    GifResult.local("frankenkey_no", "No", "no nope disagree stop", R.raw.frankenkey_no, 2198),
    GifResult.local("frankenkey_lol", "LOL", "lol laugh funny haha", R.raw.frankenkey_lol, 2005),
    GifResult.local("frankenkey_wow", "Wow", "wow surprised omg", R.raw.frankenkey_wow, 3416),
    GifResult.local("frankenkey_thanks", "Thanks", "thanks thank you grateful", R.raw.frankenkey_thanks, 3394),
  };

  private GifLibrary() {}

  static List<GifResult> searchLocal(String query)
  {
    String q = normalize(query);
    ArrayList<GifResult> out = new ArrayList<GifResult>();
    for (GifResult item : LOCAL)
      if (q.length() == 0 || normalize(item.title + " " + item.tags).contains(q))
        out.add(item);
    return out;
  }

  static GifResult byId(String id)
  {
    for (GifResult item : LOCAL)
      if (item.id.equals(id))
        return item;
    return null;
  }

  static Uri localUri(Context context, GifResult item)
  {
    return new Uri.Builder()
      .scheme("content")
      .authority(context.getPackageName() + ".gifcontent")
      .appendPath("local")
      .appendPath(item.id)
      .build();
  }

  static Uri cacheUri(Context context, GifResult item)
  {
    return new Uri.Builder()
      .scheme("content")
      .authority(context.getPackageName() + ".gifcontent")
      .appendPath("cache")
      .appendPath(item.fileName())
      .build();
  }

  static String normalize(String value)
  {
    return value == null ? "" : value.trim().toLowerCase();
  }
}
