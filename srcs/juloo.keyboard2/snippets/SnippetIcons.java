package juloo.keyboard2.snippets;

import android.content.Context;
import android.graphics.drawable.Drawable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import juloo.keyboard2.R;

/** Stable snippet icon identifiers mapped to the vendored Lucide artwork. */
public final class SnippetIcons
{
  public static final class Icon
  {
    public final String id;
    public final String title;
    private final int _drawable;

    private Icon(String id, String title, int drawable)
    {
      this.id = id;
      this.title = title;
      _drawable = drawable;
    }
  }

  private static Icon icon(String id, String title, int drawable)
  {
    return new Icon(id, title, drawable);
  }

  private static final List<Icon> ICONS = Collections.unmodifiableList(
      Arrays.asList(
        icon("mail", "Email", R.drawable.snippet_icon_mail),
        icon("phone", "Phone", R.drawable.snippet_icon_phone),
        icon("map-pin", "Location", R.drawable.snippet_icon_map_pin),
        icon("house", "Home", R.drawable.snippet_icon_house),
        icon("user", "Person", R.drawable.snippet_icon_user),
        icon("users", "People", R.drawable.snippet_icon_users),
        icon("contact", "Contact", R.drawable.snippet_icon_contact),

        icon("message-circle", "Message", R.drawable.snippet_icon_message_circle),
        icon("send", "Send", R.drawable.snippet_icon_send),
        icon("at-sign", "Account", R.drawable.snippet_icon_at_sign),
        icon("link", "Link", R.drawable.snippet_icon_link),
        icon("globe", "Website", R.drawable.snippet_icon_globe),
        icon("quote", "Quote", R.drawable.snippet_icon_quote),
        icon("signature", "Signature", R.drawable.snippet_icon_signature),

        icon("briefcase", "Work", R.drawable.snippet_icon_briefcase),
        icon("calendar", "Calendar", R.drawable.snippet_icon_calendar),
        icon("clock", "Time", R.drawable.snippet_icon_clock),
        icon("clipboard", "Clipboard", R.drawable.snippet_icon_clipboard),
        icon("file-text", "Document", R.drawable.snippet_icon_file_text),
        icon("pen-line", "Writing", R.drawable.snippet_icon_pen_line),
        icon("check", "Confirmation", R.drawable.snippet_icon_check),

        icon("key", "Password or key", R.drawable.snippet_icon_key),
        icon("lock", "Private", R.drawable.snippet_icon_lock),
        icon("scan-face", "Identity", R.drawable.snippet_icon_scan_face),
        icon("shield-check", "Security", R.drawable.snippet_icon_shield_check),
        icon("eye", "Visible", R.drawable.snippet_icon_eye),
        icon("eye-off", "Hidden", R.drawable.snippet_icon_eye_off),
        icon("badge-check", "Verified", R.drawable.snippet_icon_badge_check),

        icon("terminal", "Terminal", R.drawable.snippet_icon_terminal),
        icon("code", "Code", R.drawable.snippet_icon_code),
        icon("braces", "Code block", R.drawable.snippet_icon_braces),
        icon("database", "Database", R.drawable.snippet_icon_database),
        icon("server", "Server", R.drawable.snippet_icon_server),
        icon("wifi", "Network", R.drawable.snippet_icon_wifi),
        icon("command", "Command", R.drawable.snippet_icon_command),

        icon("credit-card", "Payment card", R.drawable.snippet_icon_credit_card),
        icon("wallet", "Wallet", R.drawable.snippet_icon_wallet),
        icon("banknote", "Money", R.drawable.snippet_icon_banknote),
        icon("receipt", "Receipt", R.drawable.snippet_icon_receipt),
        icon("shopping-cart", "Shopping", R.drawable.snippet_icon_shopping_cart),
        icon("package", "Package", R.drawable.snippet_icon_package),
        icon("tag", "Tag", R.drawable.snippet_icon_tag),

        icon("car", "Car", R.drawable.snippet_icon_car),
        icon("plane", "Flight", R.drawable.snippet_icon_plane),
        icon("train-front", "Train", R.drawable.snippet_icon_train_front),
        icon("bike", "Bicycle", R.drawable.snippet_icon_bike),
        icon("navigation", "Directions", R.drawable.snippet_icon_navigation),
        icon("bed", "Accommodation", R.drawable.snippet_icon_bed),
        icon("map", "Map", R.drawable.snippet_icon_map),

        icon("star", "Favorite", R.drawable.snippet_icon_star),
        icon("heart", "Love", R.drawable.snippet_icon_heart),
        icon("bookmark", "Bookmark", R.drawable.snippet_icon_bookmark),
        icon("bell", "Reminder", R.drawable.snippet_icon_bell),
        icon("zap", "Quick", R.drawable.snippet_icon_zap),
        icon("gift", "Gift", R.drawable.snippet_icon_gift),
        icon("music", "Music", R.drawable.snippet_icon_music)));

  private SnippetIcons() {}

  public static List<Icon> all()
  {
    return ICONS;
  }

  public static Icon find(String id)
  {
    if (id == null || id.isEmpty())
      return null;
    for (Icon icon : ICONS)
      if (icon.id.equals(id))
        return icon;
    return null;
  }

  public static Drawable drawable(Context context, String id, int color)
  {
    Icon icon = find(id);
    if (icon == null)
      return null;
    Drawable drawable = ContextCompat.getDrawable(context, icon._drawable);
    if (drawable == null)
      return null;
    drawable = DrawableCompat.wrap(drawable).mutate();
    DrawableCompat.setTint(drawable, color);
    return drawable;
  }
}
