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
    assertEquals("A finger-right swipe from the first page signals a wrap to the last page.",
        -1, SnippetRowView.targetPageForSwipe(0, 3, 48, 48));
    assertEquals("A finger-left swipe past the last page signals a wrap to the first page.",
        3, SnippetRowView.targetPageForSwipe(2, 3, -48, 48));
    assertEquals("Finger-right swipes reveal the previous page, matching the drag.",
        0, SnippetRowView.targetPageForSwipe(1, 3, 48, 48));
    assertEquals("Finger-left swipes reveal the next page, matching the drag.",
        2, SnippetRowView.targetPageForSwipe(1, 3, -48, 48));
  }

  @Test
  public void edge_rotation_moves_the_far_page_next_to_the_visible_edge()
      throws Exception
  {
    Context context = RuntimeEnvironment.getApplication();
    SnippetRowView row = new SnippetRowView(context, null);
    java.lang.reflect.Field pagesField =
        SnippetRowView.class.getDeclaredField("_pages");
    pagesField.setAccessible(true);
    android.widget.LinearLayout pages =
        (android.widget.LinearLayout)pagesField.get(row);
    int width = 320;
    android.view.View first = new android.view.View(context);
    android.view.View middle = new android.view.View(context);
    android.view.View last = new android.view.View(context);
    for (android.view.View page : new android.view.View[]{first, middle, last})
      pages.addView(page,
          new android.widget.LinearLayout.LayoutParams(width, 50));
    // Lay out for real so the scroll range covers all three pages; without
    // measured children Robolectric clamps scrollTo() to zero.
    row.measure(android.view.View.MeasureSpec.makeMeasureSpec(width,
          android.view.View.MeasureSpec.EXACTLY),
        android.view.View.MeasureSpec.makeMeasureSpec(50,
          android.view.View.MeasureSpec.EXACTLY));
    row.layout(0, 0, width, 50);

    row.scrollTo(0, 0);
    row.rotateEdgePage();
    assertSame("At the left edge the last page must become the left neighbour so a wrap swipe can slide it in.",
        last, pages.getChildAt(0));
    assertEquals("Rotating shifts the scroll offset by one page so the visible page does not appear to move.",
        width, row.getScrollX());

    row.rotateEdgePage();
    assertSame("Mid-range offsets must not rotate: the ring stays put.",
        last, pages.getChildAt(0));
    assertEquals(width, row.getScrollX());

    row.scrollTo(2 * width, 0);
    row.rotateEdgePage();
    assertSame("At the right edge the leftmost page must become the right neighbour.",
        last, pages.getChildAt(2));
    assertEquals(width, row.getScrollX());
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
        0, SnippetRowView.targetPageForSwipe(1, 3, 144, distance));
    assertTrue("A short horizontal drag must be intercepted for page movement.",
        SnippetRowView.isPageSwipe(-144, 20, distance));
    assertFalse("Sub-threshold movement must remain a snippet tap.",
        SnippetRowView.isPageSwipe(-143, 20, distance));
    assertFalse("Vertical gestures must not change snippet pages.",
        SnippetRowView.isPageSwipe(-144, 145, distance));
  }

  @Test
  public void edge_rotation_repairs_child_bounds_for_the_in_flight_tap()
      throws Exception
  {
    Context context = RuntimeEnvironment.getApplication();
    SnippetRowView row = new SnippetRowView(context, null);
    java.lang.reflect.Field pagesField =
        SnippetRowView.class.getDeclaredField("_pages");
    pagesField.setAccessible(true);
    android.widget.LinearLayout pages =
        (android.widget.LinearLayout)pagesField.get(row);
    int width = 320;
    android.view.View first = new android.view.View(context);
    android.view.View middle = new android.view.View(context);
    android.view.View last = new android.view.View(context);
    for (android.view.View page : new android.view.View[]{first, middle, last})
      pages.addView(page,
          new android.widget.LinearLayout.LayoutParams(width, 50));
    row.measure(android.view.View.MeasureSpec.makeMeasureSpec(width,
          android.view.View.MeasureSpec.EXACTLY),
        android.view.View.MeasureSpec.makeMeasureSpec(50,
          android.view.View.MeasureSpec.EXACTLY));
    row.layout(0, 0, width, 50);

    row.scrollTo(0, 0);
    row.rotateEdgePage();
    assertEquals("After a left-edge rotation the moved page must occupy slot 0 "
        + "immediately, or the in-flight ACTION_DOWN hit-tests stale bounds.",
        0, last.getLeft());
    assertEquals(width, first.getLeft());
    assertEquals(2 * width, middle.getLeft());

    // In rotated order [last, first, middle] the right-edge rotation moves
    // child 0 (last) to the end, restoring natural order.
    row.scrollTo(2 * width, 0);
    row.rotateEdgePage();
    assertEquals("After a right-edge rotation the moved page must occupy the "
        + "last slot immediately.", 2 * width, last.getLeft());
    assertEquals(0, first.getLeft());
    assertEquals(width, middle.getLeft());
  }

  @Test
  public void refresh_config_reopens_on_the_first_snippet_page()
  {
    Context context = RuntimeEnvironment.getApplication();
    android.content.SharedPreferences prefs =
        android.preference.PreferenceManager.getDefaultSharedPreferences(
            context);
    prefs.edit().putBoolean(SnippetStore.PREF_ENABLED, true).commit();
    java.util.List<SnippetSlot> slots = new java.util.ArrayList<>();
    for (int i = 0; i < 2 * SnippetSlot.PAGE_SIZE; ++i)
      slots.add(SnippetSlot.of(i, "phrase " + i, "S" + i));
    SnippetStore.saveSlots(context, slots);

    SnippetRowView row = new SnippetRowView(context, null);
    row.refresh_config(prefs, true, null);
    int width = 320;
    row.measure(android.view.View.MeasureSpec.makeMeasureSpec(width,
          android.view.View.MeasureSpec.EXACTLY),
        android.view.View.MeasureSpec.makeMeasureSpec(50,
          android.view.View.MeasureSpec.EXACTLY));
    row.layout(0, 0, width, 50);

    // Simulate the leftover rotated offset a tap on an edge page produces.
    row.scrollTo(width, 0);
    row.refresh_config(prefs, true, null);
    assertEquals("Rebuilding pages must reset the scroll offset so the row "
        + "reopens on snippet 1, not snippet 8.", 0, row.getScrollX());
  }

}
