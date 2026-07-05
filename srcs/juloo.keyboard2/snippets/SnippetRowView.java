package juloo.keyboard2.snippets;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
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

  public SnippetRowView(Context context, AttributeSet attrs)
  {
    super(context, attrs);
    setHorizontalScrollBarEnabled(false);
    setFillViewport(true);
    _pages = new LinearLayout(context);
    _pages.setOrientation(LinearLayout.HORIZONTAL);
    addView(_pages, new LayoutParams(LayoutParams.WRAP_CONTENT,
          LayoutParams.WRAP_CONTENT));
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
        SnippetSlot.of(pageIndex * SnippetPages.PAGE_SIZE + i, "", "", false);
      page.addView(makeSlotView(slot, listener));
    }
    return page;
  }

  private TextView makeSlotView(final SnippetSlot slot,
      final OnSnippetClickListener listener)
  {
    TextView v = new TextView(getContext());
    v.setText(slot.getDisplayLabel());
    v.setGravity(Gravity.CENTER);
    v.setSingleLine(true);
    v.setTextColor(themeColor(R.attr.colorLabel));
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
