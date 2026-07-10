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
  public void button_margins_are_included_when_deciding_to_stack()
  {
    ResponsiveSetupLayout layout = setupLayout();

    layout.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
    assertEquals("Three buttons whose real widths overflow must stack.",
        LinearLayout.VERTICAL, layout.getOrientation());

    layout.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
    assertEquals("The same complete buttons should stay three-across when they fit.",
        LinearLayout.HORIZONTAL, layout.getOrientation());
  }

  private ResponsiveSetupLayout setupLayout()
  {
    Context context = RuntimeEnvironment.getApplication();
    ResponsiveSetupLayout layout = new ResponsiveSetupLayout(context, null);
    for (int i = 0; i < 3; i++)
    {
      FixedWidthView child = new FixedWidthView(context, 100);
      LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.WRAP_CONTENT,
          LinearLayout.LayoutParams.WRAP_CONTENT);
      params.leftMargin = 10;
      params.rightMargin = 10;
      layout.addView(child, params);
    }
    return layout;
  }

  private static final class FixedWidthView extends View
  {
    private final int _width;

    FixedWidthView(Context context, int width)
    {
      super(context);
      _width = width;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
    {
      setMeasuredDimension(_width, 48);
    }
  }
}
