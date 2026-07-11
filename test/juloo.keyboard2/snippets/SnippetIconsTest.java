package juloo.keyboard2.snippets;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.widget.TextView;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.util.HashSet;
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
