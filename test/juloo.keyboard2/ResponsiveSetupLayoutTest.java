package juloo.keyboard2;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 35)
public class ResponsiveSetupLayoutTest
{
  @Test
  public void setup_actions_remain_equal_two_column_pairs_on_small_phones()
  {
    Context context = RuntimeEnvironment.getApplication();
    ResponsiveSetupLayout layout = new ResponsiveSetupLayout(context, null);
    for (int i = 0; i < 4; i++)
    {
      View child = new View(context);
      LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.WRAP_CONTENT, 48);
      params.leftMargin = 4;
      params.rightMargin = 4;
      layout.addView(child, params);
    }

    layout.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
    layout.layout(0, 0, 320, layout.getMeasuredHeight());

    assertEquals(152, layout.getChildAt(0).getMeasuredWidth());
    assertEquals(layout.getChildAt(0).getMeasuredWidth(),
        layout.getChildAt(3).getMeasuredWidth());
    assertEquals("Four 48dp actions must occupy exactly two compact rows.",
        96, layout.getMeasuredHeight());
    assertEquals(layout.getChildAt(0).getLeft(),
        layout.getChildAt(2).getLeft());
    assertTrue(layout.getChildAt(1).getLeft() > layout.getChildAt(0).getLeft());
  }
}
