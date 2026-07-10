package juloo.keyboard2;

import android.content.Context;
import android.content.res.Resources;
import android.util.AttributeSet;
import android.text.TextUtils;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.GridView;
import android.widget.TextView;

/** A non-scrollable three-column grid that can live inside the clipboard ScrollView. */
public class NonScrollGridView extends GridView
{
  public NonScrollGridView(Context context)
  {
    super(context);
    init(context);
  }

  public NonScrollGridView(Context context, AttributeSet attrs)
  {
    super(context, attrs);
    init(context);
  }

  public NonScrollGridView(Context context, AttributeSet attrs, int defStyle)
  {
    super(context, attrs, defStyle);
    init(context);
  }

  private void init(Context context)
  {
    setNumColumns(3);
    setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
    int gap = dp(context, 8);
    setHorizontalSpacing(gap);
    setVerticalSpacing(gap);
    setClipToPadding(false);
  }

  protected void enforceFixedCard(View card, int textViewId)
  {
    int height = fixedCardHeightPx();
    AbsListView.LayoutParams params;
    if (card.getLayoutParams() instanceof AbsListView.LayoutParams)
      params = (AbsListView.LayoutParams)card.getLayoutParams();
    else
      params = new AbsListView.LayoutParams(
          AbsListView.LayoutParams.MATCH_PARENT, height);
    params.width = AbsListView.LayoutParams.MATCH_PARENT;
    params.height = height;
    card.setLayoutParams(params);
    TextView text = (TextView)card.findViewById(textViewId);
    if (text != null)
    {
      text.setSingleLine(false);
      text.setMaxLines(5);
      text.setEllipsize(TextUtils.TruncateAt.END);
    }
  }

  private int fixedCardHeightPx()
  {
    try
    {
      return getResources().getDimensionPixelSize(
          R.dimen.clipboard_grid_card_height);
    }
    catch (Resources.NotFoundException _e)
    {
      return dp(getContext(), 112);
    }
  }

  @Override
  public void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
  {
    int heightMeasureSpec_custom = MeasureSpec.makeMeasureSpec(
        Integer.MAX_VALUE >> 2, MeasureSpec.AT_MOST);
    super.onMeasure(widthMeasureSpec, heightMeasureSpec_custom);
    ViewGroup.LayoutParams params = getLayoutParams();
    if (params != null)
      params.height = getMeasuredHeight();
  }

  private static int dp(Context context, int value)
  {
    return Math.round(value * context.getResources().getDisplayMetrics().density);
  }
}
