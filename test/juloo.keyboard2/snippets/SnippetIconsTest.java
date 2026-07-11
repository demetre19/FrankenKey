package juloo.keyboard2.snippets;

import android.content.Context;
import android.view.ContextThemeWrapper;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ColorDrawable;
import android.widget.TextView;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.util.HashSet;
import juloo.keyboard2.R;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class SnippetIconsTest
{
  @Test
  public void curated_picker_has_fifty_six_unique_renderable_icons()
  {
    Context context = RuntimeEnvironment.getApplication();
    Set<String> ids = new HashSet<>();

    assertEquals("Eight seven-icon groups keep the picker broad but ordered.",
        56, SnippetIcons.all().size());
    for (SnippetIcons.Icon icon : SnippetIcons.all())
    {
      assertTrue("Saved icon identifiers must be unique: " + icon.id,
          ids.add(icon.id));
      assertFalse("Accessibility names must be present for " + icon.id,
          icon.title.isEmpty());
      assertNotNull("Every offered icon must resolve to packaged artwork: "
          + icon.id, SnippetIcons.drawable(context, icon.id, 0xffeeeeee));
    }
  }

  @Test
  public void unknown_or_empty_icon_ids_use_text_fallback()
  {
    Context context = RuntimeEnvironment.getApplication();
    assertNull(SnippetIcons.find(""));
    assertNull(SnippetIcons.find("not-an-icon"));
    assertNull(SnippetIcons.drawable(context, "not-an-icon", 0xffeeeeee));
  }

  @Test
  public void icon_and_text_foregrounds_are_binary_across_keyboard_themes()
      throws Exception
  {
    assertThemeForeground(R.style.Light, 0xff000000);
    assertThemeForeground(R.style.FleksyRowsLight, 0xff000000);
    assertThemeForeground(R.style.Dark, 0xffffffff);
    assertThemeForeground(R.style.FleksyRowsDark, 0xffffffff);
    assertThemeForeground(R.style.Forest, 0xffffffff);
    assertThemeForeground(R.style.Blue, 0xffffffff);
    assertThemeForeground(R.style.Gray, 0xffffffff);
  }

  private void assertThemeForeground(int themeStyle, int expectedColor)
      throws Exception
  {
    Context context = new ContextThemeWrapper(
        RuntimeEnvironment.getApplication(), themeStyle);
    assertEquals("Foreground selection must contrast with the keyboard background.",
        expectedColor, SnippetRowView.foregroundColor(context));
    SnippetRowView row = new SnippetRowView(context, null);
    Method makeSlotView = SnippetRowView.class.getDeclaredMethod(
        "makeSlotView", SnippetSlot.class,
        SnippetRowView.OnSnippetClickListener.class);
    makeSlotView.setAccessible(true);

    TextView textView = (TextView)makeSlotView.invoke(row,
        SnippetSlot.of(0, "hello", "Hi"), null);
    assertEquals("Snippet text must use the theme-aware foreground.",
        expectedColor, textView.getCurrentTextColor());

    TextView iconView = (TextView)makeSlotView.invoke(row,
        SnippetSlot.of(1, "secret", "Key", "key"), null);
    Field iconField = iconView.getClass().getDeclaredField("_icon");
    iconField.setAccessible(true);
    Drawable icon = (Drawable)iconField.get(iconView);
    assertNotNull("Snippet icons must retain theme-tinted artwork.",
        icon);
    assertNotNull("Snippet icon vectors must have an explicit SRC_IN color filter.",
        icon.getColorFilter());
    Field iconColorField = iconView.getClass().getDeclaredField("_icon_color");
    iconColorField.setAccessible(true);
    assertEquals("Snippet icons and text must use the identical theme foreground.",
        expectedColor, iconColorField.getInt(iconView));

    int iconSize = Math.round(15
        * context.getResources().getDisplayMetrics().density);
    ColorDrawable renderedIcon = new ColorDrawable(expectedColor);
    renderedIcon.setBounds(0, 0, iconSize, iconSize);
    iconField.set(iconView, renderedIcon);
    int viewWidth = Math.round(100
        * context.getResources().getDisplayMetrics().density);
    int viewHeight = Math.round(34
        * context.getResources().getDisplayMetrics().density);
    iconView.layout(0, 0, viewWidth, viewHeight);
    Bitmap renderedButton = Bitmap.createBitmap(
        viewWidth, viewHeight, Bitmap.Config.ARGB_8888);
    iconView.draw(new Canvas(renderedButton));
    assertEquals("The centered icon renderer must paint the resolved theme foreground.",
        expectedColor & 0x00ffffff,
        renderedButton.getPixel(viewWidth / 2, viewHeight / 2) & 0x00ffffff);
  }

  @Test
  public void keyboard_row_renders_icon_without_exposing_private_phrase()
      throws Exception
  {
    Context context = RuntimeEnvironment.getApplication();
    SnippetRowView row = new SnippetRowView(context, null);
    Method makeSlotView = SnippetRowView.class.getDeclaredMethod(
        "makeSlotView", SnippetSlot.class,
        SnippetRowView.OnSnippetClickListener.class);
    makeSlotView.setAccessible(true);

    String secret = "correct horse battery staple";
    TextView iconView = (TextView)makeSlotView.invoke(row,
        SnippetSlot.of(0, secret, "Password", "key"), null);

    assertEquals("An icon selection replaces the visible text label.", "",
        iconView.getText().toString());
    for (Drawable compound : iconView.getCompoundDrawables())
      assertNull("Exact centering must not depend on a TextView compound slot.",
          compound);
    Field iconField = iconView.getClass().getDeclaredField("_icon");
    iconField.setAccessible(true);
    Drawable centeredIcon = (Drawable)iconField.get(iconView);
    assertNotNull("The icon-only view must retain its centered artwork.",
        centeredIcon);
    float density = context.getResources().getDisplayMetrics().density;
    int expectedIconSize = Math.round(15 * density);
    int viewWidth = Math.round(100 * density);
    int viewHeight = Math.round(34 * density);
    iconView.layout(0, 0, viewWidth, viewHeight);
    iconView.draw(new Canvas(Bitmap.createBitmap(
          viewWidth, viewHeight, Bitmap.Config.ARGB_8888)));
    int expectedLeft = iconView.getPaddingLeft()
      + (viewWidth - iconView.getPaddingLeft() - iconView.getPaddingRight()
          - expectedIconSize) / 2;
    int expectedTop = iconView.getPaddingTop()
      + (viewHeight - iconView.getPaddingTop() - iconView.getPaddingBottom()
          - expectedIconSize) / 2;
    assertEquals("Icon artwork must be horizontally centered in its button.",
        expectedLeft, centeredIcon.getBounds().left);
    assertEquals("Icon artwork must be vertically centered in its button.",
        expectedTop, centeredIcon.getBounds().top);
    assertEquals("Keyboard-row icons stay 15dp wide.",
        expectedIconSize, centeredIcon.getBounds().width());
    assertEquals("Keyboard-row icons stay 15dp high.",
        expectedIconSize, centeredIcon.getBounds().height());
    assertEquals("Accessibility identifies the button without speaking its secret phrase.",
        "Password or key snippet", iconView.getContentDescription().toString());
    assertFalse("Private snippet phrases must not leak into accessibility text.",
        iconView.getContentDescription().toString().contains(secret));

    TextView fallbackView = (TextView)makeSlotView.invoke(row,
        SnippetSlot.of(1, "hello", "Hi"), null);
    assertEquals("Text labels remain the fallback when no icon is selected.",
        "Hi", fallbackView.getText().toString());
  }
}
