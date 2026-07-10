package juloo.keyboard2;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;

/** Root controller for the clipboard pane's Samsung-like expandable area. */
public final class ClipboardPanelView extends LinearLayout
{
  private ScrollView _scroll;
  private View _handle;
  private View _showMore;
  private int _compactHeight;
  private float _startY;
  private int _startHeight;

  public ClipboardPanelView(Context context, AttributeSet attrs)
  {
    super(context, attrs);
  }

  @Override
  protected void onFinishInflate()
  {
    super.onFinishInflate();
    _scroll = (ScrollView)findViewById(R.id.clipboard_scroll);
    _handle = findViewById(R.id.clipboard_drag_handle);
    _showMore = findViewById(R.id.clipboard_show_more);
    _compactHeight = getResources().getDimensionPixelSize(
        R.dimen.clipboard_view_height);
    View.OnClickListener toggle = new View.OnClickListener()
    {
      @Override
      public void onClick(View v)
      {
        toggleExpanded();
      }
    };
    if (_handle != null)
    {
      _handle.setOnClickListener(toggle);
      _handle.setOnTouchListener(new DragToResize());
    }
    if (_showMore != null)
      _showMore.setOnClickListener(toggle);
  }

  void toggleExpanded()
  {
    if (_scroll == null)
      return;
    int current = currentHeight();
    int expanded = expandedHeight();
    setScrollHeight(current < (_compactHeight + expanded) / 2 ? expanded
        : _compactHeight);
  }

  void resizeForDrag(int startHeight, float startY, float currentY)
  {
    setScrollHeight(clamp(startHeight + Math.round(startY - currentY)));
  }

  int currentHeight()
  {
    if (_scroll == null || _scroll.getLayoutParams() == null)
      return _compactHeight;
    int height = _scroll.getLayoutParams().height;
    return height > 0 ? height : _compactHeight;
  }

  int expandedHeight()
  {
    int fallback = getResources().getDimensionPixelSize(
        R.dimen.clipboard_expanded_view_height);
    int rootHeight = getRootView() == null ? 0 : getRootView().getHeight();
    if (rootHeight <= 0)
      return fallback;
    int reserved = dp(96);
    return Math.max(_compactHeight, Math.min(fallback, rootHeight - reserved));
  }

  private void setScrollHeight(int height)
  {
    if (_scroll == null)
      return;
    android.view.ViewGroup.LayoutParams params = _scroll.getLayoutParams();
    if (params == null)
      return;
    params.height = clamp(height);
    _scroll.setLayoutParams(params);
    _scroll.requestLayout();
  }

  private int clamp(int height)
  {
    return Math.max(_compactHeight, Math.min(expandedHeight(), height));
  }

  private int dp(int value)
  {
    return Math.round(value * getResources().getDisplayMetrics().density);
  }

  private final class DragToResize implements View.OnTouchListener
  {
    private boolean _moved;

    @Override
    public boolean onTouch(View v, MotionEvent event)
    {
      switch (event.getActionMasked())
      {
        case MotionEvent.ACTION_DOWN:
          _startY = event.getRawY();
          _startHeight = currentHeight();
          _moved = false;
          return true;
        case MotionEvent.ACTION_MOVE:
          if (Math.abs(event.getRawY() - _startY) > dp(4))
            _moved = true;
          resizeForDrag(_startHeight, _startY, event.getRawY());
          return true;
        case MotionEvent.ACTION_UP:
          if (!_moved)
            v.performClick();
          return true;
        case MotionEvent.ACTION_CANCEL:
          return true;
        default:
          return false;
      }
    }
  }
}
