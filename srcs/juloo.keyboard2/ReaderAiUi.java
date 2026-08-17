package juloo.keyboard2;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Native semantic styling shared by the 2D and 3D Reader AI surfaces. */
final class ReaderAiUi
{
  final Context context;
  final int text;
  final int muted;
  final int surface;
  final int border;
  final int highlight;
  final int accent;

  ReaderAiUi(Context context)
  {
    this.context = context;
    text = color(R.attr.readerTextColor, 0xfff4f7fa);
    muted = color(R.attr.readerSecondaryTextColor, 0xffa8b2be);
    surface = color(R.attr.readerSurfaceColor, 0xff1c2229);
    border = color(R.attr.readerBorderColor, 0xff495460);
    highlight = color(R.attr.readerHighlightColor, 0xff1f5b53);
    accent = color(android.R.attr.colorAccent, 0xff55d6be);
  }

  TextView text(String value, float sp, int color)
  {
    TextView view = new TextView(context);
    view.setText(value);
    view.setTextSize(sp);
    view.setTextColor(color);
    return view;
  }

  Button button(String label)
  {
    Button button = new Button(context);
    button.setText(label);
    button.setTextColor(text);
    button.setTextSize(12);
    button.setAllCaps(false);
    button.setMinWidth(0);
    button.setMinHeight(dp(42));
    button.setMinimumWidth(0);
    button.setMinimumHeight(dp(42));
    button.setPadding(dp(8), 0, dp(8), 0);
    button.setBackground(panel(surface, border, 8));
    return button;
  }

  void selected(Button button, boolean selected)
  {
    button.setBackground(panel(selected ? highlight : surface,
          selected ? accent : border, 8));
  }

  GradientDrawable panel(int fill, int stroke, int radiusDp)
  {
    GradientDrawable drawable = new GradientDrawable();
    drawable.setColor(fill);
    drawable.setCornerRadius(dp(radiusDp));
    drawable.setStroke(dp(1), stroke);
    return drawable;
  }

  LinearLayout row()
  {
    LinearLayout row = new LinearLayout(context);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setGravity(Gravity.CENTER_VERTICAL);
    return row;
  }

  void addWeighted(LinearLayout row, android.view.View child, float weight,
      int marginStart)
  {
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
        dp(42), weight);
    params.setMarginStart(marginStart);
    row.addView(child, params);
  }

  int dp(int value)
  {
    return Math.round(value * Resources.getSystem().getDisplayMetrics().density);
  }

  private int color(int attribute, int fallback)
  {
    TypedValue value = new TypedValue();
    return context.getTheme().resolveAttribute(attribute, value, true)
      ? value.data : fallback;
  }
}
