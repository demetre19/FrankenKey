package juloo.keyboard2.snippets;

import android.content.Context;
import android.widget.ImageView;
import android.widget.TextView;
import java.lang.reflect.Method;
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
    ImageView iconView = (ImageView)makeSlotView.invoke(row,
        SnippetSlot.of(0, secret, "Password", "key"), null);

    assertNotNull("The selected icon must render on the keyboard button.",
        iconView.getDrawable());
    assertEquals("CENTER_INSIDE keeps icon artwork centered on both axes.",
        ImageView.ScaleType.CENTER_INSIDE, iconView.getScaleType());
    int expectedIconSize = (int)(20 *
        context.getResources().getDisplayMetrics().density + 0.5f);
    assertEquals("Vertical inset must leave exactly 20dp for icon artwork.",
        expectedIconSize, iconView.getLayoutParams().height -
        iconView.getPaddingTop() - iconView.getPaddingBottom());
    assertEquals("Equal vertical insets center the icon.",
        iconView.getPaddingTop(), iconView.getPaddingBottom());
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
