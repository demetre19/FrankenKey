package juloo.keyboard2;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

/** Keeps the setup actions in one row when their complete labels fit. */
public final class ResponsiveSetupLayout extends LinearLayout
{
  public ResponsiveSetupLayout(Context context, AttributeSet attrs)
  {
    super(context, attrs);
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
  {
    int available = Math.max(0, MeasureSpec.getSize(widthMeasureSpec)
        - getPaddingLeft() - getPaddingRight());
    int required = 0;
    for (int i = 0; i < getChildCount(); i++)
    {
      View child = getChildAt(i);
      child.measure(MeasureSpec.makeMeasureSpec(available, MeasureSpec.AT_MOST),
          MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
      ViewGroup.MarginLayoutParams margins =
        (ViewGroup.MarginLayoutParams)child.getLayoutParams();
      required += child.getMeasuredWidth()
        + margins.leftMargin + margins.rightMargin;
    }
    setOrientation(getChildCount() > 0 && required <= available
        ? HORIZONTAL : VERTICAL);
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
  }
}
