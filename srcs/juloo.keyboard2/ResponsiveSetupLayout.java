package juloo.keyboard2;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

/** Lays out setup actions as equal-width pairs on every phone size. */
public final class ResponsiveSetupLayout extends LinearLayout
{
  private static final int COLUMN_COUNT = 2;

  public ResponsiveSetupLayout(Context context, AttributeSet attrs)
  {
    super(context, attrs);
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
  {
    int available = Math.max(0, MeasureSpec.getSize(widthMeasureSpec)
        - getPaddingLeft() - getPaddingRight());
    int columnWidth = available / COLUMN_COUNT;
    int contentHeight = 0;
    int rowHeight = 0;
    for (int i = 0; i < getChildCount(); i++)
    {
      View child = getChildAt(i);
      ViewGroup.MarginLayoutParams margins =
        (ViewGroup.MarginLayoutParams)child.getLayoutParams();
      int childWidth = Math.max(0,
          columnWidth - margins.leftMargin - margins.rightMargin);
      int childHeightSpec = getChildMeasureSpec(heightMeasureSpec,
          getPaddingTop() + getPaddingBottom()
            + margins.topMargin + margins.bottomMargin,
          margins.height);
      child.measure(MeasureSpec.makeMeasureSpec(childWidth, MeasureSpec.EXACTLY),
          childHeightSpec);
      rowHeight = Math.max(rowHeight, child.getMeasuredHeight()
          + margins.topMargin + margins.bottomMargin);
      if (i % COLUMN_COUNT == COLUMN_COUNT - 1 || i == getChildCount() - 1)
      {
        contentHeight += rowHeight;
        rowHeight = 0;
      }
    }
    int desiredHeight = getPaddingTop() + contentHeight + getPaddingBottom();
    setMeasuredDimension(resolveSize(MeasureSpec.getSize(widthMeasureSpec),
          widthMeasureSpec), resolveSize(desiredHeight, heightMeasureSpec));
  }

  @Override
  protected void onLayout(boolean changed, int left, int top, int right,
      int bottom)
  {
    int available = right - left - getPaddingLeft() - getPaddingRight();
    int columnWidth = Math.max(0, available / COLUMN_COUNT);
    int rowTop = getPaddingTop();
    int rowHeight = 0;
    for (int i = 0; i < getChildCount(); i++)
    {
      View child = getChildAt(i);
      ViewGroup.MarginLayoutParams margins =
        (ViewGroup.MarginLayoutParams)child.getLayoutParams();
      int column = i % COLUMN_COUNT;
      int childLeft = getPaddingLeft() + column * columnWidth
        + margins.leftMargin;
      int childTop = rowTop + margins.topMargin;
      child.layout(childLeft, childTop,
          childLeft + child.getMeasuredWidth(),
          childTop + child.getMeasuredHeight());
      rowHeight = Math.max(rowHeight, child.getMeasuredHeight()
          + margins.topMargin + margins.bottomMargin);
      if (column == COLUMN_COUNT - 1 || i == getChildCount() - 1)
      {
        rowTop += rowHeight;
        rowHeight = 0;
      }
    }
  }
}
