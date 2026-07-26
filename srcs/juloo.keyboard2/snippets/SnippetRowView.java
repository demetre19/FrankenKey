package juloo.keyboard2.snippets;

import android.graphics.Canvas;
import android.graphics.Paint;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.List;
import juloo.keyboard2.R;

public class SnippetRowView extends HorizontalScrollView
{
  public interface OnSnippetClickListener
  {
    void onSnippetClicked(SnippetSlot slot);
  }


  private LinearLayout _pages;
  private final Paint _divider_paint = new Paint();
  private float _touch_down_x = Float.NaN;
  private float _touch_down_y;
  private int _touch_down_scroll_x;

  public SnippetRowView(Context context, AttributeSet attrs)
  {
    super(context, attrs);
    setHorizontalScrollBarEnabled(false);
    setFillViewport(true);
    _pages = new LinearLayout(context);
    _pages.setOrientation(LinearLayout.HORIZONTAL);
    addView(_pages, new LayoutParams(LayoutParams.WRAP_CONTENT,
          LayoutParams.WRAP_CONTENT));
    setWillNotDraw(false);
    _divider_paint.setColor(themeColor(R.attr.clipboard_divider_color));
    _divider_paint.setAlpha(140);
  }

  public void refresh_config(SharedPreferences prefs, boolean editorAllowsText,
      OnSnippetClickListener listener)
  {
    if (!editorAllowsText || !SnippetStore.isEnabled(prefs))
    {
      _pages.removeAllViews();
      setVisibility(GONE);
      return;
    }
    setVisibility(VISIBLE);
    List<SnippetSlot> slots = SnippetStore.loadSlots(getContext());
    int pages = SnippetPages.pageCount(slots.size());
    _pages.removeAllViews();
    for (int page = 0; page < pages; ++page)
      _pages.addView(makePage(page, SnippetPages.pageOf(slots, page), listener));
    applyPageWidths();
  }

  private LinearLayout makePage(int pageIndex, List<SnippetSlot> slots,
      OnSnippetClickListener listener)
  {
    LinearLayout page = new LinearLayout(getContext());
    page.setOrientation(LinearLayout.HORIZONTAL);
    page.setGravity(Gravity.CENTER);
    page.setPadding(0, 0, 0, 0);
    page.setLayoutParams(new LinearLayout.LayoutParams(pageWidth(),
          ViewGroup.LayoutParams.WRAP_CONTENT));
    for (int i = 0; i < SnippetPages.PAGE_SIZE; ++i)
    {
      SnippetSlot slot = i < slots.size() ? slots.get(i) :
        SnippetSlot.of(pageIndex * SnippetPages.PAGE_SIZE + i, "", "");
      page.addView(makeSlotView(slot, listener));
    }
    return page;
  }

