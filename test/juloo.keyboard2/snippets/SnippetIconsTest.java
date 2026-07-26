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

  @Test
  public void snippet_page_swipes_wrap_in_both_directions()
  {
    assertEquals("A finger-right swipe past the last page must wrap to the first page.",
        0, SnippetRowView.targetPageForSwipe(2, 3, 48, 48));
    assertEquals("A finger-left swipe from the first page must wrap to the last page.",
        2, SnippetRowView.targetPageForSwipe(0, 3, -48, 48));
    assertEquals("Finger-right swipes still advance one page at a time.",
        2, SnippetRowView.targetPageForSwipe(1, 3, 48, 48));
    assertEquals("Finger-left swipes still move backward one page at a time.",
        0, SnippetRowView.targetPageForSwipe(1, 3, -48, 48));
  }

  @Test
  public void snippet_page_swipes_use_a_short_bounded_activation_distance()
  {
    int distance = SnippetRowView.swipeActivationDistance(1080, 72, 144);

    assertEquals("The swipe commits after the configured 48dp ceiling.",
        144, distance);
    assertTrue("The new activation distance must be much shorter than half a page.",
        distance < 1080 / 2);
    assertEquals("Movement below the activation distance must preserve taps.",
        1, SnippetRowView.targetPageForSwipe(1, 3, 143, distance));
    assertEquals("Movement at the activation distance must change pages.",
        2, SnippetRowView.targetPageForSwipe(1, 3, 144, distance));
    assertTrue("A short horizontal drag must be intercepted for page movement.",
        SnippetRowView.isPageSwipe(-144, 20, distance));
    assertFalse("Sub-threshold movement must remain a snippet tap.",
        SnippetRowView.isPageSwipe(-143, 20, distance));
    assertFalse("Vertical gestures must not change snippet pages.",
        SnippetRowView.isPageSwipe(-144, 145, distance));
  }

}
