package juloo.keyboard2.snippets;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.List;
import juloo.keyboard2.Theme;
import juloo.keyboard2.R;

public class SnippetRowView extends HorizontalScrollView
{
  public interface OnSnippetClickListener
  {
    void onSnippetClicked(SnippetSlot slot);
  }

  private static class CenteredIconTextView extends TextView
  {
    private Drawable _icon;
    private int _icon_color;

    CenteredIconTextView(Context context)
    {
      super(context);
    }

    void setIcon(Drawable icon, int color)
    {
      _icon = icon;
      _icon_color = color;
      invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas)
    {
      super.onDraw(canvas);
      if (_icon == null)
        return;
      int width = _icon.getBounds().width();
      int height = _icon.getBounds().height();
      int contentWidth = getWidth() - getPaddingLeft() - getPaddingRight();
      int contentHeight = getHeight() - getPaddingTop() - getPaddingBottom();
      int left = getPaddingLeft() + (contentWidth - width) / 2;
      int top = getPaddingTop() + (contentHeight - height) / 2;
      _icon.setBounds(left, top, left + width, top + height);
      _icon.draw(canvas);
    }
  }

  private LinearLayout _pages;
  private final Paint _divider_paint = new Paint();

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

  static int foregroundColor(Context context)
  {
    Theme theme = new Theme(context, null);
    return theme.labelColorForKeyboardBackground();
  }

  private TextView makeSlotView(final SnippetSlot slot,
      final OnSnippetClickListener listener)
  {
    int color = foregroundColor(getContext());
    SnippetIcons.Icon icon = SnippetIcons.find(slot.getIconId());
    TextView v = icon == null ? new TextView(getContext()) :
      new CenteredIconTextView(getContext());
    if (icon == null)
      v.setText(slot.getDisplayLabel());
    else
    {
      Drawable drawable = SnippetIcons.drawable(getContext(), icon.id, color);
      if (drawable != null)
        drawable.setBounds(0, 0, dp(15), dp(15));
      ((CenteredIconTextView)v).setIcon(drawable, color);
      v.setContentDescription(icon.title + " snippet");
    }
    v.setGravity(Gravity.CENTER);
    v.setSingleLine(true);
    v.setEllipsize(TextUtils.TruncateAt.END);
    v.setTextColor(color);
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
  public boolean onTouchEvent(MotionEvent ev)
  {
    boolean handled = super.onTouchEvent(ev);
    if (ev.getAction() == MotionEvent.ACTION_UP ||
        ev.getAction() == MotionEvent.ACTION_CANCEL)
      snapToNearestPage();
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