  private View makeSlotView(final SnippetSlot slot,
      final OnSnippetClickListener listener)
  {
    int color = themeColor(R.attr.colorLabel);
    SnippetIcons.Icon icon = SnippetIcons.find(slot.getIconId());
    View v;
    if (icon == null)
    {
      TextView label = new TextView(getContext());
      label.setText(slot.getDisplayLabel());
      label.setGravity(Gravity.CENTER);
      label.setSingleLine(true);
      label.setEllipsize(TextUtils.TruncateAt.END);
      label.setTextColor(color);
      v = label;
    }
    else
    {
      ImageView image = new ImageView(getContext());
      image.setImageDrawable(
          SnippetIcons.drawable(getContext(), icon.id, color));
      image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
      int verticalInset = dp(7);
      image.setPadding(0, verticalInset, 0, verticalInset);
      image.setContentDescription(icon.title + " snippet");
      v = image;
    }
    v.setBackgroundResource(R.drawable.suggestions_item_background);
    v.setAlpha(slot.isConfigured() ? 1.0f : 0.45f);
    int margin = dp(2);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(34), 1);
    lp.setMargins(margin, margin, margin, margin);
    v.setLayoutParams(lp);
    if (slot.isConfigured() && listener != null)
      v.setOnClickListener(_v -> listener.onSnippetClicked(slot));
    return v;
  }

  private int pageWidth()
  {
    int width = getWidth();
    return width > 0 ? width : getResources().getDisplayMetrics().widthPixels;
  }

  private void applyPageWidths()
  {
    int width = pageWidth();
    for (int i = 0; i < _pages.getChildCount(); ++i)
    {
      ViewGroup.LayoutParams lp = _pages.getChildAt(i).getLayoutParams();
      if (lp.width != width)
      {
        lp.width = width;
        _pages.getChildAt(i).setLayoutParams(lp);
      }
    }
  }

  @Override
  protected void onSizeChanged(int w, int h, int oldw, int oldh)
  {
    super.onSizeChanged(w, h, oldw, oldh);
    applyPageWidths();
  }

  @Override
  public boolean onInterceptTouchEvent(MotionEvent ev)
  {
    int action = ev.getActionMasked();
    if (action == MotionEvent.ACTION_DOWN)
      beginSwipe(ev);
    else if (action == MotionEvent.ACTION_MOVE &&
        !Float.isNaN(_touch_down_x))
    {
      int activationDistance = swipeActivationDistance(
          pageWidth(), dp(24), dp(48));
      if (isPageSwipe(ev.getX() - _touch_down_x,
            ev.getY() - _touch_down_y, activationDistance))
        return true;
    }
    return super.onInterceptTouchEvent(ev);
  }

  @Override
  public boolean onTouchEvent(MotionEvent ev)
  {
    int action = ev.getActionMasked();
    if (action == MotionEvent.ACTION_DOWN)
      beginSwipe(ev);
    boolean handled = super.onTouchEvent(ev);
    if (action == MotionEvent.ACTION_UP)
    {
      if (Float.isNaN(_touch_down_x))
        snapToNearestPage();
      else
        finishSwipe(ev.getX() - _touch_down_x);
      _touch_down_x = Float.NaN;
    }
    else if (action == MotionEvent.ACTION_CANCEL)
    {
      snapToNearestPage();
      _touch_down_x = Float.NaN;
    }
    return handled;
  }

  private void snapToNearestPage()
  {
    int width = pageWidth();
    if (width <= 0)
      return;
    int page = (getScrollX() + width / 2) / width;
    smoothScrollTo(page * width, 0);
  }

  private void beginSwipe(MotionEvent ev)
  {
    _touch_down_x = ev.getX();
    _touch_down_y = ev.getY();
    _touch_down_scroll_x = getScrollX();
  }

  private void finishSwipe(float deltaX)
  {
    int width = pageWidth();
    int pageCount = _pages.getChildCount();
    if (width <= 0 || pageCount <= 0)
      return;
    int currentPage = Math.max(0, Math.min(pageCount - 1,
          (_touch_down_scroll_x + width / 2) / width));
    int activationDistance = swipeActivationDistance(
        width, dp(24), dp(48));
    int targetPage = targetPageForSwipe(
        currentPage, pageCount, deltaX, activationDistance);
    boolean wraps = pageCount > 1 &&
      ((currentPage == 0 && targetPage == pageCount - 1) ||
       (currentPage == pageCount - 1 && targetPage == 0));
    if (wraps)
      scrollTo(targetPage * width, 0);
    else
      smoothScrollTo(targetPage * width, 0);
  }

  static int swipeActivationDistance(
      int width, int minimumDistance, int maximumDistance)
  {
    int distance = Math.max(minimumDistance,
        Math.min(maximumDistance, width / 6));
    return Math.max(1, Math.min(width, distance));
  }

  static boolean isPageSwipe(
      float deltaX, float deltaY, int activationDistance)
  {
    return Math.abs(deltaX) >= activationDistance &&
      Math.abs(deltaX) > Math.abs(deltaY);
  }

  static int targetPageForSwipe(int currentPage, int pageCount,
      float deltaX, int activationDistance)
  {
    if (pageCount <= 0)
      return 0;
    int current = Math.max(0, Math.min(pageCount - 1, currentPage));
    if (Math.abs(deltaX) < activationDistance)
      return current;
    int direction = deltaX > 0 ? 1 : -1;
    return (current + direction + pageCount) % pageCount;
  }

  @Override
  protected void onDraw(Canvas canvas)
  {
    super.onDraw(canvas);
    canvas.drawRect(0, 0, getWidth(), dividerHeight(), _divider_paint);
  }

  private int dividerHeight()
  {
    TypedValue value = new TypedValue();
    getContext().getTheme().resolveAttribute(
        R.attr.clipboard_divider_height, value, true);
    if (value.type == TypedValue.TYPE_DIMENSION)
      return Math.max(1, TypedValue.complexToDimensionPixelSize(value.data,
            getResources().getDisplayMetrics()));
    return 1;
  }

  private int themeColor(int attr)
  {
    TypedValue value = new TypedValue();
    getContext().getTheme().resolveAttribute(attr, value, true);
    return value.data;
  }

  private int dp(int value)
  {
    return (int)(value * getResources().getDisplayMetrics().density + 0.5f);
  }
}
